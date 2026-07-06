package com.abk.kernel.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutCodecTest {

    @Test
    fun exportThenImportPreservesLayout() {
        val layout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD)
        val exported = DashboardLayoutCodec.export(layout)

        val imported = DashboardLayoutCodec.importStatusLayout(
            json = exported,
            definitions = StatusDashboardWidgets.definitions,
            defaultLayoutForDensity = StatusDashboardWidgets::defaultLayout
        )

        assertEquals(null, imported.error)
        assertFalse(imported.appliedDefaultFallback)
        assertEquals(layout, imported.layout)
    }

    @Test
    fun importRejectsUnsupportedPageAndFallsBackToDefault() {
        val result = DashboardLayoutCodec.importStatusLayout(
            json = """
                {
                  "version": 1,
                  "pageId": "build",
                  "layoutMode": "grid",
                  "densityPreset": "compact",
                  "items": []
                }
            """.trimIndent(),
            definitions = StatusDashboardWidgets.definitions,
            defaultLayoutForDensity = StatusDashboardWidgets::defaultLayout
        )

        assertEquals(DashboardLayoutImportError.UNSUPPORTED_PAGE, result.error)
        assertTrue(result.appliedDefaultFallback)
        assertEquals(
            StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.COMPACT),
            result.layout
        )
    }

    @Test
    fun importIgnoresUnknownWidgetsAndKeepsMissingWidgetsHidden() {
        val result = DashboardLayoutCodec.importStatusLayout(
            json = """
                {
                  "version": 1,
                  "pageId": "status",
                  "layoutMode": "grid",
                  "densityPreset": "standard",
                  "items": [
                    {
                      "widgetId": "${StatusDashboardWidgets.HERO}",
                      "x": 0,
                      "y": 0,
                      "w": 16,
                      "h": 4,
                      "visible": true
                    },
                    {
                      "widgetId": "status.unknown",
                      "x": 0,
                      "y": 4,
                      "w": 8,
                      "h": 4,
                      "visible": true
                    }
                  ]
                }
            """.trimIndent(),
            definitions = StatusDashboardWidgets.definitions,
            defaultLayoutForDensity = StatusDashboardWidgets::defaultLayout
        )

        assertEquals(null, result.error)
        assertEquals(1, result.importedItemCount)
        assertEquals(1, result.ignoredItemCount)
        assertEquals(StatusDashboardWidgets.definitions.size, result.layout.items.size)
        assertNotNull(result.layout.items.firstOrNull { it.widgetId == StatusDashboardWidgets.HERO && it.visible })
        assertTrue(
            result.layout.items
                .filter { it.widgetId != StatusDashboardWidgets.HERO }
                .all { !it.visible }
        )
    }

    @Test
    fun importAndExportPreserveSpanMode() {
        val layout = DashboardLayout(
            densityPreset = DashboardDensityPreset.STANDARD,
            items = listOf(
                DashboardLayoutItem(
                    widgetId = StatusDashboardWidgets.BUILD_ACTIVITY,
                    x = 0,
                    y = 0,
                    w = 16,
                    h = 12,
                    visible = true,
                    spanMode = DashboardItemSpanMode.MAXIMUM
                )
            )
        )

        val restored = DashboardLayoutCodec.importStatusLayout(
            json = DashboardLayoutCodec.export(layout),
            definitions = StatusDashboardWidgets.definitions,
            defaultLayoutForDensity = StatusDashboardWidgets::defaultLayout
        )

        assertEquals(
            DashboardItemSpanMode.MAXIMUM,
            restored.layout.items.first { it.widgetId == StatusDashboardWidgets.BUILD_ACTIVITY }.spanMode
        )
    }

    @Test
    fun importSupportsLegacyMinifiedPayloadWithoutPageId() {
        val result = DashboardLayoutCodec.importStatusLayout(
            json = """
                {
                  "a": 1,
                  "b": "grid",
                  "c": "standard",
                  "d": [
                    {
                      "a": "${StatusDashboardWidgets.HERO}",
                      "b": 0,
                      "c": 0,
                      "d": 16,
                      "e": 3,
                      "f": true,
                      "g": "custom"
                    }
                  ]
                }
            """.trimIndent(),
            definitions = StatusDashboardWidgets.definitions,
            defaultLayoutForDensity = StatusDashboardWidgets::defaultLayout,
            hideMissingWidgets = false
        )

        assertEquals(null, result.error)
        assertEquals(DashboardPageId.STATUS, result.layout.pageId)
        assertEquals(DashboardDensityPreset.STANDARD, result.layout.densityPreset)
        assertEquals(
            DashboardItemSpanMode.CUSTOM,
            result.layout.items.first { it.widgetId == StatusDashboardWidgets.HERO }.spanMode
        )
    }

    @Test
    fun exportThenImportPreservesFreeformMode() {
        val layout = StatusDashboardWidgets.defaultFreeformLayout(DashboardDensityPreset.STANDARD)

        val restored = DashboardLayoutCodec.importStatusLayout(
            json = DashboardLayoutCodec.export(layout),
            definitions = StatusDashboardWidgets.definitions,
            defaultLayoutForDensity = StatusDashboardWidgets::defaultLayout,
            hideMissingWidgets = false
        )

        assertEquals(DashboardLayoutMode.FREEFORM, restored.layout.layoutMode)
    }
}
