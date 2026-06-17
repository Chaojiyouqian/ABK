package com.abk.kernel.miuix.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Composable
@ReadOnlyComposable
fun isMiuixDarkTheme(): Boolean = isSystemInDarkTheme()

val LocalEnableFloatingBottomBar = staticCompositionLocalOf { true }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { true }
