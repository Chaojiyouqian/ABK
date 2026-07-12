mod agent;
mod commands;

use crate::agent::{pretty_json_value, AgentClient};
use crate::commands::{
    build_adb_detect_command, build_adb_forward_command, build_adb_start_agent_command,
    build_adb_stop_agent_command, build_cli_command, run_command,
};
use adw::prelude::*;
use anyhow::Context;
use gdk_pixbuf::Pixbuf;
use gtk::{gdk, glib};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::cell::RefCell;
use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::Cursor;
use std::path::PathBuf;
use std::process::Command;
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
    SusfsActionOutput(String),
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ModuleListKind {
    Standard,
    Extension,
}

#[derive(Debug, Clone)]
struct RuntimeModuleEntry {
    id: String,
    name: String,
    author: String,
    type_name: String,
    version: String,
    description: String,
    repo_url: String,
    entry_kind: String,
    source: String,
    extension_id: String,
    companion_package: String,
    companion_display_name: String,
    service_activity: String,
    module_dir: String,
    web_root: String,
    readonly: bool,
    controllable: bool,
    enabled: bool,
    update: bool,
    remove: bool,
    has_web_ui: bool,
    has_action_script: bool,
    action_supported: bool,
    requires_companion_app: bool,
    settings_supported: bool,
    per_app_supported: bool,
    group_id: String,
    group_name: String,
    group_role: String,
    group_description: String,
    group_repo_url: String,
    list_kind: ModuleListKind,
    raw: Value,
}

#[derive(Debug, Clone, Default)]
struct ModuleGroupEntry {
    key: String,
    name: String,
    role: String,
    description: String,
    members: Vec<RuntimeModuleEntry>,
}

#[derive(Debug, Default)]
struct ModulePageState {
    modules: Vec<RuntimeModuleEntry>,
    extension_modules: Vec<RuntimeModuleEntry>,
    groups: Vec<ModuleGroupEntry>,
    selected_module: Option<String>,
    selected_group: Option<String>,
    raw_runtime: Option<Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsPathRuleModel {
    path: String,
    max_tries: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsOpenRedirectRuleModel {
    original_path: String,
    redirected_path: String,
    stage: String,
    uid_scheme: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsKstatEntryModel {
    path: String,
    ino: String,
    dev: String,
    nlink: String,
    size: String,
    atime: String,
    atime_nsec: String,
    mtime: String,
    mtime_nsec: String,
    ctime: String,
    ctime_nsec: String,
    blocks: String,
    blksize: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsPresetOptionsModel {
    hide_custom_rom_level: i64,
    hide_vendor_sepolicy: bool,
    hide_compat_matrix: bool,
    hide_gapps: bool,
    hide_revanced: bool,
    spoof_cmdline: bool,
    hide_loops: bool,
    force_hide_lsposed: bool,
    auto_try_umount: bool,
    skip_legit_mounts: bool,
    emulate_vold_app_data_mode: i64,
    umount_for_zygote_iso_service: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsConfigModel {
    schema_version: i64,
    auto_replay_enabled: bool,
    log_enabled: bool,
    avc_log_spoofing: bool,
    hide_sus_mounts_mode: String,
    spoof_uname_stage: String,
    uname_value: String,
    build_time_value: String,
    sdcard_root_path: String,
    android_data_root_path: String,
    path_rules: Vec<SusfsPathRuleModel>,
    loop_path_rules: Vec<SusfsPathRuleModel>,
    maps: Vec<String>,
    mounts: Vec<String>,
    try_umounts: Vec<String>,
    legit_mounts: Vec<String>,
    open_redirects: Vec<SusfsOpenRedirectRuleModel>,
    kstat_entries: Vec<SusfsKstatEntryModel>,
    presets: SusfsPresetOptionsModel,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsSupportMatrixModel {
    log: bool,
    hide_sus_mounts_for_all: bool,
    hide_sus_mounts_for_non_su: bool,
    sus_path: bool,
    sus_path_loop: bool,
    sus_map: bool,
    sus_mount: bool,
    try_umount: bool,
    ksud_kernel_umount_fallback: bool,
    open_redirect: bool,
    static_kstat: bool,
    dynamic_kstat: bool,
    set_uname: bool,
    set_cmdline_or_bootconfig: bool,
    set_proc_cmdline: bool,
    sdcard_root_path: bool,
    android_data_root_path: bool,
    avc_log_spoofing: bool,
    spoof_cmdline_preset: bool,
    hide_vendor_sepolicy_preset: bool,
    hide_compat_matrix_preset: bool,
    hide_gapps_preset: bool,
    hide_revanced_preset: bool,
    hide_loops_preset: bool,
    auto_try_umount_preset: bool,
    force_hide_lsposed_preset: bool,
    umount_for_zygote_iso_service: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
struct SusfsRuntimeStatusModel {
    available: bool,
    kernel_version: String,
    raw_feature_text: String,
    feature_flags: Vec<String>,
    support: SusfsSupportMatrixModel,
    bundled_binary_ref: String,
    bundled_binary_version: String,
    bundled_binary_published_at: String,
    bundled_binary_path: String,
    installed_binary_path: String,
    runtime_module_id: String,
    runtime_module_dir: String,
    config_path: String,
    diagnostics: Vec<String>,
}

#[derive(Debug, Clone)]
struct SusfsPageState {
    raw_snapshot: Value,
    raw_config: Value,
    config: SusfsConfigModel,
    status: SusfsRuntimeStatusModel,
    support: SusfsSupportMatrixModel,
    root_granted: bool,
    error: Option<String>,
    action_output: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct DesktopPrefs {
    show_system_apps: Option<bool>,
}

impl Default for SusfsPathRuleModel {
    fn default() -> Self {
        Self {
            path: String::new(),
            max_tries: None,
        }
    }
}

impl Default for SusfsOpenRedirectRuleModel {
    fn default() -> Self {
        Self {
            original_path: String::new(),
            redirected_path: String::new(),
            stage: "boot_completed".into(),
            uid_scheme: None,
        }
    }
}

impl Default for SusfsKstatEntryModel {
    fn default() -> Self {
        Self {
            path: String::new(),
            ino: "default".into(),
            dev: "default".into(),
            nlink: "default".into(),
            size: "default".into(),
            atime: "0".into(),
            atime_nsec: "0".into(),
            mtime: "0".into(),
            mtime_nsec: "0".into(),
            ctime: "0".into(),
            ctime_nsec: "0".into(),
            blocks: "0".into(),
            blksize: "0".into(),
        }
    }
}

impl Default for SusfsPresetOptionsModel {
    fn default() -> Self {
        Self {
            hide_custom_rom_level: 0,
            hide_vendor_sepolicy: false,
            hide_compat_matrix: false,
            hide_gapps: false,
            hide_revanced: false,
            spoof_cmdline: false,
            hide_loops: true,
            force_hide_lsposed: false,
            auto_try_umount: false,
            skip_legit_mounts: false,
            emulate_vold_app_data_mode: 0,
            umount_for_zygote_iso_service: false,
        }
    }
}

impl Default for SusfsConfigModel {
    fn default() -> Self {
        Self {
            schema_version: 1,
            auto_replay_enabled: true,
            log_enabled: true,
            avc_log_spoofing: false,
            hide_sus_mounts_mode: "off".into(),
            spoof_uname_stage: "off".into(),
            uname_value: "default".into(),
            build_time_value: "default".into(),
            sdcard_root_path: "/sdcard".into(),
            android_data_root_path: "/sdcard/Android/data".into(),
            path_rules: Vec::new(),
            loop_path_rules: Vec::new(),
            maps: Vec::new(),
            mounts: Vec::new(),
            try_umounts: Vec::new(),
            legit_mounts: default_susfs_legit_mounts(),
            open_redirects: Vec::new(),
            kstat_entries: Vec::new(),
            presets: SusfsPresetOptionsModel::default(),
        }
    }
}

impl Default for SusfsSupportMatrixModel {
    fn default() -> Self {
        Self {
            log: true,
            hide_sus_mounts_for_all: false,
            hide_sus_mounts_for_non_su: false,
            sus_path: false,
            sus_path_loop: false,
            sus_map: false,
            sus_mount: false,
            try_umount: false,
            ksud_kernel_umount_fallback: false,
            open_redirect: false,
            static_kstat: false,
            dynamic_kstat: false,
            set_uname: false,
            set_cmdline_or_bootconfig: false,
            set_proc_cmdline: false,
            sdcard_root_path: false,
            android_data_root_path: false,
            avc_log_spoofing: false,
            spoof_cmdline_preset: false,
            hide_vendor_sepolicy_preset: false,
            hide_compat_matrix_preset: false,
            hide_gapps_preset: false,
            hide_revanced_preset: false,
            hide_loops_preset: true,
            auto_try_umount_preset: false,
            force_hide_lsposed_preset: false,
            umount_for_zygote_iso_service: false,
        }
    }
}

impl Default for SusfsRuntimeStatusModel {
    fn default() -> Self {
        Self {
            available: false,
            kernel_version: String::new(),
            raw_feature_text: String::new(),
            feature_flags: Vec::new(),
            support: SusfsSupportMatrixModel::default(),
            bundled_binary_ref: String::new(),
            bundled_binary_version: String::new(),
            bundled_binary_published_at: String::new(),
            bundled_binary_path: String::new(),
            installed_binary_path: String::new(),
            runtime_module_id: "abk-susfs-control".into(),
            runtime_module_dir: String::new(),
            config_path: String::new(),
            diagnostics: Vec::new(),
        }
    }
}

impl Default for SusfsPageState {
    fn default() -> Self {
        Self {
            raw_snapshot: Value::Null,
            raw_config: Value::Object(Default::default()),
            config: SusfsConfigModel::default(),
            status: SusfsRuntimeStatusModel::default(),
            support: SusfsSupportMatrixModel::default(),
            root_granted: true,
            error: None,
            action_output: String::new(),
        }
    }
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
    let session_summary = device_page.session_summary;
    let runtime_summary = device_page.runtime_summary;
    let root_summary = device_page.root_summary;
    let susfs_summary = device_page.susfs_summary;
    let root_hub_status = device_page.root_hub_status;
    let root_page_count = device_page.root_page_count;
    let root_system_switch = device_page.root_system_switch.clone();
    let root_list = device_page.root_list;
    let root_detail_title = device_page.root_detail_title;
    let root_detail_icon = device_page.root_detail_icon;
    let root_detail_package = device_page.root_detail_package;
    let root_detail_type = device_page.root_detail_type;
    let root_detail_status = device_page.root_detail_status;
    let root_detail_json = device_page.root_detail_json;
    let root_detail_switch = device_page.root_detail_switch.clone();
    let modules_hub_status = device_page.modules_hub_status;
    let module_groups_summary = device_page.module_groups_summary;
    let module_groups_list = device_page.module_groups_list;
    let module_standard_summary = device_page.module_standard_summary;
    let module_standard_list = device_page.module_standard_list;
    let module_extension_summary = device_page.module_extension_summary;
    let module_extension_list = device_page.module_extension_list;
    let module_group_detail_title = device_page.module_group_detail_title;
    let module_group_detail_summary = device_page.module_group_detail_summary;
    let module_group_detail_actions = device_page.module_group_detail_actions;
    let module_group_member_list = device_page.module_group_member_list;
    let module_detail_title = device_page.module_detail_title;
    let module_detail_summary = device_page.module_detail_summary;
    let module_detail_actions = device_page.module_detail_actions;
    let module_detail_json = device_page.module_detail_json;
    let susfs_hub_status = device_page.susfs_hub_status;
    let susfs_status_summary = device_page.susfs_status_summary;
    let susfs_support_summary = device_page.susfs_support_summary;
    let susfs_editor_host = device_page.susfs_editor_host;
    let susfs_action_output = device_page.susfs_action_output;
    let interaction_sender = sender.clone();
    let root_state = device_page.root_state.clone();
    let module_state = device_page.module_state.clone();
    let susfs_state = device_page.susfs_state.clone();

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
                    update_module_page_state(&module_state, &value);
                    render_module_page(
                        &module_state,
                        &module_groups_summary,
                        &module_groups_list,
                        &module_standard_summary,
                        &module_standard_list,
                        &module_extension_summary,
                        &module_extension_list,
                        &module_group_detail_title,
                        &module_group_detail_summary,
                        &module_group_detail_actions,
                        &module_group_member_list,
                        &module_detail_title,
                        &module_detail_summary,
                        &module_detail_actions,
                        &module_detail_json,
                        &device_nav_stack,
                        &interaction_sender,
                        &device_port_entry,
                        strings,
                    );
                    modules_hub_status
                        .set_text(&module_hub_summary(&module_state.borrow(), strings));
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
                        &root_hub_status,
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
                        &root_hub_status,
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
                    update_susfs_page_state(&susfs_state, &value);
                    render_susfs_page(
                        &susfs_state,
                        &susfs_status_summary,
                        &susfs_support_summary,
                        &susfs_editor_host,
                        strings,
                    );
                    susfs_hub_status.set_text(&susfs_hub_summary(&susfs_state.borrow(), strings));
                }
                UiMessage::SusfsActionOutput(text) => {
                    susfs_action_output.set_text(&text);
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
    session_summary: gtk::Label,
    runtime_summary: gtk::Label,
    root_summary: gtk::Label,
    susfs_summary: gtk::Label,
    root_hub_status: gtk::Label,
    root_page_count: gtk::Label,
    root_system_switch: gtk::Switch,
    root_list: gtk::ListBox,
    root_detail_title: gtk::Label,
    root_detail_icon: gtk::Image,
    root_detail_package: gtk::Label,
    root_detail_type: gtk::Label,
    root_detail_status: gtk::Label,
    root_detail_json: gtk::TextBuffer,
    root_detail_switch: gtk::Switch,
    modules_hub_status: gtk::Label,
    module_groups_summary: gtk::Label,
    module_groups_list: gtk::ListBox,
    module_standard_summary: gtk::Label,
    module_standard_list: gtk::ListBox,
    module_extension_summary: gtk::Label,
    module_extension_list: gtk::ListBox,
    module_group_detail_title: gtk::Label,
    module_group_detail_summary: gtk::Label,
    module_group_detail_actions: gtk::Box,
    module_group_member_list: gtk::ListBox,
    module_detail_title: gtk::Label,
    module_detail_summary: gtk::Label,
    module_detail_actions: gtk::Box,
    module_detail_json: gtk::TextBuffer,
    susfs_hub_status: gtk::Label,
    susfs_status_summary: gtk::Label,
    susfs_support_summary: gtk::Label,
    susfs_editor_host: gtk::Box,
    susfs_action_output: gtk::TextBuffer,
    root_state: Rc<RefCell<RootGrantPageState>>,
    module_state: Rc<RefCell<ModulePageState>>,
    susfs_state: Rc<RefCell<SusfsPageState>>,
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
    let module_state = Rc::new(RefCell::new(ModulePageState::default()));
    let susfs_state = Rc::new(RefCell::new(SusfsPageState::default()));

    let grants_card = surface_card(strings.grants_title, strings.grants_body);
    let root_hub_status = gtk::Label::new(Some(strings.grants_summary_hidden_system));
    root_hub_status.set_xalign(0.0);
    root_hub_status.set_wrap(true);
    root_hub_status.add_css_class("list-row-subtitle");
    grants_card.append(&root_hub_status);

    let root_manage_button = gtk::Button::with_label(strings.grants_manage);
    root_manage_button.add_css_class("suggested-action");
    root_manage_button.add_css_class("pill");
    grants_card.append(&root_manage_button);

    let modules_card = surface_card(strings.modules_title, strings.modules_body);
    let modules_hub_status = gtk::Label::new(Some(localized_text(
        strings,
        "分组、普通模块和扩展模块会拆到独立页面。",
        "Grouped, standard, and extension modules now live on a dedicated page.",
    )));
    modules_hub_status.set_xalign(0.0);
    modules_hub_status.set_wrap(true);
    modules_hub_status.add_css_class("list-row-subtitle");
    modules_card.append(&modules_hub_status);
    let modules_manage_button =
        gtk::Button::with_label(localized_text(strings, "打开模块页", "Open Modules Page"));
    modules_manage_button.add_css_class("suggested-action");
    modules_manage_button.add_css_class("pill");
    modules_card.append(&modules_manage_button);

    let susfs_card = surface_card(
        strings.device_susfs,
        localized_text(
            strings,
            "SUSFS 现在用结构化编辑器单独管理。",
            "SUSFS now has its own structured editor page.",
        ),
    );
    let susfs_hub_status = gtk::Label::new(Some(localized_text(
        strings,
        "支持矩阵、结构化配置和诊断输出会集中到 SUSFS 页面。",
        "Support status, structured config, and diagnostics are consolidated on the SUSFS page.",
    )));
    susfs_hub_status.set_xalign(0.0);
    susfs_hub_status.set_wrap(true);
    susfs_hub_status.add_css_class("list-row-subtitle");
    susfs_card.append(&susfs_hub_status);
    let susfs_manage_button = gtk::Button::with_label(localized_text(
        strings,
        "打开 SUSFS 页面",
        "Open SUSFS Page",
    ));
    susfs_manage_button.add_css_class("suggested-action");
    susfs_manage_button.add_css_class("pill");
    susfs_card.append(&susfs_manage_button);

    ops_grid.attach(&grants_card, 0, 0, 1, 1);
    ops_grid.attach(&modules_card, 1, 0, 1, 1);
    ops_grid.attach(&susfs_card, 0, 1, 2, 1);
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

    let (modules_page_container, modules_page_body) = new_page_shell();
    let modules_header = hero_card(
        strings.modules_title,
        localized_text(strings, "模块工作区", "Module Workspace"),
        localized_text(
            strings,
            "分组、普通模块、扩展模块以及安装导入动作全部集中在这里。",
            "Groups, standard modules, extensions, and install/import actions are all centralized here.",
        ),
    );
    let modules_back =
        gtk::Button::with_label(localized_text(strings, "返回设备页", "Back to Device"));
    modules_back.add_css_class("pill");
    {
        let nav_stack = nav_stack.clone();
        modules_back.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("main");
        });
    }
    modules_header.append(&modules_back);
    modules_page_body.append(&modules_header);

    let module_groups_card = surface_card(
        localized_text(strings, "模块分组", "Module Groups"),
        localized_text(
            strings,
            "有 group_id / group_name 的模块会先聚合在这里。",
            "Modules with group metadata are aggregated here first.",
        ),
    );
    let module_groups_summary = gtk::Label::new(Some("0"));
    module_groups_summary.set_xalign(0.0);
    module_groups_summary.add_css_class("list-row-subtitle");
    module_groups_card.append(&module_groups_summary);
    let module_groups_list = gtk::ListBox::new();
    module_groups_list.add_css_class("plain-list");
    let module_groups_scroll = new_scroller(&module_groups_list);
    module_groups_scroll.set_min_content_height(220);
    module_groups_card.append(&module_groups_scroll);
    modules_page_body.append(&module_groups_card);

    let module_standard_card = surface_card(
        localized_text(strings, "模块", "Modules"),
        localized_text(
            strings,
            "这里显示普通运行时模块，二级操作下沉到详情页。",
            "Standalone runtime modules stay compact here and move secondary actions into details.",
        ),
    );
    let module_standard_summary = gtk::Label::new(Some("0"));
    module_standard_summary.set_xalign(0.0);
    module_standard_summary.add_css_class("list-row-subtitle");
    module_standard_card.append(&module_standard_summary);
    let module_standard_list = gtk::ListBox::new();
    module_standard_list.add_css_class("plain-list");
    let module_standard_scroll = new_scroller(&module_standard_list);
    module_standard_scroll.set_min_content_height(220);
    module_standard_card.append(&module_standard_scroll);
    modules_page_body.append(&module_standard_card);

    let module_extension_card = surface_card(
        localized_text(strings, "扩展模块", "Extensions"),
        localized_text(
            strings,
            "扩展模块会把 companion app、设置入口和服务能力显式区分出来。",
            "Extensions expose companion app, settings, and service affordances distinctly.",
        ),
    );
    let module_extension_summary = gtk::Label::new(Some("0"));
    module_extension_summary.set_xalign(0.0);
    module_extension_summary.add_css_class("list-row-subtitle");
    module_extension_card.append(&module_extension_summary);
    let module_extension_list = gtk::ListBox::new();
    module_extension_list.add_css_class("plain-list");
    let module_extension_scroll = new_scroller(&module_extension_list);
    module_extension_scroll.set_min_content_height(220);
    module_extension_card.append(&module_extension_scroll);
    modules_page_body.append(&module_extension_card);

    let install_card = surface_card(
        localized_text(strings, "安装 / 导入", "Install / Import"),
        localized_text(
            strings,
            "模块 ZIP、APK 和设备路径动作都集中在这个入口。",
            "Module ZIPs, APKs, and device-path actions are centralized here.",
        ),
    );
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
    modules_page_body.append(&install_card);

    let (module_group_detail_container, module_group_detail_body) = new_page_shell();
    let module_group_detail_header = hero_card(
        localized_text(strings, "模块分组", "Module Group"),
        localized_text(strings, "分组详情", "Group Details"),
        localized_text(
            strings,
            "分组级操作会按稳定顺序路由到各成员模块。",
            "Group-level actions route through member modules in a deterministic order.",
        ),
    );
    let module_group_detail_back =
        gtk::Button::with_label(localized_text(strings, "返回模块页", "Back to Modules"));
    module_group_detail_back.add_css_class("pill");
    {
        let nav_stack = nav_stack.clone();
        module_group_detail_back.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("modules");
        });
    }
    module_group_detail_header.append(&module_group_detail_back);
    module_group_detail_body.append(&module_group_detail_header);

    let module_group_detail_card = surface_card(
        localized_text(strings, "分组摘要", "Group Summary"),
        localized_text(
            strings,
            "启用、禁用、卸载、Action 和 WebUI 都从这里触发。",
            "Enable, disable, uninstall, action, and WebUI entry all live here.",
        ),
    );
    let module_group_detail_title = gtk::Label::new(Some("-"));
    module_group_detail_title.set_xalign(0.0);
    module_group_detail_title.add_css_class("card-title");
    let module_group_detail_summary = gtk::Label::new(Some("-"));
    module_group_detail_summary.set_xalign(0.0);
    module_group_detail_summary.set_wrap(true);
    module_group_detail_summary.add_css_class("list-row-subtitle");
    let module_group_detail_actions = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    module_group_detail_actions.set_halign(gtk::Align::Start);
    module_group_detail_card.append(&module_group_detail_title);
    module_group_detail_card.append(&module_group_detail_summary);
    module_group_detail_card.append(&module_group_detail_actions);
    module_group_detail_body.append(&module_group_detail_card);

    let module_group_members_card = surface_card(
        localized_text(strings, "成员模块", "Member Modules"),
        localized_text(
            strings,
            "成员列表保持紧凑，深入操作继续进入单模块详情页。",
            "Members stay compact here and defer deeper controls to the module detail page.",
        ),
    );
    let module_group_member_list = gtk::ListBox::new();
    module_group_member_list.add_css_class("plain-list");
    let module_group_member_scroll = new_scroller(&module_group_member_list);
    module_group_member_scroll.set_min_content_height(360);
    module_group_members_card.append(&module_group_member_scroll);
    module_group_detail_body.append(&module_group_members_card);

    let (module_detail_container, module_detail_body) = new_page_shell();
    let module_detail_header = hero_card(
        strings.modules_title,
        localized_text(strings, "模块详情", "Module Details"),
        localized_text(
            strings,
            "常见动作放在详情页里，列表本身只保留紧凑摘要。",
            "The list stays compact while the detail page owns the heavier actions.",
        ),
    );
    let module_detail_back =
        gtk::Button::with_label(localized_text(strings, "返回模块页", "Back to Modules"));
    module_detail_back.add_css_class("pill");
    {
        let nav_stack = nav_stack.clone();
        module_detail_back.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("modules");
        });
    }
    module_detail_header.append(&module_detail_back);
    module_detail_body.append(&module_detail_header);

    let module_detail_card = surface_card(
        localized_text(strings, "模块状态", "Module State"),
        localized_text(
            strings,
            "这里展示元数据、运行状态和所有可执行操作。",
            "Metadata, runtime state, and all supported actions are exposed here.",
        ),
    );
    let module_detail_title = gtk::Label::new(Some("-"));
    module_detail_title.set_xalign(0.0);
    module_detail_title.add_css_class("card-title");
    let module_detail_summary = gtk::Label::new(Some("-"));
    module_detail_summary.set_xalign(0.0);
    module_detail_summary.set_wrap(true);
    module_detail_summary.add_css_class("list-row-subtitle");
    let module_detail_actions = gtk::Box::new(gtk::Orientation::Horizontal, 8);
    module_detail_actions.set_halign(gtk::Align::Start);
    module_detail_card.append(&module_detail_title);
    module_detail_card.append(&module_detail_summary);
    module_detail_card.append(&module_detail_actions);
    module_detail_body.append(&module_detail_card);

    let module_detail_json_card = surface_card(
        localized_text(strings, "原始模块 JSON", "Raw Module JSON"),
        localized_text(
            strings,
            "保留原始数据作为兜底和排障信息。",
            "Raw JSON remains available as a fallback and debugging view.",
        ),
    );
    let module_detail_json = new_text_buffer();
    let module_detail_json_view = new_text_view(&module_detail_json, false);
    module_detail_json_view.add_css_class("console-pane");
    let module_detail_json_scroll = new_scroller(&module_detail_json_view);
    module_detail_json_scroll.set_min_content_height(340);
    module_detail_json_card.append(&module_detail_json_scroll);
    module_detail_body.append(&module_detail_json_card);

    let (susfs_page_container, susfs_page_body) = new_page_shell();
    let susfs_page_header = hero_card(
        strings.device_susfs,
        localized_text(strings, "SUSFS 工作区", "SUSFS Workspace"),
        localized_text(
            strings,
            "支持矩阵、结构化配置、应用输出和原始 JSON 都在一个独立页面里。",
            "Support state, structured config, apply output, and raw JSON all live on a dedicated page.",
        ),
    );
    let susfs_page_back =
        gtk::Button::with_label(localized_text(strings, "返回设备页", "Back to Device"));
    susfs_page_back.add_css_class("pill");
    {
        let nav_stack = nav_stack.clone();
        susfs_page_back.connect_clicked(move |_| {
            nav_stack.set_visible_child_name("main");
        });
    }
    susfs_page_header.append(&susfs_page_back);
    susfs_page_body.append(&susfs_page_header);

    let susfs_runtime_card = surface_card(
        localized_text(strings, "运行状态 / 支持矩阵", "Runtime Status / Support Matrix"),
        localized_text(
            strings,
            "不支持的控件会被显式禁用，避免把整个页面变成盲写 JSON。",
            "Unsupported controls are disabled explicitly so the page stops feeling like blind JSON editing.",
        ),
    );
    let susfs_status_summary = gtk::Label::new(Some("-"));
    susfs_status_summary.set_xalign(0.0);
    susfs_status_summary.set_wrap(true);
    susfs_status_summary.add_css_class("card-title");
    let susfs_support_summary = gtk::Label::new(Some("-"));
    susfs_support_summary.set_xalign(0.0);
    susfs_support_summary.set_wrap(true);
    susfs_support_summary.add_css_class("list-row-subtitle");
    let susfs_controls = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    let load_susfs = gtk::Button::with_label(localized_text(strings, "重新加载", "Reload"));
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
        let susfs_state = susfs_state.clone();
        apply_susfs.connect_clicked(move |_| {
            if let Ok(body) = build_susfs_apply_body(&susfs_state) {
                spawn_agent_susfs_task_call(
                    parse_port(&port_entry.text()),
                    sender.clone(),
                    move |client| client.apply_susfs_json(&body),
                    strings,
                );
            } else {
                let _ = sender.send(UiMessage::ActivityLog(
                    localized_text(
                        strings,
                        "SUSFS 结构化配置无法序列化。",
                        "The structured SUSFS configuration could not be serialized.",
                    )
                    .to_string(),
                ));
            }
        });
    }
    let export = gtk::Button::with_label(strings.export_diagnostics);
    export.add_css_class("pill");
    export.add_css_class("tonal-button");
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        export.connect_clicked(move |_| {
            spawn_agent_susfs_task_call(
                parse_port(&port_entry.text()),
                sender.clone(),
                move |client| client.export_diagnostics(),
                strings,
            );
        });
    }
    susfs_controls.append(&load_susfs);
    susfs_controls.append(&apply_susfs);
    susfs_controls.append(&export);
    susfs_runtime_card.append(&susfs_status_summary);
    susfs_runtime_card.append(&susfs_support_summary);
    susfs_runtime_card.append(&susfs_controls);
    susfs_page_body.append(&susfs_runtime_card);

    let susfs_editor_card = surface_card(
        localized_text(strings, "结构化配置编辑器", "Structured Config Editor"),
        localized_text(
            strings,
            "标量字段、预设开关和各类列表都可直接编辑。",
            "Scalar fields, presets, and list-based rules are all editable directly.",
        ),
    );
    let susfs_editor_host = gtk::Box::new(gtk::Orientation::Vertical, 16);
    susfs_editor_card.append(&susfs_editor_host);
    susfs_page_body.append(&susfs_editor_card);

    let susfs_action_card = surface_card(
        localized_text(strings, "应用 / 诊断输出", "Apply / Diagnostics Output"),
        localized_text(
            strings,
            "最近一次应用或导出诊断的输出会保留在这里。",
            "The latest apply or diagnostics-export output is preserved here.",
        ),
    );
    let susfs_action_output = new_text_buffer();
    let susfs_action_view = new_text_view(&susfs_action_output, false);
    susfs_action_view.add_css_class("console-pane");
    let susfs_action_scroll = new_scroller(&susfs_action_view);
    susfs_action_scroll.set_min_content_height(220);
    susfs_action_card.append(&susfs_action_scroll);
    susfs_page_body.append(&susfs_action_card);

    let susfs_raw_card = surface_card(
        localized_text(strings, "原始 SUSFS JSON", "Raw SUSFS JSON"),
        localized_text(
            strings,
            "高级排障仍可直接回看 agent 返回的原始数据。",
            "Raw agent data remains visible for advanced troubleshooting.",
        ),
    );
    let susfs_raw_view = new_text_view(&susfs_output, false);
    susfs_raw_view.add_css_class("console-pane");
    let susfs_raw_scroll = new_scroller(&susfs_raw_view);
    susfs_raw_scroll.set_min_content_height(300);
    susfs_raw_card.append(&susfs_raw_scroll);
    susfs_page_body.append(&susfs_raw_card);

    {
        let nav_stack = nav_stack.clone();
        let root_state = root_state.clone();
        let root_list = root_list.clone();
        let root_page_count = root_page_count.clone();
        let root_hub_status = root_hub_status.clone();
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        root_manage_button.connect_clicked(move |_| {
            sort_root_entries(&mut root_state.borrow_mut().entries);
            render_root_grant_page(
                &root_state,
                &root_list,
                &root_page_count,
                &root_hub_status,
                &sender,
                &port_entry,
                strings,
            );
            nav_stack.set_visible_child_name("root-grants");
        });
    }

    {
        let root_state = root_state.clone();
        let root_list = root_list.clone();
        let root_page_count = root_page_count.clone();
        let root_hub_status = root_hub_status.clone();
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        root_search_entry.connect_search_changed(move |entry| {
            root_state.borrow_mut().search_query = entry.text().to_string();
            render_root_grant_page(
                &root_state,
                &root_list,
                &root_page_count,
                &root_hub_status,
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
        let root_hub_status = root_hub_status.clone();
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        root_system_switch.connect_active_notify(move |switch| {
            let active = switch.is_active();
            let mut state = root_state.borrow_mut();
            state.show_system_apps = active;
            save_show_system_apps_pref(active);
            sort_root_entries(&mut state.entries);
            drop(state);
            render_root_grant_page(
                &root_state,
                &root_list,
                &root_page_count,
                &root_hub_status,
                &sender,
                &port_entry,
                strings,
            );
        });
    }

    {
        let root_state = root_state.clone();
        let nav_stack = nav_stack.clone();
        let detail_title = root_detail_title.clone();
        let detail_icon = root_detail_icon.clone();
        let detail_package = root_detail_package.clone();
        let detail_type = root_detail_type.clone();
        let detail_status = root_detail_status.clone();
        let detail_json = root_detail_json.clone();
        let detail_switch = root_detail_switch.clone();
        let sender = sender.clone();
        let port_entry = port_entry.clone();
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
        let sender = sender.clone();
        let port_entry = port_entry.clone();
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

    {
        let nav_stack = nav_stack.clone();
        let module_state = module_state.clone();
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        let module_groups_summary = module_groups_summary.clone();
        let module_groups_list = module_groups_list.clone();
        let module_standard_summary = module_standard_summary.clone();
        let module_standard_list = module_standard_list.clone();
        let module_extension_summary = module_extension_summary.clone();
        let module_extension_list = module_extension_list.clone();
        let group_detail_title = module_group_detail_title.clone();
        let group_detail_summary = module_group_detail_summary.clone();
        let group_detail_actions = module_group_detail_actions.clone();
        let group_member_list = module_group_member_list.clone();
        let detail_title = module_detail_title.clone();
        let detail_summary = module_detail_summary.clone();
        let detail_actions = module_detail_actions.clone();
        let detail_json = module_detail_json.clone();
        modules_manage_button.connect_clicked(move |_| {
            render_module_page(
                &module_state,
                &module_groups_summary,
                &module_groups_list,
                &module_standard_summary,
                &module_standard_list,
                &module_extension_summary,
                &module_extension_list,
                &group_detail_title,
                &group_detail_summary,
                &group_detail_actions,
                &group_member_list,
                &detail_title,
                &detail_summary,
                &detail_actions,
                &detail_json,
                &nav_stack,
                &sender,
                &port_entry,
                strings,
            );
            nav_stack.set_visible_child_name("modules");
        });
    }

    {
        let nav_stack = nav_stack.clone();
        let susfs_state = susfs_state.clone();
        let status_summary = susfs_status_summary.clone();
        let support_summary = susfs_support_summary.clone();
        let editor_host = susfs_editor_host.clone();
        susfs_manage_button.connect_clicked(move |_| {
            render_susfs_page(
                &susfs_state,
                &status_summary,
                &support_summary,
                &editor_host,
                strings,
            );
            nav_stack.set_visible_child_name("susfs");
        });
    }

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
    nav_stack.add_titled(
        &modules_page_container,
        Some("modules"),
        localized_text(strings, "模块页", "Modules"),
    );
    nav_stack.add_titled(
        &module_group_detail_container,
        Some("module-group-detail"),
        localized_text(strings, "模块分组详情", "Module Group Detail"),
    );
    nav_stack.add_titled(
        &module_detail_container,
        Some("module-detail"),
        localized_text(strings, "模块详情", "Module Detail"),
    );
    nav_stack.add_titled(&susfs_page_container, Some("susfs"), strings.device_susfs);
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
        session_summary: session_summary.1,
        runtime_summary: runtime_summary.1,
        root_summary: root_summary.1,
        susfs_summary: susfs_summary.1,
        root_hub_status,
        root_page_count,
        root_system_switch,
        root_list,
        root_detail_title,
        root_detail_icon,
        root_detail_package,
        root_detail_type,
        root_detail_status,
        root_detail_json,
        root_detail_switch,
        modules_hub_status,
        module_groups_summary,
        module_groups_list,
        module_standard_summary,
        module_standard_list,
        module_extension_summary,
        module_extension_list,
        module_group_detail_title,
        module_group_detail_summary,
        module_group_detail_actions,
        module_group_member_list,
        module_detail_title,
        module_detail_summary,
        module_detail_actions,
        module_detail_json,
        susfs_hub_status,
        susfs_status_summary,
        susfs_support_summary,
        susfs_editor_host,
        susfs_action_output,
        root_state,
        module_state,
        susfs_state,
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

fn spawn_agent_sequence_call<F>(port: u16, sender: Sender<UiMessage>, call: F, strings: Strings)
where
    F: FnOnce(AgentClient, Strings) -> anyhow::Result<String> + Send + 'static,
{
    thread::spawn(move || {
        let client = AgentClient::new("127.0.0.1", port);
        let message = match call(client.clone(), strings) {
            Ok(text) => {
                refresh_agent(client, &sender);
                text
            }
            Err(error) => format!("{error:#}"),
        };
        let _ = sender.send(UiMessage::ActivityLog(message));
    });
}

fn spawn_agent_susfs_task_call<F>(port: u16, sender: Sender<UiMessage>, call: F, strings: Strings)
where
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
                        if final_task.kind == "diagnostics.export" {
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
                        let text = lines.join("\n\n");
                        let _ = sender.send(UiMessage::SusfsActionOutput(text.clone()));
                        let _ = sender.send(UiMessage::ActivityLog(text));
                        refresh_agent(client, &sender);
                    }
                    Err(error) => {
                        let text = format!("{error:#}");
                        let _ = sender.send(UiMessage::SusfsActionOutput(text.clone()));
                        let _ = sender.send(UiMessage::ActivityLog(text));
                    }
                }
            }
            Err(error) => {
                let text = format!("{error:#}");
                let _ = sender.send(UiMessage::SusfsActionOutput(text.clone()));
                let _ = sender.send(UiMessage::ActivityLog(text));
            }
        }
    });
}

fn spawn_module_webui_helper<F>(
    port: u16,
    module_id: impl Into<String>,
    module_name: impl Into<String>,
    on_error: F,
) where
    F: Fn(String) + Send + 'static,
{
    let module_id = module_id.into();
    let module_name = module_name.into();
    thread::spawn(move || {
        let result = (|| -> anyhow::Result<()> {
            let current =
                std::env::current_exe().context("failed to resolve current executable")?;
            let helper_name = if cfg!(windows) {
                "abk-webui.exe"
            } else {
                "abk-webui"
            };
            let helper_path = current.with_file_name(helper_name);
            if !helper_path.exists() {
                return Err(anyhow::anyhow!(
                    "module webui helper missing: {}",
                    helper_path.display()
                ));
            }
            Command::new(&helper_path)
                .arg("--port")
                .arg(port.to_string())
                .arg("--module-id")
                .arg(&module_id)
                .arg("--module-name")
                .arg(&module_name)
                .spawn()
                .with_context(|| format!("failed to launch {}", helper_path.display()))?;
            Ok(())
        })();

        if let Err(error) = result {
            on_error(format!("{error:#}"));
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

fn localized_text(strings: Strings, zh: &'static str, en: &'static str) -> &'static str {
    if strings.app_title == ZH.app_title {
        zh
    } else {
        en
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
    summary_label.set_text(&format!(
        "{} allowed · {} total · {}",
        allowed_total,
        total,
        if state.borrow().show_system_apps {
            strings.grants_summary_showing_system
        } else {
            strings.grants_summary_hidden_system
        }
    ));

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
    let (entry, cached_icon) = {
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
        (
            entry.clone(),
            guard.icon_cache.get(&entry.package_name).cloned(),
        )
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

    if let Some(bytes) = cached_icon {
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
    let mut filtered = state
        .entries
        .iter()
        .filter(|entry| state.show_system_apps || !entry.is_system_app)
        .filter(|entry| {
            query.is_empty()
                || entry.label.to_lowercase().contains(&query)
                || entry.package_name.to_lowercase().contains(&query)
        })
        .cloned()
        .collect::<Vec<_>>();
    sort_root_entries(&mut filtered);
    filtered
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

fn update_module_page_state(state: &Rc<RefCell<ModulePageState>>, value: &Value) {
    let modules = runtime_module_records(value, &["modules"])
        .into_iter()
        .filter_map(|raw| parse_runtime_module_entry(raw, ModuleListKind::Standard))
        .collect::<Vec<_>>();
    let extension_modules = runtime_module_records(value, &["extension_modules"])
        .into_iter()
        .filter_map(|raw| parse_runtime_module_entry(raw, ModuleListKind::Extension))
        .collect::<Vec<_>>();
    let groups = build_module_groups(&modules, &extension_modules);

    let mut guard = state.borrow_mut();
    guard.modules = modules;
    guard.extension_modules = extension_modules;
    guard.groups = groups;
    guard.raw_runtime = Some(value.clone());

    if guard
        .selected_module
        .as_deref()
        .map(|id| guard.find_module(id).is_none())
        .unwrap_or(false)
    {
        guard.selected_module = None;
    }
    if guard
        .selected_group
        .as_deref()
        .map(|key| guard.find_group(key).is_none())
        .unwrap_or(false)
    {
        guard.selected_group = None;
    }
}

fn render_module_page(
    state: &Rc<RefCell<ModulePageState>>,
    groups_summary: &gtk::Label,
    groups_list: &gtk::ListBox,
    standard_summary: &gtk::Label,
    standard_list: &gtk::ListBox,
    extension_summary: &gtk::Label,
    extension_list: &gtk::ListBox,
    group_detail_title: &gtk::Label,
    group_detail_summary: &gtk::Label,
    group_detail_actions: &gtk::Box,
    group_member_list: &gtk::ListBox,
    detail_title: &gtk::Label,
    detail_summary: &gtk::Label,
    detail_actions: &gtk::Box,
    detail_json: &gtk::TextBuffer,
    nav_stack: &gtk::Stack,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    clear_list_box(groups_list);
    clear_list_box(standard_list);
    clear_list_box(extension_list);

    let snapshot = state.borrow();
    let grouped_member_count = snapshot
        .groups
        .iter()
        .map(|group| group.members.len())
        .sum::<usize>();
    groups_summary.set_text(&format!(
        "{} groups · {} members",
        snapshot.groups.len(),
        grouped_member_count
    ));
    let standalone_modules = snapshot
        .modules
        .iter()
        .filter(|module| module_group_key(module).is_empty())
        .cloned()
        .collect::<Vec<_>>();
    let standalone_extensions = snapshot
        .extension_modules
        .iter()
        .filter(|module| module_group_key(module).is_empty())
        .cloned()
        .collect::<Vec<_>>();
    standard_summary.set_text(&format!(
        "{} standalone · {} grouped · {} total",
        standalone_modules.len(),
        snapshot
            .modules
            .len()
            .saturating_sub(standalone_modules.len()),
        snapshot.modules.len()
    ));
    extension_summary.set_text(&format!(
        "{} standalone · {} grouped · {} total",
        standalone_extensions.len(),
        snapshot
            .extension_modules
            .len()
            .saturating_sub(standalone_extensions.len()),
        snapshot.extension_modules.len()
    ));

    if snapshot.groups.is_empty() {
        append_placeholder_row(
            groups_list,
            localized_text(
                strings,
                "当前运行态没有可聚合的模块分组。",
                "The current runtime snapshot does not expose any module groups.",
            ),
        );
    } else {
        for group in snapshot.groups.iter().cloned() {
            let row = gtk::ListBoxRow::new();
            let shell = gtk::Box::new(gtk::Orientation::Horizontal, 12);
            shell.add_css_class("list-row-shell");
            set_margin_all(&shell, 8);

            let icon = module_icon_widget(group.members.first().expect("group member"));
            shell.append(&icon);

            let text = gtk::Box::new(gtk::Orientation::Vertical, 4);
            text.set_hexpand(true);
            let title = gtk::Label::new(Some(&group_display_name(&group)));
            title.set_xalign(0.0);
            title.add_css_class("list-row-title");
            let subtitle = gtk::Label::new(Some(&group_summary_line(&group)));
            subtitle.set_xalign(0.0);
            subtitle.set_wrap(true);
            subtitle.add_css_class("list-row-subtitle");
            text.append(&title);
            text.append(&subtitle);

            let open_button =
                gtk::Button::with_label(localized_text(strings, "打开分组", "Open Group"));
            open_button.add_css_class("pill");
            open_button.add_css_class("tonal-button");
            {
                let state = state.clone();
                let nav_stack = nav_stack.clone();
                let group_detail_title = group_detail_title.clone();
                let group_detail_summary = group_detail_summary.clone();
                let group_detail_actions = group_detail_actions.clone();
                let group_member_list = group_member_list.clone();
                let detail_title = detail_title.clone();
                let detail_summary = detail_summary.clone();
                let detail_actions = detail_actions.clone();
                let detail_json = detail_json.clone();
                let sender = sender.clone();
                let port_entry = port_entry.clone();
                let group_key = group.key.clone();
                open_button.connect_clicked(move |_| {
                    state.borrow_mut().selected_group = Some(group_key.clone());
                    render_module_group_detail(
                        &state,
                        &group_key,
                        &group_detail_title,
                        &group_detail_summary,
                        &group_detail_actions,
                        &group_member_list,
                        &detail_title,
                        &detail_summary,
                        &detail_actions,
                        &detail_json,
                        &nav_stack,
                        &sender,
                        &port_entry,
                        strings,
                    );
                    nav_stack.set_visible_child_name("module-group-detail");
                });
            }

            shell.append(&text);
            shell.append(&open_button);
            row.set_child(Some(&shell));
            groups_list.append(&row);
        }
    }

    render_module_rows_for_section(
        standard_list,
        &standalone_modules,
        state,
        detail_title,
        detail_summary,
        detail_actions,
        detail_json,
        nav_stack,
        sender,
        port_entry,
        strings,
    );
    render_module_rows_for_section(
        extension_list,
        &standalone_extensions,
        state,
        detail_title,
        detail_summary,
        detail_actions,
        detail_json,
        nav_stack,
        sender,
        port_entry,
        strings,
    );

    if let Some(selected_group) = snapshot.selected_group.clone() {
        drop(snapshot);
        render_module_group_detail(
            state,
            &selected_group,
            group_detail_title,
            group_detail_summary,
            group_detail_actions,
            group_member_list,
            detail_title,
            detail_summary,
            detail_actions,
            detail_json,
            nav_stack,
            sender,
            port_entry,
            strings,
        );
        return;
    }
    if let Some(selected_module) = snapshot.selected_module.clone() {
        drop(snapshot);
        render_module_detail(
            state,
            &selected_module,
            detail_title,
            detail_summary,
            detail_actions,
            detail_json,
            sender,
            port_entry,
            strings,
        );
    }
}

fn render_module_rows_for_section(
    list: &gtk::ListBox,
    modules: &[RuntimeModuleEntry],
    state: &Rc<RefCell<ModulePageState>>,
    detail_title: &gtk::Label,
    detail_summary: &gtk::Label,
    detail_actions: &gtk::Box,
    detail_json: &gtk::TextBuffer,
    nav_stack: &gtk::Stack,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    if modules.is_empty() {
        append_placeholder_row(
            list,
            localized_text(
                strings,
                "当前没有条目。",
                "No items reported in this section.",
            ),
        );
        return;
    }

    for module in modules.iter().cloned() {
        let row = gtk::ListBoxRow::new();
        let shell = gtk::Box::new(gtk::Orientation::Horizontal, 12);
        shell.add_css_class("list-row-shell");
        set_margin_all(&shell, 8);

        let icon = module_icon_widget(&module);
        shell.append(&icon);

        let text = gtk::Box::new(gtk::Orientation::Vertical, 4);
        text.set_hexpand(true);
        let title = gtk::Label::new(Some(&module_display_name(&module)));
        title.set_xalign(0.0);
        title.add_css_class("list-row-title");
        let subtitle = gtk::Label::new(Some(&module_compact_summary(&module)));
        subtitle.set_xalign(0.0);
        subtitle.set_wrap(true);
        subtitle.add_css_class("list-row-subtitle");
        text.append(&title);
        text.append(&subtitle);

        let actions = gtk::Box::new(gtk::Orientation::Horizontal, 8);
        actions.add_css_class("inline-actions");

        let toggle = gtk::Switch::new();
        toggle.set_active(module.enabled);
        toggle.set_sensitive(module_can_toggle(&module));
        toggle.set_valign(gtk::Align::Center);
        toggle.add_css_class("compact-switch");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module_id = module.id.clone();
            toggle.connect_active_notify(move |switch| {
                spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                    let module_id = module_id.clone();
                    let active = switch.is_active();
                    move |client| client.set_module_enabled(&module_id, active)
                });
            });
        }
        actions.append(&toggle);

        let details = gtk::Button::with_label(localized_text(strings, "详情", "Details"));
        details.add_css_class("pill");
        details.add_css_class("tonal-button");
        {
            let state = state.clone();
            let detail_title = detail_title.clone();
            let detail_summary = detail_summary.clone();
            let detail_actions = detail_actions.clone();
            let detail_json = detail_json.clone();
            let nav_stack = nav_stack.clone();
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module_id = module.id.clone();
            details.connect_clicked(move |_| {
                state.borrow_mut().selected_module = Some(module_id.clone());
                render_module_detail(
                    &state,
                    &module_id,
                    &detail_title,
                    &detail_summary,
                    &detail_actions,
                    &detail_json,
                    &sender,
                    &port_entry,
                    strings,
                );
                nav_stack.set_visible_child_name("module-detail");
            });
        }
        actions.append(&details);

        shell.append(&text);
        shell.append(&actions);
        row.set_child(Some(&shell));
        list.append(&row);
    }
}

fn render_module_group_detail(
    state: &Rc<RefCell<ModulePageState>>,
    group_key: &str,
    title_label: &gtk::Label,
    summary_label: &gtk::Label,
    actions_box: &gtk::Box,
    member_list: &gtk::ListBox,
    detail_title: &gtk::Label,
    detail_summary: &gtk::Label,
    detail_actions: &gtk::Box,
    detail_json: &gtk::TextBuffer,
    nav_stack: &gtk::Stack,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    clear_box(actions_box);
    clear_list_box(member_list);

    let Some(group) = state.borrow().find_group(group_key).cloned() else {
        title_label.set_text(localized_text(strings, "分组不存在", "Group not found"));
        summary_label.set_text(localized_text(
            strings,
            "当前运行态刷新后已经找不到这个模块分组。",
            "This module group is no longer present in the refreshed runtime snapshot.",
        ));
        append_placeholder_row(
            member_list,
            localized_text(strings, "没有可显示的成员。", "No members to display."),
        );
        return;
    };

    title_label.set_text(&group_display_name(&group));
    summary_label.set_text(&group_summary_line(&group));

    let ordered_members = deterministic_group_members(&group);
    let controllable = ordered_members
        .iter()
        .filter(|module| module_can_toggle(module))
        .cloned()
        .collect::<Vec<_>>();
    let uninstallable = ordered_members
        .iter()
        .filter(|module| module_can_uninstall(module))
        .cloned()
        .collect::<Vec<_>>();
    let actionable = ordered_members
        .iter()
        .filter(|module| module_can_run_action(module))
        .cloned()
        .collect::<Vec<_>>();
    let webui_target = select_group_webui_target(&group);

    if !controllable.is_empty() {
        let enable_all = gtk::Button::with_label(localized_text(strings, "全部启用", "Enable All"));
        enable_all.add_css_class("pill");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let modules = controllable.clone();
            enable_all.connect_clicked(move |_| {
                let modules = modules.clone();
                spawn_agent_sequence_call(
                    parse_port(&port_entry.text()),
                    sender.clone(),
                    move |client, _strings| {
                        let mut lines = Vec::new();
                        for module in &modules {
                            let result = client.set_module_enabled(&module.id, true)?;
                            lines.push(format!(
                                "{}: {}",
                                module_display_name(module),
                                result.trim()
                            ));
                        }
                        Ok(lines.join("\n"))
                    },
                    strings,
                );
            });
        }
        actions_box.append(&enable_all);

        let disable_all =
            gtk::Button::with_label(localized_text(strings, "全部禁用", "Disable All"));
        disable_all.add_css_class("pill");
        disable_all.add_css_class("tonal-button");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let modules = controllable.clone();
            disable_all.connect_clicked(move |_| {
                let modules = modules.clone();
                spawn_agent_sequence_call(
                    parse_port(&port_entry.text()),
                    sender.clone(),
                    move |client, _strings| {
                        let mut lines = Vec::new();
                        for module in &modules {
                            let result = client.set_module_enabled(&module.id, false)?;
                            lines.push(format!(
                                "{}: {}",
                                module_display_name(module),
                                result.trim()
                            ));
                        }
                        Ok(lines.join("\n"))
                    },
                    strings,
                );
            });
        }
        actions_box.append(&disable_all);
    }

    if !uninstallable.is_empty() {
        let uninstall =
            gtk::Button::with_label(localized_text(strings, "全部标记卸载", "Uninstall All"));
        uninstall.add_css_class("pill");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let modules = uninstallable.clone();
            uninstall.connect_clicked(move |_| {
                let modules = modules.clone();
                spawn_agent_sequence_call(
                    parse_port(&port_entry.text()),
                    sender.clone(),
                    move |client, _strings| {
                        let mut lines = Vec::new();
                        for module in &modules {
                            let result = client.set_module_pending_uninstall(&module.id, true)?;
                            lines.push(format!(
                                "{}: {}",
                                module_display_name(module),
                                result.trim()
                            ));
                        }
                        Ok(lines.join("\n"))
                    },
                    strings,
                );
            });
        }
        actions_box.append(&uninstall);
    }

    if !actionable.is_empty() {
        let action = gtk::Button::with_label(localized_text(
            strings,
            "运行成员 Action",
            "Run Member Actions",
        ));
        action.add_css_class("pill");
        action.add_css_class("tonal-button");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let modules = actionable.clone();
            action.connect_clicked(move |_| {
                let modules = modules.clone();
                spawn_agent_sequence_call(
                    parse_port(&port_entry.text()),
                    sender.clone(),
                    move |client, strings| {
                        let mut lines = Vec::new();
                        for module in &modules {
                            let task = client.run_module_action(&module.id)?;
                            lines.push(format!(
                                "{} {} ({})",
                                strings.log_task_queued,
                                task.id,
                                module_display_name(module)
                            ));
                            let final_task =
                                client.poll_task(&task.id, Duration::from_secs(300))?;
                            lines.push(format!(
                                "{} {} -> {}",
                                strings.log_task_result, final_task.id, final_task.state
                            ));
                            if !final_task.output.is_empty() {
                                lines.push(final_task.output.join("\n"));
                            }
                        }
                        Ok(lines.join("\n\n"))
                    },
                    strings,
                );
            });
        }
        actions_box.append(&action);
    }

    if let Some(target) = webui_target {
        let webui = gtk::Button::with_label(localized_text(strings, "打开 WebUI", "Open WebUI"));
        webui.add_css_class("pill");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let target = target.clone();
            webui.connect_clicked(move |_| {
                let port = parse_port(&port_entry.text());
                let sender = sender.clone();
                spawn_module_webui_helper(
                    port,
                    target.id.clone(),
                    module_display_name(&target),
                    move |text| {
                        let _ = sender.send(UiMessage::ActivityLog(text));
                    },
                );
            });
        }
        actions_box.append(&webui);
    }

    for module in ordered_members {
        let row = gtk::ListBoxRow::new();
        let shell = gtk::Box::new(gtk::Orientation::Horizontal, 12);
        shell.add_css_class("list-row-shell");
        set_margin_all(&shell, 8);

        let icon = module_icon_widget(&module);
        shell.append(&icon);

        let text = gtk::Box::new(gtk::Orientation::Vertical, 4);
        text.set_hexpand(true);
        let title = gtk::Label::new(Some(&module_display_name(&module)));
        title.set_xalign(0.0);
        title.add_css_class("list-row-title");
        let subtitle = gtk::Label::new(Some(&module_compact_summary(&module)));
        subtitle.set_xalign(0.0);
        subtitle.set_wrap(true);
        subtitle.add_css_class("list-row-subtitle");
        text.append(&title);
        text.append(&subtitle);

        let details = gtk::Button::with_label(localized_text(strings, "详情", "Details"));
        details.add_css_class("pill");
        details.add_css_class("tonal-button");
        {
            let state = state.clone();
            let detail_title = detail_title.clone();
            let detail_summary = detail_summary.clone();
            let detail_actions = detail_actions.clone();
            let detail_json = detail_json.clone();
            let nav_stack = nav_stack.clone();
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module_id = module.id.clone();
            details.connect_clicked(move |_| {
                state.borrow_mut().selected_module = Some(module_id.clone());
                render_module_detail(
                    &state,
                    &module_id,
                    &detail_title,
                    &detail_summary,
                    &detail_actions,
                    &detail_json,
                    &sender,
                    &port_entry,
                    strings,
                );
                nav_stack.set_visible_child_name("module-detail");
            });
        }

        shell.append(&text);
        shell.append(&details);
        row.set_child(Some(&shell));
        member_list.append(&row);
    }
}

fn render_module_detail(
    state: &Rc<RefCell<ModulePageState>>,
    module_id: &str,
    title_label: &gtk::Label,
    summary_label: &gtk::Label,
    actions_box: &gtk::Box,
    json_buffer: &gtk::TextBuffer,
    sender: &Sender<UiMessage>,
    port_entry: &gtk::Entry,
    strings: Strings,
) {
    clear_box(actions_box);
    let Some(module) = state.borrow().find_module(module_id).cloned() else {
        title_label.set_text(localized_text(strings, "模块不存在", "Module not found"));
        summary_label.set_text(localized_text(
            strings,
            "刷新后的运行态里已经没有这个模块。",
            "This module is no longer present in the refreshed runtime snapshot.",
        ));
        json_buffer.set_text("{}");
        return;
    };

    title_label.set_text(&module_display_name(&module));
    summary_label.set_text(&module_detail_summary_text(&module));
    if let Ok(text) = pretty_json_value(&module.raw) {
        json_buffer.set_text(&text);
    }

    let toggle = gtk::Switch::new();
    toggle.set_active(module.enabled);
    toggle.set_sensitive(module_can_toggle(&module));
    toggle.set_valign(gtk::Align::Center);
    {
        let sender = sender.clone();
        let port_entry = port_entry.clone();
        let module_id = module.id.clone();
        toggle.connect_active_notify(move |switch| {
            spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                let module_id = module_id.clone();
                let active = switch.is_active();
                move |client| client.set_module_enabled(&module_id, active)
            });
        });
    }
    actions_box.append(&toggle);

    if module_can_uninstall(&module) {
        let uninstall = gtk::Button::with_label(strings.uninstall_module);
        uninstall.add_css_class("pill");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module_id = module.id.clone();
            uninstall.connect_clicked(move |_| {
                spawn_agent_sync_call(parse_port(&port_entry.text()), sender.clone(), {
                    let module_id = module_id.clone();
                    move |client| client.set_module_pending_uninstall(&module_id, true)
                });
            });
        }
        actions_box.append(&uninstall);
    }

    if module_can_run_action(&module) {
        let action = gtk::Button::with_label(strings.run_action);
        action.add_css_class("pill");
        action.add_css_class("tonal-button");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module_id = module.id.clone();
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
        actions_box.append(&action);
    }

    if module.has_web_ui {
        let webui = gtk::Button::with_label(localized_text(strings, "打开 WebUI", "Open WebUI"));
        webui.add_css_class("pill");
        {
            let sender = sender.clone();
            let port_entry = port_entry.clone();
            let module = module.clone();
            webui.connect_clicked(move |_| {
                let port = parse_port(&port_entry.text());
                let sender = sender.clone();
                spawn_module_webui_helper(
                    port,
                    module.id.clone(),
                    module_display_name(&module),
                    move |text| {
                        let _ = sender.send(UiMessage::ActivityLog(text));
                    },
                );
            });
        }
        actions_box.append(&webui);
    }
}

fn clear_list_box(list: &gtk::ListBox) {
    while let Some(child) = list.first_child() {
        list.remove(&child);
    }
}

fn clear_box(container: &gtk::Box) {
    while let Some(child) = container.first_child() {
        container.remove(&child);
    }
}

impl ModulePageState {
    fn find_module(&self, module_id: &str) -> Option<&RuntimeModuleEntry> {
        self.modules
            .iter()
            .chain(self.extension_modules.iter())
            .find(|module| module.id == module_id)
    }

    fn find_group(&self, group_key: &str) -> Option<&ModuleGroupEntry> {
        self.groups.iter().find(|group| group.key == group_key)
    }
}

fn runtime_module_records(value: &Value, keys: &[&str]) -> Vec<Value> {
    find_named_array_recursive(value, keys)
        .unwrap_or_default()
        .into_iter()
        .filter(is_runtime_module_record)
        .collect()
}

fn find_named_array_recursive<'a>(value: &'a Value, keys: &[&str]) -> Option<Vec<Value>> {
    match value {
        Value::Object(map) => {
            for key in keys {
                if let Some(items) = map.get(*key).and_then(Value::as_array) {
                    return Some(items.clone());
                }
            }
            map.values()
                .find_map(|nested| find_named_array_recursive(nested, keys))
        }
        Value::Array(items) => items
            .iter()
            .find_map(|nested| find_named_array_recursive(nested, keys)),
        _ => None,
    }
}

fn parse_runtime_module_entry(raw: Value, list_kind: ModuleListKind) -> Option<RuntimeModuleEntry> {
    let id = json_str_any_recursive(&raw, &["id"])?.trim().to_string();
    if id.is_empty() {
        return None;
    }
    Some(RuntimeModuleEntry {
        id: id.clone(),
        name: json_str_any_recursive(&raw, &["name"])
            .unwrap_or(&id)
            .trim()
            .to_string(),
        author: json_str_any_recursive(&raw, &["author"])
            .unwrap_or("")
            .trim()
            .to_string(),
        type_name: json_str_any_recursive(&raw, &["type"])
            .unwrap_or("")
            .trim()
            .to_string(),
        version: json_str_any_recursive(&raw, &["version"])
            .unwrap_or("")
            .trim()
            .to_string(),
        description: json_str_any_recursive(&raw, &["description"])
            .unwrap_or("")
            .trim()
            .to_string(),
        repo_url: json_str_any_recursive(&raw, &["repo_url", "repoUrl"])
            .unwrap_or("")
            .trim()
            .to_string(),
        entry_kind: json_str_any_recursive(&raw, &["entry_kind", "entryKind"])
            .unwrap_or("")
            .trim()
            .to_string(),
        source: json_str_any_recursive(&raw, &["source"])
            .unwrap_or("")
            .trim()
            .to_string(),
        extension_id: json_str_any_recursive(&raw, &["extension_id", "extensionId"])
            .unwrap_or("")
            .trim()
            .to_string(),
        companion_package: json_str_any_recursive(&raw, &["companion_package", "companionPackage"])
            .unwrap_or("")
            .trim()
            .to_string(),
        companion_display_name: json_str_any_recursive(
            &raw,
            &["companion_display_name", "companionDisplayName"],
        )
        .unwrap_or("")
        .trim()
        .to_string(),
        service_activity: json_str_any_recursive(&raw, &["service_activity", "serviceActivity"])
            .unwrap_or("")
            .trim()
            .to_string(),
        module_dir: json_str_any_recursive(&raw, &["module_dir", "moduleDir"])
            .unwrap_or("")
            .trim()
            .to_string(),
        web_root: json_str_any_recursive(&raw, &["web_root", "webRoot"])
            .unwrap_or("")
            .trim()
            .to_string(),
        readonly: json_bool_opt_recursive(&raw, &["readonly"]).unwrap_or(false),
        controllable: json_bool_opt_recursive(&raw, &["controllable"]).unwrap_or(false),
        enabled: json_bool_opt_recursive(&raw, &["enabled"]).unwrap_or(false),
        update: json_bool_opt_recursive(&raw, &["update"]).unwrap_or(false),
        remove: json_bool_opt_recursive(&raw, &["remove"]).unwrap_or(false),
        has_web_ui: json_bool_opt_recursive(&raw, &["has_web_ui", "hasWebUi"]).unwrap_or(false),
        has_action_script: json_bool_opt_recursive(&raw, &["has_action_script", "hasActionScript"])
            .unwrap_or(false),
        action_supported: json_bool_opt_recursive(&raw, &["action_supported", "actionSupported"])
            .unwrap_or(false),
        requires_companion_app: json_bool_opt_recursive(
            &raw,
            &["requires_companion_app", "requiresCompanionApp"],
        )
        .unwrap_or(false),
        settings_supported: json_bool_opt_recursive(
            &raw,
            &["settings_supported", "settingsSupported"],
        )
        .unwrap_or(false),
        per_app_supported: json_bool_opt_recursive(&raw, &["per_app_supported", "perAppSupported"])
            .unwrap_or(false),
        group_id: json_str_any_recursive(&raw, &["group_id", "groupId"])
            .unwrap_or("")
            .trim()
            .to_string(),
        group_name: json_str_any_recursive(&raw, &["group_name", "groupName"])
            .unwrap_or("")
            .trim()
            .to_string(),
        group_role: json_str_any_recursive(&raw, &["group_role", "groupRole"])
            .unwrap_or("")
            .trim()
            .to_string(),
        group_description: json_str_any_recursive(&raw, &["group_description", "groupDescription"])
            .unwrap_or("")
            .trim()
            .to_string(),
        group_repo_url: json_str_any_recursive(&raw, &["group_repo_url", "groupRepoUrl"])
            .unwrap_or("")
            .trim()
            .to_string(),
        list_kind,
        raw,
    })
}

fn build_module_groups(
    modules: &[RuntimeModuleEntry],
    extension_modules: &[RuntimeModuleEntry],
) -> Vec<ModuleGroupEntry> {
    let mut grouped: HashMap<String, Vec<RuntimeModuleEntry>> = HashMap::new();
    for module in modules.iter().chain(extension_modules.iter()) {
        let key = module_group_key(module);
        if key.is_empty() {
            continue;
        }
        grouped.entry(key).or_default().push(module.clone());
    }

    let mut groups = grouped
        .into_iter()
        .map(|(key, mut members)| {
            members.sort_by(|left, right| {
                module_display_name(left)
                    .to_lowercase()
                    .cmp(&module_display_name(right).to_lowercase())
                    .then_with(|| left.id.cmp(&right.id))
            });
            let first = members
                .first()
                .cloned()
                .unwrap_or_else(|| RuntimeModuleEntry {
                    id: key.clone(),
                    name: key.clone(),
                    author: String::new(),
                    type_name: String::new(),
                    version: String::new(),
                    description: String::new(),
                    repo_url: String::new(),
                    entry_kind: String::new(),
                    source: String::new(),
                    extension_id: String::new(),
                    companion_package: String::new(),
                    companion_display_name: String::new(),
                    service_activity: String::new(),
                    module_dir: String::new(),
                    web_root: String::new(),
                    readonly: false,
                    controllable: false,
                    enabled: false,
                    update: false,
                    remove: false,
                    has_web_ui: false,
                    has_action_script: false,
                    action_supported: false,
                    requires_companion_app: false,
                    settings_supported: false,
                    per_app_supported: false,
                    group_id: String::new(),
                    group_name: String::new(),
                    group_role: String::new(),
                    group_description: String::new(),
                    group_repo_url: String::new(),
                    list_kind: ModuleListKind::Standard,
                    raw: Value::Null,
                });
            ModuleGroupEntry {
                key,
                name: first.group_name.clone(),
                role: first.group_role.clone(),
                description: first.group_description.clone(),
                members,
            }
        })
        .collect::<Vec<_>>();

    groups.sort_by(|left, right| {
        group_display_name(left)
            .to_lowercase()
            .cmp(&group_display_name(right).to_lowercase())
            .then_with(|| left.key.cmp(&right.key))
    });
    groups
}

fn module_group_key(module: &RuntimeModuleEntry) -> String {
    if !module.group_repo_url.is_empty() {
        format!("repo:{}", module.group_repo_url.to_lowercase())
    } else if !module.group_id.is_empty() {
        format!("id:{}", module.group_id.to_lowercase())
    } else if !module.group_name.is_empty() {
        format!("name:{}", module.group_name.to_lowercase())
    } else {
        String::new()
    }
}

fn module_display_name(module: &RuntimeModuleEntry) -> String {
    if module.name.trim().is_empty() {
        module.id.clone()
    } else {
        module.name.clone()
    }
}

fn module_hub_summary(state: &ModulePageState, strings: Strings) -> String {
    if state.modules.is_empty() && state.extension_modules.is_empty() {
        return localized_text(
            strings,
            "还没有运行态模块快照。",
            "No runtime module snapshot has been loaded yet.",
        )
        .to_string();
    }
    format!(
        "{} groups · {} modules · {} extensions",
        state.groups.len(),
        state.modules.len(),
        state.extension_modules.len()
    )
}

fn module_compact_summary(module: &RuntimeModuleEntry) -> String {
    let mut traits = Vec::new();
    if module.update {
        traits.push("update");
    }
    if module.remove {
        traits.push("pending uninstall");
    }
    if module.has_web_ui {
        traits.push("WebUI");
    }
    if module.requires_companion_app {
        traits.push("companion");
    }
    if module.settings_supported {
        traits.push("settings");
    }
    if module.per_app_supported {
        traits.push("per-app");
    }
    let mut base = format!(
        "{} · {} · {}",
        module.id,
        if module.source.is_empty() {
            "runtime"
        } else {
            module.source.as_str()
        },
        if module.enabled {
            "enabled"
        } else {
            "disabled"
        }
    );
    if !traits.is_empty() {
        base.push_str(" · ");
        base.push_str(&traits.join(", "));
    }
    base
}

fn module_detail_summary_text(module: &RuntimeModuleEntry) -> String {
    let mut lines = vec![
        format!("id: {}", module.id),
        format!("type: {}", module_normalized_type(module)),
        format!(
            "source: {}",
            if module.source.is_empty() {
                "runtime"
            } else {
                &module.source
            }
        ),
        format!(
            "state: {}",
            if module.enabled {
                "enabled"
            } else {
                "disabled"
            }
        ),
    ];
    if !module.version.is_empty() {
        lines.push(format!("version: {}", module.version));
    }
    if !module.author.is_empty() {
        lines.push(format!("author: {}", module.author));
    }
    if !module.description.is_empty() {
        lines.push(module.description.clone());
    }
    if !module.group_name.is_empty() {
        lines.push(format!(
            "group: {}",
            group_display_name(&ModuleGroupEntry {
                key: module_group_key(module),
                name: module.group_name.clone(),
                role: module.group_role.clone(),
                description: module.group_description.clone(),
                members: vec![module.clone()],
            })
        ));
    }
    if !module.companion_package.is_empty() {
        lines.push(format!("companion: {}", module.companion_package));
    }
    if !module.service_activity.is_empty() {
        lines.push(format!("service: {}", module.service_activity));
    }
    if !module.web_root.is_empty() {
        lines.push(format!("web root: {}", module.web_root));
    }
    lines.join("\n")
}

fn group_display_name(group: &ModuleGroupEntry) -> String {
    if !group.name.trim().is_empty() {
        group.name.clone()
    } else {
        group
            .key
            .split_once(':')
            .map(|(_, value)| value.to_string())
            .unwrap_or_else(|| group.key.clone())
    }
}

fn group_summary_line(group: &ModuleGroupEntry) -> String {
    let enabled = group.members.iter().filter(|module| module.enabled).count();
    let webui = group
        .members
        .iter()
        .filter(|module| module.has_web_ui)
        .count();
    let action = group
        .members
        .iter()
        .filter(|module| module_can_run_action(module))
        .count();
    let role = if group.role.is_empty() {
        "group".to_string()
    } else {
        group.role.clone()
    };
    let description = if group.description.is_empty() {
        String::new()
    } else {
        format!(" · {}", group.description)
    };
    format!(
        "{} · {} members · {} enabled · {} action-capable · {} WebUI{}",
        role,
        group.members.len(),
        enabled,
        action,
        webui,
        description
    )
}

fn deterministic_group_members(group: &ModuleGroupEntry) -> Vec<RuntimeModuleEntry> {
    let mut members = group.members.clone();
    members.sort_by(|left, right| {
        group_role_priority(&left.group_role)
            .cmp(&group_role_priority(&right.group_role))
            .then_with(|| right.enabled.cmp(&left.enabled))
            .then_with(|| {
                module_display_name(left)
                    .to_lowercase()
                    .cmp(&module_display_name(right).to_lowercase())
            })
            .then_with(|| left.id.cmp(&right.id))
    });
    members
}

fn select_group_webui_target(group: &ModuleGroupEntry) -> Option<RuntimeModuleEntry> {
    deterministic_group_members(group)
        .into_iter()
        .find(|module| module.has_web_ui)
}

fn group_role_priority(role: &str) -> usize {
    match role.trim().to_lowercase().as_str() {
        "primary" | "manager" | "main" | "base" => 0,
        "core" => 1,
        "extension" | "addon" => 2,
        _ => 9,
    }
}

fn module_normalized_type(module: &RuntimeModuleEntry) -> &str {
    if !module.type_name.is_empty() {
        module.type_name.as_str()
    } else if module.source.split(',').any(|part| part.trim() == "ksud") {
        "standard"
    } else if module.source.split(',').any(|part| part.trim() == "kpm") {
        "kpm"
    } else {
        "builtin"
    }
}

fn module_can_toggle(module: &RuntimeModuleEntry) -> bool {
    module.controllable && !module.readonly && !module.id.is_empty()
}

fn module_can_uninstall(module: &RuntimeModuleEntry) -> bool {
    !module.readonly
        && (module_normalized_type(module) == "standard" || module.source.contains("ksud"))
}

fn module_can_run_action(module: &RuntimeModuleEntry) -> bool {
    module.action_supported || module.has_action_script
}

fn default_susfs_legit_mounts() -> Vec<String> {
    [
        "/system",
        "/system_ext",
        "/vendor",
        "/odm",
        "/product",
        "/system_dlkm",
        "/vendor_dlkm",
        "/odm_dlkm",
        "/apex",
        "/system/app",
        "/system/priv-app",
        "/system/lib",
        "/system/lib64",
        "/vendor/app",
        "/vendor/priv-app",
        "/vendor/lib",
        "/vendor/lib64",
        "/product/app",
        "/product/priv-app",
        "/product/lib",
        "/product/lib64",
        "/system_ext/app",
        "/system_ext/priv-app",
        "/system_ext/lib",
        "/system_ext/lib64",
        "/data",
        "/cache",
        "/metadata",
        "/persist",
        "/mnt",
        "/storage",
        "/debug_ramdisk",
        "/dev",
        "/proc",
        "/sys",
        "/sys/fs/cgroup",
        "/my_product",
        "/my_engineering",
        "/my_company",
        "/my_carrier",
        "/my_region",
        "/my_heytap",
        "/my_stock",
        "/my_preload",
        "/my_bigball",
        "/my_manifest",
    ]
    .into_iter()
    .map(ToString::to_string)
    .collect()
}

fn update_susfs_page_state(state: &Rc<RefCell<SusfsPageState>>, value: &Value) {
    let previous_output = state.borrow().action_output.clone();
    let raw_config = value
        .get("config")
        .cloned()
        .unwrap_or_else(|| Value::Object(Default::default()));
    let parsed_config =
        serde_json::from_value::<SusfsConfigModel>(raw_config.clone()).unwrap_or_default();
    let status = value
        .get("status")
        .cloned()
        .and_then(|item| serde_json::from_value::<SusfsRuntimeStatusModel>(item).ok())
        .unwrap_or_default();
    let mut guard = state.borrow_mut();
    guard.raw_snapshot = value.clone();
    guard.raw_config = raw_config;
    guard.config = normalize_susfs_config_model(parsed_config);
    guard.status = status.clone();
    guard.support = status.support.clone();
    guard.root_granted =
        json_bool_opt_recursive(value, &["rootGranted", "root_granted"]).unwrap_or(true);
    guard.error = json_str_any_recursive(value, &["error"]).map(ToString::to_string);
    guard.action_output = previous_output;
}

fn susfs_hub_summary(state: &SusfsPageState, strings: Strings) -> String {
    if !state.root_granted {
        return localized_text(
            strings,
            "设备未授予 Root。",
            "Root is not granted on the device.",
        )
        .to_string();
    }
    if let Some(error) = state.error.as_deref() {
        return error.to_string();
    }
    format!(
        "{} · kernel {} · {} path rules · {} redirects",
        if state.status.available {
            localized_text(strings, "可用", "available")
        } else {
            localized_text(strings, "不可用", "unavailable")
        },
        if state.status.kernel_version.is_empty() {
            "unknown"
        } else {
            state.status.kernel_version.as_str()
        },
        state.config.path_rules.len(),
        state.config.open_redirects.len()
    )
}

fn build_susfs_apply_body(state: &Rc<RefCell<SusfsPageState>>) -> anyhow::Result<String> {
    let guard = state.borrow();
    let normalized = normalize_susfs_config_model(guard.config.clone());
    let overrides = serde_json::to_value(&normalized)?;
    let mut merged = if guard.raw_config.is_object() {
        guard.raw_config.clone()
    } else {
        Value::Object(Default::default())
    };
    merge_json_values(&mut merged, overrides);
    Ok(serde_json::to_string_pretty(&merged)?)
}

fn merge_json_values(base: &mut Value, override_value: Value) {
    match (base, override_value) {
        (Value::Object(base_map), Value::Object(override_map)) => {
            for (key, value) in override_map {
                if let Some(existing) = base_map.get_mut(&key) {
                    merge_json_values(existing, value);
                } else {
                    base_map.insert(key, value);
                }
            }
        }
        (base_slot, value) => {
            *base_slot = value;
        }
    }
}

fn normalize_susfs_config_model(config: SusfsConfigModel) -> SusfsConfigModel {
    let mut normalized = config;
    normalized.schema_version = normalized.schema_version.max(1);
    normalized.hide_sus_mounts_mode =
        normalize_hide_sus_mounts_mode(&normalized.hide_sus_mounts_mode);
    normalized.spoof_uname_stage = normalize_spoof_uname_stage(&normalized.spoof_uname_stage);
    normalized.uname_value = blank_to_default(&normalized.uname_value, "default");
    normalized.build_time_value = blank_to_default(&normalized.build_time_value, "default");
    normalized.sdcard_root_path = blank_to_default(&normalized.sdcard_root_path, "/sdcard");
    normalized.android_data_root_path =
        blank_to_default(&normalized.android_data_root_path, "/sdcard/Android/data");
    normalized.path_rules = normalized
        .path_rules
        .into_iter()
        .filter_map(|rule| {
            let path = rule.path.trim().to_string();
            if path.is_empty() {
                None
            } else {
                Some(SusfsPathRuleModel {
                    path,
                    max_tries: rule.max_tries,
                })
            }
        })
        .collect();
    normalized.loop_path_rules = normalized
        .loop_path_rules
        .into_iter()
        .filter_map(|rule| {
            let path = rule.path.trim().to_string();
            if path.is_empty() {
                None
            } else {
                Some(SusfsPathRuleModel {
                    path,
                    max_tries: rule.max_tries,
                })
            }
        })
        .collect();
    normalized.maps = normalize_string_list(normalized.maps);
    normalized.mounts = normalize_string_list(normalized.mounts);
    normalized.try_umounts = normalize_string_list(normalized.try_umounts);
    normalized.legit_mounts = {
        let mounts = normalize_string_list(normalized.legit_mounts);
        if mounts.is_empty() {
            default_susfs_legit_mounts()
        } else {
            mounts
        }
    };
    normalized.open_redirects = normalized
        .open_redirects
        .into_iter()
        .filter_map(|rule| {
            let original_path = rule.original_path.trim().to_string();
            let redirected_path = rule.redirected_path.trim().to_string();
            if original_path.is_empty() || redirected_path.is_empty() {
                None
            } else {
                Some(SusfsOpenRedirectRuleModel {
                    original_path,
                    redirected_path,
                    stage: normalize_open_redirect_stage(&rule.stage),
                    uid_scheme: rule.uid_scheme.map(|value| value.clamp(0, 4)),
                })
            }
        })
        .collect();
    normalized.kstat_entries = normalized
        .kstat_entries
        .into_iter()
        .filter_map(|entry| {
            let path = entry.path.trim().to_string();
            if path.is_empty() {
                None
            } else {
                Some(SusfsKstatEntryModel { path, ..entry })
            }
        })
        .collect();
    normalized.presets.hide_custom_rom_level = normalized.presets.hide_custom_rom_level.clamp(0, 5);
    normalized.presets.emulate_vold_app_data_mode =
        normalized.presets.emulate_vold_app_data_mode.clamp(0, 2);
    normalized
}

fn normalize_hide_sus_mounts_mode(value: &str) -> String {
    match value.trim().to_lowercase().as_str() {
        "all" => "all".into(),
        "non_su" => "non_su".into(),
        _ => "off".into(),
    }
}

fn normalize_spoof_uname_stage(value: &str) -> String {
    match value.trim().to_lowercase().as_str() {
        "post_fs_data" => "post_fs_data".into(),
        "boot_completed" => "boot_completed".into(),
        _ => "off".into(),
    }
}

fn normalize_open_redirect_stage(value: &str) -> String {
    match value.trim().to_lowercase().as_str() {
        "service" | "1" => "service".into(),
        _ => "boot_completed".into(),
    }
}

fn blank_to_default(value: &str, fallback: &str) -> String {
    let clean = value.trim();
    if clean.is_empty() {
        fallback.to_string()
    } else {
        clean.to_string()
    }
}

fn normalize_string_list(values: Vec<String>) -> Vec<String> {
    values
        .into_iter()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .collect()
}

fn render_susfs_page(
    state: &Rc<RefCell<SusfsPageState>>,
    status_label: &gtk::Label,
    support_label: &gtk::Label,
    editor_host: &gtk::Box,
    strings: Strings,
) {
    let snapshot = state.borrow().clone();
    status_label.set_text(&susfs_hub_summary(&snapshot, strings));
    support_label.set_text(&susfs_support_summary(&snapshot.support, strings));
    clear_box(editor_host);

    let editable = snapshot.root_granted;

    let core_card = surface_card(
        localized_text(strings, "核心开关与路径", "Core Flags and Paths"),
        localized_text(
            strings,
            "标量开关、枚举和核心路径都在这里编辑。",
            "Scalar toggles, enums, and root paths are edited here.",
        ),
    );
    append_switch_row(
        &core_card,
        localized_text(strings, "自动回放", "Auto replay"),
        snapshot.config.auto_replay_enabled,
        editable,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.auto_replay_enabled = value
        },
    );
    append_switch_row(
        &core_card,
        localized_text(strings, "日志开关", "Log enabled"),
        snapshot.config.log_enabled,
        editable && snapshot.support.log,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.log_enabled = value
        },
    );
    append_switch_row(
        &core_card,
        localized_text(strings, "AVC spoofing", "AVC spoofing"),
        snapshot.config.avc_log_spoofing,
        editable && snapshot.support.avc_log_spoofing,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.avc_log_spoofing = value
        },
    );
    append_combo_row(
        &core_card,
        localized_text(strings, "隐藏挂载模式", "Hide mounts mode"),
        &[("off", "off"), ("all", "all"), ("non_su", "non_su")],
        &snapshot.config.hide_sus_mounts_mode,
        editable
            && (snapshot.support.hide_sus_mounts_for_all
                || snapshot.support.hide_sus_mounts_for_non_su),
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.hide_sus_mounts_mode = value
        },
    );
    append_combo_row(
        &core_card,
        localized_text(strings, "Spoof uname 阶段", "Spoof uname stage"),
        &[
            ("off", "off"),
            ("post_fs_data", "post_fs_data"),
            ("boot_completed", "boot_completed"),
        ],
        &snapshot.config.spoof_uname_stage,
        editable && snapshot.support.set_uname,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.spoof_uname_stage = value
        },
    );
    append_entry_row(
        &core_card,
        localized_text(strings, "uname 值", "uname value"),
        &snapshot.config.uname_value,
        editable && snapshot.support.set_uname,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.uname_value = value
        },
    );
    append_entry_row(
        &core_card,
        localized_text(strings, "build time 值", "build time value"),
        &snapshot.config.build_time_value,
        editable && snapshot.support.set_uname,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.build_time_value = value
        },
    );
    append_entry_row(
        &core_card,
        localized_text(strings, "sdcard 根路径", "sdcard root path"),
        &snapshot.config.sdcard_root_path,
        editable && snapshot.support.sdcard_root_path,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.sdcard_root_path = value
        },
    );
    append_entry_row(
        &core_card,
        localized_text(strings, "Android/data 根路径", "Android/data root path"),
        &snapshot.config.android_data_root_path,
        editable && snapshot.support.android_data_root_path,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.android_data_root_path = value
        },
    );
    editor_host.append(&core_card);

    let presets_card = surface_card(
        localized_text(strings, "预设与兼容选项", "Presets and Compatibility"),
        localized_text(
            strings,
            "预设布尔项和整数枚举在这一组统一配置。",
            "Preset booleans and integer enums are configured together here.",
        ),
    );
    append_entry_row(
        &presets_card,
        localized_text(strings, "Hide custom ROM level", "Hide custom ROM level"),
        &snapshot.presets_hide_custom_rom_level_text(),
        editable,
        {
            let state = state.clone();
            move |value| {
                if let Ok(parsed) = value.trim().parse::<i64>() {
                    state.borrow_mut().config.presets.hide_custom_rom_level = parsed.clamp(0, 5);
                }
            }
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Hide vendor sepolicy", "Hide vendor sepolicy"),
        snapshot.config.presets.hide_vendor_sepolicy,
        editable && snapshot.support.hide_vendor_sepolicy_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.hide_vendor_sepolicy = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Hide compat matrix", "Hide compat matrix"),
        snapshot.config.presets.hide_compat_matrix,
        editable && snapshot.support.hide_compat_matrix_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.hide_compat_matrix = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Hide GApps", "Hide GApps"),
        snapshot.config.presets.hide_gapps,
        editable && snapshot.support.hide_gapps_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.hide_gapps = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Hide ReVanced", "Hide ReVanced"),
        snapshot.config.presets.hide_revanced,
        editable && snapshot.support.hide_revanced_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.hide_revanced = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Spoof cmdline", "Spoof cmdline"),
        snapshot.config.presets.spoof_cmdline,
        editable && snapshot.support.spoof_cmdline_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.spoof_cmdline = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Hide loops", "Hide loops"),
        snapshot.config.presets.hide_loops,
        editable && snapshot.support.hide_loops_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.hide_loops = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Force hide LSPosed", "Force hide LSPosed"),
        snapshot.config.presets.force_hide_lsposed,
        editable && snapshot.support.force_hide_lsposed_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.force_hide_lsposed = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Auto tryUmount", "Auto tryUmount"),
        snapshot.config.presets.auto_try_umount,
        editable && snapshot.support.auto_try_umount_preset,
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.auto_try_umount = value
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(strings, "Skip legit mounts", "Skip legit mounts"),
        snapshot.config.presets.skip_legit_mounts,
        editable && (snapshot.support.try_umount || snapshot.support.ksud_kernel_umount_fallback),
        {
            let state = state.clone();
            move |value| state.borrow_mut().config.presets.skip_legit_mounts = value
        },
    );
    append_entry_row(
        &presets_card,
        localized_text(
            strings,
            "Emulate vold app-data mode",
            "Emulate vold app-data mode",
        ),
        &snapshot.emulate_vold_mode_text(),
        editable,
        {
            let state = state.clone();
            move |value| {
                if let Ok(parsed) = value.trim().parse::<i64>() {
                    state.borrow_mut().config.presets.emulate_vold_app_data_mode =
                        parsed.clamp(0, 2);
                }
            }
        },
    );
    append_switch_row(
        &presets_card,
        localized_text(
            strings,
            "Zygote iso-service umount",
            "Zygote iso-service umount",
        ),
        snapshot.config.presets.umount_for_zygote_iso_service,
        editable && snapshot.support.umount_for_zygote_iso_service,
        {
            let state = state.clone();
            move |value| {
                state
                    .borrow_mut()
                    .config
                    .presets
                    .umount_for_zygote_iso_service = value
            }
        },
    );
    editor_host.append(&presets_card);

    append_path_rules_editor(
        editor_host,
        state,
        status_label,
        support_label,
        localized_text(strings, "Path Rules", "Path Rules"),
        localized_text(
            strings,
            "普通路径规则，支持路径与可选的重试次数。",
            "Standard path rules with path and optional retry count.",
        ),
        "path_rules",
        editable && snapshot.support.sus_path,
        strings,
    );
    append_path_rules_editor(
        editor_host,
        state,
        status_label,
        support_label,
        localized_text(strings, "Loop Path Rules", "Loop Path Rules"),
        localized_text(
            strings,
            "Loop 路径规则只在 runtime support 明确支持时可编辑。",
            "Loop path rules are editable only when runtime support exposes them.",
        ),
        "loop_path_rules",
        editable && snapshot.support.sus_path_loop,
        strings,
    );

    append_string_list_editor(
        editor_host,
        state,
        localized_text(strings, "Maps", "Maps"),
        localized_text(strings, "每行一个 map 规则。", "One map rule per line."),
        "maps",
        editable && snapshot.support.sus_map,
        strings,
    );
    append_string_list_editor(
        editor_host,
        state,
        localized_text(strings, "Mounts", "Mounts"),
        localized_text(strings, "每行一个 mount 规则。", "One mount rule per line."),
        "mounts",
        editable && snapshot.support.sus_mount,
        strings,
    );
    append_string_list_editor(
        editor_host,
        state,
        localized_text(strings, "TryUmounts", "TryUmounts"),
        localized_text(
            strings,
            "每行一个 tryUmount 项。",
            "One tryUmount item per line.",
        ),
        "try_umounts",
        editable && snapshot.support.try_umount,
        strings,
    );
    append_string_list_editor(
        editor_host,
        state,
        localized_text(strings, "LegitMounts", "LegitMounts"),
        localized_text(
            strings,
            "每行一个合法挂载路径。",
            "One legit mount path per line.",
        ),
        "legit_mounts",
        editable && (snapshot.support.try_umount || snapshot.support.ksud_kernel_umount_fallback),
        strings,
    );

    append_open_redirects_editor(
        editor_host,
        state,
        status_label,
        support_label,
        editable && snapshot.support.open_redirect,
        strings,
    );
    append_kstat_editor(
        editor_host,
        state,
        status_label,
        support_label,
        editable && (snapshot.support.static_kstat || snapshot.support.dynamic_kstat),
        strings,
    );
}

fn susfs_support_summary(support: &SusfsSupportMatrixModel, strings: Strings) -> String {
    let mut parts = Vec::new();
    if support.sus_path {
        parts.push(localized_text(strings, "path", "path"));
    }
    if support.sus_path_loop {
        parts.push(localized_text(strings, "loop", "loop"));
    }
    if support.sus_map {
        parts.push("map");
    }
    if support.sus_mount {
        parts.push("mount");
    }
    if support.try_umount {
        parts.push("tryUmount");
    }
    if support.open_redirect {
        parts.push("redirect");
    }
    if support.static_kstat || support.dynamic_kstat {
        parts.push("kstat");
    }
    if support.set_uname {
        parts.push("uname");
    }
    if parts.is_empty() {
        localized_text(
            strings,
            "没有报告可写的 SUSFS 功能位。",
            "No writable SUSFS capabilities were reported.",
        )
        .to_string()
    } else {
        format!(
            "{}: {}",
            localized_text(strings, "支持项", "Supported"),
            parts.join(", ")
        )
    }
}

fn append_switch_row<F>(
    card: &gtk::Box,
    label_text: &str,
    active: bool,
    sensitive: bool,
    on_change: F,
) where
    F: Fn(bool) + 'static,
{
    let row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    let label = gtk::Label::new(Some(label_text));
    label.set_xalign(0.0);
    label.set_hexpand(true);
    let switch = gtk::Switch::new();
    switch.set_active(active);
    switch.set_sensitive(sensitive);
    switch.connect_active_notify(move |widget| on_change(widget.is_active()));
    row.append(&label);
    row.append(&switch);
    card.append(&row);
}

fn append_entry_row<F>(
    card: &gtk::Box,
    label_text: &str,
    value: &str,
    sensitive: bool,
    on_change: F,
) where
    F: Fn(String) + 'static,
{
    let row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    let label = gtk::Label::new(Some(label_text));
    label.set_xalign(0.0);
    label.set_hexpand(true);
    let entry = gtk::Entry::new();
    entry.set_hexpand(true);
    entry.set_text(value);
    entry.set_sensitive(sensitive);
    entry.add_css_class("material-entry");
    entry.connect_changed(move |widget| on_change(widget.text().to_string()));
    row.append(&label);
    row.append(&entry);
    card.append(&row);
}

fn append_combo_row<F>(
    card: &gtk::Box,
    label_text: &str,
    options: &[(&str, &str)],
    active_id: &str,
    sensitive: bool,
    on_change: F,
) where
    F: Fn(String) + 'static,
{
    let row = gtk::Box::new(gtk::Orientation::Horizontal, 10);
    let label = gtk::Label::new(Some(label_text));
    label.set_xalign(0.0);
    label.set_hexpand(true);
    let combo = gtk::ComboBoxText::new();
    combo.set_sensitive(sensitive);
    for (id, text) in options {
        combo.append(Some(id), text);
    }
    combo.set_active_id(Some(active_id));
    combo.connect_changed(move |widget| {
        if let Some(id) = widget.active_id() {
            on_change(id.to_string());
        }
    });
    row.append(&label);
    row.append(&combo);
    card.append(&row);
}

fn append_path_rules_editor(
    editor_host: &gtk::Box,
    state: &Rc<RefCell<SusfsPageState>>,
    status_label: &gtk::Label,
    support_label: &gtk::Label,
    title: &str,
    body: &str,
    field_id: &'static str,
    sensitive: bool,
    strings: Strings,
) {
    let card = surface_card(title, body);
    let add = gtk::Button::with_label(localized_text(strings, "添加条目", "Add Entry"));
    add.add_css_class("pill");
    add.set_sensitive(sensitive);
    {
        let state = state.clone();
        let status_label = status_label.clone();
        let support_label = support_label.clone();
        let editor_host = editor_host.clone();
        add.connect_clicked(move |_| {
            with_susfs_path_rules_mut(&state, field_id, |rules| {
                rules.push(SusfsPathRuleModel::default());
            });
            render_susfs_page(&state, &status_label, &support_label, &editor_host, strings);
        });
    }
    card.append(&add);

    let rules = {
        let guard = state.borrow();
        match field_id {
            "loop_path_rules" => guard.config.loop_path_rules.clone(),
            _ => guard.config.path_rules.clone(),
        }
    };
    if rules.is_empty() {
        let placeholder = gtk::Label::new(Some(localized_text(
            strings,
            "当前没有条目。",
            "No entries configured.",
        )));
        placeholder.set_xalign(0.0);
        placeholder.add_css_class("list-row-subtitle");
        card.append(&placeholder);
    } else {
        for (index, rule) in rules.into_iter().enumerate() {
            let row = gtk::Box::new(gtk::Orientation::Horizontal, 8);
            let path = gtk::Entry::new();
            path.set_hexpand(true);
            path.set_text(&rule.path);
            path.set_sensitive(sensitive);
            path.add_css_class("material-entry");
            {
                let state = state.clone();
                path.connect_changed(move |widget| {
                    with_susfs_path_rules_mut(&state, field_id, |rules| {
                        if let Some(item) = rules.get_mut(index) {
                            item.path = widget.text().to_string();
                        }
                    });
                });
            }
            let max = gtk::Entry::new();
            max.set_width_chars(8);
            max.set_text(
                &rule
                    .max_tries
                    .map(|value| value.to_string())
                    .unwrap_or_default(),
            );
            max.set_sensitive(sensitive);
            max.add_css_class("material-entry");
            {
                let state = state.clone();
                max.connect_changed(move |widget| {
                    with_susfs_path_rules_mut(&state, field_id, |rules| {
                        if let Some(item) = rules.get_mut(index) {
                            let value = widget.text().trim().to_string();
                            item.max_tries = if value.is_empty() {
                                None
                            } else {
                                value.parse::<i64>().ok()
                            };
                        }
                    });
                });
            }
            let remove = gtk::Button::with_label(localized_text(strings, "删除", "Remove"));
            remove.add_css_class("pill");
            remove.set_sensitive(sensitive);
            {
                let state = state.clone();
                let status_label = status_label.clone();
                let support_label = support_label.clone();
                let editor_host = editor_host.clone();
                remove.connect_clicked(move |_| {
                    with_susfs_path_rules_mut(&state, field_id, |rules| {
                        if index < rules.len() {
                            rules.remove(index);
                        }
                    });
                    render_susfs_page(&state, &status_label, &support_label, &editor_host, strings);
                });
            }
            row.append(&path);
            row.append(&max);
            row.append(&remove);
            card.append(&row);
        }
    }
    editor_host.append(&card);
}

fn append_string_list_editor(
    editor_host: &gtk::Box,
    state: &Rc<RefCell<SusfsPageState>>,
    title: &str,
    body: &str,
    field_id: &'static str,
    sensitive: bool,
    strings: Strings,
) {
    let card = surface_card(title, body);
    let buffer = new_text_buffer();
    buffer.set_text(&susfs_string_list_text(state, field_id));
    buffer.connect_changed({
        let state = state.clone();
        move |buffer| update_susfs_string_list(state.clone(), field_id, buffer_text(buffer))
    });
    let view = new_text_view(&buffer, true);
    view.set_sensitive(sensitive);
    view.add_css_class("console-pane");
    let scroll = new_scroller(&view);
    scroll.set_min_content_height(120);
    card.append(&scroll);
    card.append(&gtk::Label::new(Some(localized_text(
        strings,
        "每行一个条目，空行会被忽略。",
        "One item per line. Blank lines are ignored.",
    ))));
    editor_host.append(&card);
}

fn append_open_redirects_editor(
    editor_host: &gtk::Box,
    state: &Rc<RefCell<SusfsPageState>>,
    status_label: &gtk::Label,
    support_label: &gtk::Label,
    sensitive: bool,
    strings: Strings,
) {
    let card = surface_card(
        localized_text(strings, "Open Redirects", "Open Redirects"),
        localized_text(
            strings,
            "每条规则包含 original、redirected、stage 和可选 uid_scheme。",
            "Each rule carries original, redirected, stage, and optional uid_scheme.",
        ),
    );
    let add = gtk::Button::with_label(localized_text(strings, "添加条目", "Add Entry"));
    add.add_css_class("pill");
    add.set_sensitive(sensitive);
    {
        let state = state.clone();
        let status_label = status_label.clone();
        let support_label = support_label.clone();
        let editor_host = editor_host.clone();
        add.connect_clicked(move |_| {
            state
                .borrow_mut()
                .config
                .open_redirects
                .push(SusfsOpenRedirectRuleModel::default());
            render_susfs_page(&state, &status_label, &support_label, &editor_host, strings);
        });
    }
    card.append(&add);
    let items = state.borrow().config.open_redirects.clone();
    if items.is_empty() {
        append_label_to_card(
            &card,
            localized_text(strings, "当前没有条目。", "No entries configured."),
        );
    } else {
        for (index, item) in items.into_iter().enumerate() {
            let row = gtk::Box::new(gtk::Orientation::Vertical, 8);
            let original = gtk::Entry::new();
            original.set_text(&item.original_path);
            original.set_sensitive(sensitive);
            original.add_css_class("material-entry");
            {
                let state = state.clone();
                original.connect_changed(move |widget| {
                    if let Some(rule) = state.borrow_mut().config.open_redirects.get_mut(index) {
                        rule.original_path = widget.text().to_string();
                    }
                });
            }
            row.append(&labeled_inline_widget(
                localized_text(strings, "Original", "Original"),
                &original,
            ));
            let redirected = gtk::Entry::new();
            redirected.set_text(&item.redirected_path);
            redirected.set_sensitive(sensitive);
            redirected.add_css_class("material-entry");
            {
                let state = state.clone();
                redirected.connect_changed(move |widget| {
                    if let Some(rule) = state.borrow_mut().config.open_redirects.get_mut(index) {
                        rule.redirected_path = widget.text().to_string();
                    }
                });
            }
            row.append(&labeled_inline_widget(
                localized_text(strings, "Redirected", "Redirected"),
                &redirected,
            ));
            let stage = gtk::ComboBoxText::new();
            stage.append(Some("boot_completed"), "boot_completed");
            stage.append(Some("service"), "service");
            stage.set_active_id(Some(&item.stage));
            stage.set_sensitive(sensitive);
            {
                let state = state.clone();
                stage.connect_changed(move |widget| {
                    if let Some(rule) = state.borrow_mut().config.open_redirects.get_mut(index) {
                        if let Some(id) = widget.active_id() {
                            rule.stage = id.to_string();
                        }
                    }
                });
            }
            row.append(&labeled_inline_widget(
                localized_text(strings, "Stage", "Stage"),
                &stage,
            ));
            let uid = gtk::Entry::new();
            uid.set_text(
                &item
                    .uid_scheme
                    .map(|value| value.to_string())
                    .unwrap_or_default(),
            );
            uid.set_sensitive(sensitive);
            uid.add_css_class("material-entry");
            {
                let state = state.clone();
                uid.connect_changed(move |widget| {
                    if let Some(rule) = state.borrow_mut().config.open_redirects.get_mut(index) {
                        let value = widget.text().trim().to_string();
                        rule.uid_scheme = if value.is_empty() {
                            None
                        } else {
                            value.parse::<i64>().ok()
                        };
                    }
                });
            }
            row.append(&labeled_inline_widget(
                localized_text(strings, "uid_scheme", "uid_scheme"),
                &uid,
            ));
            let remove = gtk::Button::with_label(localized_text(strings, "删除", "Remove"));
            remove.add_css_class("pill");
            remove.set_sensitive(sensitive);
            {
                let state = state.clone();
                let status_label = status_label.clone();
                let support_label = support_label.clone();
                let editor_host = editor_host.clone();
                remove.connect_clicked(move |_| {
                    if index < state.borrow().config.open_redirects.len() {
                        state.borrow_mut().config.open_redirects.remove(index);
                    }
                    render_susfs_page(&state, &status_label, &support_label, &editor_host, strings);
                });
            }
            row.append(&remove);
            card.append(&row);
        }
    }
    editor_host.append(&card);
}

fn append_kstat_editor(
    editor_host: &gtk::Box,
    state: &Rc<RefCell<SusfsPageState>>,
    status_label: &gtk::Label,
    support_label: &gtk::Label,
    sensitive: bool,
    strings: Strings,
) {
    let card = surface_card(
        localized_text(strings, "Kstat Entries", "Kstat Entries"),
        localized_text(
            strings,
            "每个条目都保留 path 与各个 stat 字段的结构化输入。",
            "Each entry keeps structured inputs for path plus all stat fields.",
        ),
    );
    let add = gtk::Button::with_label(localized_text(strings, "添加条目", "Add Entry"));
    add.add_css_class("pill");
    add.set_sensitive(sensitive);
    {
        let state = state.clone();
        let status_label = status_label.clone();
        let support_label = support_label.clone();
        let editor_host = editor_host.clone();
        add.connect_clicked(move |_| {
            state
                .borrow_mut()
                .config
                .kstat_entries
                .push(SusfsKstatEntryModel::default());
            render_susfs_page(&state, &status_label, &support_label, &editor_host, strings);
        });
    }
    card.append(&add);
    let items = state.borrow().config.kstat_entries.clone();
    if items.is_empty() {
        append_label_to_card(
            &card,
            localized_text(strings, "当前没有条目。", "No entries configured."),
        );
    } else {
        for (index, item) in items.into_iter().enumerate() {
            let section = gtk::Box::new(gtk::Orientation::Vertical, 8);
            let path = gtk::Entry::new();
            path.set_text(&item.path);
            path.set_sensitive(sensitive);
            path.add_css_class("material-entry");
            {
                let state = state.clone();
                path.connect_changed(move |widget| {
                    if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index) {
                        entry.path = widget.text().to_string();
                    }
                });
            }
            section.append(&labeled_inline_widget(
                localized_text(strings, "Path", "Path"),
                &path,
            ));
            let grid = gtk::Grid::new();
            grid.set_column_spacing(10);
            grid.set_row_spacing(10);
            append_kstat_field(
                &grid,
                0,
                0,
                localized_text(strings, "ino", "ino"),
                &item.ino,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.ino = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                1,
                0,
                localized_text(strings, "dev", "dev"),
                &item.dev,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.dev = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                0,
                1,
                localized_text(strings, "nlink", "nlink"),
                &item.nlink,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.nlink = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                1,
                1,
                localized_text(strings, "size", "size"),
                &item.size,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.size = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                0,
                2,
                localized_text(strings, "atime", "atime"),
                &item.atime,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.atime = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                1,
                2,
                localized_text(strings, "atime_nsec", "atime_nsec"),
                &item.atime_nsec,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.atime_nsec = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                0,
                3,
                localized_text(strings, "mtime", "mtime"),
                &item.mtime,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.mtime = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                1,
                3,
                localized_text(strings, "mtime_nsec", "mtime_nsec"),
                &item.mtime_nsec,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.mtime_nsec = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                0,
                4,
                localized_text(strings, "ctime", "ctime"),
                &item.ctime,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.ctime = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                1,
                4,
                localized_text(strings, "ctime_nsec", "ctime_nsec"),
                &item.ctime_nsec,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.ctime_nsec = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                0,
                5,
                localized_text(strings, "blocks", "blocks"),
                &item.blocks,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.blocks = value;
                        }
                    }
                },
            );
            append_kstat_field(
                &grid,
                1,
                5,
                localized_text(strings, "blksize", "blksize"),
                &item.blksize,
                sensitive,
                {
                    let state = state.clone();
                    move |value| {
                        if let Some(entry) = state.borrow_mut().config.kstat_entries.get_mut(index)
                        {
                            entry.blksize = value;
                        }
                    }
                },
            );
            section.append(&grid);
            let remove = gtk::Button::with_label(localized_text(strings, "删除", "Remove"));
            remove.add_css_class("pill");
            remove.set_sensitive(sensitive);
            {
                let state = state.clone();
                let status_label = status_label.clone();
                let support_label = support_label.clone();
                let editor_host = editor_host.clone();
                remove.connect_clicked(move |_| {
                    if index < state.borrow().config.kstat_entries.len() {
                        state.borrow_mut().config.kstat_entries.remove(index);
                    }
                    render_susfs_page(&state, &status_label, &support_label, &editor_host, strings);
                });
            }
            section.append(&remove);
            card.append(&section);
        }
    }
    editor_host.append(&card);
}

fn append_kstat_field<F>(
    grid: &gtk::Grid,
    column: i32,
    row: i32,
    label_text: &str,
    value: &str,
    sensitive: bool,
    on_change: F,
) where
    F: Fn(String) + 'static,
{
    let cell = gtk::Box::new(gtk::Orientation::Vertical, 4);
    let label = gtk::Label::new(Some(label_text));
    label.set_xalign(0.0);
    let entry = gtk::Entry::new();
    entry.set_text(value);
    entry.set_sensitive(sensitive);
    entry.add_css_class("material-entry");
    entry.connect_changed(move |widget| on_change(widget.text().to_string()));
    cell.append(&label);
    cell.append(&entry);
    grid.attach(&cell, column, row, 1, 1);
}

fn labeled_inline_widget(label_text: &str, widget: &impl IsA<gtk::Widget>) -> gtk::Box {
    let row = gtk::Box::new(gtk::Orientation::Vertical, 4);
    let label = gtk::Label::new(Some(label_text));
    label.set_xalign(0.0);
    row.append(&label);
    row.append(widget);
    row
}

fn append_label_to_card(card: &gtk::Box, text: &str) {
    let label = gtk::Label::new(Some(text));
    label.set_xalign(0.0);
    label.set_wrap(true);
    label.add_css_class("list-row-subtitle");
    card.append(&label);
}

fn with_susfs_path_rules_mut<F>(state: &Rc<RefCell<SusfsPageState>>, field_id: &str, update: F)
where
    F: FnOnce(&mut Vec<SusfsPathRuleModel>),
{
    let mut guard = state.borrow_mut();
    match field_id {
        "loop_path_rules" => update(&mut guard.config.loop_path_rules),
        _ => update(&mut guard.config.path_rules),
    }
}

fn susfs_string_list_text(state: &Rc<RefCell<SusfsPageState>>, field_id: &str) -> String {
    let guard = state.borrow();
    let values = match field_id {
        "mounts" => &guard.config.mounts,
        "try_umounts" => &guard.config.try_umounts,
        "legit_mounts" => &guard.config.legit_mounts,
        _ => &guard.config.maps,
    };
    values.join("\n")
}

fn update_susfs_string_list(state: Rc<RefCell<SusfsPageState>>, field_id: &str, raw: String) {
    let values = raw
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(ToString::to_string)
        .collect::<Vec<_>>();
    let mut guard = state.borrow_mut();
    match field_id {
        "mounts" => guard.config.mounts = values,
        "try_umounts" => guard.config.try_umounts = values,
        "legit_mounts" => guard.config.legit_mounts = values,
        _ => guard.config.maps = values,
    }
}

trait SusfsSnapshotText {
    fn presets_hide_custom_rom_level_text(&self) -> String;
    fn emulate_vold_mode_text(&self) -> String;
}

impl SusfsSnapshotText for SusfsPageState {
    fn presets_hide_custom_rom_level_text(&self) -> String {
        self.config.presets.hide_custom_rom_level.to_string()
    }

    fn emulate_vold_mode_text(&self) -> String {
        self.config.presets.emulate_vold_app_data_mode.to_string()
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
    let cached = {
        let guard = state.borrow();
        guard.icon_cache.get(package_name).cloned()
    };
    if let Some(bytes) = cached {
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

fn module_icon_widget(module: &RuntimeModuleEntry) -> gtk::Image {
    let icon_name = if module.source.contains("abk") {
        "applications-system-symbolic"
    } else if module.list_kind == ModuleListKind::Extension {
        "application-x-addon-symbolic"
    } else if module.source.contains("ksud") {
        "extension-symbolic"
    } else if module.source.contains("kpm") {
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

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn filtered_root_entries_match_package_and_sort_granted_first() {
        let state = RootGrantPageState {
            entries: vec![
                RootGrantEntry {
                    package_name: "com.example.beta".into(),
                    label: "Beta".into(),
                    is_system_app: false,
                    allow_su: false,
                    raw: Value::Null,
                },
                RootGrantEntry {
                    package_name: "com.example.alpha".into(),
                    label: "Alpha".into(),
                    is_system_app: false,
                    allow_su: true,
                    raw: Value::Null,
                },
            ],
            search_query: "example".into(),
            show_system_apps: false,
            selected_package: None,
            icon_cache: HashMap::new(),
            icon_inflight: HashSet::new(),
        };

        let filtered = filtered_root_entries(&state);
        assert_eq!(filtered.len(), 2);
        assert_eq!(filtered[0].package_name, "com.example.alpha");
        assert_eq!(filtered[1].package_name, "com.example.beta");
    }

    #[test]
    fn update_root_grant_state_preserves_existing_order() {
        let state = Rc::new(RefCell::new(RootGrantPageState {
            entries: vec![
                RootGrantEntry {
                    package_name: "b.pkg".into(),
                    label: "Beta".into(),
                    is_system_app: false,
                    allow_su: false,
                    raw: json!({"packageName":"b.pkg","label":"Beta","profile":{"allowSu":false}}),
                },
                RootGrantEntry {
                    package_name: "a.pkg".into(),
                    label: "Alpha".into(),
                    is_system_app: false,
                    allow_su: true,
                    raw: json!({"packageName":"a.pkg","label":"Alpha","profile":{"allowSu":true}}),
                },
            ],
            ..RootGrantPageState::default()
        }));

        update_root_grant_state(
            &state,
            &json!({
                "apps": [
                    {"packageName":"a.pkg","label":"Alpha","profile":{"allowSu":false}},
                    {"packageName":"b.pkg","label":"Beta","profile":{"allowSu":true}}
                ]
            }),
        );

        let guard = state.borrow();
        assert_eq!(guard.entries[0].package_name, "b.pkg");
        assert_eq!(guard.entries[1].package_name, "a.pkg");
    }

    #[test]
    fn module_state_aggregates_groups_and_extensions() {
        let state = Rc::new(RefCell::new(ModulePageState::default()));
        update_module_page_state(
            &state,
            &json!({
                "runtimeStatus": {
                    "modules": [
                        {
                            "id": "core-a",
                            "name": "Core A",
                            "source": "abk",
                            "enabled": true,
                            "controllable": true,
                            "group_id": "set-1",
                            "group_name": "Suite",
                            "group_role": "primary"
                        }
                    ],
                    "extension_modules": [
                        {
                            "id": "ext-b",
                            "name": "Ext B",
                            "source": "ksud",
                            "enabled": false,
                            "controllable": true,
                            "has_web_ui": true,
                            "group_id": "set-1",
                            "group_name": "Suite",
                            "group_role": "addon"
                        }
                    ]
                }
            }),
        );

        let guard = state.borrow();
        assert_eq!(guard.modules.len(), 1);
        assert_eq!(guard.extension_modules.len(), 1);
        assert_eq!(guard.groups.len(), 1);
        assert_eq!(guard.groups[0].members.len(), 2);
        assert_eq!(
            select_group_webui_target(&guard.groups[0]).unwrap().id,
            "ext-b"
        );
    }

    #[test]
    fn susfs_apply_body_preserves_unknown_fields() {
        let state = Rc::new(RefCell::new(SusfsPageState {
            raw_config: json!({
                "schemaVersion": 1,
                "customField": {"keep": true},
                "presets": {"hideLoops": true, "customPresetField": 7}
            }),
            config: SusfsConfigModel {
                uname_value: "new-kernel".into(),
                ..SusfsConfigModel::default()
            },
            ..SusfsPageState::default()
        }));

        let body = build_susfs_apply_body(&state).unwrap();
        let parsed: Value = serde_json::from_str(&body).unwrap();

        assert_eq!(parsed["unameValue"], "new-kernel");
        assert_eq!(parsed["customField"]["keep"], true);
        assert_eq!(parsed["presets"]["customPresetField"], 7);
    }
}
