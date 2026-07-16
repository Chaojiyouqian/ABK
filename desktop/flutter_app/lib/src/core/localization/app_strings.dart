import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../models/build_models.dart';
import '../models/sidecar_models.dart';
import '../state/dashboard_controller.dart';

enum AppLocale { zhCn, en }

class AppStrings {
  const AppStrings._(this._locale);

  final AppLocale _locale;

  static const LocalizationsDelegate<AppStrings> delegate =
      _AppStringsDelegate();

  static const supportedLocales = <Locale>[Locale('zh', 'CN'), Locale('en')];

  static AppStrings of(BuildContext context) {
    final strings = Localizations.of<AppStrings>(context, AppStrings);
    assert(strings != null, 'AppStrings is not available in the widget tree.');
    return strings!;
  }

  bool get isChinese => _locale == AppLocale.zhCn;

  String get appTitle => isChinese ? 'ABK 桌面端' : 'ABK Desktop';
  String get brandWordmark => 'ABK';
  String get shellCompactSubtitle =>
      isChinese ? 'Linux 桌面客户端' : 'Linux desktop client';
  String get navHome => isChinese ? '主页' : 'Home';
  String get navDetection => isChinese ? '应用探测' : 'Application Detection';
  String get navDevice => isChinese ? '设备' : 'Device';
  String get navSettings => isChinese ? '设置' : 'Settings';
  String get openSidebar => isChinese ? '展开侧栏' : 'Expand sidebar';
  String get collapseSidebar => isChinese ? '收起侧栏' : 'Collapse sidebar';
  String get refreshPipeline => isChinese ? '刷新连接流水线' : 'Refresh pipeline';
  String get refreshing => isChinese ? '刷新中' : 'Refreshing';
  String get refreshDevices => isChinese ? '刷新设备' : 'Refresh devices';
  String get scanning => isChinese ? '扫描中' : 'Scanning';
  String get disconnect => isChinese ? '断开连接' : 'Disconnect';
  String get openDetectionPage => isChinese ? '打开探测页' : 'Open detection page';
  String get currentSelected => isChinese ? '当前已选设备' : 'Currently selected';

  String get homeTitle => isChinese ? '主页' : 'Home';
  String get homeIntro => isChinese
      ? '桌面端继续沿用 ABK 的产品语言，但信息组织要更适合宽屏和排障场景。'
      : 'Keep the ABK product language, but reorganize the desktop surface for wide-screen diagnostics.';
  String get homeNarrativeTitle => isChinese ? '连接路径' : 'Connection narrative';
  String get homeNarrativeSubtitle => isChinese
      ? '桌面端需要把探测、ADB、ABK 协议提升这条链路讲清楚，而不是把状态藏在报错里。'
      : 'The desktop should make the handshake path explicit instead of hiding it in transient errors.';

  String get detectionTitle => isChinese ? '应用探测' : 'Application Detection';
  String get detectionIntro => isChinese
      ? 'ADB 是底线可观测层。桌面端先确认设备可见，再尝试把单一可用设备提升到 ABK 私有协议。'
      : 'ADB is the baseline observability layer. The desktop verifies device visibility first, then attempts to promote one ready device into ABK.';
  String get detectionSummaryTitle => isChinese ? '探测摘要' : 'Detection summary';
  String get detectionSummarySubtitle => isChinese
      ? '这一页只做发现和连接，不承载完整运行时管理。'
      : 'This page stays focused on discovery and connection, not full runtime management.';
  String get noDetectedDevicesTitle =>
      isChinese ? '没有探测到设备' : 'No detected devices';
  String get noDetectedDevicesSubtitle => isChinese
      ? '连接打开 ADB 的设备后再刷新。主页上的自动连接流水线也会继续重试。'
      : 'Connect a device with ADB enabled, then refresh this page. The startup pipeline on home will retry.';
  String get nothingVisibleOverAdb =>
      isChinese ? '当前还没有任何设备通过 ADB 可见。' : 'Nothing is visible over ADB yet.';
  String get deviceEligibleForAbk => isChinese
      ? '这个设备已经满足发起 ABK 协议提升尝试的条件。'
      : 'This device is eligible for the ABK promotion attempt.';
  String get deviceNotReadyForAbk => isChinese
      ? 'ADB 已经看到这个设备，但它还没准备好进入 ABK 握手。'
      : 'ADB sees the device, but it is not ready for an ABK handshake yet.';
  String get connectThisDevice => isChinese ? '连接这个设备' : 'Connect this device';
  String get reconnectThisDevice =>
      isChinese ? '重新连接这个设备' : 'Reconnect this device';
  String get noExtraAdbDetail =>
      isChinese ? '没有更多 ADB 细节' : 'No extra ADB detail';

  String get errorCardTitle =>
      isChinese ? '当前为什么没有进入 ABK 模式' : 'Why you are not in ABK mode';
  String get errorCardSubtitle => isChinese
      ? '桌面端必须把降级状态说清楚，让用户知道自己正处在哪一层能力上。'
      : 'The desktop should keep fallback explicit so the user understands the current capability tier.';

  String get metricMode => isChinese ? '连接模式' : 'Mode';
  String get metricReadyDevices => isChinese ? '可用设备' : 'Ready devices';
  String get metricProtocol => isChinese ? '协议版本' : 'Protocol';
  String get metricTargetPort => isChinese ? '目标端口' : 'Target port';
  String get unknownValue => isChinese ? '未知' : 'Unknown';

  String get timelineDesktopSidecar =>
      isChinese ? '1. 桌面桥接服务' : '1. Desktop sidecar';
  String get timelineAdbDetection =>
      isChinese ? '2. ADB 探测' : '2. ADB detection';
  String get timelineAbkHandshake =>
      isChinese ? '3. ABK 握手' : '3. ABK handshake';
  String sidecarResponding(String host, int port) =>
      isChinese ? '桥接服务正在响应于 $host:$port' : 'Responding on $host:$port';
  String get sidecarNotResponding =>
      isChinese ? '桥接服务当前还没有响应' : 'Not responding yet';
  String readyDeviceCount(int count) =>
      isChinese ? '$count 台设备可用' : '$count ready device(s)';
  String get noVisibleDevices => isChinese ? '当前没有可见设备' : 'No visible devices';
  String sidecarAddress(String host, int port) =>
      isChinese ? '桥接服务：$host:$port' : 'Sidecar: $host:$port';

  String get detectTotalLabel => isChinese ? '已探测设备' : 'Detected';
  String get readyLabel => isChinese ? '可用设备' : 'Ready';
  String get readyStateLabel => isChinese ? '可用' : 'Ready';

  String deviceStatusLabel(String status) {
    final normalized = status.trim().toLowerCase();
    return switch (normalized) {
      'device' => readyStateLabel,
      'offline' => isChinese ? '离线' : 'offline',
      'unauthorized' => isChinese ? '未授权' : 'unauthorized',
      'recovery' => isChinese ? '恢复模式' : 'recovery',
      'sideload' => 'sideload',
      'no permissions' => isChinese ? '无权限' : 'no permissions',
      _ => status,
    };
  }

  String connectionModeLabel(DeviceConnectionMode mode) {
    return switch (mode) {
      DeviceConnectionMode.abk =>
        isChinese ? '已连接 ABK 协议' : 'Connected over ABK',
      DeviceConnectionMode.adbFallback =>
        isChinese ? 'ADB 降级模式' : 'ADB fallback mode',
      DeviceConnectionMode.disconnected => isChinese ? '空闲' : 'Idle',
    };
  }

  String connectionStatusLabel(ConnectionFlow flow) {
    return switch (flow) {
      ConnectionFlow.connectedAbk => isChinese ? 'ABK 已在线' : 'ABK active',
      ConnectionFlow.connectedAdbFallback =>
        isChinese ? 'ADB 降级中' : 'ADB fallback',
      ConnectionFlow.sidecarUnavailable =>
        isChinese ? '桥接服务离线' : 'Sidecar offline',
      ConnectionFlow.connecting => isChinese ? '连接中' : 'Connecting',
      ConnectionFlow.detecting => isChinese ? '扫描中' : 'Scanning',
      ConnectionFlow.failed => isChinese ? '需要处理' : 'Needs attention',
      ConnectionFlow.idle => isChinese ? '待扫描' : 'Ready to scan',
    };
  }

  String heroHeadline(ConnectionFlow flow) {
    return switch (flow) {
      ConnectionFlow.connectedAbk =>
        isChinese ? '已进入 ABK 协议' : 'Connected over ABK',
      ConnectionFlow.connectedAdbFallback =>
        isChinese ? 'ABK 握手失败' : 'ABK handshake failed',
      ConnectionFlow.connecting =>
        isChinese ? '正在连接设备' : 'Connecting to device',
      ConnectionFlow.detecting =>
        isChinese ? '正在扫描 ADB 设备' : 'Scanning for ADB devices',
      ConnectionFlow.sidecarUnavailable =>
        isChinese ? '桌面桥接服务当前不可用' : 'Desktop sidecar unavailable',
      ConnectionFlow.failed => isChinese ? '需要手动处理' : 'Manual action required',
      ConnectionFlow.idle => isChinese ? '准备开始连接' : 'Ready to connect',
    };
  }

  String heroSubtitle(ConnectionFlow flow) {
    return switch (flow) {
      ConnectionFlow.connectedAbk =>
        isChinese
            ? '手机侧代理已经健康，桌面端现在可以把这台设备当作一等 ABK 端点处理。'
            : 'The phone agent is healthy, so the desktop can now treat this device as a first-class ABK endpoint.',
      ConnectionFlow.connectedAdbFallback =>
        isChinese
            ? 'ADB 仍然看得到设备，但桌面端没能把会话提升到 ABK 私有协议。'
            : 'ADB still sees the device, but the desktop could not lift the session into ABK mode.',
      ConnectionFlow.connecting =>
        isChinese
            ? 'Sidecar 正在转发 ADB，并等待手机代理进入健康状态。'
            : 'The sidecar is forwarding ADB and waiting for the phone agent to become healthy.',
      ConnectionFlow.detecting =>
        isChinese
            ? '这一轮会先刷新 ADB 可见性，再决定是否发起 ABK 协议升级。'
            : 'This pass refreshes ADB visibility before deciding whether to attempt the ABK promotion.',
      ConnectionFlow.sidecarUnavailable =>
        isChinese
            ? '请先启动 Rust 桥接服务，然后重新运行桌面端连接流水线。'
            : 'Start the Rust sidecar first, then rerun the desktop pipeline.',
      ConnectionFlow.failed =>
        isChinese
            ? '请先解决当前探测歧义，再重新尝试连接。'
            : 'Resolve the current detection ambiguity, then retry the connection.',
      ConnectionFlow.idle =>
        isChinese
            ? '桌面端会保持轻量，直到出现值得提升到 ABK 的设备。'
            : 'The app stays shallow until there is a device worth promoting into ABK.',
    };
  }

  String heroPrimaryAction(DeviceConnectionMode mode) {
    return switch (mode) {
      DeviceConnectionMode.abk => isChinese ? '重新执行握手' : 'Re-run handshake',
      DeviceConnectionMode.adbFallback =>
        isChinese ? '重试 ABK 握手' : 'Retry ABK handshake',
      DeviceConnectionMode.disconnected =>
        isChinese ? '启动连接流水线' : 'Start pipeline',
    };
  }

  String sidecarAvailabilityLabel(bool available) {
    return available
        ? (isChinese ? '桥接服务已就绪' : 'Sidecar ready')
        : (isChinese ? '桥接服务缺失' : 'Sidecar missing');
  }

  String sidecarAvailabilityDescription(bool available) {
    return available
        ? (isChinese ? '本地桥接服务正在响应。' : 'The local sidecar is responding.')
        : (isChinese
              ? '当前桌面壳没有连上本地 Rust 桥接服务。'
              : 'The Flutter shell is not currently connected to the local Rust sidecar.');
  }

  String selectedDeviceHeadline(String? serial) {
    if (serial != null && serial.isNotEmpty) {
      return serial;
    }
    return isChinese ? '还没有选中设备' : 'No device selected';
  }

  String detectionErrorSummary({
    required ConnectionFlow flow,
    required int readyDeviceCount,
    String? rawError,
  }) {
    if (flow == ConnectionFlow.connectedAdbFallback) {
      return isChinese
          ? 'ABK 协议握手失败，但 ADB 仍然可见。这台设备现在处于降级模式。'
          : 'The ABK handshake failed, but ADB is still visible. The device is now in fallback mode.';
    }
    if (flow == ConnectionFlow.sidecarUnavailable) {
      return isChinese
          ? '当前没有可连接的桌面桥接服务。请先启动 Rust 进程。'
          : 'The desktop sidecar is not available yet. Start the Rust process first.';
    }
    if (flow == ConnectionFlow.failed && readyDeviceCount > 1) {
      return isChinese
          ? '探测到了多台可用设备。请先在探测页中明确选择要连接的序列号。'
          : 'Multiple ready ADB devices were detected. Pick the intended serial on the detection page.';
    }
    if (rawError != null && rawError.isNotEmpty) {
      return rawError;
    }
    return nothingVisibleOverAdb;
  }

  String get buildTitle => isChinese ? '构建' : 'Build';
  String get buildIntro => isChinese
      ? '用表单配置并触发 GKI 构建，桌面端会把 GitHub 登录、fork 状态、构建任务和产物下载串成一个闭环。'
      : 'Use a form to configure and trigger GKI builds. The desktop keeps GitHub login, fork state, build tasks, and artifact downloads in one flow.';
  String get buildErrorTitle => isChinese ? '构建状态异常' : 'Build status issue';
  String get buildAuthTitle =>
      isChinese ? 'GitHub 认证' : 'GitHub authentication';
  String get buildAuthSubtitle => isChinese
      ? '先完成登录和 fork 状态检查，再提交构建。'
      : 'Finish login and fork checks before submitting a build.';
  String get buildLogin => isChinese ? '登录 GitHub' : 'Log in to GitHub';
  String get buildLoginPolling => isChinese ? '正在验证登录' : 'Verifying login';
  String get buildLoginOpenBrowser =>
      isChinese ? '打开浏览器并输入验证码' : 'Open browser and enter the code';
  String get buildLoginCodeTitle => isChinese ? '设备验证码' : 'Device code';
  String get buildLoginCodeHint => isChinese
      ? '如果浏览器没有自动带上验证码，请手动复制下面这串 code 到 GitHub。'
      : 'If the browser does not autofill the code, copy the value below into GitHub manually.';
  String get buildLoginCopyCode => isChinese ? '复制验证码' : 'Copy code';
  String get buildLoginCopied =>
      isChinese ? '验证码已复制到剪贴板' : 'Device code copied';
  String get buildLoginOpenGitHub =>
      isChinese ? '打开 GitHub 验证页' : 'Open GitHub verification';
  String get buildSessionRestoring =>
      isChinese ? '正在恢复 GitHub 登录态' : 'Restoring GitHub session';
  String get buildForkEnsure => isChinese ? '创建 fork' : 'Ensure fork';
  String get buildForkSync => isChinese ? '同步 fork' : 'Sync fork';
  String get buildRefreshAll => isChinese ? '刷新全部' : 'Refresh all';
  String get buildRefreshRuns => isChinese ? '刷新构建列表' : 'Refresh builds';
  String get buildTargetTitle => isChinese ? '构建目标' : 'Build target';
  String get buildKsuTitle => isChinese ? 'KernelSU' : 'KernelSU';
  String get buildVersionTitle => isChinese ? '版本信息' : 'Version info';
  String get buildKsuBranchLabel => isChinese ? 'KSU 分支' : 'KSU branch';
  String get buildRevisionLabel => isChinese ? '修订版本' : 'Revision';
  String get buildAndroidVersionLabel =>
      isChinese ? 'Android 版本' : 'Android version';
  String get buildKernelVersionLabel => isChinese ? '内核版本' : 'Kernel version';
  String get buildSubLevelLabel => isChinese ? '子版本号' : 'Sub level';
  String get buildPatchLevelLabel =>
      isChinese ? '安全补丁级别' : 'Security patch level';
  String get buildVirtLabel => isChinese ? '虚拟化支持' : 'Virtualization support';
  String get buildCustomRefLabel => isChinese ? '自定义 KSU 引用' : 'Custom KSU ref';
  String get buildBuildTimeLabel => isChinese ? '自定义构建时间' : 'Custom build time';
  String get buildCustomModulesLabel => isChinese ? '外部模块清单' : 'Custom modules';
  String get buildKpmPasswordLabel => isChinese ? 'KPM 密码' : 'KPM password';
  String get buildZramExtraAlgosLabel =>
      isChinese ? 'ZRAM 额外算法' : 'ZRAM extra algos';
  String get buildFeatureTitle => isChinese ? '功能开关' : 'Feature flags';
  String get buildAdvancedTitle => isChinese ? '高级选项' : 'Advanced options';
  String get buildCustomModulesTitle =>
      isChinese ? '自定义外部模块' : 'Custom external modules';
  String get buildCustomModulesSubtitle => isChinese
      ? '可以从 ABK 模块仓库选择模块，也可以手动填写 GitHub 仓库 URL。'
      : 'Choose modules from the ABK module catalog or enter a GitHub repository URL manually.';
  String get buildModuleSetOpen =>
      isChinese ? '选择模块集内容' : 'Choose module set entries';
  String get buildSelectedModulesTitle =>
      isChinese ? '已选模块' : 'Selected modules';
  String get buildAddFromModuleRepo =>
      isChinese ? '从 ABK 模块仓库添加' : 'Add from ABK module catalog';
  String get buildManualModuleAdd =>
      isChinese ? '手动添加 GitHub URL' : 'Add GitHub URL manually';
  String get buildModuleRepositoryUrl =>
      isChinese ? '模块仓库 URL' : 'Module repository URL';
  String get buildModuleRepositoryAdd =>
      isChinese ? '添加模块仓库' : 'Add module repository';
  String get buildManualModuleUrl =>
      isChinese ? '模块 GitHub URL' : 'Module GitHub URL';
  String get buildManualModuleAddButton => isChinese ? '添加模块' : 'Add module';
  String get buildModuleStageLabel => isChinese ? '注入阶段' : 'Injection stage';
  String get buildModuleStageAfterPatch => isChinese ? '打补丁后' : 'After patch';
  String get buildModuleStageBeforeBuild => isChinese ? '编译前' : 'Before build';
  String get buildModuleSetChildrenTitle =>
      isChinese ? '模块集子项' : 'Module set entries';
  String get buildModuleSetEmpty =>
      isChinese ? '这个模块集当前没有可选子项' : 'This module set has no selectable entries';
  String get buildModuleSetLoadFailed =>
      isChinese ? '模块集元数据读取失败' : 'Failed to load module set metadata';
  String get buildModuleSetSave =>
      isChinese ? '保存模块集选择' : 'Save module set selection';
  String get buildRecommendedSuffix => isChinese ? '（推荐）' : ' (recommended)';
  String get buildModuleRemove => isChinese ? '移除模块' : 'Remove module';
  String get buildNoSelectedModules =>
      isChinese ? '当前还没有选中的外部模块' : 'No external modules selected yet';
  String get buildNoCatalogModules =>
      isChinese ? '当前仓库没有可用模块' : 'No modules available in this repository';
  String get buildAllCatalogModulesAdded => isChinese
      ? '这个仓库里的模块已经全部加入到下方列表了'
      : 'All modules from this repository are already in the selected list.';
  String get buildQueueTitle => isChinese ? '构建队列' : 'Build queue';
  String get buildQueueSubtitle => isChinese
      ? '最新提交的构建任务会先显示在这里，状态来自桌面本地任务与 GitHub run。'
      : 'The latest submitted build tasks appear here first. Status comes from local tasks and GitHub runs.';
  String get buildTaskOpenLogs => isChinese ? '查看日志' : 'View logs';
  String get buildTaskDetailsTitle => isChinese ? '任务日志' : 'Task logs';
  String get buildTaskOverviewTitle => isChinese ? '任务概览' : 'Task overview';
  String get buildTaskConsoleTitle => isChinese ? '控制台输出' : 'Console output';
  String get buildTaskResultTitle => isChinese ? '结果负载' : 'Result payload';
  String get buildTaskNoOutput => isChinese ? '当前还没有日志输出' : 'No log output yet';
  String get buildTaskNoResult =>
      isChinese ? '当前没有额外结果数据' : 'No extra result payload';
  String get buildTaskCopyLogs => isChinese ? '复制日志' : 'Copy logs';
  String get buildTaskLogsCopied =>
      isChinese ? '任务日志已复制到剪贴板' : 'Task logs copied';
  String get buildTaskIdentifier => isChinese ? '任务 ID' : 'Task ID';
  String get buildTaskLiveHint => isChinese
      ? '任务仍在运行时，这里的日志会随着轮询自动刷新。'
      : 'While the task is still running, this log view refreshes automatically.';
  String get buildWorkflowCenterTitle => isChinese ? '工作流列表' : 'Workflow list';
  String get buildWorkflowCenterSubtitle => isChinese
      ? '把 GitHub 上真实存在的内核工作流单独拉出来看，不和本地任务状态混在一起。'
      : 'Show the real kernel workflows from GitHub separately from local task state.';
  String get buildArtifactCenterTitle => isChinese ? '产物中心' : 'Artifact center';
  String get buildArtifactCenterSubtitle => isChinese
      ? '按工作流查看产物，不再把下载入口塞在同一张概览卡里。'
      : 'Browse artifacts by workflow instead of packing downloads into one overview card.';
  String get buildOpenWorkflowCenter => isChinese ? '打开工作流页' : 'Open workflows';
  String get buildOpenArtifactCenter =>
      isChinese ? '打开产物中心' : 'Open artifact center';
  String get buildActiveWorkflowsLabel =>
      isChinese ? '活跃工作流' : 'Active workflows';
  String get buildTotalWorkflowsLabel =>
      isChinese ? '工作流总数' : 'Total workflows';
  String get buildTaskWorkflowPending =>
      isChinese ? '工作流待分配' : 'Workflow pending';
  String get buildTaskCurrentStep => isChinese ? '当前步骤' : 'Current step';
  String get buildTaskOpenWorkflow => isChinese ? '查看工作流' : 'Open workflow';
  String get buildTaskNoWorkflowLink => isChinese
      ? '这个活动工作流还没有可打开的链接'
      : 'This active workflow does not have an openable link yet.';
  String get buildOpenRunArtifacts => isChinese ? '查看产物' : 'Open artifacts';
  String get buildDownloadBundle => isChinese ? '下载整组' : 'Download bundle';
  String get buildArtifactCategoryKernel =>
      isChinese ? '内核刷写包' : 'Kernel bundle';
  String get buildArtifactCategoryManager => isChinese ? '管理器' : 'Manager';
  String get buildArtifactCategoryModule => isChinese ? '模块' : 'Module';
  String get buildArtifactRawList => isChinese ? '原始产物' : 'Raw artifacts';
  String get buildArtifactRecommended => isChinese ? '推荐集合' : 'Recommended set';
  String get buildArtifactQueuedSingle =>
      isChinese ? '下载任务已加入队列。' : 'The download task was queued.';
  String buildArtifactQueuedMany(int count) =>
      isChinese ? '已加入 $count 个下载任务。' : 'Queued $count download tasks.';
  String get buildArtifactGroupedHint => isChinese
      ? '左侧选择工作流，右侧查看该工作流的产物。'
      : 'Pick a workflow on the left to inspect its artifacts on the right.';
  String get buildRunsTitle => isChinese ? '最近构建' : 'Recent builds';
  String get buildRunsSubtitle => isChinese
      ? '这里展示 GitHub 侧真实的 workflow run。'
      : 'These are the real GitHub workflow runs.';
  String get buildArtifactsTitle => isChinese ? '产物' : 'Artifacts';
  String get buildArtifactsSubtitle => isChinese
      ? '先下载，再校验，再打开目录。'
      : 'Download, verify, then open the directory.';
  String get buildSubmit => isChinese ? '提交构建' : 'Submit build';
  String get buildSubmitting => isChinese ? '正在提交' : 'Submitting';
  String get buildDownload => isChinese ? '下载产物' : 'Download artifact';
  String get buildOpenDirectory => isChinese ? '打开目录' : 'Open directory';
  String get buildNoRuns => isChinese ? '暂无构建记录' : 'No build runs yet';
  String get buildNoArtifacts =>
      isChinese ? '当前 run 没有产物' : 'This run has no artifacts';
  String get buildNoTasks => isChinese ? '当前没有任务' : 'No tasks yet';
  String get buildNoSession =>
      isChinese ? '尚未完成 GitHub 登录' : 'GitHub login not completed';
  String get buildNeedsFork =>
      isChinese ? '需要先创建 fork' : 'A fork is required first';
  String get buildNeedsSync =>
      isChinese ? 'fork 需要先同步' : 'The fork needs syncing first';
  String get buildLoggedInAs => isChinese ? '已登录为' : 'Logged in as';
  String get buildRepo => isChinese ? '仓库' : 'Repo';
  String get buildFork => isChinese ? 'Fork' : 'Fork';
  String get buildBehind => isChinese ? '落后' : 'Behind';
  String get buildSignKey => isChinese ? '签名公钥' : 'Signing key';
  String get buildSignKeyGitHub =>
      isChinese ? 'GitHub fork 公钥' : 'GitHub fork key';
  String get buildSignKeyUnknown => isChinese ? '未知来源' : 'Unknown source';
  String get buildRuntimePrefill => isChinese ? '自动预填' : 'Auto fill';
  String get buildRuntimePrefillSubtitle => isChinese
      ? '如果当前设备可读，桌面端会把 Android / kernel / sublevel / patch 信息带进表单。'
      : 'When available, the desktop brings Android/kernel/sublevel/patch values into the form.';
  String get buildCustomGroup => isChinese ? '自定义构建' : 'Custom build';
  String get buildCustomGroupSubtitle => isChinese
      ? '仅在 target = custom 时显示。'
      : 'Shown only when target = custom.';
  String get buildTaskGroup => isChinese ? '任务' : 'Tasks';
  String get buildTaskGroupSubtitle => isChinese
      ? '这里显示刚提交的构建和下载任务。'
      : 'Recently submitted build and download tasks appear here.';
  String get buildInfoLoginStarted => isChinese
      ? '已打开 GitHub 验证流程，请在浏览器中完成授权。'
      : 'The GitHub verification flow has started. Finish the authorization in your browser.';
  String get buildInfoLoginComplete =>
      isChinese ? 'GitHub 登录已完成。' : 'GitHub login complete.';
  String get buildInfoForkReady =>
      isChinese ? 'fork 已就绪。' : 'The fork is ready.';
  String get buildInfoForkSynced =>
      isChinese ? 'fork 已同步。' : 'The fork has been synced.';
  String get buildInfoBuildAccepted =>
      isChinese ? '构建任务已提交。' : 'The build request was accepted.';
  String get buildErrorLoginTimedOut =>
      isChinese ? 'GitHub 登录已超时。' : 'GitHub login timed out.';
  String get deviceTitle => isChinese ? '设备' : 'Device';
  String get deviceIntro => isChinese
      ? '这一页承载设备已经进入 ABK 后的联动能力：Root 授权、模块管理，以及进入内核功能与 SUSFS 的入口。'
      : 'This page holds the ABK-linked capabilities after the device enters ABK: root grants, module management, and entry points into kernel features and SUSFS.';
  String get deviceRefreshAll => isChinese ? '刷新设备页' : 'Refresh device page';
  String get deviceBlockedTitle =>
      isChinese ? '设备页需要 ABK 在线' : 'ABK must be online';
  String get deviceBlockedSubtitle => isChinese
      ? '先在主页或应用探测页把设备连接到 ABK，再回到这里管理授权、模块和内核功能。'
      : 'Connect the device into ABK from Home or Detection first, then come back here to manage grants, modules, and kernel features.';
  String get deviceOpenDetection => isChinese ? '打开应用探测' : 'Open detection';
  String get deviceTabRoot => isChinese ? 'Root 授权' : 'Root grants';
  String get deviceTabModules => isChinese ? '模块管理' : 'Modules';
  String get deviceTabKernel => isChinese ? '内核功能' : 'Kernel';
  String get deviceRootSearch =>
      isChinese ? '搜索应用 / 包名 / UID' : 'Search app / package / UID';
  String get deviceRootShowSystem => isChinese ? '显示系统应用' : 'Show system apps';
  String get deviceRootListTitle => isChinese ? 'Root 授权列表' : 'Root grants';
  String get deviceRootListSubtitle => isChinese
      ? '这里展示已进入原生管理器授权视野的应用。'
      : 'These are the apps currently visible to the native root grant manager.';
  String get deviceRootNoApps =>
      isChinese ? '当前没有可展示的 Root 授权应用' : 'No root-grant apps to show';
  String get deviceRootDetailTitle => isChinese ? '应用详情' : 'Application detail';
  String get deviceRootDetailEmpty => isChinese
      ? '从左侧列表选中一个应用，再查看详情和授权状态。'
      : 'Select an app from the list to inspect its detail and grant state.';
  String get deviceRootAllow => isChinese ? '允许 Root' : 'Allow root';
  String get deviceRootDenied => isChinese ? '未允许' : 'Not allowed';
  String get deviceRootUpdated =>
      isChinese ? 'Root 授权状态已更新。' : 'The root grant state was updated.';
  String get deviceModuleTabInstalled => isChinese ? '已安装' : 'Installed';
  String get deviceModuleTabRepository =>
      isChinese ? '运行时模块仓库' : 'Runtime repositories';
  String get deviceModuleTabLocalInstall =>
      isChinese ? '本地安装' : 'Local install';
  String get deviceModuleOfficialRepo =>
      isChinese ? '官方运行时模块仓库' : 'Official runtime module repo';
  String get deviceModuleRepoDefault =>
      isChinese ? '运行时模块仓库' : 'Runtime module repo';
  String get deviceModuleRepoUrl =>
      isChinese ? '运行时模块仓库 JSON URL' : 'Runtime module repository JSON URL';
  String get deviceModuleAddRepo =>
      isChinese ? '添加运行时仓库' : 'Add runtime repository';
  String get deviceModuleOpenRepo => isChinese ? '打开模块页' : 'Open module page';
  String get deviceModuleNoRepositories =>
      isChinese ? '当前没有运行时模块仓库' : 'No runtime module repositories';
  String get deviceModuleNoCatalogModules =>
      isChinese ? '当前仓库没有可用模块' : 'No modules available in this repository';
  String get deviceModuleSearch =>
      isChinese ? '搜索模块 / 作者 / 描述' : 'Search module / author / description';
  String get deviceModuleNoInstalled =>
      isChinese ? '当前没有已安装运行时模块' : 'No installed runtime modules';
  String get deviceModuleInstalledSubtitle => isChinese
      ? '把设备当前运行时模块拆成普通模块、自定义模块和自定义模块集三类看。'
      : 'Split the currently installed runtime modules into standard modules, custom modules, and custom module sets.';
  String get deviceModuleStandardTitle =>
      isChinese ? '普通模块' : 'Standard modules';
  String get deviceModuleStandardSubtitle => isChinese
      ? '常规运行时模块，包括标准 KernelSU / KPM / 内建模块。'
      : 'Regular runtime modules, including standard KernelSU, KPM, and built-in modules.';
  String get deviceModuleCustomTitle => isChinese ? '自定义模块' : 'Custom modules';
  String get deviceModuleCustomSubtitle => isChinese
      ? 'ABK 自定义外部模块会单独列在这里，不和普通模块混排。'
      : 'ABK custom external modules are listed here instead of being mixed into the standard module list.';
  String get deviceModuleSetTitle =>
      isChinese ? '自定义模块集' : 'Custom module sets';
  String get deviceModuleSetSubtitle => isChinese
      ? '同一个模块集的子模块会聚合展示，便于统一看 WebUI、action 和启停状态。'
      : 'Child modules from the same custom module set are grouped together so WebUI, action, and enable state stay readable.';
  String get deviceModuleNoStandard =>
      isChinese ? '当前没有普通运行时模块' : 'No standard runtime modules';
  String get deviceModuleNoCustom =>
      isChinese ? '当前没有自定义模块' : 'No custom modules';
  String get deviceModuleNoModuleSets =>
      isChinese ? '当前没有自定义模块集' : 'No custom module sets';
  String get deviceModuleRuntimeRepoTitle =>
      isChinese ? '运行时模块仓库' : 'Runtime module repositories';
  String get deviceModuleRuntimeRepoSubtitle => isChinese
      ? '这里只管理设备运行时模块仓库，不是构建页里的 ABK 模块仓库。'
      : 'This area manages runtime module repositories for the connected device, not the ABK build-module catalog from the Build page.';
  String get deviceModuleNoCatalogResults =>
      isChinese ? '没有匹配的仓库模块' : 'No matching repository modules';
  String get deviceModuleEnable => isChinese ? '启用' : 'Enable';
  String get deviceModulePendingUninstall =>
      isChinese ? '待卸载' : 'Pending uninstall';
  String get deviceModuleAction => isChinese ? '执行动作' : 'Run action';
  String get deviceModuleWebUi => isChinese ? '打开 WebUI' : 'Open WebUI';
  String get deviceModuleWebUiDesktop =>
      isChinese ? '在桌面独立窗口打开 WebUI' : 'Open WebUI in a separate desktop window';
  String get deviceModuleInstall => isChinese ? '安装模块' : 'Install module';
  String get deviceModuleChooseZip => isChinese ? '选择 ZIP' : 'Choose ZIP';
  String get deviceModuleNoLocalZip =>
      isChinese ? '当前还没有选中模块 ZIP' : 'No module ZIP selected yet';
  String get deviceModuleUpdated =>
      isChinese ? '模块状态已更新。' : 'The module state was updated.';
  String get deviceKernelSummaryTitle =>
      isChinese ? '运行时摘要' : 'Runtime summary';
  String get deviceKernelSummarySubtitle => isChinese
      ? '桌面端直接展示 agent 已返回的运行时信息，不在这里发明新的解释层。'
      : 'Render the runtime information returned by the agent directly instead of inventing a new interpretation layer here.';
  String get deviceKernelNoRuntime =>
      isChinese ? '当前没有可用的运行时摘要' : 'No runtime summary is available right now';
  String get deviceKernelEntryTitle =>
      isChinese ? '内核功能入口' : 'Kernel feature entry';
  String get deviceKernelEntrySubtitle => isChinese
      ? 'ADB Root、SULog、内核卸载模块等开关移到单独页面；这里保留入口和摘要。'
      : 'ADB Root, SU log, kernel unmount, and related toggles live on a dedicated page; this tab keeps the entry and summary.';
  String get deviceKernelOpenFeatures =>
      isChinese ? '打开内核功能页' : 'Open kernel features';
  String get deviceKernelFeaturesTitle =>
      isChinese ? '内核功能' : 'Kernel features';
  String get deviceKernelFeaturesIntro => isChinese
      ? '把 ADB Root、SULog、SELinux 隐藏与默认卸载模块等开关单独拎出来，按 Android ABK 的管理方式展示。'
      : 'ADB Root, SU log, SELinux hide, default unmount, and related toggles are surfaced here in an Android-ABK-style management page.';
  String get deviceKernelFeaturesUnsupported => isChinese
      ? '当前连接的设备侧 ABK 还没有暴露内核功能接口，请升级设备侧 ABK 并重新连接。'
      : 'The connected device-side ABK does not expose kernel feature controls yet. Upgrade the device-side ABK and reconnect.';
  String get deviceKernelFeatureUpdated =>
      isChinese ? '内核功能状态已更新。' : 'The kernel feature state was updated.';
  String get deviceKernelFeatureAdbRootTitle =>
      isChinese ? 'ADB Root' : 'ADB Root';
  String get deviceKernelFeatureAdbRootSubtitle =>
      isChinese ? '以 root 权限运行 adbd 守护进程。' : 'Run the adbd daemon as root.';
  String get deviceKernelFeatureSulogTitle =>
      isChinese ? '超级用户访问日志' : 'Superuser access log';
  String get deviceKernelFeatureSulogSubtitle => isChinese
      ? '记录与 Root 有关的事件到 KernelSU 的超级用户访问日志。'
      : 'Record root-related events to the KernelSU superuser access log.';
  String get deviceKernelFeatureKernelUmountTitle =>
      isChinese ? '卸载模块（内核级）' : 'Unmount modules (kernel level)';
  String get deviceKernelFeatureKernelUmountSubtitle => isChinese
      ? '让内核为需要的应用处理模块卸载。'
      : 'Let the kernel handle module unmount for apps that need it.';
  String get deviceKernelFeatureSelinuxHideTitle =>
      isChinese ? '隐藏 SELinux 修改' : 'Hide SELinux changes';
  String get deviceKernelFeatureSelinuxHideSubtitle => isChinese
      ? '阻止应用检测 SELinux 修改。'
      : 'Prevent apps from detecting SELinux changes.';
  String get deviceKernelFeatureDefaultUmountTitle =>
      isChinese ? '默认卸载模块' : 'Default unmount modules';
  String get deviceKernelFeatureDefaultUmountSubtitle => isChinese
      ? '作为 App Profile 里“卸载模块”的全局默认值。'
      : 'Use this as the global default for “Unmount modules” in App Profile.';
  String get deviceKernelFeatureStatusSupported =>
      isChinese ? '支持' : 'Supported';
  String get deviceKernelFeatureStatusManaged => isChinese ? '受管' : 'Managed';
  String get deviceKernelFeatureStatusUnsupported =>
      isChinese ? '不支持' : 'Unsupported';
  String get deviceSusfsTitle => isChinese ? 'SUSFS 控制' : 'SUSFS control';
  String get deviceSusfsSubtitle => isChinese
      ? '先展示 SUSFS 运行状态，再用 JSON 草稿编辑配置并应用。'
      : 'Show the SUSFS runtime state first, then let the user edit and apply the JSON config draft.';
  String get deviceSusfsPageTitle => isChinese ? 'SUSFS' : 'SUSFS';
  String get deviceSusfsPageIntro => isChinese
      ? 'SUSFS 单独落成一页，保留运行状态、诊断和 JSON 草稿编辑。'
      : 'SUSFS lives on its own page with runtime status, diagnostics, and JSON-draft editing.';
  String get deviceSusfsOpenPage =>
      isChinese ? '打开 SUSFS 页面' : 'Open SUSFS page';
  String get deviceSusfsApply =>
      isChinese ? '应用 SUSFS 配置' : 'Apply SUSFS config';
  String get deviceSusfsReset => isChinese ? '重置草稿' : 'Reset draft';
  String get deviceSusfsDraftInvalid =>
      isChinese ? 'SUSFS 配置 JSON 无效' : 'The SUSFS config JSON is invalid';
  String get deviceSusfsDraftEmpty =>
      isChinese ? 'SUSFS 配置草稿为空' : 'The SUSFS config draft is empty';
  String get deviceTaskQueued =>
      isChinese ? '任务已加入队列。' : 'The task was queued.';
  String get deviceTaskTitle => isChinese ? '设备任务' : 'Device tasks';
  String get deviceTaskSubtitle => isChinese
      ? '这里展示模块安装、模块动作、SUSFS 应用等设备侧任务。'
      : 'This list shows device-side tasks such as module installs, module actions, and SUSFS apply runs.';
  String get deviceTaskNoTasks =>
      isChinese ? '当前没有设备任务' : 'No device tasks yet';
  String get settingsTitle => isChinese ? '设置' : 'Settings';
  String get settingsIntro => isChinese
      ? '桌面端设置只承载账户、下载、诊断和关于等应用级能力；设备联动能力已经留在设备页。'
      : 'Desktop settings cover account, downloads, diagnostics, and about-level app settings. Device-linked capabilities stay on the Device page.';
  String get settingsRefresh => isChinese ? '刷新设置页' : 'Refresh settings';
  String get settingsAccountTitle => isChinese ? '账户' : 'Account';
  String get settingsAccountSubtitle => isChinese
      ? '这里展示 GitHub 登录态、fork 与下载目录等构建前提。'
      : 'This section shows the GitHub session, fork state, and other build prerequisites.';
  String get settingsNotLoggedIn => isChinese ? '未登录' : 'Not logged in';
  String get settingsLogout => isChinese ? '退出登录' : 'Log out';
  String get settingsLoggedOut =>
      isChinese ? 'GitHub 登录态已移除。' : 'The GitHub session was cleared.';
  String get settingsBuildTitle => isChinese ? '构建' : 'Build';
  String get settingsBuildSubtitle => isChinese
      ? '桌面端在这里承载下载目录和构建相关的基础偏好。'
      : 'This section carries the basic build-side preferences such as the download directory.';
  String get settingsDownloadDir =>
      isChinese ? '默认下载目录' : 'Default download directory';
  String get settingsChooseDirectory => isChinese ? '选择目录' : 'Choose directory';
  String get settingsSaveDirectory => isChinese ? '保存目录' : 'Save directory';
  String get settingsDirectorySaved =>
      isChinese ? '默认下载目录已保存。' : 'The default download directory was saved.';
  String get settingsDiagnosticsTitle => isChinese ? '诊断' : 'Diagnostics';
  String get settingsDiagnosticsSubtitle => isChinese
      ? '导出桌面壳与设备代理的诊断包，排障时直接从这里拿。'
      : 'Export a diagnostics bundle for the desktop shell and device agent from here.';
  String get settingsExportDiagnostics =>
      isChinese ? '导出诊断包' : 'Export diagnostics';
  String get settingsDownloadDiagnostic =>
      isChinese ? '下载诊断包' : 'Download diagnostics';
  String get settingsAboutTitle => isChinese ? '关于' : 'About';
  String get settingsAboutSubtitle => isChinese
      ? '展示桌面壳、sidecar 与连接状态的基础信息。'
      : 'Show the basic desktop shell, sidecar, and connection information.';
  String get settingsOpenFork => isChinese ? '打开 fork' : 'Open fork';
  String get settingsOpenRepo => isChinese ? '打开仓库' : 'Open repo';
  String get settingsNoDiagnosticsTask =>
      isChinese ? '当前没有诊断导出任务' : 'No diagnostics export task is available yet';
  String get settingsErrorTitle =>
      isChinese ? '设置页当前不可用' : 'Settings are currently unavailable';
  String get settingsDiagnosticsChecking => isChinese
      ? '正在检查设备侧 ABK 是否支持诊断导出。'
      : 'Checking whether the device-side ABK supports diagnostics export.';
  String get settingsDiagnosticsRequiresAbk => isChinese
      ? '需要先让设备进入 ABK 模式，才能导出设备侧诊断包。'
      : 'Move the device into ABK mode before exporting a device-side diagnostics bundle.';
  String get settingsDiagnosticsUnsupported => isChinese
      ? '当前连接的设备侧 ABK 不支持诊断导出，请升级设备侧 ABK 并重新连接。'
      : 'The connected device-side ABK does not support diagnostics export yet. Upgrade the device-side ABK and reconnect.';
  String get commonEdit => isChinese ? '编辑' : 'Edit';
  String get commonCount => isChinese ? '数量' : 'Count';
  String get commonSave => isChinese ? '保存' : 'Save';
  String get commonCancel => isChinese ? '取消' : 'Cancel';
  String artifactCategoryLabel(BuildArtifactCategory category) {
    return switch (category) {
      BuildArtifactCategory.kernel => buildArtifactCategoryKernel,
      BuildArtifactCategory.manager => buildArtifactCategoryManager,
      BuildArtifactCategory.module => buildArtifactCategoryModule,
    };
  }

  String buildTargetLabel(String target) {
    return switch (target) {
      'a12' => isChinese ? 'Android 12 / 5.10' : 'Android 12 / 5.10',
      'a13' => isChinese ? 'Android 13 / 5.15' : 'Android 13 / 5.15',
      'a14' => isChinese ? 'Android 14 / 6.1' : 'Android 14 / 6.1',
      'a15' => isChinese ? 'Android 15 / 6.6' : 'Android 15 / 6.6',
      'a16' => isChinese ? 'Android 16 / 6.12' : 'Android 16 / 6.12',
      'custom' => isChinese ? '自定义' : 'Custom',
      _ => target,
    };
  }

  String buildTaskLabel(String kind) {
    return switch (kind) {
      'build.gki' => isChinese ? 'GKI 构建' : 'GKI build',
      'artifact.download' => isChinese ? '产物下载' : 'Artifact download',
      'diagnostics.export' => isChinese ? '诊断导出' : 'Diagnostics export',
      'workflow.download' => isChinese ? '工作流下载' : 'Workflow download',
      _ => kind,
    };
  }

  String buildTaskStateLabel(String state) {
    return switch (state) {
      'pending' => isChinese ? '排队中' : 'Pending',
      'running' => isChinese ? '进行中' : 'Running',
      'succeeded' => isChinese ? '已完成' : 'Succeeded',
      'failed' => isChinese ? '失败' : 'Failed',
      _ => state,
    };
  }

  String buildRunStatusLabel(BuildRunSummary run) {
    if (run.isSuccess) {
      return isChinese ? '成功' : 'Success';
    }
    if (run.isFailure) {
      return isChinese ? '失败' : 'Failure';
    }
    if (run.isRunning) {
      return isChinese ? '进行中' : 'Running';
    }
    return run.status;
  }

  String buildModuleStageLabelForValue(String stage) {
    return switch (stage) {
      'before_build' => buildModuleStageBeforeBuild,
      _ => buildModuleStageAfterPatch,
    };
  }

  String deviceKernelFeatureTitle(String id) {
    return switch (id) {
      'adb_root' => deviceKernelFeatureAdbRootTitle,
      'sulog' => deviceKernelFeatureSulogTitle,
      'kernel_umount' => deviceKernelFeatureKernelUmountTitle,
      'selinux_hide' => deviceKernelFeatureSelinuxHideTitle,
      'default_umount' => deviceKernelFeatureDefaultUmountTitle,
      _ => id,
    };
  }

  String deviceKernelFeatureSubtitle(String id) {
    return switch (id) {
      'adb_root' => deviceKernelFeatureAdbRootSubtitle,
      'sulog' => deviceKernelFeatureSulogSubtitle,
      'kernel_umount' => deviceKernelFeatureKernelUmountSubtitle,
      'selinux_hide' => deviceKernelFeatureSelinuxHideSubtitle,
      'default_umount' => deviceKernelFeatureDefaultUmountSubtitle,
      _ => '',
    };
  }

  String deviceKernelFeatureStatusLabel(String status) {
    return switch (status) {
      'supported' => deviceKernelFeatureStatusSupported,
      'managed' => deviceKernelFeatureStatusManaged,
      _ => deviceKernelFeatureStatusUnsupported,
    };
  }

  static AppStrings fromLocale(Locale locale) {
    if (locale.languageCode.toLowerCase().startsWith('zh')) {
      return const AppStrings._(AppLocale.zhCn);
    }
    return const AppStrings._(AppLocale.en);
  }
}

extension AppStringsContext on BuildContext {
  AppStrings get strings => AppStrings.of(this);
}

class _AppStringsDelegate extends LocalizationsDelegate<AppStrings> {
  const _AppStringsDelegate();

  @override
  bool isSupported(Locale locale) {
    return AppStrings.supportedLocales.any(
      (supported) => supported.languageCode == locale.languageCode,
    );
  }

  @override
  Future<AppStrings> load(Locale locale) {
    return SynchronousFuture<AppStrings>(AppStrings.fromLocale(locale));
  }

  @override
  bool shouldReload(_AppStringsDelegate old) => false;
}
