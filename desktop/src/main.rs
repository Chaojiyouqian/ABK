mod agent;
mod commands;

use crate::agent::RemoteAgentClient;
use crate::commands::{
    build_adb_detect_command, build_adb_forward_command, build_adb_push_command,
    build_adb_remove_forward_command, build_adb_shell_command, build_adb_start_agent_command,
    build_adb_stop_agent_command, build_cli_command, repo_root, run_command,
};
use anyhow::{anyhow, Context, Result};
use axum::body::{Body, Bytes};
use axum::extract::{Path, Query, State};
use axum::http::header::{CACHE_CONTROL, CONTENT_TYPE};
use axum::http::{HeaderMap, HeaderValue, Method, Response, StatusCode, Uri};
use axum::response::IntoResponse;
use axum::routing::{get, post};
use axum::{Json, Router};
use futures_util::TryStreamExt;
use mime_guess::MimeGuess;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet, VecDeque};
use std::env;
use std::fs;
use std::net::SocketAddr;
use std::path::{Path as FsPath, PathBuf};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tokio::net::TcpListener;
use tokio::time::sleep;
use tower_http::cors::{Any, CorsLayer};
use uuid::Uuid;

const DEFAULT_SIDECAR_HOST: &str = "127.0.0.1";
const DEFAULT_SIDECAR_PORT: u16 = 38765;
const DEFAULT_AGENT_HOST: &str = "127.0.0.1";
const DEFAULT_AGENT_PORT: u16 = 48765;
const SIDELOAD_DIR: &str = "/data/local/tmp/abk-desktop";
const CLI_CONFIG_PATH_SUFFIX: &str = ".config/abk/config.json";
const GITHUB_OAUTH_DEVICE_URL: &str = "https://github.com/login/device/code";
const GITHUB_OAUTH_TOKEN_URL: &str = "https://github.com/login/oauth/access_token";
const GITHUB_CLIENT_ID_FALLBACK: &str = "Ov23li8skGo6AFPBeSTh";
const MAX_LOG_LINES: usize = 500;
const MAX_TASKS: usize = 64;
const BUILD_TRACK_RUN_LIMIT: usize = 50;
const BUILD_DISCOVERY_POLL_INTERVAL: Duration = Duration::from_secs(3);
const BUILD_COMPLETION_POLL_INTERVAL: Duration = Duration::from_secs(10);
const BUILD_DISCOVERY_TIMEOUT: Duration = Duration::from_secs(120);

#[derive(Clone)]
struct AppState {
    inner: Arc<InnerState>,
}

struct InnerState {
    agent: RemoteAgentClient,
    connection: RwLock<ConnectionState>,
    logs: Mutex<VecDeque<LogEntry>>,
    tasks: Mutex<HashMap<String, LocalTask>>,
    task_order: Mutex<VecDeque<String>>,
}

#[derive(Debug, Clone, Serialize, Default)]
#[serde(rename_all = "camelCase")]
struct ConnectionState {
    serial: Option<String>,
    agent_host: String,
    agent_port: u16,
    connected: bool,
    mode: ConnectionMode,
    last_error: Option<String>,
    last_detected: Vec<DetectedDevice>,
    last_detect_raw: String,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase")]
enum ConnectionMode {
    #[default]
    Disconnected,
    Abk,
    AdbFallback,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DetectedDevice {
    serial: String,
    status: String,
    detail: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct LogEntry {
    id: String,
    timestamp_ms: u64,
    scope: String,
    level: String,
    message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct TaskSnapshot {
    id: String,
    kind: String,
    state: String,
    message: Option<String>,
    #[serde(default)]
    output: Vec<String>,
    #[serde(default)]
    result: Value,
    download_name: Option<String>,
    download_content_type: Option<String>,
}

#[derive(Debug, Clone)]
struct LocalTask {
    snapshot: TaskSnapshot,
    download_path: Option<PathBuf>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConnectRequest {
    serial: Option<String>,
    port: Option<u16>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstallModuleRequest {
    zip_path: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct KernelFeatureRequest {
    enabled: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstallApkRequest {
    apk_path: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FlashImageRequest {
    image_path: String,
    partition: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CliRunRequest {
    args: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GitHubLoginPollRequest {
    device_code: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DownloadDirRequest {
    path: String,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BuildGkiRequest {
    target: String,
    ksu_variant: Option<String>,
    ksu_branch: Option<String>,
    version: Option<String>,
    revision: Option<String>,
    custom_ref: Option<String>,
    build_time: Option<String>,
    custom_modules: Option<String>,
    kpm_password: Option<String>,
    virt: Option<String>,
    zram: bool,
    bbg: bool,
    ddk: bool,
    kpm: bool,
    susfs: bool,
    rekernel: bool,
    ntsync: bool,
    networking: bool,
    zram_full_algo: bool,
    zram_extra_algos: Option<String>,
    android_version: Option<String>,
    kernel_version: Option<String>,
    sub_level: Option<String>,
    os_patch_level: Option<String>,
    force: Option<bool>,
}

#[derive(Debug, Deserialize)]
struct BuildRunsQuery {
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ArtifactDownloadRequest {
    artifact_id: u64,
    output_dir: Option<String>,
}

#[derive(Debug, Deserialize)]
struct LogQuery {
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
struct PackageQuery {
    r#type: Option<String>,
}

#[derive(Debug, Deserialize)]
struct AgentPortQuery {
    port: Option<u16>,
}

#[derive(Debug)]
struct ApiError {
    status: StatusCode,
    message: String,
}

impl ApiError {
    fn bad_request(message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::BAD_REQUEST,
            message: message.into(),
        }
    }

    fn service_unavailable(message: impl Into<String>) -> Self {
        Self {
            status: StatusCode::SERVICE_UNAVAILABLE,
            message: message.into(),
        }
    }

    fn internal(error: anyhow::Error) -> Self {
        Self {
            status: StatusCode::INTERNAL_SERVER_ERROR,
            message: error.to_string(),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response<Body> {
        let body = Json(json!({ "error": self.message }));
        (self.status, body).into_response()
    }
}

impl From<anyhow::Error> for ApiError {
    fn from(value: anyhow::Error) -> Self {
        ApiError::internal(value)
    }
}

impl From<serde_json::Error> for ApiError {
    fn from(value: serde_json::Error) -> Self {
        ApiError::internal(anyhow::Error::new(value))
    }
}

impl AppState {
    fn new(_repo_root: PathBuf) -> Result<Self> {
        Ok(Self {
            inner: Arc::new(InnerState {
                agent: RemoteAgentClient::new()?,
                connection: RwLock::new(ConnectionState {
                    serial: None,
                    agent_host: DEFAULT_AGENT_HOST.into(),
                    agent_port: DEFAULT_AGENT_PORT,
                    connected: false,
                    mode: ConnectionMode::Disconnected,
                    last_error: None,
                    last_detected: Vec::new(),
                    last_detect_raw: String::new(),
                }),
                logs: Mutex::new(VecDeque::new()),
                tasks: Mutex::new(HashMap::new()),
                task_order: Mutex::new(VecDeque::new()),
            }),
        })
    }

    fn log(&self, scope: &str, level: &str, message: impl Into<String>) {
        let message = message.into();
        let entry = LogEntry {
            id: Uuid::new_v4().to_string(),
            timestamp_ms: now_ms(),
            scope: scope.into(),
            level: level.into(),
            message,
        };
        let mut logs = self.inner.logs.lock().expect("logs");
        logs.push_back(entry);
        while logs.len() > MAX_LOG_LINES {
            logs.pop_front();
        }
    }

    fn connection(&self) -> ConnectionState {
        self.inner.connection.read().expect("connection").clone()
    }

    fn update_connection(&self, update: impl FnOnce(&mut ConnectionState)) {
        let mut connection = self.inner.connection.write().expect("connection");
        update(&mut connection);
    }

    fn recent_logs(&self, limit: usize) -> Vec<LogEntry> {
        let logs = self.inner.logs.lock().expect("logs");
        logs.iter()
            .rev()
            .take(limit)
            .cloned()
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
            .collect()
    }

    fn upsert_task(&self, task: LocalTask) {
        let id = task.snapshot.id.clone();
        let mut tasks = self.inner.tasks.lock().expect("tasks");
        let mut order = self.inner.task_order.lock().expect("task_order");
        if !tasks.contains_key(&id) {
            order.push_back(id.clone());
        }
        tasks.insert(id.clone(), task);
        while order.len() > MAX_TASKS {
            if let Some(oldest) = order.pop_front() {
                tasks.remove(&oldest);
            }
        }
    }

    fn get_local_task(&self, task_id: &str) -> Option<LocalTask> {
        self.inner
            .tasks
            .lock()
            .expect("tasks")
            .get(task_id)
            .cloned()
    }

    fn base_agent_url(&self) -> Result<String> {
        let connection = self.connection();
        if !connection.connected {
            return Err(anyhow!("device service not connected"));
        }
        Ok(format!(
            "http://{}:{}",
            connection.agent_host, connection.agent_port
        ))
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let host = env::var("ABK_DESKTOP_HOST").unwrap_or_else(|_| DEFAULT_SIDECAR_HOST.into());
    let port = env::args()
        .skip(1)
        .collect::<Vec<_>>()
        .windows(2)
        .find_map(|pair| {
            if pair[0] == "--port" {
                pair[1].parse::<u16>().ok()
            } else {
                None
            }
        })
        .unwrap_or(DEFAULT_SIDECAR_PORT);
    let state = AppState::new(repo_root())?;
    state.log(
        "sidecar",
        "info",
        format!("starting ABK desktop sidecar on {host}:{port}"),
    );

    let app = Router::new()
        .route("/api/v1/health", get(local_health))
        .route("/api/v1/device", get(get_device_state))
        .route("/api/v1/device/detect", post(detect_devices))
        .route("/api/v1/device/connect", post(connect_device))
        .route("/api/v1/device/disconnect", post(disconnect_device))
        .route("/api/v1/cli/run", post(run_cli_task))
        .route("/api/v1/logs", get(get_logs))
        .route("/api/v1/github/session", get(get_github_session))
        .route("/api/v1/github/login/start", post(start_github_login))
        .route("/api/v1/github/login/poll", post(poll_github_login))
        .route("/api/v1/github/fork/ensure", post(ensure_github_fork))
        .route("/api/v1/github/fork/sync", post(sync_github_fork))
        .route("/api/v1/github/logout", post(logout_github))
        .route("/api/v1/settings/download-dir", post(set_download_dir))
        .route("/api/v1/builds/gki", post(start_gki_build))
        .route("/api/v1/builds/runs", get(list_build_runs))
        .route("/api/v1/builds/runs/{run_id}", get(get_build_run))
        .route(
            "/api/v1/builds/runs/{run_id}/artifacts",
            get(list_build_run_artifacts),
        )
        .route(
            "/api/v1/builds/runs/{run_id}/artifacts/download",
            post(download_build_artifact),
        )
        .route("/api/v1/session", get(proxy_session))
        .route("/api/v1/runtime", get(proxy_runtime))
        .route("/api/v1/root-grants", get(proxy_root_grants))
        .route("/api/v1/kernel-features", get(proxy_kernel_features))
        .route("/api/v1/packages", get(proxy_packages))
        .route("/api/v1/packages/info", post(proxy_package_info))
        .route(
            "/api/v1/root-grants/{package_name}/allow",
            post(proxy_root_grant_allow),
        )
        .route(
            "/api/v1/kernel-features/{feature_id}",
            post(proxy_kernel_feature_set),
        )
        .route(
            "/api/v1/root-grants/{package_name}/icon",
            get(proxy_root_grant_icon),
        )
        .route("/api/v1/susfs", get(proxy_susfs))
        .route("/api/v1/susfs/apply", post(proxy_susfs_apply))
        .route(
            "/api/v1/runtime/modules/{module_id}/enable",
            post(proxy_module_enable),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/pending-uninstall",
            post(proxy_module_pending_uninstall),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/action",
            post(proxy_module_action),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/module-info",
            get(proxy_module_webui_module_info),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/exec",
            post(proxy_module_webui_exec),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/spawn",
            post(proxy_module_webui_spawn),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/http-proxy",
            get(proxy_module_webui_http_proxy).post(proxy_module_webui_http_proxy),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/files",
            get(proxy_module_webui_root),
        )
        .route(
            "/api/v1/runtime/modules/{module_id}/webui/files/{*relative_path}",
            get(proxy_module_webui_file),
        )
        .route("/api/v1/install/module", post(proxy_install_module))
        .route("/api/v1/install/apk", post(proxy_install_apk))
        .route("/api/v1/flash/image", post(proxy_flash_image))
        .route("/api/v1/diagnostics/export", post(proxy_export_diagnostics))
        .route("/api/v1/tasks/{task_id}", get(get_task))
        .route("/api/v1/tasks/{task_id}/download", get(download_task_file))
        .route("/internal/insets.css", get(insets_css))
        .fallback(proxy_webui_root_asset_fallback)
        .with_state(state.clone())
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_headers(Any)
                .allow_methods(Any),
        );

    let addr = SocketAddr::from((host.parse::<std::net::IpAddr>()?, port));
    let listener = TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}

async fn local_health(
    State(state): State<AppState>,
    Query(query): Query<AgentPortQuery>,
) -> Result<Json<Value>, ApiError> {
    let connection = state.connection();
    let port = query.port.unwrap_or(connection.agent_port);
    let agent_health = if connection.connected {
        state
            .inner
            .agent
            .get_json(
                &format!("http://{}:{}", connection.agent_host, port),
                "/api/v1/health",
            )
            .await
            .ok()
    } else {
        None
    };
    Ok(Json(json!({
        "status": "ok",
        "protocolVersion": "abk-desktop-sidecar-v1",
        "sidecar": {
            "host": DEFAULT_SIDECAR_HOST,
            "port": DEFAULT_SIDECAR_PORT,
        },
        "device": connection,
        "agent": agent_health,
    })))
}

async fn get_device_state(State(state): State<AppState>) -> Json<ConnectionState> {
    Json(state.connection())
}

async fn detect_devices(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    let spec = build_adb_detect_command();
    let output = tokio::task::spawn_blocking(move || run_command(&spec))
        .await
        .context("adb detect task failed")?
        .map_err(ApiError::from)?;
    let devices = parse_detected_devices(&output);
    state.update_connection(|connection| {
        connection.last_detect_raw = output.clone();
        connection.last_detected = devices.clone();
        connection.last_error = None;
        reconcile_connection_after_detect(connection, &devices);
    });
    state.log(
        "device.detect",
        "info",
        format!("detected {} adb device(s)", devices.len()),
    );
    Ok(Json(json!({
        "devices": devices,
        "raw": output,
    })))
}

async fn connect_device(
    State(state): State<AppState>,
    Json(request): Json<ConnectRequest>,
) -> Result<Json<Value>, ApiError> {
    let serial = match resolve_connect_serial(request.serial.as_deref(), &state.connection()) {
        Ok(serial) => serial,
        Err(error) => {
            let message = error.message.clone();
            state.update_connection(|connection| {
                connection.connected = false;
                connection.mode = ConnectionMode::Disconnected;
                connection.last_error = Some(message.clone());
            });
            return Err(error);
        }
    };
    let port = request.port.unwrap_or(DEFAULT_AGENT_PORT);

    let forward = build_adb_forward_command(&serial, port);
    let start = build_adb_start_agent_command(&serial, port);
    state.update_connection(|connection| {
        connection.serial = Some(serial.clone());
        connection.agent_port = port;
        connection.agent_host = DEFAULT_AGENT_HOST.into();
        connection.connected = false;
        connection.last_error = None;
    });

    let result = async {
        run_blocking_command(forward).await?;
        run_blocking_command(start).await?;
        state.log(
            "device.connect",
            "info",
            format!("started phone agent on port {port} for {serial}"),
        );

        wait_for_agent(
            &state,
            &format!("http://{}:{}", DEFAULT_AGENT_HOST, port),
            Duration::from_secs(20),
        )
        .await?;

        state
            .inner
            .agent
            .get_json(
                &format!("http://{}:{}", DEFAULT_AGENT_HOST, port),
                "/api/v1/health",
            )
            .await
            .map_err(ApiError::from)
    }
    .await;

    match result {
        Ok(health) => {
            state.update_connection(|connection| {
                connection.connected = true;
                connection.mode = ConnectionMode::Abk;
                connection.last_error = None;
            });
            Ok(Json(json!({
                "connected": true,
                "mode": ConnectionMode::Abk,
                "device": state.connection(),
                "agent": health,
            })))
        }
        Err(error) => {
            let message = error.message.clone();
            run_blocking_command(build_adb_stop_agent_command(&serial))
                .await
                .ok();
            run_blocking_command(build_adb_remove_forward_command(&serial, port))
                .await
                .ok();
            state.update_connection(|connection| {
                connection.connected = false;
                connection.mode = ConnectionMode::AdbFallback;
                connection.last_error = Some(message.clone());
            });
            state.log(
                "device.connect",
                "error",
                format!("failed to establish ABK agent for {serial}: {message}"),
            );
            Err(error)
        }
    }
}

async fn disconnect_device(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    let connection = state.connection();
    let serial = connection.serial.unwrap_or_default();
    run_blocking_command(build_adb_stop_agent_command(&serial))
        .await
        .ok();
    run_blocking_command(build_adb_remove_forward_command(
        &serial,
        connection.agent_port,
    ))
    .await
    .ok();
    state.update_connection(|current| {
        current.serial = None;
        current.connected = false;
        current.mode = ConnectionMode::Disconnected;
        current.last_error = None;
    });
    state.log(
        "device.disconnect",
        "info",
        "stopped forwarded agent session",
    );
    Ok(Json(json!({
        "connected": false,
        "device": state.connection(),
    })))
}

async fn run_cli_task(
    State(state): State<AppState>,
    Json(request): Json<CliRunRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let spec = build_cli_command(&request.args).map_err(ApiError::from)?;
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: "cli.run".into(),
        state: "pending".into(),
        message: Some("cli command accepted".into()),
        output: Vec::new(),
        result: json!({ "args": request.args }),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });
    state.log("builds", "info", format!("queued CLI task {id}"));

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running = TaskSnapshot {
            state: "running".into(),
            message: Some("cli command running".into()),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running,
            download_path: None,
        });
        let output = tokio::task::spawn_blocking(move || run_command(&spec)).await;
        match output {
            Ok(Ok(text)) => {
                let lines = split_lines(&text);
                task_state.log("builds", "info", format!("CLI task {id} succeeded"));
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "succeeded".into(),
                        message: Some("cli command completed".into()),
                        output: lines.clone(),
                        result: json!({ "stdout": text }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Ok(Err(error)) => {
                let message = error.to_string();
                task_state.log(
                    "builds",
                    "error",
                    format!("CLI task {id} failed: {message}"),
                );
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("cli command failed".into()),
                        output: split_lines(&message),
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.to_string();
                task_state.log(
                    "builds",
                    "error",
                    format!("CLI task {id} join failure: {message}"),
                );
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("cli task join failure".into()),
                        output: vec![message.clone()],
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });

    Ok((StatusCode::ACCEPTED, Json(accepted_snapshot)))
}

async fn get_logs(State(state): State<AppState>, Query(query): Query<LogQuery>) -> Json<Value> {
    let limit = query.limit.unwrap_or(200).clamp(1, MAX_LOG_LINES);
    Json(json!({ "logs": state.recent_logs(limit) }))
}

async fn get_github_session() -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec!["--json".into(), "whoami".into()])
        .await
        .map(Json)
}

async fn start_github_login() -> Result<Json<Value>, ApiError> {
    let client_id = cli_client_id().await?;
    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .context("failed to build github client")?;
    let response = http
        .post(GITHUB_OAUTH_DEVICE_URL)
        .header("Accept", "application/json")
        .header("User-Agent", "ABK-Desktop")
        .form(&[
            ("client_id", client_id.as_str()),
            ("scope", "repo workflow"),
        ])
        .send()
        .await
        .context("failed to request github device code")?;
    let status = response.status();
    let body = response
        .text()
        .await
        .context("failed to read github device response")?;
    if !status.is_success() {
        return Err(ApiError::service_unavailable(body));
    }
    let value: Value = serde_json::from_str(&body)?;
    Ok(Json(json!({
        "deviceCode": value.get("device_code").and_then(Value::as_str).unwrap_or_default(),
        "userCode": value.get("user_code").and_then(Value::as_str).unwrap_or_default(),
        "verificationUri": value.get("verification_uri").and_then(Value::as_str).unwrap_or_default(),
        "verificationUriComplete": value.get("verification_uri_complete").and_then(Value::as_str),
        "expiresIn": value.get("expires_in").and_then(Value::as_u64).unwrap_or(900),
        "interval": value.get("interval").and_then(Value::as_u64).unwrap_or(5),
    })))
}

async fn poll_github_login(
    Json(request): Json<GitHubLoginPollRequest>,
) -> Result<Json<Value>, ApiError> {
    let client_id = cli_client_id().await?;
    let http = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .context("failed to build github client")?;
    let response = http
        .post(GITHUB_OAUTH_TOKEN_URL)
        .header("Accept", "application/json")
        .header("User-Agent", "ABK-Desktop")
        .form(&[
            ("client_id", client_id.as_str()),
            ("device_code", request.device_code.as_str()),
            ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
        ])
        .send()
        .await
        .context("failed to poll github login")?;
    let status = response.status();
    let body = response
        .text()
        .await
        .context("failed to read github login response")?;
    if !status.is_success() {
        return Err(ApiError::service_unavailable(body));
    }
    let value: Value = serde_json::from_str(&body)?;
    if let Some(token) = value.get("access_token").and_then(Value::as_str) {
        persist_cli_token(token).await?;
        let session = run_cli_json_command(vec!["--json".into(), "whoami".into()])
            .await
            .unwrap_or_else(|_| json!({"ok": true, "loggedIn": true}));
        return Ok(Json(json!({"state": "authorized", "session": session})));
    }
    let error = value
        .get("error")
        .and_then(Value::as_str)
        .unwrap_or("unknown");
    Ok(Json(json!({
        "state": error,
        "interval": value.get("interval").and_then(Value::as_u64).unwrap_or(5),
        "error": value.get("error_description").and_then(Value::as_str),
    })))
}

async fn ensure_github_fork() -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec!["--json".into(), "fork".into(), "--no-sync".into()]).await?;
    get_github_session().await
}

async fn sync_github_fork() -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec!["--json".into(), "sync".into()]).await?;
    get_github_session().await
}

async fn logout_github() -> Result<Json<Value>, ApiError> {
    clear_cli_token().await?;
    get_github_session().await
}

async fn set_download_dir(
    Json(request): Json<DownloadDirRequest>,
) -> Result<Json<Value>, ApiError> {
    let path = request.path.trim();
    if path.is_empty() {
        return Err(ApiError::bad_request("download directory path missing"));
    }
    run_cli_json_command(vec![
        "--json".into(),
        "artifacts".into(),
        "--set-download-dir".into(),
        path.into(),
    ])
    .await
    .map(Json)
}

async fn list_build_runs(Query(query): Query<BuildRunsQuery>) -> Result<Json<Value>, ApiError> {
    let limit = query.limit.unwrap_or(10);
    run_cli_json_command(vec![
        "--json".into(),
        "status".into(),
        "--limit".into(),
        limit.to_string(),
    ])
    .await
    .map(Json)
}

async fn get_build_run(Path(run_id): Path<u64>) -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec![
        "--json".into(),
        "status".into(),
        "--run-id".into(),
        run_id.to_string(),
    ])
    .await
    .map(Json)
}

async fn list_build_run_artifacts(Path(run_id): Path<u64>) -> Result<Json<Value>, ApiError> {
    run_cli_json_command(vec![
        "--json".into(),
        "artifacts".into(),
        "--run-id".into(),
        run_id.to_string(),
    ])
    .await
    .map(Json)
}

async fn track_dispatched_build_task(
    state: AppState,
    snapshot: TaskSnapshot,
    dispatch_result: Value,
    baseline_run_ids: HashSet<u64>,
    base_output: Vec<String>,
) -> Result<(), ApiError> {
    let dispatches = extract_dispatch_workflow_names(&dispatch_result);
    if dispatches.is_empty() {
        state.upsert_task(LocalTask {
            snapshot: TaskSnapshot {
                state: "succeeded".into(),
                message: Some("build dispatch finished".into()),
                output: build_gki_tracking_output(&base_output, &[], "build dispatch finished"),
                result: merge_build_tracking_result(&dispatch_result, &[], "dispatch_finished"),
                ..snapshot
            },
            download_path: None,
        });
        return Ok(());
    }

    let discovery_started = SystemTime::now();
    let mut tracked_runs = loop {
        let runs_payload = run_cli_json_command(vec![
            "--json".into(),
            "status".into(),
            "--limit".into(),
            BUILD_TRACK_RUN_LIMIT.to_string(),
        ])
        .await?;
        let recent_runs = extract_runs_from_status(&runs_payload);
        let tracked_runs = select_dispatched_runs(&recent_runs, &baseline_run_ids, &dispatches);
        let discovered = tracked_runs.len();
        let expected = dispatches.len();
        state.upsert_task(LocalTask {
            snapshot: TaskSnapshot {
                state: "running".into(),
                message: Some(format!(
                    "build dispatched, waiting for workflow runs ({discovered}/{expected})"
                )),
                output: build_gki_tracking_output(
                    &base_output,
                    &tracked_runs,
                    &format!("workflow discovery {discovered}/{expected}"),
                ),
                result: merge_build_tracking_result(
                    &dispatch_result,
                    &tracked_runs,
                    "discovering_runs",
                ),
                ..snapshot.clone()
            },
            download_path: None,
        });
        if discovered >= expected {
            break tracked_runs;
        }
        if discovery_started.elapsed().unwrap_or_default() < BUILD_DISCOVERY_TIMEOUT {
            sleep(BUILD_DISCOVERY_POLL_INTERVAL).await;
            continue;
        }
        return Err(ApiError::service_unavailable(format!(
            "build dispatched but only discovered {discovered}/{expected} workflow runs"
        )));
    };

    loop {
        let mut refreshed_runs = Vec::with_capacity(tracked_runs.len());
        for run in &tracked_runs {
            let run_id = extract_run_id(run)
                .ok_or_else(|| ApiError::service_unavailable("tracked workflow run missing id"))?;
            let run_payload = run_cli_json_command(vec![
                "--json".into(),
                "status".into(),
                "--run-id".into(),
                run_id.to_string(),
            ])
            .await?;
            refreshed_runs.push(
                run_payload
                    .get("run")
                    .cloned()
                    .unwrap_or_else(|| run.clone()),
            );
        }
        tracked_runs = refreshed_runs;
        let completed = tracked_runs
            .iter()
            .filter(|run| is_run_terminal(run))
            .count();
        let expected = tracked_runs.len();
        let all_terminal = completed == expected;
        let all_succeeded = tracked_runs.iter().all(|run| run_succeeded(run));
        state.upsert_task(LocalTask {
            snapshot: TaskSnapshot {
                state: if all_terminal && all_succeeded {
                    "succeeded".into()
                } else if all_terminal {
                    "failed".into()
                } else {
                    "running".into()
                },
                message: Some(if all_terminal && all_succeeded {
                    "build workflow finished".into()
                } else if all_terminal {
                    "build workflow failed".into()
                } else {
                    format!("build workflow running ({completed}/{expected})")
                }),
                output: build_gki_tracking_output(
                    &base_output,
                    &tracked_runs,
                    &format!("workflow completion {completed}/{expected}"),
                ),
                result: merge_build_tracking_result(
                    &dispatch_result,
                    &tracked_runs,
                    if all_terminal {
                        "workflow_finished"
                    } else {
                        "running"
                    },
                ),
                ..snapshot.clone()
            },
            download_path: None,
        });
        if all_terminal {
            return Ok(());
        }
        sleep(BUILD_COMPLETION_POLL_INTERVAL).await;
    }
}

async fn start_gki_build(
    State(state): State<AppState>,
    Json(request): Json<BuildGkiRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let session = run_cli_json_command(vec!["--json".into(), "whoami".into()]).await?;
    if !session
        .get("loggedIn")
        .and_then(Value::as_bool)
        .unwrap_or(false)
    {
        return Err(ApiError::service_unavailable(
            "github account not logged in",
        ));
    }
    if session
        .get("needsFork")
        .and_then(Value::as_bool)
        .unwrap_or(true)
    {
        return Err(ApiError::bad_request("fork your ABK repository first"));
    }
    if session
        .get("needsSync")
        .and_then(Value::as_bool)
        .unwrap_or(false)
    {
        return Err(ApiError::bad_request("sync the fork before building"));
    }

    let baseline_run_ids = match run_cli_json_command(vec![
        "--json".into(),
        "status".into(),
        "--limit".into(),
        BUILD_TRACK_RUN_LIMIT.to_string(),
    ])
    .await
    {
        Ok(value) => extract_run_ids_from_status(&value),
        Err(error) => {
            state.log(
                "builds",
                "warn",
                format!(
                    "failed to capture baseline workflow runs: {}",
                    error.message
                ),
            );
            HashSet::new()
        }
    };

    let args = build_gki_cli_args(&request)?;
    let args_for_result = args.clone();
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: "build.gki".into(),
        state: "pending".into(),
        message: Some("build request accepted".into()),
        output: Vec::new(),
        result: json!({
            "request": request,
            "cliArgs": args_for_result,
        }),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running = TaskSnapshot {
            state: "running".into(),
            message: Some("build is being dispatched".into()),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running,
            download_path: None,
        });
        let output = tokio::task::spawn_blocking(move || {
            let spec = crate::commands::build_cli_command_parts(&args)?;
            run_command(&spec)
        })
        .await;
        match output {
            Ok(Ok(text)) => {
                let parsed = parse_cli_json_output(&text).unwrap_or_else(|_| {
                    json!({
                        "stdout": text,
                    })
                });
                let base_output = split_lines(&text);
                if parsed
                    .get("dryRun")
                    .and_then(Value::as_bool)
                    .unwrap_or(false)
                {
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "succeeded".into(),
                            message: Some("build dry run finished".into()),
                            output: base_output,
                            result: parsed,
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                    return;
                }
                if let Err(error) = track_dispatched_build_task(
                    task_state.clone(),
                    snapshot.clone(),
                    parsed,
                    baseline_run_ids,
                    base_output,
                )
                .await
                {
                    let message = error.message.clone();
                    task_state.upsert_task(LocalTask {
                        snapshot: TaskSnapshot {
                            state: "failed".into(),
                            message: Some("build tracking failed".into()),
                            output: split_lines(&message),
                            result: json!({ "error": message }),
                            ..snapshot.clone()
                        },
                        download_path: None,
                    });
                }
            }
            Ok(Err(error)) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("build dispatch failed".into()),
                        output: split_lines(&message),
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("build dispatch join failure".into()),
                        output: vec![message.clone()],
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });

    Ok((StatusCode::ACCEPTED, Json(accepted_snapshot)))
}

async fn download_build_artifact(
    State(state): State<AppState>,
    Path(run_id): Path<u64>,
    Json(request): Json<ArtifactDownloadRequest>,
) -> Result<(StatusCode, Json<TaskSnapshot>), ApiError> {
    let output_dir = request.output_dir.clone();
    let id = Uuid::new_v4().to_string();
    let snapshot = TaskSnapshot {
        id: id.clone(),
        kind: "artifact.download".into(),
        state: "pending".into(),
        message: Some("artifact download accepted".into()),
        output: Vec::new(),
        result: json!({
            "runId": run_id,
            "artifactId": request.artifact_id,
            "outputDir": output_dir,
        }),
        download_name: None,
        download_content_type: None,
    };
    state.upsert_task(LocalTask {
        snapshot: snapshot.clone(),
        download_path: None,
    });

    let task_state = state.clone();
    let accepted_snapshot = snapshot.clone();
    tokio::spawn(async move {
        let running = TaskSnapshot {
            state: "running".into(),
            message: Some("artifact is being downloaded".into()),
            ..snapshot.clone()
        };
        task_state.upsert_task(LocalTask {
            snapshot: running,
            download_path: None,
        });

        let mut args = vec![
            "--json".into(),
            "artifacts".into(),
            "--run-id".into(),
            run_id.to_string(),
            "--download".into(),
            "--artifact-id".into(),
            request.artifact_id.to_string(),
        ];
        if let Some(dir) = output_dir.clone() {
            args.push("--output".into());
            args.push(dir);
        }

        let output = tokio::task::spawn_blocking(move || {
            let spec = crate::commands::build_cli_command_parts(&args)?;
            run_command(&spec)
        })
        .await;

        match output {
            Ok(Ok(text)) => {
                let parsed = parse_cli_json_output(&text).unwrap_or_else(|_| {
                    json!({
                        "stdout": text,
                    })
                });
                let download_path = parsed
                    .get("downloads")
                    .and_then(Value::as_array)
                    .and_then(|items| items.first())
                    .and_then(|item| item.get("path"))
                    .and_then(Value::as_str)
                    .map(PathBuf::from);
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "succeeded".into(),
                        message: Some("artifact download finished".into()),
                        output: split_lines(&text),
                        result: parsed,
                        download_name: download_path
                            .as_ref()
                            .and_then(|path| path.file_name())
                            .and_then(|name| name.to_str())
                            .map(ToString::to_string),
                        download_content_type: Some("application/zip".into()),
                        ..snapshot.clone()
                    },
                    download_path,
                });
            }
            Ok(Err(error)) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("artifact download failed".into()),
                        output: split_lines(&message),
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
            Err(error) => {
                let message = error.to_string();
                task_state.upsert_task(LocalTask {
                    snapshot: TaskSnapshot {
                        state: "failed".into(),
                        message: Some("artifact download join failure".into()),
                        output: vec![message.clone()],
                        result: json!({ "error": message }),
                        ..snapshot.clone()
                    },
                    download_path: None,
                });
            }
        }
    });

    Ok((StatusCode::ACCEPTED, Json(accepted_snapshot)))
}

async fn proxy_session(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/session", None).await?,
    ))
}

async fn proxy_runtime(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/runtime", None).await?,
    ))
}

async fn proxy_root_grants(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/root-grants", None).await?,
    ))
}

async fn proxy_kernel_features(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/kernel-features", None).await?,
    ))
}

async fn proxy_packages(
    State(state): State<AppState>,
    Query(query): Query<PackageQuery>,
) -> Result<Json<Value>, ApiError> {
    let package_type = query.r#type.unwrap_or_else(|| "all".into());
    let path = format!(
        "/api/v1/packages?type={}",
        urlencoding::encode(&package_type)
    );
    Ok(Json(
        proxy_agent_json(&state, Method::GET, &path, None).await?,
    ))
}

async fn proxy_package_info(
    State(state): State<AppState>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::POST, "/api/v1/packages/info", Some(payload)).await?,
    ))
}

async fn proxy_root_grant_allow(
    State(state): State<AppState>,
    Path(package_name): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/root-grants/{}/allow",
        urlencoding::encode(package_name.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_kernel_feature_set(
    State(state): State<AppState>,
    Path(feature_id): Path<String>,
    Json(payload): Json<KernelFeatureRequest>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/kernel-features/{}",
        urlencoding::encode(feature_id.trim())
    );
    Ok(Json(
        proxy_agent_json(
            &state,
            Method::POST,
            &path,
            Some(json!({ "enabled": payload.enabled })),
        )
        .await?,
    ))
}

async fn proxy_root_grant_icon(
    State(state): State<AppState>,
    Path(package_name): Path<String>,
) -> Result<Response<Body>, ApiError> {
    let path = format!(
        "/api/v1/root-grants/{}/icon",
        urlencoding::encode(package_name.trim())
    );
    proxy_binary_response(&state, Method::GET, &path, HeaderMap::new(), None).await
}

async fn proxy_susfs(State(state): State<AppState>) -> Result<Json<Value>, ApiError> {
    Ok(Json(
        proxy_agent_json(&state, Method::GET, "/api/v1/susfs", None).await?,
    ))
}

async fn proxy_susfs_apply(
    State(state): State<AppState>,
    Json(payload): Json<Value>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let value =
        proxy_agent_json_with_status(&state, Method::POST, "/api/v1/susfs/apply", Some(payload))
            .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_module_enable(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/enable",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_pending_uninstall(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/pending-uninstall",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_action(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/action",
        urlencoding::encode(module_id.trim())
    );
    let value = proxy_agent_json_with_status(&state, Method::POST, &path, Some(payload)).await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_module_webui_module_info(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/module-info",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::GET, &path, None).await?,
    ))
}

async fn proxy_module_webui_exec(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/exec",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_webui_spawn(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    Json(payload): Json<Value>,
) -> Result<Json<Value>, ApiError> {
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/spawn",
        urlencoding::encode(module_id.trim())
    );
    Ok(Json(
        proxy_agent_json(&state, Method::POST, &path, Some(payload)).await?,
    ))
}

async fn proxy_module_webui_http_proxy(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
    method: Method,
    query: Query<HashMap<String, String>>,
    headers: HeaderMap,
    body: Bytes,
) -> Result<Response<Body>, ApiError> {
    let target = query.0.get("target").cloned().unwrap_or_default();
    if target.trim().is_empty() {
        return Err(ApiError::bad_request("target missing"));
    }
    let path = format!(
        "/api/v1/runtime/modules/{}/webui/http-proxy?target={}",
        urlencoding::encode(module_id.trim()),
        urlencoding::encode(target.trim())
    );
    proxy_binary_response(
        &state,
        method,
        &path,
        forward_headers(&headers),
        Some(body.to_vec()),
    )
    .await
}

async fn proxy_module_webui_root(
    State(state): State<AppState>,
    Path(module_id): Path<String>,
) -> Result<Response<Body>, ApiError> {
    proxy_module_webui_file_impl(state, module_id, String::new()).await
}

async fn proxy_module_webui_file(
    State(state): State<AppState>,
    Path((module_id, relative_path)): Path<(String, String)>,
) -> Result<Response<Body>, ApiError> {
    proxy_module_webui_file_impl(state, module_id, relative_path).await
}

async fn proxy_module_webui_file_impl(
    state: AppState,
    module_id: String,
    relative_path: String,
) -> Result<Response<Body>, ApiError> {
    let clean_module_id = module_id.trim().to_string();
    let path = if relative_path.trim().is_empty() {
        format!(
            "/api/v1/runtime/modules/{}/webui/files",
            urlencoding::encode(&clean_module_id)
        )
    } else {
        format!(
            "/api/v1/runtime/modules/{}/webui/files/{}",
            urlencoding::encode(&clean_module_id),
            relative_path
        )
    };
    let base_url = state
        .base_agent_url()
        .map_err(|error| ApiError::service_unavailable(error.to_string()))?;
    let response = state
        .inner
        .agent
        .request(
            &base_url,
            reqwest_method(Method::GET)?,
            &path,
            &HeaderMap::new(),
            None,
        )
        .await
        .map_err(ApiError::from)?;
    let status =
        StatusCode::from_u16(response.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let content_type = response
        .headers()
        .get(CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .map(str::to_string)
        .unwrap_or_else(|| guess_content_type(&relative_path).to_string());
    let bytes = response
        .bytes()
        .await
        .context("failed to read module webui asset")?;
    let body_bytes =
        rewrite_module_webui_asset(&clean_module_id, &relative_path, &content_type, &bytes);

    let mut builder = Response::builder().status(status);
    builder = builder
        .header(
            CONTENT_TYPE,
            HeaderValue::from_str(&content_type)
                .unwrap_or(HeaderValue::from_static("application/octet-stream")),
        )
        .header(CACHE_CONTROL, HeaderValue::from_static("no-store"));
    Ok(builder.body(Body::from(body_bytes)).expect("response"))
}

async fn proxy_install_module(
    State(state): State<AppState>,
    Json(payload): Json<InstallModuleRequest>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let remote_path = stage_host_file_if_needed(&state, &payload.zip_path).await?;
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/install/module",
        Some(json!({ "zipPath": remote_path })),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_install_apk(
    State(state): State<AppState>,
    Json(payload): Json<InstallApkRequest>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let remote_path = stage_host_file_if_needed(&state, &payload.apk_path).await?;
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/install/apk",
        Some(json!({ "apkPath": remote_path })),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_flash_image(
    State(state): State<AppState>,
    Json(payload): Json<FlashImageRequest>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let remote_path = stage_host_file_if_needed(&state, &payload.image_path).await?;
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/flash/image",
        Some(json!({
            "imagePath": remote_path,
            "partition": payload.partition,
        })),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn proxy_export_diagnostics(
    State(state): State<AppState>,
) -> Result<(StatusCode, Json<Value>), ApiError> {
    let value = proxy_agent_json_with_status(
        &state,
        Method::POST,
        "/api/v1/diagnostics/export",
        Some(json!({})),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(value)))
}

async fn get_task(
    State(state): State<AppState>,
    Path(task_id): Path<String>,
) -> Result<Json<Value>, ApiError> {
    if let Some(task) = state.get_local_task(task_id.trim()) {
        return Ok(Json(
            serde_json::to_value(task.snapshot).map_err(ApiError::from)?,
        ));
    }
    let path = format!("/api/v1/tasks/{}", urlencoding::encode(task_id.trim()));
    Ok(Json(
        proxy_agent_json(&state, Method::GET, &path, None).await?,
    ))
}

async fn download_task_file(
    State(state): State<AppState>,
    Path(task_id): Path<String>,
) -> Result<Response<Body>, ApiError> {
    if let Some(task) = state.get_local_task(task_id.trim()) {
        if let Some(path) = task.download_path {
            let bytes = tokio::fs::read(&path)
                .await
                .with_context(|| format!("failed to read {}", path.display()))?;
            let file_name = path
                .file_name()
                .and_then(|value| value.to_str())
                .unwrap_or("download.bin");
            return Ok(Response::builder()
                .status(StatusCode::OK)
                .header(
                    CONTENT_TYPE,
                    HeaderValue::from_static("application/octet-stream"),
                )
                .header(
                    "content-disposition",
                    HeaderValue::from_str(&format!("attachment; filename=\"{file_name}\""))
                        .unwrap_or(HeaderValue::from_static("attachment")),
                )
                .body(Body::from(bytes))
                .expect("response"));
        }
    }
    let path = format!(
        "/api/v1/tasks/{}/download",
        urlencoding::encode(task_id.trim())
    );
    proxy_binary_response(&state, Method::GET, &path, HeaderMap::new(), None).await
}

async fn insets_css() -> impl IntoResponse {
    (
        StatusCode::OK,
        [(CONTENT_TYPE, "text/css; charset=utf-8"), (CACHE_CONTROL, "no-store")],
        ":root{--ksu-safe-area-inset-top:0px;--ksu-safe-area-inset-right:0px;--ksu-safe-area-inset-bottom:0px;--ksu-safe-area-inset-left:0px;}",
    )
}

async fn proxy_webui_root_asset_fallback(
    State(state): State<AppState>,
    method: Method,
    uri: Uri,
    headers: HeaderMap,
) -> Response<Body> {
    if method != Method::GET && method != Method::HEAD {
        return StatusCode::NOT_FOUND.into_response();
    }

    let Some((module_id, relative_path)) = fallback_webui_asset_target(
        uri.path(),
        headers.get("referer").and_then(|value| value.to_str().ok()),
    ) else {
        return StatusCode::NOT_FOUND.into_response();
    };

    let mut path = format!(
        "/api/v1/runtime/modules/{}/webui/files/{}",
        urlencoding::encode(module_id.trim()),
        relative_path
    );
    if let Some(query) = uri.query() {
        path.push('?');
        path.push_str(query);
    }

    match proxy_binary_response(&state, Method::GET, &path, HeaderMap::new(), None).await {
        Ok(response) => response,
        Err(error) => error.into_response(),
    }
}

async fn proxy_agent_json(
    state: &AppState,
    method: Method,
    path: &str,
    body: Option<Value>,
) -> Result<Value, ApiError> {
    proxy_agent_json_with_status(state, method, path, body).await
}

async fn proxy_agent_json_with_status(
    state: &AppState,
    method: Method,
    path: &str,
    body: Option<Value>,
) -> Result<Value, ApiError> {
    let base_url = state
        .base_agent_url()
        .map_err(|error| ApiError::service_unavailable(error.to_string()))?;
    let response = match method {
        Method::GET => state
            .inner
            .agent
            .request(
                &base_url,
                reqwest_method(method)?,
                path,
                &HeaderMap::new(),
                None,
            )
            .await
            .map_err(ApiError::from)?,
        Method::POST => state
            .inner
            .agent
            .post_json(&base_url, path, &body.unwrap_or_else(|| json!({})))
            .await
            .map_err(ApiError::from)?,
        other => return Err(ApiError::bad_request(format!("unsupported method {other}"))),
    };
    let status =
        StatusCode::from_u16(response.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let text = response
        .text()
        .await
        .context("failed to read agent response")?;
    let value = serde_json::from_str::<Value>(&text).unwrap_or_else(|_| json!({ "stdout": text }));
    if !status.is_success() && status != StatusCode::ACCEPTED {
        let message = value
            .get("error")
            .and_then(Value::as_str)
            .unwrap_or_else(|| text.trim());
        return Err(ApiError {
            status,
            message: message.to_string(),
        });
    }
    Ok(value)
}

async fn proxy_binary_response(
    state: &AppState,
    method: Method,
    path: &str,
    headers: HeaderMap,
    body: Option<Vec<u8>>,
) -> Result<Response<Body>, ApiError> {
    let base_url = state
        .base_agent_url()
        .map_err(|error| ApiError::service_unavailable(error.to_string()))?;
    let response = state
        .inner
        .agent
        .request(&base_url, reqwest_method(method)?, path, &headers, body)
        .await
        .map_err(ApiError::from)?;
    into_streaming_response(response).await
}

async fn into_streaming_response(response: reqwest::Response) -> Result<Response<Body>, ApiError> {
    let status =
        StatusCode::from_u16(response.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let headers = response.headers().clone();
    let stream = response
        .bytes_stream()
        .map_err(|error| std::io::Error::other(error.to_string()));
    let mut builder = Response::builder().status(status);
    if let Some(content_type) = headers.get(CONTENT_TYPE) {
        builder = builder.header(CONTENT_TYPE, content_type);
    }
    builder = builder.header(CACHE_CONTROL, HeaderValue::from_static("no-store"));
    Ok(builder.body(Body::from_stream(stream)).expect("response"))
}

async fn run_blocking_command(spec: crate::commands::CommandSpec) -> Result<String, ApiError> {
    tokio::task::spawn_blocking(move || run_command(&spec))
        .await
        .context("command join failure")
        .map_err(ApiError::from)?
        .map_err(ApiError::from)
}

async fn wait_for_agent(
    state: &AppState,
    base_url: &str,
    timeout: Duration,
) -> Result<(), ApiError> {
    let started = std::time::Instant::now();
    loop {
        match state.inner.agent.get_json(base_url, "/api/v1/health").await {
            Ok(_) => return Ok(()),
            Err(error) if started.elapsed() < timeout => {
                state.log(
                    "device.connect",
                    "info",
                    format!("waiting for phone agent: {error}"),
                );
                sleep(Duration::from_millis(500)).await;
            }
            Err(error) => {
                return Err(ApiError::service_unavailable(format!(
                    "phone agent did not become ready: {error}"
                )))
            }
        }
    }
}

async fn stage_host_file_if_needed(state: &AppState, value: &str) -> Result<String, ApiError> {
    let trimmed = value.trim();
    if trimmed.is_empty() {
        return Err(ApiError::bad_request("file path missing"));
    }
    let path = FsPath::new(trimmed);
    if !path.is_file() {
        return Ok(trimmed.to_string());
    }
    let connection = state.connection();
    if !connection.connected {
        return Err(ApiError::service_unavailable(
            "device service not connected",
        ));
    }
    let serial = connection.serial.unwrap_or_default();
    let file_name = path
        .file_name()
        .and_then(|value| value.to_str())
        .ok_or_else(|| ApiError::bad_request("file name missing"))?;
    let remote_path = format!("{SIDELOAD_DIR}/{file_name}");
    run_blocking_command(build_adb_shell_command(
        &serial,
        &format!("mkdir -p {SIDELOAD_DIR}"),
    ))
    .await?;
    run_blocking_command(build_adb_push_command(&serial, trimmed, &remote_path)).await?;
    state.log(
        "device.stage",
        "info",
        format!("pushed host file {} -> {remote_path}", path.display()),
    );
    Ok(remote_path)
}

async fn run_cli_json_command(parts: Vec<String>) -> Result<Value, ApiError> {
    let spec = crate::commands::build_cli_command_parts(&parts).map_err(ApiError::from)?;
    let output = run_blocking_command(spec).await?;
    parse_cli_json_output(&output)
}

fn parse_cli_json_output(output: &str) -> Result<Value, ApiError> {
    let trimmed = output.trim();
    if trimmed.is_empty() {
        return Err(ApiError::service_unavailable("cli returned empty output"));
    }
    if let Ok(value) = serde_json::from_str::<Value>(trimmed) {
        return Ok(value);
    }
    for line in trimmed.lines().rev() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        if let Ok(value) = serde_json::from_str::<Value>(line) {
            return Ok(value);
        }
    }
    Err(ApiError::service_unavailable(format!(
        "failed to parse cli json output\n{trimmed}"
    )))
}

async fn cli_client_id() -> Result<String, ApiError> {
    let config = read_cli_config_json().await?;
    Ok(config
        .get("client_id")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToString::to_string)
        .or_else(|| env::var("ABK_CLIENT_ID").ok())
        .unwrap_or_else(|| GITHUB_CLIENT_ID_FALLBACK.to_string()))
}

async fn read_cli_config_json() -> Result<Value, ApiError> {
    let path = cli_config_path()?;
    read_cli_config_json_from_path(&path)
}

fn read_cli_config_json_from_path(path: &PathBuf) -> Result<Value, ApiError> {
    if !path.is_file() {
        return Ok(json!({}));
    }
    let content =
        fs::read_to_string(&path).with_context(|| format!("failed to read {}", path.display()))?;
    serde_json::from_str(&content).map_err(ApiError::from)
}

async fn persist_cli_token(token: &str) -> Result<(), ApiError> {
    let path = cli_config_path()?;
    persist_cli_token_to_path(&path, token)
}

async fn clear_cli_token() -> Result<(), ApiError> {
    let path = cli_config_path()?;
    clear_cli_token_at_path(&path)
}

fn persist_cli_token_to_path(path: &PathBuf, token: &str) -> Result<(), ApiError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let mut config = read_cli_config_json_from_path(path)?;
    let object = config
        .as_object_mut()
        .ok_or_else(|| ApiError::service_unavailable("cli config is not a json object"))?;
    object.insert("token".into(), Value::String(token.to_string()));
    let content = serde_json::to_string_pretty(&config)?;
    fs::write(&path, content).with_context(|| format!("failed to write {}", path.display()))?;
    Ok(())
}

fn clear_cli_token_at_path(path: &PathBuf) -> Result<(), ApiError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .with_context(|| format!("failed to create {}", parent.display()))?;
    }
    let mut config = read_cli_config_json_from_path(path)?;
    let object = config
        .as_object_mut()
        .ok_or_else(|| ApiError::service_unavailable("cli config is not a json object"))?;
    object.remove("token");
    let content = serde_json::to_string_pretty(&config)?;
    fs::write(&path, content).with_context(|| format!("failed to write {}", path.display()))?;
    Ok(())
}

fn cli_config_path() -> Result<PathBuf, ApiError> {
    let home = env::var("HOME").map_err(|_| ApiError::service_unavailable("HOME is not set"))?;
    Ok(PathBuf::from(home).join(CLI_CONFIG_PATH_SUFFIX))
}

fn build_gki_cli_args(request: &BuildGkiRequest) -> Result<Vec<String>, ApiError> {
    let target = request.target.trim().to_lowercase();
    let valid_targets = ["a12", "a13", "a14", "a15", "a16", "custom"];
    if !valid_targets.contains(&target.as_str()) {
        return Err(ApiError::bad_request("unsupported GKI target"));
    }

    let mut args = vec!["--json".into(), "build".into(), "--force".into()];
    if target == "custom" {
        let sub_level = request
            .sub_level
            .clone()
            .filter(|value| !value.trim().is_empty())
            .ok_or_else(|| ApiError::bad_request("custom target requires subLevel"))?;
        let os_patch_level = request
            .os_patch_level
            .clone()
            .filter(|value| !value.trim().is_empty())
            .ok_or_else(|| ApiError::bad_request("custom target requires osPatchLevel"))?;
        args.extend(["--sub-level".into(), sub_level]);
        args.extend(["--os-patch-level".into(), os_patch_level]);
        if let Some(android_version) = request
            .android_version
            .clone()
            .filter(|value| !value.trim().is_empty())
        {
            args.extend(["--android-version".into(), android_version]);
        }
        if let Some(kernel_version) = request
            .kernel_version
            .clone()
            .filter(|value| !value.trim().is_empty())
        {
            args.extend(["--kernel-version".into(), kernel_version]);
        }
    } else {
        args.extend(["--matrix".into(), target]);
    }

    if let Some(value) = request
        .ksu_variant
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--ksu".into(), value]);
    }
    if let Some(value) = request
        .ksu_branch
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--ksu-branch".into(), value]);
    }
    if let Some(value) = request
        .version
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--version".into(), value]);
    }
    if let Some(value) = request
        .revision
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--revision".into(), value]);
    }
    if let Some(value) = request
        .custom_ref
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--custom-ref".into(), value]);
    }
    if let Some(value) = request
        .build_time
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--build-time".into(), value]);
    }
    if let Some(value) = request
        .custom_modules
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--custom-modules".into(), value]);
    }
    if let Some(value) = request
        .kpm_password
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--kpm-password".into(), value]);
    }
    if let Some(value) = request
        .virt
        .clone()
        .filter(|value| !value.trim().is_empty() && value.trim() != "off")
    {
        args.extend(["--virt".into(), value]);
    }
    if let Some(value) = request
        .zram_extra_algos
        .clone()
        .filter(|value| !value.trim().is_empty())
    {
        args.extend(["--zram-extra-algos".into(), value]);
    }

    args.push(if request.zram { "--zram" } else { "--no-zram" }.into());
    args.push(if request.bbg { "--bbg" } else { "--no-bbg" }.into());
    args.push(if request.ddk { "--ddk" } else { "--no-ddk" }.into());
    args.push(if request.kpm { "--kpm" } else { "--no-kpm" }.into());
    args.push(
        if request.susfs {
            "--susfs"
        } else {
            "--no-susfs"
        }
        .into(),
    );
    args.push(
        if request.rekernel {
            "--rekernel"
        } else {
            "--no-rekernel"
        }
        .into(),
    );
    if request.ntsync {
        args.push("--ntsync".into());
    }
    if request.networking {
        args.push("--networking".into());
    }
    if request.zram_full_algo {
        args.push("--zram-full-algo".into());
    }
    Ok(args)
}

fn parse_detected_devices(output: &str) -> Vec<DetectedDevice> {
    output
        .lines()
        .skip(1)
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter_map(|line| {
            let mut parts = line.split_whitespace();
            let serial = parts.next()?.to_string();
            let status = parts.next().unwrap_or_default().to_string();
            let detail = parts.collect::<Vec<_>>().join(" ");
            Some(DetectedDevice {
                serial,
                status,
                detail,
            })
        })
        .collect()
}

fn reconcile_connection_after_detect(connection: &mut ConnectionState, devices: &[DetectedDevice]) {
    if connection.connected {
        return;
    }

    let candidates = available_device_candidates(devices);
    let current_serial = connection
        .serial
        .as_deref()
        .map(str::trim)
        .filter(|serial| !serial.is_empty());
    let serial_still_present = current_serial
        .map(|serial| candidates.iter().any(|device| device.serial == serial))
        .unwrap_or(false);

    if candidates.is_empty() {
        connection.serial = None;
        connection.mode = ConnectionMode::Disconnected;
        return;
    }

    if !serial_still_present {
        connection.serial = None;
    }
}

fn available_device_candidates(devices: &[DetectedDevice]) -> Vec<&DetectedDevice> {
    devices
        .iter()
        .filter(|device| device.status.eq_ignore_ascii_case("device"))
        .collect()
}

fn resolve_connect_serial(
    requested: Option<&str>,
    connection: &ConnectionState,
) -> Result<String, ApiError> {
    let requested = requested.unwrap_or_default().trim();
    if !requested.is_empty() {
        return Ok(requested.to_string());
    }

    let devices = available_device_candidates(&connection.last_detected);
    match devices.as_slice() {
        [device] => Ok(device.serial.clone()),
        [] => Err(ApiError::bad_request(
            "no usable adb device detected; run detect first",
        )),
        _ => Err(ApiError::bad_request(
            "multiple adb devices detected; choose a serial explicitly",
        )),
    }
}

fn reqwest_method(method: Method) -> Result<reqwest::Method, ApiError> {
    reqwest::Method::from_bytes(method.as_str().as_bytes())
        .map_err(|error| ApiError::bad_request(error.to_string()))
}

fn extract_run_ids_from_status(value: &Value) -> HashSet<u64> {
    extract_runs_from_status(value)
        .into_iter()
        .filter_map(|run| extract_run_id(&run))
        .collect()
}

fn extract_runs_from_status(value: &Value) -> Vec<Value> {
    value
        .get("runs")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default()
}

fn extract_dispatch_workflow_names(value: &Value) -> Vec<String> {
    value
        .get("dispatches")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|dispatch| {
            dispatch
                .get("workflowName")
                .and_then(Value::as_str)
                .map(str::trim)
                .filter(|name| !name.is_empty())
                .map(ToString::to_string)
        })
        .collect()
}

fn select_dispatched_runs(
    runs: &[Value],
    baseline_run_ids: &HashSet<u64>,
    dispatch_workflow_names: &[String],
) -> Vec<Value> {
    let mut expected_by_name = HashMap::<String, usize>::new();
    for workflow_name in dispatch_workflow_names {
        *expected_by_name
            .entry(workflow_name.trim().to_ascii_lowercase())
            .or_insert(0) += 1;
    }

    let mut matched = Vec::new();
    for run in runs {
        let Some(run_id) = extract_run_id(run) else {
            continue;
        };
        if baseline_run_ids.contains(&run_id) {
            continue;
        }
        let run_name = run
            .get("name")
            .and_then(Value::as_str)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .unwrap_or_default()
            .to_ascii_lowercase();
        let Some(remaining) = expected_by_name.get_mut(&run_name) else {
            continue;
        };
        if *remaining == 0 {
            continue;
        }
        *remaining -= 1;
        matched.push(run.clone());
        if expected_by_name.values().all(|count| *count == 0) {
            break;
        }
    }
    matched
}

fn build_gki_tracking_output(
    base_output: &[String],
    tracked_runs: &[Value],
    phase: &str,
) -> Vec<String> {
    let mut lines = Vec::new();
    lines.push(format!("## {phase}"));
    lines.extend(base_output.iter().cloned());
    if !tracked_runs.is_empty() {
        lines.push("## tracked workflow runs".into());
        for run in tracked_runs {
            lines.push(build_run_tracking_line(run));
            if let Some(url) = run.get("htmlUrl").and_then(Value::as_str) {
                let url = url.trim();
                if !url.is_empty() {
                    lines.push(format!("  {url}"));
                }
            }
        }
    }
    lines
}

fn build_run_tracking_line(run: &Value) -> String {
    let run_id = extract_run_id(run)
        .map(|id| format!("#{id}"))
        .unwrap_or_else(|| "#?".into());
    let run_name = run
        .get("name")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("workflow");
    let status = run
        .get("status")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("unknown");
    let conclusion = run
        .get("conclusion")
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty());
    match conclusion {
        Some(conclusion) => format!("{run_id} {run_name} | {status} | {conclusion}"),
        None => format!("{run_id} {run_name} | {status}"),
    }
}

fn merge_build_tracking_result(
    dispatch_result: &Value,
    tracked_runs: &[Value],
    phase: &str,
) -> Value {
    let mut merged = dispatch_result.clone();
    let tracked_run_ids = tracked_runs
        .iter()
        .filter_map(extract_run_id)
        .map(Value::from)
        .collect::<Vec<_>>();
    if let Some(object) = merged.as_object_mut() {
        object.insert("trackingState".into(), Value::String(phase.into()));
        object.insert("trackedRuns".into(), Value::Array(tracked_runs.to_vec()));
        object.insert("runIds".into(), Value::Array(tracked_run_ids));
        object.insert(
            "completedRuns".into(),
            Value::from(
                tracked_runs
                    .iter()
                    .filter(|run| is_run_terminal(run))
                    .count() as u64,
            ),
        );
    }
    merged
}

fn extract_run_id(run: &Value) -> Option<u64> {
    run.get("id").and_then(Value::as_u64)
}

fn is_run_terminal(run: &Value) -> bool {
    run.get("status")
        .and_then(Value::as_str)
        .map(|status| status == "completed")
        .unwrap_or(false)
}

fn run_succeeded(run: &Value) -> bool {
    is_run_terminal(run)
        && run
            .get("conclusion")
            .and_then(Value::as_str)
            .map(|value| value == "success")
            .unwrap_or(false)
}

fn split_lines(value: &str) -> Vec<String> {
    value
        .lines()
        .map(str::trim_end)
        .filter(|line| !line.is_empty())
        .map(ToString::to_string)
        .collect()
}

fn guess_content_type(relative_path: &str) -> &'static str {
    MimeGuess::from_path(relative_path)
        .first_raw()
        .unwrap_or("application/octet-stream")
}

fn rewrite_module_webui_asset(
    module_id: &str,
    relative_path: &str,
    content_type: &str,
    bytes: &[u8],
) -> Vec<u8> {
    if !is_rewritable_webui_asset(content_type, relative_path) {
        return bytes.to_vec();
    }

    let base_prefix = format!(
        "/api/v1/runtime/modules/{}/webui/files/",
        urlencoding::encode(module_id.trim())
    );
    let mut text = String::from_utf8_lossy(bytes).into_owned();

    if is_html_content(content_type, relative_path) {
        text = inject_html_base_href(&text, &base_prefix);
    }

    rewrite_root_asset_paths(&text, &base_prefix).into_bytes()
}

fn is_rewritable_webui_asset(content_type: &str, relative_path: &str) -> bool {
    is_html_content(content_type, relative_path)
        || is_javascript_content(content_type, relative_path)
        || is_css_content(content_type, relative_path)
}

fn is_html_content(content_type: &str, relative_path: &str) -> bool {
    let lower = content_type.to_ascii_lowercase();
    lower.contains("text/html")
        || lower.contains("application/xhtml+xml")
        || relative_path.trim().is_empty()
        || relative_path.ends_with(".html")
        || relative_path.ends_with(".htm")
}

fn is_javascript_content(content_type: &str, relative_path: &str) -> bool {
    let lower = content_type.to_ascii_lowercase();
    lower.contains("javascript")
        || lower.contains("ecmascript")
        || relative_path.ends_with(".js")
        || relative_path.ends_with(".mjs")
}

fn is_css_content(content_type: &str, relative_path: &str) -> bool {
    content_type.to_ascii_lowercase().contains("text/css") || relative_path.ends_with(".css")
}

fn inject_html_base_href(html: &str, base_prefix: &str) -> String {
    if html.to_ascii_lowercase().contains("<base ") {
        return html.to_string();
    }

    let base_tag = format!(r#"<base href="{base_prefix}">"#);
    if let Some(index) = html.to_ascii_lowercase().find("</head>") {
        let mut output = String::with_capacity(html.len() + base_tag.len());
        output.push_str(&html[..index]);
        output.push_str(&base_tag);
        output.push_str(&html[index..]);
        return output;
    }

    format!("{base_tag}{html}")
}

fn rewrite_root_asset_paths(text: &str, base_prefix: &str) -> String {
    let asset_prefix = format!("{base_prefix}assets/");
    text.replace("\"/assets/", &format!("\"{asset_prefix}"))
        .replace("'/assets/", &format!("'{asset_prefix}"))
        .replace("url(/assets/", &format!("url({asset_prefix}"))
}

fn fallback_webui_asset_target(
    request_path: &str,
    referer: Option<&str>,
) -> Option<(String, String)> {
    let relative_path = request_path.trim().trim_start_matches('/').trim();
    if relative_path.is_empty()
        || relative_path.starts_with("api/")
        || relative_path.starts_with("internal/")
    {
        return None;
    }

    let referer = referer?.trim();
    if referer.is_empty() {
        return None;
    }
    let referer_uri = Uri::try_from(referer).ok()?;
    let segments = referer_uri
        .path()
        .trim_start_matches('/')
        .split('/')
        .collect::<Vec<_>>();
    if segments.len() < 7
        || segments[0] != "api"
        || segments[1] != "v1"
        || segments[2] != "runtime"
        || segments[3] != "modules"
        || segments[5] != "webui"
        || segments[6] != "files"
    {
        return None;
    }

    let module_id = urlencoding::decode(segments[4]).ok()?.into_owned();
    if module_id.trim().is_empty() {
        return None;
    }

    Some((module_id, relative_path.to_string()))
}

fn forward_headers(headers: &HeaderMap) -> HeaderMap {
    let mut forwarded = HeaderMap::new();
    for (name, value) in headers {
        let skip = matches!(
            name.as_str().to_ascii_lowercase().as_str(),
            "host" | "connection" | "content-length" | "accept-encoding" | "origin" | "referer"
        );
        if !skip {
            forwarded.insert(name, value.clone());
        }
    }
    forwarded
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_adb_detect_output() {
        let devices = parse_detected_devices(
            "List of devices attached\nABC123 device product:foo model:bar device:baz\n",
        );
        assert_eq!(devices.len(), 1);
        assert_eq!(devices[0].serial, "ABC123");
        assert_eq!(devices[0].status, "device");
    }

    #[test]
    fn clears_disconnected_serial_when_no_devices_are_detected() {
        let mut connection = ConnectionState {
            serial: Some("ABC123".into()),
            mode: ConnectionMode::AdbFallback,
            ..ConnectionState::default()
        };

        reconcile_connection_after_detect(&mut connection, &[]);

        assert_eq!(connection.serial, None);
        assert_eq!(connection.mode, ConnectionMode::Disconnected);
    }

    #[test]
    fn clears_disconnected_serial_when_previous_device_is_gone() {
        let mut connection = ConnectionState {
            serial: Some("ABC123".into()),
            mode: ConnectionMode::Disconnected,
            ..ConnectionState::default()
        };
        let devices = vec![DetectedDevice {
            serial: "XYZ789".into(),
            status: "device".into(),
            detail: String::new(),
        }];

        reconcile_connection_after_detect(&mut connection, &devices);

        assert_eq!(connection.serial, None);
        assert_eq!(connection.mode, ConnectionMode::Disconnected);
    }

    #[test]
    fn resolves_single_detected_serial_when_request_missing() {
        let connection = ConnectionState {
            last_detected: vec![DetectedDevice {
                serial: "ABC123".into(),
                status: "device".into(),
                detail: "product:foo".into(),
            }],
            ..ConnectionState::default()
        };

        let serial = resolve_connect_serial(None, &connection).unwrap();
        assert_eq!(serial, "ABC123");
    }

    #[test]
    fn rejects_ambiguous_detected_serials() {
        let connection = ConnectionState {
            last_detected: vec![
                DetectedDevice {
                    serial: "ABC123".into(),
                    status: "device".into(),
                    detail: String::new(),
                },
                DetectedDevice {
                    serial: "XYZ789".into(),
                    status: "device".into(),
                    detail: String::new(),
                },
            ],
            ..ConnectionState::default()
        };

        let error = resolve_connect_serial(None, &connection).unwrap_err();
        assert_eq!(error.status, StatusCode::BAD_REQUEST);
        assert!(error.message.contains("multiple adb devices"));
    }

    #[test]
    fn persists_cli_token_to_config_path() {
        let temp_root = std::env::temp_dir().join(format!("abk-test-{}", Uuid::new_v4()));
        let config_path = temp_root.join("config.json");

        persist_cli_token_to_path(&config_path, "token-123").unwrap();
        let config = read_cli_config_json_from_path(&config_path).unwrap();

        assert_eq!(
            config.get("token").and_then(Value::as_str),
            Some("token-123")
        );

        fs::remove_dir_all(temp_root).ok();
    }

    #[test]
    fn select_dispatched_runs_ignores_baseline_ids() {
        let runs = vec![
            json!({
                "id": 101_u64,
                "name": "自定义内核构建",
                "status": "queued",
            }),
            json!({
                "id": 100_u64,
                "name": "自定义内核构建",
                "status": "completed",
                "conclusion": "success",
            }),
        ];
        let baseline = HashSet::from([100_u64]);

        let matched = select_dispatched_runs(&runs, &baseline, &["自定义内核构建".into()]);

        assert_eq!(matched.len(), 1);
        assert_eq!(extract_run_id(&matched[0]), Some(101));
    }

    #[test]
    fn select_dispatched_runs_respects_duplicate_workflow_names() {
        let runs = vec![
            json!({
                "id": 203_u64,
                "name": "Matrix Build",
                "status": "queued",
            }),
            json!({
                "id": 202_u64,
                "name": "Matrix Build",
                "status": "in_progress",
            }),
            json!({
                "id": 201_u64,
                "name": "Other Build",
                "status": "queued",
            }),
        ];

        let matched = select_dispatched_runs(
            &runs,
            &HashSet::new(),
            &["Matrix Build".into(), "Matrix Build".into()],
        );

        assert_eq!(matched.len(), 2);
        assert_eq!(extract_run_id(&matched[0]), Some(203));
        assert_eq!(extract_run_id(&matched[1]), Some(202));
    }

    #[test]
    fn rewrites_module_webui_html_assets_with_base_prefix() {
        let rewritten = rewrite_module_webui_asset(
            "abi_bridge",
            "",
            "text/html; charset=utf-8",
            br#"<!doctype html><html><head><script type="module" src="/assets/index.js"></script><link rel="stylesheet" href="assets/index.css"></head><body></body></html>"#,
        );
        let html = String::from_utf8(rewritten).unwrap();

        assert!(html.contains(r#"<base href="/api/v1/runtime/modules/abi_bridge/webui/files/">"#));
        assert!(html
            .contains(r#"src="/api/v1/runtime/modules/abi_bridge/webui/files/assets/index.js""#));
        assert!(html.contains(r#"href="assets/index.css""#));
    }

    #[test]
    fn rewrites_module_webui_javascript_root_asset_paths() {
        let rewritten = rewrite_module_webui_asset(
            "abi_bridge",
            "assets/index.js",
            "application/javascript",
            br#"const a="/assets/index.css";const b='/assets/fallback.js';"#,
        );
        let script = String::from_utf8(rewritten).unwrap();

        assert!(
            script.contains(r#""/api/v1/runtime/modules/abi_bridge/webui/files/assets/index.css""#)
        );
        assert!(script
            .contains(r#"'/api/v1/runtime/modules/abi_bridge/webui/files/assets/fallback.js'"#));
    }

    #[test]
    fn resolves_root_asset_fallback_from_webui_referer() {
        let resolved = fallback_webui_asset_target(
            "/index-BphXklzb.js",
            Some("http://127.0.0.1:38765/api/v1/runtime/modules/abi_bridge/webui/files"),
        );

        assert_eq!(
            resolved,
            Some(("abi_bridge".into(), "index-BphXklzb.js".into()))
        );
    }

    #[test]
    fn ignores_non_webui_referer_for_root_asset_fallback() {
        let resolved =
            fallback_webui_asset_target("/index-BphXklzb.js", Some("http://127.0.0.1:38765/home"));

        assert_eq!(resolved, None);
    }
}
