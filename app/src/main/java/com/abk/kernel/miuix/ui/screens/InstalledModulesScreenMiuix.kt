package com.abk.kernel.miuix.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Web
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.screens.MODULE_INSTALL_MIME_TYPES
import com.abk.kernel.ui.screens.RuntimeModuleDisplayGroup
import com.abk.kernel.ui.screens.canUninstallRuntimeModule
import com.abk.kernel.ui.screens.copyRuntimeModuleUriToCache
import com.abk.kernel.ui.screens.displayName
import com.abk.kernel.ui.screens.groupRuntimeModulesForDisplay
import com.abk.kernel.ui.screens.hasRuntimeModuleFileAccess
import com.abk.kernel.ui.screens.matchesRuntimeModuleQuery
import com.abk.kernel.ui.screens.normalizedType
import com.abk.kernel.ui.screens.runtimeModuleUriDisplayName
import com.abk.kernel.ui.screens.typeOrder
import com.abk.kernel.ui.webui.ModuleWebUiActivity
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun InstalledModulesScreenMiuix(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    pendingModuleInstallUri: String? = null,
    onPendingModuleInstallUriConsumed: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingInstallUri by remember { mutableStateOf<Uri?>(null) }
    var installDialogVisible by remember { mutableStateOf(false) }
    var installRunning by remember { mutableStateOf(false) }
    var installSuccess by remember { mutableStateOf<Boolean?>(null) }
    var installLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAllFilesAccessPrompt by remember { mutableStateOf(false) }
    var resumeModulePickerAfterPermission by remember { mutableStateOf(false) }
    var uninstallTarget by remember { mutableStateOf<AbkRuntimeModule?>(null) }

    val modules = remember(state.abkRuntimeStatus?.modules, query) {
        state.abkRuntimeStatus?.modules.orEmpty()
            .filter { it.matchesRuntimeModuleQuery(query) }
            .sortedWith(
                compareBy<AbkRuntimeModule> { it.typeOrder() }
                    .thenBy { !it.enabled }
                    .thenBy { it.displayName().lowercase() }
            )
    }
    val groupedModules = remember(modules) { groupRuntimeModulesForDisplay(modules) }

    val showEmptyState by remember {
        derivedStateOf {
            modules.isEmpty() && !state.abkRuntimeLoading
        }
    }

    val scrollDistance = remember { mutableFloatStateOf(0f) }
    var fabVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val canScrollForward = listState.canScrollForward
                if (!canScrollForward) return Offset.Zero

                scrollDistance.floatValue += available.y

                if (scrollDistance.floatValue <= -50f && fabVisible) {
                    fabVisible = false
                    scrollDistance.floatValue = 0f
                    return Offset(0f, available.y)
                }

                if (scrollDistance.floatValue >= 50f && !fabVisible) {
                    fabVisible = true
                    scrollDistance.floatValue = 0f
                    return Offset(0f, available.y)
                }

                return Offset.Zero
            }
        }
    }

    val offsetHeight by animateDpAsState(
        targetValue = if (fabVisible) 0.dp else 180.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        animationSpec = tween(durationMillis = 350)
    )

    fun appendInstallLog(line: String) {
        scope.launch(Dispatchers.Main.immediate) {
            installLog = installLog + line
        }
    }

    fun installModuleFromUri(uri: Uri) {
        if (installRunning) return
        installDialogVisible = true
        installRunning = true
        installSuccess = null
        installLog = listOf(
            "\$ module install",
            "source: $uri",
            "",
            context.getString(R.string.runtime_copying_module)
        )
        scope.launch {
            var stagedName = "module.zip"
            var stagedPath = ""
            val result = withContext(Dispatchers.IO) {
                var stagedFile: File? = null
                runCatching {
                    stagedFile = copyRuntimeModuleUriToCache(context, uri).also {
                        stagedName = it.name
                        stagedPath = it.absolutePath
                    }
                    appendInstallLog("file: $stagedPath")
                    appendInstallLog(context.getString(R.string.runtime_wait_root_shell))
                    if (!RootUtils.refreshRootState()) {
                        RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_manager_inactive)))
                    } else {
                        RootUtils.installModule(stagedPath, ::appendInstallLog)
                    }
                }.getOrElse {
                    RootUtils.ShellResult(false, listOf(context.getString(R.string.runtime_module_file_read_failed)))
                }.also {
                    stagedFile?.delete()
                }
            }
            installRunning = false
            installSuccess = result.success
            installLog = listOf(
                "\$ module install $stagedName",
                "file: ${stagedPath.ifBlank { context.getString(R.string.runtime_temp_file_missing) }}",
                ""
            ) + result.output.ifEmpty {
                listOf(
                    if (result.success) {
                        context.getString(R.string.runtime_module_install_done_no_output)
                    } else {
                        context.getString(R.string.runtime_module_install_failed_no_log)
                    }
                )
            }
            if (result.success) vm.refreshAbkRuntimeStatus()
        }
    }

    val modulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingInstallUri = uri
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (resumeModulePickerAfterPermission) {
            if (hasRuntimeModuleFileAccess()) {
                resumeModulePickerAfterPermission = false
                modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
            } else {
                showAllFilesAccessPrompt = true
            }
        }
    }

    fun launchModulePickerWithPermissionCheck() {
        if (installRunning) return
        if (hasRuntimeModuleFileAccess()) {
            modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
        } else {
            resumeModulePickerAfterPermission = true
            showAllFilesAccessPrompt = true
        }
    }

    fun openAllFilesAccessSettings() {
        showAllFilesAccessPrompt = false
        resumeModulePickerAfterPermission = true
        val packageUri = Uri.parse("package:${context.packageName}")
        val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        val allFilesSettings = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        runCatching {
            allFilesAccessLauncher.launch(appSettings)
        }.getOrElse {
            runCatching { allFilesAccessLauncher.launch(allFilesSettings) }
                .onFailure { showAllFilesAccessPrompt = true }
        }
    }

    fun launchModulePickerFallback() {
        showAllFilesAccessPrompt = false
        resumeModulePickerAfterPermission = false
        if (!installRunning) modulePicker.launch(MODULE_INSTALL_MIME_TYPES)
    }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            runCatching { Uri.parse(pendingModuleInstallUri) }.getOrNull()?.let { uri ->
                pendingInstallUri = uri
            }
            onPendingModuleInstallUriConsumed()
        }
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.rootGranted) {
        if (state.runtimeNavigationEnabled) vm.refreshAbkRuntimeStatus()
    }

    val scrollBehavior = MiuixScrollBehavior()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)
    val barColor = if (backdrop != null) Color.Transparent else surfaceColor

    Scaffold(
        topBar = {
            BlurredBar(backdrop, surfaceColor) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.runtime_installed_modules_title),
                    actions = {
                        IconButton(
                            onClick = { vm.refreshAbkRuntimeStatus() }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(R.string.runtime_refresh_installed_modules),
                                tint = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = fabVisible) {
                FloatingActionButton(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetHeight.roundToPx()) }
                        .padding(bottom = outerPadding.calculateBottomPadding() + 20.dp, end = 20.dp)
                        .border(0.05.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                    shadowElevation = 0.dp,
                    onClick = { launchModulePickerWithPermissionCheck() },
                    content = {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = stringResource(R.string.runtime_install_module),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current

        if (showEmptyState) {
            EmptyModulesStateView(
                innerPadding = innerPadding,
                bottomPadding = outerPadding.calculateBottomPadding(),
                layoutDirection = layoutDirection,
                hasModules = state.abkRuntimeStatus?.modules.orEmpty().isNotEmpty(),
                query = query
            )
        } else {
            ModuleListContent(
                abkRuntimeLoading = state.abkRuntimeLoading,
                abkRuntimeError = state.abkRuntimeError,
                hasNativeManagerPermission = state.hasNativeManagerPermission,
                abkRuntimeModuleActionId = state.abkRuntimeModuleActionId,
                vm = vm,
                groupedModules = groupedModules,
                query = query,
                onQueryChange = { query = it },
                scrollBehavior = scrollBehavior,
                nestedScrollConnection = nestedScrollConnection,
                listState = listState,
                innerPadding = innerPadding,
                bottomPadding = outerPadding.calculateBottomPadding(),
                layoutDirection = layoutDirection,
                context = context,
                onOpenWebUi = { module ->
                    context.startActivity(
                        Intent(context, ModuleWebUiActivity::class.java)
                            .putExtra(ModuleWebUiActivity.EXTRA_MODULE_ID, module.id)
                            .putExtra(ModuleWebUiActivity.EXTRA_MODULE_NAME, module.displayName())
                    )
                },
                onRequestUninstall = { module -> uninstallTarget = module },
                onRunAction = { moduleId -> vm.runRuntimeModuleAction(moduleId) },
                onSetEnabled = { moduleId, enabled -> vm.setAbkRuntimeModuleEnabled(moduleId, enabled) }
            )
        }
    }

    if (state.abkRuntimeModuleActionTitle != null) {
        OverlayDialog(
            show = true,
            title = state.abkRuntimeModuleActionTitle,
            onDismissRequest = { vm.dismissRuntimeModuleActionOutput() }
        ) {
            Column {
                if (state.abkRuntimeModuleActionId != null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        progress = null
                    )
                }
                Text(
                    text = state.abkRuntimeModuleActionOutput.ifEmpty {
                        listOf(stringResource(R.string.runtime_waiting_output))
                    }.joinToString("\n"),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        text = stringResource(R.string.close),
                        onClick = { vm.dismissRuntimeModuleActionOutput() }
                    )
                }
            }
        }
    }

    OverlayDialog(
        show = showAllFilesAccessPrompt,
        title = stringResource(R.string.runtime_file_access_required),
        onDismissRequest = {
            showAllFilesAccessPrompt = false
            resumeModulePickerAfterPermission = false
        }
    ) {
        Column {
            Text(
                text = stringResource(R.string.runtime_file_access_vendor_picker_warning),
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.runtime_file_access_desc),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { openAllFilesAccessSettings() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.runtime_grant_permission))
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.runtime_system_picker),
                    onClick = { launchModulePickerFallback() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    pendingInstallUri?.let { uri ->
        val uriDisplayName = remember(context, uri) { runtimeModuleUriDisplayName(context, uri) }
        OverlayDialog(
            show = true,
            title = stringResource(R.string.runtime_confirm_flash_module),
            onDismissRequest = { if (!installRunning) pendingInstallUri = null }
        ) {
            Column {
                Text(
                    text = uriDisplayName,
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uri.toString(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.runtime_confirm_flash_module_desc),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { if (!installRunning) pendingInstallUri = null }
                    )
                    TextButton(
                        text = stringResource(R.string.runtime_confirm_flash),
                        onClick = {
                            if (!installRunning) {
                                pendingInstallUri = null
                                installModuleFromUri(uri)
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }

    uninstallTarget?.let { module ->
        val pending = !module.remove
        val dialogTitle = if (pending) {
            stringResource(R.string.runtime_confirm_uninstall_module)
        } else {
            stringResource(R.string.runtime_revoke_uninstall_module)
        }
        val dialogMessage = if (pending) {
            stringResource(R.string.runtime_confirm_uninstall_module_desc)
        } else {
            stringResource(R.string.runtime_revoke_uninstall_module_desc)
        }
        val actionLabel = if (pending) {
            stringResource(R.string.runtime_uninstall)
        } else {
            stringResource(R.string.runtime_revoke)
        }
        OverlayDialog(
            show = true,
            title = dialogTitle,
            onDismissRequest = { uninstallTarget = null }
        ) {
            Column {
                Text(
                    text = module.displayName(),
                    style = MiuixTheme.textStyles.subtitle,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = module.id,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dialogMessage,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { uninstallTarget = null }
                    )
                    TextButton(
                        text = actionLabel,
                        onClick = {
                            vm.setAbkRuntimeModulePendingUninstall(module.id, !module.remove)
                            uninstallTarget = null
                        },
                        colors = if (pending) {
                            ButtonDefaults.textButtonColors(
                                color = MiuixTheme.colorScheme.error
                            )
                        } else {
                            ButtonDefaults.textButtonColorsPrimary()
                        }
                    )
                }
            }
        }
    }

    if (installDialogVisible) {
        val terminalScroll = rememberScrollState()
        val isDark = isSystemInDarkTheme()
        val terminalContainer = if (isDark) {
            MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)
        } else {
            MiuixTheme.colorScheme.surface.copy(alpha = 0.7f)
        }

        LaunchedEffect(installLog.size) {
            terminalScroll.animateScrollTo(terminalScroll.maxValue)
        }

        OverlayDialog(
            show = true,
            title = if (installRunning) {
                stringResource(R.string.runtime_installing_module)
            } else {
                stringResource(R.string.runtime_install_module)
            },
            onDismissRequest = { if (!installRunning) installDialogVisible = false }
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 190.dp, max = 360.dp)
                        .background(
                            color = terminalContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(terminalScroll)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        installLog.ifEmpty { listOf(stringResource(R.string.runtime_waiting_output)) }.forEach { line ->
                            Text(
                                text = line,
                                style = MiuixTheme.textStyles.body2,
                                fontFamily = FontFamily.Monospace,
                                color = if (line.startsWith("\$")) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (installRunning) {
                        TextButton(
                            text = stringResource(R.string.runtime_running),
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        TextButton(
                            text = stringResource(R.string.close),
                            onClick = { installDialogVisible = false }
                        )
                        if (installSuccess == true) {
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) { RootUtils.reboot() }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.error,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.runtime_reboot))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleListContent(
    abkRuntimeLoading: Boolean,
    abkRuntimeError: String?,
    hasNativeManagerPermission: Boolean,
    abkRuntimeModuleActionId: String?,
    vm: MainViewModel,
    groupedModules: List<RuntimeModuleDisplayGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    nestedScrollConnection: NestedScrollConnection,
    listState: LazyListState,
    innerPadding: PaddingValues,
    bottomPadding: androidx.compose.ui.unit.Dp,
    layoutDirection: LayoutDirection,
    context: android.content.Context,
    onOpenWebUi: (AbkRuntimeModule) -> Unit,
    onRequestUninstall: (AbkRuntimeModule) -> Unit,
    onRunAction: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit
) {

    val refreshPulling = stringResource(R.string.runtime_refresh_installed_modules)
    val refreshRelease = stringResource(R.string.runtime_refresh_installed_modules)
    val refreshRefresh = stringResource(R.string.runtime_refresh_installed_modules)
    val refreshComplete = stringResource(R.string.runtime_refresh_installed_modules)

    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val refreshTexts = remember {
        listOf(refreshPulling, refreshRelease, refreshRefresh, refreshComplete)
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(350)
            vm.refreshAbkRuntimeStatus()
            isRefreshing = false
        }
    }

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { if (!isRefreshing) isRefreshing = true },
        refreshTexts = refreshTexts,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 6.dp,
            start = innerPadding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection),
        ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxHeight()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .nestedScroll(nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
            ),
            overscrollEffect = null,
        ) {
            item {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    label = stringResource(R.string.runtime_installed_modules_title),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                )
            }

            if (abkRuntimeLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        progress = null
                    )
                }
            }

            abkRuntimeError?.let { error ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (!hasNativeManagerPermission) {
                                    error
                                } else {
                                    stringResource(R.string.runtime_operation_incomplete_retry)
                                },
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.body2
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { vm.refreshAbkRuntimeStatus() },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(stringResource(R.string.runtime_refresh_installed_modules))
                            }
                        }
                    }
                }
            }

            groupedModules.forEach { group ->
                group.groupName?.let { groupName ->
                    item(key = "group-${groupName}") {
                        Text(
                            text = groupName,
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        group.groupDescription?.takeIf { it.isNotBlank() }?.let { desc ->
                            Text(
                                text = desc,
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                items(
                    items = group.modules,
                    key = { "module-${it.id}" }
                ) { module ->
                    InstalledModuleCardMiuix(
                        module = module,
                        actionInFlight = abkRuntimeModuleActionId == module.id,
                        onSetEnabled = { enabled -> onSetEnabled(module.id, enabled) },
                        onRequestUninstall = { onRequestUninstall(module) },
                        onRunAction = { onRunAction(module.id) },
                        onOpenWebUi = { onOpenWebUi(module) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding + 80.dp))
            }
        }
    }
}

@Composable
private fun InstalledModuleCardMiuix(
    module: AbkRuntimeModule,
    actionInFlight: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestUninstall: () -> Unit,
    onRunAction: () -> Unit,
    onOpenWebUi: () -> Unit
) {
    val canUninstall = module.canUninstallRuntimeModule()
    val isDark = isSystemInDarkTheme()
    val onSurface = MiuixTheme.colorScheme.onSurface
    val secondaryContainer = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val actionIconTint = remember(isDark) { onSurface.copy(alpha = if (isDark) 0.7f else 0.9f) }
    val typeLabel = miuixRuntimeModuleTypeLabel(module)

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                SubcomposeLayout { constraints ->
                    val namePlaceable = subcompose("name") {
                        Text(
                            text = module.displayName(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight(550),
                            color = MiuixTheme.colorScheme.onSurface,
                            onTextLayout = { }
                        )
                    }.first().measure(constraints)

                    layout(namePlaceable.width, namePlaceable.height) {
                        namePlaceable.placeRelative(0, 0)
                    }
                }
                if (module.version.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.runtime_module_version, module.version),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                if (module.author.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.runtime_module_author, module.author),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Text(
                    text = "Type: $typeLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            if (module.controllable && !module.readonly) {
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onSetEnabled,
                    enabled = !actionInFlight
                )
            }
        }

        if (module.description.isNotBlank()) {
            Text(
                text = module.description,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 4
            )
        }

        if (module.repoUrl.isNotBlank()) {
            Text(
                text = module.repoUrl,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
        )

        Row {
            AnimatedVisibility(
                visible = module.actionSupported || module.hasActionScript,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    backgroundColor = secondaryContainer,
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onRunAction
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Filled.Settings,
                        tint = actionIconTint,
                        contentDescription = stringResource(R.string.runtime_run_action)
                    )
                }
            }

            AnimatedVisibility(
                visible = module.hasWebUi,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    backgroundColor = secondaryContainer,
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onOpenWebUi
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Filled.Web,
                        tint = actionIconTint,
                        contentDescription = stringResource(R.string.runtime_open_webui)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (canUninstall) {
                if (actionInFlight) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.CenterVertically),
                        progress = null
                    )
                }
                IconButton(
                    minHeight = 35.dp,
                    minWidth = 35.dp,
                    onClick = onRequestUninstall,
                    backgroundColor = secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = if (module.remove) Icons.Filled.RestartAlt else Icons.Filled.Delete,
                            tint = actionIconTint,
                            contentDescription = null
                        )
                        Text(
                            modifier = Modifier.padding(start = 4.dp, end = 3.dp),
                            text = if (module.remove) {
                                stringResource(R.string.runtime_revoke)
                            } else {
                                stringResource(R.string.runtime_uninstall)
                            },
                            color = actionIconTint,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyModulesStateView(
    innerPadding: PaddingValues,
    bottomPadding: androidx.compose.ui.unit.Dp,
    layoutDirection: LayoutDirection,
    hasModules: Boolean,
    query: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = bottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Extension,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 16.dp)
            )
            Text(
                text = if (hasModules && query.isNotBlank()) {
                    stringResource(R.string.runtime_no_matching_modules)
                } else {
                    stringResource(R.string.runtime_no_reported_modules)
                },
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun miuixRuntimeModuleTypeLabel(module: AbkRuntimeModule): String =
    when (module.normalizedType()) {
        "standard" -> stringResource(R.string.runtime_module_type_standard)
        "builtin" -> stringResource(R.string.runtime_module_type_builtin)
        "kpm" -> "KPM"
        else -> module.normalizedType()
    }
