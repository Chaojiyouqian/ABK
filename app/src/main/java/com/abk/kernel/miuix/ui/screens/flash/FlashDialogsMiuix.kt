package com.abk.kernel.miuix.ui.screens.flash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildParameterSummary
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.ui.screens.flash.ParameterSummarySections
import com.abk.kernel.ui.screens.flash.parameterDisplayValue
import com.abk.kernel.ui.screens.flash.parsePrebuiltGkiParameterSummary
import com.abk.kernel.ui.screens.flash.releaseDateLabel
import com.abk.kernel.ui.screens.flash.WorkflowArtifactGroup
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

// ─────────────────────────────────────────────────────────────────────────────
// 1. Flash confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixFlashConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_confirm),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.flash_confirm_msg),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.flash_confirm),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onConfirm
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Install manager confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixInstallManagerConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_confirm_install_manager),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.flash_confirm_install_manager_msg),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.flash_confirm_install_manager),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onConfirm
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Cancel build confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixCancelBuildConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_cancel_confirm_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.flash_cancel_confirm_msg),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.flash_cancel_confirm_yes),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error
                    ),
                    onClick = onConfirm
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Delete single file confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixDeleteFileDialog(
    artifact: DownloadedArtifact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_delete_file),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.flash_delete_file_msg, artifact.name),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.delete),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error
                    ),
                    onClick = onConfirm
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Delete workflow artifacts dialog (local + optional remote)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixDeleteWorkflowDialog(
    group: WorkflowArtifactGroup,
    hasRemote: Boolean,
    onConfirm: (deleteRemote: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteRemote by remember { mutableStateOf(false) }
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_delete_workflow_record),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val runLabel = if (group.runNumber > 0) {
                        "#${group.runNumber}"
                    } else {
                        "#${group.runId}"
                    }
                    Text(
                        text = stringResource(R.string.flash_delete_workflow_msg, runLabel),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (hasRemote) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteRemote = !deleteRemote },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                state = ToggleableState(deleteRemote),
                                onClick = { deleteRemote = !deleteRemote }
                            )
                            Text(
                                text = stringResource(R.string.flash_delete_remote_workflow),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.delete),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error
                    ),
                    onClick = { onConfirm(deleteRemote) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Dismiss failed run dialog (with optional file deletion)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixDismissFailedRunDialog(
    hasDownloadedFiles: Boolean,
    onConfirm: (deleteFiles: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteFiles by remember(hasDownloadedFiles) { mutableStateOf(hasDownloadedFiles) }
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_dismiss_failed_title),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.flash_dismiss_failed_message),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (hasDownloadedFiles) {
                                    Modifier.clickable { deleteFiles = !deleteFiles }
                                } else {
                                    Modifier
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            state = ToggleableState(deleteFiles),
                            onClick = if (hasDownloadedFiles) {
                                { deleteFiles = !deleteFiles }
                            } else null,
                            enabled = hasDownloadedFiles
                        )
                        Text(
                            text = stringResource(R.string.flash_dismiss_delete_files),
                            style = MiuixTheme.textStyles.body2,
                            color = if (hasDownloadedFiles) {
                                MiuixTheme.colorScheme.onSurface
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.flash_dismiss_confirm),
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error
                    ),
                    onClick = { onConfirm(deleteFiles) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. Build parameter summary dialog (workflow build)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixBuildParameterSummaryDialog(
    summary: BuildParameterSummary,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_parameter_details),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiuixParameterSection(stringResource(R.string.flash_workflow)) {
                        MiuixParameterRow(
                            stringResource(R.string.flash_number),
                            if (summary.runNumber > 0) "#${summary.runNumber}" else "#${summary.runId}"
                        )
                        MiuixParameterRow(stringResource(R.string.flash_title_label), summary.runTitle)
                    }
                    ParameterSummarySections(summary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.close),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onDismiss
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Prebuilt GKI release parameter summary dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixPrebuiltParameterSummaryDialog(
    release: PrebuiltGkiRelease,
    onDismiss: () -> Unit
) {
    val summary = remember(release) { parsePrebuiltGkiParameterSummary(release) }
    WindowDialog(
        show = true,
        title = stringResource(R.string.flash_parameter_details),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiuixParameterSection("Release") {
                        MiuixParameterRow(stringResource(R.string.flash_name), release.name)
                        MiuixParameterRow("Tag", release.tagName)
                        MiuixParameterRow(
                            stringResource(R.string.flash_published_at),
                            releaseDateLabel(
                                release.publishedAt,
                                stringResource(R.string.flash_unknown_date)
                            )
                        )
                        MiuixParameterRow(
                            stringResource(R.string.flash_assets),
                            if (release.assetCount > 0) {
                                stringResource(R.string.flash_asset_count, release.assetCount)
                            } else {
                                stringResource(R.string.flash_unknown)
                            }
                        )
                    }
                    if (summary != null) {
                        ParameterSummarySections(summary)
                    } else {
                        Text(
                            text = stringResource(R.string.flash_release_no_matrix),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.close),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onDismiss
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. Terminal / shell operation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixTerminalDialog(
    title: String,
    running: Boolean,
    success: Boolean?,
    onClose: () -> Unit,
    onReboot: (() -> Unit)?,
    terminalLog: List<String>
) {
    val terminalScroll = rememberScrollState()
    LaunchedEffect(terminalLog.size) {
        terminalScroll.animateScrollTo(terminalScroll.maxValue)
    }
    val surfaceLuminance = MiuixTheme.colorScheme.surface.luminance()
    val isDark = surfaceLuminance < 0.5f
    val darkBg = if (isDark) Color(0xFF303030) else Color(0xFF1A1A1A)
    val textColor = Color.White
    val primaryColor = MiuixTheme.colorScheme.primary

    WindowDialog(
        show = true,
        title = if (running) {
            stringResource(R.string.flash_executing_title, title)
        } else {
            stringResource(R.string.flash_terminal)
        },
        onDismissRequest = { if (!running) onClose() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 190.dp, max = 360.dp)
                            .background(darkBg, RoundedCornerShape(8.dp))
                            .verticalScroll(terminalScroll)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        terminalLog.forEach { line ->
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                color = if (line.startsWith("$")) primaryColor else textColor
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (running) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.flash_executing),
                        enabled = false,
                        onClick = {}
                    )
                } else {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.close),
                        onClick = onClose
                    )
                    if (success == true && onReboot != null) {
                        top.yukonga.miuix.kmp.basic.Button(
                            modifier = Modifier.weight(1f),
                            onClick = onReboot,
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.flash_reboot))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal MIUIX-styled parameter display helpers (avoids MaterialTheme dependency
// from the MD3 ParameterSection/ParameterRow in FlashDialogs.kt)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixParameterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.primary
        )
        content()
        HorizontalDivider()
    }
}

@Composable
private fun MiuixParameterRow(label: String, rawValue: String) {
    val unknownLabel = stringResource(R.string.flash_unknown)
    val enabledLabel = stringResource(R.string.build_feature_enabled)
    val disabledLabel = stringResource(R.string.build_virtualization_off)
    val noneLabel = stringResource(R.string.flash_value_none)
    val defaultLabel = stringResource(R.string.flash_value_default)
    val setLabel = stringResource(R.string.flash_value_set)
    val displayValue = parameterDisplayValue(
        value = rawValue,
        unknown = unknownLabel,
        enabled = enabledLabel,
        disabled = disabledLabel,
        none = noneLabel,
        defaultValue = defaultLabel,
        set = setLabel
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = displayValue,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
