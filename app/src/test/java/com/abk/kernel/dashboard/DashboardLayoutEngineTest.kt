package com.abk.kernel.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLayoutEngineTest {

    @Test
    fun defaultLayoutsAreLegalForAllDensityPresets() {
        DashboardDensityPreset.entries.forEach { densityPreset ->
            val layout = StatusDashboardWidgets.defaultLayout(densityPreset)
            assertTrue(
                "Expected ${densityPreset.rawValue} layout to be legal",
                DashboardLayoutEngine.isLayoutLegal(layout, StatusDashboardWidgets.definitions)
            )
        }
    }

    @Test
    fun sanitizeCorrectsBoundsAndConflicts() {
        val dirtyLayout = DashboardLayout(
            densityPreset = DashboardDensityPreset.STANDARD,
            items = listOf(
                DashboardLayoutItem(StatusDashboardWidgets.HERO, x = 12, y = -2, w = 30, h = 1),
                DashboardLayoutItem(StatusDashboardWidgets.METRICS, x = 0, y = 0, w = 20, h = 1),
                DashboardLayoutItem(StatusDashboardWidgets.DEVICE_REPOSITORY, x = 0, y = 0, w = 30, h = 2),
            )
        )

        val sanitized = DashboardLayoutEngine.sanitize(
            layout = dirtyLayout,
            definitions = StatusDashboardWidgets.definitions,
            defaultLayout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD),
            appendMissingDefinitions = false
        )

        assertTrue(DashboardLayoutEngine.isLayoutLegal(sanitized, StatusDashboardWidgets.definitions))
        assertEquals(3, sanitized.items.size)
        assertTrue(sanitized.items.any { it.widgetId == StatusDashboardWidgets.DEVICE_REPOSITORY && it.y > 0 })
    }

    @Test
    fun contentRowCountIgnoresHiddenItemsBelowVisibleArea() {
        val layout = DashboardLayout(
            densityPreset = DashboardDensityPreset.STANDARD,
            items = listOf(
                DashboardLayoutItem(StatusDashboardWidgets.HERO, x = 0, y = 0, w = 16, h = 4, visible = true),
                DashboardLayoutItem(StatusDashboardWidgets.RECENT_RUNS, x = 0, y = 24, w = 16, h = 8, visible = false)
            )
        )

        assertEquals(4, DashboardLayoutEngine.contentRowCount(layout))
    }

    @Test
    fun moveAndResizeRejectConflicts() {
        val layout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD)

        assertFalse(
            DashboardLayoutEngine.canMoveItem(
                layout = layout,
                widgetId = StatusDashboardWidgets.METRICS,
                targetX = 0,
                targetY = 0,
                definitions = StatusDashboardWidgets.definitions
            )
        )
        assertEquals(
            layout,
            DashboardLayoutEngine.moveItemExact(
                layout = layout,
                widgetId = StatusDashboardWidgets.METRICS,
                targetX = 0,
                targetY = 0,
                definitions = StatusDashboardWidgets.definitions
            )
        )

        assertFalse(
            DashboardLayoutEngine.canResizeItem(
                layout = layout,
                widgetId = StatusDashboardWidgets.DEVICE_REPOSITORY,
                targetW = 16,
                targetH = 10,
                definitions = StatusDashboardWidgets.definitions
            )
        )
    }

    @Test
    fun densityRemapKeepsLayoutLegal() {
        val source = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.COMPACT)

        val remapped = DashboardLayoutEngine.remapDensity(
            layout = source,
            targetDensityPreset = DashboardDensityPreset.RELAXED,
            definitions = StatusDashboardWidgets.definitions,
            defaultLayout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.RELAXED)
        )

        assertEquals(DashboardDensityPreset.RELAXED, remapped.densityPreset)
        assertTrue(DashboardLayoutEngine.isLayoutLegal(remapped, StatusDashboardWidgets.definitions))
    }
}
