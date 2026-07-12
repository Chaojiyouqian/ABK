package com.abk.kernel.agent

import android.content.Context
import com.abk.kernel.BuildConfig
import com.abk.kernel.R
import com.abk.kernel.data.model.AbkRuntimeModule
import com.abk.kernel.data.model.AbkRuntimeStatus
import com.abk.kernel.data.model.RootGrantApp
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsRuntimeStatus
import com.abk.kernel.utils.RootUtils
import com.abk.kernel.utils.defaultSusfsConfig
import com.abk.kernel.utils.normalizeSusfsConfig
import com.abk.kernel.viewmodel.MainUiState
import com.abk.kernel.viewmodel.RuntimeModuleActionBackend
import com.abk.kernel.viewmodel.RuntimeModuleControlBackend
import com.abk.kernel.viewmodel.exportDiagnosticBundle
import com.abk.kernel.viewmodel.isAbkMetaMount
import com.abk.kernel.viewmodel.isKsuBacked
import com.abk.kernel.viewmodel.mergeRuntimeStatus
import com.abk.kernel.viewmodel.normalizedType
import com.abk.kernel.viewmodel.preferredActionBackend
import com.abk.kernel.viewmodel.preferredControlBackend
import com.abk.kernel.viewmodel.sortRuntimeModulesForDisplay
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal data class AbkAgentSessionResponse(
    val protocolVersion: String,
    val appVersion: String,
    val appVersionCode: Long,
    val packageName: String,
    val serviceHost: String,
    val servicePort: Int,
    val rootGranted: Boolean,
    val managerAccessKind: String,
    val managerDiagnostic: String? = null,
    val capabilities: List<String> = emptyList(),
)

internal data class AbkAgentRuntimeResponse(
    val rootGranted: Boolean,
    val managerAccessKind: String,
    val managerDiagnostic: String? = null,
    val runtimeStatus: AbkRuntimeStatus? = null,
)

internal data class AbkAgentRootGrantResponse(
    val rootGranted: Boolean,
    val managerAccessKind: String,
    val managerDiagnostic: String? = null,
    val apps: List<RootGrantApp> = emptyList(),
)

internal data class AbkAgentSusfsResponse(
    val rootGranted: Boolean,
    val status: SusfsRuntimeStatus? = null,
    val config: SusfsConfig = defaultSusfsConfig(),
    val error: String? = null,
)

internal object AbkAgentFacade {
    private val gson = Gson()
    private val ksuModuleListType = object : TypeToken<List<Map<String, Any?>>>() {}.type

    fun health(port: Int): Map<String, Any> = mapOf(
        "status" to "ok",
        "protocolVersion" to "abk-agent-v1",
        "port" to port,
    )

    fun session(context: Context, host: String, port: Int): AbkAgentSessionResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val runtime = currentRuntimeSnapshot(rootGranted, access)
        val capabilities = mutableListOf(
            "session.read",
            "runtime.read",
            "root.refresh",
            "diagnostics.export",
        )
        if (rootGranted) {
            capabilities += listOf(
                "susfs.read",
                "susfs.write",
                "install.module",
                "install.apk",
                "flash.image",
            )
        }
        if (runtime?.modules?.isNotEmpty() == true) {
            capabilities += listOf(
                "runtime.module.enable",
                "runtime.module.uninstall",
                "runtime.module.action",
            )
        }
        if (access.hasNativeManagerPermission) {
            capabilities += listOf(
                "root_grants.read",
                "root_grants.write",
            )
        }
        return AbkAgentSessionResponse(
            protocolVersion = "abk-agent-v1",
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.APP_VERSION_CODE,
            packageName = context.packageName,
            serviceHost = host,
            servicePort = port,
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            capabilities = capabilities.distinct().sorted(),
        )
    }

    fun runtime(context: Context): AbkAgentRuntimeResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val runtimeStatus = currentRuntimeSnapshot(rootGranted, access)
        return AbkAgentRuntimeResponse(
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            runtimeStatus = runtimeStatus?.copy(
                modules = sortRuntimeModulesForDisplay(runtimeStatus.modules),
            ),
        )
    }

    fun rootGrants(context: Context): AbkAgentRootGrantResponse {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val apps = if (access.hasNativeManagerPermission) {
            RootUtils.listRootGrantApps(context)
        } else {
            emptyList()
        }
        return AbkAgentRootGrantResponse(
            rootGranted = rootGranted,
            managerAccessKind = access.kind.name.lowercase(),
            managerDiagnostic = managerAccessError(context, access, rootGranted),
            apps = apps,
        )
    }

    fun setRootGrantAllowed(
        context: Context,
        packageName: String,
        allowed: Boolean,
    ): RootUtils.ShellResult {
        val cleanPackage = packageName.trim()
        if (cleanPackage.isBlank()) {
            return RootUtils.ShellResult(false, listOf("package_name missing"))
        }
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        if (!access.hasNativeManagerPermission) {
            return RootUtils.ShellResult(false, listOf(managerAccessError(context, access, rootGranted)))
        }
        val app = RootUtils.listRootGrantApps(context).firstOrNull { it.packageName == cleanPackage }
            ?: return RootUtils.ShellResult(false, listOf("package not found: $cleanPackage"))
        val profile = app.profile.copy(
            name = cleanPackage,
            currentUid = app.uid,
            allowSu = allowed,
            rootUseDefault = true,
            nonRootUseDefault = true,
        )
        return if (RootUtils.setRootGrantProfile(profile)) {
            RootUtils.ShellResult(true, listOf("updated $cleanPackage"))
        } else {
            RootUtils.ShellResult(false, listOf("failed to update $cleanPackage"))
        }
    }

    fun susfs(context: Context): AbkAgentSusfsResponse {
        val rootGranted = RootUtils.isRootAvailable()
        if (!rootGranted) {
            return AbkAgentSusfsResponse(
                rootGranted = false,
                config = defaultSusfsConfig(),
            )
        }
        return runCatching {
            AbkAgentSusfsResponse(
                rootGranted = true,
                status = RootUtils.readSusfsRuntimeStatus(),
                config = normalizeSusfsConfig(RootUtils.readSusfsConfig()),
            )
        }.getOrElse { error ->
            AbkAgentSusfsResponse(
                rootGranted = true,
                config = defaultSusfsConfig(),
                error = error.message ?: "susfs load failed",
            )
        }
    }

    fun applySusfsConfig(
        config: SusfsConfig,
        onOutput: (String) -> Unit,
    ): RootUtils.ShellResult = RootUtils.applySusfsConfig(normalizeSusfsConfig(config), onOutput)

    fun setRuntimeModuleEnabled(moduleId: String, enabled: Boolean): RootUtils.ShellResult {
        val module = findRuntimeModule(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module not found: $moduleId"))
        return when {
            module.isAbkMetaMount() -> RootUtils.setAbkMetaMountEnabled(enabled)
            module.preferredControlBackend() == RuntimeModuleControlBackend.ABK_CONTROL -> {
                val command = if (enabled) "enable ${module.id}" else "disable ${module.id}"
                val controlResult = RootUtils.writeAbkControlCommand(command)
                if (controlResult.success) {
                    controlResult
                } else if (module.isKsuBacked()) {
                    RootUtils.setKsuModuleEnabled(module.id, enabled)
                } else {
                    controlResult
                }
            }
            module.preferredControlBackend() == RuntimeModuleControlBackend.KSU ->
                RootUtils.setKsuModuleEnabled(module.id, enabled)
            else ->
                RootUtils.writeAbkControlCommand(
                    if (enabled) "enable ${module.id}" else "disable ${module.id}",
                )
        }
    }

    fun setRuntimeModulePendingUninstall(moduleId: String, pending: Boolean): RootUtils.ShellResult {
        val module = findRuntimeModule(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module not found: $moduleId"))
        if (!module.isKsuBacked()) {
            return RootUtils.ShellResult(false, listOf("module uninstall unsupported"))
        }
        return RootUtils.setKsuModulePendingUninstall(module.id, pending)
    }

    fun runRuntimeModuleAction(
        moduleId: String,
        onOutput: (String) -> Unit,
    ): RootUtils.ShellResult {
        val module = findRuntimeModule(moduleId)
            ?: return RootUtils.ShellResult(false, listOf("module not found: $moduleId"))
        return when (module.preferredActionBackend()) {
            RuntimeModuleActionBackend.ABK_ACTION_SCRIPT ->
                RootUtils.runModuleActionScript(module.moduleDir.ifBlank { "/data/adb/modules/${module.id}" }, onOutput)
            RuntimeModuleActionBackend.KSU_ACTION ->
                RootUtils.runKsuModuleAction(module.id, onOutput)
            RuntimeModuleActionBackend.NONE ->
                RootUtils.ShellResult(false, listOf("module action unsupported"))
        }
    }

    fun installModule(zipPath: String, onOutput: (String) -> Unit): RootUtils.ShellResult =
        RootUtils.installModule(zipPath, onOutput)

    fun installApk(context: Context, apkPath: String, onOutput: (String) -> Unit): RootUtils.ShellResult =
        RootUtils.installApk(context, apkPath, onOutput)

    fun flashImage(imagePath: String, partition: String, onOutput: (String) -> Unit): RootUtils.ShellResult =
        RootUtils.flashImage(imagePath, partition, onOutput)

    suspend fun exportDiagnostics(context: Context): Pair<java.io.File, List<String>> {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        val state = MainUiState(
            rootGranted = rootGranted,
            abkRuntimeStatus = currentRuntimeSnapshot(rootGranted, access),
        )
        val result = exportDiagnosticBundle(context, state)
        return result.zipFile to result.warnings
    }

    private fun currentRuntimeSnapshot(
        rootGranted: Boolean,
        access: RootUtils.ManagerAccessInfo,
    ): AbkRuntimeStatus? {
        if (!access.hasNativeManagerPermission && !rootGranted) return null
        val snapshot = RootUtils.readManagerRuntimeSnapshot()
        if (!snapshot.manager.active) return null
        return mergeRuntimeStatus(
            gson = gson,
            ksuModuleListType = ksuModuleListType,
            manager = snapshot.manager,
            controlJson = snapshot.controlStatusJson,
            ksuModulesJson = snapshot.ksuModulesJson,
        )
    }

    private fun findRuntimeModule(moduleId: String): AbkRuntimeModule? {
        val rootGranted = RootUtils.isRootAvailable()
        val access = RootUtils.resolveManagerAccess(rootGranted)
        return currentRuntimeSnapshot(rootGranted, access)
            ?.modules
            ?.firstOrNull { it.id == moduleId.trim() }
    }

    private fun managerAccessError(
        context: Context,
        access: RootUtils.ManagerAccessInfo,
        rootGranted: Boolean,
    ): String? {
        access.diagnostic?.takeIf { it.isNotBlank() }?.let { return it }
        val message = when (access.kind) {
            RootUtils.ManagerAccessKind.NATIVE_MANAGER -> ""
            RootUtils.ManagerAccessKind.NO_ROOT -> context.getString(R.string.vm_external_manager_no_root)
            RootUtils.ManagerAccessKind.ROOT_ONLY -> context.getString(R.string.vm_external_root_no_native_permission)
            RootUtils.ManagerAccessKind.NATIVE_KERNEL_NO_MANAGER ->
                context.getString(R.string.vm_native_kernel_no_manager)
        }
        return message.ifBlank { if (rootGranted) null else null }
    }
}
