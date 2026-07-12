package com.abk.kernel.agent

internal sealed interface AbkAgentRoute {
    data object Health : AbkAgentRoute
    data object Session : AbkAgentRoute
    data object Runtime : AbkAgentRoute
    data object RootGrants : AbkAgentRoute
    data class RootGrantAllow(val packageName: String) : AbkAgentRoute
    data object Susfs : AbkAgentRoute
    data object ApplySusfs : AbkAgentRoute
    data class RuntimeModuleEnable(val moduleId: String) : AbkAgentRoute
    data class RuntimeModulePendingUninstall(val moduleId: String) : AbkAgentRoute
    data class RuntimeModuleAction(val moduleId: String) : AbkAgentRoute
    data object InstallModule : AbkAgentRoute
    data object InstallApk : AbkAgentRoute
    data object FlashImage : AbkAgentRoute
    data object ExportDiagnostics : AbkAgentRoute
    data class Task(val taskId: String) : AbkAgentRoute
    data class TaskDownload(val taskId: String) : AbkAgentRoute
}

internal object AbkAgentRoutes {
    private val runtimeEnable = Regex("""^/api/v1/runtime/modules/([^/]+)/enable$""")
    private val runtimePendingUninstall = Regex("""^/api/v1/runtime/modules/([^/]+)/pending-uninstall$""")
    private val runtimeAction = Regex("""^/api/v1/runtime/modules/([^/]+)/action$""")
    private val rootGrantAllow = Regex("""^/api/v1/root-grants/([^/]+)/allow$""")
    private val task = Regex("""^/api/v1/tasks/([^/]+)$""")
    private val taskDownload = Regex("""^/api/v1/tasks/([^/]+)/download$""")

    fun parse(path: String?): AbkAgentRoute? {
        val clean = path.orEmpty()
            .substringBefore('?')
            .trim()
            .trimEnd('/')
            .ifBlank { "/" }
        return when (clean) {
            "/api/v1/health" -> AbkAgentRoute.Health
            "/api/v1/session" -> AbkAgentRoute.Session
            "/api/v1/runtime" -> AbkAgentRoute.Runtime
            "/api/v1/root-grants" -> AbkAgentRoute.RootGrants
            "/api/v1/susfs" -> AbkAgentRoute.Susfs
            "/api/v1/susfs/apply" -> AbkAgentRoute.ApplySusfs
            "/api/v1/install/module" -> AbkAgentRoute.InstallModule
            "/api/v1/install/apk" -> AbkAgentRoute.InstallApk
            "/api/v1/flash/image" -> AbkAgentRoute.FlashImage
            "/api/v1/diagnostics/export" -> AbkAgentRoute.ExportDiagnostics
            else -> {
                runtimeEnable.matchEntire(clean)?.groupValues?.getOrNull(1)?.let {
                    return AbkAgentRoute.RuntimeModuleEnable(it)
                }
                runtimePendingUninstall.matchEntire(clean)?.groupValues?.getOrNull(1)?.let {
                    return AbkAgentRoute.RuntimeModulePendingUninstall(it)
                }
                runtimeAction.matchEntire(clean)?.groupValues?.getOrNull(1)?.let {
                    return AbkAgentRoute.RuntimeModuleAction(it)
                }
                rootGrantAllow.matchEntire(clean)?.groupValues?.getOrNull(1)?.let {
                    return AbkAgentRoute.RootGrantAllow(it)
                }
                taskDownload.matchEntire(clean)?.groupValues?.getOrNull(1)?.let {
                    return AbkAgentRoute.TaskDownload(it)
                }
                task.matchEntire(clean)?.groupValues?.getOrNull(1)?.let {
                    return AbkAgentRoute.Task(it)
                }
                null
            }
        }
    }
}
