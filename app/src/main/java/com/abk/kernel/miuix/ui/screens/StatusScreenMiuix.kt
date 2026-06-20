package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.RunCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun StatusScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {},
) {
    val state by vm.uiState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadRecentRuns() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                navigationIcon = {
                    IconButton(onClick = onToggleRuntimeNavigation) {
                        Icon(
                            imageVector = if (runtimeNavigationEnabled) {
                                Icons.Default.Home
                            } else {
                                Icons.Default.SwapHoriz
                            },
                            contentDescription = stringResource(
                                if (runtimeNavigationEnabled) R.string.nav_home else R.string.nav_status
                            )
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .overScrollVertical()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusHeroCardMiuix(
                    rootGranted = state.rootGranted,
                    currentVersion = BuildConfig.VERSION_NAME,
                    forkRepoName = state.forkRepo?.name,
                    isLoading = state.isLoading,
                    onRequestRoot = { vm.requestRoot() }
                )
            }

            item {
                val ksuVersion = androidx.compose.runtime.remember(state.rootGranted) {
                    if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
                }
                StatusMetricGridMiuix(
                    rootGranted = state.rootGranted,
                    forkReady = state.forkRepo != null && state.behindBy <= 0,
                    ksuVersion = ksuVersion,
                    buildStatus = state.buildStatus
                )
            }

            item {
                BuildStatusCardMiuix(
                    title = stringResource(R.string.status_build),
                    subtitle = stringResource(R.string.status_progress_sync),
                    icon = Icons.Default.RunCircle,
                    status = state.kernelBuildStatus,
                    progress = state.kernelBuildProgress,
                    currentRun = state.kernelCurrentRun,
                    activeRunsCount = state.kernelActiveBuildRuns.size,
                    cancellingRunIds = state.cancellingWorkflowRunIds,
                    onCancel = { run -> vm.cancelWorkflowRun(run.id) }
                )
            }

            if (state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
                item {
                    BuildStatusCardMiuix(
                        title = stringResource(R.string.status_manager_build),
                        subtitle = stringResource(R.string.status_manager_progress_sync),
                        icon = Icons.Default.Shield,
                        status = state.managerBuildStatus,
                        progress = state.managerBuildProgress,
                        currentRun = state.managerCurrentRun,
                        activeRunsCount = state.managerActiveBuildRuns.size,
                        cancellingRunIds = state.cancellingWorkflowRunIds,
                        onCancel = { run -> vm.cancelWorkflowRun(run.id) }
                    )
                }
            }

            item {
                val ksuVersionForRepo = androidx.compose.runtime.remember(state.rootGranted) {
                    if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
                }
                val kernelVersion = androidx.compose.runtime.remember(state.rootGranted) {
                    RootUtils.getKernelVersion()
                }
                DeviceRepoCardMiuix(
                    kernelVersion = kernelVersion,
                    ksuVersion = ksuVersionForRepo,
                    user = state.user,
                    forkRepo = state.forkRepo,
                    behindBy = state.behindBy
                )
            }

            if (state.recentRuns.isNotEmpty()) {
                item {
                    RecentRunsCardMiuix(
                        recentRuns = state.recentRuns.take(5),
                        cancellingRunIds = state.cancellingWorkflowRunIds,
                        onCancel = { run -> vm.cancelWorkflowRun(run.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp + outerPadding.calculateBottomPadding())) }
        }
    }
}

@Composable
private fun StatusHeroCardMiuix(
    rootGranted: Boolean,
    currentVersion: String,
    forkRepoName: String?,
    isLoading: Boolean,
    onRequestRoot: () -> Unit,
) {
    val containerColor = if (rootGranted) {
        MiuixTheme.colorScheme.secondaryContainer
    } else {
        if (isSystemInDarkTheme()) {
            Color(0xFF5C3030)
        } else {
            MiuixTheme.colorScheme.errorContainer
        }
    }
    val contentColor = if (rootGranted) {
        MiuixTheme.colorScheme.onSecondaryContainer
    } else {
        MiuixTheme.colorScheme.onErrorContainer
    }
    val descColor = contentColor.copy(alpha = 0.8f)
    val bgIconTint = if (rootGranted) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MiuixTheme.colorScheme.error.copy(alpha = 0.8f)
    }

    Card(
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(50.dp, 38.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    modifier = Modifier.size(170.dp),
                    imageVector = if (rootGranted) {
                        Icons.Rounded.CheckCircleOutline
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    tint = bgIconTint,
                    contentDescription = null
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 16.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (rootGranted) {
                        stringResource(R.string.status_hero_activated_title)
                    } else {
                        stringResource(R.string.status_hero_deactivated_title)
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(Modifier.height(2.dp))

                val subtitle = if (rootGranted) {
                    val repoPart = if (forkRepoName != null) {
                        stringResource(R.string.status_hero_activated_subtitle_repo_synced)
                    } else {
                        stringResource(R.string.status_no_fork_detected)
                    }
                    "${stringResource(R.string.status_version, currentVersion)} / $repoPart"
                } else {
                    stringResource(R.string.status_version, currentVersion)
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = descColor
                )

                if (rootGranted) {
                    Spacer(Modifier.height(36.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.status_hero_deactivated_hint),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = descColor
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRequestRoot,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = contentColor
                            )
                        } else {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.grant_root))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChipMiuix(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusMetricGridMiuix(
    rootGranted: Boolean,
    forkReady: Boolean,
    ksuVersion: String,
    buildStatus: BuildStatus,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCardMiuix(
                label = "Root",
                value = if (rootGranted) stringResource(R.string.status_authorized) else stringResource(R.string.status_partially_active),
                icon = if (rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                color = if (rootGranted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCardMiuix(
                label = "Fork",
                value = if (forkReady) stringResource(R.string.status_synced) else stringResource(R.string.status_pending_check),
                icon = Icons.Default.ForkRight,
                color = if (forkReady) MiuixTheme.colorScheme.secondary else MiuixTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCardMiuix(
                label = "KernelSU",
                value = if (ksuVersion == "N/A") stringResource(R.string.status_not_detected) else stringResource(R.string.status_detected),
                icon = Icons.Default.Shield,
                color = if (ksuVersion == "N/A") MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCardMiuix(
                label = "Build",
                value = buildStatusDisplayMiuix(buildStatus),
                icon = Icons.Default.RunCircle,
                color = buildStatusColorMiuix(buildStatus),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusMetricCardMiuix(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

private fun buildStatusDisplayMiuix(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> "Idle"
    BuildStatus.QUEUED -> "Queued"
    BuildStatus.IN_PROGRESS -> "In Progress"
    BuildStatus.SUCCESS -> "Success"
    BuildStatus.FAILURE -> "Failure"
    BuildStatus.CANCELLED -> "Cancelled"
}

@Composable
private fun buildStatusColorMiuix(status: BuildStatus): androidx.compose.ui.graphics.Color = when (status) {
    BuildStatus.SUCCESS -> MiuixTheme.colorScheme.primary
    BuildStatus.FAILURE -> MiuixTheme.colorScheme.error
    BuildStatus.IN_PROGRESS -> MiuixTheme.colorScheme.primary
    BuildStatus.CANCELLED -> MiuixTheme.colorScheme.onSurfaceSecondary
    else -> MiuixTheme.colorScheme.onSurfaceSecondary
}

@Composable
private fun BuildStatusCardMiuix(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    status: BuildStatus,
    progress: com.abk.kernel.data.model.BuildProgress,
    currentRun: WorkflowRun?,
    activeRunsCount: Int,
    cancellingRunIds: Set<Long>,
    onCancel: (WorkflowRun) -> Unit,
) {
    val context = LocalContext.current
    val statusColor = buildStatusColorMiuix(status)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    BuildStatus.IDLE -> {
                        Icon(Icons.Default.HourglassEmpty, null, tint = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.status_no_running_build), style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
                    }
                    BuildStatus.QUEUED -> {
                        Icon(Icons.Default.Queue, null, tint = statusColor, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (activeRunsCount > 1) stringResource(R.string.status_parallel_build_waiting_runner, activeRunsCount)
                            else stringResource(R.string.status_build_waiting_runner),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    BuildStatus.IN_PROGRESS -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = statusColor
                        )
                        Text(
                            text = "${progress.percent}% · ${progress.currentStep}",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    BuildStatus.SUCCESS -> {
                        Icon(Icons.Default.CheckCircle, null, tint = statusColor, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.status_recent_build_success), style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
                    }
                    BuildStatus.FAILURE -> {
                        Icon(Icons.Default.Error, null, tint = statusColor, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.status_recent_build_failed), style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.error)
                    }
                    BuildStatus.CANCELLED -> {
                        Icon(Icons.Filled.Cancel, null, tint = statusColor, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.status_build_cancelled), style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurface)
                    }
                }
            }

            val run = currentRun
            if (run != null && progress.totalSteps > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = statusColor,
                    trackColor = MiuixTheme.colorScheme.surface,
                )
                Text(
                    text = stringResource(R.string.status_steps_complete, progress.completedSteps, progress.totalSteps),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            val showSingleRunAction = activeRunsCount <= 1
            if (run != null && showSingleRunAction) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MaterialTextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl)))
                            }
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        MaterialText(stringResource(R.string.status_view_details, run.runNumber))
                    }
                    if (run.status in setOf("queued", "waiting", "requested", "pending", "in_progress")) {
                        val isCancelling = run.id in cancellingRunIds
                        MaterialTextButton(
                            onClick = { onCancel(run) },
                            enabled = !isCancelling,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MiuixTheme.colorScheme.error
                            )
                        ) {
                            if (isCancelling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MiuixTheme.colorScheme.error
                                )
                            } else {
                                Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            MaterialText(
                                text = if (isCancelling) stringResource(R.string.status_cancelling) else stringResource(R.string.status_cancel)
                            )
                        }
                    }
                }
            }

            if (activeRunsCount > 1) {
                Text(
                    text = stringResource(R.string.status_parallel_workflows_desc, activeRunsCount),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
    }
}

@Composable
private fun DeviceRepoCardMiuix(
    kernelVersion: String,
    ksuVersion: String,
    user: com.abk.kernel.data.model.GitHubUser?,
    forkRepo: com.abk.kernel.data.model.GitHubRepo?,
    behindBy: Int,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.status_device_repo_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.status_device_repo_subtitle),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceInfoRowMiuix(
                    icon = Icons.Default.Memory,
                    label = stringResource(R.string.status_kernel),
                    value = kernelVersion,
                    isError = false
                )
                DeviceInfoRowMiuix(
                    icon = Icons.Default.Shield,
                    label = "KSU",
                    value = ksuVersion,
                    isError = ksuVersion == "N/A"
                )
            }

            if (user != null) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                AccountRepositoryRowMiuix(
                    avatarUrl = user.avatarUrl,
                    login = user.login,
                    repository = forkRepo?.name ?: stringResource(R.string.status_no_fork)
                )
            }
            if (behindBy > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.status_fork_behind, behindBy),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRowMiuix(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isError: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Text(
                text = value,
                style = MiuixTheme.textStyles.body1,
                color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountRepositoryRowMiuix(
    avatarUrl: String,
    login: String,
    repository: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = login,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = repository,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentRunsCardMiuix(
    recentRuns: List<WorkflowRun>,
    cancellingRunIds: Set<Long>,
    onCancel: (WorkflowRun) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.status_recent_runs_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.status_recent_runs_subtitle),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentRuns.forEach { run ->
                    RunListItemMiuix(
                        run = run,
                        cancelling = run.id in cancellingRunIds,
                        onCancel = { onCancel(run) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RunListItemMiuix(
    run: WorkflowRun,
    cancelling: Boolean,
    onCancel: () -> Unit,
) {
    val statusDisplay = buildStatusDisplayMiuix(run.status.toBuildStatus())
    val statusColor = when (run.status) {
        "success" -> MiuixTheme.colorScheme.primary
        "failure" -> MiuixTheme.colorScheme.error
        "in_progress" -> MiuixTheme.colorScheme.primary
        "cancelled" -> MiuixTheme.colorScheme.onSurfaceSecondary
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = run.runNumber.toString(),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = run.createdAt,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = statusDisplay,
                style = MiuixTheme.textStyles.body2,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
        }
        if (run.status in setOf("queued", "waiting", "requested", "pending", "in_progress")) {
            IconButton(
                onClick = onCancel,
                enabled = !cancelling,
                modifier = Modifier.size(32.dp)
            ) {
                if (cancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MiuixTheme.colorScheme.error
                    )
                } else {
                    Icon(Icons.Filled.Cancel, stringResource(R.string.status_cancel_workflow), tint = MiuixTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun String.toBuildStatus(): BuildStatus = when (this) {
    "queued", "waiting", "requested", "pending" -> BuildStatus.QUEUED
    "in_progress" -> BuildStatus.IN_PROGRESS
    "success" -> BuildStatus.SUCCESS
    "failure" -> BuildStatus.FAILURE
    "cancelled" -> BuildStatus.CANCELLED
    else -> BuildStatus.IDLE
}
