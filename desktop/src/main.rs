mod agent;
mod commands;

use crate::agent::AgentClient;
use crate::commands::{
    build_adb_detect_command, build_adb_forward_command, build_adb_start_agent_command,
    build_adb_stop_agent_command, build_cli_command, run_command,
};
use adw::prelude::*;
use std::sync::mpsc::{self, Sender};
use std::thread;
use std::time::Duration;

#[derive(Debug, Clone)]
enum UiMessage {
    CliOutput(String),
    DeviceLog(String),
    ActionLog(String),
    SessionJson(String),
    RuntimeJson(String),
    RootJson(String),
    SusfsJson(String),
    SusfsEditor(String),
}

fn main() {
    let app = adw::Application::builder()
        .application_id("com.abk.desktop")
        .build();
    app.connect_activate(build_ui);
    app.run();
}

fn build_ui(app: &adw::Application) {
    let (sender, receiver) = mpsc::channel::<UiMessage>();

    let window = adw::ApplicationWindow::builder()
        .application(app)
        .title("ABK Desktop")
        .default_width(1440)
        .default_height(920)
        .build();

    let root = gtk::Box::new(gtk::Orientation::Vertical, 0);
    let header = adw::HeaderBar::new();
    let title = gtk::Label::new(Some("ABK Desktop"));
    title.add_css_class("title-1");
    header.set_title_widget(Some(&title));
    root.append(&header);

    let content = gtk::Paned::new(gtk::Orientation::Horizontal);
    content.set_wide_handle(true);
    root.append(&content);

    let stack = gtk::Stack::new();
    stack.set_hexpand(true);
    stack.set_vexpand(true);
    stack.set_transition_type(gtk::StackTransitionType::SlideLeftRight);

    let sidebar = gtk::StackSidebar::new();
    sidebar.set_stack(&stack);
    sidebar.set_vexpand(true);

    content.set_start_child(Some(&sidebar));
    content.set_end_child(Some(&stack));

    let cli_page = build_cli_page(&sender);
    stack.add_titled(&cli_page.container, Some("cli"), "CLI");

    let device_page = build_device_page(&sender);
    stack.add_titled(&device_page.container, Some("device"), "Device");

    let actions_page = build_actions_page(&sender);
    stack.add_titled(&actions_page.container, Some("actions"), "Actions");

    let cli_buffer = cli_page.output;
    let device_log_buffer = device_page.log_output;
    let session_buffer = device_page.session_output;
    let runtime_buffer = device_page.runtime_output;
    let root_buffer = device_page.root_output;
    let susfs_buffer = device_page.susfs_output;
    let action_buffer = actions_page.log_output;
    let susfs_editor = actions_page.susfs_editor;

    glib::timeout_add_local(Duration::from_millis(100), move || {
        while let Ok(message) = receiver.try_recv() {
            match message {
                UiMessage::CliOutput(text) => cli_buffer.set_text(&text),
                UiMessage::DeviceLog(text) => append_buffer(&device_log_buffer, &text),
                UiMessage::ActionLog(text) => append_buffer(&action_buffer, &text),
                UiMessage::SessionJson(text) => session_buffer.set_text(&text),
                UiMessage::RuntimeJson(text) => runtime_buffer.set_text(&text),
                UiMessage::RootJson(text) => root_buffer.set_text(&text),
                UiMessage::SusfsJson(text) => susfs_buffer.set_text(&text),
                UiMessage::SusfsEditor(text) => susfs_editor.set_text(&text),
            }
        }
        glib::ControlFlow::Continue
    });

    window.set_content(Some(&root));
    window.present();
}

struct CliPage {
    container: gtk::Box,
    output: gtk::TextBuffer,
}

fn build_cli_page(sender: &Sender<UiMessage>) -> CliPage {
    let container = gtk::Box::new(gtk::Orientation::Vertical, 12);
    container.set_margin_top(16);
    container.set_margin_bottom(16);
    container.set_margin_start(16);
    container.set_margin_end(16);

    let intro = gtk::Label::new(Some(
        "Desktop GitHub/build operations execute the existing ABK CLI so the command surface stays aligned.",
    ));
    intro.set_wrap(true);
    intro.set_xalign(0.0);
    container.append(&intro);

    let entry = gtk::Entry::new();
    entry.set_hexpand(true);
    entry.set_placeholder_text(Some(
        "Example: build --sub-level 162 --os-patch-level 2026-03",
    ));

    let run_button = gtk::Button::with_label("Run");
    {
        let sender = sender.clone();
        let entry = entry.clone();
        run_button.connect_clicked(move |_| {
            let raw = entry.text().to_string();
            spawn_cli(raw, sender.clone());
        });
    }

    let controls = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    controls.append(&entry);
    controls.append(&run_button);
    container.append(&controls);

    let quick = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    for (label, command) in [
        ("login", "login"),
        ("whoami", "whoami"),
        ("fork", "fork"),
        ("sync", "sync"),
        ("list", "list"),
        ("status", "status"),
        ("artifacts", "artifacts --run-id 0"),
        (
            "build",
            "build --dry-run --sub-level 162 --os-patch-level 2026-03",
        ),
    ] {
        let button = gtk::Button::with_label(label);
        let sender = sender.clone();
        let command = command.to_string();
        button.connect_clicked(move |_| spawn_cli(command.clone(), sender.clone()));
        quick.append(&button);
    }
    container.append(&quick);

    let output = new_text_buffer();
    let output_view = new_text_view(&output, false);
    let output_scroll = new_scroller(&output_view);
    output_scroll.set_vexpand(true);
    container.append(&output_scroll);

    CliPage { container, output }
}

struct DevicePage {
    container: gtk::Box,
    log_output: gtk::TextBuffer,
    session_output: gtk::TextBuffer,
    runtime_output: gtk::TextBuffer,
    root_output: gtk::TextBuffer,
    susfs_output: gtk::TextBuffer,
}

fn build_device_page(sender: &Sender<UiMessage>) -> DevicePage {
    let container = gtk::Box::new(gtk::Orientation::Vertical, 12);
    container.set_margin_top(16);
    container.set_margin_bottom(16);
    container.set_margin_start(16);
    container.set_margin_end(16);

    let serial_entry = gtk::Entry::new();
    serial_entry.set_placeholder_text(Some("ADB serial (optional if only one device)"));
    serial_entry.set_hexpand(true);

    let port_entry = gtk::Entry::new();
    port_entry.set_text("48765");
    port_entry.set_width_chars(8);

    let detect_button = gtk::Button::with_label("adb devices");
    {
        let sender = sender.clone();
        detect_button.connect_clicked(move |_| {
            let sender = sender.clone();
            thread::spawn(move || {
                let result = run_command(&build_adb_detect_command())
                    .unwrap_or_else(|error| format!("{error:#}"));
                let _ = sender.send(UiMessage::DeviceLog(result));
            });
        });
    }

    let start_button = gtk::Button::with_label("Start agent");
    {
        let sender = sender.clone();
        let serial_entry = serial_entry.clone();
        let port_entry = port_entry.clone();
        start_button.connect_clicked(move |_| {
            let serial = serial_entry.text().to_string();
            let port = parse_port(&port_entry.text());
            spawn_agent_start(serial, port, sender.clone());
        });
    }

    let stop_button = gtk::Button::with_label("Stop agent");
    {
        let sender = sender.clone();
        let serial_entry = serial_entry.clone();
        stop_button.connect_clicked(move |_| {
            let serial = serial_entry.text().to_string();
            let sender = sender.clone();
            thread::spawn(move || {
                let result = run_command(&build_adb_stop_agent_command(&serial))
                    .unwrap_or_else(|error| format!("{error:#}"));
                let _ = sender.send(UiMessage::DeviceLog(result));
            });
        });
    }

    let refresh_button = gtk::Button::with_label("Refresh");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        refresh_button.connect_clicked(move |_| {
            spawn_agent_refresh(parse_port(&port_entry.text()), sender.clone());
        });
    }

    let controls = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    controls.append(&serial_entry);
    controls.append(&port_entry);
    controls.append(&detect_button);
    controls.append(&start_button);
    controls.append(&stop_button);
    controls.append(&refresh_button);
    container.append(&controls);

    let notebook = gtk::Notebook::new();
    notebook.set_vexpand(true);

    let session_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&session_output, false)),
        Some(&gtk::Label::new(Some("Session"))),
    );

    let runtime_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&runtime_output, false)),
        Some(&gtk::Label::new(Some("Runtime"))),
    );

    let root_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&root_output, false)),
        Some(&gtk::Label::new(Some("Root grants"))),
    );

    let susfs_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&susfs_output, false)),
        Some(&gtk::Label::new(Some("SUSFS"))),
    );
    container.append(&notebook);

    let log_output = new_text_buffer();
    let log_label = gtk::Label::new(Some("Device bridge log"));
    log_label.set_xalign(0.0);
    container.append(&log_label);
    let log_view = new_text_view(&log_output, false);
    let log_scroll = new_scroller(&log_view);
    log_scroll.set_min_content_height(160);
    container.append(&log_scroll);

    DevicePage {
        container,
        log_output,
        session_output,
        runtime_output,
        root_output,
        susfs_output,
    }
}

struct ActionsPage {
    container: gtk::Box,
    log_output: gtk::TextBuffer,
    susfs_editor: gtk::TextBuffer,
}

fn build_actions_page(sender: &Sender<UiMessage>) -> ActionsPage {
    let container = gtk::Box::new(gtk::Orientation::Vertical, 12);
    container.set_margin_top(16);
    container.set_margin_bottom(16);
    container.set_margin_start(16);
    container.set_margin_end(16);

    let port_entry = gtk::Entry::new();
    port_entry.set_text("48765");
    port_entry.set_width_chars(8);

    let package_entry = gtk::Entry::new();
    package_entry.set_hexpand(true);
    package_entry.set_placeholder_text(Some("Package name for root-grant allow/revoke"));

    let allow_button = gtk::Button::with_label("Allow root");
    {
        let sender = sender.clone();
        let package_entry = package_entry.clone();
        let port_entry = port_entry.clone();
        allow_button.connect_clicked(move |_| {
            let package_name = package_entry.text().to_string();
            spawn_agent_sync_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.set_root_grant(&package_name, true),
            );
        });
    }

    let revoke_button = gtk::Button::with_label("Revoke root");
    {
        let sender = sender.clone();
        let package_entry = package_entry.clone();
        let port_entry = port_entry.clone();
        revoke_button.connect_clicked(move |_| {
            let package_name = package_entry.text().to_string();
            spawn_agent_sync_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.set_root_grant(&package_name, false),
            );
        });
    }

    let root_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    root_row.append(&port_entry);
    root_row.append(&package_entry);
    root_row.append(&allow_button);
    root_row.append(&revoke_button);
    container.append(&root_row);

    let module_entry = gtk::Entry::new();
    module_entry.set_hexpand(true);
    module_entry.set_placeholder_text(Some("Runtime module id"));

    let enable_button = gtk::Button::with_label("Enable");
    {
        let sender = sender.clone();
        let module_entry = module_entry.clone();
        let port_entry = port_entry.clone();
        enable_button.connect_clicked(move |_| {
            let module_id = module_entry.text().to_string();
            spawn_agent_sync_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.set_module_enabled(&module_id, true),
            );
        });
    }

    let disable_button = gtk::Button::with_label("Disable");
    {
        let sender = sender.clone();
        let module_entry = module_entry.clone();
        let port_entry = port_entry.clone();
        disable_button.connect_clicked(move |_| {
            let module_id = module_entry.text().to_string();
            spawn_agent_sync_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.set_module_enabled(&module_id, false),
            );
        });
    }

    let uninstall_button = gtk::Button::with_label("Mark uninstall");
    {
        let sender = sender.clone();
        let module_entry = module_entry.clone();
        let port_entry = port_entry.clone();
        uninstall_button.connect_clicked(move |_| {
            let module_id = module_entry.text().to_string();
            spawn_agent_sync_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.set_module_pending_uninstall(&module_id, true),
            );
        });
    }

    let action_button = gtk::Button::with_label("Run action");
    {
        let sender = sender.clone();
        let module_entry = module_entry.clone();
        let port_entry = port_entry.clone();
        action_button.connect_clicked(move |_| {
            let module_id = module_entry.text().to_string();
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.run_module_action(&module_id),
                false,
            );
        });
    }

    let module_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    module_row.append(&module_entry);
    module_row.append(&enable_button);
    module_row.append(&disable_button);
    module_row.append(&uninstall_button);
    module_row.append(&action_button);
    container.append(&module_row);

    let module_zip_entry = gtk::Entry::new();
    module_zip_entry.set_hexpand(true);
    module_zip_entry.set_placeholder_text(Some("Module zip path on device-accessible filesystem"));

    let install_module_button = gtk::Button::with_label("Install module");
    {
        let sender = sender.clone();
        let module_zip_entry = module_zip_entry.clone();
        let port_entry = port_entry.clone();
        install_module_button.connect_clicked(move |_| {
            let path = module_zip_entry.text().to_string();
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.install_module(&path),
                false,
            );
        });
    }

    let module_zip_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    module_zip_row.append(&module_zip_entry);
    module_zip_row.append(&install_module_button);
    container.append(&module_zip_row);

    let apk_entry = gtk::Entry::new();
    apk_entry.set_hexpand(true);
    apk_entry.set_placeholder_text(Some("APK path on device-accessible filesystem"));

    let install_apk_button = gtk::Button::with_label("Install APK");
    {
        let sender = sender.clone();
        let apk_entry = apk_entry.clone();
        let port_entry = port_entry.clone();
        install_apk_button.connect_clicked(move |_| {
            let path = apk_entry.text().to_string();
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.install_apk(&path),
                false,
            );
        });
    }

    let apk_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    apk_row.append(&apk_entry);
    apk_row.append(&install_apk_button);
    container.append(&apk_row);

    let image_entry = gtk::Entry::new();
    image_entry.set_hexpand(true);
    image_entry.set_placeholder_text(Some("Boot image path on device-accessible filesystem"));
    let partition_entry = gtk::Entry::new();
    partition_entry.set_text("boot");
    partition_entry.set_width_chars(8);

    let flash_button = gtk::Button::with_label("Flash image");
    {
        let sender = sender.clone();
        let image_entry = image_entry.clone();
        let partition_entry = partition_entry.clone();
        let port_entry = port_entry.clone();
        flash_button.connect_clicked(move |_| {
            let path = image_entry.text().to_string();
            let partition = partition_entry.text().to_string();
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.flash_image(&path, &partition),
                false,
            );
        });
    }

    let flash_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    flash_row.append(&image_entry);
    flash_row.append(&partition_entry);
    flash_row.append(&flash_button);
    container.append(&flash_row);

    let susfs_controls = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    let load_susfs_button = gtk::Button::with_label("Load SUSFS");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        load_susfs_button.connect_clicked(move |_| {
            let port = parse_port(&port_entry.text());
            let sender = sender.clone();
            thread::spawn(move || {
                let client = AgentClient::new("127.0.0.1", port);
                match client.susfs() {
                    Ok(json) => {
                        let _ = sender.send(UiMessage::SusfsJson(json.clone()));
                        let _ = sender.send(UiMessage::SusfsEditor(json));
                    }
                    Err(error) => {
                        let _ = sender.send(UiMessage::ActionLog(format!("{error:#}")));
                    }
                }
            });
        });
    }

    let apply_susfs_button = gtk::Button::with_label("Apply edited SUSFS JSON");
    let susfs_editor = new_text_buffer();
    {
        let sender = sender.clone();
        let susfs_editor = susfs_editor.clone();
        let port_entry = port_entry.clone();
        apply_susfs_button.connect_clicked(move |_| {
            let json = buffer_text(&susfs_editor);
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.apply_susfs_json(&json),
                false,
            );
        });
    }

    let export_diag_button = gtk::Button::with_label("Export diagnostics");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        export_diag_button.connect_clicked(move |_| {
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.export_diagnostics(),
                true,
            );
        });
    }

    susfs_controls.append(&load_susfs_button);
    susfs_controls.append(&apply_susfs_button);
    susfs_controls.append(&export_diag_button);
    container.append(&susfs_controls);

    let susfs_view = new_text_view(&susfs_editor, true);
    let susfs_scroll = new_scroller(&susfs_view);
    susfs_scroll.set_vexpand(true);
    container.append(&susfs_scroll);

    let log_output = new_text_buffer();
    let log_view = new_text_view(&log_output, false);
    let log_scroll = new_scroller(&log_view);
    log_scroll.set_min_content_height(180);
    container.append(&log_scroll);

    ActionsPage {
        container,
        log_output,
        susfs_editor,
    }
}

fn spawn_cli(raw: String, sender: Sender<UiMessage>) {
    thread::spawn(move || {
        let message = match build_cli_command(&raw).and_then(|spec| run_command(&spec)) {
            Ok(output) => output,
            Err(error) => format!("{error:#}"),
        };
        let _ = sender.send(UiMessage::CliOutput(message));
    });
}

fn spawn_agent_start(serial: String, port: u16, sender: Sender<UiMessage>) {
    thread::spawn(move || {
        let forward = run_command(&build_adb_forward_command(&serial, port));
        let start = run_command(&build_adb_start_agent_command(&serial, port));
        let mut lines = Vec::new();
        lines.push(format!(
            "adb forward: {}",
            forward.unwrap_or_else(|e| format!("{e:#}"))
        ));
        lines.push(format!(
            "start agent: {}",
            start.unwrap_or_else(|e| format!("{e:#}"))
        ));
        let _ = sender.send(UiMessage::DeviceLog(lines.join("\n\n")));
        for _ in 0..20 {
            let client = AgentClient::new("127.0.0.1", port);
            match client.health() {
                Ok(health) => {
                    let _ = sender.send(UiMessage::DeviceLog(format!("Agent ready\n{health}")));
                    refresh_agent(client, &sender);
                    return;
                }
                Err(_) => thread::sleep(Duration::from_millis(500)),
            }
        }
        let _ = sender.send(UiMessage::DeviceLog(
            "Agent did not become healthy in time".into(),
        ));
    });
}

fn spawn_agent_refresh(port: u16, sender: Sender<UiMessage>) {
    thread::spawn(move || refresh_agent(AgentClient::new("127.0.0.1", port), &sender));
}

fn refresh_agent(client: AgentClient, sender: &Sender<UiMessage>) {
    match client.session() {
        Ok(text) => {
            let _ = sender.send(UiMessage::SessionJson(text));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::DeviceLog(format!("{error:#}")));
        }
    }
    match client.runtime() {
        Ok(text) => {
            let _ = sender.send(UiMessage::RuntimeJson(text));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::DeviceLog(format!("{error:#}")));
        }
    }
    match client.root_grants() {
        Ok(text) => {
            let _ = sender.send(UiMessage::RootJson(text));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::DeviceLog(format!("{error:#}")));
        }
    }
    match client.susfs() {
        Ok(text) => {
            let _ = sender.send(UiMessage::SusfsJson(text.clone()));
            let _ = sender.send(UiMessage::SusfsEditor(text));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::DeviceLog(format!("{error:#}")));
        }
    }
}

fn spawn_agent_sync_call<F>(port: u16, sender: Sender<UiMessage>, call: F)
where
    F: FnOnce(AgentClient) -> anyhow::Result<String> + Send + 'static,
{
    thread::spawn(move || {
        let client = AgentClient::new("127.0.0.1", port);
        let message = match call(client.clone()) {
            Ok(text) => {
                refresh_agent(client, &sender);
                text
            }
            Err(error) => format!("{error:#}"),
        };
        let _ = sender.send(UiMessage::ActionLog(message));
    });
}

fn spawn_agent_task_call<F>(port: u16, sender: Sender<UiMessage>, call: F, download: bool)
where
    F: FnOnce(AgentClient) -> anyhow::Result<agent::TaskSnapshot> + Send + 'static,
{
    thread::spawn(move || {
        let client = AgentClient::new("127.0.0.1", port);
        match call(client.clone()) {
            Ok(task) => {
                let _ = sender.send(UiMessage::ActionLog(format!(
                    "Queued task {} ({})",
                    task.id, task.kind
                )));
                match client.poll_task(&task.id, Duration::from_secs(300)) {
                    Ok(final_task) => {
                        let mut lines = vec![format!(
                            "Task {} -> {}{}",
                            final_task.id,
                            final_task.state,
                            final_task
                                .message
                                .as_deref()
                                .map(|m| format!(" ({m})"))
                                .unwrap_or_default()
                        )];
                        if !final_task.output.is_empty() {
                            lines.push(final_task.output.join("\n"));
                        }
                        if download {
                            match client.download_task_file(
                                &final_task.id,
                                &AgentClient::default_download_dir(),
                            ) {
                                Ok(path) => lines.push(format!("Downloaded to {}", path.display())),
                                Err(error) => lines.push(format!("Download failed: {error:#}")),
                            }
                        }
                        let _ = sender.send(UiMessage::ActionLog(lines.join("\n\n")));
                        refresh_agent(client, &sender);
                    }
                    Err(error) => {
                        let _ = sender.send(UiMessage::ActionLog(format!("{error:#}")));
                    }
                }
            }
            Err(error) => {
                let _ = sender.send(UiMessage::ActionLog(format!("{error:#}")));
            }
        }
    });
}

fn parse_port(raw: &str) -> u16 {
    raw.trim()
        .parse::<u16>()
        .ok()
        .filter(|port| *port > 0)
        .unwrap_or(48765)
}

fn new_text_buffer() -> gtk::TextBuffer {
    gtk::TextBuffer::new(None)
}

fn new_text_view(buffer: &gtk::TextBuffer, editable: bool) -> gtk::TextView {
    let view = gtk::TextView::new();
    view.set_buffer(Some(buffer));
    view.set_editable(editable);
    view.set_monospace(true);
    view.set_wrap_mode(gtk::WrapMode::WordChar);
    view
}

fn new_scroller(child: &impl IsA<gtk::Widget>) -> gtk::ScrolledWindow {
    let scroller = gtk::ScrolledWindow::new();
    scroller.set_policy(gtk::PolicyType::Automatic, gtk::PolicyType::Automatic);
    scroller.set_child(Some(child));
    scroller
}

fn append_buffer(buffer: &gtk::TextBuffer, text: &str) {
    let existing = buffer_text(buffer);
    let next = if existing.trim().is_empty() {
        text.to_string()
    } else {
        format!("{existing}\n\n{text}")
    };
    buffer.set_text(&next);
}

fn buffer_text(buffer: &gtk::TextBuffer) -> String {
    let start = buffer.start_iter();
    let end = buffer.end_iter();
    buffer.text(&start, &end, true).to_string()
}
