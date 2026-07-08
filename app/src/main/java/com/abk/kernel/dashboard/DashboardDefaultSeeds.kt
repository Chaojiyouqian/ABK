package com.abk.kernel.dashboard

internal fun dashboardFreeformWidth(preset: DashboardDensityPreset): Int {
    val cellWidth = when (preset) {
        DashboardDensityPreset.COMPACT -> 16
        DashboardDensityPreset.STANDARD -> 20
        DashboardDensityPreset.RELAXED -> 24
    }
    return preset.columns * cellWidth + (preset.columns - 1) * 4
}

internal fun dashboardFreeformItem(
    widgetId: String,
    preset: DashboardDensityPreset,
    y: Int,
    h: Int,
    visible: Boolean = true
): DashboardLayoutItem = DashboardLayoutItem(
    widgetId = widgetId,
    x = 0,
    y = y,
    w = dashboardFreeformWidth(preset),
    h = h,
    visible = visible,
    spanMode = DashboardItemSpanMode.CUSTOM
)
