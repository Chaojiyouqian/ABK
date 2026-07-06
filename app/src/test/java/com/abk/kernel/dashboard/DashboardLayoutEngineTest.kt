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

    @Test
    fun maximizingWidgetCanUseFullAvailableWidth() {
        val layout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.COMPACT)

        val maximized = DashboardLayoutEngine.setItemSpanMode(
            layout = layout,
            widgetId = StatusDashboardWidgets.DEVICE_REPOSITORY,
            spanMode = DashboardItemSpanMode.MAXIMUM,
            definitions = StatusDashboardWidgets.definitions
        )

        val widget = maximized.items.first { it.widgetId == StatusDashboardWidgets.DEVICE_REPOSITORY }
        assertEquals(DashboardDensityPreset.COMPACT.columns, widget.w)
        assertEquals(DashboardItemSpanMode.MAXIMUM, widget.spanMode)
    }

    @Test
    fun resizingMarksWidgetAsCustom() {
        val layout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD)
        val moved = DashboardLayoutEngine.moveItemExact(
            layout = layout,
            widgetId = StatusDashboardWidgets.RECENT_RUNS,
            targetX = 0,
            targetY = 28,
            definitions = StatusDashboardWidgets.definitions
        )

        val resized = DashboardLayoutEngine.resizeItemExact(
            layout = moved,
            widgetId = StatusDashboardWidgets.RECENT_RUNS,
            targetW = 10,
            targetH = 7,
            definitions = StatusDashboardWidgets.definitions
        )

        assertEquals(
            DashboardItemSpanMode.CUSTOM,
            resized.items.first { it.widgetId == StatusDashboardWidgets.RECENT_RUNS }.spanMode
        )
    }

    @Test
    fun minimizingBuildActivityUsesFourRows() {
        val layout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD)

        val minimized = DashboardLayoutEngine.setItemSpanMode(
            layout = layout,
            widgetId = StatusDashboardWidgets.BUILD_ACTIVITY,
            spanMode = DashboardItemSpanMode.MINIMUM,
            definitions = StatusDashboardWidgets.definitions
        )

        val buildActivity = minimized.items.first { it.widgetId == StatusDashboardWidgets.BUILD_ACTIVITY }
        assertEquals(4, buildActivity.h)
        assertEquals(DashboardItemSpanMode.MINIMUM, buildActivity.spanMode)
    }

    @Test
    fun sanitizePreservesTightStackingUsingSpatialOrderInsteadOfOriginalListOrder() {
        val layout = DashboardLayout(
            densityPreset = DashboardDensityPreset.STANDARD,
            items = listOf(
                DashboardLayoutItem(
                    widgetId = StatusDashboardWidgets.RECENT_RUNS,
                    x = 0,
                    y = 10,
                    w = 16,
                    h = 8,
                    visible = true
                ),
                DashboardLayoutItem(
                    widgetId = StatusDashboardWidgets.BUILD_ACTIVITY,
                    x = 0,
                    y = 0,
                    w = 16,
                    h = 10,
                    visible = true
                )
            )
        )

        val sanitized = DashboardLayoutEngine.sanitize(
            layout = layout,
            definitions = StatusDashboardWidgets.definitions,
            defaultLayout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD),
            appendMissingDefinitions = false
        )

        val buildActivity = sanitized.items.first { it.widgetId == StatusDashboardWidgets.BUILD_ACTIVITY }
        val recentRuns = sanitized.items.first { it.widgetId == StatusDashboardWidgets.RECENT_RUNS }
        assertEquals(10, recentRuns.y)
        assertEquals(buildActivity.bottom, recentRuns.y)
        assertTrue(DashboardLayoutEngine.isLayoutLegal(sanitized, StatusDashboardWidgets.definitions))
    }

    @Test
    fun changeModeConvertsGridLayoutToFreeform() {
        val gridLayout = StatusDashboardWidgets.defaultLayout(DashboardDensityPreset.STANDARD)

        val freeformLayout = DashboardLayoutEngine.changeMode(
            layout = gridLayout,
            targetMode = DashboardLayoutMode.FREEFORM,
            definitions = StatusDashboardWidgets.definitions,
            defaultLayout = StatusDashboardWidgets.defaultFreeformLayout(DashboardDensityPreset.STANDARD)
        )

        assertEquals(DashboardLayoutMode.FREEFORM, freeformLayout.layoutMode)
        assertTrue(freeformLayout.items.all { it.w > 0 && it.h > 0 })
    }
}
