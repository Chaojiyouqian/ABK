package com.abk.kernel.miuix.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.APP_UPDATE_LINE_DEV
import com.abk.kernel.data.model.APP_UPDATE_LINE_NORMAL
import com.abk.kernel.data.model.APP_UPDATE_STABILITY_STABLE
import com.abk.kernel.data.model.APP_UPDATE_STABILITY_UNSTABLE
import com.abk.kernel.data.model.AppUpdateCheckResult
import com.abk.kernel.data.model.ManagerSettingKind
import com.abk.kernel.data.model.normalizeAppUpdateLine
import com.abk.kernel.data.model.normalizeAppUpdateStability
import com.abk.kernel.data.repository.PreferencesRepository
import com.abk.kernel.utils.DownloadDirectoryUtils
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * MIUIX-styled settings screen for ABK.
 *
 * Covers all Material 3 settings sections in the same order and with the same
 * callback signatures as [com.abk.kernel.ui.screens.SettingsScreen]:
 * Account, Build, App Update, Manager Injected, Notification, Navigation,
 * Language, Theme, Extensions, About.
 *
 * Navigation to sub-pages (ThemeSettings, AppProfileTemplates, etc.) is
 * handled via [LocalNavigator] + [Route] (Navigation3 NavDisplay).
 * [onOpenInstalledModules] is kept because it performs cross-tab navigation
 * that lives outside the NavDisplay scope.
 */
@Composable
fun SettingsScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onLogout: () -> Unit = {},
    onOpenInstalledModules: () -> Unit = {},
) {
    val state by vm.uiState.collectAsState()
    val scrollBehavior = MiuixScrollBehavior()
    val iconTint = MiuixTheme.colorScheme.onSurfaceSecondary
    val navigator = LocalNavigator.current

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearArtifactsDialog by remember { mutableStateOf(false) }

    // Refresh manager settings on first composition (mirrors MD3 LaunchedEffect).
    LaunchedEffect(Unit) {
        vm.refreshManagerSettings(force = true)
    }

    // Auto-install pending app update APK (mirrors MD3 LaunchedEffect).
    LaunchedEffect(state.appUpdatePendingInstallPath) {
        val apkPath = state.appUpdatePendingInstallPath ?: return@LaunchedEffect
        vm.consumeAppUpdatePendingInstallPath()
    }

    // ── Main layout ────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize()) {
        // ── Logout confirmation dialog ──────────────────────────────────────
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.settings_logout_title)) },
                text = { Text(stringResource(R.string.settings_logout_message)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            vm.logout()
                            onLogout()
                        }
                    ) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // ── Clear artifacts confirmation dialog ─────────────────────────────
        if (showClearArtifactsDialog) {
            AlertDialog(
                onDismissRequest = { showClearArtifactsDialog = false },
                title = { Text(stringResource(R.string.settings_clear_artifacts_title)) },
                text = { Text(stringResource(R.string.settings_clear_artifacts_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        vm.clearAllDownloadedArtifacts()
                        showClearArtifactsDialog = false
                    }) {
                        Text(stringResource(R.string.settings_clear_artifacts_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearArtifactsDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = stringResource(R.string.settings_title),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            val listState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(listState)
                    .overScrollVertical()
                    .scrollEndHaptic()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ═══════════════════════════════════════════════════════════
                // 1. ACCOUNT
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_account))
                Card(modifier = Modifier.fillMaxWidth()) {
                    state.user?.let { user ->
                        // User row: avatar + login + subtitle + logout button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Avatar (MD3 uses 42.dp CircleShape AsyncImage)
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = user.login,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                            )
                            // Name + subtitle
                            Column(modifier = Modifier.weight(1f)) {
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = user.login,
                                    style = MiuixTheme.textStyles.main
                                )
                                top.yukonga.miuix.kmp.basic.Text(
                                    text = user.name ?: user.htmlUrl,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            // Logout button (MD3 IconButton + error tint)
                            IconButton(onClick = { showLogoutDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = stringResource(R.string.settings_logout_desc),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        // Fork repository link (clickable)
                        val context = LocalContext.current
                        val forkUrl = state.forkRepo?.let { repo ->
                            repo.htmlUrl.takeIf { it.isNotBlank() }
                                ?: "https://github.com/${repo.fullName}"
                        }
                        ArrowPreference(
                            title = stringResource(R.string.settings_fork_repo),
                            summary = state.forkRepo?.fullName
                                ?: stringResource(R.string.settings_waiting_fork),
                            startAction = { Icon(Icons.Default.ForkRight, contentDescription = null, tint = iconTint) },
                            onClick = forkUrl?.let { url -> { openUrl(context, url) } }
                        )
                    } ?: run {
                        // Not logged in
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            top.yukonga.miuix.kmp.basic.Text(
                                text = stringResource(R.string.settings_not_logged_in),
                                style = MiuixTheme.textStyles.main,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 2. BUILD
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_build))
                Card(modifier = Modifier.fillMaxWidth()) {
                    // Foreground refresh switch
                    SwitchPreference(
                        title = stringResource(R.string.settings_workflow_foreground_refresh),
                        summary = stringResource(R.string.settings_workflow_foreground_refresh_desc),
                        startAction = { Icon(Icons.Default.Sync, contentDescription = null, tint = iconTint) },
                        checked = state.workflowForegroundRefreshEnabled,
                        onCheckedChange = { vm.setWorkflowForegroundRefreshEnabled(it) }
                    )
                    // Conditional interval picker (animated)
                    AnimatedVisibility(
                        visible = state.workflowForegroundRefreshEnabled,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        ForegroundRefreshIntervalPicker(
                            selectedSec = state.workflowForegroundRefreshIntervalSec,
                            onSelect = { vm.setWorkflowForegroundRefreshIntervalSec(it) }
                        )
                    }
                    // Auto download
                    SwitchPreference(
                        title = stringResource(R.string.settings_auto_download),
                        summary = stringResource(R.string.settings_auto_download_desc),
                        startAction = { Icon(Icons.Default.Download, contentDescription = null, tint = iconTint) },
                        checked = state.autoDownload,
                        onCheckedChange = { vm.setAutoDownload(it) }
                    )
                    // Prebuilt GKI
                    SwitchPreference(
                        title = stringResource(R.string.settings_prebuilt_gki),
                        summary = stringResource(R.string.settings_prebuilt_gki_desc),
                        startAction = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = iconTint) },
                        checked = state.prebuiltGkiEnabled,
                        onCheckedChange = { vm.setPrebuiltGkiEnabled(it) }
                    )
                    // Download directory
                    DownloadDirectoryItem(
                        value = state.downloadDirectory,
                        onValueChange = { vm.setDownloadDirectory(it) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = iconTint) }
                    )
                    // Mirror URL
                    MirrorUrlItem(
                        value = state.downloadMirrorBaseUrl,
                        onValueChange = { vm.setDownloadMirrorBaseUrl(it) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = iconTint) }
                    )
                    // Clear artifacts
                    val hasArtifacts = state.downloadedArtifacts.isNotEmpty()
                    ArrowPreference(
                        title = stringResource(R.string.settings_clear_artifacts),
                        summary = if (hasArtifacts) {
                            val count = state.downloadedArtifacts.size
                            val totalBytes = state.downloadedArtifacts.sumOf { it.sizeBytes }
                            "$count ${stringResource(R.string.settings_clear_artifacts_files)} · ${DownloadUtils.formatSize(totalBytes)}"
                        } else {
                            stringResource(R.string.settings_clear_artifacts_empty)
                        },
                        startAction = { Icon(Icons.Default.Delete, contentDescription = null, tint = iconTint) },
                        onClick = if (hasArtifacts) {
                            { showClearArtifactsDialog = true }
                        } else null
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 3. APP UPDATE
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_app_update))
                Card(modifier = Modifier.fillMaxWidth()) {
                    // Stability picker (stable / unstable as two SuperArrow rows)
                    SectionSubLabel(stringResource(R.string.settings_app_update_stability))
                    AppUpdateStabilityPicker(
                        selected = state.appUpdateStability,
                        onSelect = vm::setAppUpdateStability
                    )
                    // Line picker (normal / dev as two SuperArrow rows)
                    SectionSubLabel(stringResource(R.string.settings_app_update_line))
                    AppUpdateLinePicker(
                        selected = state.appUpdateLine,
                        onSelect = vm::setAppUpdateLine
                    )
                    // Check for update
                    ArrowPreference(
                        title = stringResource(R.string.settings_check_app_update),
                        summary = appUpdateCheckSubtitle(state),
                        endActions = {
                            if (state.appUpdateChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MiuixTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = iconTint)
                            }
                        },
                        onClick = { if (!state.appUpdateChecking) vm.checkAppUpdate() }
                    )
                    // Update info display
                    state.appUpdateInfo?.let { info ->
                        ArrowPreference(
                            title = if (info.hasUpdate) {
                                stringResource(R.string.settings_app_update_available)
                            } else {
                                stringResource(R.string.settings_app_update_latest)
                            },
                            summary = appUpdateResultSubtitle(info),
                            startAction = {
                                Icon(
                                    imageVector = if (info.hasUpdate) Icons.Default.Download else Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = iconTint
                                )
                            }
                        )
                        AnimatedVisibility(
                            visible = info.hasUpdate,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            val downloadUrl = info.remote.downloadUrl
                            ArrowPreference(
                                title = stringResource(R.string.settings_download_install_update),
                                summary = when {
                                    state.appUpdateDownloading -> stringResource(
                                        R.string.settings_app_update_downloading_progress,
                                        state.appUpdateDownloadProgress
                                    )
                                    downloadUrl.isBlank() -> stringResource(R.string.settings_app_update_link_missing)
                                    else -> downloadUrl
                                },
                                startAction = { Icon(Icons.Default.InstallMobile, contentDescription = null, tint = iconTint) },
                                endActions = {
                                    if (state.appUpdateDownloading) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MiuixTheme.colorScheme.primary)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = iconTint)
                                    }
                                },
                                onClick = downloadUrl.takeIf { it.isNotBlank() }
                                    ?.let { { vm.downloadAndInstallAppUpdate() } }
                            )
                        }
                    }
                    // Error display
                    state.appUpdateError?.takeIf { it.isNotBlank() }?.let { error ->
                        ArrowPreference(
                            title = stringResource(R.string.settings_app_update_error),
                            summary = error,
                            startAction = { Icon(Icons.Default.Error, contentDescription = null, tint = iconTint) }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 4. MANAGER INJECTED SETTINGS (conditional)
                // ═══════════════════════════════════════════════════════════
                if (state.hasNativeManagerPermission) {
                    val hasInjectedSettings = state.managerSettingsItems.isNotEmpty()
                    if (hasInjectedSettings ||
                        state.managerSettingsLoading ||
                        state.managerSettingsError != null
                    ) {
                        SectionTitle(
                            state.managerSettingsTitle.ifBlank {
                                stringResource(R.string.settings_manager_settings)
                            }
                        )
                        Card(modifier = Modifier.fillMaxWidth()) {
                            // Loading state
                            if (state.managerSettingsLoading && !hasInjectedSettings) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MiuixTheme.colorScheme.primary)
                                    Column {
                                        top.yukonga.miuix.kmp.basic.Text(
                                            text = stringResource(R.string.settings_manager_loading_title),
                                    style = MiuixTheme.textStyles.main
                                        )
                                        top.yukonga.miuix.kmp.basic.Text(
                                            text = stringResource(R.string.settings_manager_loading_desc),
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                            // Error state
                            state.managerSettingsError?.let { error ->
                                ArrowPreference(
                                    title = stringResource(R.string.settings_manager_load_failed),
                                    summary = error,
                                    onClick = { vm.refreshManagerSettings(force = true) }
                                )
                            }
                            // Render each item based on kind
                            state.managerSettingsItems.forEach { item ->
                                val actionInFlight = state.managerSettingActionId == item.id
                                when (item.kind) {
                                    ManagerSettingKind.NAVIGATION -> ArrowPreference(
                                        title = item.title,
                                        summary = item.subtitle,
                                        startAction = { Icon(managerSettingIcon(item.id), contentDescription = null, tint = iconTint) },
                                        endActions = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = iconTint) },
                                        onClick = if (item.enabled && !actionInFlight) {
                                            {
                                                when (item.id) {
                                                    "app_profile_templates" -> navigator.push(Route.AppProfileTemplates)
                                                    "manager_tools" -> navigator.push(Route.ManagerTools)
                                                    "kpm" -> onOpenInstalledModules()
                                                }
                                            }
                                        } else null
                                    )
                                    ManagerSettingKind.SWITCH -> SwitchPreference(
                                        title = item.title,
                                        summary = item.subtitle,
                                        startAction = { Icon(managerSettingIcon(item.id), contentDescription = null, tint = iconTint) },
                                        checked = item.checked,
                                        onCheckedChange = { checked ->
                                            if (item.enabled && !actionInFlight) {
                                                vm.setManagerSettingChecked(item.id, checked)
                                            }
                                        }
                                    )
                                    ManagerSettingKind.MODE -> {
                                        // MODE: show options as selectable SuperArrow rows
                                        val options = item.options
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        val selectedIndex = if (options.isNotEmpty()) {
                                            item.selectedIndex.coerceIn(0, options.lastIndex)
                                        } else 0
                                        SectionSubLabel(item.title)
                                        options.forEachIndexed { index, option ->
                                            val selected = index == selectedIndex
                                            ArrowPreference(
                                                title = option,
                                                summary = if (selected) "✓" else null,
                                                startAction = { Icon(managerSettingIcon(item.id), contentDescription = null, tint = iconTint) },
                                                onClick = if (item.enabled && !actionInFlight && index != selectedIndex) {
                                                    { vm.setManagerSettingMode(item.id, index) }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // 5. NOTIFICATION
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_notification))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_notify_build),
                        summary = stringResource(R.string.settings_notify_build_desc),
                        startAction = { Icon(Icons.Default.Notifications, contentDescription = null, tint = iconTint) },
                        checked = state.notifyBuild,
                        onCheckedChange = { vm.setNotifyBuild(it) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 6. NAVIGATION
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_navigation))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_predictive_back),
                        summary = stringResource(R.string.settings_predictive_back_desc),
                        startAction = { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = iconTint) },
                        checked = state.predictiveBackEnabled,
                        onCheckedChange = { vm.setPredictiveBackEnabled(it) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 7. LANGUAGE
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_language))
                Card(modifier = Modifier.fillMaxWidth()) {
                    val langCtx = LocalContext.current
                    LanguagePicker(
                        currentLanguage = LocaleHelper.getLanguage(langCtx),
                        onSelect = { lang ->
                            LocaleHelper.setLanguage(langCtx, lang)
                            vm.onUiLanguageChanged()
                            (langCtx as? Activity)?.recreate()
                        }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 8. THEME
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_theme))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_color_appearance),
                        summary = "${themeModeLabel(state.themeMode)} · ${dynamicColorLabel(state.dynamicColorEnabled)}",
                        startAction = { Icon(Icons.Default.Palette, contentDescription = null, tint = iconTint) },
                        endActions = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.ThemeSettings) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 9. EXTENSIONS
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_extensions_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_extensions_manage),
                        summary = stringResource(R.string.settings_extensions_manage_desc),
                        startAction = { Icon(Icons.Default.Extension, contentDescription = null, tint = iconTint) },
                        endActions = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.ExtensionManager) }
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // 10. ABOUT
                // ═══════════════════════════════════════════════════════════
                SectionTitle(stringResource(R.string.settings_about))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(R.string.app_full_name),
                        summary = "AnyBase Kernel v${BuildConfig.VERSION_NAME}",
                        startAction = { Icon(Icons.Default.Info, contentDescription = null, tint = iconTint) }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_about_desc),
                        startAction = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = iconTint) },
                        endActions = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.About) }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_open_source_licenses),
                        summary = stringResource(R.string.settings_open_source_licenses_desc),
                        startAction = { Icon(Icons.Default.Article, contentDescription = null, tint = iconTint) },
                        endActions = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = iconTint) },
                        onClick = { navigator.push(Route.OpenSourceLicenses) }
                    )
                }

                Spacer(Modifier.height(60.dp + outerPadding.calculateBottomPadding()))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helper composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    top.yukonga.miuix.kmp.basic.Text(
        text = title,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun SectionSubLabel(label: String) {
    top.yukonga.miuix.kmp.basic.Text(
        text = label,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        textAlign = TextAlign.Center
    )
}

/** Foreground refresh interval picker — three SuperArrow rows (10 / 20 / 30 s). */
@Composable
private fun ForegroundRefreshIntervalPicker(
    selectedSec: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        PreferencesRepository.WORKFLOW_FOREGROUND_REFRESH_INTERVALS_SEC.sorted().forEach { sec ->
            val selected = selectedSec == sec
            ArrowPreference(
                title = stringResource(
                    R.string.settings_workflow_foreground_refresh_interval_sec,
                    sec
                ),
                summary = if (selected) "✓" else null,
                onClick = { onSelect(sec) }
            )
        }
    }
}

/** App update stability picker — two SuperArrow rows (stable / unstable). */
@Composable
private fun AppUpdateStabilityPicker(
    selected: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        APP_UPDATE_STABILITY_STABLE to stringResource(R.string.settings_app_update_stable),
        APP_UPDATE_STABILITY_UNSTABLE to stringResource(R.string.settings_app_update_unstable)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = normalizeAppUpdateStability(selected) == value
            ArrowPreference(
                title = label,
                summary = if (isSelected) "✓" else null,
                onClick = { onSelect(value) }
            )
        }
    }
}

/** App update line picker — two SuperArrow rows (normal / dev). */
@Composable
private fun AppUpdateLinePicker(
    selected: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        APP_UPDATE_LINE_NORMAL to stringResource(R.string.settings_app_update_line_normal),
        APP_UPDATE_LINE_DEV to stringResource(R.string.settings_app_update_line_dev)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = normalizeAppUpdateLine(selected) == value
            ArrowPreference(
                title = label,
                summary = if (isSelected) "✓" else null,
                onClick = { onSelect(value) }
            )
        }
    }
}

/** Download directory selector — uses OpenDocumentTree + text field. */
@Composable
private fun DownloadDirectoryItem(
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val defaultDirectory = remember { DownloadDirectoryUtils.defaultDirectoryPath() }
    val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        !Environment.isExternalStorageManager()
    val unsupportedTreeMessage = stringResource(R.string.settings_download_directory_tree_unsupported)
    val restoredMessage = stringResource(R.string.settings_download_directory_default_restored)
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val selectedPath = DownloadDirectoryUtils.directoryPathFromTreeUri(uri)
            if (selectedPath == null) {
                Toast.makeText(context, unsupportedTreeMessage, Toast.LENGTH_SHORT).show()
            } else {
                onValueChange(selectedPath)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (leadingIcon != null) leadingIcon()
            top.yukonga.miuix.kmp.basic.Text(
                text = stringResource(R.string.settings_download_directory),
                style = MiuixTheme.textStyles.main
            )
        }
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(R.string.settings_download_directory_desc),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        // MD3 OutlinedTextField used as placeholder since MIUIX has no text field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text(defaultDirectory) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_download_directory_choose))
            }
            TextButton(
                onClick = {
                    onValueChange(defaultDirectory)
                    Toast.makeText(context, restoredMessage, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_download_directory_reset))
            }
        }
        AnimatedVisibility(visible = needsAllFilesAccess) {
            OutlinedButton(
                onClick = { openAllFilesAccessSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_download_directory_storage_permission))
            }
        }
    }
}

/** Mirror URL text field. */
@Composable
private fun MirrorUrlItem(
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (leadingIcon != null) leadingIcon()
            top.yukonga.miuix.kmp.basic.Text(
                text = stringResource(R.string.settings_download_mirror),
                style = MiuixTheme.textStyles.main
            )
        }
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(R.string.settings_download_mirror_desc),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("https://hk.gh-proxy.org/") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Language picker — three SuperArrow rows (zh / en / ru). */
@Composable
private fun LanguagePicker(
    currentLanguage: String,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        LocaleHelper.LANG_ZH to stringResource(R.string.settings_language_zh),
        LocaleHelper.LANG_EN to stringResource(R.string.settings_language_en),
        LocaleHelper.LANG_RU to stringResource(R.string.settings_language_ru)
    )
    options.forEach { (lang, label) ->
        val selected = currentLanguage == lang
        ArrowPreference(
            title = label,
            summary = if (selected) "✓" else null,
            startAction = { Icon(Icons.Default.Language, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceSecondary) },
            onClick = { onSelect(lang) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private label helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun themeModeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun dynamicColorLabel(enabled: Boolean): String = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "动态色不可用"
    enabled -> "动态色"
    else -> "自定义"
}

// ─────────────────────────────────────────────────────────────────────────────
// App update subtitle helpers (mirrored from MD3 SettingsScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun appUpdateCheckSubtitle(state: MainUiState): String = when {
    state.appUpdateDownloading -> stringResource(
        R.string.settings_app_update_downloading_progress,
        state.appUpdateDownloadProgress
    )
    state.appUpdateChecking -> stringResource(R.string.settings_app_update_checking)
    state.appUpdateInfo != null -> appUpdateResultSubtitle(state.appUpdateInfo)
    state.appUpdateError?.isNotBlank() == true -> state.appUpdateError
    else -> stringResource(
        R.string.settings_app_update_desc,
        appUpdateStabilityLabel(state.appUpdateStability),
        appUpdateLineLabel(state.appUpdateLine)
    )
}

@Composable
private fun appUpdateResultSubtitle(info: AppUpdateCheckResult): String {
    val status = if (info.hasUpdate) {
        stringResource(R.string.settings_app_update_status_available)
    } else {
        stringResource(R.string.settings_app_update_status_latest)
    }
    val publishedAt = info.remote.publishedAt.ifBlank {
        stringResource(R.string.settings_unknown)
    }
    return stringResource(
        R.string.settings_app_update_result,
        info.currentVersionName,
        info.remote.versionName,
        appUpdateStabilityLabel(info.stability),
        appUpdateLineLabel(info.line),
        publishedAt,
        status
    )
}

@Composable
private fun appUpdateStabilityLabel(value: String): String =
    when (normalizeAppUpdateStability(value)) {
        APP_UPDATE_STABILITY_UNSTABLE -> stringResource(R.string.settings_app_update_unstable)
        else -> stringResource(R.string.settings_app_update_stable)
    }

@Composable
private fun appUpdateLineLabel(value: String): String =
    when (normalizeAppUpdateLine(value)) {
        APP_UPDATE_LINE_DEV -> stringResource(R.string.settings_app_update_line_dev)
        else -> stringResource(R.string.settings_app_update_line_normal)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Manager setting icon mapping (mirrored from MD3 SettingsScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

private fun managerSettingIcon(id: String) = when (id) {
    "app_profile_templates" -> Icons.Default.Apps
    "manager_tools" -> Icons.Default.Build
    "kpm" -> Icons.Default.Extension
    "su_compat" -> Icons.Default.RemoveModerator
    "kernel_umount" -> Icons.Default.RemoveCircle
    "adb_root" -> Icons.Default.Adb
    "sulog" -> Icons.Default.Article
    "selinux_hide" -> Icons.Default.Shield
    "default_umount_modules" -> Icons.Default.FolderDelete
    "webview_debug" -> Icons.Default.Code
    else -> Icons.Default.Settings
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility functions (mirrored from MD3 SettingsScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun openAllFilesAccessSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val appSettings = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        packageUri
    )
    runCatching {
        context.startActivity(appSettings)
    }.getOrElse {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}
