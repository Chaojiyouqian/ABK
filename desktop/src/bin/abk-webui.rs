use anyhow::{anyhow, Context, Result};
use serde_json::to_string;
use tao::event::{Event, WindowEvent};
use tao::event_loop::{ControlFlow, EventLoopBuilder};
use tao::window::WindowBuilder;
use urlencoding::encode;
use wry::{NewWindowResponse, WebViewBuilder};

fn main() {
    if let Err(error) = run() {
        eprintln!("{error:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let args = parse_args(std::env::args().skip(1).collect())?;
    run_module_webui_window(args.port, &args.module_id, &args.module_name)
}

fn run_module_webui_window(port: u16, module_id: &str, module_name: &str) -> Result<()> {
    if module_id.trim().is_empty() {
        return Err(anyhow!("module id missing for WebUI"));
    }

    let encoded_id = encode(module_id.trim());
    let bridge_base = format!("http://127.0.0.1:{port}/api/v1/runtime/modules/{encoded_id}/webui");
    let page_url = format!("{bridge_base}/files");
    let init_script =
        build_ksu_bridge_script(&bridge_base).context("failed to build module WebUI bridge")?;
    let title = if module_name.trim().is_empty() {
        format!("Module WebUI · {module_id}")
    } else {
        format!("Module WebUI · {module_name}")
    };

    let event_loop = EventLoopBuilder::<WebUiUserEvent>::with_user_event().build();
    let proxy = event_loop.create_proxy();
    let window = WindowBuilder::new()
        .with_title(title)
        .with_inner_size(tao::dpi::LogicalSize::new(1180.0, 840.0))
        .build(&event_loop)
        .context("failed to create WebUI window")?;

    let builder = WebViewBuilder::new()
        .with_url(page_url)
        .with_initialization_script(init_script)
        .with_new_window_req_handler(|_, _| NewWindowResponse::Deny)
        .with_ipc_handler(move |request| {
            let message = request.body();
            if message == "close" {
                let _ = proxy.send_event(WebUiUserEvent::Close);
            } else if let Some(text) = message.strip_prefix("toast:") {
                let _ = proxy.send_event(WebUiUserEvent::Toast(text.to_string()));
            }
        });

    #[cfg(target_os = "linux")]
    let _webview = {
        use tao::platform::unix::WindowExtUnix;
        use wry::WebViewBuilderExtUnix;

        let vbox = window
            .default_vbox()
            .ok_or_else(|| anyhow!("failed to acquire linux window container"))?;
        builder
            .build_gtk(vbox)
            .context("failed to create linux WebUI webview")?
    };

    #[cfg(not(target_os = "linux"))]
    let _webview = builder
        .build(&window)
        .context("failed to create WebUI webview")?;

    event_loop.run(move |event, _, control_flow| {
        *control_flow = ControlFlow::Wait;

        match event {
            Event::UserEvent(WebUiUserEvent::Close) => {
                *control_flow = ControlFlow::Exit;
            }
            Event::UserEvent(WebUiUserEvent::Toast(message)) => {
                eprintln!("module webui toast: {message}");
            }
            Event::WindowEvent {
                event: WindowEvent::CloseRequested,
                ..
            } => {
                *control_flow = ControlFlow::Exit;
            }
            _ => {}
        }
    });
}

fn parse_args(args: Vec<String>) -> Result<CliArgs> {
    let mut port = None;
    let mut module_id = None;
    let mut module_name = String::new();
    let mut index = 0;
    while index < args.len() {
        let key = args[index].as_str();
        let next = args.get(index + 1).cloned();
        match key {
            "--port" => {
                let value = next.ok_or_else(|| anyhow!("--port requires a value"))?;
                port = value.parse::<u16>().ok();
                index += 2;
            }
            "--module-id" => {
                module_id = Some(next.ok_or_else(|| anyhow!("--module-id requires a value"))?);
                index += 2;
            }
            "--module-name" => {
                module_name = next.ok_or_else(|| anyhow!("--module-name requires a value"))?;
                index += 2;
            }
            other => {
                return Err(anyhow!("unexpected argument: {other}"));
            }
        }
    }
    Ok(CliArgs {
        port: port.unwrap_or(48765),
        module_id: module_id
            .map(|value| value.trim().to_string())
            .filter(|value| !value.is_empty())
            .ok_or_else(|| anyhow!("--module-id is required"))?,
        module_name: module_name.trim().to_string(),
    })
}

fn build_ksu_bridge_script(bridge_base: &str) -> Result<String> {
    let base = to_string(bridge_base)?;
    Ok(format!(
        r#"
(() => {{
  if (window.ksu && window.ksu.__abkDesktopBridge) {{
    return;
  }}

  const bridgeBase = {base};

  function parseJson(text) {{
    if (!text) return {{}};
    try {{
      return JSON.parse(text);
    }} catch (_error) {{
      return {{ stdout: text, output: [text] }};
    }}
  }}

  function syncRequest(method, path, body) {{
    const xhr = new XMLHttpRequest();
    xhr.open(method, bridgeBase + path, false);
    if (body !== undefined) {{
      xhr.setRequestHeader("Content-Type", "application/json");
    }}
    try {{
      xhr.send(body === undefined ? null : JSON.stringify(body));
    }} catch (error) {{
      const text = String(error);
      return {{ success: false, code: 1, stdout: text, output: [text] }};
    }}
    const payload = parseJson(xhr.responseText || "");
    if (xhr.status >= 200 && xhr.status < 300) {{
      return payload;
    }}
    const text = payload.error || payload.stdout || xhr.responseText || `HTTP ${{
      xhr.status
    }}`;
    return {{ success: false, code: 1, stdout: text, output: [text] }};
  }}

  async function asyncRequest(method, path, body) {{
    const response = await fetch(bridgeBase + path, {{
      method,
      headers: body === undefined ? undefined : {{ "Content-Type": "application/json" }},
      body: body === undefined ? undefined : JSON.stringify(body),
    }});
    const text = await response.text();
    const payload = parseJson(text);
    if (!response.ok) {{
      throw new Error(payload.error || payload.stdout || text || `HTTP ${{response.status}}`);
    }}
    return payload;
  }}

  function resolveCallback(callbackRef) {{
    if (typeof callbackRef === "function") {{
      return callbackRef;
    }}
    if (typeof callbackRef !== "string" || !callbackRef.trim()) {{
      return null;
    }}
    let current = window;
    for (const segment of callbackRef.split(".")) {{
      if (!segment || current == null) {{
        current = null;
        break;
      }}
      current = current[segment];
    }}
    if (typeof current === "function") {{
      return current;
    }}
    try {{
      const evaluated = globalThis.eval(callbackRef);
      return typeof evaluated === "function" ? evaluated : null;
    }} catch (_error) {{
      return null;
    }}
  }}

  function normalizeOutput(payload) {{
    if (typeof payload.stdout === "string") {{
      return payload.stdout;
    }}
    if (Array.isArray(payload.output)) {{
      return payload.output.join("\\n");
    }}
    return "";
  }}

  function exec(command, optionsOrCallback, maybeCallback) {{
    const callback =
      maybeCallback === undefined
        ? resolveCallback(optionsOrCallback)
        : resolveCallback(maybeCallback);
    const options =
      callback && maybeCallback === undefined ? undefined : optionsOrCallback;

    if (!callback) {{
      const payload = syncRequest("POST", "/exec", {{ command, options }});
      return normalizeOutput(payload);
    }}

    asyncRequest("POST", "/exec", {{ command, options }})
      .then((payload) => callback(payload.code ?? 0, normalizeOutput(payload), ""))
      .catch((error) => callback(1, String(error), ""));
  }}

  function spawn(command, args, options, callbackRef) {{
    const callback = resolveCallback(callbackRef);
    asyncRequest("POST", "/spawn", {{
      command,
      args: Array.isArray(args) ? args : [],
      options,
    }})
      .then((payload) => {{
        const output = normalizeOutput(payload);
        if (callback && callback.stdout && typeof callback.stdout.emit === "function") {{
          callback.stdout.emit("data", output);
        }}
        if (callback && typeof callback.emit === "function") {{
          callback.emit("exit", payload.code ?? 0);
        }}
      }})
      .catch((error) => {{
        const text = String(error);
        if (callback && callback.stdout && typeof callback.stdout.emit === "function") {{
          callback.stdout.emit("data", text);
        }}
        if (callback && typeof callback.emit === "function") {{
          callback.emit("exit", 1);
        }}
      }});
  }}

  function moduleInfo() {{
    const payload = syncRequest("GET", "/module-info");
    return payload.raw || JSON.stringify(payload.info || {{}});
  }}

  window.ksu = {{
    __abkDesktopBridge: true,
    exec,
    spawn,
    toast(message) {{
      if (window.ipc && typeof window.ipc.postMessage === "function") {{
        window.ipc.postMessage(`toast:${{String(message)}}`);
      }} else {{
        console.info(message);
      }}
    }},
    moduleInfo,
    exit() {{
      if (window.ipc && typeof window.ipc.postMessage === "function") {{
        window.ipc.postMessage("close");
      }} else {{
        window.close();
      }}
    }},
  }};
}})();
"#,
        base = base
    ))
}

#[derive(Debug, Clone)]
enum WebUiUserEvent {
    Close,
    Toast(String),
}

#[derive(Debug, Clone)]
struct CliArgs {
    port: u16,
    module_id: String,
    module_name: String,
}
