@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.abk.kernel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abk.kernel.R
import com.abk.kernel.data.model.SusfsConfig
import com.abk.kernel.data.model.SusfsPresetOptions
import com.abk.kernel.ui.components.AbkInlineLoadingPill
import com.abk.kernel.ui.components.AbkSegmentedButtonOption
import com.abk.kernel.ui.components.AbkSingleChoiceSegmentedButtonRow
import com.abk.kernel.ui.components.ExpressiveSectionCard
import com.abk.kernel.ui.components.ExpressiveStatusChip
import com.abk.kernel.ui.components.ExpressiveSwitchItem
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_ALL
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_NON_SU
import com.abk.kernel.utils.SUSFS_HIDE_MOUNTS_OFF
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_BOOT_COMPLETED
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_OFF
import com.abk.kernel.utils.SUSFS_SPOOF_UNAME_POST_FS_DATA
import com.abk.kernel.utils.normalizeSusfsConfig
import com.abk.kernel.utils.parseSusfsKstatJson
import com.abk.kernel.utils.parseSusfsOpenRedirects
import com.abk.kernel.utils.parseSusfsPathRules
import com.abk.kernel.utils.parseSusfsStringList
import com.abk.kernel.utils.renderSusfsKstatJson
import com.abk.kernel.utils.renderSusfsOpenRedirects
import com.abk.kernel.utils.renderSusfsPathRules
import com.abk.kernel.utils.renderSusfsStringList
import com.abk.kernel.viewmodel.MainUiState

@Composable
internal fun SusfsControlScreen(
    padding: PaddingValues,
    state: MainUiState,
    showRefreshLoading: Boolean,
    onApply: (SusfsConfig) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    val runtime = state.susfsRuntimeStatus
    val config = normalizeSusfsConfig(state.susfsConfig)
    val applyFailedText = stringResource(R.string.susfs_apply_failed)

    var autoReplayEnabled by rememberSaveable { mutableStateOf(config.autoReplayEnabled) }
    var logEnabled by rememberSaveable { mutableStateOf(config.logEnabled) }
    var avcLogSpoofing by rememberSaveable { mutableStateOf(config.avcLogSpoofing) }
    var hideSusMountsMode by rememberSaveable { mutableStateOf(config.hideSusMountsMode) }
    var spoofUnameStage by rememberSaveable { mutableStateOf(config.spoofUnameStage) }
    var unameValue by rememberSaveable { mutableStateOf(config.unameValue) }
    var buildTimeValue by rememberSaveable { mutableStateOf(config.buildTimeValue) }
    var sdcardRootPath by rememberSaveable { mutableStateOf(config.sdcardRootPath) }
    var androidDataRootPath by rememberSaveable { mutableStateOf(config.androidDataRootPath) }
    var hideCustomRomLevel by rememberSaveable { mutableIntStateOf(config.presets.hideCustomRomLevel) }
    var emulateVoldAppDataMode by rememberSaveable { mutableIntStateOf(config.presets.emulateVoldAppDataMode) }
    var hideVendorSepolicy by rememberSaveable { mutableStateOf(config.presets.hideVendorSepolicy) }
    var hideCompatMatrix by rememberSaveable { mutableStateOf(config.presets.hideCompatMatrix) }
    var hideGapps by rememberSaveable { mutableStateOf(config.presets.hideGapps) }
    var hideRevanced by rememberSaveable { mutableStateOf(config.presets.hideRevanced) }
    var spoofCmdline by rememberSaveable { mutableStateOf(config.presets.spoofCmdline) }
    var hideLoops by rememberSaveable { mutableStateOf(config.presets.hideLoops) }
    var forceHideLsposed by rememberSaveable { mutableStateOf(config.presets.forceHideLsposed) }
    var autoTryUmount by rememberSaveable { mutableStateOf(config.presets.autoTryUmount) }
    var skipLegitMounts by rememberSaveable { mutableStateOf(config.presets.skipLegitMounts) }
    var umountForZygoteIsoService by rememberSaveable { mutableStateOf(config.presets.umountForZygoteIsoService) }
    var pathRulesText by rememberSaveable { mutableStateOf(renderSusfsPathRules(config.pathRules)) }
    var loopPathRulesText by rememberSaveable { mutableStateOf(renderSusfsPathRules(config.loopPathRules)) }
    var mapsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.maps)) }
    var mountsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.mounts)) }
    var tryUmountText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.tryUmounts)) }
    var legitMountsText by rememberSaveable { mutableStateOf(renderSusfsStringList(config.legitMounts)) }
    var openRedirectText by rememberSaveable { mutableStateOf(renderSusfsOpenRedirects(config.openRedirects)) }
    var kstatJsonText by rememberSaveable { mutableStateOf(renderSusfsKstatJson(config.kstatEntries)) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.susfsConfig) {
        val synced = normalizeSusfsConfig(state.susfsConfig)
        autoReplayEnabled = synced.autoReplayEnabled
        logEnabled = synced.logEnabled
        avcLogSpoofing = synced.avcLogSpoofing
        hideSusMountsMode = synced.hideSusMountsMode
        spoofUnameStage = synced.spoofUnameStage
        unameValue = synced.unameValue
        buildTimeValue = synced.buildTimeValue
        sdcardRootPath = synced.sdcardRootPath
        androidDataRootPath = synced.androidDataRootPath
        hideCustomRomLevel = synced.presets.hideCustomRomLevel
        emulateVoldAppDataMode = synced.presets.emulateVoldAppDataMode
        hideVendorSepolicy = synced.presets.hideVendorSepolicy
        hideCompatMatrix = synced.presets.hideCompatMatrix
        hideGapps = synced.presets.hideGapps
        hideRevanced = synced.presets.hideRevanced
        spoofCmdline = synced.presets.spoofCmdline
        hideLoops = synced.presets.hideLoops
        forceHideLsposed = synced.presets.forceHideLsposed
        autoTryUmount = synced.presets.autoTryUmount
        skipLegitMounts = synced.presets.skipLegitMounts
        umountForZygoteIsoService = synced.presets.umountForZygoteIsoService
        pathRulesText = renderSusfsPathRules(synced.pathRules)
        loopPathRulesText = renderSusfsPathRules(synced.loopPathRules)
        mapsText = renderSusfsStringList(synced.maps)
        mountsText = renderSusfsStringList(synced.mounts)
        tryUmountText = renderSusfsStringList(synced.tryUmounts)
        legitMountsText = renderSusfsStringList(synced.legitMounts)
        openRedirectText = renderSusfsOpenRedirects(synced.openRedirects)
        kstatJsonText = renderSusfsKstatJson(synced.kstatEntries)
        formError = null
    }

    fun submit() {
        formError = null
        runCatching {
            normalizeSusfsConfig(
                SusfsConfig(
                    autoReplayEnabled = autoReplayEnabled,
                    logEnabled = logEnabled,
                    avcLogSpoofing = avcLogSpoofing,
                    hideSusMountsMode = hideSusMountsMode,
                    spoofUnameStage = spoofUnameStage,
                    unameValue = unameValue,
                    buildTimeValue = buildTimeValue,
                    sdcardRootPath = sdcardRootPath,
                    androidDataRootPath = androidDataRootPath,
                    pathRules = parseSusfsPathRules(pathRulesText),
                    loopPathRules = parseSusfsPathRules(loopPathRulesText),
                    maps = parseSusfsStringList(mapsText),
                    mounts = parseSusfsStringList(mountsText),
                    tryUmounts = parseSusfsStringList(tryUmountText),
                    legitMounts = parseSusfsStringList(legitMountsText),
                    openRedirects = parseSusfsOpenRedirects(openRedirectText),
                    kstatEntries = parseSusfsKstatJson(kstatJsonText),
                    presets = SusfsPresetOptions(
                        hideCustomRomLevel = hideCustomRomLevel,
                        hideVendorSepolicy = hideVendorSepolicy,
                        hideCompatMatrix = hideCompatMatrix,
                        hideGapps = hideGapps,
                        hideRevanced = hideRevanced,
                        spoofCmdline = spoofCmdline,
                        hideLoops = hideLoops,
                        forceHideLsposed = forceHideLsposed,
                        autoTryUmount = autoTryUmount,
                        skipLegitMounts = skipLegitMounts,
                        emulateVoldAppDataMode = emulateVoldAppDataMode,
                        umountForZygoteIsoService = umountForZygoteIsoService,
                    ),
                )
            )
        }.onSuccess(onApply).onFailure { formError = it.message ?: applyFailedText }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showRefreshLoading) {
            AbkInlineLoadingPill(
                text = stringResource(R.string.settings_manager_loading_title),
                modifier = Modifier.fillMaxWidth(),
                compact = false
            )
        }
        state.susfsError?.takeIf { it.isNotBlank() }?.let { error ->
            ExpressiveSectionCard(
                title = "状态",
                subtitle = "SUSFS 探测或应用过程返回了错误",
                icon = Icons.Default.Info
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        formError?.takeIf { it.isNotBlank() }?.let { error ->
            ExpressiveSectionCard(
                title = "表单错误",
                subtitle = "请先修正配置格式，再重新应用",
                icon = Icons.Default.Info
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        runtime?.let {
            ExpressiveSectionCard(
                title = "运行时概览",
                subtitle = "latest binary、内核版本与动态特性探测",
                icon = Icons.Default.Extension
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveStatusChip(label = it.kernelVersion.ifBlank { "unknown" })
                    ExpressiveStatusChip(label = it.bundledBinaryVersion)
                    ExpressiveStatusChip(label = "${it.featureFlags.size} flags")
                }
                Spacer(Modifier.height(8.dp))
                Text("内核 SUSFS 版本: ${it.kernelVersion.ifBlank { "unknown" }}")
                Text("Bundled binary: ${it.bundledBinaryVersion} (${it.bundledBinaryRef})")
                Text("已安装 binary: ${it.installedBinaryPath.ifBlank { "未落盘" }}")
                Text("配置文件: ${it.configPath}")
                if (it.rawFeatureText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it.rawFeatureText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ExpressiveSectionCard(
            title = "应用",
            subtitle = "保存配置、刷新状态或恢复默认配置",
            icon = Icons.Default.Settings
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = ::submit,
                    enabled = !state.susfsSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.susfsSaving) "应用中..." else "应用配置")
                }
                TextButton(
                    onClick = onReset,
                    enabled = !state.susfsSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("恢复默认")
                }
            }
            TextButton(onClick = onRefresh, enabled = !state.susfsSaving) {
                Text("重新探测")
            }
        }

        ExpressiveSectionCard(
            title = "基础设置",
            subtitle = "最常用的运行时与开机重放开关",
            icon = Icons.Default.Settings
        ) {
            ExpressiveSwitchItem(
                title = "开机重放",
                subtitle = "由 ABK runtime module 在开机阶段自动重放当前配置",
                checked = autoReplayEnabled,
                onCheckedChange = { autoReplayEnabled = it }
            )
            ExpressiveSwitchItem(
                title = "启用日志",
                subtitle = "调用 ksu_susfs enable_log",
                checked = logEnabled,
                onCheckedChange = { logEnabled = it }
            )
            ExpressiveSwitchItem(
                title = "AVC Log Spoofing",
                subtitle = "调用 enable_avc_log_spoofing",
                checked = avcLogSpoofing,
                onCheckedChange = { avcLogSpoofing = it }
            )
            SegmentedSetting(
                title = "隐藏 SUS 挂载模式",
                options = listOf(
                    AbkSegmentedButtonOption(SUSFS_HIDE_MOUNTS_OFF, "关闭"),
                    AbkSegmentedButtonOption(SUSFS_HIDE_MOUNTS_ALL, "全部进程"),
                    AbkSegmentedButtonOption(SUSFS_HIDE_MOUNTS_NON_SU, "仅非 SU"),
                ),
                selected = hideSusMountsMode,
                onSelect = { hideSusMountsMode = it }
            )
            SegmentedSetting(
                title = "Spoof Uname 阶段",
                options = listOf(
                    AbkSegmentedButtonOption(SUSFS_SPOOF_UNAME_OFF, "关闭"),
                    AbkSegmentedButtonOption(SUSFS_SPOOF_UNAME_POST_FS_DATA, "post-fs-data"),
                    AbkSegmentedButtonOption(SUSFS_SPOOF_UNAME_BOOT_COMPLETED, "boot-completed"),
                ),
                selected = spoofUnameStage,
                onSelect = { spoofUnameStage = it }
            )
            TextAreaSetting("Uname 值", unameValue, { unameValue = it }, minLines = 1)
            TextAreaSetting("Build Time 值", buildTimeValue, { buildTimeValue = it }, minLines = 1)
            TextAreaSetting("sdcard root path", sdcardRootPath, { sdcardRootPath = it }, minLines = 1)
            TextAreaSetting("Android/data root path", androidDataRootPath, { androidDataRootPath = it }, minLines = 1)
        }

        ExpressiveSectionCard(
            title = "预设行为",
            subtitle = "对上游脚本行为的 ABK 化封装",
            icon = Icons.Default.Route
        ) {
            SegmentedSetting(
                title = "Hide Custom ROM 级别",
                options = (0..5).map { level ->
                    AbkSegmentedButtonOption(level, level.toString())
                },
                selected = hideCustomRomLevel,
                onSelect = { hideCustomRomLevel = it },
                equalWidth = false
            )
            SegmentedSetting(
                title = "模拟 Vold App Data",
                options = listOf(
                    AbkSegmentedButtonOption(0, "关闭"),
                    AbkSegmentedButtonOption(1, "sus_path"),
                    AbkSegmentedButtonOption(2, "sus_path_loop"),
                ),
                selected = emulateVoldAppDataMode,
                onSelect = { emulateVoldAppDataMode = it },
                equalWidth = false
            )
            ExpressiveSwitchItem("Hide Vendor Sepolicy", hideVendorSepolicy, { hideVendorSepolicy = it })
            ExpressiveSwitchItem("Hide Compat Matrix", hideCompatMatrix, { hideCompatMatrix = it })
            ExpressiveSwitchItem("Hide Gapps", hideGapps, { hideGapps = it })
            ExpressiveSwitchItem("Hide ReVanced", hideRevanced, { hideRevanced = it })
            ExpressiveSwitchItem("Spoof Cmdline", spoofCmdline, { spoofCmdline = it })
            ExpressiveSwitchItem("Hide Loops", hideLoops, { hideLoops = it })
            ExpressiveSwitchItem("Force Hide LSPosed", forceHideLsposed, { forceHideLsposed = it })
            ExpressiveSwitchItem("Auto Try Umount", autoTryUmount, { autoTryUmount = it })
            ExpressiveSwitchItem("Skip Legit Mounts", skipLegitMounts, { skipLegitMounts = it })
            ExpressiveSwitchItem("Umount For Zygote Iso Service", umountForZygoteIsoService, { umountForZygoteIsoService = it })
        }

        ExpressiveSectionCard(
            title = "路径与挂载",
            subtitle = "复杂能力统一用多行文本承载；每行一条规则",
            icon = Icons.Default.Storage
        ) {
            TextAreaSetting("sus_path", pathRulesText, { pathRulesText = it }, hint = "格式: <path> [max_tries]")
            TextAreaSetting("sus_path_loop", loopPathRulesText, { loopPathRulesText = it }, hint = "格式: <path> [max_tries]")
            TextAreaSetting("sus_map", mapsText, { mapsText = it })
            TextAreaSetting("sus_mount", mountsText, { mountsText = it })
            TextAreaSetting("try_umount", tryUmountText, { tryUmountText = it })
            TextAreaSetting("legit_mounts", legitMountsText, { legitMountsText = it })
        }

        ExpressiveSectionCard(
            title = "重定向与 KSTAT",
            subtitle = "open_redirect 用行式配置，KSTAT 用 JSON",
            icon = Icons.Default.DataObject
        ) {
            TextAreaSetting(
                "open_redirect",
                openRedirectText,
                { openRedirectText = it },
                hint = "格式: <original> <redirected> <boot_completed|service> [uid_scheme]"
            )
            TextAreaSetting("sus_kstat_statically.json", kstatJsonText, { kstatJsonText = it }, minLines = 8)
        }

        ExpressiveSectionCard(
            title = "最后输出",
            subtitle = "保存/应用过程中采集到的 shell 输出",
            icon = Icons.Default.Description
        ) {
            Text(
                text = state.susfsLastApplyOutput.joinToString("\n").ifBlank { "暂无输出" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun <T> SegmentedSetting(
    title: String,
    options: List<AbkSegmentedButtonOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    equalWidth: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        AbkSingleChoiceSegmentedButtonRow(
            options = options,
            selectedValue = selected,
            onSelect = onSelect,
            equalWidth = equalWidth,
            showSelectionIcon = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TextAreaSetting(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    minLines: Int = 4
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            supportingText = hint?.let { { Text(it) } }
        )
    }
}
