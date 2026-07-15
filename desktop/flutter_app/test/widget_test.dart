import 'package:abk_desktop/src/app.dart';
import 'package:abk_desktop/src/core/api/abk_sidecar_api.dart';
import 'package:abk_desktop/src/core/localization/app_strings.dart';
import 'package:abk_desktop/src/core/models/build_models.dart';
import 'package:abk_desktop/src/core/models/device_models.dart';
import 'package:abk_desktop/src/core/models/sidecar_models.dart';
import 'package:abk_desktop/src/core/state/dashboard_controller.dart';
import 'package:abk_desktop/src/features/build/build_page.dart';
import 'package:abk_desktop/src/features/device/device_page.dart';
import 'package:abk_desktop/src/features/settings/settings_page.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'dart:typed_data';

void main() {
  testWidgets('shows ABK connected home after bootstrap', (tester) async {
    final api = _FakeSidecarApi();

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: const AbkDesktopApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('ABK'), findsWidgets);
    expect(find.text('主页'), findsAtLeastNWidgets(2));
    expect(find.text('应用探测'), findsOneWidget);
  });

  testWidgets('shows restoring session state before github session loads', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      sessionDelay: const Duration(milliseconds: 400),
    );

    await _pumpBuildPage(tester, api, settle: false);
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('正在恢复 GitHub 登录态'), findsOneWidget);
    expect(find.text('登录 GitHub'), findsNothing);

    await tester.pump(const Duration(milliseconds: 450));
    await tester.pumpAndSettle();
  });

  testWidgets('does not show stale serial when no adb devices are present', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      deviceState: DeviceConnectionState(
        serial: '34c4c788',
        agentHost: '127.0.0.1',
        agentPort: 48765,
        connected: false,
        mode: DeviceConnectionMode.disconnected,
        lastError: null,
        lastDetected: const <DetectedDevice>[],
        lastDetectRaw: '',
      ),
      detectionResult: const DeviceDetectionResult(
        devices: <DetectedDevice>[],
        raw: 'List of devices attached',
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: const AbkDesktopApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('34c4c788'), findsNothing);
    expect(find.text('还没有选中设备'), findsOneWidget);
  });

  testWidgets('shows only sync fork when fork exists but is behind', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      session: const GitHubSessionStatus(
        ok: true,
        loggedIn: true,
        repo: 'foo/bar',
        needsFork: false,
        needsSync: true,
        behindBy: 2,
        aheadBy: 0,
        userLogin: 'tester',
        forkFullName: 'tester/ABK',
        signingKeyAvailable: true,
        signingKeySource: 'config',
        downloadDir: '/tmp',
      ),
    );

    await _pumpBuildPage(tester, api);

    expect(find.text('同步 fork'), findsOneWidget);
    expect(find.text('创建 fork'), findsNothing);
    expect(find.text('登录 GitHub'), findsNothing);
  });

  testWidgets('shows only ensure fork when logged in without fork', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      session: const GitHubSessionStatus(
        ok: true,
        loggedIn: true,
        repo: 'foo/bar',
        needsFork: true,
        needsSync: false,
        behindBy: 0,
        aheadBy: 0,
        userLogin: 'tester',
        forkFullName: null,
        signingKeyAvailable: false,
        signingKeySource: null,
        downloadDir: null,
      ),
    );

    await _pumpBuildPage(tester, api);

    expect(find.text('创建 fork'), findsOneWidget);
    expect(find.text('同步 fork'), findsNothing);
    expect(find.text('登录 GitHub'), findsNothing);
  });

  testWidgets('takes over active kernel workflows into the queue list', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      runs: const <BuildRunSummary>[
        BuildRunSummary(
          id: 4242,
          name: 'Kernel Workflow',
          displayTitle: 'Android 14 / 6.1',
          status: 'in_progress',
          conclusion: null,
          event: 'workflow_dispatch',
          headBranch: 'main',
          htmlUrl: 'https://github.com/foo/bar/actions/runs/4242',
          createdAt: '2026-07-15T02:00:00Z',
          updatedAt: '2026-07-15T02:10:00Z',
          runNumber: 128,
        ),
      ],
    );

    await _pumpBuildPage(tester, api);

    expect(find.textContaining('#128 · Kernel Workflow'), findsOneWidget);
    expect(find.textContaining('当前步骤 · 进行中'), findsOneWidget);
  });

  testWidgets('shows blocked device page when ABK is not connected', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      deviceState: DeviceConnectionState.disconnected(),
      detectionResult: const DeviceDetectionResult(
        devices: <DetectedDevice>[],
        raw: 'List of devices attached',
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: DevicePage()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('设备页需要 ABK 在线'), findsOneWidget);
    expect(find.text('打开应用探测'), findsOneWidget);
  });

  testWidgets('shows settings page sections', (tester) async {
    final api = _FakeSidecarApi();

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('设置'), findsAtLeastNWidgets(1));
    expect(find.text('账户'), findsOneWidget);
    expect(find.text('构建'), findsOneWidget);
    expect(find.text('诊断'), findsOneWidget);
    expect(find.text('关于'), findsOneWidget);
  });

  testWidgets('shows restoring session state on settings page before load', (
    tester,
  ) async {
    final api = _FakeSidecarApi(
      sessionDelay: const Duration(milliseconds: 400),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.text('正在恢复 GitHub 登录态'), findsOneWidget);
    expect(find.text('未登录'), findsNothing);

    await tester.pump(const Duration(milliseconds: 450));
    await tester.pump();
  });

  testWidgets('shows diagnostics upgrade hint for legacy agent', (
    tester,
  ) async {
    final connectedDevice = DeviceConnectionState(
      serial: 'ABC123',
      agentHost: '127.0.0.1',
      agentPort: 48765,
      connected: true,
      mode: DeviceConnectionMode.abk,
      lastError: null,
      lastDetected: const <DetectedDevice>[
        DetectedDevice(
          serial: 'ABC123',
          status: 'device',
          detail: 'model:zorn product:abk',
        ),
      ],
      lastDetectRaw: '',
    );
    final api = _FakeSidecarApi(
      deviceState: connectedDevice,
      health: SidecarHealth(
        status: 'ok',
        protocolVersion: 'abk-desktop-sidecar-v1',
        sidecar: const SidecarEndpoint(host: '127.0.0.1', port: 38765),
        device: connectedDevice,
        agent: const AgentHealth(
          status: 'ok',
          protocolVersion: 'abk-agent-v1',
          port: 48765,
          appVersion: '1.0.0',
          appVersionCode: 1,
          rootGranted: true,
          managerAccessKind: 'native_manager',
          managerDiagnostic: null,
          capabilities: <String>[],
        ),
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('当前连接的设备侧 ABK 不支持诊断导出，请升级设备侧 ABK 并重新连接。'), findsOneWidget);
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '导出诊断包'),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets('enables diagnostics export when agent advertises capability', (
    tester,
  ) async {
    final connectedDevice = DeviceConnectionState(
      serial: 'ABC123',
      agentHost: '127.0.0.1',
      agentPort: 48765,
      connected: true,
      mode: DeviceConnectionMode.abk,
      lastError: null,
      lastDetected: const <DetectedDevice>[
        DetectedDevice(
          serial: 'ABC123',
          status: 'device',
          detail: 'model:zorn product:abk',
        ),
      ],
      lastDetectRaw: '',
    );
    final api = _FakeSidecarApi(
      deviceState: connectedDevice,
      health: SidecarHealth(
        status: 'ok',
        protocolVersion: 'abk-desktop-sidecar-v1',
        sidecar: const SidecarEndpoint(host: '127.0.0.1', port: 38765),
        device: connectedDevice,
        agent: const AgentHealth(
          status: 'ok',
          protocolVersion: 'abk-agent-v1',
          port: 48765,
          appVersion: '1.0.0',
          appVersionCode: 1,
          rootGranted: true,
          managerAccessKind: 'native_manager',
          managerDiagnostic: null,
          capabilities: <String>['diagnostics.export'],
        ),
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
        child: MaterialApp(
          locale: const Locale('zh', 'CN'),
          supportedLocales: AppStrings.supportedLocales,
          localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
            AppStrings.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: const Scaffold(body: SettingsPage()),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, '导出诊断包'),
    );
    expect(button.onPressed, isNotNull);
  });
}

Future<void> _pumpBuildPage(
  WidgetTester tester,
  _FakeSidecarApi api, {
  bool settle = true,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: <Override>[sidecarApiProvider.overrideWithValue(api)],
      child: MaterialApp(
        locale: const Locale('zh', 'CN'),
        supportedLocales: AppStrings.supportedLocales,
        localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
          AppStrings.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        home: const Scaffold(body: BuildPage()),
      ),
    ),
  );
  if (settle) {
    await tester.pumpAndSettle();
  }
}

class _FakeSidecarApi implements AbkSidecarApi {
  _FakeSidecarApi({
    this._deviceState,
    this._detectionResult,
    this._health,
    GitHubSessionStatus? session,
    List<BuildRunSummary>? runs,
    Map<int, List<BuildArtifactSummary>>? artifactsByRunId,
    this.sessionDelay = Duration.zero,
  }) : _session = session ?? _defaultSession,
       _runs = runs ?? const <BuildRunSummary>[],
       _artifactsByRunId =
           artifactsByRunId ?? const <int, List<BuildArtifactSummary>>{};

  final DeviceConnectionState? _deviceState;
  final DeviceDetectionResult? _detectionResult;
  final SidecarHealth? _health;
  final GitHubSessionStatus _session;
  final List<BuildRunSummary> _runs;
  final Map<int, List<BuildArtifactSummary>> _artifactsByRunId;
  final Duration sessionDelay;

  @override
  Future<DesktopTaskSnapshot> downloadBuildArtifact({
    required int runId,
    required int artifactId,
    String? outputDir,
  }) async {
    return const DesktopTaskSnapshot(
      id: 'task-download',
      kind: 'artifact.download',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<GitHubSessionStatus> ensureGitHubFork() async {
    return _session;
  }

  @override
  Future<ConnectResult> connectDevice(String serial) async {
    return ConnectResult(
      connected: true,
      mode: DeviceConnectionMode.abk,
      device: DeviceConnectionState(
        serial: serial,
        agentHost: '127.0.0.1',
        agentPort: 48765,
        connected: true,
        mode: DeviceConnectionMode.abk,
        lastError: null,
        lastDetected: const <DetectedDevice>[
          DetectedDevice(
            serial: 'ABC123',
            status: 'device',
            detail: 'model:zorn product:abk',
          ),
        ],
        lastDetectRaw: '',
      ),
      agent: const AgentHealth(
        status: 'ok',
        protocolVersion: 'abk-agent-v1',
        port: 48765,
        appVersion: '1.0.0',
        appVersionCode: 1,
        rootGranted: true,
        managerAccessKind: 'native_manager',
        managerDiagnostic: null,
        capabilities: <String>['diagnostics.export'],
      ),
    );
  }

  @override
  void close() {}

  @override
  Future<DeviceDetectionResult> detectDevices() async {
    return _detectionResult ??
        const DeviceDetectionResult(
          devices: <DetectedDevice>[
            DetectedDevice(
              serial: 'ABC123',
              status: 'device',
              detail: 'model:zorn product:abk',
            ),
          ],
          raw: 'List of devices attached',
        );
  }

  @override
  Future<DeviceConnectionState> disconnectDevice() async {
    return DeviceConnectionState.disconnected();
  }

  @override
  Future<DeviceConnectionState> getDeviceState() async {
    return _deviceState ??
        DeviceConnectionState.disconnected(
          lastDetected: const <DetectedDevice>[
            DetectedDevice(
              serial: 'ABC123',
              status: 'device',
              detail: 'model:zorn product:abk',
            ),
          ],
        );
  }

  @override
  Future<BuildDispatchResult> getBuildRun(int runId) async {
    return const BuildDispatchResult(
      ok: true,
      repo: 'foo/bar',
      dryRun: false,
      total: 0,
      run: null,
      runs: <BuildRunSummary>[],
      dispatches: <BuildDispatchItem>[],
      warnings: <String>[],
      error: null,
    );
  }

  @override
  Future<GitHubSessionStatus> getGitHubSession() async {
    if (sessionDelay > Duration.zero) {
      await Future<void>.delayed(sessionDelay);
    }
    return _session;
  }

  @override
  Future<SidecarHealth> getHealth() async {
    return _health ??
        SidecarHealth(
          status: 'ok',
          protocolVersion: 'abk-desktop-sidecar-v1',
          sidecar: const SidecarEndpoint(host: '127.0.0.1', port: 38765),
          device: await getDeviceState(),
          agent: null,
        );
  }

  @override
  Future<DesktopTaskSnapshot> getTask(String taskId) async {
    return const DesktopTaskSnapshot(
      id: 'task',
      kind: 'build.gki',
      state: 'succeeded',
      message: 'done',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> exportDiagnostics() async {
    return const DesktopTaskSnapshot(
      id: 'task-diagnostics',
      kind: 'diagnostics.export',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Uri taskDownloadUri(String taskId) =>
      Uri.parse('http://127.0.0.1:38765/api/v1/tasks/$taskId/download');

  @override
  Future<List<BuildArtifactSummary>> listBuildArtifacts(int runId) async {
    return _artifactsByRunId[runId] ?? const <BuildArtifactSummary>[];
  }

  @override
  Future<BuildDispatchResult> listBuildRuns({int limit = 20}) async {
    return BuildDispatchResult(
      ok: true,
      repo: 'foo/bar',
      dryRun: false,
      total: _runs.length,
      run: null,
      runs: _runs,
      dispatches: const <BuildDispatchItem>[],
      warnings: const <String>[],
      error: null,
    );
  }

  @override
  Future<GitHubLoginResult> pollGitHubLogin(String deviceCode) async {
    return GitHubLoginResult(
      state: 'authorized',
      session: _session,
      error: null,
    );
  }

  @override
  Future<RuntimeBuildSummary?> getRuntimeBuildSummary() async {
    return const RuntimeBuildSummary(
      androidVersion: 'android14',
      kernelVersion: '6.1',
      subLevel: '162',
      osPatchLevel: '2025-05',
      revision: 'r11',
    );
  }

  @override
  Future<GitHubLoginChallenge> startGitHubLogin() async {
    return const GitHubLoginChallenge(
      deviceCode: 'device',
      userCode: 'user',
      verificationUri: 'https://github.com/login/device',
      verificationUriComplete: null,
      expiresIn: 900,
      interval: 5,
    );
  }

  @override
  Future<DesktopTaskSnapshot> startGkiBuild(
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-build',
      kind: 'build.gki',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<GitHubSessionStatus> syncGitHubFork() async {
    return _session;
  }

  @override
  Future<GitHubSessionStatus> logoutGitHub() async {
    return const GitHubSessionStatus(
      ok: true,
      loggedIn: false,
      repo: 'foo/bar',
      needsFork: true,
      needsSync: false,
      behindBy: 0,
      aheadBy: 0,
      userLogin: null,
      forkFullName: null,
      signingKeyAvailable: false,
      signingKeySource: null,
      downloadDir: '/tmp',
    );
  }

  @override
  Future<String?> setDownloadDirectory(String path) async => path;

  @override
  Future<AbkRuntimeEnvelope> getRuntime() async {
    return const AbkRuntimeEnvelope(
      rootGranted: false,
      managerAccessKind: 'no_root',
      managerDiagnostic: null,
      runtimeStatus: null,
    );
  }

  @override
  Future<RootGrantsEnvelope> getRootGrants() async {
    return const RootGrantsEnvelope(
      rootGranted: false,
      managerAccessKind: 'no_root',
      managerDiagnostic: null,
      apps: <RootGrantApp>[],
    );
  }

  @override
  Future<PackageInfoSummary?> getPackageInfo(String packageName) async => null;

  @override
  Future<ShellOperationResult> setRootGrantAllowed(
    String packageName,
    bool allowed,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<Uint8List?> getRootGrantIcon(String packageName) async => null;

  @override
  Future<SusfsEnvelope> getSusfs() async {
    return const SusfsEnvelope(
      rootGranted: false,
      status: null,
      config: <String, dynamic>{},
      error: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> applySusfs(Map<String, dynamic> config) async {
    return const DesktopTaskSnapshot(
      id: 'task-susfs',
      kind: 'susfs.apply',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<ShellOperationResult> setRuntimeModuleEnabled(
    String moduleId,
    bool enabled,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<ShellOperationResult> setRuntimeModulePendingUninstall(
    String moduleId,
    bool pending,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
  }

  @override
  Future<DesktopTaskSnapshot> runRuntimeModuleAction(String moduleId) async {
    return const DesktopTaskSnapshot(
      id: 'task-module-action',
      kind: 'runtime.module.action',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> installModule(String zipPath) async {
    return const DesktopTaskSnapshot(
      id: 'task-install-module',
      kind: 'install.module',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Uri runtimeModuleWebUiUri(String moduleId, {String? relativePath}) {
    return Uri.parse('http://127.0.0.1/$moduleId');
  }

  static const GitHubSessionStatus _defaultSession = GitHubSessionStatus(
    ok: true,
    loggedIn: true,
    repo: 'foo/bar',
    needsFork: false,
    needsSync: false,
    behindBy: 0,
    aheadBy: 0,
    userLogin: 'tester',
    forkFullName: 'tester/ABK',
    signingKeyAvailable: true,
    signingKeySource: 'config',
    downloadDir: '/tmp',
  );
}
