package com.abk.kernel.dashboard

object ModuleRepositoryDashboardWidgets {
    const val SUMMARY = "modules.summary"
    const val CONTENT = "modules.content"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = SUMMARY,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 6,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 8,
            maxH = 10,
            defaultVisible = false
        ),
        BuiltinWidgetDefinition(
            widgetId = CONTENT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 28,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 40,
            maxH = 48
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
            DashboardLayoutItem(SUMMARY, 0, 0, densityPreset.columns, 6),
            DashboardLayoutItem(CONTENT, 0, 6, densityPreset.columns, 28)
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
            defaultH = 10,
            minW = 8,
            minH = 6,
            collapsedW = 8,
            collapsedH = 6,
            expandedH = 12,
            maxH = 14,
            defaultVisible = false
        ),
        BuiltinWidgetDefinition(
            widgetId = CONTENT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 32,
            minW = 8,
            minH = 14,
            collapsedW = 8,
            collapsedH = 14,
            expandedH = 44,
            maxH = 56
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
            DashboardLayoutItem(SUMMARY, 0, 0, densityPreset.columns, 10),
            DashboardLayoutItem(CONTENT, 0, 10, densityPreset.columns, 32)
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
            defaultH = 8,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 10,
            maxH = 12
        ),
        BuiltinWidgetDefinition(
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 26,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 38,
            maxH = 48
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
            DashboardLayoutItem(CONTROLS, 0, 0, densityPreset.columns, 8),
            DashboardLayoutItem(LIST, 0, 8, densityPreset.columns, 26)
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
            defaultH = 8,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 10,
            maxH = 12
        ),
        BuiltinWidgetDefinition(
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 26,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 38,
            maxH = 48
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
            DashboardLayoutItem(CONTROLS, 0, 0, densityPreset.columns, 8),
            DashboardLayoutItem(LIST, 0, 8, densityPreset.columns, 26)
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
            defaultH = 34,
            minW = 8,
            minH = 18,
            collapsedW = 8,
            collapsedH = 18,
            expandedH = 44,
            maxH = 56,
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
            DashboardLayoutItem(CONTENT, 0, 0, densityPreset.columns, 34)
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
