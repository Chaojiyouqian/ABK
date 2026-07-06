@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.dashboard.StatusDashboardWidgets
import com.abk.kernel.data.model.BuildStatus
import com.abk.kernel.data.model.WorkflowRun
import com.abk.kernel.ui.components.AbkScreenHorizontalPadding
import com.abk.kernel.ui.components.ExpressiveHeroCard
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveTopBar
import com.abk.kernel.ui.components.ShimmerLinearProgress
import com.abk.kernel.ui.dashboard.DashboardGridMetrics
import com.abk.kernel.ui.dashboard.DashboardGrid
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class HiddenWidgetDragState(
    val widgetId: String,
    val pointerX: Float,
    val pointerY: Float,
    val materialized: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusScreen(
    vm: MainViewModel,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    runtimeNavigationEnabled: Boolean = false,
    onToggleRuntimeNavigation: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val widgetLabels = mapOf(
        StatusDashboardWidgets.HERO to stringResource(R.string.status_widget_hero),
        StatusDashboardWidgets.METRICS to stringResource(R.string.status_widget_metrics),
        StatusDashboardWidgets.BUILD_ACTIVITY to stringResource(R.string.status_widget_build_activity),
        StatusDashboardWidgets.DEVICE_REPOSITORY to stringResource(R.string.status_widget_device_repository),
        StatusDashboardWidgets.RECENT_RUNS to stringResource(R.string.status_widget_recent_runs)
    )
    val dashboardLayout = if (state.statusDashboardEditMode) {
        state.statusDashboardDraftLayout ?: state.statusDashboardLayout
    } else {
        state.statusDashboardLayout
    }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var widgetsTrayExpanded by remember { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    var activeDragPointerY by remember { mutableStateOf<Float?>(null) }
    var gridMetrics by remember { mutableStateOf<DashboardGridMetrics?>(null) }
    var trayTopY by remember { mutableStateOf(Float.MAX_VALUE) }
    var hiddenWidgetDrag by remember { mutableStateOf<HiddenWidgetDragState?>(null) }
    val actionMenuRotation by animateFloatAsState(
        targetValue = if (actionMenuExpanded) 45f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "status-layout-fab-rotation"
    )
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val payload = readTextFromUri(context, uri)
                vm.importStatusDashboardLayoutJson(payload)
            }.onSuccess { result ->
                val messageRes = if (result.error == null) {
                    R.string.status_layout_import_success
                } else {
                    R.string.status_layout_import_failed_reset
                }
                val message = if (result.error == null) {
                    context.getString(
                        messageRes,
                        result.importedItemCount,
                        result.ignoredItemCount
                    )
                } else {
                    context.getString(messageRes)
                }
                vm.showSnackbar(
                    message,
                    longDuration = result.error != null
                )
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
    LaunchedEffect(state.statusDashboardEditMode) {
        if (!state.statusDashboardEditMode) {
            actionMenuExpanded = false
            widgetsTrayExpanded = false
            hiddenWidgetDrag = null
            activeDragPointerY = null
        }
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
                scrollState.scrollBy(delta)
            }
            delay(16)
        }
    }

    LaunchedEffect(Unit) { vm.loadRecentRuns() }

    Scaffold(
        containerColor = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
        topBar = {
            ExpressiveTopBar(
                title = if (state.statusDashboardEditMode) {
                    stringResource(R.string.status_layout_editor_title)
                } else {
                    stringResource(R.string.app_name)
                },
                compactTitle = true,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (!state.statusDashboardEditMode) {
                        IconButton(onClick = onToggleRuntimeNavigation) {
                            Icon(
                                imageVector = if (runtimeNavigationEnabled) Icons.Default.SwapHoriz else Icons.Default.Home,
                                contentDescription = if (runtimeNavigationEnabled) {
                                    stringResource(R.string.nav_status)
                                } else {
                                    stringResource(R.string.nav_home)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val editorDockHeight = 92.dp
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .onGloballyPositioned { viewportHeightPx = it.size.height.toFloat() }
        ) {
            val ksuVersion = remember(state.rootGranted) {
                if (state.rootGranted) RootUtils.getKsuVersion() else "N/A"
            }
            val kernelVersion = remember(state.rootGranted) {
                RootUtils.getKernelVersion()
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AbkScreenHorizontalPadding)
                    .padding(
                        bottom = if (state.statusDashboardEditMode) {
                            editorDockHeight + 28.dp
                        } else {
                            80.dp + outerPadding.calculateBottomPadding()
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardGrid(
                    layout = dashboardLayout,
                    widgetLabels = widgetLabels,
                    editable = state.statusDashboardEditMode,
                    canMoveItem = { widgetId, targetX, targetY ->
                        com.abk.kernel.dashboard.DashboardLayoutEngine.canMoveItem(
                            layout = dashboardLayout,
                            widgetId = widgetId,
                            targetX = targetX,
                            targetY = targetY,
                            definitions = StatusDashboardWidgets.definitions
                        )
                    },
                    canResizeItem = { widgetId, targetW, targetH ->
                        com.abk.kernel.dashboard.DashboardLayoutEngine.canResizeItem(
                            layout = dashboardLayout,
                            widgetId = widgetId,
                            targetW = targetW,
                            targetH = targetH,
                            definitions = StatusDashboardWidgets.definitions
                        )
                    },
                    canHideWidget = { widgetId ->
                        StatusDashboardWidgets.definitionMap[widgetId]?.canHide == true
                    },
                    canMinimizeWidget = { widgetId ->
                        StatusDashboardWidgets.definitionMap[widgetId]?.canResize == true
                    },
                    canMaximizeWidget = { widgetId ->
                        StatusDashboardWidgets.definitionMap[widgetId]?.canResize == true
                    },
                    canResizeWidget = { widgetId ->
                        StatusDashboardWidgets.definitionMap[widgetId]?.canResize == true
                    },
                    onMoveItem = vm::moveStatusDashboardWidget,
                    onResizeItem = vm::resizeStatusDashboardWidget,
                    onSetItemSpanMode = vm::setStatusDashboardWidgetSpanMode,
                    onHideItem = { widgetId -> vm.setStatusDashboardWidgetVisible(widgetId, false) },
                    onGridMetricsChanged = { metrics -> gridMetrics = metrics },
                    onDragPointerYChanged = { activeDragPointerY = it }
                ) { widgetId, interactionsEnabled ->
                    StatusWidgetContent(
                        widgetId = widgetId,
                        state = state,
                        vm = vm,
                        ksuVersion = ksuVersion,
                        kernelVersion = kernelVersion,
                        actionsEnabled = interactionsEnabled,
                        showManagerPlaceholder = state.statusDashboardEditMode
                    )
                }
            }

            if (state.statusDashboardEditMode) {
                StatusEditorWidgetsTray(
                    visible = widgetsTrayExpanded,
                    hiddenItems = dashboardLayout.items.filter { !it.visible }.map { it.widgetId },
                    widgetLabels = widgetLabels,
                    dashboardLayout = dashboardLayout,
                    state = state,
                    vm = vm,
                    ksuVersion = ksuVersion,
                    kernelVersion = kernelVersion,
                    onHiddenWidgetDrag = { dragState ->
                        hiddenWidgetDrag = dragState
                        activeDragPointerY = dragState?.pointerY
                    },
                    onHiddenWidgetDrop = { dragState ->
                        activeDragPointerY = null
                        hiddenWidgetDrag = null
                        val metrics = gridMetrics ?: return@StatusEditorWidgetsTray
                        if (!dragState.materialized || dragState.pointerY >= trayTopY) return@StatusEditorWidgetsTray
                        val item = dashboardLayout.items.firstOrNull { it.widgetId == dragState.widgetId } ?: return@StatusEditorWidgetsTray
                        val targetX = ((dragState.pointerX - metrics.originX) / (metrics.cellWidthPx + metrics.gapPx))
                            .roundToInt()
                            .coerceIn(0, (metrics.columns - item.w).coerceAtLeast(0))
                        val targetY = ((dragState.pointerY - metrics.originY) / (metrics.rowHeightPx + metrics.gapPx))
                            .roundToInt()
                            .coerceAtLeast(0)
                        vm.placeStatusDashboardHiddenWidget(
                            widgetId = dragState.widgetId,
                            targetX = targetX,
                            targetY = targetY
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onGloballyPositioned { trayTopY = it.positionInRoot().y }
                )
                if (!widgetsTrayExpanded) {
                    StatusEditorBottomDock(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                StatusEditorFabMenu(
                    expanded = actionMenuExpanded,
                    rotation = actionMenuRotation,
                    onToggle = { actionMenuExpanded = !actionMenuExpanded },
                    onImport = {
                        actionMenuExpanded = false
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    onShare = {
                        actionMenuExpanded = false
                        shareStatusLayout(context, vm.exportStatusDashboardLayoutJson())
                    },
                    onSaveAndExit = {
                        actionMenuExpanded = false
                        vm.saveStatusDashboardLayoutDraft()
                    },
                    onToggleWidgets = {
                        widgetsTrayExpanded = !widgetsTrayExpanded
                        actionMenuExpanded = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 28.dp)
                )
                HiddenWidgetFloatingPreview(
                    dragState = hiddenWidgetDrag,
                    trayTopY = trayTopY,
                    gridMetrics = gridMetrics,
                    layout = dashboardLayout,
                    state = state,
                    vm = vm,
                    ksuVersion = ksuVersion,
                    kernelVersion = kernelVersion
                )
            }
        }
    }
}

@Composable
private fun StatusWidgetContent(
    widgetId: String,
    state: MainUiState,
    vm: MainViewModel,
    ksuVersion: String,
    kernelVersion: String,
    actionsEnabled: Boolean,
    showManagerPlaceholder: Boolean
) {
    when (widgetId) {
        StatusDashboardWidgets.HERO -> StatusHeroWidget(
            state = state,
            vm = vm,
            actionsEnabled = actionsEnabled
        )
        StatusDashboardWidgets.METRICS -> StatusMetricsWidget(
            state = state,
            ksuVersion = ksuVersion
        )
        StatusDashboardWidgets.BUILD_ACTIVITY -> StatusBuildActivityWidget(
            state = state,
            vm = vm,
            actionsEnabled = actionsEnabled,
            showManagerPlaceholder = showManagerPlaceholder
        )
        StatusDashboardWidgets.DEVICE_REPOSITORY -> StatusDeviceRepositoryWidget(
            state = state,
            kernelVersion = kernelVersion,
            ksuVersion = ksuVersion
        )
        StatusDashboardWidgets.RECENT_RUNS -> StatusRecentRunsWidget(
            state = state,
            actionsEnabled = actionsEnabled,
            onCancel = vm::cancelWorkflowRun
        )
    }
}

@Composable
private fun StatusEditorWidgetsTray(
    visible: Boolean,
    hiddenItems: List<String>,
    widgetLabels: Map<String, String>,
    dashboardLayout: com.abk.kernel.dashboard.DashboardLayout,
    state: MainUiState,
    vm: MainViewModel,
    ksuVersion: String,
    kernelVersion: String,
    onHiddenWidgetDrag: (HiddenWidgetDragState?) -> Unit,
    onHiddenWidgetDrop: (HiddenWidgetDragState) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 },
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 },
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            border = BorderStroke(2.dp, Color(0xFFF6B94C)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                hiddenItems.forEach { widgetId ->
                    HiddenWidgetThumbnail(
                        widgetId = widgetId,
                        label = widgetLabels[widgetId] ?: widgetId,
                        layout = dashboardLayout,
                        state = state,
                        vm = vm,
                        ksuVersion = ksuVersion,
                        kernelVersion = kernelVersion,
                        onDragChanged = onHiddenWidgetDrag,
                        onDragReleased = onHiddenWidgetDrop
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusEditorBottomDock(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .drawBehind {
                val accent = Color(0xFFF6B94C)
                val stripeWidth = size.width / 10f
                var x = -size.height * 1.5f
                while (x < size.width + size.height) {
                    drawLine(
                        color = accent,
                        start = androidx.compose.ui.geometry.Offset(x, size.height),
                        end = androidx.compose.ui.geometry.Offset(x + size.height, 0f),
                        strokeWidth = stripeWidth
                    )
                    x += stripeWidth * 2f
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, Color(0xFFF6B94C)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {}
}

@Composable
private fun StatusEditorFabMenu(
    expanded: Boolean,
    rotation: Float,
    onToggle: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onSaveAndExit: () -> Unit,
    onToggleWidgets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(156.dp), contentAlignment = Alignment.BottomEnd) {
        StatusEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.UploadFile,
            contentDescription = stringResource(R.string.status_layout_import),
            offsetX = (-4).dp,
            offsetY = (-96).dp,
            onClick = onImport
        )
        StatusEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.status_layout_share),
            offsetX = (-44).dp,
            offsetY = (-82).dp,
            onClick = onShare
        )
        StatusEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.Save,
            contentDescription = stringResource(R.string.status_layout_save_exit),
            offsetX = (-78).dp,
            offsetY = (-48).dp,
            onClick = onSaveAndExit
        )
        StatusEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.Widgets,
            contentDescription = stringResource(R.string.status_layout_widgets),
            offsetX = (-92).dp,
            offsetY = (-4).dp,
            onClick = onToggleWidgets
        )
        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.status_layout_actions),
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun StatusEditorMiniFab(
    visible: Boolean,
    icon: ImageVector,
    contentDescription: String,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = Modifier.offset(x = offsetX, y = offsetY)
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun HiddenWidgetThumbnail(
    widgetId: String,
    label: String,
    layout: com.abk.kernel.dashboard.DashboardLayout,
    state: MainUiState,
    vm: MainViewModel,
    ksuVersion: String,
    kernelVersion: String,
    onDragChanged: (HiddenWidgetDragState?) -> Unit,
    onDragReleased: (HiddenWidgetDragState) -> Unit
) {
    var originX by remember { mutableStateOf(0f) }
    var originY by remember { mutableStateOf(0f) }
    val item = remember(layout.items, widgetId) {
        layout.items.firstOrNull { it.widgetId == widgetId }
    } ?: return

    Column(
        modifier = Modifier
            .width(110.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                originX = position.x
                originY = position.y
            }
            .pointerInput(widgetId, item.w, item.h) {
                var dragX = 0f
                var dragY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        dragX = originX + offset.x
                        dragY = originY + offset.y
                        onDragChanged(
                            HiddenWidgetDragState(
                                widgetId = widgetId,
                                pointerX = dragX,
                                pointerY = dragY,
                                materialized = false
                            )
                        )
                    },
                    onDragCancel = {
                        onDragChanged(null)
                    },
                    onDragEnd = {
                        val finalState = HiddenWidgetDragState(
                            widgetId = widgetId,
                            pointerX = dragX,
                            pointerY = dragY,
                            materialized = true
                        )
                        onDragReleased(finalState)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragX += dragAmount.x
                    dragY += dragAmount.y
                    onDragChanged(
                        HiddenWidgetDragState(
                            widgetId = widgetId,
                            pointerX = dragX,
                            pointerY = dragY,
                            materialized = true
                        )
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = MaterialTheme.shapes.large,
            color = uiSurfaceColor(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = 0.30f,
                            scaleY = 0.30f,
                            transformOrigin = TransformOrigin(0f, 0f)
                        )
                ) {
                    StatusWidgetContent(
                        widgetId = widgetId,
                        state = state,
                        vm = vm,
                        ksuVersion = ksuVersion,
                        kernelVersion = kernelVersion,
                        actionsEnabled = false,
                        showManagerPlaceholder = true
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HiddenWidgetFloatingPreview(
    dragState: HiddenWidgetDragState?,
    trayTopY: Float,
    gridMetrics: DashboardGridMetrics?,
    layout: com.abk.kernel.dashboard.DashboardLayout,
    state: MainUiState,
    vm: MainViewModel,
    ksuVersion: String,
    kernelVersion: String
) {
    val activeDrag = dragState ?: return
    if (!activeDrag.materialized || activeDrag.pointerY >= trayTopY) return
    val metrics = gridMetrics ?: return
    val density = LocalDensity.current
    val item = layout.items.firstOrNull { it.widgetId == activeDrag.widgetId } ?: return
    val previewWidthDp = with(density) {
        (metrics.cellWidthPx * item.w + metrics.gapPx * (item.w - 1)).toDp()
    }
    val previewHeightDp = with(density) {
        (metrics.rowHeightPx * item.h + metrics.gapPx * (item.h - 1)).toDp()
    }
    val gridStepX = metrics.cellWidthPx + metrics.gapPx
    val gridStepY = metrics.rowHeightPx + metrics.gapPx
    val snappedX = ((activeDrag.pointerX - metrics.originX) / gridStepX)
        .roundToInt()
        .coerceIn(0, (metrics.columns - item.w).coerceAtLeast(0))
    val snappedY = ((activeDrag.pointerY - metrics.originY) / gridStepY)
        .roundToInt()
        .coerceAtLeast(0)
    val snappedLeftPx = metrics.originX + snappedX * gridStepX
    val snappedTopPx = metrics.originY + snappedY * gridStepY
    val previewLeftDp = with(density) { snappedLeftPx.toDp() }
    val previewTopDp = with(density) { snappedTopPx.toDp() }

    Box(
        modifier = Modifier
            .offset(x = previewLeftDp, y = previewTopDp)
            .size(previewWidthDp, previewHeightDp)
            .graphicsLayer { alpha = 0.92f }
    ) {
        StatusWidgetContent(
            widgetId = activeDrag.widgetId,
            state = state,
            vm = vm,
            ksuVersion = ksuVersion,
            kernelVersion = kernelVersion,
            actionsEnabled = false,
            showManagerPlaceholder = true
        )
    }
}

@Composable
private fun StatusHeroWidget(
    state: MainUiState,
    vm: MainViewModel,
    actionsEnabled: Boolean
) {
    ExpressiveHeroCard(
        title = if (state.rootGranted) stringResource(R.string.status_working) else stringResource(R.string.status_partially_active),
        subtitle = if (state.rootGranted) {
            when {
                state.activeBuildRuns.size > 1 -> stringResource(R.string.status_parallel_build_number, state.activeBuildRuns.size)
                state.currentRun != null -> state.currentRun?.let { stringResource(R.string.status_build_number, it.runNumber) }.orEmpty()
                else -> stringResource(R.string.status_version, BuildConfig.VERSION_NAME)
            }
        } else {
            stringResource(R.string.status_version_build_download, BuildConfig.VERSION_NAME)
        },
        icon = if (state.rootGranted) Icons.Default.CheckCircleOutline else Icons.Default.Info,
        containerColor = if (state.rootGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (state.rootGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        badge = {
            ExpressiveStatusChip(
                label = if (state.rootGranted) stringResource(R.string.status_root_authorized) else stringResource(R.string.status_root_unauthorized),
                icon = if (state.rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                color = if (state.rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            ExpressiveStatusChip(
                label = state.forkRepo?.name ?: stringResource(R.string.status_no_fork_detected),
                icon = Icons.Default.ForkRight,
                color = if (state.forkRepo != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        }
    ) {
        if (!state.rootGranted) {
            OutlinedButton(
                onClick = { vm.requestRoot() },
                enabled = actionsEnabled && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.grant_root))
                }
            }
        }
    }
}

@Composable
private fun StatusMetricsWidget(
    state: MainUiState,
    ksuVersion: String
) {
    StatusMetricGrid(
        rootGranted = state.rootGranted,
        forkReady = state.forkRepo != null && state.behindBy <= 0,
        ksuVersion = ksuVersion,
        buildStatus = state.buildStatus
    )
}

@Composable
private fun StatusBuildActivityWidget(
    state: MainUiState,
    vm: MainViewModel,
    actionsEnabled: Boolean,
    showManagerPlaceholder: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusBuildSectionCard(
            title = stringResource(R.string.status_build),
            subtitle = stringResource(R.string.status_progress_sync),
            icon = Icons.Default.RunCircle,
            buildStatus = state.kernelBuildStatus,
            currentRun = state.kernelCurrentRun,
            activeRuns = state.kernelActiveBuildRuns,
            progress = state.kernelBuildProgress,
            cancellingWorkflowRunIds = state.cancellingWorkflowRunIds,
            actionsEnabled = actionsEnabled,
            onCancelRun = vm::cancelWorkflowRun
        )
        if (showManagerPlaceholder || state.managerBuildStatus != BuildStatus.IDLE || state.managerCurrentRun != null) {
            StatusBuildSectionCard(
                title = stringResource(R.string.status_manager_build),
                subtitle = stringResource(R.string.status_manager_progress_sync),
                icon = Icons.Default.Shield,
                buildStatus = state.managerBuildStatus,
                currentRun = state.managerCurrentRun,
                activeRuns = state.managerActiveBuildRuns,
                progress = state.managerBuildProgress,
                cancellingWorkflowRunIds = state.cancellingWorkflowRunIds,
                actionsEnabled = actionsEnabled,
                onCancelRun = vm::cancelWorkflowRun
            )
        }
    }
}

@Composable
private fun StatusBuildSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    buildStatus: BuildStatus,
    currentRun: WorkflowRun?,
    activeRuns: List<WorkflowRun>,
    progress: com.abk.kernel.data.model.BuildProgress,
    cancellingWorkflowRunIds: Set<Long>,
    actionsEnabled: Boolean,
    onCancelRun: (Long) -> Unit
) {
    val context = LocalContext.current
    ExpressiveSectionCard(
        title = title,
        subtitle = subtitle,
        icon = icon,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        when (buildStatus) {
            BuildStatus.IDLE -> StatusRow(Icons.Default.HourglassEmpty, stringResource(R.string.status_no_running_build), false)
            BuildStatus.QUEUED -> StatusRow(
                Icons.Default.Queue,
                if (activeRuns.size > 1) {
                    stringResource(R.string.status_parallel_build_waiting_runner, activeRuns.size)
                } else {
                    stringResource(R.string.status_build_waiting_runner)
                },
                false
            )
            BuildStatus.IN_PROGRESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("${progress.percent}% · ${progress.currentStep}")
            }
            BuildStatus.SUCCESS -> StatusRow(Icons.Default.CheckCircle, stringResource(R.string.status_recent_build_success), false)
            BuildStatus.FAILURE -> StatusRow(Icons.Default.Error, stringResource(R.string.status_recent_build_failed), true)
            BuildStatus.CANCELLED -> StatusRow(Icons.Default.Cancel, stringResource(R.string.status_build_cancelled), true)
        }
        if (currentRun != null && progress.totalSteps > 0) {
            Spacer(Modifier.height(8.dp))
            val animatedProgress by animateFloatAsState(
                targetValue = (progress.percent / 100f).coerceIn(0f, 1f),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "status-widget-progress-$title"
            )
            ShimmerLinearProgress(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    R.string.status_steps_complete,
                    progress.completedSteps,
                    progress.totalSteps
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val showSingleRunAction = activeRuns.size <= 1
        currentRun?.takeIf { showSingleRunAction }?.let { run ->
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))
                            )
                        }
                    },
                    enabled = actionsEnabled,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.status_view_details, run.runNumber), style = MaterialTheme.typography.labelMedium)
                }
                if (run.isActiveStatusRun()) {
                    TextButton(
                        onClick = { onCancelRun(run.id) },
                        enabled = actionsEnabled && run.id !in cancellingWorkflowRunIds,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        if (run.id in cancellingWorkflowRunIds) {
                            LoadingIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (run.id in cancellingWorkflowRunIds) {
                                stringResource(R.string.status_cancelling)
                            } else {
                                stringResource(R.string.status_cancel)
                            }
                        )
                    }
                }
            }
        }
        if (activeRuns.size > 1) {
            Text(
                stringResource(R.string.status_parallel_workflows_desc, activeRuns.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusDeviceRepositoryWidget(
    state: MainUiState,
    kernelVersion: String,
    ksuVersion: String
) {
    ExpressiveSectionCard(
        title = stringResource(R.string.status_device_repo_title),
        subtitle = stringResource(R.string.status_device_repo_subtitle),
        icon = Icons.Default.Memory
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceInfoRow(
                icon = Icons.Default.Memory,
                label = stringResource(R.string.status_kernel),
                value = kernelVersion,
                isError = false
            )
            DeviceInfoRow(
                icon = Icons.Default.Shield,
                label = "KSU",
                value = ksuVersion,
                isError = ksuVersion == "N/A"
            )
        }
        state.user?.let { user ->
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
            AccountRepositoryRow(
                avatarUrl = user.avatarUrl,
                login = user.login,
                repository = state.forkRepo?.fullName ?: stringResource(R.string.status_no_fork)
            )
        }
        if (state.behindBy > 0) {
            StatusRow(Icons.Default.Warning, stringResource(R.string.status_fork_behind, state.behindBy), true)
        }
    }
}

@Composable
private fun StatusRecentRunsWidget(
    state: MainUiState,
    actionsEnabled: Boolean,
    onCancel: (Long) -> Unit
) {
    ExpressiveSectionCard(
        title = stringResource(R.string.status_recent_runs_title),
        subtitle = stringResource(R.string.status_recent_runs_subtitle),
        icon = Icons.Default.History
    ) {
        if (state.recentRuns.isEmpty()) {
            Text(
                text = stringResource(R.string.status_no_build),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recentRuns.take(5).forEach { run ->
                    RunListItem(
                        run = run,
                        cancelling = run.id in state.cancellingWorkflowRunIds,
                        actionsEnabled = actionsEnabled,
                        onCancel = { onCancel(run.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    isError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountRepositoryRow(
    avatarUrl: String,
    login: String,
    repository: String
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = repository,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusMetricGrid(
    rootGranted: Boolean,
    forkReady: Boolean,
    ksuVersion: String,
    buildStatus: BuildStatus
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCard(
                label = stringResource(R.string.status_root),
                value = if (rootGranted) stringResource(R.string.status_authorized) else stringResource(R.string.status_partially_active),
                icon = if (rootGranted) Icons.Default.Lock else Icons.Default.LockOpen,
                color = if (rootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = stringResource(R.string.status_fork),
                value = if (forkReady) stringResource(R.string.status_synced) else stringResource(R.string.status_pending_check),
                icon = Icons.Default.ForkRight,
                color = if (forkReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatusMetricCard(
                label = "KernelSU",
                value = if (ksuVersion == "N/A") stringResource(R.string.status_not_detected) else stringResource(R.string.status_detected),
                icon = Icons.Default.Shield,
                color = if (ksuVersion == "N/A") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = stringResource(R.string.status_build),
                value = buildStatusDisplay(buildStatus),
                icon = Icons.Default.RunCircle,
                color = buildStatusColor(buildStatus),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val animatedColor by animateColorAsState(
        color,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "metric-color"
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = animatedColor, modifier = Modifier.size(22.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isError: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            icon, null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RunListItem(
    run: WorkflowRun,
    cancelling: Boolean,
    actionsEnabled: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                run.displayTitle ?: run.name ?: "#${run.runNumber}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                run.createdAt.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val (color, label) = when {
            run.status == "completed" && run.conclusion == "success" ->
                MaterialTheme.colorScheme.primary to stringResource(R.string.status_success)
            run.status == "completed" && run.conclusion == "cancelled" ->
                MaterialTheme.colorScheme.outline to stringResource(R.string.status_cancelled_label)
            run.status == "completed" ->
                MaterialTheme.colorScheme.error to stringResource(R.string.status_failure)
            run.status == "in_progress" ->
                MaterialTheme.colorScheme.tertiary to stringResource(R.string.status_in_progress)
            else -> MaterialTheme.colorScheme.outline to run.status
        }
        Badge(containerColor = color.copy(alpha = 0.15f)) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
        if (run.isActiveStatusRun()) {
            IconButton(onClick = onCancel, enabled = actionsEnabled && !cancelling) {
                if (cancelling) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = stringResource(R.string.status_cancel_workflow),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun WorkflowRun.isActiveStatusRun(): Boolean =
    status in setOf("queued", "waiting", "requested", "pending", "in_progress")

@Composable
private fun buildStatusDisplay(status: BuildStatus): String = when (status) {
    BuildStatus.IDLE -> stringResource(R.string.status_idle)
    BuildStatus.QUEUED -> stringResource(R.string.status_queued)
    BuildStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    BuildStatus.SUCCESS -> stringResource(R.string.status_success)
    BuildStatus.FAILURE -> stringResource(R.string.status_failure)
    BuildStatus.CANCELLED -> stringResource(R.string.status_stopped)
}

@Composable
private fun buildStatusColor(status: BuildStatus) = when (status) {
    BuildStatus.IDLE -> MaterialTheme.colorScheme.outline
    BuildStatus.QUEUED -> MaterialTheme.colorScheme.tertiary
    BuildStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    BuildStatus.FAILURE -> MaterialTheme.colorScheme.error
    BuildStatus.CANCELLED -> MaterialTheme.colorScheme.outline
}

private suspend fun readTextFromUri(
    context: android.content.Context,
    uri: Uri
): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        reader.readText()
    } ?: error("Unable to open imported layout")
}

private fun shareStatusLayout(
    context: android.content.Context,
    payload: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.status_layout_editor_title))
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.status_layout_share)))
}
