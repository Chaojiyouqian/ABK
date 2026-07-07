@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.abk.kernel.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.R
import com.abk.kernel.dashboard.DashboardLayoutMode
import com.abk.kernel.dashboard.DashboardPageId
import com.abk.kernel.dashboard.RootAuthDashboardWidgets
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.RootGrantProfile
import com.abk.kernel.ui.components.AbkCenteredLoadingTransition
import com.abk.kernel.ui.components.AbkLoadingPill
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.AppPageBackground
import com.abk.kernel.ui.components.ObserveChildPageVisibility
import com.abk.kernel.ui.components.childPageOverlayEnterTransition
import com.abk.kernel.ui.components.childPageOverlayExitTransition
import com.abk.kernel.ui.components.childPageScrimExitTransition
import com.abk.kernel.ui.components.rememberChildPageBackController
import com.abk.kernel.ui.components.rememberChildPageOverlayTransition
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitch
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.dashboard.DashboardFreeform
import com.abk.kernel.ui.dashboard.DashboardFreeformMetrics
import com.abk.kernel.ui.dashboard.DashboardGrid
import com.abk.kernel.ui.dashboard.DashboardGridMetrics
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun RootAuthorizationScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    onDetailPageVisibleChange: (Boolean) -> Unit = {},
    readOnlyPreview: Boolean = false,
    pagePickerActive: Boolean = false,
    onRequestPagePicker: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val editorActive = state.dashboardEditingPageId == DashboardPageId.ROOT_AUTH && !readOnlyPreview
    val pageLayout = if (readOnlyPreview) {
        state.dashboardDraftLayouts[DashboardPageId.ROOT_AUTH]
            ?: state.dashboardLayouts[DashboardPageId.ROOT_AUTH]
            ?: RootAuthDashboardWidgets.defaultLayout()
    } else if (editorActive) {
        state.dashboardDraftLayouts[DashboardPageId.ROOT_AUTH]
            ?: state.dashboardLayouts[DashboardPageId.ROOT_AUTH]
            ?: RootAuthDashboardWidgets.defaultLayout()
    } else {
        state.dashboardLayouts[DashboardPageId.ROOT_AUTH]
            ?: RootAuthDashboardWidgets.defaultLayout()
    }
    val widgetLabels = mapOf(
        RootAuthDashboardWidgets.CONTROLS to stringResource(R.string.root_auth_title),
        RootAuthDashboardWidgets.LIST to stringResource(R.string.root_auth_title)
    )
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var widgetsTrayExpanded by remember { mutableStateOf(false) }
    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    var activeDragPointerY by remember { mutableStateOf<Float?>(null) }
    var gridMetrics by remember { mutableStateOf<DashboardGridMetrics?>(null) }
    var freeformMetrics by remember { mutableStateOf<DashboardFreeformMetrics?>(null) }
    var trayTopY by remember { mutableStateOf(Float.MAX_VALUE) }
    var hiddenWidgetDrag by remember { mutableStateOf<DashboardHiddenWidgetDragState?>(null) }
    val actionMenuRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (actionMenuExpanded) 45f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rootauth-layout-fab-rotation"
    )
    val trayWidgetIds = remember(pageLayout.items, hiddenWidgetDrag) {
        buildList {
            val hiddenIds = pageLayout.items.filter { !it.visible }.map { it.widgetId }
            addAll(hiddenIds)
            val draggingWidgetId = hiddenWidgetDrag?.widgetId
            if (draggingWidgetId != null && draggingWidgetId !in this) add(draggingWidgetId)
        }
    }
    val pinchObserver = rememberEditorPinchObserver(onRequestPagePicker)
    var query by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    val motionScheme = MaterialTheme.motionScheme
    val apps = remember(state.rootGrantApps, query, showSystemApps) {
        state.rootGrantApps
            .filter { showSystemApps || !it.isSystemApp }
            .filter { app ->
                val needle = query.trim()
                needle.isBlank() ||
                    app.label.contains(needle, ignoreCase = true) ||
                    app.packageName.contains(needle, ignoreCase = true) ||
                    app.uid.toString().contains(needle)
            }
    }
    val selectedListApp = remember(state.rootGrantApps, selectedPackage) {
        selectedPackage?.let { packageName ->
            state.rootGrantApps.firstOrNull { it.packageName == packageName }
        }
    }
    val selectedDetailApp = remember(state.rootGrantDetailApp, selectedPackage) {
        state.rootGrantDetailApp?.takeIf { it.packageName == selectedPackage }
    }
    val detailPageVisible = selectedPackage != null
    val detailPageTransition = rememberChildPageOverlayTransition(
        visible = detailPageVisible,
        label = "root-auth-detail"
    )
    val canLeaveDetail = state.rootGrantSavingPackage == null && !state.rootGrantDetailLoading
    val showInitialLoading = state.rootGrantLoading && state.rootGrantApps.isEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val payload = readLayoutTextFromUri(context, uri)
                vm.importDashboardLayoutJson(DashboardPageId.ROOT_AUTH, payload)
            }.onSuccess { result ->
                val messageRes = if (result.error == null) {
                    R.string.status_layout_import_success
                } else {
                    R.string.status_layout_import_failed_reset
                }
                val message = if (result.error == null) {
                    context.getString(messageRes, result.importedItemCount, result.ignoredItemCount)
                } else {
                    context.getString(messageRes)
                }
                vm.showSnackbar(message, longDuration = result.error != null)
            }.onFailure { error ->
                vm.showSnackbar(
                    context.getString(
                        R.string.status_layout_import_failed,
                        error.message ?: error::class.java.simpleName
                    ),
                    longDuration = true
                )
            }
        }
    }

    LaunchedEffect(state.runtimeNavigationEnabled, state.abkRuntimeStatus?.runtimeBackend?.backend) {
        if (state.runtimeNavigationEnabled) vm.refreshRootGrantApps()
    }

    LaunchedEffect(editorActive) {
        if (!editorActive) {
            actionMenuExpanded = false
            widgetsTrayExpanded = false
            selectedWidgetId = null
            hiddenWidgetDrag = null
            activeDragPointerY = null
        }
    }

    LaunchedEffect(pagePickerActive) {
        if (pagePickerActive) actionMenuExpanded = false
    }

    LaunchedEffect(activeDragPointerY, viewportHeightPx) {
        val triggerY = activeDragPointerY ?: return@LaunchedEffect
        if (viewportHeightPx <= 0f) return@LaunchedEffect
        val thresholdPx = with(density) { 88.dp.toPx() }
        while (activeDragPointerY != null) {
            val currentY = activeDragPointerY ?: break
            val topDistance = currentY
            val bottomDistance = viewportHeightPx - currentY
            val delta = when {
                topDistance < thresholdPx -> {
                    val ratio = 1f - (topDistance / thresholdPx).coerceIn(0f, 1f)
                    -((4f) + ratio * 28f)
                }
                bottomDistance < thresholdPx -> {
                    val ratio = 1f - (bottomDistance / thresholdPx).coerceIn(0f, 1f)
                    (4f) + ratio * 28f
                }
                else -> 0f
            }
            if (delta != 0f) {
                scrollState.scrollTo((scrollState.value + delta.roundToInt()).coerceIn(0, scrollState.maxValue))
            }
            delay(16)
        }
    }

    @Composable
    fun RootAuthWidget(widgetId: String) {
        when (widgetId) {
            RootAuthDashboardWidgets.CONTROLS -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RootGrantSearchField(query = query, onQueryChange = { query = it })
                RootGrantControlsCard(
                    showSystemApps = showSystemApps,
                    onShowSystemAppsChange = { showSystemApps = it }
                )
                if (!editorActive && !readOnlyPreview) {
                    OutlinedButton(
                        onClick = { vm.refreshRootGrantApps(force = true) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.root_auth_refresh_list))
                    }
                }
                if (state.rootGrantLoading && state.rootGrantApps.isNotEmpty()) {
                    RootGrantRefreshingRow()
                }
                state.rootGrantError?.let {
                    RootGrantMessageCard(it) { vm.refreshRootGrantApps(force = true) }
                }
            }
            RootAuthDashboardWidgets.LIST -> {
                if (!state.rootGrantLoading && apps.isEmpty()) {
                    Text(
                        text = if (query.isBlank()) {
                            stringResource(R.string.root_auth_no_apps)
                        } else {
                            stringResource(R.string.root_auth_no_matching_apps)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        apps.forEach { app ->
                            RootGrantAppCard(
                                app = app,
                                saving = state.rootGrantSavingPackage == app.packageName,
                                anySaving = state.rootGrantSavingPackage != null,
                                onToggle = { allowed -> vm.setRootGrantAllowed(app.packageName, allowed) },
                                onOpen = {
                                    childPageBack.resetProgress()
                                    selectedPackage = app.packageName
                                    vm.openRootGrantProfile(app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fun closeDetailPage() {
        if (canLeaveDetail) {
            selectedPackage = null
            vm.clearRootGrantDetail()
        }
    }

    val childPageBack = rememberChildPageBackController(
        enabled = detailPageVisible && canLeaveDetail,
        predictiveBackEnabled = state.predictiveBackEnabled,
        onBack = ::closeDetailPage,
    )

    ObserveChildPageVisibility(
        transition = detailPageTransition,
        onVisibleChange = onDetailPageVisibleChange,
        onAfterExitAnimation = { childPageBack.resetProgress() }
    )

    DisposableEffect(Unit) {
        onDispose { onDetailPageVisibleChange(false) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val childPageTopInset = outerPadding.calculateTopPadding()
        val childPageBottomInset = outerPadding.calculateBottomPadding()
        val childPageModifier = Modifier
            .fillMaxWidth()
            .height(maxHeight + childPageTopInset + childPageBottomInset)
            .offset(y = -childPageTopInset)

        Scaffold(
            containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            topBar = {
                ExpressiveTopBar(
                    title = stringResource(R.string.root_auth_title),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (!editorActive && !readOnlyPreview) {
                            IconButton(
                                onClick = { vm.refreshRootGrantApps(force = true) },
                                enabled = !state.rootGrantLoading
                            ) {
                                if (state.rootGrantLoading) {
                                    LoadingIndicator(Modifier.size(22.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.root_auth_refresh_list))
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
            if (showInitialLoading) {
                RootGrantInitialLoadingScreen(
                    padding = padding,
                    outerPadding = outerPadding,
                    query = query,
                    onQueryChange = { query = it },
                    showSystemApps = showSystemApps,
                    onShowSystemAppsChange = { showSystemApps = it },
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                )
                return@Scaffold
            }

            val editorDockHeight = 92.dp
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .onGloballyPositioned { viewportHeightPx = it.size.height.toFloat() }
                    .then(
                        if (editorActive && !pagePickerActive) {
                            Modifier.motionEventSpy(pinchObserver)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = AbkScreenHorizontalPadding)
                        .padding(
                            bottom = if (editorActive) {
                                editorDockHeight + 28.dp
                            } else {
                                80.dp + outerPadding.calculateBottomPadding()
                            }
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (pageLayout.layoutMode) {
                        DashboardLayoutMode.GRID -> DashboardGrid(
                            layout = pageLayout,
                            widgetLabels = widgetLabels,
                            editable = editorActive,
                            canMoveItem = { widgetId, targetX, targetY ->
                                com.abk.kernel.dashboard.DashboardLayoutEngine.canMoveItem(
                                    layout = pageLayout,
                                    widgetId = widgetId,
                                    targetX = targetX,
                                    targetY = targetY,
                                    definitions = RootAuthDashboardWidgets.definitions
                                )
                            },
                            canResizeItem = { widgetId, targetW, targetH ->
                                com.abk.kernel.dashboard.DashboardLayoutEngine.canResizeItem(
                                    layout = pageLayout,
                                    widgetId = widgetId,
                                    targetW = targetW,
                                    targetH = targetH,
                                    definitions = RootAuthDashboardWidgets.definitions
                                )
                            },
                            canHideWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canHide == true },
                            canMinimizeWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canResize == true },
                            canMaximizeWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canResize == true },
                            canResizeWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canResize == true },
                            onMoveItem = { widgetId, x, y -> vm.moveDashboardWidget(DashboardPageId.ROOT_AUTH, widgetId, x, y) },
                            onResizeItem = { widgetId, w, h -> vm.resizeDashboardWidget(DashboardPageId.ROOT_AUTH, widgetId, w, h) },
                            onSetItemSpanMode = { widgetId, spanMode -> vm.setDashboardWidgetSpanMode(DashboardPageId.ROOT_AUTH, widgetId, spanMode) },
                            onHideItem = { widgetId -> vm.setDashboardWidgetVisible(DashboardPageId.ROOT_AUTH, widgetId, false) },
                            selectedWidgetId = selectedWidgetId,
                            onSelectWidget = { selectedWidgetId = it },
                            onGridMetricsChanged = { metrics -> gridMetrics = metrics },
                            onDragPointerYChanged = { activeDragPointerY = it }
                        ) { widgetId, _ -> RootAuthWidget(widgetId) }
                        DashboardLayoutMode.FREEFORM -> DashboardFreeform(
                            layout = pageLayout,
                            widgetLabels = widgetLabels,
                            editable = editorActive,
                            canMoveItem = { widgetId, targetX, targetY ->
                                com.abk.kernel.dashboard.DashboardLayoutEngine.canMoveItem(
                                    layout = pageLayout,
                                    widgetId = widgetId,
                                    targetX = targetX,
                                    targetY = targetY,
                                    definitions = RootAuthDashboardWidgets.definitions
                                )
                            },
                            canResizeItem = { widgetId, targetW, targetH ->
                                com.abk.kernel.dashboard.DashboardLayoutEngine.canResizeItem(
                                    layout = pageLayout,
                                    widgetId = widgetId,
                                    targetW = targetW,
                                    targetH = targetH,
                                    definitions = RootAuthDashboardWidgets.definitions
                                )
                            },
                            canHideWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canHide == true },
                            canMinimizeWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canResize == true },
                            canMaximizeWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canResize == true },
                            canResizeWidget = { widgetId -> RootAuthDashboardWidgets.definitionMap[widgetId]?.canResize == true },
                            onMoveItem = { widgetId, x, y -> vm.moveDashboardWidget(DashboardPageId.ROOT_AUTH, widgetId, x, y) },
                            onResizeItem = { widgetId, w, h -> vm.resizeDashboardWidget(DashboardPageId.ROOT_AUTH, widgetId, w, h) },
                            onSetItemSpanMode = { widgetId, spanMode -> vm.setDashboardWidgetSpanMode(DashboardPageId.ROOT_AUTH, widgetId, spanMode) },
                            onHideItem = { widgetId -> vm.setDashboardWidgetVisible(DashboardPageId.ROOT_AUTH, widgetId, false) },
                            selectedWidgetId = selectedWidgetId,
                            onSelectWidget = { selectedWidgetId = it },
                            onCanvasMetricsChanged = { metrics -> freeformMetrics = metrics },
                            onDragPointerYChanged = { activeDragPointerY = it }
                        ) { widgetId, _ -> RootAuthWidget(widgetId) }
                    }
                }

                if (editorActive) {
                    DashboardEditorWidgetsTray(
                        visible = widgetsTrayExpanded,
                        hiddenItems = trayWidgetIds,
                        widgetLabels = widgetLabels,
                        dashboardLayout = pageLayout,
                        onHiddenWidgetDrag = { dragState ->
                            hiddenWidgetDrag = dragState
                            activeDragPointerY = dragState?.pointerY
                        },
                        onHiddenWidgetDrop = { dragState ->
                            activeDragPointerY = null
                            hiddenWidgetDrag = null
                            if (!dragState.leftTray || dragState.pointerY >= trayTopY) return@DashboardEditorWidgetsTray
                            val item = pageLayout.items.firstOrNull { it.widgetId == dragState.widgetId } ?: return@DashboardEditorWidgetsTray
                            val target = when (pageLayout.layoutMode) {
                                DashboardLayoutMode.GRID -> {
                                    val metrics = gridMetrics ?: return@DashboardEditorWidgetsTray
                                    computeHiddenWidgetDropTarget(metrics, item, dragState.pointerX, dragState.pointerY)
                                }
                                DashboardLayoutMode.FREEFORM -> {
                                    val metrics = freeformMetrics ?: return@DashboardEditorWidgetsTray
                                    computeHiddenWidgetFreeformDropTarget(metrics, item, dragState.pointerX, dragState.pointerY, density)
                                }
                            }
                            vm.placeDashboardHiddenWidget(DashboardPageId.ROOT_AUTH, dragState.widgetId, target.first, target.second)
                            selectedWidgetId = dragState.widgetId
                        },
                        activeDragWidgetId = hiddenWidgetDrag?.widgetId,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .onGloballyPositioned { trayTopY = it.positionInRoot().y }
                    ) { widgetId -> RootAuthWidget(widgetId) }
                    AnimatedVisibility(
                        visible = !pagePickerActive,
                        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 28.dp)
                    ) {
                        DashboardEditorFabMenu(
                            expanded = actionMenuExpanded,
                            rotation = actionMenuRotation,
                            onToggle = { actionMenuExpanded = !actionMenuExpanded },
                            onImport = { actionMenuExpanded = false; importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                            onShare = {
                                actionMenuExpanded = false
                                shareDashboardLayout(
                                    context = context,
                                    payload = vm.exportDashboardLayoutJson(DashboardPageId.ROOT_AUTH),
                                    title = context.getString(R.string.root_auth_title)
                                )
                            },
                            onSaveAndExit = {
                                actionMenuExpanded = false
                                vm.saveDashboardLayoutDraft(DashboardPageId.ROOT_AUTH)
                            },
                            onToggleWidgets = {
                                widgetsTrayExpanded = !widgetsTrayExpanded
                                actionMenuExpanded = false
                            }
                        )
                    }
                    DashboardHiddenWidgetFloatingPreview(
                        dragState = hiddenWidgetDrag,
                        trayTopY = trayTopY,
                        layoutMode = pageLayout.layoutMode,
                        gridMetrics = gridMetrics,
                        freeformMetrics = freeformMetrics,
                        layout = pageLayout,
                        definitions = RootAuthDashboardWidgets.definitions
                    )
                }
            }
        }

        detailPageTransition.AnimatedVisibility(
            visible = { it },
            enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = childPageScrimExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = childPageBack.scrimAlpha))
            )
        }

        detailPageTransition.AnimatedVisibility(
            visible = { it },
            enter = childPageOverlayEnterTransition(state.predictiveBackEnabled, motionScheme),
            exit = childPageOverlayExitTransition(state.predictiveBackEnabled, motionScheme),
            modifier = childPageModifier
        ) {
            selectedPackage?.let { packageName ->
                val headerApp = selectedDetailApp ?: selectedListApp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(childPageBack.backTransformModifier())
                ) {
                    RootGrantDetailPageBackground(
                        backgroundUri = state.customBackgroundUri,
                        backgroundImageEnabled = state.backgroundImageEnabled
                    )
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            ExpressiveTopBar(
                                title = headerApp?.label?.ifBlank { packageName } ?: packageName,
                                navigationIcon = {
                                    IconButton(
                                        enabled = canLeaveDetail,
                                        onClick = childPageBack::requestDismiss
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.root_auth_back_to_list))
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        when {
                            state.rootGrantDetailLoading -> RootGrantDetailLoadingPage(padding = padding)
                            selectedDetailApp != null -> RootGrantProfilePage(
                                app = selectedDetailApp,
                                padding = padding,
                                saving = state.rootGrantSavingPackage == selectedDetailApp.packageName,
                                warning = state.rootGrantDetailWarning,
                                onSave = { profile ->
                                    vm.saveRootGrantProfile(profile)
                                }
                            )
                            else -> RootGrantDetailMessagePage(
                                padding = padding,
                                message = state.rootGrantError ?: stringResource(R.string.runtime_manager_inactive)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootGrantInitialLoadingScreen(
    padding: PaddingValues,
    outerPadding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(padding)
            .fillMaxSize()
            .padding(
                start = AbkScreenHorizontalPadding,
                top = 0.dp,
                end = AbkScreenHorizontalPadding,
                bottom = 80.dp + outerPadding.calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RootGrantSearchField(
            query = query,
            onQueryChange = onQueryChange
        )
        RootGrantControlsCard(
            showSystemApps = showSystemApps,
            onShowSystemAppsChange = onShowSystemAppsChange
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            RootGrantInitialLoading()
        }
    }
}

@Composable
private fun RootGrantSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(Icons.Default.Search, null) },
        placeholder = { Text(stringResource(R.string.root_auth_search_apps)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun RootGrantControlsCard(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit
) {
    ExpressiveSectionCard(
        title = stringResource(R.string.root_auth_section_title),
        subtitle = stringResource(R.string.root_auth_section_desc),
        icon = Icons.Default.AdminPanelSettings
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.root_auth_show_system_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(checked = showSystemApps, onCheckedChange = onShowSystemAppsChange)
        }
    }
}

@Composable
private fun RootGrantInitialLoading() {
    AbkLoadingPill(text = stringResource(R.string.loading))
}

@Composable
private fun RootGrantRefreshingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AbkLoadingPill(
            text = stringResource(R.string.loading),
            compact = true
        )
    }
}

@Composable
private fun RootGrantDetailLoadingPage(
    padding: PaddingValues
) {
    AbkCenteredLoadingTransition(
        text = stringResource(R.string.loading),
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = AbkScreenHorizontalPadding)
    )
}

@Composable
private fun RootGrantDetailMessagePage(
    padding: PaddingValues,
    message: String
) {
    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = AbkScreenHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RootGrantDetailPageBackground(
    backgroundUri: String?,
    backgroundImageEnabled: Boolean
) {
    AppPageBackground(
        backgroundUri = backgroundUri,
        backgroundImageEnabled = backgroundImageEnabled
    )
}

@Composable
private fun RootGrantAppCard(
    app: RootGrantApp,
    saving: Boolean,
    anySaving: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onOpen
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(
                    packageName = app.packageName,
                    label = app.label,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = app.label.ifBlank { app.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf("UID ${app.uid}", app.userName).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                } else {
                    ExpressiveSwitch(
                        checked = app.profile.allowSu,
                        enabled = !anySaving,
                        onCheckedChange = onToggle
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp).size(24.dp)
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RootGrantChip(if (app.profile.allowSu) stringResource(R.string.root_auth_allow) else stringResource(R.string.root_auth_deny))
                if (app.isSystemApp) RootGrantChip(stringResource(R.string.root_auth_system_app))
                if (app.profileLoaded) {
                    RootGrantChip(
                        if (app.profile.rootUseDefault) {
                            stringResource(R.string.root_auth_default_profile)
                        } else {
                            stringResource(R.string.root_auth_custom_profile)
                        }
                    )
                    if (app.profile.umountModules) {
                        RootGrantChip(stringResource(R.string.root_auth_umount_modules))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier.size(56.dp),
    cornerSize: Dp = 14.dp
) {
    val context = LocalContext.current
    var drawable by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        drawable = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    }
    val iconModifier = modifier.clip(RoundedCornerShape(cornerSize))
    if (drawable != null) {
        AsyncImage(
            model = drawable,
            contentDescription = label.ifBlank { packageName },
            contentScale = ContentScale.Crop,
            modifier = iconModifier
        )
    } else {
        Box(
            modifier = iconModifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun RootGrantProfilePage(
    app: RootGrantApp,
    padding: androidx.compose.foundation.layout.PaddingValues,
    saving: Boolean,
    warning: String?,
    onSave: (RootGrantProfile) -> Unit
) {
    val profile = app.profile
    var allowSu by rememberSaveable(app.packageName) { mutableStateOf(profile.allowSu) }
    var rootUseDefault by rememberSaveable(app.packageName) { mutableStateOf(profile.rootUseDefault) }
    var rootTemplate by rememberSaveable(app.packageName) { mutableStateOf(profile.rootTemplate) }
    var uidText by rememberSaveable(app.packageName) { mutableStateOf(profile.uid.toString()) }
    var gidText by rememberSaveable(app.packageName) { mutableStateOf(profile.gid.toString()) }
    var groupsText by rememberSaveable(app.packageName) { mutableStateOf(profile.groups.joinToString(",")) }
    var capabilitiesText by rememberSaveable(app.packageName) { mutableStateOf(profile.capabilities.joinToString(",")) }
    var contextText by rememberSaveable(app.packageName) { mutableStateOf(profile.context) }
    var namespaceText by rememberSaveable(app.packageName) { mutableStateOf(profile.namespace.toString()) }
    var nonRootUseDefault by rememberSaveable(app.packageName) { mutableStateOf(profile.nonRootUseDefault) }
    var umountModules by rememberSaveable(app.packageName) { mutableStateOf(profile.umountModules) }
    var rulesText by rememberSaveable(app.packageName) { mutableStateOf(profile.rules) }

    fun saveProfile() {
        onSave(
            profile.copy(
                name = app.packageName,
                currentUid = app.uid,
                allowSu = allowSu,
                rootUseDefault = rootUseDefault,
                rootTemplate = rootTemplate.trim(),
                uid = uidText.toIntOrNull() ?: 0,
                gid = gidText.toIntOrNull() ?: 0,
                groups = parseIntList(groupsText),
                capabilities = parseIntList(capabilitiesText),
                context = contextText.trim().ifBlank { "u:r:ksu:s0" },
                namespace = namespaceText.toIntOrNull() ?: 0,
                nonRootUseDefault = nonRootUseDefault,
                umountModules = umountModules,
                rules = rulesText
            )
        )
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AbkScreenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!warning.isNullOrBlank()) {
            ExpressiveSectionCard(
                title = stringResource(R.string.root_auth_profile_read_disabled_title),
                subtitle = warning,
                icon = Icons.Default.AdminPanelSettings
            ) {}
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                label = app.label,
                modifier = Modifier.size(64.dp),
                cornerSize = 16.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = app.label.ifBlank { app.packageName },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ExpressiveSectionCard(
            title = stringResource(R.string.root_auth_title),
            subtitle = if (allowSu) stringResource(R.string.root_auth_allow_request) else stringResource(R.string.root_auth_deny_request),
            icon = Icons.Default.Security
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (allowSu) stringResource(R.string.root_auth_allowed) else stringResource(R.string.root_auth_not_allowed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ExpressiveSwitch(
                    checked = allowSu,
                    enabled = !saving,
                    onCheckedChange = { allowSu = it }
                )
            }
        }

        ExpressiveSectionCard(
            title = "App Profile",
            subtitle = if (allowSu) {
                if (rootUseDefault) stringResource(R.string.root_auth_default) else stringResource(R.string.root_auth_custom)
            } else {
                if (nonRootUseDefault) {
                    stringResource(R.string.root_auth_default_non_root)
                } else {
                    stringResource(R.string.root_auth_custom_non_root)
                }
            },
            icon = Icons.Default.AccountCircle
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowSu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rootUseDefault,
                            onClick = { rootUseDefault = true },
                            label = { Text(stringResource(R.string.root_auth_default)) }
                        )
                        FilterChip(
                            selected = !rootUseDefault && rootTemplate.isNotBlank(),
                            onClick = {
                                rootUseDefault = false
                                if (rootTemplate.isBlank()) rootTemplate = "default"
                            },
                            label = { Text(stringResource(R.string.root_auth_template)) }
                        )
                        FilterChip(
                            selected = !rootUseDefault && rootTemplate.isBlank(),
                            onClick = {
                                rootUseDefault = false
                                rootTemplate = ""
                            },
                            label = { Text(stringResource(R.string.root_auth_custom)) }
                        )
                    }
                    if (!rootUseDefault && rootTemplate.isNotBlank()) {
                        RootGrantTextField(
                            stringResource(R.string.root_auth_template),
                            rootTemplate,
                            { rootTemplate = it },
                            stringResource(R.string.root_auth_template_name)
                        )
                    }
                    if (!rootUseDefault) {
                        RootGrantTextField("UID", uidText, { uidText = it })
                        RootGrantTextField("GID", gidText, { gidText = it })
                        RootGrantTextField("Groups", groupsText, { groupsText = it }, stringResource(R.string.root_auth_comma_separated))
                        RootGrantTextField("Capabilities", capabilitiesText, { capabilitiesText = it }, stringResource(R.string.root_auth_comma_separated))
                        RootGrantTextField("SELinux Context", contextText, { contextText = it })
                        RootGrantTextField("Namespace", namespaceText, { namespaceText = it }, stringResource(R.string.root_auth_namespace_hint))
                        RootGrantTextField("SEPolicy Rules", rulesText, { rulesText = it }, stringResource(R.string.root_auth_optional_empty), singleLine = false)
                    }
                } else {
                    RootGrantSwitchRow(stringResource(R.string.root_auth_use_default_non_root), nonRootUseDefault) { nonRootUseDefault = it }
                    RootGrantSwitchRow(stringResource(R.string.root_auth_umount_modules), umountModules) { umountModules = it }
                }
            }
        }

        Button(
            onClick = ::saveProfile,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun RootGrantSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RootGrantTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4
    )
}

@Composable
private fun RootGrantChip(label: String) {
    ExpressiveStatusChip(
        label = label,
        icon = Icons.Default.Tune,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RootGrantMessageCard(message: String, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = uiSurfaceColor(MaterialTheme.colorScheme.errorContainer)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.runtime_recheck))
            }
        }
    }
}

private fun parseIntList(value: String): List<Int> =
    value.split(',', ' ', '\n', '\t')
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
