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

#[derive(Clone, Copy)]
struct Strings {
    app_title: &'static str,
    app_subtitle: &'static str,
    page_cli: &'static str,
    page_device: &'static str,
    page_actions: &'static str,
    cli_title: &'static str,
    cli_description: &'static str,
    cli_command_title: &'static str,
    cli_command_description: &'static str,
    cli_command_placeholder: &'static str,
    cli_run: &'static str,
    cli_output_title: &'static str,
    cli_output_description: &'static str,
    quick_login: &'static str,
    quick_whoami: &'static str,
    quick_fork: &'static str,
    quick_sync: &'static str,
    quick_list: &'static str,
    quick_status: &'static str,
    quick_artifacts: &'static str,
    quick_build: &'static str,
    device_title: &'static str,
    device_description: &'static str,
    device_connection_title: &'static str,
    device_connection_description: &'static str,
    device_serial_placeholder: &'static str,
    device_port_placeholder: &'static str,
    device_detect: &'static str,
    device_start: &'static str,
    device_stop: &'static str,
    device_refresh: &'static str,
    device_snapshot_title: &'static str,
    device_snapshot_description: &'static str,
    device_session_tab: &'static str,
    device_runtime_tab: &'static str,
    device_root_tab: &'static str,
    device_susfs_tab: &'static str,
    device_log_title: &'static str,
    device_log_description: &'static str,
    actions_title: &'static str,
    actions_description: &'static str,
    root_card_title: &'static str,
    root_card_description: &'static str,
    root_package_placeholder: &'static str,
    root_allow: &'static str,
    root_revoke: &'static str,
    module_card_title: &'static str,
    module_card_description: &'static str,
    module_placeholder: &'static str,
    module_enable: &'static str,
    module_disable: &'static str,
    module_uninstall: &'static str,
    module_action: &'static str,
    install_card_title: &'static str,
    install_card_description: &'static str,
    install_module_placeholder: &'static str,
    install_module_button: &'static str,
    install_apk_placeholder: &'static str,
    install_apk_button: &'static str,
    flash_image_placeholder: &'static str,
    flash_partition_placeholder: &'static str,
    flash_button: &'static str,
    susfs_card_title: &'static str,
    susfs_card_description: &'static str,
    susfs_load: &'static str,
    susfs_apply: &'static str,
    diagnostics_export: &'static str,
    action_log_title: &'static str,
    action_log_description: &'static str,
    log_adb_forward: &'static str,
    log_start_agent: &'static str,
    log_agent_ready: &'static str,
    log_agent_timeout: &'static str,
    log_task_queued: &'static str,
    log_task_result: &'static str,
    log_downloaded_to: &'static str,
    log_download_failed: &'static str,
}

const ZH_STRINGS: Strings = Strings {
    app_title: "ABK 桌面版",
    app_subtitle: "GTK / libadwaita 控制台",
    page_cli: "CLI",
    page_device: "设备",
    page_actions: "操作",
    cli_title: "GitHub 与构建入口",
    cli_description: "桌面端直接调用现有 ABK CLI，这一页应该像控制台前端，而不是一块空黑板。",
    cli_command_title: "命令执行",
    cli_command_description: "输入任意 `abk` 子命令参数，桌面端会调用仓库里的 `cli/abk.py`。",
    cli_command_placeholder: "例如：build --sub-level 162 --os-patch-level 2026-03",
    cli_run: "运行",
    cli_output_title: "命令输出",
    cli_output_description: "CLI 的标准输出和错误输出会显示在这里。",
    quick_login: "登录",
    quick_whoami: "当前账号",
    quick_fork: "检查 Fork",
    quick_sync: "同步 Fork",
    quick_list: "列出选项",
    quick_status: "最近状态",
    quick_artifacts: "产物",
    quick_build: "预览构建",
    device_title: "设备桥接",
    device_description:
        "通过 `adb forward` 连接手机上的 ABK agent，读取运行态、Root 授权和 SUSFS 状态。",
    device_connection_title: "连接",
    device_connection_description: "先检测 ADB，再启动手机端 agent，最后刷新各个快照页。",
    device_serial_placeholder: "ADB 序列号（只有一台设备时可留空）",
    device_port_placeholder: "端口",
    device_detect: "检测设备",
    device_start: "启动 agent",
    device_stop: "停止 agent",
    device_refresh: "刷新",
    device_snapshot_title: "运行态快照",
    device_snapshot_description: "这里显示 session、runtime、root grants 和 SUSFS 的 JSON 快照。",
    device_session_tab: "Session",
    device_runtime_tab: "Runtime",
    device_root_tab: "Root 授权",
    device_susfs_tab: "SUSFS",
    device_log_title: "桥接日志",
    device_log_description: "ADB 转发、agent 启动和刷新时的本地日志。",
    actions_title: "设备动作",
    actions_description: "桌面端发起命令，真正的 Root / Android 动作仍在手机端 ABK agent 上执行。",
    root_card_title: "Root 授权",
    root_card_description: "按包名允许或撤销 Root 授权。",
    root_package_placeholder: "要操作的包名",
    root_allow: "允许 Root",
    root_revoke: "撤销 Root",
    module_card_title: "运行时模块",
    module_card_description: "控制模块启用状态、卸载标记和 action 脚本。",
    module_placeholder: "运行时模块 ID",
    module_enable: "启用",
    module_disable: "禁用",
    module_uninstall: "标记卸载",
    module_action: "运行 Action",
    install_card_title: "安装与刷写",
    install_card_description: "路径必须是手机端能访问到的文件路径。",
    install_module_placeholder: "模块 ZIP 路径（手机可访问）",
    install_module_button: "安装模块",
    install_apk_placeholder: "APK 路径（手机可访问）",
    install_apk_button: "安装 APK",
    flash_image_placeholder: "镜像路径（手机可访问）",
    flash_partition_placeholder: "分区",
    flash_button: "刷写镜像",
    susfs_card_title: "SUSFS 与诊断",
    susfs_card_description: "读取、编辑并回写 SUSFS JSON，同时支持导出诊断包。",
    susfs_load: "加载 SUSFS",
    susfs_apply: "应用编辑后的 SUSFS JSON",
    diagnostics_export: "导出诊断包",
    action_log_title: "动作日志",
    action_log_description: "长任务状态、任务输出和下载结果会显示在这里。",
    log_adb_forward: "ADB 转发",
    log_start_agent: "启动 agent",
    log_agent_ready: "Agent 已就绪",
    log_agent_timeout: "Agent 在预期时间内没有就绪",
    log_task_queued: "任务已入队",
    log_task_result: "任务结果",
    log_downloaded_to: "已下载到",
    log_download_failed: "下载失败",
};

const EN_STRINGS: Strings = Strings {
    app_title: "ABK Desktop",
    app_subtitle: "GTK / libadwaita console",
    page_cli: "CLI",
    page_device: "Device",
    page_actions: "Actions",
    cli_title: "GitHub and Build Frontend",
    cli_description: "The desktop app calls the existing ABK CLI directly. This page should feel like a real frontend, not a blank slab.",
    cli_command_title: "Command Runner",
    cli_command_description: "Enter any `abk` subcommand arguments and the desktop app will invoke `cli/abk.py` from this repo.",
    cli_command_placeholder: "Example: build --sub-level 162 --os-patch-level 2026-03",
    cli_run: "Run",
    cli_output_title: "Command Output",
    cli_output_description: "Standard output and standard error from the CLI appear here.",
    quick_login: "Login",
    quick_whoami: "Whoami",
    quick_fork: "Fork",
    quick_sync: "Sync",
    quick_list: "List",
    quick_status: "Status",
    quick_artifacts: "Artifacts",
    quick_build: "Build Preview",
    device_title: "Device Bridge",
    device_description: "Connect to the phone-side ABK agent through `adb forward` and inspect runtime, root grant, and SUSFS state.",
    device_connection_title: "Connection",
    device_connection_description: "Detect ADB first, then start the phone agent, then refresh the snapshot panes.",
    device_serial_placeholder: "ADB serial (optional when only one device is connected)",
    device_port_placeholder: "Port",
    device_detect: "Detect Devices",
    device_start: "Start Agent",
    device_stop: "Stop Agent",
    device_refresh: "Refresh",
    device_snapshot_title: "Runtime Snapshots",
    device_snapshot_description: "Session, runtime, root grants, and SUSFS JSON snapshots are shown here.",
    device_session_tab: "Session",
    device_runtime_tab: "Runtime",
    device_root_tab: "Root Grants",
    device_susfs_tab: "SUSFS",
    device_log_title: "Bridge Log",
    device_log_description: "Local ADB forwarding, agent startup, and refresh logs appear here.",
    actions_title: "Device Actions",
    actions_description: "The desktop app triggers commands, but the actual Root / Android actions still run inside the phone-side ABK agent.",
    root_card_title: "Root Grants",
    root_card_description: "Allow or revoke root access by package name.",
    root_package_placeholder: "Package name to update",
    root_allow: "Allow Root",
    root_revoke: "Revoke Root",
    module_card_title: "Runtime Modules",
    module_card_description: "Control module enable state, uninstall marks, and module action scripts.",
    module_placeholder: "Runtime module ID",
    module_enable: "Enable",
    module_disable: "Disable",
    module_uninstall: "Mark Uninstall",
    module_action: "Run Action",
    install_card_title: "Install and Flash",
    install_card_description: "Paths must point to files that are accessible on the phone side.",
    install_module_placeholder: "Module ZIP path (phone-accessible)",
    install_module_button: "Install Module",
    install_apk_placeholder: "APK path (phone-accessible)",
    install_apk_button: "Install APK",
    flash_image_placeholder: "Image path (phone-accessible)",
    flash_partition_placeholder: "Partition",
    flash_button: "Flash Image",
    susfs_card_title: "SUSFS and Diagnostics",
    susfs_card_description: "Load, edit, and apply SUSFS JSON while also supporting diagnostic export.",
    susfs_load: "Load SUSFS",
    susfs_apply: "Apply Edited SUSFS JSON",
    diagnostics_export: "Export Diagnostics",
    action_log_title: "Action Log",
    action_log_description: "Long task state, task output, and download results appear here.",
    log_adb_forward: "ADB forward",
    log_start_agent: "Start agent",
    log_agent_ready: "Agent ready",
    log_agent_timeout: "Agent did not become healthy in time",
    log_task_queued: "Queued task",
    log_task_result: "Task result",
    log_downloaded_to: "Downloaded to",
    log_download_failed: "Download failed",
};

fn main() {
    let app = adw::Application::builder()
        .application_id("com.abk.desktop")
        .build();
    app.connect_activate(build_ui);
    app.run();
}

fn build_ui(app: &adw::Application) {
    let strings = current_strings();
    let (sender, receiver) = mpsc::channel::<UiMessage>();

    let window = adw::ApplicationWindow::builder()
        .application(app)
        .title(strings.app_title)
        .default_width(1380)
        .default_height(920)
        .build();

    let header = adw::HeaderBar::new();
    let title = adw::WindowTitle::new(strings.app_title, strings.app_subtitle);
    header.set_title_widget(Some(&title));

    let content = gtk::Paned::new(gtk::Orientation::Horizontal);
    content.set_position(220);
    content.set_resize_start_child(false);
    content.set_shrink_start_child(false);
    content.set_shrink_end_child(false);

    let stack = gtk::Stack::new();
    stack.set_hexpand(true);
    stack.set_vexpand(true);
    stack.set_transition_type(gtk::StackTransitionType::Crossfade);

    let sidebar_box = gtk::Box::new(gtk::Orientation::Vertical, 12);
    set_margin_all(&sidebar_box, 16);
    sidebar_box.set_size_request(200, -1);

    let sidebar = gtk::StackSidebar::new();
    sidebar.set_stack(&stack);
    sidebar.set_vexpand(true);
    sidebar_box.append(&sidebar);

    content.set_start_child(Some(&sidebar_box));
    content.set_end_child(Some(&stack));

    let cli_page = build_cli_page(&sender, strings);
    stack.add_titled(&cli_page.container, Some("cli"), strings.page_cli);

    let device_page = build_device_page(&sender, strings);
    stack.add_titled(&device_page.container, Some("device"), strings.page_device);

    let actions_page = build_actions_page(&sender, strings);
    stack.add_titled(
        &actions_page.container,
        Some("actions"),
        strings.page_actions,
    );

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

    window.set_titlebar(Some(&header));
    window.set_content(Some(&content));
    window.present();
}

struct CliPage {
    container: gtk::ScrolledWindow,
    output: gtk::TextBuffer,
}

fn build_cli_page(sender: &Sender<UiMessage>, strings: Strings) -> CliPage {
    let (container, body) = new_page_shell();
    body.append(&page_header(strings.cli_title, strings.cli_description));

    let (command_section, command_content) =
        new_card_section(strings.cli_command_title, strings.cli_command_description);

    let entry = gtk::Entry::new();
    entry.set_hexpand(true);
    entry.set_placeholder_text(Some(strings.cli_command_placeholder));

    let run_button = gtk::Button::with_label(strings.cli_run);
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
    command_content.append(&controls);

    let quick = gtk::FlowBox::new();
    quick.set_selection_mode(gtk::SelectionMode::None);
    quick.set_row_spacing(8);
    quick.set_column_spacing(8);
    quick.set_max_children_per_line(4);
    quick.set_homogeneous(false);
    quick.set_valign(gtk::Align::Start);

    for (label, command) in quick_actions(strings) {
        let button = gtk::Button::with_label(label);
        let sender = sender.clone();
        let command = command.to_string();
        button.connect_clicked(move |_| spawn_cli(command.clone(), sender.clone()));
        quick.insert(&button, -1);
    }
    command_content.append(&quick);
    body.append(&command_section);

    let (output_section, output_content) =
        new_card_section(strings.cli_output_title, strings.cli_output_description);
    let output = new_text_buffer();
    let output_view = new_text_view(&output, false);
    let output_scroll = new_scroller(&output_view);
    output_scroll.set_min_content_height(360);
    output_scroll.set_vexpand(true);
    output_content.append(&output_scroll);
    body.append(&output_section);

    CliPage { container, output }
}

struct DevicePage {
    container: gtk::ScrolledWindow,
    log_output: gtk::TextBuffer,
    session_output: gtk::TextBuffer,
    runtime_output: gtk::TextBuffer,
    root_output: gtk::TextBuffer,
    susfs_output: gtk::TextBuffer,
}

fn build_device_page(sender: &Sender<UiMessage>, strings: Strings) -> DevicePage {
    let (container, body) = new_page_shell();
    body.append(&page_header(
        strings.device_title,
        strings.device_description,
    ));

    let (connection_section, connection_content) = new_card_section(
        strings.device_connection_title,
        strings.device_connection_description,
    );

    let serial_entry = gtk::Entry::new();
    serial_entry.set_placeholder_text(Some(strings.device_serial_placeholder));
    serial_entry.set_hexpand(true);

    let port_entry = gtk::Entry::new();
    port_entry.set_placeholder_text(Some(strings.device_port_placeholder));
    port_entry.set_text("48765");
    port_entry.set_width_chars(8);

    let detect_button = gtk::Button::with_label(strings.device_detect);
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

    let start_button = gtk::Button::with_label(strings.device_start);
    {
        let sender = sender.clone();
        let serial_entry = serial_entry.clone();
        let port_entry = port_entry.clone();
        start_button.connect_clicked(move |_| {
            let serial = serial_entry.text().to_string();
            let port = parse_port(&port_entry.text());
            spawn_agent_start(serial, port, sender.clone(), strings);
        });
    }

    let stop_button = gtk::Button::with_label(strings.device_stop);
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

    let refresh_button = gtk::Button::with_label(strings.device_refresh);
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
    connection_content.append(&controls);
    body.append(&connection_section);

    let (snapshot_section, snapshot_content) = new_card_section(
        strings.device_snapshot_title,
        strings.device_snapshot_description,
    );

    let notebook = gtk::Notebook::new();
    notebook.set_vexpand(true);

    let session_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&session_output, false)),
        Some(&gtk::Label::new(Some(strings.device_session_tab))),
    );

    let runtime_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&runtime_output, false)),
        Some(&gtk::Label::new(Some(strings.device_runtime_tab))),
    );

    let root_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&root_output, false)),
        Some(&gtk::Label::new(Some(strings.device_root_tab))),
    );

    let susfs_output = new_text_buffer();
    notebook.append_page(
        &new_scroller(&new_text_view(&susfs_output, false)),
        Some(&gtk::Label::new(Some(strings.device_susfs_tab))),
    );
    snapshot_content.append(&notebook);
    body.append(&snapshot_section);

    let (log_section, log_content) =
        new_card_section(strings.device_log_title, strings.device_log_description);
    let log_output = new_text_buffer();
    let log_view = new_text_view(&log_output, false);
    let log_scroll = new_scroller(&log_view);
    log_scroll.set_min_content_height(180);
    log_content.append(&log_scroll);
    body.append(&log_section);

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
    container: gtk::ScrolledWindow,
    log_output: gtk::TextBuffer,
    susfs_editor: gtk::TextBuffer,
}

fn build_actions_page(sender: &Sender<UiMessage>, strings: Strings) -> ActionsPage {
    let (container, body) = new_page_shell();
    body.append(&page_header(
        strings.actions_title,
        strings.actions_description,
    ));

    let port_entry = gtk::Entry::new();
    port_entry.set_placeholder_text(Some(strings.device_port_placeholder));
    port_entry.set_text("48765");
    port_entry.set_width_chars(8);

    let (root_section, root_content) =
        new_card_section(strings.root_card_title, strings.root_card_description);
    let package_entry = gtk::Entry::new();
    package_entry.set_hexpand(true);
    package_entry.set_placeholder_text(Some(strings.root_package_placeholder));

    let allow_button = gtk::Button::with_label(strings.root_allow);
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

    let revoke_button = gtk::Button::with_label(strings.root_revoke);
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
    root_content.append(&root_row);
    body.append(&root_section);

    let (module_section, module_content) =
        new_card_section(strings.module_card_title, strings.module_card_description);
    let module_entry = gtk::Entry::new();
    module_entry.set_hexpand(true);
    module_entry.set_placeholder_text(Some(strings.module_placeholder));

    let enable_button = gtk::Button::with_label(strings.module_enable);
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

    let disable_button = gtk::Button::with_label(strings.module_disable);
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

    let uninstall_button = gtk::Button::with_label(strings.module_uninstall);
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

    let action_button = gtk::Button::with_label(strings.module_action);
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
                strings,
            );
        });
    }

    let module_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    module_row.append(&module_entry);
    module_row.append(&enable_button);
    module_row.append(&disable_button);
    module_row.append(&uninstall_button);
    module_row.append(&action_button);
    module_content.append(&module_row);
    body.append(&module_section);

    let (install_section, install_content) =
        new_card_section(strings.install_card_title, strings.install_card_description);

    let module_zip_entry = gtk::Entry::new();
    module_zip_entry.set_hexpand(true);
    module_zip_entry.set_placeholder_text(Some(strings.install_module_placeholder));

    let install_module_button = gtk::Button::with_label(strings.install_module_button);
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
                strings,
            );
        });
    }

    let module_zip_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    module_zip_row.append(&module_zip_entry);
    module_zip_row.append(&install_module_button);
    install_content.append(&module_zip_row);

    let apk_entry = gtk::Entry::new();
    apk_entry.set_hexpand(true);
    apk_entry.set_placeholder_text(Some(strings.install_apk_placeholder));

    let install_apk_button = gtk::Button::with_label(strings.install_apk_button);
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
                strings,
            );
        });
    }

    let apk_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    apk_row.append(&apk_entry);
    apk_row.append(&install_apk_button);
    install_content.append(&apk_row);

    let image_entry = gtk::Entry::new();
    image_entry.set_hexpand(true);
    image_entry.set_placeholder_text(Some(strings.flash_image_placeholder));

    let partition_entry = gtk::Entry::new();
    partition_entry.set_placeholder_text(Some(strings.flash_partition_placeholder));
    partition_entry.set_text("boot");
    partition_entry.set_width_chars(8);

    let flash_button = gtk::Button::with_label(strings.flash_button);
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
                strings,
            );
        });
    }

    let flash_row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    flash_row.append(&image_entry);
    flash_row.append(&partition_entry);
    flash_row.append(&flash_button);
    install_content.append(&flash_row);
    body.append(&install_section);

    let (susfs_section, susfs_content) =
        new_card_section(strings.susfs_card_title, strings.susfs_card_description);

    let susfs_controls = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    let load_susfs_button = gtk::Button::with_label(strings.susfs_load);
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

    let apply_susfs_button = gtk::Button::with_label(strings.susfs_apply);
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
                strings,
            );
        });
    }

    let export_diag_button = gtk::Button::with_label(strings.diagnostics_export);
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        export_diag_button.connect_clicked(move |_| {
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.export_diagnostics(),
                true,
                strings,
            );
        });
    }

    susfs_controls.append(&load_susfs_button);
    susfs_controls.append(&apply_susfs_button);
    susfs_controls.append(&export_diag_button);
    susfs_content.append(&susfs_controls);

    let susfs_view = new_text_view(&susfs_editor, true);
    let susfs_scroll = new_scroller(&susfs_view);
    susfs_scroll.set_min_content_height(260);
    susfs_content.append(&susfs_scroll);
    body.append(&susfs_section);

    let (log_section, log_content) =
        new_card_section(strings.action_log_title, strings.action_log_description);
    let log_output = new_text_buffer();
    let log_view = new_text_view(&log_output, false);
    let log_scroll = new_scroller(&log_view);
    log_scroll.set_min_content_height(200);
    log_content.append(&log_scroll);
    body.append(&log_section);

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

fn spawn_agent_start(serial: String, port: u16, sender: Sender<UiMessage>, strings: Strings) {
    thread::spawn(move || {
        let forward = run_command(&build_adb_forward_command(&serial, port));
        let start = run_command(&build_adb_start_agent_command(&serial, port));
        let mut lines = Vec::new();
        lines.push(format!(
            "{}: {}",
            strings.log_adb_forward,
            forward.unwrap_or_else(|e| format!("{e:#}"))
        ));
        lines.push(format!(
            "{}: {}",
            strings.log_start_agent,
            start.unwrap_or_else(|e| format!("{e:#}"))
        ));
        let _ = sender.send(UiMessage::DeviceLog(lines.join("\n\n")));
        for _ in 0..20 {
            let client = AgentClient::new("127.0.0.1", port);
            match client.health() {
                Ok(health) => {
                    let _ = sender.send(UiMessage::DeviceLog(format!(
                        "{}\n{}",
                        strings.log_agent_ready, health
                    )));
                    refresh_agent(client, &sender);
                    return;
                }
                Err(_) => thread::sleep(Duration::from_millis(500)),
            }
        }
        let _ = sender.send(UiMessage::DeviceLog(strings.log_agent_timeout.into()));
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

fn spawn_agent_task_call<F>(
    port: u16,
    sender: Sender<UiMessage>,
    call: F,
    download: bool,
    strings: Strings,
) where
    F: FnOnce(AgentClient) -> anyhow::Result<agent::TaskSnapshot> + Send + 'static,
{
    thread::spawn(move || {
        let client = AgentClient::new("127.0.0.1", port);
        match call(client.clone()) {
            Ok(task) => {
                let _ = sender.send(UiMessage::ActionLog(format!(
                    "{} {} ({})",
                    strings.log_task_queued, task.id, task.kind
                )));
                match client.poll_task(&task.id, Duration::from_secs(300)) {
                    Ok(final_task) => {
                        let mut lines = vec![format!(
                            "{} {} -> {}{}",
                            strings.log_task_result,
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
                                Ok(path) => lines.push(format!(
                                    "{} {}",
                                    strings.log_downloaded_to,
                                    path.display()
                                )),
                                Err(error) => lines
                                    .push(format!("{}: {error:#}", strings.log_download_failed)),
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

fn current_strings() -> Strings {
    let locale = [
        std::env::var("LC_ALL").ok(),
        std::env::var("LC_MESSAGES").ok(),
        std::env::var("LANG").ok(),
    ]
    .into_iter()
    .flatten()
    .next()
    .unwrap_or_default()
    .to_lowercase();

    if locale.starts_with("zh") {
        ZH_STRINGS
    } else {
        EN_STRINGS
    }
}

fn quick_actions(strings: Strings) -> [(&'static str, &'static str); 8] {
    [
        (strings.quick_login, "login"),
        (strings.quick_whoami, "whoami"),
        (strings.quick_fork, "fork"),
        (strings.quick_sync, "sync"),
        (strings.quick_list, "list"),
        (strings.quick_status, "status"),
        (strings.quick_artifacts, "artifacts --run-id 0"),
        (
            strings.quick_build,
            "build --dry-run --sub-level 162 --os-patch-level 2026-03",
        ),
    ]
}

fn new_page_shell() -> (gtk::ScrolledWindow, gtk::Box) {
    let body = gtk::Box::new(gtk::Orientation::Vertical, 24);
    set_margin_all(&body, 24);

    let clamp = adw::Clamp::new();
    clamp.set_maximum_size(1120);
    clamp.set_tightening_threshold(760);
    clamp.set_child(Some(&body));

    let scroller = gtk::ScrolledWindow::new();
    scroller.set_policy(gtk::PolicyType::Automatic, gtk::PolicyType::Automatic);
    scroller.set_vexpand(true);
    scroller.set_hexpand(true);
    scroller.set_child(Some(&clamp));
    (scroller, body)
}

fn page_header(title: &str, description: &str) -> gtk::Box {
    let header = gtk::Box::new(gtk::Orientation::Vertical, 6);

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.set_wrap(true);
    title_label.add_css_class("title-2");
    header.append(&title_label);

    let description_label = gtk::Label::new(Some(description));
    description_label.set_xalign(0.0);
    description_label.set_wrap(true);
    description_label.add_css_class("dim-label");
    header.append(&description_label);

    header
}

fn new_card_section(title: &str, description: &str) -> (gtk::Box, gtk::Box) {
    let section = gtk::Box::new(gtk::Orientation::Vertical, 10);

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.set_wrap(true);
    title_label.add_css_class("title-4");
    section.append(&title_label);

    let description_label = gtk::Label::new(Some(description));
    description_label.set_xalign(0.0);
    description_label.set_wrap(true);
    description_label.add_css_class("dim-label");
    section.append(&description_label);

    let frame = gtk::Frame::new(None);
    frame.add_css_class("card");

    let content = gtk::Box::new(gtk::Orientation::Vertical, 12);
    set_margin_all(&content, 16);
    frame.set_child(Some(&content));
    section.append(&frame);

    (section, content)
}

fn set_margin_all(widget: &impl IsA<gtk::Widget>, margin: i32) {
    widget.set_margin_top(margin);
    widget.set_margin_bottom(margin);
    widget.set_margin_start(margin);
    widget.set_margin_end(margin);
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
