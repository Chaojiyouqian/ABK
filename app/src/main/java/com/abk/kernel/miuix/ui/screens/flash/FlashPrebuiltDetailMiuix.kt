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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.ArtifactType
import com.abk.kernel.data.model.DownloadedArtifact
import com.abk.kernel.data.model.KernelSupport
import com.abk.kernel.data.model.PREBUILT_GKI_RUN_ID
import com.abk.kernel.data.model.PrebuiltGkiAsset
import com.abk.kernel.data.model.PrebuiltGkiRelease
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.ui.screens.flash.defaultPrebuiltFilter
import com.abk.kernel.ui.screens.flash.prebuiltAssetMatchesFilter
import com.abk.kernel.ui.screens.flash.prebuiltSubLevelOptions
import com.abk.kernel.ui.screens.flash.releaseDateLabel
import com.abk.kernel.ui.screens.flash.sanitizePrebuiltFilter
import com.abk.kernel.utils.DownloadUtils
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.abk.kernel.ui.screens.flash.PrebuiltGkiFilter

// ─────────────────────────────────────────────────────────────────────────────
// FlashPrebuiltDetailScreenMiuix — Navigation3 sub-page for prebuilt GKI detail
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FlashPrebuiltDetailScreenMiuix(
    vm: MainViewModel,
    route: Route.FlashPrebuiltDetail,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onBack: () -> Unit = {}
) {

    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.uiState.collectAsState()

    val releaseId = route.releaseId
    val release = state.prebuiltGkiReleases.firstOrNull { it.id == releaseId }
    val assets = state.prebuiltGkiAssetsByReleaseId[releaseId].orEmpty()
    val assetsLoading = releaseId in state.loadingPrebuiltGkiAssetReleaseIds

    var filter by remember { mutableStateOf(defaultPrebuiltFilter()) }

    val filteredAssets = remember(assets, filter) {
        if (assets.isEmpty()) emptyList()
        else assets.filter { prebuiltAssetMatchesFilter(it, filter) }
    }

    // ── Dialog / operation state ─────────────────────────────────────────
    var parameterTarget by remember { mutableStateOf<PrebuiltGkiRelease?>(null) }
    var deleteTarget by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var pendingArtifact by remember { mutableStateOf<DownloadedArtifact?>(null) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    var showInstallConfirm by remember { mutableStateOf(false) }

    var showTerminal by remember { mutableStateOf(false) }
    var terminalTitle by remember { mutableStateOf("") }
    var terminalRunning by remember { mutableStateOf(false) }
    var terminalSuccess by remember { mutableStateOf<Boolean?>(null) }
    var terminalLog by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(release) {
        val r = release ?: return@LaunchedEffect
        if (!state.prebuiltGkiAssetsByReleaseId.containsKey(releaseId)) {
            vm.loadPrebuiltGkiAssets(r)
        }
    }

    // ── Local helpers ────────────────────────────────────────────────────

    fun appendLine(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            terminalLog = terminalLog + line
        }
    }

    fun copyPath(item: DownloadedArtifact) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(item.name, item.filePath))
        Toast.makeText(context, context.getString(R.string.flash_copy_path_done), Toast.LENGTH_SHORT).show()
    }

    fun requestFlash(item: DownloadedArtifact) {
        pendingArtifact = item
        showFlashConfirm = true
    }

    fun startFlash(item: DownloadedArtifact) {
        if (!state.rootGranted) {
            terminalTitle = context.getString(R.string.flash_root_unauthorized)
            terminalRunning = false
            terminalSuccess = false
            terminalLog = listOf(
                context.getString(R.string.flash_partial_files_only),
                context.getString(R.string.flash_grant_root_flash)
            )
            showTerminal = true
            return
        }
        terminalTitle = context.getString(R.string.flash_operation_flash_boot)
        terminalRunning = true
        terminalSuccess = null
        terminalLog = listOf(
            "$ flash ${item.name}",
            "file: ${item.filePath}",
            "",
            context.getString(R.string.flash_wait_root_shell)
        )
        showTerminal = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val prepared = DownloadUtils.prepareDownloadedArtifact(context, item)
                    try {
                        if (prepared.cleanupDir != null) {
                            appendLine("[ABK] Extracted to cache")
                            appendLine("[ABK] Payload: ${prepared.file.absolutePath}")
                        }
                        when (prepared.resolvedType ?: item.type) {
                            ArtifactType.KERNEL_IMG ->
                                RootUtils.flashImage(prepared.file.absolutePath, onOutput = ::appendLine)
                            ArtifactType.ANYKERNEL3 ->
                                RootUtils.flashAnyKernel3(context, prepared.file.absolutePath, onOutput = ::appendLine)
                            ArtifactType.SUSFS_MODULE ->
                                RootUtils.installModule(prepared.file.absolutePath, ::appendLine)
                            ArtifactType.KSU_MANAGER ->
                                RootUtils.installApk(context, prepared.file.absolutePath, ::appendLine)
                            else ->
                                RootUtils.ShellResult(false, listOf("unsupported: ${item.type}"))
                        }
                    } finally {
                        prepared.cleanupDir?.deleteRecursively()
                    }
                }
            }.getOrElse { error ->
                RootUtils.ShellResult(false, listOf(error.message ?: error::class.java.simpleName))
            }
            terminalRunning = false
            terminalSuccess = result.success
            terminalLog = listOf(
                "$ flash ${item.name}",
                "file: ${item.filePath}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) context.getString(R.string.flash_command_done_no_output)
                    else context.getString(R.string.flash_command_failed_no_log)
                )
            }
        }
    }

    fun requestInstall(item: DownloadedArtifact) {
        pendingArtifact = item
        showInstallConfirm = true
    }

    fun installManager(item: DownloadedArtifact) {
        if (!state.rootGranted) {
            terminalTitle = context.getString(R.string.flash_root_unauthorized)
            terminalRunning = false
            terminalSuccess = false
            terminalLog = listOf(
                context.getString(R.string.flash_partial_files_only),
                context.getString(R.string.flash_grant_root_install_manager)
            )
            showTerminal = true
            return
        }
        terminalTitle = context.getString(R.string.flash_install_manager_apk)
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
                withContext(Dispatchers.IO) {
                    val prepared = DownloadUtils.prepareDownloadedArtifact(context, item)
                    try {
                        if (prepared.cleanupDir != null) {
                            appendLine("[ABK] Extracted to cache")
                            appendLine("[ABK] Payload: ${prepared.file.absolutePath}")
                        }
                        RootUtils.installApk(context, prepared.file.absolutePath, ::appendLine)
                    } finally {
                        prepared.cleanupDir?.deleteRecursively()
                    }
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
                    if (result.success) context.getString(R.string.flash_command_done_no_output)
                    else context.getString(R.string.flash_command_failed_no_log)
                )
            }
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.flash_prebuilt_gki),
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Release info ─────────────────────────────────────────
            if (release != null) {
                item(key = "release_info") {
                    MiuixPrebuiltReleaseInfoCard(release = release, assetCount = assets.size) {
                        parameterTarget = release
                    }
                }
            }

            // ── Filter ───────────────────────────────────────────────
            item(key = "filter") {
                MiuixPrebuiltFilterCard(
                    filter = filter,
                    onFilterChange = { filter = sanitizePrebuiltFilter(it) }
                )
            }

            // ── Assets ───────────────────────────────────────────────
            if (assetsLoading && assets.isEmpty()) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            progress = null,
                            size = 24.dp,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.flash_loading_release),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else if (filteredAssets.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (assets.isEmpty()) {
                                stringResource(R.string.flash_asset_load_later)
                            } else {
                                stringResource(R.string.flash_no_matching_assets)
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else {
                item(key = "count_header") {
                    Text(
                        text = stringResource(R.string.flash_asset_count, filteredAssets.size),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(
                    items = filteredAssets,
                    key = { it.id }
                ) { asset: PrebuiltGkiAsset ->
                    val downloadedFiles = state.downloadedArtifacts.filter {
                        it.runId == PREBUILT_GKI_RUN_ID && it.name == asset.name
                    }
                    val progressKey = DownloadUtils.prebuiltProgressKey(asset.id)
                    val progress = state.downloadProgress[progressKey]

                    MiuixPrebuiltAssetCard(
                        asset = asset,
                        downloadedFiles = downloadedFiles,
                        progress = progress,
                        onDownload = { vm.downloadPrebuiltGki(asset) },
                        onCopyPath = { file -> copyPath(file) },
                        onFlash = { file -> requestFlash(file) },
                        onInstall = { file -> requestInstall(file) },
                        onDelete = { file -> deleteTarget = file }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    parameterTarget?.let { rel ->
        MiuixPrebuiltParameterSummaryDialog(
            release = rel,
            onDismiss = { parameterTarget = null }
        )
    }

    deleteTarget?.let { artifact ->
        MiuixDeleteFileDialog(
            artifact = artifact,
            onConfirm = {
                vm.deleteDownloadedArtifact(artifact.filePath)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showFlashConfirm) {
        MiuixFlashConfirmDialog(
            onConfirm = {
                showFlashConfirm = false
                pendingArtifact?.let { startFlash(it) }
            },
            onDismiss = {
                showFlashConfirm = false
                pendingArtifact = null
            }
        )
    }

    if (showInstallConfirm) {
        MiuixInstallManagerConfirmDialog(
            onConfirm = {
                showInstallConfirm = false
                pendingArtifact?.let { installManager(it) }
            },
            onDismiss = {
                showInstallConfirm = false
                pendingArtifact = null
            }
        )
    }

    if (showTerminal) {
        MiuixTerminalDialog(
            title = terminalTitle,
            running = terminalRunning,
            success = terminalSuccess,
            onClose = {
                showTerminal = false
                terminalLog = emptyList()
            },
            onReboot = if (terminalSuccess == true) {
                { scope.launch { RootUtils.reboot() } }
            } else {
                null
            },
            terminalLog = terminalLog
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiuixPrebuiltReleaseInfoCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixPrebuiltReleaseInfoCard(
    release: PrebuiltGkiRelease,
    assetCount: Int,
    onParameterClick: () -> Unit
) {
    val unknownDate = stringResource(R.string.flash_unknown_date)
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = release.name,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 32.dp)
            ) {
                Text(
                    text = release.tagName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = "·",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = releaseDateLabel(release.publishedAt, unknownDate),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.weight(1f))
                MiuixTagChip(
                    label = stringResource(R.string.flash_asset_count, assetCount),
                    primary = false
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = stringResource(R.string.flash_parameter_details),
                    onClick = onParameterClick
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiuixPrebuiltFilterCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixPrebuiltFilterCard(
    filter: PrebuiltGkiFilter,
    onFilterChange: (PrebuiltGkiFilter) -> Unit
) {
    val unlimitedLabel = stringResource(R.string.flash_unlimited)

    // Value lists (empty string = "不限")
    val androidValues = remember { listOf("") + KernelSupport.androidVersions() }
    val kernelValues = remember { listOf("") + KernelSupport.kernelVersions() }
    val subLevelValues = remember(filter.androidVersion, filter.kernelVersion) {
        listOf("") + prebuiltSubLevelOptions(filter.androidVersion, filter.kernelVersion)
    }

    // Display label lists for OverlayDropdownPreference
    val androidLabels = remember(androidValues, unlimitedLabel) {
        androidValues.map { it.ifBlank { unlimitedLabel } }
    }
    val kernelLabels = remember(kernelValues, unlimitedLabel) {
        kernelValues.map { it.ifBlank { unlimitedLabel } }
    }
    val subLevelLabels = remember(subLevelValues, unlimitedLabel) {
        subLevelValues.map { it.ifBlank { unlimitedLabel } }
    }

    val androidIndex = androidValues.indexOf(filter.androidVersion).coerceAtLeast(0)
    val kernelIndex = kernelValues.indexOf(filter.kernelVersion).coerceAtLeast(0)
    val subLevelIndex = subLevelValues.indexOf(filter.subLevel).coerceAtLeast(0)

    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.flash_filters),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // Android version
            OverlayDropdownPreference(
                title = stringResource(R.string.build_android_version),
                items = androidLabels,
                selectedIndex = androidIndex,
                onSelectedIndexChange = { index ->
                    onFilterChange(filter.copy(androidVersion = androidValues[index]))
                }
            )

            // Kernel version
            OverlayDropdownPreference(
                title = stringResource(R.string.build_kernel_version),
                items = kernelLabels,
                selectedIndex = kernelIndex,
                onSelectedIndexChange = { index ->
                    onFilterChange(filter.copy(kernelVersion = kernelValues[index]))
                }
            )

            // Minor / sub-level version
            OverlayDropdownPreference(
                title = stringResource(R.string.flash_minor_version),
                items = subLevelLabels,
                selectedIndex = subLevelIndex,
                onSelectedIndexChange = { index ->
                    onFilterChange(filter.copy(subLevel = subLevelValues[index]))
                }
            )

            // Show only matching assets
            SwitchPreference(
                title = stringResource(R.string.flash_only_matching_assets),
                checked = filter.onlyMatches,
                onCheckedChange = { onFilterChange(filter.copy(onlyMatches = it)) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiuixPrebuiltAssetCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiuixPrebuiltAssetCard(
    asset: PrebuiltGkiAsset,
    downloadedFiles: List<DownloadedArtifact>,
    progress: Int?,
    onDownload: () -> Unit,
    onCopyPath: (DownloadedArtifact) -> Unit,
    onFlash: (DownloadedArtifact) -> Unit,
    onInstall: (DownloadedArtifact) -> Unit,
    onDelete: (DownloadedArtifact) -> Unit
) {
    val type = DownloadUtils.classifyArtifact(asset.name)

    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header ───────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (type) {
                        ArtifactType.KERNEL_IMG -> Icons.Default.Memory
                        ArtifactType.ANYKERNEL3 -> Icons.Default.Inventory2
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = asset.name,
                        style = MiuixTheme.textStyles.main,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${DownloadUtils.formatSize(asset.sizeBytes)} · ${asset.releaseTag}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── State-dependent content ──────────────────────────
            when {
                progress != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = (progress / 100f).coerceIn(0f, 1f),
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.primary,
                                backgroundColor = MiuixTheme.colorScheme.surface
                            )
                        )
                        Text(
                            text = stringResource(R.string.flash_download_progress, progress),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }

                downloadedFiles.isEmpty() -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.flash_download),
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                }

                else -> {
                    downloadedFiles.forEach { file ->
                        MiuixDownloadedOutputRow(
                            artifact = file,
                            onCopyPath = { onCopyPath(file) },
                            onFlash = { onFlash(file) },
                            onInstall = { onInstall(file) },
                            onDelete = { onDelete(file) },
                            allowRootActions = true
                        )
                    }
                }
            }
        }
    }
}
