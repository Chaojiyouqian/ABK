@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.dashboard.BuiltinWidgetDefinition
import com.abk.kernel.dashboard.DashboardLayout
import com.abk.kernel.dashboard.DashboardLayoutEngine
import com.abk.kernel.dashboard.DashboardLayoutItem
import com.abk.kernel.dashboard.DashboardLayoutMode
import com.abk.kernel.ui.dashboard.DashboardFreeformMetrics
import com.abk.kernel.ui.dashboard.DashboardGridMetrics
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class DashboardHiddenWidgetDragState(
    val widgetId: String,
    val pointerX: Float,
    val pointerY: Float,
    val leftTray: Boolean
)

@Composable
internal fun DashboardEditorWidgetsTray(
    visible: Boolean,
    hiddenItems: List<String>,
    widgetLabels: Map<String, String>,
    dashboardLayout: DashboardLayout,
    onHiddenWidgetDrag: (DashboardHiddenWidgetDragState?) -> Unit,
    onHiddenWidgetDrop: (DashboardHiddenWidgetDragState) -> Unit,
    activeDragWidgetId: String?,
    modifier: Modifier = Modifier,
    thumbnailContent: @Composable (widgetId: String) -> Unit
) {
    val dockShape = RoundedCornerShape(28.dp)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
            slideInVertically(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 },
        exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutVertically(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .clip(dockShape)
                .drawBehind {
                    val accent = Color(0xFFF6B94C)
                    val stripeWidth = size.width / 18f
                    var x = -size.height
                    while (x < size.width + size.height) {
                        drawLine(
                            color = accent.copy(alpha = 0.55f),
                            start = androidx.compose.ui.geometry.Offset(x, size.height),
                            end = androidx.compose.ui.geometry.Offset(x + size.height, 0f),
                            strokeWidth = stripeWidth / 3.4f
                        )
                        x += stripeWidth * 1.5f
                    }
                },
            shape = dockShape,
            color = appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)),
            border = BorderStroke(1.5.dp, Color(0xFFF6B94C)),
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
                    DashboardHiddenWidgetThumbnail(
                        widgetId = widgetId,
                        label = widgetLabels[widgetId] ?: widgetId,
                        layout = dashboardLayout,
                        isDragging = activeDragWidgetId == widgetId,
                        onDragChanged = onHiddenWidgetDrag,
                        onDragReleased = onHiddenWidgetDrop
                    ) {
                        thumbnailContent(widgetId)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardEditorFabMenu(
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
        DashboardEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.UploadFile,
            contentDescription = stringResource(R.string.status_layout_import),
            offsetX = (-4).dp,
            offsetY = (-96).dp,
            onClick = onImport
        )
        DashboardEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.status_layout_share),
            offsetX = (-44).dp,
            offsetY = (-82).dp,
            onClick = onShare
        )
        DashboardEditorMiniFab(
            visible = expanded,
            icon = Icons.Default.Save,
            contentDescription = stringResource(R.string.status_layout_save_exit),
            offsetX = (-78).dp,
            offsetY = (-48).dp,
            onClick = onSaveAndExit
        )
        DashboardEditorMiniFab(
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
internal fun DashboardHiddenWidgetFloatingPreview(
    dragState: DashboardHiddenWidgetDragState?,
    trayTopY: Float,
    layoutMode: DashboardLayoutMode,
    gridMetrics: DashboardGridMetrics?,
    freeformMetrics: DashboardFreeformMetrics?,
    layout: DashboardLayout,
    definitions: List<BuiltinWidgetDefinition>
) {
    val activeDrag = dragState ?: return
    if (!activeDrag.leftTray || activeDrag.pointerY >= trayTopY) return
    val density = LocalDensity.current
    val item = layout.items.firstOrNull { it.widgetId == activeDrag.widgetId } ?: return
    val (previewLeftDp, previewTopDp, previewWidthDp, previewHeightDp, borderColor) = when (layoutMode) {
        DashboardLayoutMode.GRID -> {
            val metrics = gridMetrics ?: return
            val previewWidth = with(density) {
                (metrics.cellWidthPx * item.w + metrics.gapPx * (item.w - 1)).toDp()
            }
            val previewHeight = with(density) {
                (metrics.rowHeightPx * item.h + metrics.gapPx * (item.h - 1)).toDp()
            }
            val target = computeHiddenWidgetDropTarget(metrics, item, activeDrag.pointerX, activeDrag.pointerY)
            val isValid = DashboardLayoutEngine.canMoveItem(
                layout = layout,
                widgetId = activeDrag.widgetId,
                targetX = target.first,
                targetY = target.second,
                definitions = definitions
            )
            val previewGridX = if (item.visible) item.x else target.first
            val previewGridY = if (item.visible) item.y else target.second
            val left = with(density) { (metrics.originX + previewGridX * (metrics.cellWidthPx + metrics.gapPx)).toDp() }
            val top = with(density) { (metrics.originY + previewGridY * (metrics.rowHeightPx + metrics.gapPx)).toDp() }
            val color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            PreviewFrame(left, top, previewWidth, previewHeight, color)
        }
        DashboardLayoutMode.FREEFORM -> {
            val metrics = freeformMetrics ?: return
            val previewWidth = item.w.dp
            val previewHeight = item.h.dp
            val target = computeHiddenWidgetFreeformDropTarget(metrics, item, activeDrag.pointerX, activeDrag.pointerY, density)
            val isValid = DashboardLayoutEngine.canMoveItem(
                layout = layout,
                widgetId = activeDrag.widgetId,
                targetX = target.first,
                targetY = target.second,
                definitions = definitions
            )
            val left = if (item.visible) item.x.dp else target.first.dp
            val top = if (item.visible) item.y.dp else target.second.dp
            val color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            PreviewFrame(left, top, previewWidth, previewHeight, color)
        }
    }

    Surface(
        modifier = Modifier
            .offset(x = previewLeftDp, y = previewTopDp)
            .size(previewWidthDp, previewHeightDp)
            .graphicsLayer { alpha = 0.96f },
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(color = borderColor.copy(alpha = 0.08f)) }
        )
    }
}

internal suspend fun readLayoutTextFromUri(
    context: Context,
    uri: Uri
): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
        reader.readText()
    } ?: error("Unable to open imported layout")
}

internal fun shareDashboardLayout(
    context: Context,
    payload: String,
    title: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.status_layout_share)))
}

internal fun computeHiddenWidgetDropTarget(
    metrics: DashboardGridMetrics,
    item: DashboardLayoutItem,
    pointerX: Float,
    pointerY: Float
): Pair<Int, Int> {
    val targetX = ((pointerX - metrics.originX) / (metrics.cellWidthPx + metrics.gapPx))
        .roundToInt()
        .coerceIn(0, (metrics.columns - item.w).coerceAtLeast(0))
    val targetY = ((pointerY - metrics.originY) / (metrics.rowHeightPx + metrics.gapPx))
        .roundToInt()
        .coerceAtLeast(0)
    return targetX to targetY
}

internal fun computeHiddenWidgetFreeformDropTarget(
    metrics: DashboardFreeformMetrics,
    item: DashboardLayoutItem,
    pointerX: Float,
    pointerY: Float,
    density: Density
): Pair<Int, Int> {
    val itemWidthPx = with(density) { item.w.dp.toPx() }
    val itemHeightPx = with(density) { item.h.dp.toPx() }
    val leftPx = (pointerX - metrics.originX - itemWidthPx / 2f).coerceAtLeast(0f)
    val topPx = (pointerY - metrics.originY - itemHeightPx / 2f).coerceAtLeast(0f)
    return with(density) { leftPx.toDp().value.roundToInt() } to
        with(density) { topPx.toDp().value.roundToInt() }
}

@Composable
private fun DashboardEditorMiniFab(
    visible: Boolean,
    icon: ImageVector,
    contentDescription: String,
    offsetX: Dp,
    offsetY: Dp,
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
private fun DashboardHiddenWidgetThumbnail(
    widgetId: String,
    label: String,
    layout: DashboardLayout,
    isDragging: Boolean,
    onDragChanged: (DashboardHiddenWidgetDragState?) -> Unit,
    onDragReleased: (DashboardHiddenWidgetDragState) -> Unit,
    thumbnailContent: @Composable () -> Unit
) {
    var originX by remember { mutableStateOf(0f) }
    var originY by remember { mutableStateOf(0f) }
    val item = remember(layout.items, widgetId) {
        layout.items.firstOrNull { it.widgetId == widgetId }
    } ?: return

    androidx.compose.foundation.layout.Column(
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
                var active = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragX = originX + offset.x
                        dragY = originY + offset.y
                        active = false
                    },
                    onDragCancel = {
                        active = false
                        onDragChanged(null)
                    },
                    onDragEnd = {
                        if (active) {
                            onDragReleased(
                                DashboardHiddenWidgetDragState(
                                    widgetId = widgetId,
                                    pointerX = dragX,
                                    pointerY = dragY,
                                    leftTray = dragY < originY
                                )
                            )
                        }
                        active = false
                    }
                ) { change, dragAmount ->
                    dragX = originX + change.position.x
                    dragY += dragAmount
                    val hasLeftTray = dragY < originY
                    if (hasLeftTray) {
                        active = true
                    }
                    if (active) {
                        change.consume()
                        onDragChanged(
                            DashboardHiddenWidgetDragState(
                                widgetId = widgetId,
                                pointerX = dragX,
                                pointerY = dragY,
                                leftTray = true
                            )
                        )
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .graphicsLayer { alpha = if (isDragging) 0f else 1f },
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
                    thumbnailContent()
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

private data class PreviewFrame(
    val left: Dp,
    val top: Dp,
    val width: Dp,
    val height: Dp,
    val color: Color
)
