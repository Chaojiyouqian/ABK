package com.abk.kernel.miuix.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.WaterDrop
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
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
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
            Card {
                val uiStyleOptions = listOf("material" to "Material 3", "miuix" to "MIUIX HyperOS")
                val uiStyleIndex = if (state.uiStyle == "miuix") 1 else 0
                OverlayDropdownPreference(
                    title = "UI 风格",
                    items = uiStyleOptions.map { it.second },
                    selectedIndex = uiStyleIndex,
                    onSelectedIndexChange = { index ->
                        vm.setUiStyle(uiStyleOptions[index].first)
                    }
                )
            }

            // Section 2: 外观模式
            Card {
                val themeModeOptions = listOf(
                    "system" to stringResource(R.string.settings_theme_system),
                    "light" to stringResource(R.string.settings_theme_light),
                    "dark" to stringResource(R.string.settings_theme_dark)
                )
                val themeModeIndex = themeModeOptions.indexOfFirst {
                    it.first == state.themeMode
                }.takeIf { it >= 0 } ?: 0
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_appearance_mode),
                    items = themeModeOptions.map { it.second },
                    selectedIndex = themeModeIndex,
                    onSelectedIndexChange = { index ->
                        vm.setThemeMode(themeModeOptions[index].first)
                    }
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
                    title = "模糊",
                    summary = "启用顶栏和底栏的模糊效果",
                    startAction = {
                        Icon(
                            Icons.Rounded.BlurOn,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "模糊",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    },
                    checked = state.miuixBlurEnabled,
                    onCheckedChange = { vm.setMiuixBlurEnabled(it) }
                )
                SwitchPreference(
                    title = "悬浮底栏",
                    summary = "使用 Apple 风格的悬浮底栏",
                    startAction = {
                        Icon(
                            Icons.Rounded.CallToAction,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "悬浮底栏",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    },
                    checked = state.miuixFloatingBottomBarEnabled,
                    onCheckedChange = { vm.setMiuixFloatingBottomBarEnabled(it) }
                )
                AnimatedVisibility(visible = state.miuixFloatingBottomBarEnabled) {
                    SwitchPreference(
                        title = "液态玻璃",
                        summary = "启用悬浮底栏的液态玻璃效果",
                        startAction = {
                            Icon(
                                Icons.Rounded.WaterDrop,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "液态玻璃",
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        },
                        checked = state.miuixLiquidGlassEnabled,
                        onCheckedChange = { vm.setMiuixLiquidGlassEnabled(it) }
                    )
                }
            }

            // Section 5: 导航（MIUIX-only，仅控制 NavDisplay 的预测返回手势；MD3 的 PredictiveChildPageBack 由 SettingsScreen 的独立开关控制）
            SectionTitleMiuix("导航")
            Card {
                SwitchPreference(
                    title = "预测性返回手势",
                    summary = "启用边缘滑动返回预览和自定义页面关闭动画",
                    startAction = {
                        Icon(
                            Icons.Default.ArrowBack,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "预测性返回手势",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    },
                    checked = state.miuixPredictiveBackEnabled,
                    onCheckedChange = { vm.setMiuixPredictiveBackEnabled(it) }
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
