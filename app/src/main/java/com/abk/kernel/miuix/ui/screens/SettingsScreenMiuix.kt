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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
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
 * MIUIX-styled settings screen pilot for ABK.
 *
 * This is a pilot implementation demonstrating MIUIX component usage.
 * It covers the main settings sections (account, build, theme, about)
 * and includes a switch to return to the Material 3 style.
 */
@Composable
fun SettingsScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onChildPageVisibleChange: (Boolean) -> Unit = {},
    onOpenInstalledModules: () -> Unit = {}
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

            // Account section
            SettingsSectionTitle(stringResource(R.string.settings_account))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
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

            // Build settings section
            SettingsSectionTitle(stringResource(R.string.settings_build))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
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
            }

            // Theme section (with MIUIX <-> Material switch)
            SettingsSectionTitle(stringResource(R.string.settings_theme))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                SuperArrow(
                    title = stringResource(R.string.settings_theme),
                    summary = "${themeModeLabel(state.themeMode)} · ${if (state.dynamicColorEnabled) "动态色" else "自定义"}",
                    onClick = { showThemeSettings = true }
                )
                SuperSwitch(
                    title = "Material You 动态色",
                    summary = "跟随系统壁纸配色",
                    checked = state.dynamicColorEnabled,
                    onCheckedChange = { vm.setDynamicColorEnabled(it) }
                )
                SuperArrow(
                    title = "切换到 Material 3 风格",
                    summary = "返回 Material 3 Expressive 主题",
                    onClick = { vm.setUiStyle("material") }
                )
            }

            // About section
            SettingsSectionTitle(stringResource(R.string.settings_about))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                SuperArrow(
                    title = stringResource(R.string.settings_version),
                    summary = "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})"
                )
                SuperArrow(
                    title = stringResource(R.string.settings_about_title),
                    summary = stringResource(R.string.settings_about_desc),
                    onClick = onOpenInstalledModules
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
