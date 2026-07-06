package com.abk.kernel.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.abk.kernel.dashboard.DashboardItemSpanMode
import com.abk.kernel.dashboard.DashboardLayout
import com.abk.kernel.dashboard.DashboardLayoutItem
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlin.math.max
import kotlin.math.roundToInt

data class DashboardFreeformMetrics(
    val originX: Float,
    val originY: Float,
    val widthPx: Float,
    val heightPx: Float
)

@Composable
fun DashboardFreeform(
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
    onCanvasMetricsChanged: (DashboardFreeformMetrics) -> Unit = {},
    onDragPointerYChanged: (Float?) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (widgetId: String, interactionsEnabled: Boolean) -> Unit
) {
    val visibleItems = remember(layout.items) {
        layout.items.filter { it.visible }.sortedWith(compareBy<DashboardLayoutItem> { it.y }.thenBy { it.x }.thenBy { it.widgetId })
    }
    val layoutRevision = remember(layout.items) { layout.items.hashCode() }
    var previewBottomDp by remember(layoutRevision) { mutableIntStateOf(0) }
    val contentHeightDp = max(1, max(visibleItems.maxOfOrNull { it.bottom } ?: 0, previewBottomDp))

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeightDp.dp)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onCanvasMetricsChanged(
                        DashboardFreeformMetrics(
                            originX = position.x,
                            originY = position.y,
                            widthPx = coordinates.size.width.toFloat(),
                            heightPx = coordinates.size.height.toFloat()
                        )
                    )
                }
                .clip(MaterialTheme.shapes.extraLarge)
                .background(uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainerLowest))
                .pointerInput(editable, selectedWidgetId) {
                    if (!editable) return@pointerInput
                    detectTapGestures(onTap = { onSelectWidget(null) })
                }
        ) {
            visibleItems.forEach { item ->
                DashboardFreeformItem(
                    item = item,
                    editable = editable,
                    title = widgetLabels[item.widgetId] ?: item.widgetId,
                    canHide = canHideWidget(item.widgetId),
                    canMinimize = canMinimizeWidget(item.widgetId),
                    canMaximize = canMaximizeWidget(item.widgetId),
                    canResize = canResizeWidget(item.widgetId),
                    spanMode = item.spanMode,
                    selected = selectedWidgetId == item.widgetId,
                    layoutRevision = layoutRevision,
                    canvasWidthPx = canvasWidthPx,
                    isMoveValid = { x, y -> canMoveItem(item.widgetId, x, y) },
                    isResizeValid = { w, h -> canResizeItem(item.widgetId, w, h) },
                    onMoveItem = { x, y -> onMoveItem(item.widgetId, x, y) },
                    onResizeItem = { w, h -> onResizeItem(item.widgetId, w, h) },
                    onSetSpanMode = { spanMode -> onSetItemSpanMode(item.widgetId, spanMode) },
                    onHideItem = { onHideItem(item.widgetId) },
                    onSelect = { onSelectWidget(item.widgetId) },
                    onDragPointerYChanged = onDragPointerYChanged,
                    onPreviewBottomDpChanged = { nextBottom -> previewBottomDp = nextBottom ?: 0 }
                ) {
                    content(item.widgetId, !editable)
                }
            }
        }
    }
}

private data class FreeformPreviewState(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val valid: Boolean,
    val mode: FreeformInteractionMode
)

private enum class FreeformInteractionMode {
    MOVE,
    RESIZE
}

@Composable
private fun DashboardFreeformItem(
    item: DashboardLayoutItem,
    editable: Boolean,
    title: String,
    canHide: Boolean,
    canMinimize: Boolean,
    canMaximize: Boolean,
    canResize: Boolean,
    spanMode: DashboardItemSpanMode,
    selected: Boolean,
    layoutRevision: Int,
    canvasWidthPx: Float,
    isMoveValid: (Int, Int) -> Boolean,
    isResizeValid: (Int, Int) -> Boolean,
    onMoveItem: (Int, Int) -> Unit,
    onResizeItem: (Int, Int) -> Unit,
    onSetSpanMode: (DashboardItemSpanMode) -> Unit,
    onHideItem: () -> Unit,
    onSelect: () -> Unit,
    onDragPointerYChanged: (Float?) -> Unit,
    onPreviewBottomDpChanged: (Int?) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val widthPx = with(density) { item.w.dp.toPx() }
    val heightPx = with(density) { item.h.dp.toPx() }
    var previewState by remember(item.widgetId, item.x, item.y, item.w, item.h) {
        mutableStateOf<FreeformPreviewState?>(null)
    }
    var overlayOriginY by remember { mutableStateOf(0f) }
    val displayX = previewState?.x ?: item.x
    val displayY = previewState?.y ?: item.y
    val displayW = previewState?.w ?: item.w
    val displayH = previewState?.h ?: item.h
    val outlineColor = when {
        !editable -> androidx.compose.ui.graphics.Color.Transparent
        previewState?.valid == false -> MaterialTheme.colorScheme.error
        previewState?.mode == FreeformInteractionMode.RESIZE -> MaterialTheme.colorScheme.secondary
        previewState?.mode == FreeformInteractionMode.MOVE -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }

    Box(
        modifier = Modifier
            .offset(x = displayX.dp, y = displayY.dp)
            .size(displayW.dp, displayH.dp)
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
            color = androidx.compose.ui.graphics.Color.Transparent,
            shadowElevation = if (previewState != null) 8.dp else 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        detectDragGestures(
                            onDragStart = {
                                accumulatedDx = 0f
                                accumulatedDy = 0f
                                onSelect()
                                previewState = FreeformPreviewState(
                                    x = item.x,
                                    y = item.y,
                                    w = item.w,
                                    h = item.h,
                                    valid = true,
                                    mode = FreeformInteractionMode.MOVE
                                )
                            },
                            onDragCancel = {
                                onDragPointerYChanged(null)
                                onPreviewBottomDpChanged(null)
                                previewState = null
                            },
                            onDragEnd = {
                                onDragPointerYChanged(null)
                                onPreviewBottomDpChanged(null)
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
                            val nextX = (item.x + with(density) { accumulatedDx.toDp().value }).roundToInt()
                            val nextY = (item.y + with(density) { accumulatedDy.toDp().value }).roundToInt()
                            val boundedX = nextX.coerceIn(0, ((canvasWidthPx - widthPx).coerceAtLeast(0f) / density.density).roundToInt())
                            val boundedY = nextY.coerceAtLeast(0)
                            previewState = FreeformPreviewState(
                                x = boundedX,
                                y = boundedY,
                                w = item.w,
                                h = item.h,
                                valid = isMoveValid(boundedX, boundedY),
                                mode = FreeformInteractionMode.MOVE
                            )
                            onPreviewBottomDpChanged(previewState?.let { it.y + it.h })
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
                        DashboardFreeformModeToken(
                            label = "MIN",
                            selected = spanMode == DashboardItemSpanMode.MINIMUM,
                            onClick = {
                                onSetSpanMode(
                                    if (spanMode == DashboardItemSpanMode.MINIMUM) DashboardItemSpanMode.DEFAULT
                                    else DashboardItemSpanMode.MINIMUM
                                )
                            }
                        )
                    }
                    if (canMaximize) {
                        DashboardFreeformModeToken(
                            label = "MAX",
                            selected = spanMode == DashboardItemSpanMode.MAXIMUM,
                            onClick = {
                                onSetSpanMode(
                                    if (spanMode == DashboardItemSpanMode.MAXIMUM) DashboardItemSpanMode.DEFAULT
                                    else DashboardItemSpanMode.MAXIMUM
                                )
                            }
                        )
                    }
                    if (canHide) {
                        IconButton(
                            onClick = onHideItem,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null)
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
                                    previewState = FreeformPreviewState(
                                        x = item.x,
                                        y = item.y,
                                        w = item.w,
                                        h = item.h,
                                        valid = true,
                                        mode = FreeformInteractionMode.RESIZE
                                    )
                                },
                                onDragCancel = {
                                    onDragPointerYChanged(null)
                                    onPreviewBottomDpChanged(null)
                                    previewState = null
                                },
                                onDragEnd = {
                                    onDragPointerYChanged(null)
                                    onPreviewBottomDpChanged(null)
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
                                val nextW = (item.w + with(density) { accumulatedDx.toDp().value }).roundToInt().coerceAtLeast(1)
                                val nextH = (item.h + with(density) { accumulatedDy.toDp().value }).roundToInt().coerceAtLeast(1)
                                previewState = FreeformPreviewState(
                                    x = item.x,
                                    y = item.y,
                                    w = nextW,
                                    h = nextH,
                                    valid = isResizeValid(nextW, nextH),
                                    mode = FreeformInteractionMode.RESIZE
                                )
                                onPreviewBottomDpChanged(previewState?.let { it.y + it.h })
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
private fun DashboardFreeformModeToken(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else uiSurfaceColor(MaterialTheme.colorScheme.surface)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
