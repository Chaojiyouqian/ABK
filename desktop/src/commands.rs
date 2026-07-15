use anyhow::{anyhow, Context, Result};
use std::path::PathBuf;
use std::process::Command;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommandSpec {
    pub program: String,
    pub args: Vec<String>,
    pub cwd: PathBuf,
}

impl CommandSpec {
    pub fn display(&self) -> String {
        std::iter::once(self.program.as_str())
            .chain(self.args.iter().map(String::as_str))
            .collect::<Vec<_>>()
            .join(" ")
    }
}

pub fn repo_root() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("desktop lives under repo root")
        .to_path_buf()
}

pub fn build_cli_command(raw_args: &str) -> Result<CommandSpec> {
    let parsed = shell_words::split(raw_args).context("failed to parse CLI args")?;
    if parsed.is_empty() {
        return Err(anyhow!("CLI args are empty"));
    }
    build_cli_command_parts(&parsed)
}

pub fn build_cli_command_parts(parts: &[String]) -> Result<CommandSpec> {
    if parts.is_empty() {
        return Err(anyhow!("CLI args are empty"));
    }
    let script = repo_root().join("cli").join("abk.py");
    Ok(CommandSpec {
        program: "python3".into(),
        args: std::iter::once(script.to_string_lossy().to_string())
            .chain(parts.iter().cloned())
            .collect(),
        cwd: repo_root(),
    })
}

pub fn build_adb_detect_command() -> CommandSpec {
    CommandSpec {
        program: "adb".into(),
        args: vec!["devices".into(), "-l".into()],
        cwd: repo_root(),
    }
}

pub fn build_adb_forward_command(serial: &str, port: u16) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "forward".into(),
        format!("tcp:{port}"),
        format!("tcp:{port}"),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
    }
}

pub fn build_adb_remove_forward_command(serial: &str, port: u16) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend(["forward".into(), "--remove".into(), format!("tcp:{port}")]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
    }
}

pub fn build_adb_start_agent_command(serial: &str, port: u16) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "shell".into(),
        "am".into(),
        "start-foreground-service".into(),
        "-a".into(),
        "com.abk.kernel.agent.START".into(),
        "-n".into(),
        "com.abk.kernel/.agent.AbkAgentService".into(),
        "--es".into(),
        "host".into(),
        "127.0.0.1".into(),
        "--ei".into(),
        "port".into(),
        port.to_string(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
    }
}

pub fn build_adb_stop_agent_command(serial: &str) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "shell".into(),
        "am".into(),
        "startservice".into(),
        "-a".into(),
        "com.abk.kernel.agent.STOP".into(),
        "-n".into(),
        "com.abk.kernel/.agent.AbkAgentService".into(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
    }
}

pub fn build_adb_push_command(serial: &str, local_path: &str, remote_path: &str) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "push".into(),
        local_path.to_string(),
        remote_path.to_string(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
    }
}

pub fn build_adb_shell_command(serial: &str, script: &str) -> CommandSpec {
    let mut args = adb_prefix(serial);
    args.extend([
        "shell".into(),
        "sh".into(),
        "-lc".into(),
        script.to_string(),
    ]);
    CommandSpec {
        program: "adb".into(),
        args,
        cwd: repo_root(),
    }
}

pub fn run_command(spec: &CommandSpec) -> Result<String> {
    let output = Command::new(&spec.program)
        .args(&spec.args)
        .current_dir(&spec.cwd)
        .output()
        .with_context(|| format!("failed to execute {}", spec.display()))?;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let combined = match (stdout.trim(), stderr.trim()) {
        ("", "") => String::new(),
        ("", err) => err.to_string(),
        (out, "") => out.to_string(),
        (out, err) => format!("{out}\n{err}"),
    };
    if output.status.success() {
        Ok(combined)
    } else {
        Err(anyhow!(
            "command failed ({})\n{}",
            output.status,
            combined.trim()
        ))
    }
}

fn adb_prefix(serial: &str) -> Vec<String> {
    let serial = serial.trim();
    if serial.is_empty() {
        Vec::new()
    } else {
        vec!["-s".into(), serial.into()]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builds_cli_command() {
        let spec = build_cli_command("status --run-id 42").unwrap();
        assert_eq!(spec.program, "python3");
        assert!(spec.args[0].ends_with("cli/abk.py"));
        assert_eq!(&spec.args[1..], ["status", "--run-id", "42"]);
    }

    #[test]
    fn builds_start_agent_command_with_serial() {
        let spec = build_adb_start_agent_command("ABC123", 48765);
        assert_eq!(spec.program, "adb");
        assert_eq!(spec.args[0], "-s");
        assert!(spec
            .args
            .contains(&"com.abk.kernel/.agent.AbkAgentService".into()));
        assert!(spec.args.contains(&"48765".into()));
    }

    #[test]
    fn builds_push_command() {
        let spec =
            build_adb_push_command("ABC123", "/tmp/module.zip", "/data/local/tmp/module.zip");
        assert_eq!(spec.args[0], "-s");
        assert_eq!(spec.args[2], "push");
        assert_eq!(spec.args[3], "/tmp/module.zip");
        assert_eq!(spec.args[4], "/data/local/tmp/module.zip");
    }
}
