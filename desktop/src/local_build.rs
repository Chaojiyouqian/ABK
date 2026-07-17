use crate::commands::{
    build_local_init_command, build_local_rebuild_command, repo_root, wrap_command_with_sudo,
    CommandSpec,
};
use crate::local_build_paths::{
    ensure_local_build_root_materialized, load_local_build_path_settings,
    normalize_optional_dir_setting, persist_local_build_path_settings,
    resolve_local_build_profile_store_dir, resolve_local_build_root,
    resolve_local_build_workspace_dir, LocalBuildPathSettings,
};
use crate::proxy::ProxySettings;
use crate::{inspect_local_build_status, BuildGkiRequest, LocalBuildStatus};
use anyhow::{anyhow, Context, Result};
#[cfg(unix)]
use libc::{getgid, getuid};
use serde::{Deserialize, Serialize};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use uuid::Uuid;

const STORE_SCHEMA_VERSION: u32 = 1;
const DEFAULT_CONTAINER_IMAGE: &str = "ghcr.io/xingguangcuican6666/abk:latest";
const DEFAULT_WSL_ROOTFS_TAR_URL: &str =
    "https://github.com/xingguangcuican6666/ABK/releases/download/v1.0.0-wsl/wsl-ubuntu-abk.tar";
const CONTAINER_HOME_MOUNT_TARGET: &str = "/tmp/abk-home";
const DOCKER_CONTAINER_HOST_ALIAS: &str = "host.docker.internal";
const DOCKER_CONTAINER_HOST_MAP: &str = "host.docker.internal:host-gateway";
const PODMAN_CONTAINER_HOST_ALIAS: &str = "host.containers.internal";

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq, Hash)]
#[serde(rename_all = "snake_case")]
pub enum LocalBuildBackendKind {
    Docker,
    Podman,
    Wsl,
    Script,
}

impl LocalBuildBackendKind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Docker => "docker",
            Self::Podman => "podman",
            Self::Wsl => "wsl",
            Self::Script => "script",
        }
    }

    pub fn display_name(self) -> &'static str {
        match self {
            Self::Docker => "Docker",
            Self::Podman => "Podman",
            Self::Wsl => "WSL",
            Self::Script => "Script adapter",
        }
    }

    pub fn family(self) -> &'static str {
        match self {
            Self::Docker | Self::Podman => "container",
            Self::Wsl => "wsl",
            Self::Script => "script",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildBackendCapabilities {
    pub family: String,
    pub host_owned_paths: bool,
    pub supports_source_sync: bool,
    pub supports_build_execution: bool,
    pub supports_profile_projection: bool,
    pub notes: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildBackendDescriptor {
    pub kind: LocalBuildBackendKind,
    pub label: String,
    pub available: bool,
    pub is_global_default: bool,
    pub authorization_required: bool,
    pub authorization_kind: Option<String>,
    pub authorization_message: Option<String>,
    pub capabilities: LocalBuildBackendCapabilities,
    pub detail: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SupportedKernelLine {
    pub id: String,
    pub android_version: String,
    pub kernel_version: String,
    pub display_name: String,
    pub branch_month_format: String,
    pub script_template_path: String,
    pub script_template_available: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildCatalogResponse {
    pub kernel_lines: Vec<SupportedKernelLine>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildBackendsResponse {
    pub global_default_backend_kind: LocalBuildBackendKind,
    pub backends: Vec<LocalBuildBackendDescriptor>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildSettings {
    pub global_default_backend_kind: LocalBuildBackendKind,
    pub active_source_instance_id: Option<String>,
    pub script_root_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub profile_store_dir: Option<String>,
}

impl Default for LocalBuildSettings {
    fn default() -> Self {
        Self {
            global_default_backend_kind: LocalBuildBackendKind::Script,
            active_source_instance_id: None,
            script_root_dir: None,
            workspace_dir: None,
            profile_store_dir: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildMaterializedState {
    pub script_root: Option<String>,
    pub env_file_path: Option<String>,
    pub state_dir: Option<String>,
    pub sources_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub artifacts_dir: Option<String>,
    pub logs_dir: Option<String>,
    pub cache_dir: Option<String>,
    pub kernel_root: Option<String>,
    pub template_name: Option<String>,
    pub template_root: Option<String>,
    pub template_branch: Option<String>,
    pub template_common_branch: Option<String>,
    pub sub_level: Option<String>,
    pub os_patch_level: Option<String>,
    pub latest_log_path: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildSourceInstance {
    pub id: String,
    pub display_name: String,
    pub kernel_line_id: String,
    pub android_version: String,
    pub kernel_version: String,
    pub branch_month: String,
    pub cache_root: String,
    pub working_tree_root: String,
    pub state: String,
    pub created_at_ms: u64,
    pub updated_at_ms: u64,
    pub last_synced_at_ms: Option<u64>,
    pub active_backend_kind: Option<LocalBuildBackendKind>,
    pub last_task_id: Option<String>,
    pub last_error: Option<String>,
    pub materialized: Option<LocalBuildMaterializedState>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildProfile {
    pub id: String,
    pub name: String,
    pub source_instance_id: String,
    pub backend_kind: Option<LocalBuildBackendKind>,
    pub build: BuildGkiRequest,
    pub created_at_ms: u64,
    pub updated_at_ms: u64,
    pub last_built_at_ms: Option<u64>,
    pub last_task_id: Option<String>,
    pub last_error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildProfilesResponse {
    pub settings: LocalBuildSettings,
    pub profiles: Vec<LocalBuildProfile>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildSourceInstancesResponse {
    pub settings: LocalBuildSettings,
    pub source_instances: Vec<LocalBuildSourceInstance>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildArtifactEntry {
    pub id: String,
    pub task_id: String,
    pub profile_id: Option<String>,
    pub source_instance_id: String,
    pub backend_kind: LocalBuildBackendKind,
    pub path: String,
    pub file_name: String,
    pub exists: bool,
    pub created_at_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildArtifactsResponse {
    pub artifacts: Vec<LocalBuildArtifactEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildLogEntry {
    pub id: String,
    pub task_id: String,
    pub profile_id: Option<String>,
    pub source_instance_id: String,
    pub backend_kind: LocalBuildBackendKind,
    pub path: String,
    pub file_name: String,
    pub exists: bool,
    pub created_at_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalBuildLogsResponse {
    pub logs: Vec<LocalBuildLogEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateLocalBuildSourceInstanceRequest {
    pub kernel_line_id: String,
    pub branch_month: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct SyncLocalBuildSourceInstanceRequest {
    pub backend_kind: Option<LocalBuildBackendKind>,
    pub force: Option<bool>,
    pub skip_deps: Option<bool>,
    pub sudo_password: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveLocalBuildProfileRequest {
    pub id: Option<String>,
    pub name: Option<String>,
    pub source_instance_id: String,
    pub backend_kind: Option<LocalBuildBackendKind>,
    pub build: Option<BuildGkiRequest>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct BuildLocalBuildProfileRequest {
    pub clean_out: Option<bool>,
    pub reseed: Option<bool>,
    pub no_package: Option<bool>,
    pub sudo_password: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateLocalBuildSettingsRequest {
    pub global_default_backend_kind: Option<LocalBuildBackendKind>,
    pub script_root_dir: Option<String>,
    pub workspace_dir: Option<String>,
    pub profile_store_dir: Option<String>,
}

#[derive(Debug, Clone)]
pub struct LocalBuildSourceSyncPlan {
    pub source_instance: LocalBuildSourceInstance,
    pub backend_kind: LocalBuildBackendKind,
    pub command: CommandSpec,
}

#[derive(Debug, Clone)]
pub struct LocalBuildProfileBuildPlan {
    pub profile: LocalBuildProfile,
    pub source_instance: LocalBuildSourceInstance,
    pub backend_kind: LocalBuildBackendKind,
    pub activation_command: Option<CommandSpec>,
    pub build_command: CommandSpec,
    pub build_request: BuildGkiRequest,
}

#[derive(Debug, Serialize, Deserialize)]
struct LocalBuildStore {
    schema_version: u32,
    settings: LocalBuildSettings,
    source_instances: Vec<LocalBuildSourceInstance>,
    profiles: Vec<LocalBuildProfile>,
    artifacts: Vec<LocalBuildArtifactEntry>,
    logs: Vec<LocalBuildLogEntry>,
}

#[derive(Debug, Clone)]
struct BackendProbe {
    available: bool,
    detail: Option<String>,
    authorization_required: bool,
    authorization_kind: Option<String>,
    authorization_message: Option<String>,
}

impl Default for LocalBuildStore {
    fn default() -> Self {
        Self {
            schema_version: STORE_SCHEMA_VERSION,
            settings: LocalBuildSettings::default(),
            source_instances: Vec::new(),
            profiles: Vec::new(),
            artifacts: Vec::new(),
            logs: Vec::new(),
        }
    }
}

#[derive(Debug)]
pub struct LocalBuildManager {
    repo_root: PathBuf,
    config_path: PathBuf,
    path_settings: LocalBuildPathSettings,
    data_root: PathBuf,
    store_path: PathBuf,
    store: LocalBuildStore,
}

impl LocalBuildManager {
    pub fn new(repo_root: PathBuf) -> Result<Self> {
        let path_settings = load_local_build_path_settings(&repo_root)?;
        let _ = ensure_local_build_root_materialized(&repo_root, &path_settings)?;
        let config_path = crate::local_build_paths::local_build_config_path(&repo_root);
        let data_root = resolve_local_build_profile_store_dir(&repo_root, &path_settings);
        fs::create_dir_all(&data_root)
            .with_context(|| format!("failed to create {}", data_root.display()))?;
        let store_path = data_root.join("state.json");
        let store = load_store(&store_path)?;
        let mut manager = Self {
            repo_root,
            config_path,
            path_settings,
            data_root,
            store_path,
            store,
        };
        if !manager
            .collect_backend_descriptors()
            .iter()
            .any(|backend| backend.kind == manager.store.settings.global_default_backend_kind)
        {
            manager.store.settings.global_default_backend_kind = manager.default_backend_kind();
            manager.persist()?;
        }
        Ok(manager)
    }

    fn export_settings(&self) -> LocalBuildSettings {
        LocalBuildSettings {
            global_default_backend_kind: self.store.settings.global_default_backend_kind,
            active_source_instance_id: self.store.settings.active_source_instance_id.clone(),
            script_root_dir: self.path_settings.script_root_dir.clone(),
            workspace_dir: self.path_settings.workspace_dir.clone(),
            profile_store_dir: self.path_settings.profile_store_dir.clone(),
        }
    }

    fn script_root(&self) -> PathBuf {
        resolve_local_build_root(&self.repo_root, &self.path_settings)
    }

    fn workspace_dir(&self) -> PathBuf {
        resolve_local_build_workspace_dir(&self.script_root(), &self.path_settings)
    }

    fn refresh_source_instance_roots(&mut self) {
        for source in &mut self.store.source_instances {
            source.cache_root = self
                .data_root
                .join("sources")
                .join(&source.id)
                .to_string_lossy()
                .to_string();
            source.working_tree_root = self
                .data_root
                .join("working-trees")
                .join(&source.id)
                .to_string_lossy()
                .to_string();
        }
    }

    pub fn list_backends(&self) -> LocalBuildBackendsResponse {
        LocalBuildBackendsResponse {
            global_default_backend_kind: self.store.settings.global_default_backend_kind,
            backends: self.collect_backend_descriptors(),
        }
    }

    pub fn catalog(&self) -> LocalBuildCatalogResponse {
        LocalBuildCatalogResponse {
            kernel_lines: supported_kernel_lines(&self.script_root()),
        }
    }

    pub fn update_settings(
        &mut self,
        request: UpdateLocalBuildSettingsRequest,
    ) -> Result<LocalBuildSettings> {
        if let Some(kind) = request.global_default_backend_kind {
            if !self
                .collect_backend_descriptors()
                .iter()
                .any(|backend| backend.kind == kind)
            {
                return Err(anyhow!("unsupported backend kind {}", kind.as_str()));
            }
            self.store.settings.global_default_backend_kind = kind;
        }

        let next_path_settings = LocalBuildPathSettings {
            script_root_dir: normalize_optional_dir_setting(request.script_root_dir)?,
            workspace_dir: normalize_optional_dir_setting(request.workspace_dir)?,
            profile_store_dir: normalize_optional_dir_setting(request.profile_store_dir)?,
        };
        let _ = ensure_local_build_root_materialized(&self.repo_root, &next_path_settings)?;
        let next_data_root =
            resolve_local_build_profile_store_dir(&self.repo_root, &next_path_settings);
        if next_data_root != self.data_root {
            fs::create_dir_all(&next_data_root)
                .with_context(|| format!("failed to create {}", next_data_root.display()))?;
            self.data_root = next_data_root;
            self.store_path = self.data_root.join("state.json");
            self.refresh_source_instance_roots();
        }
        self.path_settings = next_path_settings;
        persist_local_build_path_settings(&self.repo_root, &self.path_settings)
            .with_context(|| format!("failed to write {}", self.config_path.display()))?;
        self.persist()?;
        Ok(self.export_settings())
    }

    pub fn list_source_instances(&self) -> LocalBuildSourceInstancesResponse {
        let mut source_instances = self.store.source_instances.clone();
        source_instances.sort_by(|left, right| right.updated_at_ms.cmp(&left.updated_at_ms));
        LocalBuildSourceInstancesResponse {
            settings: self.export_settings(),
            source_instances,
        }
    }

    pub fn create_source_instance(
        &mut self,
        request: CreateLocalBuildSourceInstanceRequest,
    ) -> Result<LocalBuildSourceInstance> {
        let kernel_line = find_kernel_line(&self.script_root(), &request.kernel_line_id)?;
        let branch_month = normalize_branch_month(&request.branch_month)?;
        let id = source_instance_id(&kernel_line.id, &branch_month);
        if let Some(existing) = self
            .store
            .source_instances
            .iter()
            .find(|source| source.id == id)
            .cloned()
        {
            return Ok(existing);
        }
        let now = now_ms();
        let cache_root = self
            .data_root
            .join("sources")
            .join(&id)
            .to_string_lossy()
            .to_string();
        let working_tree_root = self
            .data_root
            .join("working-trees")
            .join(&id)
            .to_string_lossy()
            .to_string();
        let source_instance = LocalBuildSourceInstance {
            id: id.clone(),
            display_name: format!(
                "{}/{}@{}",
                kernel_line.android_version, kernel_line.kernel_version, branch_month
            ),
            kernel_line_id: kernel_line.id.clone(),
            android_version: kernel_line.android_version.clone(),
            kernel_version: kernel_line.kernel_version.clone(),
            branch_month,
            cache_root,
            working_tree_root,
            state: "draft".into(),
            created_at_ms: now,
            updated_at_ms: now,
            last_synced_at_ms: None,
            active_backend_kind: None,
            last_task_id: None,
            last_error: None,
            materialized: None,
        };
        self.store.source_instances.push(source_instance.clone());
        self.persist()?;
        Ok(source_instance)
    }

    pub fn plan_source_sync(
        &mut self,
        source_instance_id: &str,
        request: &SyncLocalBuildSourceInstanceRequest,
    ) -> Result<LocalBuildSourceSyncPlan> {
        let backend_kind = request
            .backend_kind
            .unwrap_or(self.store.settings.global_default_backend_kind);
        let backend = self.backend_descriptor(backend_kind);
        if !backend.available {
            return Err(anyhow!("{} is not available on this host", backend.label));
        }
        if !backend.capabilities.supports_source_sync {
            return Err(anyhow!(
                "{} source sync is not implemented yet",
                backend.label
            ));
        }
        let source_instance = self.require_source_instance(source_instance_id)?;
        let command = match backend_kind {
            LocalBuildBackendKind::Script => build_local_init_command(
                &source_instance.android_version,
                &source_instance.kernel_version,
                &source_instance.branch_month,
                request.force.unwrap_or(false),
                request.skip_deps.unwrap_or(false),
            )?,
            LocalBuildBackendKind::Docker => self.build_container_init_command(
                "docker",
                &source_instance,
                request.force.unwrap_or(false),
                request.skip_deps.unwrap_or(false),
                None,
            )?,
            LocalBuildBackendKind::Podman => self.build_container_init_command(
                "podman",
                &source_instance,
                request.force.unwrap_or(false),
                request.skip_deps.unwrap_or(false),
                None,
            )?,
            _ => {
                return Err(anyhow!(
                    "{} source sync is not implemented yet",
                    backend.label
                ))
            }
        };
        let command =
            authorize_command_if_needed(command, &backend, request.sudo_password.as_deref())?;
        {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.state = "syncing".into();
            source_instance.updated_at_ms = now_ms();
            source_instance.last_error = None;
        }
        self.persist()?;
        Ok(LocalBuildSourceSyncPlan {
            source_instance: self.require_source_instance(source_instance_id)?,
            backend_kind,
            command,
        })
    }

    pub fn finalize_source_sync(
        &mut self,
        source_instance_id: &str,
        task_id: &str,
        backend_kind: LocalBuildBackendKind,
    ) -> Result<LocalBuildSourceInstance> {
        let materialized = inspect_local_build_status()
            .ok()
            .map(materialized_state_from_legacy_status);
        let now = now_ms();
        let active_source_instance_id = {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.state = "ready".into();
            source_instance.updated_at_ms = now;
            source_instance.last_synced_at_ms = Some(now);
            source_instance.active_backend_kind = Some(backend_kind);
            source_instance.last_task_id = Some(task_id.to_string());
            source_instance.last_error = None;
            if let Some(materialized) = materialized {
                source_instance.materialized = Some(materialized);
            }
            source_instance.id.clone()
        };
        self.store.settings.active_source_instance_id = Some(active_source_instance_id);
        self.persist()?;
        self.require_source_instance(source_instance_id)
    }

    pub fn fail_source_sync(
        &mut self,
        source_instance_id: &str,
        task_id: &str,
        error: &str,
    ) -> Result<LocalBuildSourceInstance> {
        {
            let source_instance = self.require_source_instance_mut(source_instance_id)?;
            source_instance.state = "failed".into();
            source_instance.updated_at_ms = now_ms();
            source_instance.last_task_id = Some(task_id.to_string());
            source_instance.last_error = Some(error.trim().to_string());
        }
        self.persist()?;
        self.require_source_instance(source_instance_id)
    }

    pub fn list_profiles(&self) -> LocalBuildProfilesResponse {
        let mut profiles = self.store.profiles.clone();
        profiles.sort_by(|left, right| right.updated_at_ms.cmp(&left.updated_at_ms));
        LocalBuildProfilesResponse {
            settings: self.export_settings(),
            profiles,
        }
    }

    pub fn save_profile(
        &mut self,
        request: SaveLocalBuildProfileRequest,
    ) -> Result<LocalBuildProfile> {
        let source_instance = self.require_source_instance(&request.source_instance_id)?;
        let now = now_ms();
        let normalized_build = normalize_build_request(
            request
                .build
                .unwrap_or_else(|| default_build_request_for_source(&source_instance)),
            &source_instance,
        );
        if let Some(profile_id) = request.id.as_deref() {
            {
                let profile = self.require_profile_mut(profile_id)?;
                profile.name = request
                    .name
                    .unwrap_or_else(|| profile.name.clone())
                    .trim()
                    .to_string();
                profile.source_instance_id = source_instance.id.clone();
                profile.backend_kind = request.backend_kind;
                profile.build = normalized_build;
                profile.updated_at_ms = now;
                profile.last_error = None;
            }
            self.persist()?;
            return self.require_profile(profile_id);
        }

        let source_display = format!(
            "{}/{}@{}",
            source_instance.android_version,
            source_instance.kernel_version,
            source_instance.branch_month
        );
        let profile = LocalBuildProfile {
            id: Uuid::new_v4().to_string(),
            name: request
                .name
                .unwrap_or_else(|| format!("Profile {}", source_display))
                .trim()
                .to_string(),
            source_instance_id: source_instance.id.clone(),
            backend_kind: request.backend_kind,
            build: normalized_build,
            created_at_ms: now,
            updated_at_ms: now,
            last_built_at_ms: None,
            last_task_id: None,
            last_error: None,
        };
        self.store.profiles.push(profile.clone());
        self.persist()?;
        Ok(profile)
    }

    pub fn plan_profile_build(
        &mut self,
        profile_id: &str,
        request: &BuildLocalBuildProfileRequest,
    ) -> Result<LocalBuildProfileBuildPlan> {
        let profile = self.require_profile(profile_id)?;
        let source_instance = self.require_source_instance(&profile.source_instance_id)?;
        if source_instance.last_synced_at_ms.is_none() {
            return Err(anyhow!(
                "source instance {} has not been synced yet",
                source_instance.display_name
            ));
        }
        let backend_kind = profile
            .backend_kind
            .unwrap_or(self.store.settings.global_default_backend_kind);
        let backend = self.backend_descriptor(backend_kind);
        if !backend.available {
            return Err(anyhow!("{} is not available on this host", backend.label));
        }
        if !backend.capabilities.supports_build_execution {
            return Err(anyhow!(
                "{} build execution is not implemented yet",
                backend.label
            ));
        }
        let build_request = normalize_build_request(profile.build.clone(), &source_instance);
        let activation_command =
            match backend_kind {
                LocalBuildBackendKind::Script => {
                    let legacy_status = inspect_local_build_status().ok();
                    if legacy_status.as_ref().is_some_and(|status| {
                        legacy_status_matches_source(status, &source_instance)
                    }) {
                        None
                    } else {
                        Some(build_local_init_command(
                            &source_instance.android_version,
                            &source_instance.kernel_version,
                            &source_instance.branch_month,
                            false,
                            true,
                        )?)
                    }
                }
                LocalBuildBackendKind::Docker => {
                    let legacy_status = inspect_local_build_status().ok();
                    if legacy_status.as_ref().is_some_and(|status| {
                        legacy_status_matches_source(status, &source_instance)
                    }) {
                        None
                    } else {
                        Some(self.build_container_init_command(
                            "docker",
                            &source_instance,
                            false,
                            true,
                            Some(&build_request),
                        )?)
                    }
                }
                LocalBuildBackendKind::Podman => {
                    let legacy_status = inspect_local_build_status().ok();
                    if legacy_status.as_ref().is_some_and(|status| {
                        legacy_status_matches_source(status, &source_instance)
                    }) {
                        None
                    } else {
                        Some(self.build_container_init_command(
                            "podman",
                            &source_instance,
                            false,
                            true,
                            Some(&build_request),
                        )?)
                    }
                }
                _ => {
                    return Err(anyhow!(
                        "{} build execution is not implemented yet",
                        backend.label
                    ))
                }
            };
        let activation_command = activation_command
            .map(|command| {
                authorize_command_if_needed(command, &backend, request.sudo_password.as_deref())
            })
            .transpose()?;
        let build_command = match backend_kind {
            LocalBuildBackendKind::Script => build_local_rebuild_command(
                request.clean_out.unwrap_or(false),
                request.reseed.unwrap_or(false),
                request.no_package.unwrap_or(false),
            ),
            LocalBuildBackendKind::Docker => self.build_container_rebuild_command(
                "docker",
                request.clean_out.unwrap_or(false),
                request.reseed.unwrap_or(false),
                request.no_package.unwrap_or(false),
                &build_request,
            )?,
            LocalBuildBackendKind::Podman => self.build_container_rebuild_command(
                "podman",
                request.clean_out.unwrap_or(false),
                request.reseed.unwrap_or(false),
                request.no_package.unwrap_or(false),
                &build_request,
            )?,
            _ => {
                return Err(anyhow!(
                    "{} build execution is not implemented yet",
                    backend.label
                ))
            }
        };
        let build_command =
            authorize_command_if_needed(build_command, &backend, request.sudo_password.as_deref())?;
        {
            let profile_mut = self.require_profile_mut(profile_id)?;
            profile_mut.updated_at_ms = now_ms();
            profile_mut.last_error = None;
        }
        self.persist()?;
        Ok(LocalBuildProfileBuildPlan {
            profile,
            source_instance,
            backend_kind,
            activation_command,
            build_command,
            build_request,
        })
    }

    pub fn finalize_profile_build(
        &mut self,
        profile_id: &str,
        task_id: &str,
        backend_kind: LocalBuildBackendKind,
    ) -> Result<LocalBuildProfile> {
        let materialized = inspect_local_build_status()
            .ok()
            .map(materialized_state_from_legacy_status);
        let source_instance_id = {
            let profile = self.require_profile_mut(profile_id)?;
            let now = now_ms();
            profile.updated_at_ms = now;
            profile.last_built_at_ms = Some(now);
            profile.last_task_id = Some(task_id.to_string());
            profile.last_error = None;
            profile.source_instance_id.clone()
        };
        if let Some(materialized) = materialized.clone() {
            let captured_source_id = {
                let source_instance = self.require_source_instance_mut(&source_instance_id)?;
                source_instance.updated_at_ms = now_ms();
                source_instance.active_backend_kind = Some(backend_kind);
                source_instance.materialized = Some(materialized.clone());
                source_instance.last_task_id = Some(task_id.to_string());
                source_instance.last_error = None;
                source_instance.id.clone()
            };
            self.store.settings.active_source_instance_id = Some(captured_source_id.clone());
            self.capture_artifacts_and_logs(
                &captured_source_id,
                Some(profile_id),
                task_id,
                backend_kind,
                &materialized,
            );
        }
        self.persist()?;
        self.require_profile(profile_id)
    }

    pub fn fail_profile_build(
        &mut self,
        profile_id: &str,
        task_id: &str,
        error: &str,
    ) -> Result<LocalBuildProfile> {
        {
            let profile = self.require_profile_mut(profile_id)?;
            profile.updated_at_ms = now_ms();
            profile.last_task_id = Some(task_id.to_string());
            profile.last_error = Some(error.trim().to_string());
        }
        self.persist()?;
        self.require_profile(profile_id)
    }

    pub fn list_artifacts(&self) -> LocalBuildArtifactsResponse {
        let mut artifacts = self.store.artifacts.clone();
        artifacts.sort_by(|left, right| right.created_at_ms.cmp(&left.created_at_ms));
        LocalBuildArtifactsResponse { artifacts }
    }

    pub fn list_logs(&self) -> LocalBuildLogsResponse {
        let mut logs = self.store.logs.clone();
        logs.sort_by(|left, right| right.created_at_ms.cmp(&left.created_at_ms));
        LocalBuildLogsResponse { logs }
    }

    pub fn ensure_legacy_source_instance(
        &mut self,
        android_version: &str,
        kernel_version: &str,
        branch_month: &str,
    ) -> Result<LocalBuildSourceInstance> {
        let kernel_line_id = format!(
            "{}/{}",
            android_version.trim().to_lowercase(),
            kernel_version.trim()
        );
        self.create_source_instance(CreateLocalBuildSourceInstanceRequest {
            kernel_line_id,
            branch_month: branch_month.to_string(),
        })
    }

    pub fn ensure_legacy_profile(
        &mut self,
        source_instance_id: &str,
        build: Option<BuildGkiRequest>,
    ) -> Result<LocalBuildProfile> {
        if let Some(existing) = self
            .store
            .profiles
            .iter()
            .find(|profile| profile.id == "legacy-script-profile")
            .cloned()
        {
            let request = SaveLocalBuildProfileRequest {
                id: Some(existing.id),
                name: Some("Legacy script profile".into()),
                source_instance_id: source_instance_id.to_string(),
                backend_kind: Some(LocalBuildBackendKind::Script),
                build: build.or(Some(existing.build)),
            };
            return self.save_profile(request);
        }
        self.save_profile(SaveLocalBuildProfileRequest {
            id: Some("legacy-script-profile".into()),
            name: Some("Legacy script profile".into()),
            source_instance_id: source_instance_id.to_string(),
            backend_kind: Some(LocalBuildBackendKind::Script),
            build,
        })
    }

    fn collect_backend_descriptors(&self) -> Vec<LocalBuildBackendDescriptor> {
        let default_kind = self.store.settings.global_default_backend_kind;
        let backends = [
            backend_descriptor(
                LocalBuildBackendKind::Docker,
                inspect_container_backend(
                    "docker",
                    &container_image_for(LocalBuildBackendKind::Docker),
                ),
                true,
                true,
                vec![
                    "Host-owned source caches and artifacts.".into(),
                    format!(
                        "Container image: {}",
                        container_image_for(LocalBuildBackendKind::Docker)
                    ),
                    "Runs new_test scripts inside the container with host bind mounts.".into(),
                ],
            ),
            backend_descriptor(
                LocalBuildBackendKind::Podman,
                inspect_container_backend(
                    "podman",
                    &container_image_for(LocalBuildBackendKind::Podman),
                ),
                true,
                true,
                vec![
                    "Docker-compatible container backend.".into(),
                    format!(
                        "Container image: {}",
                        container_image_for(LocalBuildBackendKind::Podman)
                    ),
                    "Runs new_test scripts inside the container with host bind mounts.".into(),
                ],
            ),
            backend_descriptor(
                LocalBuildBackendKind::Wsl,
                inspect_wsl_backend(),
                true,
                false,
                vec![
                    "Windows-first backend for host-owned worktrees.".into(),
                    format!("Rootfs tar: {DEFAULT_WSL_ROOTFS_TAR_URL}"),
                    "Protocol reserved, execution not wired yet.".into(),
                ],
            ),
            backend_descriptor(
                LocalBuildBackendKind::Script,
                if script_backend_available(&self.script_root()) {
                    BackendProbe {
                        available: true,
                        detail: None,
                        authorization_required: false,
                        authorization_kind: None,
                        authorization_message: None,
                    }
                } else {
                    BackendProbe {
                        available: false,
                        detail: Some(
                            "init.sh or rebuild.sh is missing under the local build root.".into(),
                        ),
                        authorization_required: false,
                        authorization_kind: None,
                        authorization_message: None,
                    }
                },
                true,
                true,
                vec![
                    "Linux development adapter around new_test/init.sh and rebuild.sh.".into(),
                    "Only one active working tree is materialized at a time.".into(),
                ],
            ),
        ];
        backends
            .into_iter()
            .map(|mut descriptor| {
                descriptor.is_global_default = descriptor.kind == default_kind;
                descriptor
            })
            .collect()
    }

    fn backend_descriptor(&self, kind: LocalBuildBackendKind) -> LocalBuildBackendDescriptor {
        self.collect_backend_descriptors()
            .into_iter()
            .find(|backend| backend.kind == kind)
            .unwrap_or_else(|| {
                backend_descriptor(
                    kind,
                    BackendProbe {
                        available: false,
                        detail: None,
                        authorization_required: false,
                        authorization_kind: None,
                        authorization_message: None,
                    },
                    false,
                    false,
                    Vec::new(),
                )
            })
    }

    fn default_backend_kind(&self) -> LocalBuildBackendKind {
        self.collect_backend_descriptors()
            .into_iter()
            .find(|backend| backend.available && backend.capabilities.supports_build_execution)
            .map(|backend| backend.kind)
            .unwrap_or(LocalBuildBackendKind::Script)
    }

    fn persist(&self) -> Result<()> {
        let payload = serde_json::to_vec_pretty(&self.store)
            .context("failed to serialize local build store")?;
        fs::write(&self.store_path, payload)
            .with_context(|| format!("failed to write {}", self.store_path.display()))
    }

    fn build_container_init_command(
        &self,
        engine: &str,
        source_instance: &LocalBuildSourceInstance,
        force: bool,
        skip_deps: bool,
        build_request: Option<&BuildGkiRequest>,
    ) -> Result<CommandSpec> {
        let script_root = self.script_root();
        let script = script_root.join("init.sh");
        let mut script_args = vec![
            script.to_string_lossy().to_string(),
            "--android".into(),
            source_instance.android_version.clone(),
            "--kernel".into(),
            source_instance.kernel_version.clone(),
            "--branch-month".into(),
            source_instance.branch_month.clone(),
        ];
        if force {
            script_args.push("--force".into());
        }
        if skip_deps {
            script_args.push("--skip-deps".into());
        }
        self.build_container_command(engine, script_root, script_args, build_request)
    }

    fn build_container_rebuild_command(
        &self,
        engine: &str,
        clean_out: bool,
        reseed: bool,
        no_package: bool,
        build_request: &BuildGkiRequest,
    ) -> Result<CommandSpec> {
        let script_root = self.script_root();
        let script = script_root.join("rebuild.sh");
        let mut script_args = vec![script.to_string_lossy().to_string()];
        if clean_out {
            script_args.push("--clean-out".into());
        }
        if reseed {
            script_args.push("--reseed".into());
        }
        if no_package {
            script_args.push("--no-package".into());
        }
        self.build_container_command(engine, script_root, script_args, Some(build_request))
    }

    fn build_container_command(
        &self,
        engine: &str,
        script_root: PathBuf,
        script_args: Vec<String>,
        build_request: Option<&BuildGkiRequest>,
    ) -> Result<CommandSpec> {
        let image = container_image_for(match engine {
            "docker" => LocalBuildBackendKind::Docker,
            "podman" => LocalBuildBackendKind::Podman,
            _ => return Err(anyhow!("unsupported container engine {}", engine)),
        });
        let home_dir = self.data_root.join("container-home");
        fs::create_dir_all(&home_dir)
            .with_context(|| format!("failed to create {}", home_dir.display()))?;

        let workspace_dir = self.workspace_dir();
        if workspace_dir != self.script_root().join(".local-build").join("workspace") {
            fs::create_dir_all(&workspace_dir)
                .with_context(|| format!("failed to create {}", workspace_dir.display()))?;
        }

        let mut mounts = vec![repo_root(), self.script_root(), workspace_dir];
        if let Some(build_request) = build_request {
            mounts.extend(extract_custom_module_paths(build_request));
        }
        let mounts = normalize_mounts(mounts);
        let (uid, gid) = current_uid_gid();

        let mut args = vec![
            "run".into(),
            "--rm".into(),
            "--pull".into(),
            "never".into(),
            "--user".into(),
            format!("{uid}:{gid}"),
            "-e".into(),
            format!("HOME={CONTAINER_HOME_MOUNT_TARGET}"),
            "-e".into(),
            format!(
                "ABK_LOCAL_BUILD_ABK_SOURCE_DIR={}",
                repo_root().to_string_lossy()
            ),
            "-w".into(),
            script_root.to_string_lossy().to_string(),
        ];
        args.extend(container_runtime_network_args(
            engine,
            &ProxySettings::from_env(),
        ));
        for mount in mounts {
            let path = mount.to_string_lossy().to_string();
            args.push("-v".into());
            args.push(format!("{path}:{path}"));
        }
        args.push("-v".into());
        args.push(format!(
            "{}:{}",
            home_dir.to_string_lossy(),
            CONTAINER_HOME_MOUNT_TARGET
        ));
        args.push("--entrypoint".into());
        args.push("bash".into());
        args.push(image);
        args.extend(script_args);
        Ok(CommandSpec {
            program: engine.into(),
            args,
            cwd: repo_root(),
            env: Vec::new(),
            stdin: None,
        })
    }

    fn require_source_instance(
        &self,
        source_instance_id: &str,
    ) -> Result<LocalBuildSourceInstance> {
        self.store
            .source_instances
            .iter()
            .find(|source| source.id == source_instance_id)
            .cloned()
            .ok_or_else(|| anyhow!("unknown source instance {}", source_instance_id))
    }

    fn require_source_instance_mut(
        &mut self,
        source_instance_id: &str,
    ) -> Result<&mut LocalBuildSourceInstance> {
        self.store
            .source_instances
            .iter_mut()
            .find(|source| source.id == source_instance_id)
            .ok_or_else(|| anyhow!("unknown source instance {}", source_instance_id))
    }

    fn require_profile(&self, profile_id: &str) -> Result<LocalBuildProfile> {
        self.store
            .profiles
            .iter()
            .find(|profile| profile.id == profile_id)
            .cloned()
            .ok_or_else(|| anyhow!("unknown local build profile {}", profile_id))
    }

    fn require_profile_mut(&mut self, profile_id: &str) -> Result<&mut LocalBuildProfile> {
        self.store
            .profiles
            .iter_mut()
            .find(|profile| profile.id == profile_id)
            .ok_or_else(|| anyhow!("unknown local build profile {}", profile_id))
    }

    fn capture_artifacts_and_logs(
        &mut self,
        source_instance_id: &str,
        profile_id: Option<&str>,
        task_id: &str,
        backend_kind: LocalBuildBackendKind,
        materialized: &LocalBuildMaterializedState,
    ) {
        let created_at_ms = now_ms();
        if let Some(artifacts_dir) = materialized.artifacts_dir.as_deref() {
            for path in list_regular_files(Path::new(artifacts_dir)) {
                let file_name = path
                    .file_name()
                    .map(|item| item.to_string_lossy().to_string())
                    .unwrap_or_else(|| path.to_string_lossy().to_string());
                self.store.artifacts.push(LocalBuildArtifactEntry {
                    id: Uuid::new_v4().to_string(),
                    task_id: task_id.to_string(),
                    profile_id: profile_id.map(ToString::to_string),
                    source_instance_id: source_instance_id.to_string(),
                    backend_kind,
                    path: path.to_string_lossy().to_string(),
                    file_name,
                    exists: path.is_file(),
                    created_at_ms,
                });
            }
        }
        if let Some(log_path) = materialized.latest_log_path.as_deref() {
            let path = PathBuf::from(log_path);
            let file_name = path
                .file_name()
                .map(|item| item.to_string_lossy().to_string())
                .unwrap_or_else(|| path.to_string_lossy().to_string());
            self.store.logs.push(LocalBuildLogEntry {
                id: Uuid::new_v4().to_string(),
                task_id: task_id.to_string(),
                profile_id: profile_id.map(ToString::to_string),
                source_instance_id: source_instance_id.to_string(),
                backend_kind,
                path: path.to_string_lossy().to_string(),
                file_name,
                exists: path.is_file(),
                created_at_ms,
            });
        }
    }
}

fn load_store(path: &Path) -> Result<LocalBuildStore> {
    if !path.is_file() {
        return Ok(LocalBuildStore::default());
    }
    let content =
        fs::read_to_string(path).with_context(|| format!("failed to read {}", path.display()))?;
    let store: LocalBuildStore =
        serde_json::from_str(&content).context("failed to parse local build store")?;
    if store.schema_version != STORE_SCHEMA_VERSION {
        return Ok(LocalBuildStore::default());
    }
    Ok(store)
}

fn backend_descriptor(
    kind: LocalBuildBackendKind,
    probe: BackendProbe,
    supports_source_sync: bool,
    supports_build_execution: bool,
    notes: Vec<String>,
) -> LocalBuildBackendDescriptor {
    LocalBuildBackendDescriptor {
        kind,
        label: kind.display_name().to_string(),
        available: probe.available,
        is_global_default: false,
        authorization_required: probe.authorization_required,
        authorization_kind: probe.authorization_kind,
        authorization_message: probe.authorization_message,
        capabilities: LocalBuildBackendCapabilities {
            family: kind.family().to_string(),
            host_owned_paths: true,
            supports_source_sync,
            supports_build_execution,
            supports_profile_projection: kind == LocalBuildBackendKind::Script,
            notes,
        },
        detail: probe.detail,
    }
}

fn supported_kernel_lines(script_root: &Path) -> Vec<SupportedKernelLine> {
    [
        ("android12", "5.10"),
        ("android13", "5.15"),
        ("android14", "6.1"),
        ("android15", "6.6"),
        ("android16", "6.12"),
    ]
    .into_iter()
    .map(|(android_version, kernel_version)| {
        let id = format!("{android_version}/{kernel_version}");
        let script_template_path =
            script_template_path(&script_root, android_version, kernel_version);
        SupportedKernelLine {
            id,
            android_version: android_version.to_string(),
            kernel_version: kernel_version.to_string(),
            display_name: format!("{android_version} / {kernel_version}"),
            branch_month_format: "YYYY-MM".into(),
            script_template_available: Path::new(&script_template_path).is_dir(),
            script_template_path,
        }
    })
    .collect()
}

fn script_template_path(script_root: &Path, android_version: &str, kernel_version: &str) -> String {
    let android_suffix = android_version.trim_start_matches("android");
    script_root
        .join(format!(
            "AOSP_Kernel_A{}_{}",
            android_suffix, kernel_version
        ))
        .to_string_lossy()
        .to_string()
}

fn find_kernel_line(script_root: &Path, kernel_line_id: &str) -> Result<SupportedKernelLine> {
    supported_kernel_lines(script_root)
        .into_iter()
        .find(|line| line.id.eq_ignore_ascii_case(kernel_line_id.trim()))
        .ok_or_else(|| anyhow!("unsupported kernel line {}", kernel_line_id.trim()))
}

fn normalize_branch_month(raw: &str) -> Result<String> {
    let value = raw.trim();
    let bytes = value.as_bytes();
    let valid = bytes.len() == 7
        && bytes[0].is_ascii_digit()
        && bytes[1].is_ascii_digit()
        && bytes[2].is_ascii_digit()
        && bytes[3].is_ascii_digit()
        && bytes[4] == b'-'
        && bytes[5].is_ascii_digit()
        && bytes[6].is_ascii_digit();
    if !valid {
        return Err(anyhow!("branchMonth must use YYYY-MM"));
    }
    Ok(value.to_string())
}

fn source_instance_id(kernel_line_id: &str, branch_month: &str) -> String {
    format!(
        "{}@{}",
        kernel_line_id.replace('/', "-"),
        branch_month.trim()
    )
}

fn default_build_request_for_source(source_instance: &LocalBuildSourceInstance) -> BuildGkiRequest {
    BuildGkiRequest {
        target: "custom".into(),
        ksu_variant: Some("ReSukiSU".into()),
        ksu_branch: Some("Stable".into()),
        version: Some(String::new()),
        revision: Some(if source_instance.kernel_version == "5.10" {
            "r11".into()
        } else {
            String::new()
        }),
        custom_ref: Some(String::new()),
        build_time: Some(String::new()),
        custom_modules: Some(String::new()),
        kpm_password: Some(String::new()),
        virt: Some("off".into()),
        zram: false,
        bbg: false,
        ddk: false,
        kpm: false,
        susfs: true,
        rekernel: false,
        ntsync: false,
        networking: false,
        zram_full_algo: false,
        zram_extra_algos: Some(String::new()),
        android_version: Some(source_instance.android_version.clone()),
        kernel_version: Some(source_instance.kernel_version.clone()),
        sub_level: source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.sub_level.clone()),
        os_patch_level: source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.os_patch_level.clone())
            .or_else(|| Some(source_instance.branch_month.clone())),
        force: None,
    }
}

fn normalize_build_request(
    mut build_request: BuildGkiRequest,
    source_instance: &LocalBuildSourceInstance,
) -> BuildGkiRequest {
    build_request.target = "custom".into();
    build_request.android_version = Some(source_instance.android_version.clone());
    build_request.kernel_version = Some(source_instance.kernel_version.clone());
    if build_request
        .sub_level
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
    {
        build_request.sub_level = source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.sub_level.clone());
    }
    if build_request
        .os_patch_level
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
    {
        build_request.os_patch_level = source_instance
            .materialized
            .as_ref()
            .and_then(|materialized| materialized.os_patch_level.clone())
            .or_else(|| Some(source_instance.branch_month.clone()));
    }
    if source_instance.kernel_version != "5.10" {
        build_request.revision = Some(String::new());
    } else if build_request
        .revision
        .as_deref()
        .unwrap_or_default()
        .trim()
        .is_empty()
    {
        build_request.revision = Some("r11".into());
    }
    build_request.force = None;
    build_request
}

fn materialized_state_from_legacy_status(status: LocalBuildStatus) -> LocalBuildMaterializedState {
    LocalBuildMaterializedState {
        script_root: Some(status.script_root),
        env_file_path: Some(status.env_file_path),
        state_dir: Some(status.state_dir),
        sources_dir: Some(status.sources_dir),
        workspace_dir: Some(status.workspace_dir),
        artifacts_dir: Some(status.artifacts_dir),
        logs_dir: Some(status.logs_dir),
        cache_dir: Some(status.cache_dir),
        kernel_root: Some(status.kernel_root),
        template_name: status.template_name,
        template_root: status.template_root,
        template_branch: status.template_branch,
        template_common_branch: status.template_common_branch,
        sub_level: status.sub_level,
        os_patch_level: status.os_patch_level,
        latest_log_path: status.latest_log_path,
    }
}

fn legacy_status_matches_source(
    status: &LocalBuildStatus,
    source_instance: &LocalBuildSourceInstance,
) -> bool {
    status.available
        && status.has_env_file
        && status.template_android_version.as_deref()
            == Some(source_instance.android_version.as_str())
        && status.template_kernel_version.as_deref()
            == Some(source_instance.kernel_version.as_str())
        && status.branch_month.as_deref() == Some(source_instance.branch_month.as_str())
}

fn script_backend_available(script_root: &Path) -> bool {
    script_root.join("init.sh").is_file() && script_root.join("rebuild.sh").is_file()
}

fn command_available(program: &str, args: &[&str]) -> bool {
    Command::new(program)
        .args(args)
        .current_dir(repo_root())
        .output()
        .map(|output| output.status.success())
        .unwrap_or(false)
}

fn container_image_for(kind: LocalBuildBackendKind) -> String {
    let env_name = match kind {
        LocalBuildBackendKind::Docker => "ABK_LOCAL_BUILD_DOCKER_IMAGE",
        LocalBuildBackendKind::Podman => "ABK_LOCAL_BUILD_PODMAN_IMAGE",
        _ => return DEFAULT_CONTAINER_IMAGE.into(),
    };
    env::var(env_name)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| DEFAULT_CONTAINER_IMAGE.into())
}

fn inspect_container_backend(program: &str, image: &str) -> BackendProbe {
    if !command_available(program, &["--version"]) {
        return BackendProbe {
            available: false,
            detail: Some(format!("{program} is not installed on this host.")),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        };
    }
    match Command::new(program)
        .args(["image", "inspect", image])
        .current_dir(repo_root())
        .output()
    {
        Ok(output) if output.status.success() => BackendProbe {
            available: true,
            detail: None,
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        },
        Ok(output) => {
            let stderr = String::from_utf8_lossy(&output.stderr);
            let stdout = String::from_utf8_lossy(&output.stdout);
            let text = format!("{}\n{}", stdout.trim(), stderr.trim())
                .trim()
                .to_string();
            let lower = text.to_ascii_lowercase();
            if lower.contains("permission denied") {
                return BackendProbe {
                    available: true,
                    detail: Some(format!(
                        "{program} daemon is not accessible by the current user."
                    )),
                    authorization_required: true,
                    authorization_kind: Some("sudo".into()),
                    authorization_message: Some(format!(
                        "Authorize elevated access so ABK can run {program} through sudo."
                    )),
                };
            }
            let detail = if lower.contains("no such image")
                || lower.contains("not found")
                || lower.contains("image not known")
            {
                format!("Container image {image} is not present locally.")
            } else if text.is_empty() {
                format!("failed to inspect {program} image {image}.")
            } else {
                text
            };
            BackendProbe {
                available: false,
                detail: Some(detail),
                authorization_required: false,
                authorization_kind: None,
                authorization_message: None,
            }
        }
        Err(error) => BackendProbe {
            available: false,
            detail: Some(format!("failed to execute {program}: {error}")),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        },
    }
}

fn inspect_wsl_backend() -> BackendProbe {
    if !command_available("wsl", &["--status"]) {
        return BackendProbe {
            available: false,
            detail: Some(
                "wsl.exe is not available on this host. This backend is for Windows only.".into(),
            ),
            authorization_required: false,
            authorization_kind: None,
            authorization_message: None,
        };
    }
    BackendProbe {
        available: false,
        detail: Some("WSL backend protocol is reserved but not wired yet.".into()),
        authorization_required: false,
        authorization_kind: None,
        authorization_message: None,
    }
}

fn current_uid_gid() -> (u32, u32) {
    #[cfg(unix)]
    // SAFETY: getuid/getgid are pure libc calls without preconditions.
    unsafe {
        return (getuid(), getgid());
    }

    #[cfg(not(unix))]
    {
        (0, 0)
    }
}

fn extract_custom_module_paths(build_request: &BuildGkiRequest) -> Vec<PathBuf> {
    build_request
        .custom_modules
        .as_deref()
        .unwrap_or_default()
        .split('|')
        .filter_map(parse_custom_module_path)
        .collect()
}

fn parse_custom_module_path(entry: &str) -> Option<PathBuf> {
    let head = entry.trim().split(';').next()?.trim();
    if head.is_empty() {
        return None;
    }
    let path = head
        .strip_prefix("module:")
        .or_else(|| head.strip_prefix("set:"))
        .unwrap_or(head)
        .split('#')
        .next()
        .unwrap_or_default()
        .trim();
    if path.starts_with('/') {
        Some(PathBuf::from(path))
    } else {
        None
    }
}

fn normalize_mounts(paths: Vec<PathBuf>) -> Vec<PathBuf> {
    let mut unique = paths
        .into_iter()
        .filter_map(|path| fs::canonicalize(path).ok())
        .collect::<Vec<_>>();
    unique.sort();
    unique.dedup();
    let mut filtered = Vec::new();
    for candidate in &unique {
        if !unique_path_has_parent_in_list(candidate, &unique) {
            filtered.push(candidate.clone());
        }
    }
    filtered
}

fn unique_path_has_parent_in_list(candidate: &Path, all: &[PathBuf]) -> bool {
    all.iter()
        .any(|other| other != candidate && candidate.starts_with(other))
}

fn container_runtime_network_args(engine: &str, proxy_settings: &ProxySettings) -> Vec<String> {
    if proxy_settings.requires_host_network_for_container() {
        let mut args = vec!["--network".into(), "host".into()];
        args.extend(proxy_settings.container_env_args());
        return args;
    }
    let mut args = Vec::new();
    if let Some(host_map) = container_host_map_arg(engine) {
        args.push("--add-host".into());
        args.push(host_map.into());
    }
    args.extend(proxy_settings.container_env_args_for_host(container_proxy_host_alias(engine)));
    args
}

fn container_proxy_host_alias(engine: &str) -> Option<&'static str> {
    match engine {
        "docker" => Some(DOCKER_CONTAINER_HOST_ALIAS),
        "podman" => Some(PODMAN_CONTAINER_HOST_ALIAS),
        _ => None,
    }
}

fn container_host_map_arg(engine: &str) -> Option<&'static str> {
    match engine {
        "docker" => Some(DOCKER_CONTAINER_HOST_MAP),
        _ => None,
    }
}

fn authorize_command_if_needed(
    command: CommandSpec,
    backend: &LocalBuildBackendDescriptor,
    sudo_password: Option<&str>,
) -> Result<CommandSpec> {
    if !backend.authorization_required {
        return Ok(command);
    }
    let password = sudo_password
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .ok_or_else(|| anyhow!("{} requires elevated authorization", backend.label))?;
    match backend.authorization_kind.as_deref() {
        Some("sudo") | None => Ok(wrap_command_with_sudo(command, password)),
        Some(other) => Err(anyhow!("unsupported authorization kind {}", other)),
    }
}

fn list_regular_files(dir: &Path) -> Vec<PathBuf> {
    let mut files = fs::read_dir(dir)
        .ok()
        .into_iter()
        .flatten()
        .filter_map(|entry| {
            let entry = entry.ok()?;
            if !entry.file_type().ok()?.is_file() {
                return None;
            }
            Some(entry.path())
        })
        .collect::<Vec<_>>();
    files.sort();
    files
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn docker_network_args_use_host_network_for_loopback_proxy() {
        let proxy_settings = ProxySettings {
            http_proxy: Some("http://127.0.0.1:7890".into()),
            https_proxy: Some("http://localhost:7890".into()),
            all_proxy: Some("socks5://127.0.0.1:7891".into()),
            no_proxy: Some("127.0.0.1,localhost".into()),
        };

        assert_eq!(
            container_runtime_network_args("docker", &proxy_settings),
            vec![
                "--network",
                "host",
                "-e",
                "http_proxy=http://127.0.0.1:7890",
                "-e",
                "HTTP_PROXY=http://127.0.0.1:7890",
                "-e",
                "https_proxy=http://localhost:7890",
                "-e",
                "HTTPS_PROXY=http://localhost:7890",
                "-e",
                "all_proxy=socks5://127.0.0.1:7891",
                "-e",
                "ALL_PROXY=socks5://127.0.0.1:7891",
                "-e",
                "no_proxy=127.0.0.1,localhost",
                "-e",
                "NO_PROXY=127.0.0.1,localhost",
            ]
        );
    }

    #[test]
    fn docker_network_args_rewrite_non_loopback_proxy_to_host_alias() {
        let proxy_settings = ProxySettings {
            http_proxy: Some("http://proxy.example:7890".into()),
            https_proxy: None,
            all_proxy: None,
            no_proxy: Some("127.0.0.1,localhost".into()),
        };

        assert_eq!(
            container_runtime_network_args("docker", &proxy_settings),
            vec![
                "--add-host",
                "host.docker.internal:host-gateway",
                "-e",
                "http_proxy=http://proxy.example:7890",
                "-e",
                "HTTP_PROXY=http://proxy.example:7890",
                "-e",
                "no_proxy=127.0.0.1,localhost",
                "-e",
                "NO_PROXY=127.0.0.1,localhost",
            ]
        );
    }

    #[test]
    fn container_home_uses_short_mount_target_inside_container() {
        let repo_root = repo_root();
        let manager = LocalBuildManager::new(repo_root.clone()).expect("manager");
        let source = LocalBuildSourceInstance {
            id: "android14-6.1@2025-01".into(),
            display_name: "android14/6.1 2025-01".into(),
            kernel_line_id: "android14/6.1".into(),
            android_version: "android14".into(),
            kernel_version: "6.1".into(),
            branch_month: "2025-01".into(),
            cache_root: String::new(),
            working_tree_root: String::new(),
            state: "ready".into(),
            created_at_ms: 0,
            updated_at_ms: 0,
            last_synced_at_ms: None,
            active_backend_kind: None,
            last_task_id: None,
            last_error: None,
            materialized: None,
        };

        let command = manager
            .build_container_init_command("docker", &source, false, true, None)
            .expect("command");

        assert!(command
            .args
            .contains(&format!("HOME={CONTAINER_HOME_MOUNT_TARGET}")));
        let expected_home_mount = format!(
            "{}:{CONTAINER_HOME_MOUNT_TARGET}",
            manager.data_root.join("container-home").to_string_lossy()
        );
        assert!(command
            .args
            .iter()
            .any(|arg| { arg == &expected_home_mount }));
    }
}
