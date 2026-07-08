package com.abk.kernel.dashboard

object BuildDashboardWidgets {
    const val OVERVIEW = "build.overview"
    const val TOOLS = "build.tools"
    const val KERNEL_VERSION = "build.kernel_version"
    const val KERNEL_SU = "build.kernel_su"
    const val FEATURES = "build.features"
    const val CUSTOM_MODULES = "build.custom_modules"
    const val OPTIONAL_CONFIG = "build.optional_config"
    const val SUBMIT = "build.submit"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = OVERVIEW,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 12,
            minW = 8,
            minH = 8,
            collapsedW = 8,
            collapsedH = 8,
            expandedH = 16,
            maxH = 20
        ),
        BuiltinWidgetDefinition(
            widgetId = TOOLS,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 8,
            minW = 8,
            minH = 6,
            collapsedW = 8,
            collapsedH = 6,
            expandedH = 12,
            maxH = 14
        ),
        BuiltinWidgetDefinition(
            widgetId = KERNEL_VERSION,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 12,
            minW = 8,
            minH = 8,
            collapsedW = 8,
            collapsedH = 8,
            expandedH = 16,
            maxH = 20
        ),
        BuiltinWidgetDefinition(
            widgetId = KERNEL_SU,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 8,
            minW = 8,
            minH = 6,
            collapsedW = 8,
            collapsedH = 6,
            expandedH = 12,
            maxH = 16
        ),
        BuiltinWidgetDefinition(
            widgetId = FEATURES,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 18,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 28,
            maxH = 36
        ),
        BuiltinWidgetDefinition(
            widgetId = CUSTOM_MODULES,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 18,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 30,
            maxH = 40
        ),
        BuiltinWidgetDefinition(
            widgetId = OPTIONAL_CONFIG,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 16,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 24,
            maxH = 32
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
            seed(OVERVIEW, 0, 0, columns, 12),
            seed(TOOLS, 0, 12, columns, 8),
            seed(KERNEL_VERSION, 0, 20, columns, 12),
            seed(KERNEL_SU, 0, 32, columns, 8),
            seed(FEATURES, 0, 40, columns, 18),
            seed(CUSTOM_MODULES, 0, 58, columns, 18),
            seed(OPTIONAL_CONFIG, 0, 76, columns, 16),
            seed(SUBMIT, 0, 92, columns, 4)
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
