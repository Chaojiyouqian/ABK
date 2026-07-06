package com.abk.kernel.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.abk.kernel.dashboard.DashboardLayout
import com.abk.kernel.dashboard.DashboardLayoutItem
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DashboardGrid(
    layout: DashboardLayout,
    widgetLabels: Map<String, String>,
    editable: Boolean,
    canMoveItem: (String, Int, Int) -> Boolean,
    canResizeItem: (String, Int, Int) -> Boolean,
    canHideWidget: (String) -> Boolean,
    canResizeWidget: (String) -> Boolean,
    onMoveItem: (String, Int, Int) -> Unit,
    onResizeItem: (String, Int, Int) -> Unit,
    onHideItem: (String) -> Unit,
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
    val gridGap = 8.dp
    val contentRows = max(
        1,
        visibleItems.maxOfOrNull { it.bottom } ?: 0
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
                .clip(MaterialTheme.shapes.extraLarge)
                .background(uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainerLowest))
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
                    canResize = canResizeWidget(item.widgetId),
                    cellWidth = cellWidth,
                    rowHeight = rowHeight,
                    gridGap = gridGap,
                    columns = layout.densityPreset.columns,
                    isMoveValid = { x, y -> canMoveItem(item.widgetId, x, y) },
                    isResizeValid = { w, h -> canResizeItem(item.widgetId, w, h) },
                    onMoveItem = { x, y -> onMoveItem(item.widgetId, x, y) },
                    onResizeItem = { w, h -> onResizeItem(item.widgetId, w, h) },
                    onHideItem = { onHideItem(item.widgetId) }
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
    canResize: Boolean,
    cellWidth: androidx.compose.ui.unit.Dp,
    rowHeight: androidx.compose.ui.unit.Dp,
    gridGap: androidx.compose.ui.unit.Dp,
    columns: Int,
    isMoveValid: (Int, Int) -> Boolean,
    isResizeValid: (Int, Int) -> Boolean,
    onMoveItem: (Int, Int) -> Unit,
    onResizeItem: (Int, Int) -> Unit,
    onHideItem: () -> Unit,
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
        else -> MaterialTheme.colorScheme.outlineVariant
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
                    width = if (editable) 1.5.dp else 0.dp,
                    color = outlineColor,
                    shape = MaterialTheme.shapes.extraLarge
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            shadowElevation = if (previewState != null) 8.dp else 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (editable) 8.dp else 0.dp,
                        top = if (editable) 36.dp else 0.dp,
                        end = if (editable) 8.dp else 0.dp,
                        bottom = if (editable) 8.dp else 0.dp
                    )
            ) {
                content()
            }
        }

        if (editable) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.widgetId, item.x, item.y, item.w, item.h) {
                        var accumulatedDx = 0f
                        var accumulatedDy = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                accumulatedDx = 0f
                                accumulatedDy = 0f
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
                                previewState = null
                            },
                            onDragEnd = {
                                previewState?.takeIf { it.valid }?.let { preview ->
                                    onMoveItem(preview.x, preview.y)
                                }
                                previewState = null
                            }
                        ) { change, dragAmount ->
                            change.consume()
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
                        }
                    }
            )

            AssistChip(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                label = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = null
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
                    disabledContainerColor = uiSurfaceColor(MaterialTheme.colorScheme.surface),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            if (canHide) {
                IconButton(
                    onClick = onHideItem,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }

            if (canResize) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(uiSurfaceColor(MaterialTheme.colorScheme.surface))
                        .pointerInput(item.widgetId, item.w, item.h) {
                            var accumulatedDx = 0f
                            var accumulatedDy = 0f
                            detectDragGestures(
                                onDragStart = {
                                    accumulatedDx = 0f
                                    accumulatedDy = 0f
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
                                    previewState = null
                                },
                                onDragEnd = {
                                    previewState?.takeIf { it.valid }?.let { preview ->
                                        onResizeItem(preview.w, preview.h)
                                    }
                                    previewState = null
                                }
                            ) { change, dragAmount ->
                                change.consume()
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
