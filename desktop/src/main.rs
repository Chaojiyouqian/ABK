mod agent;
mod commands;

use crate::agent::{pretty_json_value, AgentClient};
use crate::commands::{
    build_adb_detect_command, build_adb_forward_command, build_adb_start_agent_command,
    build_adb_stop_agent_command, build_cli_command, run_command,
};
use adw::prelude::*;
use gdk_pixbuf::Pixbuf;
use gtk::{gdk, glib};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::cell::RefCell;
use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::Cursor;
use std::path::PathBuf;
use std::rc::Rc;
use std::sync::mpsc::{self, Sender};
use std::thread;
use std::time::Duration;

#[derive(Debug, Clone)]
enum UiMessage {
    BuildOutput(String),
    ActivityLog(String),
    SessionSnapshot(Value),
    RuntimeSnapshot(Value),
    RootSnapshot(Value),
    RootIconLoaded(String, Vec<u8>),
    SusfsSnapshot(Value),
}

#[derive(Debug, Clone)]
struct RootGrantEntry {
    package_name: String,
    label: String,
    is_system_app: bool,
    allow_su: bool,
    raw: Value,
}

#[derive(Debug, Default)]
struct RootGrantPageState {
    entries: Vec<RootGrantEntry>,
    search_query: String,
    show_system_apps: bool,
    selected_package: Option<String>,
    icon_cache: HashMap<String, Vec<u8>>,
    icon_inflight: HashSet<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct DesktopPrefs {
    show_system_apps: Option<bool>,
}

#[derive(Clone, Copy)]
struct Strings {
    app_title: &'static str,
    app_subtitle: &'static str,
    nav_overview: &'static str,
    nav_overview_desc: &'static str,
    nav_build: &'static str,
    nav_build_desc: &'static str,
    nav_device: &'static str,
    nav_device_desc: &'static str,
    brand_badge: &'static str,
    overview_kicker: &'static str,
    overview_title: &'static str,
    overview_body: &'static str,
    overview_connect: &'static str,
    overview_preview: &'static str,
    overview_diagnostics: &'static str,
    overview_summary_title: &'static str,
    overview_summary_body: &'static str,
    overview_live_title: &'static str,
    overview_live_body: &'static str,
    overview_card_build_title: &'static str,
    overview_card_build_body: &'static str,
    overview_card_device_title: &'static str,
    overview_card_device_body: &'static str,
    overview_card_runtime_title: &'static str,
    overview_card_runtime_body: &'static str,
    overview_card_surface_title: &'static str,
    overview_card_surface_body: &'static str,
    build_kicker: &'static str,
    build_title: &'static str,
    build_body: &'static str,
    build_quick_title: &'static str,
    build_quick_body: &'static str,
    build_login: &'static str,
    build_whoami: &'static str,
    build_fork: &'static str,
    build_sync: &'static str,
    build_preview: &'static str,
    build_matrix: &'static str,
    build_status: &'static str,
    build_artifacts: &'static str,
    build_command_title: &'static str,
    build_command_body: &'static str,
    build_command_placeholder: &'static str,
    build_run: &'static str,
    build_output_title: &'static str,
    build_output_body: &'static str,
    device_kicker: &'static str,
    device_title: &'static str,
    device_body: &'static str,
    device_connect_title: &'static str,
    device_connect_body: &'static str,
    device_serial: &'static str,
    device_port: &'static str,
    device_detect: &'static str,
    device_start: &'static str,
    device_stop: &'static str,
    device_refresh: &'static str,
    device_snapshot_title: &'static str,
    device_snapshot_body: &'static str,
    device_parsed_title: &'static str,
    device_parsed_body: &'static str,
    device_raw_title: &'static str,
    device_session: &'static str,
    device_runtime: &'static str,
    device_root: &'static str,
    device_susfs: &'static str,
    grants_title: &'static str,
    grants_body: &'static str,
    grants_manage: &'static str,
    grants_page_title: &'static str,
    grants_page_body: &'static str,
    grants_page_back: &'static str,
    grants_search_placeholder: &'static str,
    grants_show_system_apps: &'static str,
    grants_detail_title: &'static str,
    grants_detail_body: &'static str,
    grants_detail_back: &'static str,
    grants_detail_package: &'static str,
    grants_detail_type: &'static str,
    grants_detail_status: &'static str,
    grants_detail_system: &'static str,
    grants_detail_user: &'static str,
    grants_detail_allow: &'static str,
    grants_detail_revoke: &'static str,
    grants_detail_profile_json: &'static str,
    grants_summary_hidden_system: &'static str,
    grants_summary_showing_system: &'static str,
    modules_title: &'static str,
    modules_body: &'static str,
    uninstall_module: &'static str,
    run_action: &'static str,
    install_title: &'static str,
    install_body: &'static str,
    module_zip_placeholder: &'static str,
    install_module: &'static str,
    apk_placeholder: &'static str,
    install_apk: &'static str,
    image_placeholder: &'static str,
    partition_placeholder: &'static str,
    flash_image: &'static str,
    susfs_tools_title: &'static str,
    susfs_tools_body: &'static str,
    load_susfs: &'static str,
    apply_susfs: &'static str,
    export_diagnostics: &'static str,
    activity_title: &'static str,
    activity_body: &'static str,
    log_forward: &'static str,
    log_start: &'static str,
    log_ready: &'static str,
    log_timeout: &'static str,
    log_task_queued: &'static str,
    log_task_result: &'static str,
    log_downloaded: &'static str,
    log_download_failed: &'static str,
}

const ZH: Strings = Strings {
    app_title: "ABK 桌面版",
    app_subtitle: "Material 3 风格 GTK 控制台",
    nav_overview: "概览",
    nav_overview_desc: "起步和状态",
    nav_build: "构建",
    nav_build_desc: "GitHub 与工作流",
    nav_device: "设备",
    nav_device_desc: "ADB、Root 与运行态",
    brand_badge: "M3 风格预览",
    overview_kicker: "AnyBase Kernel",
    overview_title: "把构建、设备和运行态放进一个更清楚的桌面工作区",
    overview_body:
        "这版界面不再把所有控件摊在一个平面上，而是按照 Material 3 的层级做成主操作、摘要和危险操作分区。",
    overview_connect: "连接手机",
    overview_preview: "预览构建计划",
    overview_diagnostics: "导出诊断包",
    overview_summary_title: "工作区摘要",
    overview_summary_body: "先用概览页定位当前任务，再进入更细的构建页或设备页。",
    overview_live_title: "实时状态",
    overview_live_body: "连接、运行态、Root 授权和 SUSFS 状态会直接展示在这里，不再要求先读原始 JSON。",
    overview_card_build_title: "构建流程",
    overview_card_build_body: "登录 GitHub、检查 fork、同步仓库、预览构建参数，并把 CLI 输出集中到一处。",
    overview_card_device_title: "设备桥接",
    overview_card_device_body: "通过 ADB 启动手机上的 ABK agent，读取 session、runtime、root grants 与 SUSFS 快照。",
    overview_card_runtime_title: "运行态工具",
    overview_card_runtime_body: "Root 授权、模块开关、刷写与安装仍在手机执行，但桌面端提供更清晰的入口。",
    overview_card_surface_title: "界面方向",
    overview_card_surface_body: "使用导航轨、hero 卡片和高低层 surface，让桌面端更像一个产品，而不是脚本启动器。",
    build_kicker: "GitHub / Actions",
    build_title: "把常用构建动作做成主操作，把原始命令留给高级入口",
    build_body: "默认先点按钮完成登录、sync、dry-run，再在需要时写高级命令。",
    build_quick_title: "常用动作",
    build_quick_body: "这些按钮覆盖最常见的 GitHub / ABK CLI 路径。",
    build_login: "登录",
    build_whoami: "当前账号",
    build_fork: "检查 Fork",
    build_sync: "同步 Fork",
    build_preview: "Dry Run",
    build_matrix: "矩阵预览",
    build_status: "最近状态",
    build_artifacts: "查看产物",
    build_command_title: "高级命令",
    build_command_body: "需要完整 CLI 自由度时，在这里直接输入 `abk` 子命令参数。",
    build_command_placeholder: "例如：build --sub-level 162 --os-patch-level 2026-03",
    build_run: "运行",
    build_output_title: "CLI 输出",
    build_output_body: "CLI 的标准输出与标准错误会在这里汇总。",
    device_kicker: "ADB / Agent / Runtime",
    device_title: "把连接、快照和危险操作拆开，避免所有动作挤在一起",
    device_body: "先建立连接，再看快照，最后做 Root、模块、安装和 SUSFS 操作。",
    device_connect_title: "连接手机",
    device_connect_body: "先检测设备，再转发端口并启动手机端 agent。",
    device_serial: "ADB 序列号（只有一台时可留空）",
    device_port: "端口",
    device_detect: "检测设备",
    device_start: "启动 Agent",
    device_stop: "停止 Agent",
    device_refresh: "刷新快照",
    device_snapshot_title: "运行态快照",
    device_snapshot_body: "原始 JSON 还在，但被放进次一级区域，不再挡住主操作。",
    device_parsed_title: "解析后的状态",
    device_parsed_body: "先看可读的结构化状态，再按需展开原始 JSON。",
    device_raw_title: "原始快照 JSON",
    device_session: "Session",
    device_runtime: "Runtime",
    device_root: "Root 授权",
    device_susfs: "SUSFS",
    grants_title: "Root 授权",
    grants_body: "按包名允许或撤销 Root 权限。",
    grants_manage: "管理 544 个应用",
    grants_page_title: "Root 授权管理",
    grants_page_body: "搜索应用、切换系统应用显示，并从列表进入单应用详情页。",
    grants_page_back: "返回设备页",
    grants_search_placeholder: "搜索应用名或包名",
    grants_show_system_apps: "显示系统应用",
    grants_detail_title: "应用详情",
    grants_detail_body: "保留快速授权切换，把 profile 细节和原始 JSON 下沉到详情页。",
    grants_detail_back: "返回 Root 授权列表",
    grants_detail_package: "包名",
    grants_detail_type: "类型",
    grants_detail_status: "当前授权",
    grants_detail_system: "系统应用",
    grants_detail_user: "用户应用",
    grants_detail_allow: "允许 Root",
    grants_detail_revoke: "撤销 Root",
    grants_detail_profile_json: "Profile JSON",
    grants_summary_hidden_system: "默认隐藏系统应用",
    grants_summary_showing_system: "当前显示系统应用",
    modules_title: "运行时模块",
    modules_body: "启用、禁用、标记卸载或运行模块 action。",
    uninstall_module: "标记卸载",
    run_action: "运行 Action",
    install_title: "安装与刷写",
    install_body: "路径必须是手机侧可访问的路径。",
    module_zip_placeholder: "模块 ZIP 路径",
    install_module: "安装模块",
    apk_placeholder: "APK 路径",
    install_apk: "安装 APK",
    image_placeholder: "镜像路径",
    partition_placeholder: "分区",
    flash_image: "刷写镜像",
    susfs_tools_title: "SUSFS 与诊断",
    susfs_tools_body: "加载、编辑并应用 SUSFS JSON，同时导出诊断包。",
    load_susfs: "加载 SUSFS",
    apply_susfs: "应用 SUSFS JSON",
    export_diagnostics: "导出诊断包",
    activity_title: "活动日志",
    activity_body: "ADB 转发、任务队列、安装、刷写和下载结果都汇总在这里。",
    log_forward: "ADB 转发",
    log_start: "启动 Agent",
    log_ready: "Agent 已就绪",
    log_timeout: "Agent 在预期时间内没有就绪",
    log_task_queued: "任务已入队",
    log_task_result: "任务结果",
    log_downloaded: "已下载到",
    log_download_failed: "下载失败",
};

const EN: Strings = Strings {
    app_title: "ABK Desktop",
    app_subtitle: "Material 3 inspired GTK workspace",
    nav_overview: "Overview",
    nav_overview_desc: "entry and state",
    nav_build: "Build",
    nav_build_desc: "GitHub and workflows",
    nav_device: "Device",
    nav_device_desc: "ADB, root, runtime",
    brand_badge: "M3-inspired preview",
    overview_kicker: "AnyBase Kernel",
    overview_title: "Bring builds, device control, and runtime operations into one clearer desktop workspace",
    overview_body:
        "This layout stops flattening every control onto one canvas and instead uses a Material 3 style hierarchy for primary actions, summaries, and risky operations.",
    overview_connect: "Connect Phone",
    overview_preview: "Preview Build Plan",
    overview_diagnostics: "Export Diagnostics",
    overview_summary_title: "Workspace Summary",
    overview_summary_body: "Start here to orient yourself, then move into the focused build or device workspaces.",
    overview_live_title: "Live Status",
    overview_live_body: "Connection, runtime, root grants, and SUSFS state are shown here directly so raw JSON stops being the first thing you read.",
    overview_card_build_title: "Build Flow",
    overview_card_build_body: "Sign in to GitHub, check the fork, sync, preview build parameters, and keep CLI output in one place.",
    overview_card_device_title: "Device Bridge",
    overview_card_device_body: "Start the phone-side ABK agent through ADB and inspect session, runtime, root grants, and SUSFS snapshots.",
    overview_card_runtime_title: "Runtime Tools",
    overview_card_runtime_body: "Root grants, module toggles, flashing, and installs still run on the phone, but the desktop gets cleaner entry points.",
    overview_card_surface_title: "Interface Direction",
    overview_card_surface_body: "Navigation rail, hero surfaces, and layered cards make the desktop app feel like a product instead of a script launcher.",
    build_kicker: "GitHub / Actions",
    build_title: "Turn common build flows into primary actions and keep raw commands as an advanced path",
    build_body: "Use the guided actions for login, sync, and dry-run first. Drop to advanced commands only when you need full CLI freedom.",
    build_quick_title: "Common Actions",
    build_quick_body: "These buttons cover the most common GitHub and ABK CLI flows.",
    build_login: "Login",
    build_whoami: "Whoami",
    build_fork: "Fork",
    build_sync: "Sync",
    build_preview: "Dry Run",
    build_matrix: "Matrix Preview",
    build_status: "Recent Status",
    build_artifacts: "Artifacts",
    build_command_title: "Advanced Command",
    build_command_body: "When you need full CLI flexibility, type raw `abk` subcommand arguments here.",
    build_command_placeholder: "Example: build --sub-level 162 --os-patch-level 2026-03",
    build_run: "Run",
    build_output_title: "CLI Output",
    build_output_body: "Standard output and standard error from the CLI are collected here.",
    device_kicker: "ADB / Agent / Runtime",
    device_title: "Separate connection, snapshots, and risky operations so everything stops fighting for attention",
    device_body: "Connect first, inspect the snapshots second, and only then touch root, modules, installs, or SUSFS.",
    device_connect_title: "Phone Connection",
    device_connect_body: "Detect the device first, then forward the port and start the phone-side agent.",
    device_serial: "ADB serial (optional when only one device is connected)",
    device_port: "Port",
    device_detect: "Detect Devices",
    device_start: "Start Agent",
    device_stop: "Stop Agent",
    device_refresh: "Refresh Snapshots",
    device_snapshot_title: "Runtime Snapshots",
    device_snapshot_body: "Raw JSON still exists, but it now sits in a secondary surface instead of dominating the whole page.",
    device_parsed_title: "Parsed State",
    device_parsed_body: "Read the structured state first, then expand raw JSON only when you need the details.",
    device_raw_title: "Raw Snapshot JSON",
    device_session: "Session",
    device_runtime: "Runtime",
    device_root: "Root Grants",
    device_susfs: "SUSFS",
    grants_title: "Root Grants",
    grants_body: "Allow or revoke root access by package name.",
    grants_manage: "Manage 544 apps",
    grants_page_title: "Root Grant Management",
    grants_page_body: "Search apps, toggle system-app visibility, and open a dedicated detail page for each app.",
    grants_page_back: "Back to Device",
    grants_search_placeholder: "Search by app name or package",
    grants_show_system_apps: "Show system apps",
    grants_detail_title: "App Details",
    grants_detail_body: "Keep fast grant toggles in the list and move profile detail plus raw JSON into a focused page.",
    grants_detail_back: "Back to Root Grant List",
    grants_detail_package: "Package",
    grants_detail_type: "Type",
    grants_detail_status: "Grant Status",
    grants_detail_system: "System app",
    grants_detail_user: "User app",
    grants_detail_allow: "Allow Root",
    grants_detail_revoke: "Revoke Root",
    grants_detail_profile_json: "Profile JSON",
    grants_summary_hidden_system: "System apps hidden by default",
    grants_summary_showing_system: "Currently showing system apps",
    modules_title: "Runtime Modules",
    modules_body: "Enable, disable, mark uninstall, or run module actions.",
    uninstall_module: "Mark Uninstall",
    run_action: "Run Action",
    install_title: "Install and Flash",
    install_body: "Paths must refer to files available on the phone side.",
    module_zip_placeholder: "Module ZIP path",
    install_module: "Install Module",
    apk_placeholder: "APK path",
    install_apk: "Install APK",
    image_placeholder: "Image path",
    partition_placeholder: "Partition",
    flash_image: "Flash Image",
    susfs_tools_title: "SUSFS and Diagnostics",
    susfs_tools_body: "Load, edit, and apply SUSFS JSON while exporting diagnostics when needed.",
    load_susfs: "Load SUSFS",
    apply_susfs: "Apply SUSFS JSON",
    export_diagnostics: "Export Diagnostics",
    activity_title: "Activity Log",
    activity_body: "ADB forwarding, queued tasks, installs, flashes, and downloads are aggregated here.",
    log_forward: "ADB forward",
    log_start: "Start Agent",
    log_ready: "Agent ready",
    log_timeout: "Agent did not become healthy in time",
    log_task_queued: "Queued task",
    log_task_result: "Task result",
    log_downloaded: "Downloaded to",
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
    install_css();
    let strings = current_strings();
    let (sender, receiver) = mpsc::channel::<UiMessage>();

    let window = adw::ApplicationWindow::builder()
        .application(app)
        .title(strings.app_title)
        .default_width(1480)
        .default_height(940)
        .build();

    let root = gtk::Box::new(gtk::Orientation::Horizontal, 0);
    root.add_css_class("abk-root");

    let stack = gtk::Stack::new();
    stack.set_hexpand(true);
    stack.set_vexpand(true);
    stack.set_transition_type(gtk::StackTransitionType::SlideLeftRight);

    let overview_page = build_overview_page(&sender, strings);
    let build_page = build_build_page(&sender, strings);
    let device_page = build_device_page(&sender, strings);

    stack.add_titled(
        &overview_page.container,
        Some("overview"),
        strings.nav_overview,
    );
    stack.add_titled(&build_page.container, Some("build"), strings.nav_build);
    stack.add_titled(&device_page.container, Some("device"), strings.nav_device);
    stack.set_visible_child_name("overview");

    let rail = build_navigation_rail(&stack, strings);
    root.append(&rail);
    root.append(&stack);

    let overview_session_status = overview_page.session_status;
    let overview_runtime_status = overview_page.runtime_status;
    let overview_root_status = overview_page.root_status;
    let overview_susfs_status = overview_page.susfs_status;
    let build_output = build_page.output;
    let activity_output = device_page.activity_log;
    let device_port_entry = device_page.port_entry.clone();
    let device_nav_stack = device_page.nav_stack.clone();
    let session_output = device_page.session_output;
    let runtime_output = device_page.runtime_output;
    let root_output = device_page.root_output;
    let susfs_output = device_page.susfs_output;
    let susfs_editor = device_page.susfs_editor;
    let session_summary = device_page.session_summary;
    let runtime_summary = device_page.runtime_summary;
    let root_summary = device_page.root_summary;
    let susfs_summary = device_page.susfs_summary;
    let root_summary_caption = device_page.root_summary_caption;
    let root_manage_button = device_page.root_manage_button.clone();
    let root_page_count = device_page.root_page_count;
    let root_search_entry = device_page.root_search_entry.clone();
    let root_system_switch = device_page.root_system_switch.clone();
    let root_list = device_page.root_list;
    let root_detail_title = device_page.root_detail_title;
    let root_detail_icon = device_page.root_detail_icon;
    let root_detail_package = device_page.root_detail_package;
    let root_detail_type = device_page.root_detail_type;
    let root_detail_status = device_page.root_detail_status;
    let root_detail_json = device_page.root_detail_json;
    let root_detail_switch = device_page.root_detail_switch.clone();
    let module_list = device_page.module_list;
    let interaction_sender = sender.clone();
    let root_state = device_page.root_state.clone();

    {
        let nav_stack = device_nav_stack.clone();
        root_manage_button.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("root-grants");
        });
    }

    {
        let root_state = root_state.clone();
        let root_list = root_list.clone();
        let root_page_count = root_page_count.clone();
        let root_summary_caption = root_summary_caption.clone();
        let sender = interaction_sender.clone();
        let port_entry = device_port_entry.clone();
        root_search_entry.connect_search_changed(move |entry| {
            root_state.borrow_mut().search_query = entry.text().to_string();
            render_root_grant_page(
                &root_state,
                &root_list,
                &root_page_count,
                &root_summary_caption,
                &sender,
                &port_entry,
                strings,
            );
        });
    }

    {
        let root_state = root_state.clone();
        let root_list = root_list.clone();
        let root_page_count = root_page_count.clone();
        let root_summary_caption = root_summary_caption.clone();
        let sender = interaction_sender.clone();
        let port_entry = device_port_entry.clone();
        root_system_switch.connect_active_notify(move |switch| {
            let active = switch.is_active();
            let mut state = root_state.borrow_mut();
            state.show_system_apps = active;
            save_show_system_apps_pref(active);
            drop(state);
            render_root_grant_page(
                &root_state,
                &root_list,
                &root_page_count,
                &root_summary_caption,
                &sender,
                &port_entry,
                strings,
            );
        });
    }

    {
        let root_state = root_state.clone();
        let nav_stack = device_nav_stack.clone();
        let detail_title = root_detail_title.clone();
        let detail_icon = root_detail_icon.clone();
        let detail_package = root_detail_package.clone();
        let detail_type = root_detail_type.clone();
        let detail_status = root_detail_status.clone();
        let detail_json = root_detail_json.clone();
        let detail_switch = root_detail_switch.clone();
        let sender = interaction_sender.clone();
        let port_entry = device_port_entry.clone();
        root_list.connect_row_activated(move |_list, row| {
            let package = row.widget_name().to_string();
            if package.is_empty() {
                return;
            }
            root_state.borrow_mut().selected_package = Some(package.clone());
            render_root_grant_detail(
                &root_state,
                &package,
                &detail_title,
                &detail_icon,
                &detail_package,
                &detail_type,
                &detail_status,
                &detail_json,
                &detail_switch,
                &sender,
                &port_entry,
                strings,
            );
            nav_stack.set_visible_child_name("root-grant-detail");
        });
    }

    {
        let sender = interaction_sender.clone();
        let port_entry = device_port_entry.clone();
        root_detail_switch.connect_active_notify(move |switch| {
            let package = switch.widget_name().to_string();
            if package.is_empty() {
                return;
            }
            spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                let package = package.clone();
                let active = switch.is_active();
                move |client| client.set_root_grant(&package, active)
            });
        });
    }

    glib::timeout_add_local(Duration::from_millis(100), move || {
        while let Ok(message) = receiver.try_recv() {
            match message {
                UiMessage::BuildOutput(text) => build_output.set_text(&text),
                UiMessage::ActivityLog(text) => append_buffer(&activity_output, &text),
                UiMessage::SessionSnapshot(value) => {
                    if let Ok(text) = pretty_json_value(&value) {
                        session_output.set_text(&text);
                    }
                    let session_text = summarize_session(&value);
                    session_summary.set_text(&session_text);
                    overview_session_status.set_text(&session_text);
                }
                UiMessage::RuntimeSnapshot(value) => {
                    if let Ok(text) = pretty_json_value(&value) {
                        runtime_output.set_text(&text);
                    }
                    let runtime_text = summarize_runtime(&value);
                    runtime_summary.set_text(&runtime_text);
                    overview_runtime_status.set_text(&runtime_text);
                    render_module_rows(
                        &module_list,
                        &value,
                        &interaction_sender,
                        &device_port_entry,
                        strings,
                    );
                }
                UiMessage::RootSnapshot(value) => {
                    if let Ok(text) = pretty_json_value(&value) {
                        root_output.set_text(&text);
                    }
                    let root_text = summarize_root_grants(&value);
                    root_summary.set_text(&root_text);
                    overview_root_status.set_text(&root_text);
                    update_root_grant_state(&root_state, &value);
                    let show_system_apps = root_state.borrow().show_system_apps;
                    root_system_switch.set_active(show_system_apps);
                    render_root_grant_page(
                        &root_state,
                        &root_list,
                        &root_page_count,
                        &root_summary_caption,
                        &interaction_sender,
                        &device_port_entry,
                        strings,
                    );
                }
                UiMessage::RootIconLoaded(package_name, bytes) => {
                    handle_root_icon_loaded(
                        &root_state,
                        &package_name,
                        bytes,
                        &root_list,
                        &root_page_count,
                        &root_summary_caption,
                        &interaction_sender,
                        &device_port_entry,
                        strings,
                    );
                    if root_state
                        .borrow()
                        .selected_package
                        .as_deref()
                        .map(|selected| selected == package_name)
                        .unwrap_or(false)
                    {
                        render_root_grant_detail(
                            &root_state,
                            &package_name,
                            &root_detail_title,
                            &root_detail_icon,
                            &root_detail_package,
                            &root_detail_type,
                            &root_detail_status,
                            &root_detail_json,
                            &root_detail_switch,
                            &interaction_sender,
                            &device_port_entry,
                            strings,
                        );
                    }
                }
                UiMessage::SusfsSnapshot(value) => {
                    if let Ok(text) = pretty_json_value(&value) {
                        susfs_output.set_text(&text);
                    }
                    let susfs_text = summarize_susfs(&value);
                    susfs_summary.set_text(&susfs_text);
                    overview_susfs_status.set_text(&susfs_text);
                    if let Some(config) = value.get("config") {
                        if let Ok(text) = pretty_json_value(config) {
                            susfs_editor.set_text(&text);
                        }
                    }
                }
            }
        }
        glib::ControlFlow::Continue
    });

    window.set_content(Some(&root));
    window.present();
}

struct OverviewPage {
    container: gtk::ScrolledWindow,
    session_status: gtk::Label,
    runtime_status: gtk::Label,
    root_status: gtk::Label,
    susfs_status: gtk::Label,
}

struct BuildPage {
    container: gtk::ScrolledWindow,
    output: gtk::TextBuffer,
}

struct DevicePage {
    container: gtk::Stack,
    activity_log: gtk::TextBuffer,
    port_entry: gtk::Entry,
    nav_stack: gtk::Stack,
    session_output: gtk::TextBuffer,
    runtime_output: gtk::TextBuffer,
    root_output: gtk::TextBuffer,
    susfs_output: gtk::TextBuffer,
    susfs_editor: gtk::TextBuffer,
    session_summary: gtk::Label,
    runtime_summary: gtk::Label,
    root_summary: gtk::Label,
    susfs_summary: gtk::Label,
    root_summary_caption: gtk::Label,
    root_manage_button: gtk::Button,
    root_page_count: gtk::Label,
    root_search_entry: gtk::SearchEntry,
    root_system_switch: gtk::Switch,
    root_list: gtk::ListBox,
    root_detail_title: gtk::Label,
    root_detail_icon: gtk::Image,
    root_detail_package: gtk::Label,
    root_detail_type: gtk::Label,
    root_detail_status: gtk::Label,
    root_detail_json: gtk::TextBuffer,
    root_detail_switch: gtk::Switch,
    module_list: gtk::ListBox,
    root_state: Rc<RefCell<RootGrantPageState>>,
}

fn build_navigation_rail(stack: &gtk::Stack, strings: Strings) -> gtk::Box {
    let rail = gtk::Box::new(gtk::Orientation::Vertical, 20);
    rail.add_css_class("nav-rail");
    rail.set_size_request(240, -1);
    set_margin_all(&rail, 20);

    let brand = gtk::Box::new(gtk::Orientation::Vertical, 6);
    brand.add_css_class("brand-card");

    let badge = gtk::Label::new(Some(strings.brand_badge));
    badge.set_xalign(0.0);
    badge.add_css_class("state-chip");
    badge.add_css_class("caption");
    brand.append(&badge);

    let title = gtk::Label::new(Some(strings.app_title));
    title.set_xalign(0.0);
    title.add_css_class("brand-title");
    brand.append(&title);

    let subtitle = gtk::Label::new(Some(strings.app_subtitle));
    subtitle.set_xalign(0.0);
    subtitle.add_css_class("brand-body");
    subtitle.set_wrap(true);
    brand.append(&subtitle);
    rail.append(&brand);

    let nav = gtk::ListBox::new();
    nav.add_css_class("nav-list");
    nav.set_selection_mode(gtk::SelectionMode::Single);

    for (icon, title, subtitle) in [
        (
            "view-grid-symbolic",
            strings.nav_overview,
            strings.nav_overview_desc,
        ),
        (
            "system-run-symbolic",
            strings.nav_build,
            strings.nav_build_desc,
        ),
        (
            "smartphone-symbolic",
            strings.nav_device,
            strings.nav_device_desc,
        ),
    ] {
        nav.append(&build_nav_row(icon, title, subtitle));
    }

    {
        let stack = stack.clone();
        nav.connect_row_selected(move |_, row| {
            let Some(row) = row else { return };
            match row.index() {
                0 => stack.set_visible_child_name("overview"),
                1 => stack.set_visible_child_name("build"),
                2 => stack.set_visible_child_name("device"),
                _ => {}
            }
        });
    }

    if let Some(first) = nav.row_at_index(0) {
        nav.select_row(Some(&first));
    }

    rail.append(&nav);
    rail
}

fn build_nav_row(icon: &str, title: &str, subtitle: &str) -> gtk::ListBoxRow {
    let row = gtk::ListBoxRow::new();
    row.set_selectable(true);
    row.set_activatable(true);

    let shell = gtk::Box::new(gtk::Orientation::Horizontal, 14);
    shell.add_css_class("nav-shell");
    set_margin_all(&shell, 10);

    let image = gtk::Image::from_icon_name(icon);
    image.add_css_class("nav-icon");
    shell.append(&image);

    let text = gtk::Box::new(gtk::Orientation::Vertical, 2);

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.add_css_class("nav-title");
    text.append(&title_label);

    let subtitle_label = gtk::Label::new(Some(subtitle));
    subtitle_label.set_xalign(0.0);
    subtitle_label.add_css_class("nav-subtitle");
    text.append(&subtitle_label);

    shell.append(&text);
    row.set_child(Some(&shell));
    row
}

fn build_overview_page(sender: &Sender<UiMessage>, strings: Strings) -> OverviewPage {
    let (container, body) = new_page_shell();

    let hero = hero_card(
        strings.overview_kicker,
        strings.overview_title,
        strings.overview_body,
    );
    let hero_actions = gtk::Box::new(gtk::Orientation::Horizontal, 10);

    let connect = gtk::Button::with_label(strings.overview_connect);
    connect.add_css_class("suggested-action");
    connect.add_css_class("pill");
    connect.add_css_class("big-button");
    {
        let sender = sender.clone();
        connect.connect_clicked(move |_| {
            spawn_agent_start(String::new(), 48765, sender.clone(), strings);
        });
    }

    let preview = gtk::Button::with_label(strings.overview_preview);
    preview.add_css_class("big-button");
    preview.add_css_class("tonal-button");
    {
        let sender = sender.clone();
        preview.connect_clicked(move |_| {
            spawn_cli(
                "build --dry-run --sub-level 162 --os-patch-level 2026-03".into(),
                sender.clone(),
            );
        });
    }

    let diagnostics = gtk::Button::with_label(strings.overview_diagnostics);
    diagnostics.add_css_class("big-button");
    diagnostics.add_css_class("tonal-button");
    {
        let sender = sender.clone();
        diagnostics.connect_clicked(move |_| {
            spawn_agent_task_call(
                48765,
                sender.clone(),
                move |client| client.export_diagnostics(),
                true,
                strings,
            );
        });
    }

    hero_actions.append(&connect);
    hero_actions.append(&preview);
    hero_actions.append(&diagnostics);
    hero.append(&hero_actions);
    body.append(&hero);

    let live_card = surface_card(strings.overview_live_title, strings.overview_live_body);
    let live_grid = gtk::Grid::new();
    live_grid.set_column_spacing(12);
    live_grid.set_row_spacing(12);
    live_grid.set_column_homogeneous(true);

    let session_status = status_block("Device Session", "Not connected yet");
    let runtime_status = status_block("Runtime", "No runtime snapshot yet");
    let root_status = status_block("Root Grants", "No root grant snapshot yet");
    let susfs_status = status_block("SUSFS", "No SUSFS snapshot yet");

    live_grid.attach(&session_status.0, 0, 0, 1, 1);
    live_grid.attach(&runtime_status.0, 1, 0, 1, 1);
    live_grid.attach(&root_status.0, 0, 1, 1, 1);
    live_grid.attach(&susfs_status.0, 1, 1, 1, 1);
    live_card.append(&live_grid);
    body.append(&live_card);

    let section = section_header(
        strings.overview_summary_title,
        strings.overview_summary_body,
    );
    body.append(&section);

    let grid = gtk::Grid::new();
    grid.set_column_spacing(16);
    grid.set_row_spacing(16);
    grid.set_column_homogeneous(true);

    let build_card = summary_card(
        "system-run-symbolic",
        strings.overview_card_build_title,
        strings.overview_card_build_body,
    );
    let device_card = summary_card(
        "smartphone-symbolic",
        strings.overview_card_device_title,
        strings.overview_card_device_body,
    );
    let runtime_card = summary_card(
        "applications-system-symbolic",
        strings.overview_card_runtime_title,
        strings.overview_card_runtime_body,
    );
    let surface_card_widget = summary_card(
        "preferences-desktop-theme-symbolic",
        strings.overview_card_surface_title,
        strings.overview_card_surface_body,
    );

    grid.attach(&build_card, 0, 0, 1, 1);
    grid.attach(&device_card, 1, 0, 1, 1);
    grid.attach(&runtime_card, 0, 1, 1, 1);
    grid.attach(&surface_card_widget, 1, 1, 1, 1);
    body.append(&grid);

    OverviewPage {
        container,
        session_status: session_status.1,
        runtime_status: runtime_status.1,
        root_status: root_status.1,
        susfs_status: susfs_status.1,
    }
}

fn build_build_page(sender: &Sender<UiMessage>, strings: Strings) -> BuildPage {
    let (container, body) = new_page_shell();
    body.append(&hero_card(
        strings.build_kicker,
        strings.build_title,
        strings.build_body,
    ));

    let quick_card = surface_card(strings.build_quick_title, strings.build_quick_body);
    let quick_flow = gtk::FlowBox::new();
    quick_flow.set_selection_mode(gtk::SelectionMode::None);
    quick_flow.set_row_spacing(10);
    quick_flow.set_column_spacing(10);
    quick_flow.set_max_children_per_line(4);

    for (label, command) in [
        (strings.build_login, "login"),
        (strings.build_whoami, "whoami"),
        (strings.build_fork, "fork"),
        (strings.build_sync, "sync"),
        (
            strings.build_preview,
            "build --dry-run --sub-level 162 --os-patch-level 2026-03",
        ),
        (strings.build_matrix, "build --matrix both --dry-run"),
        (strings.build_status, "status"),
        (strings.build_artifacts, "artifacts --run-id 0"),
    ] {
        let button = gtk::Button::with_label(label);
        button.add_css_class("pill");
        button.add_css_class("tonal-button");
        let sender = sender.clone();
        let command = command.to_string();
        button.connect_clicked(move |_| spawn_cli(command.clone(), sender.clone()));
        quick_flow.insert(&button, -1);
    }
    quick_card.append(&quick_flow);
    body.append(&quick_card);

    let command_card = surface_card(strings.build_command_title, strings.build_command_body);
    let command_entry = gtk::Entry::new();
    command_entry.set_hexpand(true);
    command_entry.set_placeholder_text(Some(strings.build_command_placeholder));
    command_entry.add_css_class("material-entry");

    let run_button = gtk::Button::with_label(strings.build_run);
    run_button.add_css_class("suggested-action");
    run_button.add_css_class("pill");
    {
        let sender = sender.clone();
        let entry = command_entry.clone();
        run_button.connect_clicked(move |_| {
            let raw = entry.text().to_string();
            spawn_cli(raw, sender.clone());
        });
    }

    let command_row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    command_row.append(&command_entry);
    command_row.append(&run_button);
    command_card.append(&command_row);
    body.append(&command_card);

    let output_card = surface_card(strings.build_output_title, strings.build_output_body);
    let output = new_text_buffer();
    let output_view = new_text_view(&output, false);
    output_view.add_css_class("console-pane");
    let output_scroll = new_scroller(&output_view);
    output_scroll.set_min_content_height(420);
    output_card.append(&output_scroll);
    body.append(&output_card);

    BuildPage { container, output }
}

fn build_device_page(sender: &Sender<UiMessage>, strings: Strings) -> DevicePage {
    let nav_stack = gtk::Stack::new();
    nav_stack.set_hexpand(true);
    nav_stack.set_vexpand(true);
    nav_stack.set_transition_type(gtk::StackTransitionType::SlideLeftRight);

    let (main_container, body) = new_page_shell();
    body.append(&hero_card(
        strings.device_kicker,
        strings.device_title,
        strings.device_body,
    ));

    let connect_card = surface_card(strings.device_connect_title, strings.device_connect_body);
    let serial_entry = gtk::Entry::new();
    serial_entry.set_hexpand(true);
    serial_entry.set_placeholder_text(Some(strings.device_serial));
    serial_entry.add_css_class("material-entry");

    let port_entry = gtk::Entry::new();
    port_entry.set_width_chars(8);
    port_entry.set_text("48765");
    port_entry.set_placeholder_text(Some(strings.device_port));
    port_entry.add_css_class("material-entry");

    let detect_button = gtk::Button::with_label(strings.device_detect);
    detect_button.add_css_class("pill");
    {
        let sender = sender.clone();
        detect_button.connect_clicked(move |_| {
            let sender = sender.clone();
            thread::spawn(move || {
                let result = run_command(&build_adb_detect_command())
                    .unwrap_or_else(|error| format!("{error:#}"));
                let _ = sender.send(UiMessage::ActivityLog(result));
            });
        });
    }

    let start_button = gtk::Button::with_label(strings.device_start);
    start_button.add_css_class("suggested-action");
    start_button.add_css_class("pill");
    {
        let sender = sender.clone();
        let serial_entry = serial_entry.clone();
        let port_entry = port_entry.clone();
        start_button.connect_clicked(move |_| {
            spawn_agent_start(
                serial_entry.text().to_string(),
                parse_port(&port_entry.text()),
                sender.clone(),
                strings,
            );
        });
    }

    let stop_button = gtk::Button::with_label(strings.device_stop);
    stop_button.add_css_class("pill");
    {
        let sender = sender.clone();
        let serial_entry = serial_entry.clone();
        stop_button.connect_clicked(move |_| {
            let serial = serial_entry.text().to_string();
            let sender = sender.clone();
            thread::spawn(move || {
                let result = run_command(&build_adb_stop_agent_command(&serial))
                    .unwrap_or_else(|error| format!("{error:#}"));
                let _ = sender.send(UiMessage::ActivityLog(result));
            });
        });
    }

    let refresh_button = gtk::Button::with_label(strings.device_refresh);
    refresh_button.add_css_class("pill");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        refresh_button.connect_clicked(move |_| {
            spawn_agent_refresh(parse_port(&port_entry.text()), sender.clone());
        });
    }

    let connect_row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    connect_row.append(&serial_entry);
    connect_row.append(&port_entry);
    connect_row.append(&detect_button);
    connect_row.append(&start_button);
    connect_row.append(&stop_button);
    connect_row.append(&refresh_button);
    connect_card.append(&connect_row);
    body.append(&connect_card);

    let snapshot_card = surface_card(strings.device_snapshot_title, strings.device_snapshot_body);
    let parsed_card = surface_card(strings.device_parsed_title, strings.device_parsed_body);
    let parsed_grid = gtk::Grid::new();
    parsed_grid.set_column_spacing(12);
    parsed_grid.set_row_spacing(12);
    parsed_grid.set_column_homogeneous(true);

    let session_summary = status_block("Session", "Not loaded");
    let runtime_summary = status_block("Runtime", "Not loaded");
    let root_summary = status_block("Root Grants", "Not loaded");
    let susfs_summary = status_block("SUSFS", "Not loaded");

    parsed_grid.attach(&session_summary.0, 0, 0, 1, 1);
    parsed_grid.attach(&runtime_summary.0, 1, 0, 1, 1);
    parsed_grid.attach(&root_summary.0, 0, 1, 1, 1);
    parsed_grid.attach(&susfs_summary.0, 1, 1, 1, 1);
    parsed_card.append(&parsed_grid);
    snapshot_card.append(&parsed_card);

    let raw_expander = gtk::Expander::new(Some(strings.device_raw_title));
    raw_expander.set_expanded(false);
    let notebook = gtk::Notebook::new();
    notebook.add_css_class("snapshot-notebook");

    let session_output = new_text_buffer();
    let session_view = new_text_view(&session_output, false);
    session_view.add_css_class("console-pane");
    notebook.append_page(
        &new_scroller(&session_view),
        Some(&gtk::Label::new(Some(strings.device_session))),
    );

    let runtime_output = new_text_buffer();
    let runtime_view = new_text_view(&runtime_output, false);
    runtime_view.add_css_class("console-pane");
    notebook.append_page(
        &new_scroller(&runtime_view),
        Some(&gtk::Label::new(Some(strings.device_runtime))),
    );

    let root_output = new_text_buffer();
    let root_view = new_text_view(&root_output, false);
    root_view.add_css_class("console-pane");
    notebook.append_page(
        &new_scroller(&root_view),
        Some(&gtk::Label::new(Some(strings.device_root))),
    );

    let susfs_output = new_text_buffer();
    let susfs_view = new_text_view(&susfs_output, false);
    susfs_view.add_css_class("console-pane");
    notebook.append_page(
        &new_scroller(&susfs_view),
        Some(&gtk::Label::new(Some(strings.device_susfs))),
    );
    raw_expander.set_child(Some(&notebook));
    snapshot_card.append(&raw_expander);
    body.append(&snapshot_card);

    let ops_grid = gtk::Grid::new();
    ops_grid.set_column_spacing(16);
    ops_grid.set_row_spacing(16);
    ops_grid.set_column_homogeneous(true);

    let prefs = load_desktop_prefs();
    let root_state = Rc::new(RefCell::new(RootGrantPageState {
        show_system_apps: prefs.show_system_apps.unwrap_or(false),
        ..RootGrantPageState::default()
    }));

    let grants_card = surface_card(strings.grants_title, strings.grants_body);
    let root_summary_caption = gtk::Label::new(Some(strings.grants_summary_hidden_system));
    root_summary_caption.set_xalign(0.0);
    root_summary_caption.add_css_class("list-row-subtitle");
    grants_card.append(&root_summary_caption);

    let root_manage_button = gtk::Button::with_label(strings.grants_manage);
    root_manage_button.add_css_class("suggested-action");
    root_manage_button.add_css_class("pill");
    grants_card.append(&root_manage_button);

    let modules_card = surface_card(strings.modules_title, strings.modules_body);
    let module_list = gtk::ListBox::new();
    module_list.add_css_class("plain-list");
    let module_scroll = new_scroller(&module_list);
    module_scroll.set_min_content_height(230);
    modules_card.append(&module_scroll);

    let install_card = surface_card(strings.install_title, strings.install_body);

    let module_zip_entry = gtk::Entry::new();
    module_zip_entry.set_hexpand(true);
    module_zip_entry.set_placeholder_text(Some(strings.module_zip_placeholder));
    module_zip_entry.add_css_class("material-entry");

    let install_module = gtk::Button::with_label(strings.install_module);
    install_module.add_css_class("pill");
    install_module.add_css_class("tonal-button");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        let module_zip_entry = module_zip_entry.clone();
        install_module.connect_clicked(move |_| {
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

    let module_row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    module_row.append(&module_zip_entry);
    module_row.append(&install_module);
    install_card.append(&module_row);

    let apk_entry = gtk::Entry::new();
    apk_entry.set_hexpand(true);
    apk_entry.set_placeholder_text(Some(strings.apk_placeholder));
    apk_entry.add_css_class("material-entry");

    let install_apk = gtk::Button::with_label(strings.install_apk);
    install_apk.add_css_class("pill");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        let apk_entry = apk_entry.clone();
        install_apk.connect_clicked(move |_| {
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

    let apk_row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    apk_row.append(&apk_entry);
    apk_row.append(&install_apk);
    install_card.append(&apk_row);

    let image_entry = gtk::Entry::new();
    image_entry.set_hexpand(true);
    image_entry.set_placeholder_text(Some(strings.image_placeholder));
    image_entry.add_css_class("material-entry");

    let partition_entry = gtk::Entry::new();
    partition_entry.set_width_chars(8);
    partition_entry.set_text("boot");
    partition_entry.set_placeholder_text(Some(strings.partition_placeholder));
    partition_entry.add_css_class("material-entry");

    let flash = gtk::Button::with_label(strings.flash_image);
    flash.add_css_class("pill");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        let image_entry = image_entry.clone();
        let partition_entry = partition_entry.clone();
        flash.connect_clicked(move |_| {
            let image = image_entry.text().to_string();
            let partition = partition_entry.text().to_string();
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.flash_image(&image, &partition),
                false,
                strings,
            );
        });
    }

    let flash_row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    flash_row.append(&image_entry);
    flash_row.append(&partition_entry);
    flash_row.append(&flash);
    install_card.append(&flash_row);

    let susfs_tools = surface_card(strings.susfs_tools_title, strings.susfs_tools_body);
    let susfs_controls = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    let susfs_editor = new_text_buffer();

    let load_susfs = gtk::Button::with_label(strings.load_susfs);
    load_susfs.add_css_class("pill");
    load_susfs.add_css_class("tonal-button");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        load_susfs.connect_clicked(move |_| {
            let sender = sender.clone();
            let port = parse_port(&port_entry.text());
            thread::spawn(move || {
                let client = AgentClient::new("127.0.0.1", port);
                match client.susfs_json() {
                    Ok(value) => {
                        let _ = sender.send(UiMessage::SusfsSnapshot(value));
                    }
                    Err(error) => {
                        let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
                    }
                }
            });
        });
    }

    let apply_susfs = gtk::Button::with_label(strings.apply_susfs);
    apply_susfs.add_css_class("pill");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        let susfs_editor_clone = susfs_editor.clone();
        apply_susfs.connect_clicked(move |_| {
            let body = buffer_text(&susfs_editor_clone);
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.apply_susfs_json(&body),
                false,
                strings,
            );
        });
    }

    let export = gtk::Button::with_label(strings.export_diagnostics);
    export.add_css_class("pill");
    export.add_css_class("tonal-button");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        export.connect_clicked(move |_| {
            spawn_agent_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.export_diagnostics(),
                true,
                strings,
            );
        });
    }

    susfs_controls.append(&load_susfs);
    susfs_controls.append(&apply_susfs);
    susfs_controls.append(&export);
    susfs_tools.append(&susfs_controls);

    let editor_view = new_text_view(&susfs_editor, true);
    editor_view.add_css_class("console-pane");
    let editor_scroll = new_scroller(&editor_view);
    editor_scroll.set_min_content_height(240);
    susfs_tools.append(&editor_scroll);

    ops_grid.attach(&grants_card, 0, 0, 1, 1);
    ops_grid.attach(&modules_card, 1, 0, 1, 1);
    ops_grid.attach(&install_card, 0, 1, 1, 1);
    ops_grid.attach(&susfs_tools, 1, 1, 1, 1);
    body.append(&ops_grid);

    let activity = surface_card(strings.activity_title, strings.activity_body);
    let activity_log = new_text_buffer();
    let activity_view = new_text_view(&activity_log, false);
    activity_view.add_css_class("console-pane");
    let activity_scroll = new_scroller(&activity_view);
    activity_scroll.set_min_content_height(260);
    activity.append(&activity_scroll);
    body.append(&activity);

    let (root_page_container, root_page_body) = new_page_shell();
    let root_page_header = hero_card(
        strings.grants_title,
        strings.grants_page_title,
        strings.grants_page_body,
    );
    let root_page_back = gtk::Button::with_label(strings.grants_page_back);
    root_page_back.add_css_class("pill");
    {
        let nav_stack = nav_stack.clone();
        root_page_back.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("main");
        });
    }
    root_page_header.append(&root_page_back);
    root_page_body.append(&root_page_header);

    let root_filter_card = surface_card(strings.grants_title, strings.grants_body);
    let root_page_count = gtk::Label::new(Some("0"));
    root_page_count.set_xalign(0.0);
    root_page_count.add_css_class("card-title");
    root_filter_card.append(&root_page_count);

    let root_search_entry = gtk::SearchEntry::new();
    root_search_entry.set_placeholder_text(Some(strings.grants_search_placeholder));
    root_search_entry.add_css_class("material-entry");

    let root_system_switch = gtk::Switch::new();
    root_system_switch.set_active(root_state.borrow().show_system_apps);
    root_system_switch.set_valign(gtk::Align::Center);

    let root_system_row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    let root_system_label = gtk::Label::new(Some(strings.grants_show_system_apps));
    root_system_label.set_xalign(0.0);
    root_system_label.set_hexpand(true);
    root_system_row.append(&root_system_label);
    root_system_row.append(&root_system_switch);

    root_filter_card.append(&root_search_entry);
    root_filter_card.append(&root_system_row);
    root_page_body.append(&root_filter_card);

    let root_list_card = surface_card(strings.grants_title, strings.grants_body);
    let root_list = gtk::ListBox::new();
    root_list.add_css_class("plain-list");
    root_list.set_activate_on_single_click(true);
    let root_scroll = new_scroller(&root_list);
    root_scroll.set_min_content_height(520);
    root_list_card.append(&root_scroll);
    root_page_body.append(&root_list_card);

    let (root_detail_container, root_detail_body) = new_page_shell();
    let root_detail_header = hero_card(
        strings.grants_title,
        strings.grants_detail_title,
        strings.grants_detail_body,
    );
    let root_detail_back = gtk::Button::with_label(strings.grants_detail_back);
    root_detail_back.add_css_class("pill");
    {
        let nav_stack = nav_stack.clone();
        root_detail_back.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("root-grants");
        });
    }
    root_detail_header.append(&root_detail_back);
    root_detail_body.append(&root_detail_header);

    let root_detail_card = surface_card(strings.grants_detail_title, strings.grants_detail_body);
    let detail_row = gtk::Box::new(gtk::Orientation::Horizontal, 16);
    let root_detail_icon = gtk::Image::from_icon_name("application-x-executable-symbolic");
    root_detail_icon.add_css_class("detail-icon");
    root_detail_icon.set_pixel_size(48);
    detail_row.append(&root_detail_icon);

    let detail_text = gtk::Box::new(gtk::Orientation::Vertical, 8);
    let root_detail_title = gtk::Label::new(Some("-"));
    root_detail_title.set_xalign(0.0);
    root_detail_title.add_css_class("card-title");
    let root_detail_package = gtk::Label::new(Some("-"));
    root_detail_package.set_xalign(0.0);
    root_detail_package.add_css_class("list-row-subtitle");
    let root_detail_type = gtk::Label::new(Some("-"));
    root_detail_type.set_xalign(0.0);
    root_detail_type.add_css_class("list-row-subtitle");
    let root_detail_status = gtk::Label::new(Some("-"));
    root_detail_status.set_xalign(0.0);
    root_detail_status.add_css_class("list-row-subtitle");
    detail_text.append(&root_detail_title);
    detail_text.append(&root_detail_package);
    detail_text.append(&root_detail_type);
    detail_text.append(&root_detail_status);
    detail_row.append(&detail_text);

    let root_detail_switch = gtk::Switch::new();
    root_detail_switch.set_valign(gtk::Align::Center);
    detail_row.append(&root_detail_switch);

    root_detail_card.append(&detail_row);
    root_detail_body.append(&root_detail_card);

    let profile_card = surface_card(
        strings.grants_detail_profile_json,
        strings.grants_detail_body,
    );
    let root_detail_json = new_text_buffer();
    let root_detail_json_view = new_text_view(&root_detail_json, false);
    root_detail_json_view.add_css_class("console-pane");
    let root_detail_json_scroll = new_scroller(&root_detail_json_view);
    root_detail_json_scroll.set_min_content_height(360);
    profile_card.append(&root_detail_json_scroll);
    root_detail_body.append(&profile_card);

    nav_stack.add_titled(&main_container, Some("main"), strings.device_title);
    nav_stack.add_titled(
        &root_page_container,
        Some("root-grants"),
        strings.grants_page_title,
    );
    nav_stack.add_titled(
        &root_detail_container,
        Some("root-grant-detail"),
        strings.grants_detail_title,
    );
    nav_stack.set_visible_child_name("main");

    DevicePage {
        container: nav_stack.clone(),
        activity_log,
        port_entry,
        nav_stack,
        session_output,
        runtime_output,
        root_output,
        susfs_output,
        susfs_editor,
        session_summary: session_summary.1,
        runtime_summary: runtime_summary.1,
        root_summary: root_summary.1,
        susfs_summary: susfs_summary.1,
        root_summary_caption,
        root_manage_button,
        root_page_count,
        root_search_entry,
        root_system_switch,
        root_list,
        root_detail_title,
        root_detail_icon,
        root_detail_package,
        root_detail_type,
        root_detail_status,
        root_detail_json,
        root_detail_switch,
        module_list,
        root_state,
    }
}

fn spawn_cli(raw: String, sender: Sender<UiMessage>) {
    thread::spawn(move || {
        let message = match build_cli_command(&raw).and_then(|spec| run_command(&spec)) {
            Ok(output) => output,
            Err(error) => format!("{error:#}"),
        };
        let _ = sender.send(UiMessage::BuildOutput(message));
    });
}

fn spawn_agent_start(serial: String, port: u16, sender: Sender<UiMessage>, strings: Strings) {
    thread::spawn(move || {
        let forward = run_command(&build_adb_forward_command(&serial, port));
        let start = run_command(&build_adb_start_agent_command(&serial, port));
        let summary = format!(
            "{}: {}\n\n{}: {}",
            strings.log_forward,
            forward.unwrap_or_else(|error| format!("{error:#}")),
            strings.log_start,
            start.unwrap_or_else(|error| format!("{error:#}")),
        );
        let _ = sender.send(UiMessage::ActivityLog(summary));
        for _ in 0..20 {
            let client = AgentClient::new("127.0.0.1", port);
            match client.health() {
                Ok(health) => {
                    let _ = sender.send(UiMessage::ActivityLog(format!(
                        "{}\n{}",
                        strings.log_ready, health
                    )));
                    refresh_agent(client, &sender);
                    return;
                }
                Err(_) => thread::sleep(Duration::from_millis(500)),
            }
        }
        let _ = sender.send(UiMessage::ActivityLog(strings.log_timeout.into()));
    });
}

fn spawn_agent_refresh(port: u16, sender: Sender<UiMessage>) {
    thread::spawn(move || refresh_agent(AgentClient::new("127.0.0.1", port), &sender));
}

fn refresh_agent(client: AgentClient, sender: &Sender<UiMessage>) {
    match client.session_json() {
        Ok(value) => {
            let _ = sender.send(UiMessage::SessionSnapshot(value));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
        }
    }
    match client.runtime_json() {
        Ok(value) => {
            let _ = sender.send(UiMessage::RuntimeSnapshot(value));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
        }
    }
    match client.root_grants_json() {
        Ok(value) => {
            let _ = sender.send(UiMessage::RootSnapshot(value));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
        }
    }
    match client.susfs_json() {
        Ok(value) => {
            let _ = sender.send(UiMessage::SusfsSnapshot(value));
        }
        Err(error) => {
            let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
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
        let _ = sender.send(UiMessage::ActivityLog(message));
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
                let _ = sender.send(UiMessage::ActivityLog(format!(
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
                                    strings.log_downloaded,
                                    path.display()
                                )),
                                Err(error) => lines
                                    .push(format!("{}: {error:#}", strings.log_download_failed)),
                            }
                        }
                        let _ = sender.send(UiMessage::ActivityLog(lines.join("\n\n")));
                        refresh_agent(client, &sender);
                    }
                    Err(error) => {
                        let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
                    }
                }
            }
            Err(error) => {
                let _ = sender.send(UiMessage::ActivityLog(format!("{error:#}")));
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
        ZH
    } else {
        EN
    }
}

fn install_css() {
    let provider = gtk::CssProvider::new();
    provider.load_from_data(APP_CSS);
    if let Some(display) = gdk::Display::default() {
        gtk::style_context_add_provider_for_display(
            &display,
            &provider,
            gtk::STYLE_PROVIDER_PRIORITY_APPLICATION,
        );
    }
}

fn new_page_shell() -> (gtk::ScrolledWindow, gtk::Box) {
    let body = gtk::Box::new(gtk::Orientation::Vertical, 20);
    body.add_css_class("page-body");
    set_margin_all(&body, 28);

    let clamp = adw::Clamp::new();
    clamp.set_maximum_size(1180);
    clamp.set_tightening_threshold(860);
    clamp.set_child(Some(&body));

    let scroller = gtk::ScrolledWindow::new();
    scroller.set_policy(gtk::PolicyType::Automatic, gtk::PolicyType::Automatic);
    scroller.set_vexpand(true);
    scroller.set_child(Some(&clamp));
    (scroller, body)
}

fn hero_card(kicker: &str, title: &str, body: &str) -> gtk::Box {
    let card = gtk::Box::new(gtk::Orientation::Vertical, 12);
    card.add_css_class("hero-card");

    let kicker_label = gtk::Label::new(Some(kicker));
    kicker_label.set_xalign(0.0);
    kicker_label.add_css_class("hero-kicker");
    card.append(&kicker_label);

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.set_wrap(true);
    title_label.add_css_class("hero-title");
    card.append(&title_label);

    let body_label = gtk::Label::new(Some(body));
    body_label.set_xalign(0.0);
    body_label.set_wrap(true);
    body_label.add_css_class("hero-body");
    card.append(&body_label);

    card
}

fn section_header(title: &str, body: &str) -> gtk::Box {
    let section = gtk::Box::new(gtk::Orientation::Vertical, 6);

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.add_css_class("section-title");
    section.append(&title_label);

    let body_label = gtk::Label::new(Some(body));
    body_label.set_xalign(0.0);
    body_label.set_wrap(true);
    body_label.add_css_class("section-body");
    section.append(&body_label);

    section
}

fn summary_card(icon: &str, title: &str, body: &str) -> gtk::Box {
    let card = surface_card(title, body);

    let image = gtk::Image::from_icon_name(icon);
    image.add_css_class("summary-icon");
    card.prepend(&image);
    card
}

fn surface_card(title: &str, body: &str) -> gtk::Box {
    let card = gtk::Box::new(gtk::Orientation::Vertical, 10);
    card.add_css_class("surface-card");

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.set_wrap(true);
    title_label.add_css_class("card-title");
    card.append(&title_label);

    let body_label = gtk::Label::new(Some(body));
    body_label.set_xalign(0.0);
    body_label.set_wrap(true);
    body_label.add_css_class("card-body");
    card.append(&body_label);

    card
}

fn status_block(title: &str, initial: &str) -> (gtk::Box, gtk::Label) {
    let card = gtk::Box::new(gtk::Orientation::Vertical, 6);
    card.add_css_class("status-block");

    let title_label = gtk::Label::new(Some(title));
    title_label.set_xalign(0.0);
    title_label.add_css_class("status-title");
    card.append(&title_label);

    let value_label = gtk::Label::new(Some(initial));
    value_label.set_xalign(0.0);
    value_label.set_wrap(true);
    value_label.add_css_class("status-value");
    card.append(&value_label);

    (card, value_label)
}

fn summarize_session(value: &Value) -> String {
    let version = json_str_any_recursive(value, &["appVersion", "app_version"]);
    let manager = json_str_any_recursive(value, &["managerAccessKind", "manager_access_kind"]);
    let root = json_bool_opt_recursive(value, &["rootGranted", "root_granted"]);
    let caps = json_array_len_recursive(value, &["capabilities"]);
    if version.is_none() && manager.is_none() && root.is_none() && caps.is_none() {
        return diagnostic_or_keys(
            value,
            &["error", "managerDiagnostic", "manager_diagnostic"],
            "Session payload unavailable",
        );
    }
    format!(
        "ABK {} · {} · {} · {} capabilities",
        version.unwrap_or("?"),
        manager.unwrap_or("unknown"),
        root.map(|granted| bool_label(granted, "root", "no root"))
            .unwrap_or("root unknown"),
        caps.unwrap_or(0)
    )
}

fn summarize_runtime(value: &Value) -> String {
    let root = json_bool_opt_recursive(value, &["rootGranted", "root_granted"]);
    let manager =
        json_str_any_recursive(value, &["display_name", "displayName"]).unwrap_or("inactive");
    let modules = find_array_of_objects(value, is_runtime_module_record).len();
    let build =
        json_str_any_recursive(value, &["kernel_version", "kernelVersion"]).unwrap_or("unknown");
    if manager == "inactive" && modules == 0 && build == "unknown" {
        return diagnostic_or_keys(
            value,
            &["managerDiagnostic", "manager_diagnostic", "error"],
            "Runtime payload unavailable",
        );
    }
    format!(
        "{manager} · {modules} modules · kernel {build} · {}",
        root.map(|granted| bool_label(granted, "root", "no root"))
            .unwrap_or("root unknown")
    )
}

fn summarize_root_grants(value: &Value) -> String {
    let root = json_bool_opt_recursive(value, &["rootGranted", "root_granted"]);
    let diagnostic =
        json_str_any_recursive(value, &["managerDiagnostic", "manager_diagnostic", "error"]);
    let apps = find_array_of_objects(value, is_root_grant_record);
    if apps.is_empty() {
        return match root {
            Some(false) => "Root not granted on device".to_string(),
            _ => diagnostic
                .map(ToString::to_string)
                .unwrap_or_else(|| payload_keys_message(value, "No root grant entries reported")),
        };
    }
    let allowed = apps
        .iter()
        .filter(|app| {
            app.get("profile")
                .and_then(|v| v.get("allowSu"))
                .and_then(Value::as_bool)
                .unwrap_or(false)
        })
        .count();
    format!("{allowed} allowed · {} visible apps", apps.len())
}

fn summarize_susfs(value: &Value) -> String {
    let root = json_bool_opt_recursive(value, &["rootGranted", "root_granted"]);
    if matches!(root, Some(false)) {
        return "Root not granted on device".to_string();
    }
    let available = json_bool_opt_recursive(value, &["available"]).unwrap_or(false);
    let kernel =
        json_str_any_recursive(value, &["kernelVersion", "kernel_version"]).unwrap_or("unknown");
    let path_rules = json_array_len_recursive(value, &["pathRules", "path_rules"]).unwrap_or(0);
    format!(
        "{} · kernel {kernel} · {path_rules} path rules",
        if available {
            "available"
        } else {
            "unavailable"
        }
    )
}

fn update_root_grant_state(state: &Rc<RefCell<RootGrantPageState>>, value: &Value) {
    let fresh = find_array_of_objects(value, is_root_grant_record)
        .into_iter()
        .filter_map(|app| {
            let package = json_str_any_recursive(&app, &["packageName", "package_name"])?;
            let label = json_str_any_recursive(&app, &["label"]).unwrap_or(package);
            let allow_su = app
                .get("profile")
                .and_then(|v| v.get("allowSu"))
                .and_then(Value::as_bool)
                .unwrap_or(false);
            Some(RootGrantEntry {
                package_name: package.to_string(),
                label: label.to_string(),
                is_system_app: app
                    .get("isSystemApp")
                    .or_else(|| app.get("is_system_app"))
                    .and_then(Value::as_bool)
                    .unwrap_or(false),
                allow_su,
                raw: app,
            })
        })
        .collect::<Vec<_>>();

    let mut guard = state.borrow_mut();
    if guard.entries.is_empty() {
        guard.entries = fresh;
        sort_root_entries(&mut guard.entries);
        return;
    }

    let old_order: Vec<String> = guard
        .entries
        .iter()
        .map(|entry| entry.package_name.clone())
        .collect();
    let mut by_package: HashMap<String, RootGrantEntry> = fresh
        .into_iter()
        .map(|entry| (entry.package_name.clone(), entry))
        .collect();

    let mut merged = Vec::new();
    for package in old_order {
        if let Some(entry) = by_package.remove(&package) {
            merged.push(entry);
        }
    }

    let mut remainder = by_package.into_values().collect::<Vec<_>>();
    sort_root_entries(&mut remainder);
    merged.extend(remainder);
    guard.entries = merged;
}

fn render_root_grant_page(
    state: &Rc<RefCell<RootGrantPageState>>,
    list: &gtk::ListBox,
    count_label: &gtk::Label,
    summary_label: &gtk::Label,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    clear_list_box(list);

    let filtered = filtered_root_entries(&state.borrow());
    let total = state.borrow().entries.len();
    let allowed_total = state
        .borrow()
        .entries
        .iter()
        .filter(|entry| entry.allow_su)
        .count();
    count_label.set_text(&format!(
        "{} allowed · {} shown · {} total",
        allowed_total,
        filtered.len(),
        total
    ));
    summary_label.set_text(if state.borrow().show_system_apps {
        strings.grants_summary_showing_system
    } else {
        strings.grants_summary_hidden_system
    });

    if filtered.is_empty() {
        append_placeholder_row(list, "No apps match the current filters");
        return;
    }

    for entry in filtered {
        let row = gtk::ListBoxRow::new();
        row.set_activatable(true);
        row.set_selectable(false);
        row.set_widget_name(&entry.package_name);

        let shell = gtk::Box::new(gtk::Orientation::Horizontal, 12);
        shell.add_css_class("list-row-shell");
        set_margin_all(&shell, 8);

        let icon = root_grant_icon_widget(state, sender, port_entry, &entry.package_name);
        shell.append(&icon);

        let text = gtk::Box::new(gtk::Orientation::Vertical, 4);
        text.set_hexpand(true);
        let title = gtk::Label::new(Some(&entry.label));
        title.set_xalign(0.0);
        title.add_css_class("list-row-title");
        let subtitle = gtk::Label::new(Some(&entry.package_name));
        subtitle.set_xalign(0.0);
        subtitle.add_css_class("list-row-subtitle");
        text.append(&title);
        text.append(&subtitle);

        let toggle = gtk::Switch::new();
        toggle.set_active(entry.allow_su);
        toggle.set_valign(gtk::Align::Center);
        toggle.add_css_class("compact-switch");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let package = entry.package_name.clone();
            toggle.connect_active_notify(move |switch| {
                spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                    let package = package.clone();
                    let active = switch.is_active();
                    move |client| client.set_root_grant(&package, active)
                });
            });
        }

        shell.append(&text);
        shell.append(&toggle);
        row.set_child(Some(&shell));
        list.append(&row);
    }
}

fn render_root_grant_detail(
    state: &Rc<RefCell<RootGrantPageState>>,
    package: &str,
    title_label: &gtk::Label,
    icon: &gtk::Image,
    package_label: &gtk::Label,
    type_label: &gtk::Label,
    status_label: &gtk::Label,
    json_buffer: &gtk::TextBuffer,
    switch: &gtk::Switch,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    let package = package.trim();
    let guard = state.borrow();
    let Some(entry) = guard
        .entries
        .iter()
        .find(|entry| entry.package_name == package)
    else {
        title_label.set_text("Unknown app");
        package_label.set_text(package);
        type_label.set_text("-");
        status_label.set_text("-");
        json_buffer.set_text("{}");
        return;
    };

    title_label.set_text(&entry.label);
    package_label.set_text(&format!(
        "{}: {}",
        strings.grants_detail_package, entry.package_name
    ));
    type_label.set_text(&format!(
        "{}: {}",
        strings.grants_detail_type,
        if entry.is_system_app {
            strings.grants_detail_system
        } else {
            strings.grants_detail_user
        }
    ));
    status_label.set_text(&format!(
        "{}: {}",
        strings.grants_detail_status,
        if entry.allow_su {
            strings.grants_detail_allow
        } else {
            strings.grants_detail_revoke
        }
    ));
    if let Ok(text) = pretty_json_value(&entry.raw) {
        json_buffer.set_text(&text);
    }
    switch.set_widget_name("");
    switch.set_active(entry.allow_su);
    switch.set_widget_name(&entry.package_name);

    if let Some(bytes) = guard.icon_cache.get(&entry.package_name) {
        if let Ok(pixbuf) = Pixbuf::from_read(Cursor::new(bytes.clone())) {
            icon.set_from_pixbuf(Some(&pixbuf));
            icon.set_pixel_size(48);
        }
    } else {
        icon.set_icon_name(Some("application-x-executable-symbolic"));
        trigger_root_icon_fetch(state, sender, port_entry, &entry.package_name);
    }
}

fn handle_root_icon_loaded(
    state: &Rc<RefCell<RootGrantPageState>>,
    package_name: &str,
    bytes: Vec<u8>,
    list: &gtk::ListBox,
    count_label: &gtk::Label,
    summary_label: &gtk::Label,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    let mut guard = state.borrow_mut();
    guard.icon_cache.insert(package_name.to_string(), bytes);
    guard.icon_inflight.remove(package_name);
    drop(guard);
    render_root_grant_page(
        state,
        list,
        count_label,
        summary_label,
        sender,
        port_entry,
        strings,
    );
}

fn filtered_root_entries(state: &RootGrantPageState) -> Vec<RootGrantEntry> {
    let query = state.search_query.trim().to_lowercase();
    state
        .entries
        .iter()
        .filter(|entry| state.show_system_apps || !entry.is_system_app)
        .filter(|entry| {
            query.is_empty()
                || entry.label.to_lowercase().contains(&query)
                || entry.package_name.to_lowercase().contains(&query)
        })
        .cloned()
        .collect()
}

fn sort_root_entries(entries: &mut [RootGrantEntry]) {
    entries.sort_by(|left, right| {
        right
            .allow_su
            .cmp(&left.allow_su)
            .then_with(|| left.label.to_lowercase().cmp(&right.label.to_lowercase()))
            .then_with(|| left.package_name.cmp(&right.package_name))
    });
}

fn render_module_rows(
    list: &gtk::ListBox,
    value: &Value,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    clear_list_box(list);
    let diagnostic =
        json_str_any_recursive(value, &["managerDiagnostic", "manager_diagnostic", "error"]);
    let modules = find_array_of_objects(value, is_runtime_module_record);
    if modules.is_empty() {
        append_placeholder_row(
            list,
            &payload_keys_message(
                value,
                diagnostic.unwrap_or("No runtime modules reported by the device"),
            ),
        );
        return;
    }

    for module in modules.into_iter().take(20) {
        let module_id = json_str_any_recursive(&module, &["id"])
            .unwrap_or("")
            .to_string();
        if module_id.is_empty() {
            continue;
        }
        let name = json_str_any_recursive(&module, &["name"])
            .unwrap_or(&module_id)
            .to_string();
        let source = json_str_any_recursive(&module, &["source"]).unwrap_or("runtime");
        let enabled = module
            .get("enabled")
            .and_then(Value::as_bool)
            .unwrap_or(false);
        let action_supported = module
            .get("actionSupported")
            .or_else(|| module.get("action_supported"))
            .and_then(Value::as_bool)
            .unwrap_or(false);
        let uninstall_supported = module
            .get("type")
            .and_then(Value::as_str)
            .map(|kind| kind == "standard")
            .unwrap_or(false)
            || source.contains("ksud");

        let row = gtk::ListBoxRow::new();
        let shell = gtk::Box::new(gtk::Orientation::Horizontal, 12);
        shell.add_css_class("list-row-shell");
        set_margin_all(&shell, 8);

        let icon = module_icon_widget(&module);
        shell.append(&icon);

        let text = gtk::Box::new(gtk::Orientation::Vertical, 4);
        text.set_hexpand(true);
        let title = gtk::Label::new(Some(&name));
        title.set_xalign(0.0);
        title.add_css_class("list-row-title");
        let subtitle = gtk::Label::new(Some(&format!("{module_id} · {source}")));
        subtitle.set_xalign(0.0);
        subtitle.add_css_class("list-row-subtitle");
        text.append(&title);
        text.append(&subtitle);

        let actions = gtk::Box::new(gtk::Orientation::Horizontal, 8);
        actions.add_css_class("inline-actions");

        let toggle = gtk::Switch::new();
        toggle.set_active(enabled);
        toggle.set_valign(gtk::Align::Center);
        toggle.add_css_class("compact-switch");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module_id = module_id.clone();
            toggle.connect_active_notify(move |switch| {
                spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                    let module_id = module_id.clone();
                    let active = switch.is_active();
                    move |client| client.set_module_enabled(&module_id, active)
                });
            });
        }
        actions.append(&toggle);

        if uninstall_supported {
            let uninstall = gtk::Button::with_label(strings.uninstall_module);
            uninstall.add_css_class("pill");
            {
                let sender = sender.clone();
                let port_entry = port_entry.clone();
                let module_id = module_id.clone();
                uninstall.connect_clicked(move |_| {
                    spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                        let module_id = module_id.clone();
                        move |client| client.set_module_pending_uninstall(&module_id, true)
                    });
                });
            }
            actions.append(&uninstall);
        }

        if action_supported {
            let action = gtk::Button::with_label(strings.run_action);
            action.add_css_class("pill");
            action.add_css_class("tonal-button");
            {
                let sender = sender.clone();
                let port_entry = port_entry.clone();
                let module_id = module_id.clone();
                action.connect_clicked(move |_| {
                    spawn_agent_task_call(
                        parse_port(&port_entry.text()),
                        sender.clone(),
                        {
                            let module_id = module_id.clone();
                            move |client| client.run_module_action(&module_id)
                        },
                        false,
                        strings,
                    );
                });
            }
            actions.append(&action);
        }

        shell.append(&text);
        shell.append(&actions);
        row.set_child(Some(&shell));
        list.append(&row);
    }
}

fn clear_list_box(list: &gtk::ListBox) {
    while let Some(child) = list.first_child() {
        list.remove(&child);
    }
}

fn append_placeholder_row(list: &gtk::ListBox, message: &str) {
    let row = gtk::ListBoxRow::new();
    row.set_selectable(false);
    row.set_activatable(false);
    let label = gtk::Label::new(Some(message));
    label.set_xalign(0.0);
    label.set_wrap(true);
    label.add_css_class("list-row-subtitle");
    set_margin_all(&label, 14);
    row.set_child(Some(&label));
    list.append(&row);
}

fn root_grant_icon_widget(
    state: &Rc<RefCell<RootGrantPageState>>,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    package_name: &str,
) -> gtk::Image {
    let image = gtk::Image::from_icon_name("application-x-executable-symbolic");
    image.add_css_class("list-icon");
    image.set_pixel_size(28);
    if package_name.trim().is_empty() {
        return image;
    }
    if let Some(bytes) = state.borrow().icon_cache.get(package_name) {
        if let Ok(pixbuf) = Pixbuf::from_read(Cursor::new(bytes.clone())) {
            image.set_from_pixbuf(Some(&pixbuf));
            image.set_pixel_size(28);
        }
    } else {
        trigger_root_icon_fetch(state, sender, port_entry, package_name);
    }
    image
}

fn trigger_root_icon_fetch(
    state: &Rc<RefCell<RootGrantPageState>>,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    package_name: &str,
) {
    let package = package_name.trim().to_string();
    if package.is_empty() {
        return;
    }
    {
        let mut guard = state.borrow_mut();
        if guard.icon_cache.contains_key(&package) || !guard.icon_inflight.insert(package.clone()) {
            return;
        }
    }
    let sender = sender.clone();
    let port = parse_port(&port_entry.text());
    thread::spawn(move || {
        let client = AgentClient::new("127.0.0.1", port);
        if let Ok(bytes) = client.root_grant_icon_png(&package) {
            let _ = sender.send(UiMessage::RootIconLoaded(package, bytes));
        }
    });
}

fn module_icon_widget(module: &Value) -> gtk::Image {
    let source = json_str_any_recursive(module, &["source"]).unwrap_or("");
    let icon_name = if source.contains("abk") {
        "applications-system-symbolic"
    } else if source.contains("ksud") {
        "extension-symbolic"
    } else if source.contains("kpm") {
        "preferences-system-symbolic"
    } else {
        "application-x-executable-symbolic"
    };
    let image = gtk::Image::from_icon_name(icon_name);
    image.add_css_class("list-icon");
    image.set_pixel_size(24);
    image
}

fn json_str_any<'a>(value: &'a Value, keys: &[&str]) -> Option<&'a str> {
    keys.iter()
        .find_map(|key| value.get(*key).and_then(Value::as_str))
}

fn json_str_any_recursive<'a>(value: &'a Value, keys: &[&str]) -> Option<&'a str> {
    json_str_any(value, keys).or_else(|| match value {
        Value::Object(map) => map
            .values()
            .find_map(|nested| json_str_any_recursive(nested, keys)),
        Value::Array(items) => items
            .iter()
            .find_map(|nested| json_str_any_recursive(nested, keys)),
        _ => None,
    })
}

fn json_bool_opt(value: &Value, keys: &[&str]) -> Option<bool> {
    keys.iter()
        .find_map(|key| value.get(*key).and_then(Value::as_bool))
}

fn json_bool_opt_recursive(value: &Value, keys: &[&str]) -> Option<bool> {
    json_bool_opt(value, keys).or_else(|| match value {
        Value::Object(map) => map
            .values()
            .find_map(|nested| json_bool_opt_recursive(nested, keys)),
        Value::Array(items) => items
            .iter()
            .find_map(|nested| json_bool_opt_recursive(nested, keys)),
        _ => None,
    })
}

fn json_array_len(value: &Value, keys: &[&str]) -> Option<usize> {
    keys.iter()
        .find_map(|key| value.get(*key).and_then(Value::as_array).map(Vec::len))
}

fn json_array_len_recursive(value: &Value, keys: &[&str]) -> Option<usize> {
    json_array_len(value, keys).or_else(|| match value {
        Value::Object(map) => map
            .values()
            .find_map(|nested| json_array_len_recursive(nested, keys)),
        Value::Array(items) => items
            .iter()
            .find_map(|nested| json_array_len_recursive(nested, keys)),
        _ => None,
    })
}

fn find_array_of_objects(value: &Value, predicate: fn(&Value) -> bool) -> Vec<Value> {
    match value {
        Value::Array(items) => {
            if items.iter().any(predicate) {
                items.clone()
            } else {
                items
                    .iter()
                    .find_map(|nested| {
                        let found = find_array_of_objects(nested, predicate);
                        if found.is_empty() {
                            None
                        } else {
                            Some(found)
                        }
                    })
                    .unwrap_or_default()
            }
        }
        Value::Object(map) => map
            .values()
            .find_map(|nested| {
                let found = find_array_of_objects(nested, predicate);
                if found.is_empty() {
                    None
                } else {
                    Some(found)
                }
            })
            .unwrap_or_default(),
        _ => Vec::new(),
    }
}

fn is_root_grant_record(value: &Value) -> bool {
    value.get("packageName").is_some()
        || value.get("package_name").is_some()
        || value.get("profile").is_some()
}

fn is_runtime_module_record(value: &Value) -> bool {
    value.get("id").is_some()
        && (value.get("name").is_some()
            || value.get("source").is_some()
            || value.get("enabled").is_some())
}

fn diagnostic_or_keys(value: &Value, keys: &[&str], fallback: &str) -> String {
    json_str_any_recursive(value, keys)
        .map(ToString::to_string)
        .unwrap_or_else(|| payload_keys_message(value, fallback))
}

fn payload_keys_message(value: &Value, fallback: &str) -> String {
    let keys = top_level_keys(value);
    if keys.is_empty() {
        fallback.to_string()
    } else {
        format!("{fallback} · keys: {}", keys.join(", "))
    }
}

fn top_level_keys(value: &Value) -> Vec<String> {
    match value {
        Value::Object(map) => map.keys().cloned().collect(),
        Value::Array(items) if !items.is_empty() => vec![format!("array[{}]", items.len())],
        _ => Vec::new(),
    }
}

fn desktop_prefs_path() -> PathBuf {
    let base = std::env::var_os("XDG_CONFIG_HOME")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|home| PathBuf::from(home).join(".config")))
        .unwrap_or_else(|| PathBuf::from("."));
    base.join("abk-desktop").join("prefs.json")
}

fn load_desktop_prefs() -> DesktopPrefs {
    let path = desktop_prefs_path();
    let raw = match fs::read_to_string(path) {
        Ok(raw) => raw,
        Err(_) => return DesktopPrefs::default(),
    };
    serde_json::from_str(&raw).unwrap_or_default()
}

fn save_show_system_apps_pref(show_system_apps: bool) {
    let path = desktop_prefs_path();
    let Some(parent) = path.parent() else { return };
    let _ = fs::create_dir_all(parent);
    let prefs = DesktopPrefs {
        show_system_apps: Some(show_system_apps),
    };
    if let Ok(raw) = serde_json::to_string_pretty(&prefs) {
        let _ = fs::write(path, raw);
    }
}

fn bool_label<'a>(value: bool, truthy: &'a str, falsy: &'a str) -> &'a str {
    if value {
        truthy
    } else {
        falsy
    }
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

const APP_CSS: &str = r#"
.abk-root {
  background-image: linear-gradient(
    180deg,
    alpha(@accent_bg_color, 0.08) 0%,
    alpha(@window_bg_color, 1.0) 28%
  );
}

.nav-rail {
  background-color: alpha(@headerbar_bg_color, 0.82);
  border-right: 1px solid alpha(@window_fg_color, 0.08);
}

.brand-card {
  background-image: linear-gradient(
    135deg,
    alpha(@accent_bg_color, 0.16),
    alpha(@card_bg_color, 0.95)
  );
  border-radius: 28px;
  border: 1px solid alpha(@accent_bg_color, 0.18);
  padding: 18px;
}

.brand-title {
  font-size: 1.6rem;
  font-weight: 800;
}

.brand-body,
.nav-subtitle,
.section-body,
.card-body,
.hero-body {
  opacity: 0.78;
}

.nav-list {
  background: transparent;
}

.nav-list row {
  border-radius: 24px;
  margin: 4px 0;
}

.nav-list row:selected {
  background-color: alpha(@accent_bg_color, 0.96);
  color: @accent_fg_color;
}

.nav-list row:hover {
  background-color: alpha(@accent_bg_color, 0.08);
}

.nav-shell {
  min-height: 58px;
}

.nav-title {
  font-size: 1rem;
  font-weight: 700;
}

.nav-icon,
.summary-icon {
  -gtk-icon-size: 22px;
}

.page-body {
  padding-top: 8px;
  padding-bottom: 40px;
}

.hero-card {
  background-image: linear-gradient(
    135deg,
    alpha(@accent_bg_color, 0.22),
    alpha(@card_bg_color, 0.98)
  );
  border-radius: 34px;
  border: 1px solid alpha(@accent_bg_color, 0.18);
  padding: 30px;
}

.hero-kicker {
  letter-spacing: 0.12em;
  font-size: 0.78rem;
  font-weight: 700;
  opacity: 0.68;
}

.hero-title {
  font-size: 2.2rem;
  font-weight: 800;
}

.section-title,
.card-title {
  font-size: 1.06rem;
  font-weight: 760;
}

.surface-card {
  background-color: alpha(@card_bg_color, 0.98);
  border-radius: 28px;
  border: 1px solid alpha(@window_fg_color, 0.08);
  padding: 18px;
}

.plain-list {
  background: transparent;
}

.plain-list row {
  border-radius: 18px;
  margin: 2px 0;
}

.plain-list row:hover {
  background-color: alpha(@accent_bg_color, 0.06);
}

.list-row-shell {
  min-height: 52px;
}

.list-row-title {
  font-size: 1rem;
  font-weight: 680;
}

.list-row-subtitle {
  font-size: 0.9rem;
  opacity: 0.72;
}

.list-icon {
  min-width: 32px;
  min-height: 32px;
}

.inline-actions {
  margin-left: 8px;
}

.compact-switch {
  margin-left: 8px;
}

.material-entry {
  min-height: 44px;
  border-radius: 18px;
}

.big-button {
  padding: 10px 18px;
}

.tonal-button {
  background-color: alpha(@accent_bg_color, 0.14);
}

.console-pane {
  background-color: alpha(@view_bg_color, 0.9);
  border-radius: 22px;
}

.state-chip {
  background-color: alpha(@accent_bg_color, 0.16);
  color: @window_fg_color;
  border-radius: 999px;
  padding: 6px 12px;
}
"#;
