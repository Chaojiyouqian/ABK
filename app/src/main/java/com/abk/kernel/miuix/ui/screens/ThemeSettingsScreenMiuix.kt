package com.abk.kernel.miuix.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ThemeSettingsScreenMiuix(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val state by vm.uiState.collectAsState()
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_color_appearance),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Section 1: UI 风格
            SectionTitleMiuix("UI 风格")
            Card {
                SwitchPreference(
                    title = "MIUIX HyperOS 风格",
                    summary = "使用小米 HyperOS 设计语言（试点）",
                    checked = state.uiStyle == "miuix",
                    onCheckedChange = { enabled ->
                        vm.setUiStyle(if (enabled) "miuix" else "material")
                    }
                )
            }

            // Section 2: 外观模式
            SectionTitleMiuix(stringResource(R.string.settings_appearance_mode))
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_system),
                    summary = "跟随系统",
                    onClick = { vm.setThemeMode("system") }
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_light),
                    summary = "浅色",
                    onClick = { vm.setThemeMode("light") }
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_dark),
                    summary = "深色",
                    onClick = { vm.setThemeMode("dark") }
                )
            }

            // Section 3: 颜色来源
            SectionTitleMiuix(stringResource(R.string.settings_color_source))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.settings_monet),
                    summary = if (dynamicColorAvailable) {
                        stringResource(R.string.settings_monet_desc)
                    } else {
                        stringResource(R.string.settings_monet_unavailable_desc)
                    },
                    checked = dynamicColorAvailable && state.dynamicColorEnabled,
                    onCheckedChange = { enabled ->
                        vm.setDynamicColorEnabled(enabled)
                    }
                )
            }

            // Section 4: 视觉效果
            SectionTitleMiuix("视觉效果")
            Card {
                SwitchPreference(
                    title = "模糊效果",
                    summary = "悬浮底栏的高斯模糊与液态玻璃 backdrop",
                    checked = state.miuixBlurEnabled,
                    onCheckedChange = { vm.setMiuixBlurEnabled(it) }
                )
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "使用悬浮药丸形状的底栏（关闭后使用 Material 3 默认底栏）",
                    checked = state.miuixFloatingBottomBarEnabled,
                    onCheckedChange = { vm.setMiuixFloatingBottomBarEnabled(it) }
                )
                SwitchPreference(
                    title = "液态玻璃",
                    summary = "Liquid Glass 折射与高光效果（实验性）",
                    checked = state.miuixLiquidGlassEnabled,
                    onCheckedChange = { vm.setMiuixLiquidGlassEnabled(it) }
                )
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SectionTitleMiuix(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}
