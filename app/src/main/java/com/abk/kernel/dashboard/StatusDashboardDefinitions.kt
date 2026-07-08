package com.abk.kernel.dashboard

object StatusDashboardWidgets {
    const val HERO = "status.hero"
    const val METRICS = "status.metrics"
    const val BUILD_ACTIVITY = "status.build_activity"
    const val DEVICE_REPOSITORY = "status.device_repository"
    const val RECENT_RUNS = "status.recent_runs"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = HERO,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 4,
            minW = 8,
            minH = 3,
            collapsedW = 8,
            collapsedH = 3,
            expandedH = 5,
            maxH = 5,
            canHide = true
        ),
        BuiltinWidgetDefinition(
            widgetId = METRICS,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 4,
            minW = 8,
            minH = 3,
            collapsedW = 8,
            collapsedH = 3,
            expandedH = 5,
            maxH = 5
        ),
        BuiltinWidgetDefinition(
            widgetId = BUILD_ACTIVITY,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 6,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 8,
            maxH = 10
        ),
        BuiltinWidgetDefinition(
            widgetId = DEVICE_REPOSITORY,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 6,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 8,
            maxH = 10
        ),
        BuiltinWidgetDefinition(
            widgetId = RECENT_RUNS,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 7,
            minW = 6,
            minH = 5,
            collapsedW = 6,
            collapsedH = 5,
            expandedH = 10,
            maxH = 12
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout {
        val columns = densityPreset.columns
        val items = when (densityPreset) {
            DashboardDensityPreset.COMPACT -> listOf(
                seed(HERO, 0, 0, columns, 4),
                seed(METRICS, 0, 4, columns, 4),
                seed(BUILD_ACTIVITY, 0, 8, columns, 6),
                seed(DEVICE_REPOSITORY, 0, 14, columns, 6),
                seed(RECENT_RUNS, 0, 20, columns, 7),
            )
            DashboardDensityPreset.STANDARD -> listOf(
                seed(HERO, 0, 0, columns, 4),
                seed(METRICS, 0, 4, columns, 4),
                seed(BUILD_ACTIVITY, 0, 8, columns, 6),
                seed(DEVICE_REPOSITORY, 0, 14, columns, 6),
                seed(RECENT_RUNS, 0, 20, columns, 7),
            )
            DashboardDensityPreset.RELAXED -> listOf(
                seed(HERO, 0, 0, columns, 4),
                seed(METRICS, 0, 4, columns, 4),
                seed(BUILD_ACTIVITY, 0, 8, columns, 6),
                seed(DEVICE_REPOSITORY, 0, 14, columns, 6),
                seed(RECENT_RUNS, 0, 20, columns, 7),
            )
        }
        return DashboardLayout(
            version = DASHBOARD_LAYOUT_VERSION,
            pageId = DashboardPageId.STATUS,
            layoutMode = DashboardLayoutMode.GRID,
            densityPreset = densityPreset,
            items = items.map { seed ->
                val definition = requireNotNull(definitionMap[seed.widgetId]) {
                    "Missing widget definition for ${seed.widgetId}"
                }
                DashboardLayoutItem(
                    widgetId = seed.widgetId,
                    x = seed.x,
                    y = seed.y,
                    w = seed.w.coerceAtMost(densityPreset.columns),
                    h = seed.h,
                    visible = definition.defaultVisible
                )
            }
        )
    }

    fun defaultFreeformLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayoutEngine.changeMode(
        layout = defaultLayout(densityPreset),
        targetMode = DashboardLayoutMode.FREEFORM,
        definitions = definitions,
        defaultLayout = defaultLayout(densityPreset)
    )

    private fun seed(widgetId: String, x: Int, y: Int, w: Int, h: Int): DefaultSeed =
        DefaultSeed(widgetId = widgetId, x = x, y = y, w = w, h = h)

    private data class DefaultSeed(
        val widgetId: String,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int
    )
}
