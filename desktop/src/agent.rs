use anyhow::{anyhow, Context, Result};
use serde::Deserialize;
use serde_json::{json, Value};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::thread;
use std::time::{Duration, Instant};

use crate::commands::repo_root;

#[derive(Debug, Clone)]
pub struct AgentClient {
    host: String,
    port: u16,
    http: reqwest::blocking::Client,
}

#[derive(Debug, Clone, Deserialize)]
pub struct TaskSnapshot {
    pub id: String,
    pub kind: String,
    pub state: String,
    pub message: Option<String>,
    pub output: Vec<String>,
    #[serde(rename = "downloadName")]
    pub download_name: Option<String>,
}

impl AgentClient {
    pub fn new(host: impl Into<String>, port: u16) -> Self {
        Self {
            host: host.into(),
            port,
            http: shared_http_client().clone(),
        }
    }

    pub fn health(&self) -> Result<String> {
        self.get_pretty("/api/v1/health")
    }

    pub fn session_json(&self) -> Result<Value> {
        self.get_json("/api/v1/session")
    }

    pub fn runtime_json(&self) -> Result<Value> {
        self.get_json("/api/v1/runtime")
    }

    pub fn root_grants_json(&self) -> Result<Value> {
        self.get_json("/api/v1/root-grants")
    }

    pub fn susfs_json(&self) -> Result<Value> {
        self.get_json("/api/v1/susfs")
    }

    pub fn set_root_grant(&self, package_name: &str, allowed: bool) -> Result<String> {
        self.post_pretty(
            &format!("/api/v1/root-grants/{package_name}/allow"),
            json!({ "allowed": allowed }),
        )
    }

    pub fn root_grant_icon_png(&self, package_name: &str) -> Result<Vec<u8>> {
        let url = format!("{}/api/v1/root-grants/{package_name}/icon", self.base_url());
        let response = self
            .http
            .get(&url)
            .send()
            .with_context(|| format!("GET {url} failed"))?;
        if !response.status().is_success() {
            let body = response.text().unwrap_or_default();
            return Err(anyhow!("HTTP icon fetch failed: {}", body.trim()));
        }
        Ok(response
            .bytes()
            .context("failed to read icon bytes")?
            .to_vec())
    }

    pub fn set_module_enabled(&self, module_id: &str, enabled: bool) -> Result<String> {
        self.post_pretty(
            &format!("/api/v1/runtime/modules/{module_id}/enable"),
            json!({ "enabled": enabled }),
        )
    }

    pub fn set_module_pending_uninstall(&self, module_id: &str, pending: bool) -> Result<String> {
        self.post_pretty(
            &format!("/api/v1/runtime/modules/{module_id}/pending-uninstall"),
            json!({ "pending": pending }),
        )
    }

    pub fn apply_susfs_json(&self, body: &str) -> Result<TaskSnapshot> {
        let value: Value = serde_json::from_str(body).context("invalid JSON in SUSFS editor")?;
        self.submit_task("/api/v1/susfs/apply", value)
    }

    pub fn run_module_action(&self, module_id: &str) -> Result<TaskSnapshot> {
        self.submit_task(
            &format!("/api/v1/runtime/modules/{module_id}/action"),
            json!({}),
        )
    }

    pub fn install_module(&self, path: &str) -> Result<TaskSnapshot> {
        self.submit_task("/api/v1/install/module", json!({ "zipPath": path }))
    }

    pub fn install_apk(&self, path: &str) -> Result<TaskSnapshot> {
        self.submit_task("/api/v1/install/apk", json!({ "apkPath": path }))
    }

    pub fn flash_image(&self, path: &str, partition: &str) -> Result<TaskSnapshot> {
        self.submit_task(
            "/api/v1/flash/image",
            json!({ "imagePath": path, "partition": partition }),
        )
    }

    pub fn export_diagnostics(&self) -> Result<TaskSnapshot> {
        self.submit_task("/api/v1/diagnostics/export", json!({}))
    }

    pub fn poll_task(&self, task_id: &str, timeout: Duration) -> Result<TaskSnapshot> {
        let started = Instant::now();
        loop {
            let snapshot = self.fetch_task(task_id)?;
            if snapshot.state == "succeeded" || snapshot.state == "failed" {
                return Ok(snapshot);
            }
            if started.elapsed() > timeout {
                return Err(anyhow!("task timed out: {task_id}"));
            }
            thread::sleep(Duration::from_millis(500));
        }
    }

    pub fn download_task_file(&self, task_id: &str, output_dir: &Path) -> Result<PathBuf> {
        fs::create_dir_all(output_dir)
            .with_context(|| format!("failed to create {}", output_dir.display()))?;
        let snapshot = self.fetch_task(task_id)?;
        let file_name = snapshot
            .download_name
            .clone()
            .unwrap_or_else(|| format!("{task_id}.bin"));
        let url = format!("{}/api/v1/tasks/{task_id}/download", self.base_url());
        let response = self
            .http
            .get(&url)
            .send()
            .context("failed to download task file")?;
        if !response.status().is_success() {
            let body = response.text().unwrap_or_default();
            return Err(anyhow!("download failed: {}", body.trim()));
        }
        let bytes = response
            .bytes()
            .context("failed to read task download bytes")?;
        let path = output_dir.join(file_name);
        fs::write(&path, &bytes).with_context(|| format!("failed to write {}", path.display()))?;
        Ok(path)
    }

    pub fn default_download_dir() -> PathBuf {
        repo_root().join("desktop-downloads")
    }

    fn fetch_task(&self, task_id: &str) -> Result<TaskSnapshot> {
        let text = self.get_raw(&format!("/api/v1/tasks/{task_id}"))?;
        serde_json::from_str(&text).context("failed to parse task response")
    }

    fn submit_task(&self, path: &str, body: Value) -> Result<TaskSnapshot> {
        let text = self.post_raw(path, body)?;
        serde_json::from_str(&text).context("failed to parse accepted task response")
    }

    fn get_pretty(&self, path: &str) -> Result<String> {
        pretty_json(&self.get_raw(path)?)
    }

    fn get_json(&self, path: &str) -> Result<Value> {
        let raw = self.get_raw(path)?;
        serde_json::from_str(&raw).context("failed to parse response JSON")
    }

    fn post_pretty(&self, path: &str, body: Value) -> Result<String> {
        pretty_json(&self.post_raw(path, body)?)
    }

    fn get_raw(&self, path: &str) -> Result<String> {
        let url = format!("{}{}", self.base_url(), path);
        let response = self
            .http
            .get(&url)
            .send()
            .with_context(|| format!("GET {url} failed"))?;
        handle_response(response)
    }

    fn post_raw(&self, path: &str, body: Value) -> Result<String> {
        let url = format!("{}{}", self.base_url(), path);
        let response = self
            .http
            .post(&url)
            .json(&body)
            .send()
            .with_context(|| format!("POST {url} failed"))?;
        handle_response(response)
    }

    fn base_url(&self) -> String {
        format!("http://{}:{}", self.host, self.port)
    }
}

fn shared_http_client() -> &'static reqwest::blocking::Client {
    static HTTP_CLIENT: OnceLock<reqwest::blocking::Client> = OnceLock::new();
    HTTP_CLIENT.get_or_init(|| {
        reqwest::blocking::Client::builder()
            .timeout(Duration::from_secs(15))
            .build()
            .expect("reqwest client")
    })
}

fn handle_response(response: reqwest::blocking::Response) -> Result<String> {
    let status = response.status();
    let body = response
        .text()
        .context("failed to read HTTP response body")?;
    if status.is_success() {
        Ok(body)
    } else {
        Err(anyhow!("HTTP {}: {}", status, body.trim()))
    }
}

fn pretty_json(raw: &str) -> Result<String> {
    let value: Value = serde_json::from_str(raw).context("failed to parse response JSON")?;
    serde_json::to_string_pretty(&value).context("failed to pretty-print JSON")
}

pub fn pretty_json_value(value: &Value) -> Result<String> {
    serde_json::to_string_pretty(value).context("failed to pretty-print JSON")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builds_default_download_dir_under_repo() {
        assert!(AgentClient::default_download_dir()
            .to_string_lossy()
            .contains("desktop-downloads"));
    }
}
