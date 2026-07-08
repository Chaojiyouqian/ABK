package com.abk.kernel.dashboard

object ModuleRepositoryDashboardWidgets {
    const val SUMMARY = "modules.summary"
    const val SEARCH = "modules.search"
    const val LIST = "modules.list"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = SUMMARY,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 7,
            maxH = 9,
            defaultVisible = false
        ),
        BuiltinWidgetDefinition(
            widgetId = SEARCH,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 4,
            minW = 8,
            minH = 3,
            collapsedW = 8,
            collapsedH = 3,
            expandedH = 4,
            maxH = 5
        ),
        BuiltinWidgetDefinition(
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 18,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 30,
            maxH = 40
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayout(
        version = DASHBOARD_LAYOUT_VERSION,
        pageId = DashboardPageId.MODULES,
        layoutMode = DashboardLayoutMode.GRID,
        densityPreset = densityPreset,
        items = listOf(
            DashboardLayoutItem(SUMMARY, 0, 0, densityPreset.columns, 5, visible = false),
            DashboardLayoutItem(SEARCH, 0, 0, densityPreset.columns, 4),
            DashboardLayoutItem(LIST, 0, 4, densityPreset.columns, 18)
        )
    )

    fun defaultFreeformLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayoutEngine.changeMode(
        layout = defaultLayout(densityPreset),
        targetMode = DashboardLayoutMode.FREEFORM,
        definitions = definitions,
        defaultLayout = defaultLayout(densityPreset)
    )
}

object FlashDashboardWidgets {
    const val SUMMARY = "flash.summary"
    const val CONTENT = "flash.content"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = SUMMARY,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 8,
            minW = 8,
            minH = 6,
            collapsedW = 8,
            collapsedH = 6,
            expandedH = 10,
            maxH = 12,
            defaultVisible = false
        ),
        BuiltinWidgetDefinition(
            widgetId = CONTENT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 20,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 30,
            maxH = 40
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayout(
        version = DASHBOARD_LAYOUT_VERSION,
        pageId = DashboardPageId.FLASH,
        layoutMode = DashboardLayoutMode.GRID,
        densityPreset = densityPreset,
        items = listOf(
            DashboardLayoutItem(SUMMARY, 0, 0, densityPreset.columns, 8, visible = false),
            DashboardLayoutItem(CONTENT, 0, 0, densityPreset.columns, 20)
        )
    )

    fun defaultFreeformLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayoutEngine.changeMode(
        layout = defaultLayout(densityPreset),
        targetMode = DashboardLayoutMode.FREEFORM,
        definitions = definitions,
        defaultLayout = defaultLayout(densityPreset)
    )
}

object InstalledModulesDashboardWidgets {
    const val CONTROLS = "installed.controls"
    const val LIST = "installed.list"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = CONTROLS,
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
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 18,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 30,
            maxH = 40
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayout(
        version = DASHBOARD_LAYOUT_VERSION,
        pageId = DashboardPageId.INSTALLED_MODULES,
        layoutMode = DashboardLayoutMode.GRID,
        densityPreset = densityPreset,
        items = listOf(
            DashboardLayoutItem(CONTROLS, 0, 0, densityPreset.columns, 6),
            DashboardLayoutItem(LIST, 0, 6, densityPreset.columns, 18)
        )
    )

    fun defaultFreeformLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayoutEngine.changeMode(
        layout = defaultLayout(densityPreset),
        targetMode = DashboardLayoutMode.FREEFORM,
        definitions = definitions,
        defaultLayout = defaultLayout(densityPreset)
    )
}

object RootAuthDashboardWidgets {
    const val CONTROLS = "rootauth.controls"
    const val LIST = "rootauth.list"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = CONTROLS,
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
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 18,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 30,
            maxH = 40
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayout(
        version = DASHBOARD_LAYOUT_VERSION,
        pageId = DashboardPageId.ROOT_AUTH,
        layoutMode = DashboardLayoutMode.GRID,
        densityPreset = densityPreset,
        items = listOf(
            DashboardLayoutItem(CONTROLS, 0, 0, densityPreset.columns, 6),
            DashboardLayoutItem(LIST, 0, 6, densityPreset.columns, 18)
        )
    )

    fun defaultFreeformLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayoutEngine.changeMode(
        layout = defaultLayout(densityPreset),
        targetMode = DashboardLayoutMode.FREEFORM,
        definitions = definitions,
        defaultLayout = defaultLayout(densityPreset)
    )
}

object SettingsDashboardWidgets {
    const val CONTENT = "settings.content"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = CONTENT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 20,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 30,
            maxH = 40,
            canHide = false
        )
    )

    val definitionMap: Map<String, BuiltinWidgetDefinition> = definitions.associateBy { it.widgetId }

    fun defaultLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayout(
        version = DASHBOARD_LAYOUT_VERSION,
        pageId = DashboardPageId.SETTINGS,
        layoutMode = DashboardLayoutMode.GRID,
        densityPreset = densityPreset,
        items = listOf(
            DashboardLayoutItem(CONTENT, 0, 0, densityPreset.columns, 20)
        )
    )

    fun defaultFreeformLayout(
        densityPreset: DashboardDensityPreset = DashboardDensityPreset.STANDARD
    ): DashboardLayout = DashboardLayoutEngine.changeMode(
        layout = defaultLayout(densityPreset),
        targetMode = DashboardLayoutMode.FREEFORM,
        definitions = definitions,
        defaultLayout = defaultLayout(densityPreset)
    )
}
