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
            defaultH = 3,
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
            defaultH = 16,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 24,
            maxH = 32
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
            DashboardLayoutItem(SEARCH, 0, 0, densityPreset.columns, 3),
            DashboardLayoutItem(LIST, 0, 3, densityPreset.columns, 16)
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
            defaultH = 16,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 24,
            maxH = 32
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
            DashboardLayoutItem(CONTENT, 0, 0, densityPreset.columns, 16)
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
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 6,
            maxH = 8
        ),
        BuiltinWidgetDefinition(
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 14,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 22,
            maxH = 30
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
            DashboardLayoutItem(CONTROLS, 0, 0, densityPreset.columns, 5),
            DashboardLayoutItem(LIST, 0, 5, densityPreset.columns, 14)
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
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 6,
            maxH = 8
        ),
        BuiltinWidgetDefinition(
            widgetId = LIST,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 14,
            minW = 8,
            minH = 10,
            collapsedW = 8,
            collapsedH = 10,
            expandedH = 22,
            maxH = 30
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
            DashboardLayoutItem(CONTROLS, 0, 0, densityPreset.columns, 5),
            DashboardLayoutItem(LIST, 0, 5, densityPreset.columns, 14)
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
    const val ACCOUNT = "settings.account"
    const val BUILD = "settings.build"
    const val APP_UPDATE = "settings.app_update"
    const val MANAGER = "settings.manager"
    const val NOTIFICATION = "settings.notification"
    const val NAVIGATION = "settings.navigation"
    const val LANGUAGE = "settings.language"
    const val THEME = "settings.theme"
    const val EXTENSIONS = "settings.extensions"
    const val ABOUT = "settings.about"

    val definitions: List<BuiltinWidgetDefinition> = listOf(
        BuiltinWidgetDefinition(
            widgetId = ACCOUNT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 8,
            minW = 8,
            minH = 6,
            collapsedW = 8,
            collapsedH = 6,
            expandedH = 10,
            maxH = 12
        ),
        BuiltinWidgetDefinition(
            widgetId = BUILD,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 24,
            minW = 8,
            minH = 12,
            collapsedW = 8,
            collapsedH = 12,
            expandedH = 30,
            maxH = 38
        ),
        BuiltinWidgetDefinition(
            widgetId = APP_UPDATE,
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
            widgetId = MANAGER,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 12,
            minW = 8,
            minH = 8,
            collapsedW = 8,
            collapsedH = 8,
            expandedH = 18,
            maxH = 24
        ),
        BuiltinWidgetDefinition(
            widgetId = NOTIFICATION,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 6,
            maxH = 8
        ),
        BuiltinWidgetDefinition(
            widgetId = NAVIGATION,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 6,
            maxH = 8
        ),
        BuiltinWidgetDefinition(
            widgetId = LANGUAGE,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 6,
            maxH = 8
        ),
        BuiltinWidgetDefinition(
            widgetId = THEME,
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
            widgetId = EXTENSIONS,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 5,
            minW = 8,
            minH = 4,
            collapsedW = 8,
            collapsedH = 4,
            expandedH = 6,
            maxH = 8
        ),
        BuiltinWidgetDefinition(
            widgetId = ABOUT,
            defaultW = DashboardDensityPreset.STANDARD.columns,
            defaultH = 12,
            minW = 8,
            minH = 8,
            collapsedW = 8,
            collapsedH = 8,
            expandedH = 16,
            maxH = 20
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
            DashboardLayoutItem(ACCOUNT, 0, 0, densityPreset.columns, 8),
            DashboardLayoutItem(BUILD, 0, 8, densityPreset.columns, 24),
            DashboardLayoutItem(APP_UPDATE, 0, 32, densityPreset.columns, 12),
            DashboardLayoutItem(MANAGER, 0, 44, densityPreset.columns, 12),
            DashboardLayoutItem(NOTIFICATION, 0, 56, densityPreset.columns, 5),
            DashboardLayoutItem(NAVIGATION, 0, 61, densityPreset.columns, 5),
            DashboardLayoutItem(LANGUAGE, 0, 66, densityPreset.columns, 5),
            DashboardLayoutItem(THEME, 0, 71, densityPreset.columns, 6),
            DashboardLayoutItem(EXTENSIONS, 0, 77, densityPreset.columns, 5),
            DashboardLayoutItem(ABOUT, 0, 82, densityPreset.columns, 12)
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
