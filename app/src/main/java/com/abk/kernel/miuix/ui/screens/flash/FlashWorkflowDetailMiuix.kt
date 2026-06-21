package com.abk.kernel.miuix.ui.screens.flash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactCategory
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.BuildProgress
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.data.model.isActive
import com.abk.kernel.data.model.isFailedFlashRun
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.ui.screens.flash.WorkflowArtifactGroup
import com.abk.kernel.ui.screens.flash.artifactCategoryOrder
import com.abk.kernel.ui.screens.flash.buildWorkflowGroups
import com.abk.kernel.ui.screens.flash.emptyWorkflowGroupFor
import com.abk.kernel.ui.screens.flash.flashCommandPreview
import com.abk.kernel.ui.screens.flash.flashOperationLabelRes
import com.abk.kernel.ui.screens.flash.hasArtifactsInCategory
import com.abk.kernel.ui.screens.flash.hasKernelArtifact
import com.abk.kernel.ui.screens.flash.hasManagerArtifact
import com.abk.kernel.ui.screens.flash.isAbkManagerFlashRun
import com.abk.kernel.ui.screens.flash.shouldAppearInWorkflowList
import com.abk.kernel.ui.screens.flash.shouldShowParameterDetails
import com.abk.kernel.ui.screens.flash.sortedForWorkflowDisplay
import com.abk.kernel.utils.BuildProgressUtils
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.FlashWorkflowFilter
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.WorkflowPrimary
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─────────────────────────────────────────────────────────────────────────────
// Workflow Detail Sub-page (MIUIX)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FlashWorkflowDetailScreenMiuix(
    vm: MainViewModel,
    route: Route.FlashWorkflowDetail,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onBack: () -> Unit = {}
) {

    val navigator = LocalNavigator.current
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootGranted = state.rootGranted
    val runId = route.runId

    // ── Derived state mirrors (same logic as FlashScreenMiuix) ──────────
    val unlinkedWorkflowTitle = stringResource(R.string.workflow_unlinked)
    val recentRunById = remember(state.recentRuns, state.sessionGhostFailedRuns) {
        state.recentRuns.associateBy { it.id } + state.sessionGhostFailedRuns
    }
    val remoteArtifacts = remember(state.artifacts, state.isLoggedIn) {
        if (!state.isLoggedIn) {
            emptyList()
        } else {
            state.artifacts.filter {
                !it.expired && DownloadUtils.classifyCategory(
                    DownloadUtils.classifyArtifact(it.name)
                ) != null
            }
        }
    }
    val workflowDownloadedArtifacts = remember(
        state.downloadedArtifacts, state.prebuiltGkiEnabled, state.isLoggedIn
    ) {
        if (!state.isLoggedIn) {
            emptyList()
        } else if (state.prebuiltGkiEnabled) {
            state.downloadedArtifacts.filterNot { it.runId == PREBUILT_GKI_RUN_ID }
        } else {
            state.downloadedArtifacts
        }
    }
    val workflowGroups = remember(
        remoteArtifacts, workflowDownloadedArtifacts, unlinkedWorkflowTitle, recentRunById
    ) {
        buildWorkflowGroups(
            remoteArtifacts, workflowDownloadedArtifacts, unlinkedWorkflowTitle, recentRunById
        )
    }
    val allWorkflowGroups = remember(
        workflowGroups, state.sessionGhostFailedRuns, state.dismissedFailedRunIds, recentRunById
    ) {
        val activeRunIds = state.recentRuns.filter { it.isActive() }.map { it.id }.toSet()
        val extraGroups = activeRunIds
            .filter { id -> workflowGroups.none { it.runId == id } }
            .mapNotNull { id ->
                val run = recentRunById[id] ?: return@mapNotNull null
                emptyWorkflowGroupFor(run, unlinkedWorkflowTitle)
            }
        val ghostRunIds = state.sessionGhostFailedRuns.keys
            .filter { it !in state.dismissedFailedRunIds }
            .toSet()
        val extraGhostGroups = ghostRunIds
            .filter { id -> workflowGroups.none { it.runId == id } && id !in activeRunIds }
            .mapNotNull { id ->
                val run = recentRunById[id] ?: return@mapNotNull null
                emptyWorkflowGroupFor(run, unlinkedWorkflowTitle)
            }
        (workflowGroups + extraGroups + extraGhostGroups)
            .filter { group ->
                if (group.runId in state.dismissedFailedRunIds) return@filter false
                val run = recentRunById[group.runId]
                if (run.isAbkManagerFlashRun(group.runTitle)) return@filter false
                val isGroupActive = run?.isActive() == true
                val isSessionGhost = group.runId in state.sessionGhostFailedRuns
                isGroupActive || isSessionGhost || group.shouldAppearInWorkflowList(run)
            }
            .sortedForWorkflowDisplay(recentRunById)
    }

    val group = allWorkflowGroups.firstOrNull { it.runId == runId }
    val activeRun = recentRunById[runId]?.takeIf { it.isActive() }
    val isCancelling = runId in state.cancellingWorkflowRunIds

    // Keep building page while a cancel is mid-flight so the user keeps
    // seeing the "Cancelling…" spinner instead of bouncing to empty state.
    val keepBuildingForCancel = isCancelling && (group == null || group.remote.isEmpty())
    val buildingRun = activeRun
        ?: if (keepBuildingForCancel) recentRunById[runId] else null
    val showBuilding = buildingRun != null && (activeRun != null || isCancelling)

    // ── Dialog / terminal state ─────────────────────────────────────────
    var cancelConfirmDialog by remember { mutableStateOf(false) }
    var deleteWorkflowTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var parameterTarget by remember { mutableStateOf<WorkflowArtifactGroup?>(null) }
    var deleteFileTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var showInstallManagerConfirm by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var allowLegacyBundleFallback by remember { mutableStateOf(false) }

    var showTerminal by remember { mutableStateOf(false) }
    var terminalTitle by remember { mutableStateOf(context.getString(R.string.flash_terminal)) }
    var terminalCanReboot by remember { mutableStateOf(false) }
    var terminalRunning by remember { mutableStateOf(false) }
    var terminalSuccess by remember { mutableStateOf<Boolean?>(null) }
    var terminalLog by remember { mutableStateOf<List<String>>(emptyList()) }

    // ── LaunchedEffects ─────────────────────────────────────────────────

    // Transition detection: building → completed
    var wasShowingBuilding by remember(runId) { mutableStateOf(false) }
    LaunchedEffect(runId, showBuilding, activeRun?.id) {
        if (wasShowingBuilding && !showBuilding) {
            val finishedRun = recentRunById[runId]
            if (finishedRun?.isFailedFlashRun() != true) {
                val retryWhenEmpty = when (finishedRun?.conclusion) {
                    "cancelled" -> false
                    "success" -> true
                    else -> finishedRun?.status == "completed"
                }
                vm.refreshWorkflowArtifacts(
                    runId,
                    autoDownload = state.autoDownload && retryWhenEmpty,
                    retryWhenEmpty = retryWhenEmpty,
                    force = true,
                )
            }
        }
        wasShowingBuilding = showBuilding
        if (!showBuilding) return@LaunchedEffect
        vm.refreshWorkflowArtifacts(runId)
        while (true) {
            val burstActive = vm.isWorkflowStatusBurstActive(runId)
            delay(if (burstActive) 3_000L else 20_000L)
            if (recentRunById[runId]?.isActive() != true) break
            if (!burstActive) {
                vm.refreshWorkflowArtifacts(runId)
            }
        }
    }

    // Load jobs and failed run log on entry
    LaunchedEffect(runId) {
        vm.loadWorkflowJobs(runId)
        vm.loadFailedRunLogExcerpt(runId)
    }

    // ── Operational callbacks ───────────────────────────────────────────

    fun showFailure(title: String, lines: List<String>) {
        terminalTitle = title
        terminalCanReboot = false
        terminalRunning = false
        terminalSuccess = false
        terminalLog = lines
        showTerminal = true
    }

    fun copyDownloadedFilePath(item: DownloadedArtifact) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.filePath))
        Toast.makeText(
            context, context.getString(R.string.flash_copy_path_done), Toast.LENGTH_SHORT
        ).show()
    }

    fun appendTerminalOutput(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            terminalLog = terminalLog + line
        }
    }

    suspend fun executeWithPreparedArtifact(
        item: DownloadedArtifact,
        allowHighRiskFallback: Boolean = false,
        block: (DownloadUtils.PreparedDownloadedArtifact) -> RootUtils.ShellResult
    ): RootUtils.ShellResult = withContext(Dispatchers.IO) {
        val prepared = DownloadUtils.prepareDownloadedArtifact(
            context = context,
            artifact = item,
            allowHighRiskFallback = allowHighRiskFallback
        )
        try {
            if (prepared.cleanupDir != null) {
                appendTerminalOutput("[ABK] 已解包下载包到缓存目录")
                appendTerminalOutput("[ABK] Payload: ${prepared.file.absolutePath}")
                if (prepared.dependencyModules.isNotEmpty()) {
                    appendTerminalOutput(
                        "[ABK] 附带 Magisk 依赖模块: ${prepared.dependencyModules.joinToString { it.name }}"
                    )
                }
                if (prepared.dependencyApps.isNotEmpty()) {
                    appendTerminalOutput(
                        "[ABK] 附带扩展应用: ${prepared.dependencyApps.joinToString { it.name }}"
                    )
                }
            }
            block(prepared)
        } finally {
            prepared.cleanupDir?.deleteRecursively()
        }
    }

    fun installManager(item: DownloadedArtifact) {
        if (!rootGranted) {
            showFailure(
                context.getString(R.string.flash_root_unauthorized),
                listOf(
                    "$ pm install -r ${item.name}",
                    context.getString(R.string.flash_partial_files_only),
                    context.getString(R.string.flash_grant_root_install_manager)
                )
            )
            return
        }
        terminalTitle = context.getString(R.string.flash_install_manager_apk)
        terminalCanReboot = false
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "$ pm install -r ${item.name}",
            "file: ${item.filePath}",
            "",
            context.getString(R.string.flash_wait_root_shell)
        )
        showTerminal = true
        scope.launch {
            val result = runCatching {
                executeWithPreparedArtifact(item) { prepared ->
                    RootUtils.installApk(context, prepared.file.absolutePath, ::appendTerminalOutput)
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "$ pm install -r ${item.name}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.flash_command_done_no_output)
                    } else {
                        context.getString(R.string.flash_command_failed_no_log)
                    }
                )
            }
        }
    }

    fun startFlash(
        item: DownloadedArtifact,
        anyKernelSlotTarget: RootUtils.Ak3SlotTarget = RootUtils.Ak3SlotTarget.CURRENT,
        allowHighRiskFallback: Boolean = false
    ) {
        if (!rootGranted) {
            showFailure(
                context.getString(R.string.flash_root_unauthorized),
                listOf(
                    "$ ${flashCommandPreview(item, anyKernelSlotTarget)}",
                    context.getString(R.string.flash_partial_files_only),
                    context.getString(R.string.flash_grant_root_flash)
                )
            )
            return
        }
        terminalTitle = context.getString(flashOperationLabelRes(item.type))
        terminalCanReboot = true
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "$ ${flashCommandPreview(item, anyKernelSlotTarget)}",
            "file: ${item.filePath}",
            "",
            context.getString(R.string.flash_wait_root_shell)
        )
        showTerminal = true
        scope.launch {
            val result = runCatching {
                executeWithPreparedArtifact(item, allowHighRiskFallback) { prepared ->
                    val flashType = prepared.resolvedType ?: item.type
                    if (flashType == ArtifactType.KERNEL_IMG || flashType == ArtifactType.ANYKERNEL3) {
                        prepared.dependencyApps.forEach { dependency ->
                            appendTerminalOutput("[ABK] 先安装依赖扩展应用: ${dependency.name}")
                            val dependencyResult = RootUtils.installApk(
                                context, dependency.absolutePath, ::appendTerminalOutput
                            )
                            if (!dependencyResult.success) {
                                return@executeWithPreparedArtifact dependencyResult
                            }
                        }
                        prepared.dependencyModules.forEach { dependency ->
                            appendTerminalOutput("[ABK] 先安装依赖模块: ${dependency.name}")
                            val dependencyResult = RootUtils.installModule(
                                dependency.absolutePath, ::appendTerminalOutput
                            )
                            if (!dependencyResult.success) {
                                return@executeWithPreparedArtifact dependencyResult
                            }
                        }
                    }
                    when (flashType) {
                        ArtifactType.KERNEL_IMG -> RootUtils.flashImage(
                            prepared.file.absolutePath, onOutput = ::appendTerminalOutput
                        )
                        ArtifactType.ANYKERNEL3 -> RootUtils.flashAnyKernel3(
                            context,
                            prepared.file.absolutePath,
                            targetSlot = anyKernelSlotTarget,
                            onOutput = ::appendTerminalOutput
                        )
                        ArtifactType.SUSFS_MODULE -> RootUtils.installModule(
                            prepared.file.absolutePath, ::appendTerminalOutput
                        )
                        ArtifactType.KSU_MANAGER -> RootUtils.installApk(
                            context, prepared.file.absolutePath, ::appendTerminalOutput
                        )
                        ArtifactType.ABK_MANAGER ->
                            RootUtils.ShellResult(false, listOf(context.getString(R.string.flash_unsupported_auto_flash)))
                        else ->
                            RootUtils.ShellResult(false, listOf(context.getString(R.string.flash_unsupported_auto_flash)))
                    }
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
            }
            allowLegacyBundleFallback = false
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "$ ${flashCommandPreview(item, anyKernelSlotTarget)}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.flash_command_done_no_output)
                    } else {
                        context.getString(R.string.flash_command_failed_no_log)
                    }
                )
            }
        }
    }

    fun requestFlash(item: DownloadedArtifact) {
        selectedItem = item
        allowLegacyBundleFallback = false
        showFlashConfirm = true
    }

    fun requestInstallManager(item: DownloadedArtifact) {
        selectedItem = item
        showInstallManagerConfirm = true
    }

    // ── TopAppBar title ─────────────────────────────────────────────────
    val detailRun = recentRunById[runId]
    val runNumber = detailRun?.runNumber ?: 0
    val titleText = if (runId == PREBUILT_GKI_RUN_ID) {
        stringResource(R.string.flash_prebuilt_gki)
    } else {
        stringResource(
            R.string.flash_workflow_label,
            if (runNumber > 0) "#$runNumber" else "#$runId"
        )
    }

    // ── UI ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = titleText,
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.flash_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showBuilding && buildingRun != null) {
                // ── Building state ──────────────────────────────────────
                val progress: BuildProgress = state.buildProgressByRunId[runId]
                    ?: BuildProgressUtils.defaultFor(buildingRun)
                item("building-header") {
                    MiuixBuildingStateCard(
                        run = buildingRun,
                        progress = progress,
                        cancelling = isCancelling,
                        onCancel = { cancelConfirmDialog = true }
                    )
                }

                // Category progress cards
                val isPureManager = FlashWorkflowFilter.isPureManagerBuild(buildingRun)
                val visibleCategories = if (isPureManager) {
                    listOf(ArtifactCategory.MANAGER)
                } else {
                    artifactCategoryOrder
                }
                val workflowGroup = group
                    ?: emptyWorkflowGroupFor(buildingRun, unlinkedWorkflowTitle)
                visibleCategories.forEach { category ->
                    item("building-category-${category.name}") {
                        val remoteInCategory = workflowGroup.remote.filter {
                            DownloadUtils.classifyCategory(
                                DownloadUtils.classifyArtifact(it.name)
                            ) == category
                        }
                        val matchedLocalPaths = remoteInCategory
                            .flatMap { source ->
                                workflowGroup.local.filter {
                                    DownloadUtils.matchesDownloadedArtifact(it, source)
                                }
                            }
                            .map { it.filePath }
                            .toSet()
                        val localOnly = workflowGroup.local.filter {
                            it.category == category && it.filePath !in matchedLocalPaths
                        }
                        if (remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()) {
                            MiuixWorkflowCategorySection(
                                category = category,
                                sourceArtifacts = remoteInCategory,
                                localOnlyArtifacts = localOnly,
                                matchedLocalBySource = { artifact ->
                                    workflowGroup.local.filter {
                                        DownloadUtils.matchesDownloadedArtifact(it, artifact)
                                    }
                                },
                                downloadProgress = state.downloadProgress,
                                autoDownload = state.autoDownload,
                                pendingAutoDownloadRunId = state.pendingAutoDownloadRunId,
                                showDownloadCancelActions = true,
                                onDownload = vm::downloadArtifact,
                                onCancelDownload = vm::cancelDownload,
                                onCancelAutoDownload = vm::cancelAutoDownloads,
                                onCopyPath = ::copyDownloadedFilePath,
                                onInstall = ::requestInstallManager,
                                onFlash = ::requestFlash,
                                onDelete = { deleteFileTarget = it },
                                allowRootActions = rootGranted
                            )
                        } else {
                            MiuixCategoryProgressCard(progress = progress)
                        }
                    }
                }
            } else if (group != null) {
                // ── Completed state ─────────────────────────────────────
                val isManagerPrimary = FlashWorkflowFilter.primaryKind(
                    run = detailRun,
                    runTitle = group.runTitle,
                    hasKernelArtifact = group.hasKernelArtifact(),
                    hasManagerArtifact = group.hasManagerArtifact()
                ) == WorkflowPrimary.Manager
                val showParameterDetails = group.shouldShowParameterDetails(detailRun)

                item("completed-header") {
                    MiuixWorkflowDetailHeader(
                        group = group,
                        showParameterDetails = showParameterDetails,
                        onShowParameters = {
                            if (showParameterDetails) parameterTarget = group
                        },
                        onDelete = { deleteWorkflowTarget = group }
                    )
                }

                val visibleCategories = if (isManagerPrimary) {
                    listOf(ArtifactCategory.MANAGER)
                } else {
                    artifactCategoryOrder
                }
                visibleCategories.forEach { category ->
                    val remoteInCategory = group.remote.filter {
                        DownloadUtils.classifyCategory(
                            DownloadUtils.classifyArtifact(it.name)
                        ) == category
                    }
                    val matchedLocalPaths = remoteInCategory
                        .flatMap { source ->
                            group.local.filter {
                                DownloadUtils.matchesDownloadedArtifact(it, source)
                            }
                        }
                        .map { it.filePath }
                        .toSet()
                    val localOnly = group.local.filter {
                        it.category == category && it.filePath !in matchedLocalPaths
                    }

                    if (remoteInCategory.isNotEmpty() || localOnly.isNotEmpty()) {
                        item("category-${group.runId}-${category.name}") {
                            MiuixWorkflowCategorySection(
                                category = category,
                                sourceArtifacts = remoteInCategory,
                                localOnlyArtifacts = localOnly,
                                matchedLocalBySource = { artifact ->
                                    group.local.filter {
                                        DownloadUtils.matchesDownloadedArtifact(it, artifact)
                                    }
                                },
                                downloadProgress = state.downloadProgress,
                                autoDownload = state.autoDownload,
                                pendingAutoDownloadRunId = state.pendingAutoDownloadRunId,
                                showDownloadCancelActions = true,
                                onDownload = vm::downloadArtifact,
                                onCancelDownload = vm::cancelDownload,
                                onCancelAutoDownload = vm::cancelAutoDownloads,
                                onCopyPath = ::copyDownloadedFilePath,
                                onInstall = ::requestInstallManager,
                                onFlash = ::requestFlash,
                                onDelete = { deleteFileTarget = it },
                                allowRootActions = rootGranted
                            )
                        }
                    }
                }
            } else {
                item("empty") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = stringResource(R.string.flash_workflow_unavailable),
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.flash_workflow_unavailable_desc),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }

            item("bottom-spacer") {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────

    if (cancelConfirmDialog) {
        MiuixCancelBuildConfirmDialog(
            onConfirm = {
                cancelConfirmDialog = false
                vm.cancelWorkflowRun(runId)
            },
            onDismiss = { cancelConfirmDialog = false }
        )
    }

    deleteWorkflowTarget?.let { target ->
        MiuixDeleteWorkflowDialog(
            group = target,
            hasRemote = target.remote.isNotEmpty(),
            onConfirm = { deleteRemote ->
                val targetRunId = target.runId
                vm.deleteWorkflowArtifacts(targetRunId, deleteRemote)
                deleteWorkflowTarget = null
                onBack()
            },
            onDismiss = { deleteWorkflowTarget = null }
        )
    }

    parameterTarget?.let { target ->
        val run = recentRunById[target.runId]
        if (!target.shouldShowParameterDetails(run)) {
            LaunchedEffect(target.runId) { parameterTarget = null }
        } else {
            val targetRunId = target.runId
            LaunchedEffect(targetRunId) {
                vm.loadBuildParameterSummary(targetRunId)
            }
            val summary = state.buildParameterSummaries[targetRunId]
            if (summary != null) {
                MiuixBuildParameterSummaryDialog(
                    summary = summary,
                    onDismiss = { parameterTarget = null }
                )
            }
        }
    }

    deleteFileTarget?.let { artifact ->
        MiuixDeleteFileDialog(
            artifact = artifact,
            onConfirm = {
                vm.deleteDownloadedArtifact(artifact.filePath)
                deleteFileTarget = null
            },
            onDismiss = { deleteFileTarget = null }
        )
    }

    if (showFlashConfirm) {
        MiuixFlashConfirmDialog(
            onConfirm = {
                showFlashConfirm = false
                val item = selectedItem
                if (item != null) startFlash(item, allowHighRiskFallback = allowLegacyBundleFallback)
            },
            onDismiss = { showFlashConfirm = false }
        )
    }

    if (showInstallManagerConfirm) {
        MiuixInstallManagerConfirmDialog(
            onConfirm = {
                showInstallManagerConfirm = false
                val item = selectedItem
                if (item != null) installManager(item)
            },
            onDismiss = { showInstallManagerConfirm = false }
        )
    }

    if (showTerminal) {
        MiuixTerminalDialog(
            title = terminalTitle,
            running = terminalRunning,
            success = terminalSuccess,
            onClose = { showTerminal = false },
            onReboot = if (terminalSuccess == true && terminalCanReboot) {
                { scope.launch { RootUtils.reboot() } }
            } else {
                null
            },
            terminalLog = terminalLog
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Building State Card (MIUIX)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixBuildingStateCard(
    run: WorkflowRun,
    progress: BuildProgress,
    cancelling: Boolean,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    progress = null,
                    size = 22.dp,
                    strokeWidth = 2.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.displayTitle ?: run.name.orEmpty(),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (cancelling) {
                            stringResource(R.string.flash_cancelling_subtitle)
                        } else {
                            stringResource(R.string.flash_building_subtitle)
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // Progress bar
            if (progress.totalSteps > 0) {
                val animatedProgress = (progress.percent / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = animatedProgress,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surface
                    )
                )
                Text(
                    text = "${progress.percent}% · ${progress.currentStep}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = null,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surface
                    )
                )
            }

            // Cancel button
            Button(
                onClick = onCancel,
                enabled = !cancelling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                if (cancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        progress = null,
                        size = 18.dp,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.flash_cancel_build))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category Progress Card (MIUIX)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MiuixCategoryProgressCard(progress: BuildProgress?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    progress = null,
                    size = 20.dp,
                    strokeWidth = 2.dp
                )
                Text(
                    text = if (progress != null && progress.totalSteps > 0) {
                        "${progress.percent}% · ${progress.currentStep}"
                    } else {
                        stringResource(R.string.flash_building_subtitle)
                    },
                    style = MiuixTheme.textStyles.main,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (progress != null && progress.totalSteps > 0) {
                val animatedProgress = (progress.percent / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = animatedProgress,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surface
                    )
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = null,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Workflow Detail Header (MIUIX — Completed state)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixWorkflowDetailHeader(
    group: WorkflowArtifactGroup,
    showParameterDetails: Boolean,
    onShowParameters: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (group.runId == PREBUILT_GKI_RUN_ID) {
                            stringResource(R.string.flash_prebuilt_gki)
                        } else {
                            stringResource(
                                R.string.flash_workflow_label,
                                if (group.runNumber > 0) "#${group.runNumber}" else "#${group.runId}"
                            )
                        },
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = group.runTitle,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Artifact counts
            Text(
                text = stringResource(
                    R.string.flash_artifact_counts, group.remote.size, group.local.size
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showParameterDetails) {
                    Button(
                        onClick = onShowParameters,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        minWidth = 0.dp,
                        minHeight = 0.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.flash_parameter_details),
                            fontSize = 12.sp
                        )
                    }
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MiuixTheme.colorScheme.error
                    ),
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    minWidth = 0.dp,
                    minHeight = 0.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.flash_delete_workflow),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
