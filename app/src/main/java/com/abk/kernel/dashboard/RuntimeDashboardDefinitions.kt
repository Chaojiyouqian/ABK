package com.abk.kernel.dashboard

object RuntimeDashboardWidgets {
    const val STATUS_HEADER = "runtime.status_header"
    const val MANAGER = "runtime.manager"
    const val BUILD_PARAMETERS = "runtime.build_parameters"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = STATUS_HEADER,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 4,
            minW = 8,
            minH = 3,
            collapsedW = 8,
            collapsedH = 3,
            expandedH = 5,
            maxH = 5,
            canHide = false
        ),
        BuiltinWidgetDefinition(
            widgetId = MANAGER,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 7,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 10,
            maxH = 10
        ),
        BuiltinWidgetDefinition(
            widgetId = BUILD_PARAMETERS,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 8,
            minW = 8,
            minH = 5,
            collapsedW = 8,
            collapsedH = 5,
            expandedH = 12,
            maxH = 12
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout {
        val columns = densityPreset.columns
        val items = listOf(
            DashboardLayoutItem(
                widgetId = STATUS_HEADER,
                x = 0,
                y = 0,
                w = columns,
                h = 4
            ),
            DashboardLayoutItem(
                widgetId = MANAGER,
                x = 0,
                y = 4,
                w = columns,
                h = 7
            ),
            DashboardLayoutItem(
                widgetId = BUILD_PARAMETERS,
                x = 0,
                y = 11,
                w = columns,
                h = 8
            )
        )
        return DashboardLayout(
            version = DASHBOARD_LAYOUT_VERSION,
            pageId = DashboardPageId.RUNTIME_HOME,
            layoutMode = DashboardLayoutMode.GRID,
            densityPreset = densityPreset,
            items = items
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
}
