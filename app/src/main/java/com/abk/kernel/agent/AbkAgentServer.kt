package com.abk.kernel.agent

import android.content.Context
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.utils.RootUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal class AbkAgentServer(
    private val context: Context,
    private val host: String,
    port: Int,
) : NanoHTTPD(host, port) {
    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        return try {
            val route = AbkAgentRoutes.parse(session.uri)
                ?: return jsonResponse(
                    status = Response.Status.NOT_FOUND,
                    payload = mapOf("error" to "route not found", "path" to session.uri),
                )
            when (route) {
                AbkAgentRoute.Health -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.health(listeningPort))
                }
                AbkAgentRoute.Session -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.session(context, host, listeningPort))
                }
                AbkAgentRoute.Runtime -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.runtime(context))
                }
                AbkAgentRoute.RootGrants -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.rootGrants(context))
                }
                is AbkAgentRoute.RootGrantAllow -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val allowed = body?.get("allowed")?.asBoolean ?: false
                    jsonResponse(payload = shellResultPayload(AbkAgentFacade.setRootGrantAllowed(context, decode(route.packageName), allowed)))
                }
                AbkAgentRoute.Susfs -> requireMethod(session, Method.GET) {
                    jsonResponse(payload = AbkAgentFacade.susfs(context))
                }
                AbkAgentRoute.ApplySusfs -> requireMethod(session, Method.POST) {
                    val body = readBody(session)
                    if (body.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "request body missing"))
                    }
                    val config = runCatching {
                        gson.fromJson(body, SusfsConfig::class.java)
                    }.getOrNull() ?: return@requireMethod jsonResponse(
                        Response.Status.BAD_REQUEST,
                        mapOf("error" to "invalid susfs config json"),
                    )
                    acceptTask("susfs.apply") {
                        val result = AbkAgentFacade.applySusfsConfig(config) { line -> log(line) }
                        if (result.success) {
                            success(
                                message = "susfs applied",
                                result = mapOf(
                                    "shell" to shellResultPayload(result),
                                    "susfs" to AbkAgentFacade.susfs(context),
                                ),
                            )
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "susfs apply failed" }, appendMessage = false)
                        }
                    }
                }
                is AbkAgentRoute.RuntimeModuleEnable -> requireMethod(session, Method.POST) {
                    val enabled = readJsonBody(session)?.get("enabled")?.asBoolean ?: false
                    jsonResponse(payload = shellResultPayload(AbkAgentFacade.setRuntimeModuleEnabled(decode(route.moduleId), enabled)))
                }
                is AbkAgentRoute.RuntimeModulePendingUninstall -> requireMethod(session, Method.POST) {
                    val pending = readJsonBody(session)?.get("pending")?.asBoolean ?: false
                    jsonResponse(payload = shellResultPayload(AbkAgentFacade.setRuntimeModulePendingUninstall(decode(route.moduleId), pending)))
                }
                is AbkAgentRoute.RuntimeModuleAction -> requireMethod(session, Method.POST) {
                    acceptTask("runtime.module.action") {
                        val result = AbkAgentFacade.runRuntimeModuleAction(decode(route.moduleId)) { line -> log(line) }
                        if (result.success) {
                            success(
                                message = "module action complete",
                                result = shellResultPayload(result),
                            )
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "module action failed" }, appendMessage = false)
                        }
                    }
                }
                AbkAgentRoute.InstallModule -> requireMethod(session, Method.POST) {
                    val path = readJsonBody(session)?.get("zipPath")?.asString.orEmpty()
                    if (path.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "zipPath missing"))
                    }
                    acceptTask("install.module") {
                        val result = AbkAgentFacade.installModule(path) { line -> log(line) }
                        if (result.success) {
                            success(message = "module installed", result = shellResultPayload(result))
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "module install failed" })
                        }
                    }
                }
                AbkAgentRoute.InstallApk -> requireMethod(session, Method.POST) {
                    val path = readJsonBody(session)?.get("apkPath")?.asString.orEmpty()
                    if (path.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "apkPath missing"))
                    }
                    acceptTask("install.apk") {
                        val result = AbkAgentFacade.installApk(context, path) { line -> log(line) }
                        if (result.success) {
                            success(message = "apk installed", result = shellResultPayload(result))
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "apk install failed" })
                        }
                    }
                }
                AbkAgentRoute.FlashImage -> requireMethod(session, Method.POST) {
                    val body = readJsonBody(session)
                    val imagePath = body?.get("imagePath")?.asString.orEmpty()
                    val partition = body?.get("partition")?.asString?.ifBlank { "boot" } ?: "boot"
                    if (imagePath.isBlank()) {
                        return@requireMethod jsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "imagePath missing"))
                    }
                    acceptTask("flash.image") {
                        val result = AbkAgentFacade.flashImage(imagePath, partition) { line -> log(line) }
                        if (result.success) {
                            success(message = "image flashed", result = shellResultPayload(result))
                        } else {
                            fail(result.output.lastOrNull().orEmpty().ifBlank { "flash failed" })
                        }
                    }
                }
                AbkAgentRoute.ExportDiagnostics -> requireMethod(session, Method.POST) {
                    acceptTask("diagnostics.export") {
                        val (zipFile, warnings) = AbkAgentFacade.exportDiagnostics(context)
                        success(
                            message = "diagnostics exported",
                            result = mapOf("warnings" to warnings),
                            download = AbkAgentDownload(
                                file = zipFile,
                                fileName = zipFile.name,
                                contentType = "application/zip",
                            ),
                        )
                    }
                }
                is AbkAgentRoute.Task -> requireMethod(session, Method.GET) {
                    val snapshot = AbkAgentTaskStore.get(decode(route.taskId))
                        ?: return@requireMethod jsonResponse(
                            Response.Status.NOT_FOUND,
                            mapOf("error" to "task not found", "taskId" to route.taskId),
                        )
                    jsonResponse(payload = snapshot)
                }
                is AbkAgentRoute.TaskDownload -> requireMethod(session, Method.GET) {
                    val download = AbkAgentTaskStore.getDownload(decode(route.taskId))
                        ?: return@requireMethod jsonResponse(
                            Response.Status.NOT_FOUND,
                            mapOf("error" to "task download not available", "taskId" to route.taskId),
                        )
                    fileResponse(download)
                }
            }
        } catch (error: Exception) {
            jsonResponse(
                status = Response.Status.INTERNAL_ERROR,
                payload = mapOf(
                    "error" to (error.message ?: error::class.java.simpleName),
                ),
            )
        }
    }

    private fun requireMethod(
        session: IHTTPSession,
        method: Method,
        handler: () -> Response,
    ): Response {
        return if (session.method == method) {
            handler()
        } else {
            jsonResponse(
                status = Response.Status.METHOD_NOT_ALLOWED,
                payload = mapOf("error" to "method not allowed", "expected" to method.name),
            )
        }
    }

    private fun acceptTask(
        kind: String,
        operation: suspend AbkAgentTaskStore.AbkAgentTaskHandle.() -> Unit,
    ): Response {
        val snapshot = AbkAgentTaskStore.submit(kind, operation)
        return jsonResponse(
            status = Response.Status.ACCEPTED,
            payload = snapshot,
        )
    }

    private fun readJsonBody(session: IHTTPSession): JsonObject? {
        val body = readBody(session)
        if (body.isBlank()) return null
        return runCatching {
            JsonParser.parseString(body).asJsonObject
        }.getOrNull()
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"].orEmpty()
    }

    private fun jsonResponse(
        status: Response.Status = Response.Status.OK,
        payload: Any,
    ): Response {
        val response = newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            gson.toJson(payload),
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun fileResponse(download: AbkAgentDownload): Response {
        val response = newChunkedResponse(
            Response.Status.OK,
            download.contentType,
            FileInputStream(download.file),
        )
        response.addHeader("Content-Disposition", "attachment; filename=\"${download.fileName}\"")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun shellResultPayload(result: RootUtils.ShellResult): Map<String, Any> = mapOf(
        "success" to result.success,
        "output" to result.output,
    )

    private fun decode(raw: String): String =
        URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
}
