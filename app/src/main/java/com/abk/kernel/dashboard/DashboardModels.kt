package com.abk.kernel.dashboard

const val DASHBOARD_LAYOUT_VERSION = 2

enum class DashboardPageId(val rawValue: String) {
    STATUS("status"),
    BUILD("build"),
    MODULES("modules"),
    FLASH("flash"),
    RUNTIME_HOME("runtime_home"),
    INSTALLED_MODULES("installed_modules"),
    ROOT_AUTH("root_auth"),
    SETTINGS("settings");

    companion object {
        fun fromRawValue(rawValue: String?): DashboardPageId? =
            entries.firstOrNull { it.rawValue == rawValue }
    }
}

enum class DashboardLayoutMode(val rawValue: String) {
    GRID("grid"),
    FREEFORM("freeform");

    companion object {
        fun fromRawValue(rawValue: String?): DashboardLayoutMode? =
            entries.firstOrNull { it.rawValue == rawValue }
    }
}

enum class DashboardDensityPreset(
    val rawValue: String,
    val columns: Int,
    val rowHeightDp: Int
) {
    COMPACT("compact", 20, 40),
    STANDARD("standard", 16, 48),
    RELAXED("relaxed", 12, 56);

    companion object {
        fun fromRawValue(rawValue: String?): DashboardDensityPreset =
            entries.firstOrNull { it.rawValue == rawValue } ?: STANDARD
    }
}

enum class DashboardItemSpanMode(val rawValue: String) {
    MINIMUM("minimum"),
    DEFAULT("default"),
    MAXIMUM("maximum"),
    CUSTOM("custom");

    companion object {
        fun fromRawValue(rawValue: String?): DashboardItemSpanMode =
            entries.firstOrNull { it.rawValue == rawValue } ?: DEFAULT
    }
}

data class DashboardLayout(
    val version: Int = DASHBOARD_LAYOUT_VERSION,
    val pageId: DashboardPageId = DashboardPageId.STATUS,
    val layoutMode: DashboardLayoutMode = DashboardLayoutMode.GRID,
    val densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD,
    val items: List<DashboardLayoutItem> = emptyList()
)

data class DashboardLayoutItem(
    val widgetId: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val visible: Boolean = true,
    val spanMode: DashboardItemSpanMode = DashboardItemSpanMode.DEFAULT
) {
    val right: Int
        get() = x + w

    val bottom: Int
        get() = y + h
}

data class BuiltinWidgetDefinition(
    val widgetId: String,
    val defaultW: Int,
    val defaultH: Int,
    val minW: Int,
    val minH: Int,
    val maxW: Int? = null,
    val maxH: Int? = null,
    val collapsedW: Int? = null,
    val collapsedH: Int? = null,
    val expandedW: Int? = null,
    val expandedH: Int? = null,
    val defaultVisible: Boolean = true,
    val canHide: Boolean = true,
    val canResize: Boolean = true
)
