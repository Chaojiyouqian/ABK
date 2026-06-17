package com.abk.kernel.miuix.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun AbkMiuixTheme(
    themeMode: String = "system",
    dynamicColorEnabled: Boolean = true,
    customThemeColorArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamicColor = dynamicColorEnabled && dynamicColorAvailable

    val mode = when {
        useDynamicColor -> when {
            darkTheme -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetLight
        }
        else -> when {
            darkTheme -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.Light
        }
    }

    val keyColor = customThemeColorArgb?.let { Color(it) }
        ?: Color(0xFF3DDC84) // ABK green seed

    val controller = remember(mode, keyColor) {
        ThemeController(mode, keyColor = keyColor)
    }

    MiuixTheme(controller = controller, content = content)
}
