package com.abk.kernel.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.abk.kernel.dashboard.DashboardItemSpanMode
import com.abk.kernel.dashboard.DashboardLayout
import com.abk.kernel.dashboard.DashboardLayoutItem
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlin.math.max
import kotlin.math.roundToInt

data class DashboardGridMetrics(
    val originX: Float,
    val originY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val cellWidthPx: Float,
    val rowHeightPx: Float,
    val gapPx: Float,
    val columns: Int
)

@Composable
fun DashboardGrid(
    layout: DashboardLayout,
    widgetLabels: Map<String, String>,
    editable: Boolean,
    canMoveItem: (String, Int, Int) -> Boolean,
    canResizeItem: (String, Int, Int) -> Boolean,
    canHideWidget: (String) -> Boolean,
    canMinimizeWidget: (String) -> Boolean,
    canMaximizeWidget: (String) -> Boolean,
    canResizeWidget: (String) -> Boolean,
    onMoveItem: (String, Int, Int) -> Unit,
    onResizeItem: (String, Int, Int) -> Unit,
    onSetItemSpanMode: (String, DashboardItemSpanMode) -> Unit,
    onHideItem: (String) -> Unit,
    selectedWidgetId: String? = null,
    onSelectWidget: (String?) -> Unit = {},
    onGridMetricsChanged: (DashboardGridMetrics) -> Unit = {},
    onDragPointerYChanged: (Float?) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (widgetId: String, interactionsEnabled: Boolean) -> Unit
) {
    val visibleItems = remember(layout.items) {
        layout.items
            .filter { it.visible }
            .sortedWith(
                compareBy<DashboardLayoutItem> { it.y }
                    .thenBy { it.x }
                    .thenBy { it.widgetId }
            )
    }
    val rowHeight = layout.densityPreset.rowHeightDp.dp
    val gridGap = 4.dp
    val layoutRevision = remember(layout.items) { layout.items.hashCode() }
    var previewBottomRow by remember(layoutRevision) { mutableIntStateOf(0) }
    val contentRows = max(
        1,
        max(
            visibleItems.maxOfOrNull { it.bottom } ?: 0,
            previewBottomRow
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val cellWidth = ((maxWidth - gridGap * (layout.densityPreset.columns - 1)) / layout.densityPreset.columns)
            .coerceAtLeast(0.dp)
        val gridHeight = rowHeight * contentRows + gridGap * (contentRows - 1)
        val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (editable) 0.45f else 0f)
        val columnStepPx = with(density) { (cellWidth + gridGap).toPx() }
        val rowStepPx = with(density) { (rowHeight + gridGap).toPx() }
        val gapPx = with(density) { gridGap.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .pointerInput(editable, selectedWidgetId) {
                    if (!editable) return@pointerInput
                    detectTapGestures(onTap = { onSelectWidget(null) })
                }
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onGridMetricsChanged(
                        DashboardGridMetrics(
                            originX = position.x,
                            originY = position.y,
                            widthPx = coordinates.size.width.toFloat(),
                            heightPx = coordinates.size.height.toFloat(),
                            cellWidthPx = columnStepPx - gapPx,
                            rowHeightPx = rowStepPx - gapPx,
                            gapPx = gapPx,
                            columns = layout.densityPreset.columns
                        )
                    )
                }
                .then(
                    if (editable) {
                        Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainerLowest))
                    } else {
                        Modifier
                    }
                )
                .drawBehind {
                    if (!editable) return@drawBehind
                    val lineColor = outlineColor
                    for (column in 0..layout.densityPreset.columns) {
                        val x = (column * columnStepPx - gapPx / 2f).coerceAtLeast(0f)
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (row in 0..contentRows) {
                        val y = (row * rowStepPx - gapPx / 2f).coerceAtLeast(0f)
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }
                }
        ) {
            visibleItems.forEach { item ->
                DashboardGridItem(
                    item = item,
                    editable = editable,
                    title = widgetLabels[item.widgetId] ?: item.widgetId,
                    canHide = canHideWidget(item.widgetId),
                    canMinimize = canMinimizeWidget(item.widgetId),
                    canMaximize = canMaximizeWidget(item.widgetId),
                    canResize = canResizeWidget(item.widgetId),
                    spanMode = item.spanMode,
                    selected = selectedWidgetId == item.widgetId,
                    cellWidth = cellWidth,
                    rowHeight = rowHeight,
                    gridGap = gridGap,
                    layoutRevision = layoutRevision,
                    columns = layout.densityPreset.columns,
                    isMoveValid = { x, y -> canMoveItem(item.widgetId, x, y) },
                    isResizeValid = { w, h -> canResizeItem(item.widgetId, w, h) },
                    onMoveItem = { x, y -> onMoveItem(item.widgetId, x, y) },
                    onResizeItem = { w, h -> onResizeItem(item.widgetId, w, h) },
                    onSetSpanMode = { spanMode -> onSetItemSpanMode(item.widgetId, spanMode) },
                    onHideItem = { onHideItem(item.widgetId) },
                    onSelect = { onSelectWidget(item.widgetId) },
                    onDragPointerYChanged = onDragPointerYChanged,
                    onPreviewBottomRowChanged = { nextBottom ->
                        previewBottomRow = nextBottom ?: 0
                    }
                ) {
                    content(item.widgetId, !editable)
                }
            }
        }
    }
}

private enum class GridInteractionMode {
    MOVE,
    RESIZE
}

private data class GridPreviewState(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val valid: Boolean,
    val mode: GridInteractionMode
)

@Composable
private fun DashboardGridItem(
    item: DashboardLayoutItem,
    editable: Boolean,
    title: String,
    canHide: Boolean,
    canMinimize: Boolean,
    canMaximize: Boolean,
    canResize: Boolean,
    spanMode: DashboardItemSpanMode,
    selected: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    gridGap: androidx.compose.ui.unit.Dp,
    layoutRevision: Int,
    columns: Int,
    isMoveValid: (Int, Int) -> Boolean,
    isResizeValid: (Int, Int) -> Boolean,
    onMoveItem: (Int, Int) -> Unit,
    onResizeItem: (Int, Int) -> Unit,
    onSetSpanMode: (DashboardItemSpanMode) -> Unit,
    onHideItem: () -> Unit,
    onSelect: () -> Unit,
    onDragPointerYChanged: (Float?) -> Unit,
    onPreviewBottomRowChanged: (Int?) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val columnStepPx = remember(cellWidth, gridGap, density) {
        with(density) { (cellWidth + gridGap).toPx() }
    }
    val rowStepPx = remember(rowHeight, gridGap, density) {
        with(density) { (rowHeight + gridGap).toPx() }
    }
    var previewState by remember(item.widgetId, item.x, item.y, item.w, item.h) {
        mutableStateOf<GridPreviewState?>(null)
    }
    var overlayOriginY by remember { mutableStateOf(0f) }

    val displayX = previewState?.x ?: item.x
    val displayY = previewState?.y ?: item.y
    val displayW = previewState?.w ?: item.w
    val displayH = previewState?.h ?: item.h
    val displayWidth = cellWidth * displayW + gridGap * (displayW - 1)
    val displayHeight = rowHeight * displayH + gridGap * (displayH - 1)
    val outlineColor = when {
        !editable -> Color.Transparent
        previewState?.valid == false -> MaterialTheme.colorScheme.error
        previewState?.mode == GridInteractionMode.RESIZE -> MaterialTheme.colorScheme.secondary
        previewState?.mode == GridInteractionMode.MOVE -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .offset(
                x = (cellWidth + gridGap) * displayX,
                y = (rowHeight + gridGap) * displayY
            )
            .size(displayWidth, displayHeight)
            .zIndex(if (previewState != null) 2f else 0f)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.extraLarge)
                .border(
                    width = if (editable) 1.dp else 0.dp,
                    color = outlineColor,
                    shape = MaterialTheme.shapes.extraLarge
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            shadowElevation = if (previewState != null) 8.dp else 0.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        overlayOriginY = coordinates.positionInRoot().y
                    }
                    .pointerInput(item.widgetId, layoutRevision, selected) {
                        detectTapGestures(onTap = { onSelect() })
                    }
                    .pointerInput(item.widgetId, item.x, item.y, item.w, item.h, layoutRevision) {
                        var accumulatedDx = 0f
                        var accumulatedDy = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accumulatedDx = 0f
                                accumulatedDy = 0f
                                onSelect()
                                previewState = GridPreviewState(
                                    x = item.x,
                                    y = item.y,
                                    w = item.w,
                                    h = item.h,
                                    valid = true,
                                    mode = GridInteractionMode.MOVE
                                )
                            },
                            onDragCancel = {
                                onDragPointerYChanged(null)
                                onPreviewBottomRowChanged(null)
                                previewState = null
                            },
                            onDragEnd = {
                                onDragPointerYChanged(null)
                                onPreviewBottomRowChanged(null)
                                previewState?.takeIf { it.valid }?.let { preview ->
                                    onMoveItem(preview.x, preview.y)
                                }
                                previewState = null
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            onDragPointerYChanged(overlayOriginY + change.position.y)
                            accumulatedDx += dragAmount.x
                            accumulatedDy += dragAmount.y
                            val current = previewState ?: GridPreviewState(
                                x = item.x,
                                y = item.y,
                                w = item.w,
                                h = item.h,
                                    valid = true,
                                    mode = GridInteractionMode.MOVE
                            )
                            val nextX = item.x + (accumulatedDx / columnStepPx).roundToInt()
                            val nextY = item.y + (accumulatedDy / rowStepPx).roundToInt()
                            val boundedX = nextX.coerceIn(0, (columns - current.w).coerceAtLeast(0))
                            val boundedY = nextY.coerceAtLeast(0)
                            previewState = current.copy(
                                x = boundedX,
                                y = boundedY,
                                valid = isMoveValid(boundedX, boundedY),
                                mode = GridInteractionMode.MOVE
                            )
                            onPreviewBottomRowChanged(previewState?.let { it.y + it.h })
                        }
                    }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                shape = MaterialTheme.shapes.small,
                color = uiSurfaceColor(MaterialTheme.colorScheme.surface),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (selected) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .wrapContentWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canMinimize) {
                        DashboardModeToken(
                            label = "MIN",
                            selected = spanMode == DashboardItemSpanMode.MINIMUM,
                            onClick = {
                                onSetSpanMode(
                                    if (spanMode == DashboardItemSpanMode.MINIMUM) {
                                        DashboardItemSpanMode.DEFAULT
                                    } else {
                                        DashboardItemSpanMode.MINIMUM
                                    }
                                )
                            }
                        )
                    }
                    if (canMaximize) {
                        DashboardModeToken(
                            label = "MAX",
                            selected = spanMode == DashboardItemSpanMode.MAXIMUM,
                            onClick = {
                                onSetSpanMode(
                                    if (spanMode == DashboardItemSpanMode.MAXIMUM) {
                                        DashboardItemSpanMode.DEFAULT
                                    } else {
                                        DashboardItemSpanMode.MAXIMUM
                                    }
                                )
                            }
                        )
                    }
                    if (canHide) {
                        IconButton(
                            onClick = onHideItem,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    }
                }
            }

            if (selected && canResize) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(uiSurfaceColor(MaterialTheme.colorScheme.surface))
                        .pointerInput(item.widgetId, item.w, item.h, layoutRevision) {
                            var accumulatedDx = 0f
                            var accumulatedDy = 0f
                            detectDragGestures(
                                onDragStart = {
                                    accumulatedDx = 0f
                                    accumulatedDy = 0f
                                    onSelect()
                                    previewState = GridPreviewState(
                                        x = item.x,
                                        y = item.y,
                                        w = item.w,
                                        h = item.h,
                                        valid = true,
                                        mode = GridInteractionMode.RESIZE
                                    )
                                },
                                onDragCancel = {
                                    onDragPointerYChanged(null)
                                    onPreviewBottomRowChanged(null)
                                    previewState = null
                                },
                                onDragEnd = {
                                    onDragPointerYChanged(null)
                                    onPreviewBottomRowChanged(null)
                                    previewState?.takeIf { it.valid }?.let { preview ->
                                        onResizeItem(preview.w, preview.h)
                                    }
                                    previewState = null
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                onDragPointerYChanged(overlayOriginY + change.position.y)
                                accumulatedDx += dragAmount.x
                                accumulatedDy += dragAmount.y
                                val current = previewState ?: GridPreviewState(
                                    x = item.x,
                                    y = item.y,
                                    w = item.w,
                                    h = item.h,
                                    valid = true,
                                    mode = GridInteractionMode.RESIZE
                                )
                                val nextW = item.w + (accumulatedDx / columnStepPx).roundToInt()
                                val nextH = item.h + (accumulatedDy / rowStepPx).roundToInt()
                                val boundedW = nextW.coerceIn(1, (columns - item.x).coerceAtLeast(1))
                                val boundedH = nextH.coerceAtLeast(1)
                                previewState = current.copy(
                                    w = boundedW,
                                    h = boundedH,
                                    valid = isResizeValid(boundedW, boundedH),
                                    mode = GridInteractionMode.RESIZE
                                )
                                onPreviewBottomRowChanged(previewState?.let { it.y + it.h })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardModeToken(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    uiSurfaceColor(MaterialTheme.colorScheme.surface)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
