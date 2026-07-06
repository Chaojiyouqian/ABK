package com.abk.kernel.dashboard

import kotlin.math.roundToInt

object DashboardLayoutEngine {
    private const val GRID_GAP_DP = 4
    private const val FREEFORM_CELL_WIDTH_COMPACT_DP = 16
    private const val FREEFORM_CELL_WIDTH_STANDARD_DP = 20
    private const val FREEFORM_CELL_WIDTH_RELAXED_DP = 24

    fun changeMode(
        layout: DashboardLayout,
        targetMode: DashboardLayoutMode,
        definitions: Collection<BuiltinWidgetDefinition>,
        defaultLayout: DashboardLayout
    ): DashboardLayout {
        if (layout.layoutMode == targetMode) {
            return sanitize(
                layout = layout.copy(layoutMode = targetMode),
                definitions = definitions,
                defaultLayout = defaultLayout
            )
        }
        val remappedItems = when (targetMode) {
            DashboardLayoutMode.GRID -> layout.items.map { item ->
                freeformToGrid(item, layout.densityPreset)
            }
            DashboardLayoutMode.FREEFORM -> layout.items.map { item ->
                gridToFreeform(item, layout.densityPreset)
            }
        }
        return sanitize(
            layout = layout.copy(
                layoutMode = targetMode,
                items = remappedItems
            ),
            definitions = definitions,
            defaultLayout = defaultLayout
        )
    }

    fun sanitize(
        layout: DashboardLayout,
        definitions: Collection<BuiltinWidgetDefinition>,
        defaultLayout: DashboardLayout,
        appendMissingDefinitions: Boolean = true
    ): DashboardLayout {
        val definitionMap = definitions.associateBy { it.widgetId }
        val defaultItemsById = defaultLayout.items.associateBy { it.widgetId }
        val inputItemsById = layout.items.associateBy { it.widgetId }
        val orderedInputItems = layout.items.sortedWith(
            compareBy<DashboardLayoutItem> { !it.visible }
                .thenBy { it.y }
                .thenBy { it.x }
                .thenBy { it.widgetId }
        )
        val orderedWidgetIds = buildList {
            val seen = linkedSetOf<String>()
            orderedInputItems.forEach { item ->
                if (item.widgetId in definitionMap && seen.add(item.widgetId)) {
                    add(item.widgetId)
                }
            }
            if (appendMissingDefinitions) {
                defaultLayout.items.forEach { item ->
                    if (item.widgetId in definitionMap && seen.add(item.widgetId)) {
                        add(item.widgetId)
                    }
                }
            }
        }
        val placedVisibleItems = mutableListOf<DashboardLayoutItem>()
        val hiddenItems = mutableListOf<DashboardLayoutItem>()

        orderedWidgetIds.forEach { widgetId ->
            val definition = definitionMap[widgetId] ?: return@forEach
            val sourceItem = inputItemsById[widgetId]
                ?: defaultItemsById[widgetId]
                ?: DashboardLayoutItem(
                    widgetId = widgetId,
                    x = 0,
                    y = 0,
                    w = definition.defaultW,
                    h = definition.defaultH,
                    visible = definition.defaultVisible
                )
            val normalizedItem = normalizeItem(
                item = sourceItem,
                definition = definition,
                columns = layout.densityPreset.columns,
                layoutMode = layout.layoutMode
            )
            if (!normalizedItem.visible) {
                hiddenItems += normalizedItem
            } else if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
                placedVisibleItems += normalizedItem
            } else {
                placedVisibleItems += placeVisibleItemNear(
                    item = normalizedItem,
                    definition = definition,
                    columns = layout.densityPreset.columns,
                    occupiedItems = placedVisibleItems
                )
            }
        }

        return layout.copy(
            version = DASHBOARD_LAYOUT_VERSION,
            pageId = DashboardPageId.STATUS,
            layoutMode = layout.layoutMode,
            items = placedVisibleItems + hiddenItems
        )
    }

    fun remapDensity(
        layout: DashboardLayout,
        targetDensityPreset: DashboardDensityPreset,
        definitions: Collection<BuiltinWidgetDefinition>,
        defaultLayout: DashboardLayout
    ): DashboardLayout {
        if (layout.densityPreset == targetDensityPreset) {
            return sanitize(
                layout = layout.copy(densityPreset = targetDensityPreset),
                definitions = definitions,
                defaultLayout = defaultLayout
            )
        }
        if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
            return sanitize(
                layout = layout.copy(densityPreset = targetDensityPreset),
                definitions = definitions,
                defaultLayout = defaultLayout
            )
        }
        val sourceColumns = layout.densityPreset.columns.toFloat().coerceAtLeast(1f)
        val targetColumns = targetDensityPreset.columns.toFloat()
        val remappedItems = layout.items.map { item ->
            val remappedWidth = ((item.w / sourceColumns) * targetColumns).roundToInt()
            val remappedX = ((item.x / sourceColumns) * targetColumns).roundToInt()
            item.copy(
                x = remappedX,
                w = remappedWidth
            )
        }
        return sanitize(
            layout = layout.copy(
                densityPreset = targetDensityPreset,
                items = remappedItems
            ),
            definitions = definitions,
            defaultLayout = defaultLayout
        )
    }

    fun isLayoutLegal(
        layout: DashboardLayout,
        definitions: Collection<BuiltinWidgetDefinition>
    ): Boolean {
        val definitionMap = definitions.associateBy { it.widgetId }
        val visibleItems = layout.items.filter { it.visible }
        visibleItems.forEach { item ->
            val definition = definitionMap[item.widgetId] ?: return false
            val normalized = normalizeItem(item, definition, layout.densityPreset.columns, layout.layoutMode)
            if (normalized != item) return false
        }
        if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
            return true
        }
        return visibleItems.indices.none { index ->
            visibleItems.drop(index + 1).any { other ->
                overlaps(visibleItems[index], other)
            }
        }
    }

    fun contentRowCount(layout: DashboardLayout): Int =
        layout.items
            .filter { it.visible }
            .maxOfOrNull { it.bottom }
            ?: 0

    fun canMoveItem(
        layout: DashboardLayout,
        widgetId: String,
        targetX: Int,
        targetY: Int,
        definitions: Collection<BuiltinWidgetDefinition>
    ): Boolean {
        val definitionMap = definitions.associateBy { it.widgetId }
        val item = layout.items.firstOrNull { it.widgetId == widgetId } ?: return false
        val definition = definitionMap[widgetId] ?: return false
        if (!item.visible) return false
        val candidate = normalizeItem(
            item = item.copy(x = targetX, y = targetY),
            definition = definition,
            columns = layout.densityPreset.columns,
            layoutMode = layout.layoutMode
        )
        if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
            return candidate.x >= 0 && candidate.y >= 0
        }
        return isAreaFree(
            candidate = candidate,
            occupiedItems = layout.items.filter { it.visible && it.widgetId != widgetId },
            columns = layout.densityPreset.columns
        )
    }

    fun moveItemExact(
        layout: DashboardLayout,
        widgetId: String,
        targetX: Int,
        targetY: Int,
        definitions: Collection<BuiltinWidgetDefinition>
    ): DashboardLayout {
        if (!canMoveItem(layout, widgetId, targetX, targetY, definitions)) {
            return layout
        }
        val definitionMap = definitions.associateBy { it.widgetId }
        val definition = definitionMap[widgetId] ?: return layout
        return replaceItem(
            layout = layout,
            widgetId = widgetId,
            newItem = normalizeItem(
                item = requireNotNull(layout.items.firstOrNull { it.widgetId == widgetId })
                    .copy(x = targetX, y = targetY),
                definition = definition,
                columns = layout.densityPreset.columns,
                layoutMode = layout.layoutMode
            )
        )
    }

    fun canResizeItem(
        layout: DashboardLayout,
        widgetId: String,
        targetW: Int,
        targetH: Int,
        definitions: Collection<BuiltinWidgetDefinition>
    ): Boolean {
        val definitionMap = definitions.associateBy { it.widgetId }
        val item = layout.items.firstOrNull { it.widgetId == widgetId } ?: return false
        val definition = definitionMap[widgetId] ?: return false
        if (!item.visible || !definition.canResize) return false
        val candidate = normalizeItem(
            item = item.copy(w = targetW, h = targetH),
            definition = definition,
            columns = layout.densityPreset.columns,
            layoutMode = layout.layoutMode
        )
        if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
            return candidate.w > 0 && candidate.h > 0
        }
        return isAreaFree(
            candidate = candidate,
            occupiedItems = layout.items.filter { it.visible && it.widgetId != widgetId },
            columns = layout.densityPreset.columns
        )
    }

    fun resizeItemExact(
        layout: DashboardLayout,
        widgetId: String,
        targetW: Int,
        targetH: Int,
        definitions: Collection<BuiltinWidgetDefinition>
    ): DashboardLayout {
        if (!canResizeItem(layout, widgetId, targetW, targetH, definitions)) {
            return layout
        }
        val definitionMap = definitions.associateBy { it.widgetId }
        val definition = definitionMap[widgetId] ?: return layout
        return replaceItem(
            layout = layout,
            widgetId = widgetId,
            newItem = normalizeItem(
                item = requireNotNull(layout.items.firstOrNull { it.widgetId == widgetId })
                    .copy(
                        w = targetW,
                        h = targetH,
                        spanMode = DashboardItemSpanMode.CUSTOM
                    ),
                definition = definition,
                columns = layout.densityPreset.columns,
                layoutMode = layout.layoutMode
            )
        )
    }

    fun setItemSpanMode(
        layout: DashboardLayout,
        widgetId: String,
        spanMode: DashboardItemSpanMode,
        definitions: Collection<BuiltinWidgetDefinition>
    ): DashboardLayout {
        val definitionMap = definitions.associateBy { it.widgetId }
        val item = layout.items.firstOrNull { it.widgetId == widgetId } ?: return layout
        val definition = definitionMap[widgetId] ?: return layout
        if (!item.visible) return layout
        val sizedItem = normalizeItem(
            item = item.copy(spanMode = spanMode),
            definition = definition,
            columns = layout.densityPreset.columns,
            layoutMode = layout.layoutMode
        )
        if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
            return replaceItem(layout, widgetId, sizedItem)
        }
        val placedItem = placeVisibleItemNear(
            item = sizedItem,
            definition = definition,
            columns = layout.densityPreset.columns,
            occupiedItems = layout.items.filter { it.visible && it.widgetId != widgetId }
        )
        return replaceItem(layout, widgetId, placedItem)
    }

    fun setItemVisibility(
        layout: DashboardLayout,
        widgetId: String,
        visible: Boolean,
        definitions: Collection<BuiltinWidgetDefinition>
    ): DashboardLayout {
        val definitionMap = definitions.associateBy { it.widgetId }
        val item = layout.items.firstOrNull { it.widgetId == widgetId } ?: return layout
        val definition = definitionMap[widgetId] ?: return layout
        if (!visible && !definition.canHide) return layout
        if (!visible) {
            return replaceItem(layout, widgetId, item.copy(visible = false))
        }
        if (layout.layoutMode == DashboardLayoutMode.FREEFORM) {
            return replaceItem(
                layout,
                widgetId,
                normalizeItem(
                    item = item.copy(visible = true),
                    definition = definition,
                    columns = layout.densityPreset.columns,
                    layoutMode = layout.layoutMode
                )
            )
        }
        val restoredItem = placeVisibleItemNear(
            item = normalizeItem(
                item = item.copy(visible = true),
                definition = definition,
                columns = layout.densityPreset.columns,
                layoutMode = layout.layoutMode
            ),
            definition = definition,
            columns = layout.densityPreset.columns,
            occupiedItems = layout.items.filter { it.visible && it.widgetId != widgetId }
        )
        return replaceItem(layout, widgetId, restoredItem)
    }

    private fun replaceItem(
        layout: DashboardLayout,
        widgetId: String,
        newItem: DashboardLayoutItem
    ): DashboardLayout = layout.copy(
        items = layout.items.map { item ->
            if (item.widgetId == widgetId) {
                newItem
            } else {
                item
            }
        }
    )

    private fun normalizeItem(
        item: DashboardLayoutItem,
        definition: BuiltinWidgetDefinition,
        columns: Int,
        layoutMode: DashboardLayoutMode
    ): DashboardLayoutItem {
        val sizedItem = resolveSizedItem(
            item = item,
            definition = definition,
            columns = columns,
            layoutMode = layoutMode
        )
        if (layoutMode == DashboardLayoutMode.FREEFORM) {
            val width = sizedItem.w.coerceAtLeast(1)
            val height = sizedItem.h.coerceAtLeast(1)
            return sizedItem.copy(
                x = sizedItem.x.coerceAtLeast(0),
                y = sizedItem.y.coerceAtLeast(0),
                w = width,
                h = height
            )
        }
        val width = when (sizedItem.spanMode) {
            DashboardItemSpanMode.CUSTOM -> sizedItem.w.coerceIn(1, columns.coerceAtLeast(1))
            else -> {
                val maxWidth = (definition.maxW ?: columns).coerceAtMost(columns).coerceAtLeast(1)
                sizedItem.w.coerceIn(1, maxWidth)
            }
        }
        val height = when (sizedItem.spanMode) {
            DashboardItemSpanMode.CUSTOM -> sizedItem.h.coerceAtLeast(1)
            else -> {
                val maxHeight = (definition.maxH ?: Int.MAX_VALUE).coerceAtLeast(1)
                sizedItem.h.coerceIn(1, maxHeight)
            }
        }
        val x = item.x.coerceIn(0, (columns - width).coerceAtLeast(0))
        val y = item.y.coerceAtLeast(0)
        return sizedItem.copy(x = x, y = y, w = width, h = height)
    }

    private fun resolveSizedItem(
        item: DashboardLayoutItem,
        definition: BuiltinWidgetDefinition,
        columns: Int,
        layoutMode: DashboardLayoutMode
    ): DashboardLayoutItem {
        if (layoutMode == DashboardLayoutMode.FREEFORM && item.spanMode == DashboardItemSpanMode.CUSTOM) {
            return item
        }
        val freeformCellWidthDp = freeformCellWidthDp(columns = columns)
        val freeformDefaultW = definition.defaultW * freeformCellWidthDp + (definition.defaultW - 1) * GRID_GAP_DP
        val freeformMinW = (definition.collapsedW ?: definition.minW) * freeformCellWidthDp +
            ((definition.collapsedW ?: definition.minW) - 1) * GRID_GAP_DP
        val freeformMaxW = (definition.expandedW ?: definition.maxW ?: definition.defaultW) * freeformCellWidthDp +
            ((definition.expandedW ?: definition.maxW ?: definition.defaultW) - 1) * GRID_GAP_DP
        val targetWidth = when (item.spanMode) {
            DashboardItemSpanMode.MINIMUM -> if (layoutMode == DashboardLayoutMode.FREEFORM) freeformMinW else definition.collapsedW ?: definition.minW
            DashboardItemSpanMode.DEFAULT -> if (layoutMode == DashboardLayoutMode.FREEFORM) freeformDefaultW else definition.defaultW
            DashboardItemSpanMode.MAXIMUM -> if (layoutMode == DashboardLayoutMode.FREEFORM) freeformMaxW else definition.expandedW ?: (definition.maxW ?: columns)
            DashboardItemSpanMode.CUSTOM -> item.w
        }
        val targetHeight = when (item.spanMode) {
            DashboardItemSpanMode.MINIMUM -> if (layoutMode == DashboardLayoutMode.FREEFORM) {
                freeformHeightDp(definition.collapsedH ?: definition.minH, columns)
            } else {
                definition.collapsedH ?: definition.minH
            }
            DashboardItemSpanMode.DEFAULT -> if (layoutMode == DashboardLayoutMode.FREEFORM) {
                freeformHeightDp(definition.defaultH, columns)
            } else {
                definition.defaultH
            }
            DashboardItemSpanMode.MAXIMUM -> if (layoutMode == DashboardLayoutMode.FREEFORM) {
                freeformHeightDp(definition.expandedH ?: (definition.maxH ?: definition.defaultH), columns)
            } else {
                definition.expandedH ?: (definition.maxH ?: definition.defaultH)
            }
            DashboardItemSpanMode.CUSTOM -> item.h
        }
        return item.copy(w = targetWidth, h = targetHeight)
    }

    private fun placeVisibleItemNear(
        item: DashboardLayoutItem,
        definition: BuiltinWidgetDefinition,
        columns: Int,
        occupiedItems: List<DashboardLayoutItem>
    ): DashboardLayoutItem {
        val normalized = normalizeItem(item, definition, columns)
        if (isAreaFree(normalized, occupiedItems, columns)) {
            return normalized
        }
        val occupiedBottom = occupiedItems.maxOfOrNull { it.bottom } ?: 0
        val scanEndRow = occupiedBottom + normalized.h + 64
        val maxX = (columns - normalized.w).coerceAtLeast(0)
        for (row in normalized.y..scanEndRow) {
            for (column in 0..maxX) {
                val candidate = normalized.copy(x = column, y = row)
                if (isAreaFree(candidate, occupiedItems, columns)) {
                    return candidate
                }
            }
        }
        return normalized.copy(x = 0, y = occupiedBottom)
    }

    private fun isAreaFree(
        candidate: DashboardLayoutItem,
        occupiedItems: List<DashboardLayoutItem>,
        columns: Int
    ): Boolean {
        if (candidate.x < 0 || candidate.y < 0) return false
        if (candidate.w <= 0 || candidate.h <= 0) return false
        if (candidate.right > columns) return false
        return occupiedItems.none { other -> overlaps(candidate, other) }
    }

    private fun overlaps(
        left: DashboardLayoutItem,
        right: DashboardLayoutItem
    ): Boolean = left.x < right.right &&
        left.right > right.x &&
        left.y < right.bottom &&
        left.bottom > right.y

    private fun gridToFreeform(
        item: DashboardLayoutItem,
        densityPreset: DashboardDensityPreset
    ): DashboardLayoutItem {
        val cellWidth = freeformCellWidthDp(densityPreset)
        val x = item.x * (cellWidth + GRID_GAP_DP)
        val y = item.y * (densityPreset.rowHeightDp + GRID_GAP_DP)
        val w = item.w * cellWidth + (item.w - 1) * GRID_GAP_DP
        val h = item.h * densityPreset.rowHeightDp + (item.h - 1) * GRID_GAP_DP
        return item.copy(x = x, y = y, w = w, h = h)
    }

    private fun freeformToGrid(
        item: DashboardLayoutItem,
        densityPreset: DashboardDensityPreset
    ): DashboardLayoutItem {
        val cellWidth = freeformCellWidthDp(densityPreset)
        val columnStep = (cellWidth + GRID_GAP_DP).toFloat().coerceAtLeast(1f)
        val rowStep = (densityPreset.rowHeightDp + GRID_GAP_DP).toFloat().coerceAtLeast(1f)
        val gridX = (item.x / columnStep).roundToInt().coerceAtLeast(0)
        val gridY = (item.y / rowStep).roundToInt().coerceAtLeast(0)
        val gridW = ((item.w + GRID_GAP_DP) / columnStep).roundToInt().coerceAtLeast(1)
        val gridH = ((item.h + GRID_GAP_DP) / rowStep).roundToInt().coerceAtLeast(1)
        return item.copy(x = gridX, y = gridY, w = gridW, h = gridH)
    }

    private fun freeformCellWidthDp(densityPreset: DashboardDensityPreset): Int = when (densityPreset) {
        DashboardDensityPreset.COMPACT -> FREEFORM_CELL_WIDTH_COMPACT_DP
        DashboardDensityPreset.STANDARD -> FREEFORM_CELL_WIDTH_STANDARD_DP
        DashboardDensityPreset.RELAXED -> FREEFORM_CELL_WIDTH_RELAXED_DP
    }

    private fun freeformCellWidthDp(columns: Int): Int = when {
        columns >= 20 -> FREEFORM_CELL_WIDTH_COMPACT_DP
        columns <= 12 -> FREEFORM_CELL_WIDTH_RELAXED_DP
        else -> FREEFORM_CELL_WIDTH_STANDARD_DP
    }

    private fun freeformHeightDp(rows: Int, columns: Int): Int =
        rows * rowHeightDp(columns) + (rows - 1) * GRID_GAP_DP

    private fun rowHeightDp(columns: Int): Int = when {
        columns >= 20 -> DashboardDensityPreset.COMPACT.rowHeightDp
        columns <= 12 -> DashboardDensityPreset.RELAXED.rowHeightDp
        else -> DashboardDensityPreset.STANDARD.rowHeightDp
    }
}
