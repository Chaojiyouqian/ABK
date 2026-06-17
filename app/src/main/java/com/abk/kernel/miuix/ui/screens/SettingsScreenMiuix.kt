package com.abk.kernel.miuix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * MIUIX-styled settings screen for ABK.
 *
 * Covers all Material 3 settings sections: Account, Build, App Update,
 * Notification, Navigation, Language, Theme, Extensions, and About.
 */
@Composable
fun SettingsScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onChildPageVisibleChange: (Boolean) -> Unit = {},
    onAbout: () -> Unit = {},
    onOpenSourceLicenses: () -> Unit = {},
    onOpenExtensionManager: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    var showThemeSettings by rememberSaveable { mutableStateOf(false) }

    if (showThemeSettings) {
        ThemeSettingsScreenMiuix(vm = vm, onBack = { showThemeSettings = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = stringResource(R.string.nav_settings)
                )
            }
        ) { padding ->
            val listState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(listState)
                    .overScrollVertical()
                    .scrollEndHaptic()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // ── Account section ────────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_account))
                Card(modifier = Modifier.fillMaxWidth()) {
                    state.user?.let { user ->
                        SuperArrow(
                            title = user.login,
                            summary = user.name ?: user.htmlUrl
                        )
                    } ?: run {
                        SuperArrow(
                            title = stringResource(R.string.settings_not_logged_in)
                        )
                    }
                }

                // ── Build settings section ─────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_build))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_auto_download),
                        summary = stringResource(R.string.settings_auto_download_desc),
                        checked = state.autoDownload,
                        onCheckedChange = { vm.setAutoDownload(it) }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.settings_prebuilt_gki),
                        summary = stringResource(R.string.settings_prebuilt_gki_desc),
                        checked = state.prebuiltGkiEnabled,
                        onCheckedChange = { vm.setPrebuiltGkiEnabled(it) }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.settings_workflow_foreground_refresh),
                        summary = stringResource(R.string.settings_workflow_foreground_refresh_desc),
                        checked = state.workflowForegroundRefreshEnabled,
                        onCheckedChange = { vm.setWorkflowForegroundRefreshEnabled(it) }
                    )
                }

                // ── App Update section ─────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_app_update))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = stringResource(R.string.settings_app_update_stability),
                        summary = if (state.appUpdateStability == "stable")
                            stringResource(R.string.settings_app_update_stable)
                        else
                            stringResource(R.string.settings_app_update_unstable),
                        onClick = {
                            val next = if (state.appUpdateStability == "stable") "unstable" else "stable"
                            vm.setAppUpdateStability(next)
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_app_update_line),
                        summary = if (state.appUpdateLine == "normal")
                            stringResource(R.string.settings_app_update_line_normal)
                        else
                            stringResource(R.string.settings_app_update_line_dev),
                        onClick = {
                            val next = if (state.appUpdateLine == "normal") "dev" else "normal"
                            vm.setAppUpdateLine(next)
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_check_app_update),
                        summary = if (state.appUpdateChecking) "检查中..." else stringResource(R.string.settings_check_app_update),
                        onClick = { vm.checkAppUpdate() }
                    )
                }

                // ── Notification section ───────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_notification))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_notify_build),
                        summary = stringResource(R.string.settings_notify_build_desc),
                        checked = state.notifyBuild,
                        onCheckedChange = { vm.setNotifyBuild(it) }
                    )
                }

                // ── Navigation section ─────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_navigation))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_predictive_back),
                        summary = stringResource(R.string.settings_predictive_back_desc),
                        checked = state.predictiveBackEnabled,
                        onCheckedChange = { vm.setPredictiveBackEnabled(it) }
                    )
                }

                // ── Language section (display only — picker needs Activity.recreate()) ──
                SettingsSectionTitle(stringResource(R.string.settings_language))
                Card(modifier = Modifier.fillMaxWidth()) {
                    val ctx = LocalContext.current
                    val currentLang = LocaleHelper.getLanguage(ctx)
                    SuperArrow(
                        title = stringResource(R.string.settings_language),
                        summary = currentLang
                    )
                }

                // ── Theme section ──────────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_theme))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = stringResource(R.string.settings_theme),
                        summary = "${themeModeLabel(state.themeMode)} · ${if (state.dynamicColorEnabled) "动态色" else "自定义"}",
                        onClick = { showThemeSettings = true }
                    )
                }

                // ── Extensions section ─────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_extensions_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = stringResource(R.string.settings_extensions_manage),
                        summary = stringResource(R.string.settings_extensions_manage_desc),
                        onClick = onOpenExtensionManager
                    )
                }

                // ── About section ──────────────────────────────────────────
                SettingsSectionTitle(stringResource(R.string.settings_about))
                Card(modifier = Modifier.fillMaxWidth()) {
                    SuperArrow(
                        title = stringResource(R.string.app_full_name),
                        summary = "AnyBase Kernel v${BuildConfig.APP_VERSION_NAME}"
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_about_desc),
                        onClick = onAbout
                    )
                    SuperArrow(
                        title = stringResource(R.string.settings_open_source_licenses),
                        summary = stringResource(R.string.settings_open_source_licenses_desc),
                        onClick = onOpenSourceLicenses
                    )
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    } // end else
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

private fun themeModeLabel(mode: String): String = when (mode) {
    "system" -> "跟随系统"
    "light" -> "浅色"
    "dark" -> "深色"
    else -> mode
}
