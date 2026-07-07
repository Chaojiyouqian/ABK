package com.abk.kernel.dashboard

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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

    fun export(layout: DashboardLayout): String {
        val root = JsonObject().apply {
            addProperty("version", layout.version)
            addProperty("pageId", layout.pageId.rawValue)
            addProperty("layoutMode", layout.layoutMode.rawValue)
            addProperty("densityPreset", layout.densityPreset.rawValue)
            add("items", JsonArray().apply {
                layout.items.forEach { item ->
                    add(JsonObject().apply {
                        addProperty("widgetId", item.widgetId)
                        addProperty("x", item.x)
                        addProperty("y", item.y)
                        addProperty("w", item.w)
                        addProperty("h", item.h)
                        addProperty("visible", item.visible)
                        addProperty("spanMode", item.spanMode.rawValue)
                    })
                }
            })
        }
        return gson.toJson(root)
    }

    fun importStatusLayout(
        json: String,
        definitions: Collection<BuiltinWidgetDefinition>,
        defaultLayoutForDensity: (DashboardDensityPreset) -> DashboardLayout,
        hideMissingWidgets: Boolean = true
    ): DashboardLayoutImportResult = importLayout(
        json = json,
        expectedPageId = DashboardPageId.STATUS,
        definitions = definitions,
        defaultLayoutForDensity = defaultLayoutForDensity,
        hideMissingWidgets = hideMissingWidgets
    )

    fun importLayout(
        json: String,
        expectedPageId: DashboardPageId,
        definitions: Collection<BuiltinWidgetDefinition>,
        defaultLayoutForDensity: (DashboardDensityPreset) -> DashboardLayout,
        hideMissingWidgets: Boolean = true
    ): DashboardLayoutImportResult {
        val root = runCatching {
            JsonParser.parseString(json).asJsonObject
        }.getOrNull() ?: return failure(
            error = DashboardLayoutImportError.INVALID_JSON,
            fallbackLayout = defaultLayoutForDensity(DashboardDensityPreset.STANDARD)
        )

        val densityPreset = DashboardDensityPreset.fromRawValue(
            root.readString("densityPreset", "c")
        )
        val layoutMode = DashboardLayoutMode.fromRawValue(
            root.readString("layoutMode", "b")
        ) ?: return failure(
            error = DashboardLayoutImportError.UNSUPPORTED_LAYOUT_MODE,
            fallbackLayout = defaultLayoutForDensity(densityPreset)
        )
        val gridFallbackLayout = defaultLayoutForDensity(densityPreset)
        val fallbackLayout = when (layoutMode) {
            DashboardLayoutMode.GRID -> gridFallbackLayout
            DashboardLayoutMode.FREEFORM -> DashboardLayoutEngine.changeMode(
                layout = gridFallbackLayout,
                targetMode = DashboardLayoutMode.FREEFORM,
                definitions = definitions,
                defaultLayout = gridFallbackLayout
            )
        }
        val pageId = DashboardPageId.fromRawValue(
            root.readString("pageId", null)
                ?: root.readString("statusPageId", null)
                ?: expectedPageId.rawValue
        )
            ?: return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_PAGE,
                fallbackLayout = fallbackLayout
            )
        if (pageId != expectedPageId) {
            return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_PAGE,
                fallbackLayout = fallbackLayout
            )
        }
        if (root.readInt("version", "a") != DASHBOARD_LAYOUT_VERSION) {
            return failure(
                error = DashboardLayoutImportError.UNSUPPORTED_VERSION,
                fallbackLayout = fallbackLayout
            )
        }
        val knownWidgetIds = definitions.map { it.widgetId }.toSet()
        val rawItems = root.readArray("items", "d")
        val importedItems = rawItems.mapNotNull { itemObject ->
            val widgetId = itemObject.readString("widgetId", "a")?.trim().orEmpty()
            if (widgetId.isBlank() || widgetId !in knownWidgetIds) {
                null
            } else {
                DashboardLayoutItem(
                    widgetId = widgetId,
                    x = itemObject.readInt("x", "b") ?: 0,
                    y = itemObject.readInt("y", "c") ?: 0,
                    w = itemObject.readInt("w", "d") ?: 1,
                    h = itemObject.readInt("h", "e") ?: 1,
                    visible = itemObject.readBoolean("visible", "f") ?: true,
                    spanMode = DashboardItemSpanMode.fromRawValue(
                        itemObject.readString("spanMode", "g")
                    )
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
                layoutMode = layoutMode,
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
}

private fun JsonObject.readString(primaryKey: String, legacyKey: String?): String? {
    val value = get(primaryKey) ?: legacyKey?.let { get(it) } ?: return null
    return value.takeIf { it.isJsonPrimitive }?.asString
}

private fun JsonObject.readInt(primaryKey: String, legacyKey: String?): Int? {
    val value = get(primaryKey) ?: legacyKey?.let { get(it) } ?: return null
    return value.takeIf { it.isJsonPrimitive }?.asInt
}

private fun JsonObject.readBoolean(primaryKey: String, legacyKey: String?): Boolean? {
    val value = get(primaryKey) ?: legacyKey?.let { get(it) } ?: return null
    return value.takeIf { it.isJsonPrimitive }?.asBoolean
}

private fun JsonObject.readArray(primaryKey: String, legacyKey: String?): List<JsonObject> {
    val value = get(primaryKey) ?: legacyKey?.let { get(it) } ?: return emptyList()
    if (!value.isJsonArray) return emptyList()
    return value.asJsonArray.mapNotNull(JsonElement::asJsonObjectOrNull)
}

private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
    if (isJsonObject) asJsonObject else null
