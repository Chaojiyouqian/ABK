package com.abk.kernel.dashboard

import kotlin.math.roundToInt

object DashboardLayoutEngine {

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
                columns = layout.densityPreset.columns
            )
            if (!normalizedItem.visible) {
                hiddenItems += normalizedItem
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
            layoutMode = DashboardLayoutMode.GRID,
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
            val normalized = normalizeItem(item, definition, layout.densityPreset.columns)
            if (normalized != item) return false
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
            columns = layout.densityPreset.columns
        )
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
                columns = layout.densityPreset.columns
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
            columns = layout.densityPreset.columns
        )
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
                columns = layout.densityPreset.columns
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
            columns = layout.densityPreset.columns
        )
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
        val restoredItem = placeVisibleItemNear(
            item = normalizeItem(
                item = item.copy(visible = true),
                definition = definition,
                columns = layout.densityPreset.columns
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
        columns: Int
    ): DashboardLayoutItem {
        val sizedItem = resolveSizedItem(
            item = item,
            definition = definition,
            columns = columns
        )
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
        columns: Int
    ): DashboardLayoutItem {
        val targetWidth = when (item.spanMode) {
            DashboardItemSpanMode.MINIMUM -> definition.collapsedW ?: definition.minW
            DashboardItemSpanMode.DEFAULT -> definition.defaultW
            DashboardItemSpanMode.MAXIMUM -> definition.expandedW ?: (definition.maxW ?: columns)
            DashboardItemSpanMode.CUSTOM -> item.w
        }
        val targetHeight = when (item.spanMode) {
            DashboardItemSpanMode.MINIMUM -> definition.collapsedH ?: definition.minH
            DashboardItemSpanMode.DEFAULT -> definition.defaultH
            DashboardItemSpanMode.MAXIMUM -> definition.expandedH ?: (definition.maxH ?: definition.defaultH)
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
}
