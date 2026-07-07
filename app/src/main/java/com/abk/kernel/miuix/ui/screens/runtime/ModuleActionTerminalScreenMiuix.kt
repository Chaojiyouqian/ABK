package com.abk.kernel.miuix.ui.screens.runtime

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.miuix.component.KeyEventBlocker
import com.abk.kernel.miuix.ui.screens.flash.common.rememberFlashTerminalLogState
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import com.abk.kernel.viewmodel.RuntimeModuleActionBackend
import com.abk.kernel.viewmodel.preferredActionBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

// ─────────────────────────────────────────────────────────────────────────────
// ModuleActionTerminalScreenMiuix — full-page terminal log for module action execution
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ModuleActionTerminalScreenMiuix(
    params: ModuleActionTerminalParams,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logState = rememberFlashTerminalLogState()
    val executionStarted = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val fabVisible by remember {
        var previousScroll = 0
        var scrollDelta = 0f
        var visible = true
        derivedStateOf {
            val currentScroll = scrollState.value
            val delta = (currentScroll - previousScroll).toFloat()
            scrollDelta = (scrollDelta + delta).coerceIn(-FAB_SCROLL_THRESHOLD_PX, FAB_SCROLL_THRESHOLD_PX)
            previousScroll = currentScroll
            if (currentScroll <= 0) {
                visible = scrollState.maxValue <= 0
                scrollDelta = 0f
            } else if (!visible && scrollDelta >= FAB_SCROLL_THRESHOLD_PX) {
                visible = true
                scrollDelta = 0f
            } else if (visible && scrollDelta <= -FAB_SCROLL_THRESHOLD_PX) {
                visible = false
                scrollDelta = 0f
            }
            visible
        }
    }
    val closeOffset by animateDpAsState(
        targetValue = if (fabVisible) 0.dp else 180.dp,
        animationSpec = tween(durationMillis = 350),
        label = "module-action-fab-offset"
    )

    // ── Pre-resolve string resources (cannot call stringResource inside LaunchedEffect) ──
    val flashWaitRootShell = stringResource(R.string.runtime_wait_root_shell)
    val flashStatusFailed = stringResource(R.string.flash_terminal_status_failed)
    val flashStatusSuccess = stringResource(R.string.flash_terminal_status_success)
    val flashBack = stringResource(R.string.flash_back)
    val close = stringResource(R.string.close)

    // ── Key event blocker (volume keys) ─────────────────────────────────────
    KeyEventBlocker {
        it.key == Key.VolumeDown || it.key == Key.VolumeUp
    }

    // ── Auto scroll ─────────────────────────────────────────────────────────
    LaunchedEffect(logState.logText) {
        if (logState.logText.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // ── Execution ───────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (executionStarted.value) return@LaunchedEffect
        executionStarted.value = true
        logState.setRunning()

        logState.appendLine("\$ module action: ${params.moduleName}")
        logState.appendLine("")

        val result = withContext(NonCancellable + Dispatchers.IO) {
            logState.appendLine(flashWaitRootShell)
            if (!RootUtils.refreshRootState()) {
                RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_manager_inactive)))
            } else {
                val module = vm.uiState.value.abkRuntimeStatus?.modules?.firstOrNull { it.id == params.moduleId }
                val backend = module?.preferredActionBackend() ?: RuntimeModuleActionBackend.NONE
                when (backend) {
                    RuntimeModuleActionBackend.ABK_ACTION_SCRIPT,
                    RuntimeModuleActionBackend.NONE -> {
                        RootUtils.runModuleActionScript(params.moduleDir) { line ->
                            logState.appendLine(line)
                        }
                    }
                    RuntimeModuleActionBackend.KSU_ACTION -> {
                        RootUtils.runKsuModuleAction(params.moduleId) { line ->
                            logState.appendLine(line)
                        }
                    }
                }
            }
        }

        if (result.success) {
            logState.setSuccess()
            vm.refreshAbkRuntimeStatus()
        } else {
            logState.setFailed(result.output.joinToString("\n"))
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = when {
                    !logState.isSuccess && logState.isCompleted -> flashStatusFailed
                    logState.isSuccess -> flashStatusSuccess
                    else -> params.moduleName
                },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = onBack
                    ) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = flashBack,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (logState.isCompleted) {
                FloatingActionButton(
                    onClick = onBack,
                    modifier = Modifier
                        .offset { IntOffset(0, closeOffset.roundToPx()) }
                        .padding(bottom = 20.dp, end = 20.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = close
                    )
                }
            }
        },
        popupHost = { }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .scrollEndHaptic()
                .verticalScroll(scrollState)
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                text = logState.logText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

private const val FAB_SCROLL_THRESHOLD_PX = 100f
