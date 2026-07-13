use anyhow::{anyhow, Result};
use gtk3::prelude::*;
use serde_json::to_string;
use serde_json::{json, Value};
use urlencoding::encode;
use webkit2gtk::{
    LoadEvent, ScriptDialogType, SettingsExt, UserContentInjectedFrames, UserContentManager,
    UserContentManagerExt, UserScript, UserScriptInjectionTime, WebView, WebViewExt,
};

fn main() {
    if let Err(error) = run() {
        eprintln!("{error:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let args = parse_args(std::env::args().skip(1).collect())?;
    configure_linux_webview_env();
    gtk3::init().map_err(|error| anyhow!("failed to initialize GTK: {error}"))?;
    run_module_webui_window(args.port, &args.module_id, &args.module_name)
}

fn run_module_webui_window(port: u16, module_id: &str, module_name: &str) -> Result<()> {
    if module_id.trim().is_empty() {
        return Err(anyhow!("module id missing for WebUI"));
    }

    let encoded_id = encode(module_id.trim());
    let bridge_base = format!("http://127.0.0.1:{port}/api/v1/runtime/modules/{encoded_id}/webui");
    let page_base_url = format!("{bridge_base}/files/");
    let page_url = format!("{page_base_url}index.html");
    let title = if module_name.trim().is_empty() {
        format!("Module WebUI · {module_id}")
    } else {
        format!("Module WebUI · {module_name}")
    };

    let window = gtk3::Window::new(gtk3::WindowType::Toplevel);
    window.set_title(&title);
    window.set_default_size(1180, 840);
    window.connect_delete_event(|_, _| {
        gtk3::main_quit();
        gtk3::glib::Propagation::Proceed
    });

    let manager = UserContentManager::new();
    manager.add_script(&UserScript::new(
        &build_ksu_bridge_script(&bridge_base)?,
        UserContentInjectedFrames::TopFrame,
        UserScriptInjectionTime::Start,
        &[],
        &[],
    ));

    let webview = WebView::with_user_content_manager(&manager);
    if let Some(settings) = WebViewExt::settings(&webview) {
        settings.set_enable_write_console_messages_to_stdout(true);
        settings.set_javascript_can_open_windows_automatically(false);
    }

    {
        let module_id = module_id.to_string();
        webview.connect_script_dialog(move |_view, dialog| {
            if dialog.dialog_type() != ScriptDialogType::Prompt {
                return false;
            }
            let Some(message) = dialog.message().map(|value| value.to_string()) else {
                return false;
            };
            let Some(method) = message.strip_prefix("__abk__:") else {
                return false;
            };
            let payload = dialog
                .prompt_get_default_text()
                .map(|value| value.to_string())
                .unwrap_or_default();
            let response = handle_sync_bridge_call(port, &module_id, method, &payload)
                .unwrap_or_else(|error| error.to_string());
            dialog.prompt_set_text(&response);
            true
        });
    }

    {
        let page_base_url = page_base_url.clone();
        webview.connect_load_failed(move |view, event, uri, error| {
            if event == LoadEvent::Finished {
                return false;
            }
            view.load_html(
                &render_error_page(
                    "Module WebUI load failed",
                    &format!("{uri}\n\n{}", error.message()),
                ),
                Some(&page_base_url),
            );
            true
        });
    }

    webview.connect_web_process_terminated(|view, reason| {
        view.load_html(
            &render_error_page(
                "WebKit process terminated",
                &format!("The embedded WebKit process terminated: {reason:?}"),
            ),
            None,
        );
    });

    window.add(&webview);
    window.show_all();
    webview.load_uri(&page_url);
    gtk3::main();
    Ok(())
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
  const rootBase = new URL(bridgeBase).origin;
  const packageIconBase = rootBase + "/api/v1/root-grants/";
  let moduleInfoCache = null;

  function buildUrl(path) {{
    if (typeof path === "string" && /^(https?:)?\/\//.test(path)) {{
      return path;
    }}
    if (typeof path === "string" && (
      path === "/exec" ||
      path === "/spawn" ||
      path === "/module-info"
    )) {{
      return bridgeBase + path;
    }}
    if (typeof path === "string" && path.startsWith("/")) {{
      return rootBase + path;
    }}
    return bridgeBase + path;
  }}

  function normalizeUrlValue(input) {{
    if (typeof input === "string") {{
      return input;
    }}
    if (input && typeof input.url === "string") {{
      return input.url;
    }}
    return String(input ?? "");
  }}

  function isPhoneLocalhostUrl(value) {{
    try {{
      const resolved = new URL(normalizeUrlValue(value), window.location.href);
      return resolved.hostname === "127.0.0.1" ||
        resolved.hostname === "localhost" ||
        resolved.hostname === "0.0.0.0" ||
        resolved.hostname === "::1" ||
        resolved.hostname === "[::1]";
    }} catch (_error) {{
      return false;
    }}
  }}

  function proxyLocalhostUrl(value) {{
    const resolved = new URL(normalizeUrlValue(value), window.location.href);
    return (
      rootBase +
      "/api/v1/runtime/modules/" +
      encodeURIComponent(moduleInfoObject().id || "") +
      "/webui/http-proxy?target=" +
      encodeURIComponent(resolved.toString())
    );
  }}

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
    xhr.open(method, buildUrl(path), false);
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
    const response = await fetch(buildUrl(path), {{
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

  function syncNative(method, payload) {{
    try {{
      return window.prompt(`__abk__:${{method}}`, JSON.stringify(payload ?? null)) ?? "";
    }} catch (_error) {{
      return "";
    }}
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
      return syncNative("exec", {{ command, options }});
    }}

    asyncRequest("POST", "/exec", {{ command, options }})
      .then((payload) => {{
        const output = normalizeOutput(payload);
        const isOk = payload && payload.success !== false && (payload.code ?? 0) === 0;
        callback(
          payload.code ?? (isOk ? 0 : 1),
          isOk ? output : "",
          isOk ? "" : (payload.stderr || output || "command failed")
        );
      }})
      .catch((error) => callback(1, "", String(error)));
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
    if (moduleInfoCache !== null) {{
      return moduleInfoCache;
    }}
    moduleInfoCache = syncNative("moduleInfo");
    return moduleInfoCache;
  }}

  function moduleInfoObject() {{
    try {{
      return JSON.parse(moduleInfo());
    }} catch (_error) {{
      return {{}};
    }}
  }}

  function fullScreen(enabled) {{
    try {{
      if (enabled) {{
        const result = document.documentElement.requestFullscreen?.();
        if (result && typeof result.catch === "function") {{
          result.catch(() => {{}});
        }}
      }} else {{
        const result = document.exitFullscreen?.();
        if (result && typeof result.catch === "function") {{
          result.catch(() => {{}});
        }}
      }}
    }} catch (_error) {{
    }}
  }}

  function enableEdgeToEdge(_enabled) {{
  }}

  function listPackages(type) {{
    return syncNative("listPackages", {{ type: type || "all" }});
  }}

  function getPackagesInfo(packages) {{
    let values = packages;
    if (typeof values === "string") {{
      try {{
        values = JSON.parse(values);
      }} catch (_error) {{
        values = [];
      }}
    }}
    return syncNative("getPackagesInfo", {{
      packages: Array.isArray(values) ? values : [],
    }});
  }}

  const originalFetch = window.fetch?.bind(window);
  if (originalFetch) {{
    window.fetch = function(input, init) {{
      if (isPhoneLocalhostUrl(input)) {{
        return originalFetch(proxyLocalhostUrl(input), init);
      }}
      return originalFetch(input, init);
    }};
  }}

  const OriginalXHR = window.XMLHttpRequest;
  if (OriginalXHR) {{
    window.XMLHttpRequest = class extends OriginalXHR {{
      open(method, url, ...rest) {{
        const nextUrl = isPhoneLocalhostUrl(url) ? proxyLocalhostUrl(url) : url;
        return super.open(method, nextUrl, ...rest);
      }}
    }};
  }}

  const OriginalEventSource = window.EventSource;
  if (OriginalEventSource) {{
    window.EventSource = class extends OriginalEventSource {{
      constructor(url, options) {{
        super(isPhoneLocalhostUrl(url) ? proxyLocalhostUrl(url) : url, options);
      }}
    }};
  }}

  function rewriteKsuIconValue(value) {{
    if (typeof value !== "string") {{
      return value;
    }}
    if (!value.startsWith("ksu://icon/")) {{
      return value;
    }}
    const packageName = value.slice("ksu://icon/".length);
    return packageIconBase + encodeURIComponent(packageName) + "/icon";
  }}

  function rewriteKsuIconNodes(root) {{
    if (!root || !root.querySelectorAll) {{
      return;
    }}
    root.querySelectorAll("[src],[href]").forEach((node) => {{
      if (node.hasAttribute("src")) {{
        const next = rewriteKsuIconValue(node.getAttribute("src"));
        if (next !== node.getAttribute("src")) {{
          node.setAttribute("src", next);
        }}
      }}
      if (node.hasAttribute("href")) {{
        const next = rewriteKsuIconValue(node.getAttribute("href"));
        if (next !== node.getAttribute("href")) {{
          node.setAttribute("href", next);
        }}
      }}
    }});
  }}

  const observer = new MutationObserver((mutations) => {{
    for (const mutation of mutations) {{
      if (mutation.type === "attributes" && mutation.target) {{
        const target = mutation.target;
        if (mutation.attributeName === "src") {{
          const next = rewriteKsuIconValue(target.getAttribute("src"));
          if (next !== target.getAttribute("src")) {{
            target.setAttribute("src", next);
          }}
        }}
        if (mutation.attributeName === "href") {{
          const next = rewriteKsuIconValue(target.getAttribute("href"));
          if (next !== target.getAttribute("href")) {{
            target.setAttribute("href", next);
          }}
        }}
      }}
      mutation.addedNodes?.forEach?.((node) => rewriteKsuIconNodes(node));
    }}
  }});

  document.addEventListener("DOMContentLoaded", () => {{
    rewriteKsuIconNodes(document);
    observer.observe(document.documentElement, {{
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["src", "href"],
    }});
  }});

        window.ksu = {{
    __abkDesktopBridge: true,
    exec,
    spawn,
    fullScreen,
    enableEdgeToEdge,
    toast(message) {{
      console.info(String(message));
    }},
    moduleInfo,
    listPackages,
    getPackagesInfo,
    exit() {{
      window.close();
    }},
  }};
}})();
"#,
        base = base
    ))
}

fn render_error_page(title: &str, body: &str) -> String {
    format!(
        r#"<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
    <title>{title}</title>
    <style>
      body {{
        margin: 0;
        padding: 32px;
        font-family: sans-serif;
        background: #f5f1e8;
        color: #1d1b16;
      }}
      main {{
        max-width: 920px;
        margin: 0 auto;
        background: #fffdf8;
        border: 1px solid #d8d0c2;
        border-radius: 18px;
        padding: 24px;
      }}
      h1 {{
        margin-top: 0;
      }}
      pre {{
        white-space: pre-wrap;
        background: #f0ebe1;
        border-radius: 12px;
        padding: 16px;
      }}
    </style>
  </head>
  <body>
    <main>
      <h1>{title}</h1>
      <pre>{body}</pre>
    </main>
  </body>
</html>"#,
        title = html_escape(title),
        body = html_escape(body),
    )
}

fn html_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
}

fn handle_sync_bridge_call(
    port: u16,
    module_id: &str,
    method: &str,
    payload_raw: &str,
) -> Result<String> {
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(20))
        .build()?;
    let encoded_module_id = encode(module_id);
    let bridge_base =
        format!("http://127.0.0.1:{port}/api/v1/runtime/modules/{encoded_module_id}/webui");
    let payload = if payload_raw.trim().is_empty() {
        Value::Null
    } else {
        serde_json::from_str::<Value>(payload_raw).unwrap_or(Value::Null)
    };

    match method {
        "moduleInfo" => {
            let response = client.get(format!("{bridge_base}/module-info")).send()?;
            let value = response.json::<Value>()?;
            Ok(value
                .get("raw")
                .and_then(Value::as_str)
                .unwrap_or("{}")
                .to_string())
        }
        "listPackages" => {
            let package_type = payload.get("type").and_then(Value::as_str).unwrap_or("all");
            let response = client
                .get(format!(
                    "http://127.0.0.1:{port}/api/v1/packages?type={}",
                    encode(package_type)
                ))
                .send()?;
            let value = response.json::<Value>()?;
            Ok(serde_json::to_string(
                value.get("packages").unwrap_or(&Value::Array(vec![])),
            )?)
        }
        "getPackagesInfo" => {
            let packages = payload
                .get("packages")
                .cloned()
                .unwrap_or_else(|| Value::Array(vec![]));
            let response = client
                .post(format!("http://127.0.0.1:{port}/api/v1/packages/info"))
                .json(&json!({ "packages": packages }))
                .send()?;
            let value = response.json::<Value>()?;
            Ok(serde_json::to_string(
                value.get("packages").unwrap_or(&Value::Array(vec![])),
            )?)
        }
        "exec" => {
            let command = payload
                .get("command")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let options = payload.get("options").cloned().unwrap_or(Value::Null);
            let response = client
                .post(format!("{bridge_base}/exec"))
                .json(&json!({
                    "command": command,
                    "options": if options.is_null() { Value::Null } else { options }
                }))
                .send()?;
            let value = response.json::<Value>()?;
            Ok(value
                .get("stdout")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .to_string())
        }
        other => Err(anyhow!("unsupported sync bridge method: {other}")),
    }
}

#[derive(Debug, Clone)]
struct CliArgs {
    port: u16,
    module_id: String,
    module_name: String,
}

fn configure_linux_webview_env() {
    #[cfg(target_os = "linux")]
    {
        let has_wayland = std::env::var_os("WAYLAND_DISPLAY").is_some();
        let has_x11 = std::env::var_os("DISPLAY").is_some();
        let backend_set = std::env::var_os("GDK_BACKEND").is_some();
        if has_wayland && has_x11 && !backend_set {
            std::env::set_var("GDK_BACKEND", "x11");
        }
        if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
            std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
        }
    }
}
