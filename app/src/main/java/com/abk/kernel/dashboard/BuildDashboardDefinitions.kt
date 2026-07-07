package com.abk.kernel.dashboard

object BuildDashboardWidgets {
    const val OVERVIEW = "build.overview"
    const val TOOLS = "build.tools"
    const val CONFIG = "build.config"
    const val SUBMIT = "build.submit"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = OVERVIEW,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 15,
            minW = 8,
            minH = 8,
            collapsedW = 8,
            collapsedH = 8,
            expandedH = 20,
            maxH = 24
        ),
        BuiltinWidgetDefinition(
            widgetId = TOOLS,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 10,
            minW = 8,
            minH = 6,
            collapsedW = 8,
            collapsedH = 6,
            expandedH = 14,
            maxH = 16
        ),
        BuiltinWidgetDefinition(
            widgetId = CONFIG,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 86,
            minW = 8,
            minH = 24,
            collapsedW = 8,
            collapsedH = 24,
            expandedH = 110,
            maxH = 128
        ),
        BuiltinWidgetDefinition(
            widgetId = SUBMIT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 4,
            minW = 8,
            minH = 3,
            collapsedW = 8,
            collapsedH = 3,
            expandedH = 5,
            maxH = 6,
            canHide = false
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout {
        val columns = densityPreset.columns
        val items = listOf(
            seed(OVERVIEW, 0, 0, columns, 15),
            seed(TOOLS, 0, 15, columns, 10),
            seed(CONFIG, 0, 25, columns, 86),
            seed(SUBMIT, 0, 111, columns, 4)
        )
        return DashboardLayout(
            version = DASHBOARD_LAYOUT_VERSION,
            pageId = DashboardPageId.BUILD,
            layoutMode = DashboardLayoutMode.GRID,
            densityPreset = densityPreset,
            items = items.map { seed ->
                val definition = requireNotNull(definitionMap[seed.widgetId])
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
