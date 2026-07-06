package com.abk.kernel.dashboard

import com.google.gson.Gson

enum class DashboardLayoutImportError {
    INVALID_JSON,
    UNSUPPORTED_PAGE,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_LAYOUT_MODE,
    EMPTY_LAYOUT
}

data class DashboardLayoutImportResult(
    val layout: DashboardLayout,
    val importedItemCount: Int,
    val ignoredItemCount: Int,
    val error: DashboardLayoutImportError? = null,
    val appliedDefaultFallback: Boolean = false
)

object DashboardLayoutCodec {
    private val gson = Gson()

    fun export(layout: DashboardLayout): String = gson.toJson(
        DashboardLayoutDto(
            version = layout.version,
            pageId = layout.pageId.rawValue,
            layoutMode = layout.layoutMode.rawValue,
            densityPreset = layout.densityPreset.rawValue,
            items = layout.items.map { item ->
                DashboardLayoutItemDto(
                    widgetId = item.widgetId,
                    x = item.x,
                    y = item.y,
                    w = item.w,
                    h = item.h,
                    visible = item.visible,
                    spanMode = item.spanMode.rawValue
                )
            }
        )
    )

    fun importStatusLayout(
        json: String,
        definitions: Collection<BuiltinWidgetDefinition>,
        defaultLayoutForDensity: (DashboardDensityPreset) -> DashboardLayout,
        hideMissingWidgets: Boolean = true
    ): DashboardLayoutImportResult {
        val dto = runCatching {
            gson.fromJson(json, DashboardLayoutDto::class.java)
        }.getOrNull() ?: return failure(
            error = DashboardLayoutImportError.INVALID_JSON,
            fallbackLayout = defaultLayoutForDensity(DashboardDensityPreset.STANDARD)
        )

        val densityPreset = DashboardDensityPreset.fromRawValue(dto.densityPreset)
        val fallbackLayout = defaultLayoutForDensity(densityPreset)
        val pageId = DashboardPageId.fromRawValue(dto.pageId)
            ?: return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_PAGE,
                fallbackLayout = fallbackLayout
            )
        if (pageId != DashboardPageId.STATUS) {
            return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_PAGE,
                fallbackLayout = fallbackLayout
            )
        }
        if (dto.version != DASHBOARD_LAYOUT_VERSION) {
            return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_VERSION,
                fallbackLayout = fallbackLayout
            )
        }
        val layoutMode = DashboardLayoutMode.fromRawValue(dto.layoutMode)
            ?: return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_LAYOUT_MODE,
                fallbackLayout = fallbackLayout
            )
        if (layoutMode != DashboardLayoutMode.GRID) {
            return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_LAYOUT_MODE,
                fallbackLayout = fallbackLayout
            )
        }

        val knownWidgetIds = definitions.map { it.widgetId }.toSet()
        val rawItems = dto.items.orEmpty()
        val importedItems = rawItems.mapNotNull { itemDto ->
            val widgetId = itemDto.widgetId?.trim().orEmpty()
            if (widgetId.isBlank() || widgetId !in knownWidgetIds) {
                null
            } else {
                DashboardLayoutItem(
                    widgetId = widgetId,
                    x = itemDto.x ?: 0,
                    y = itemDto.y ?: 0,
                    w = itemDto.w ?: 1,
                    h = itemDto.h ?: 1,
                    visible = itemDto.visible ?: true,
                    spanMode = DashboardItemSpanMode.fromRawValue(itemDto.spanMode)
                )
            }
        }
        val ignoredItemCount = rawItems.size - importedItems.size
        if (importedItems.isEmpty()) {
            return failure(
                error = DashboardLayoutImportError.EMPTY_LAYOUT,
                fallbackLayout = fallbackLayout,
                ignoredItemCount = ignoredItemCount
            )
        }

        val sanitizedLayout = DashboardLayoutEngine.sanitize(
            layout = DashboardLayout(
                version = DASHBOARD_LAYOUT_VERSION,
                pageId = DashboardPageId.STATUS,
                layoutMode = DashboardLayoutMode.GRID,
                densityPreset = densityPreset,
                items = importedItems
            ),
            definitions = definitions,
            defaultLayout = fallbackLayout,
            appendMissingDefinitions = false
        )
        val importedWidgetIds = sanitizedLayout.items.map { it.widgetId }.toSet()
        val hiddenMissingItems = fallbackLayout.items
            .filter { it.widgetId !in importedWidgetIds }
            .map { it.copy(visible = !hideMissingWidgets) }

        return DashboardLayoutImportResult(
            layout = sanitizedLayout.copy(items = sanitizedLayout.items + hiddenMissingItems),
            importedItemCount = importedItems.size,
            ignoredItemCount = ignoredItemCount,
            error = null,
            appliedDefaultFallback = false
        )
    }

    private fun failure(
        error: DashboardLayoutImportError,
        fallbackLayout: DashboardLayout,
        ignoredItemCount: Int = 0
    ): DashboardLayoutImportResult = DashboardLayoutImportResult(
        layout = fallbackLayout,
        importedItemCount = 0,
        ignoredItemCount = ignoredItemCount,
        error = error,
        appliedDefaultFallback = true
    )

    private data class DashboardLayoutDto(
        val version: Int? = null,
        val pageId: String? = null,
        val layoutMode: String? = null,
        val densityPreset: String? = null,
        val items: List<DashboardLayoutItemDto>? = null
    )

    private data class DashboardLayoutItemDto(
        val widgetId: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val w: Int? = null,
        val h: Int? = null,
        val visible: Boolean? = null,
        val spanMode: String? = null
    )
}
