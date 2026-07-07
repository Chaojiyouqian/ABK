package com.abk.kernel.dashboard

object BuildDashboardWidgets {
    const val OVERVIEW = "build.overview"
    const val TOOLS = "build.tools"
    const val KERNEL = "build.kernel"
    const val KSU = "build.ksu"
    const val FEATURES = "build.features"
    const val CUSTOM_MODULES = "build.custom_modules"
    const val OPTIONAL = "build.optional"
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
            widgetId = KERNEL,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 18,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 24,
            maxH = 28
        ),
        BuiltinWidgetDefinition(
            widgetId = KSU,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 14,
            minW = 8,
            minH = 8,
            collapsedW = 8,
            collapsedH = 8,
            expandedH = 18,
            maxH = 22
        ),
        BuiltinWidgetDefinition(
            widgetId = FEATURES,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 24,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 30,
            maxH = 36
        ),
        BuiltinWidgetDefinition(
            widgetId = CUSTOM_MODULES,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 22,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 30,
            maxH = 34
        ),
        BuiltinWidgetDefinition(
            widgetId = OPTIONAL,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 20,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 26,
            maxH = 30
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
            seed(KERNEL, 0, 25, columns, 18),
            seed(KSU, 0, 43, columns, 14),
            seed(FEATURES, 0, 57, columns, 24),
            seed(CUSTOM_MODULES, 0, 81, columns, 22),
            seed(OPTIONAL, 0, 103, columns, 20),
            seed(SUBMIT, 0, 123, columns, 4)
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
