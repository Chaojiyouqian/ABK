package com.abk.kernel.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbkAgentRoutesTest {
    @Test
    fun parsesRuntimeModuleActionRoute() {
        val route = AbkAgentRoutes.parse("/api/v1/runtime/modules/meta-abk-mount/action")
        assertTrue(route is AbkAgentRoute.RuntimeModuleAction)
        assertEquals("meta-abk-mount", (route as AbkAgentRoute.RuntimeModuleAction).moduleId)
    }

    @Test
    fun parsesTaskDownloadRoute() {
        val route = AbkAgentRoutes.parse("/api/v1/tasks/abc-123/download")
        assertTrue(route is AbkAgentRoute.TaskDownload)
        assertEquals("abc-123", (route as AbkAgentRoute.TaskDownload).taskId)
    }
}
