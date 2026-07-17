import 'package:abk_desktop/src/core/api/abk_sidecar_api.dart';
import 'package:abk_desktop/src/core/models/build_models.dart';
import 'package:abk_desktop/src/core/models/device_models.dart';
import 'package:abk_desktop/src/core/models/sidecar_models.dart';
import 'package:abk_desktop/src/features/build/build_page_controller.dart';
import 'package:abk_desktop/src/features/build/build_module_catalog.dart';
import 'package:flutter_test/flutter_test.dart';
import 'dart:typed_data';

void main() {
  test('github device flow keeps polling on authorization_pending', () async {
    final api = _FakeBuildApi(
      pollResults: <GitHubLoginResult>[
        const GitHubLoginResult(
          state: 'authorization_pending',
          session: null,
          error: 'The authorization request is still pending.',
        ),
        GitHubLoginResult(
          state: 'authorized',
          session: _loggedInSession,
          error: null,
        ),
      ],
    );

    final controller = BuildPageController(
      api: api,
      bootstrapOnInit: false,
      catalogClient: _FakeCatalogClient(),
    );
    addTearDown(controller.dispose);

    final challenge = await controller.startLogin();
    expect(challenge, isNotNull);

    await controller.pollLoginUntilAuthorized();

    expect(controller.state.session?.loggedIn, isTrue);
    expect(controller.state.loginChallenge, isNull);
    expect(controller.state.lastError, isNull);
  });

  test('replaceModuleSetSelection keeps set workflow syntax', () {
    final controller = BuildPageController(
      api: _FakeBuildApi(pollResults: const <GitHubLoginResult>[]),
      bootstrapOnInit: false,
      catalogClient: _FakeCatalogClient(),
    );
    addTearDown(controller.dispose);

    controller.replaceModuleSetSelection(
      groupRepoUrl: 'https://github.com/acme/abk-set',
      metadata: const BuildExternalModuleMetadata(
        name: 'ABK Extras',
        version: '1.2.3',
        description: 'A grouped module pack',
        kind: 'module_set',
        moduleSetId: 'abk-extras',
        supportedStages: <String>['after_patch', 'before_build'],
        defaultStage: 'after_patch',
        recommendedStages: <String>['after_patch'],
        children: <BuildModuleSetChildMetadata>[],
        magiskModuleName: '',
        magiskModuleDownloadUrl: '',
      ),
      selections: const <BuildModuleSetChildMetadata, List<String>>{
        BuildModuleSetChildMetadata(
          id: 'graphics',
          name: 'Graphics Pack',
          description: 'GPU tuning',
          repoUrl: 'https://github.com/acme/graphics',
          supportedStages: <String>['after_patch', 'before_build'],
          defaultStage: 'after_patch',
          recommendedStages: <String>['before_build'],
          groupRole: 'driver',
          controllable: true,
          hasWebUi: false,
          magiskModuleName: '',
          magiskModuleDownloadUrl: '',
        ): <String>[
          'before_build',
        ],
      },
      fromCatalog: true,
    );

    expect(
      controller.state.form.customModules,
      'set:https://github.com/acme/abk-set#graphics;before_build',
    );
    expect(controller.state.selectedModules.single.isModuleSetChild, isTrue);
    expect(controller.state.selectedModules.single.groupName, 'ABK Extras');
  });
}

class _FakeCatalogClient extends BuildModuleCatalogClient {
  @override
  Future<BuildModuleRepository> fetchRepository(String repositoryUrl) async {
    return BuildModuleRepository(
      url: repositoryUrl,
      name: 'ABK Repo',
      modules: const <BuildModuleCatalogItem>[],
      error: null,
      indexUrl: null,
    );
  }

  @override
  Future<BuildExternalModuleMetadata> fetchModuleMetadata(
    String repositoryUrl,
  ) async {
    throw UnimplementedError();
  }

  @override
  void close() {}
}

class _FakeBuildApi implements AbkSidecarApi {
  _FakeBuildApi({required List<GitHubLoginResult> pollResults})
    : _pollResults = List<GitHubLoginResult>.from(pollResults);

  final List<GitHubLoginResult> _pollResults;

  @override
  void close() {}

  @override
  Future<GitHubSessionStatus> getGitHubSession() async => _loggedInSession;

  @override
  Future<GitHubLoginChallenge> startGitHubLogin() async {
    return const GitHubLoginChallenge(
      deviceCode: 'device-code',
      userCode: 'ABCD-EFGH',
      verificationUri: 'https://github.com/login/device',
      verificationUriComplete: null,
      expiresIn: 60,
      interval: 0,
    );
  }

  @override
  Future<GitHubLoginResult> pollGitHubLogin(String deviceCode) async {
    return _pollResults.removeAt(0);
  }

  @override
  Future<GitHubSessionStatus> ensureGitHubFork() async => _loggedInSession;

  @override
  Future<GitHubSessionStatus> syncGitHubFork() async => _loggedInSession;

  @override
  Future<GitHubSessionStatus> logoutGitHub() async => const GitHubSessionStatus(
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

  @override
  Future<ProxySettings> getProxySettings() async => const ProxySettings.empty();

  @override
  Future<ProxySettings> saveProxySettings(
    Map<String, dynamic> request,
  ) async {
    return ProxySettings(
      httpProxy: request['httpProxy'] as String?,
      httpsProxy: request['httpsProxy'] as String?,
      allProxy: request['allProxy'] as String?,
      noProxy: request['noProxy'] as String?,
    );
  }

  @override
  Future<String?> setDownloadDirectory(String path) async => path;

  @override
  Future<RuntimeBuildSummary?> getRuntimeBuildSummary() async => null;

  @override
  Future<DesktopTaskSnapshot> startGkiBuild(Map<String, dynamic> request) {
    throw UnimplementedError();
  }

  @override
  Future<List<LocalBuildBackendDescriptor>> getLocalBuildBackends() async {
    return const <LocalBuildBackendDescriptor>[
      LocalBuildBackendDescriptor(
        kind: LocalBuildBackendKind.script,
        label: 'Script adapter',
        available: true,
        isGlobalDefault: true,
        authorizationRequired: false,
        authorizationKind: null,
        authorizationMessage: null,
        capabilities: LocalBuildBackendCapabilities(
          family: 'script',
          hostOwnedPaths: true,
          supportsSourceSync: true,
          supportsBuildExecution: true,
          supportsProfileProjection: true,
          notes: <String>[],
        ),
        detail: null,
      ),
    ];
  }

  @override
  Future<List<SupportedKernelLine>> getLocalBuildCatalog() async {
    return const <SupportedKernelLine>[
      SupportedKernelLine(
        id: 'android14/6.1',
        androidVersion: 'android14',
        kernelVersion: '6.1',
        displayName: 'android14 / 6.1',
        branchMonthFormat: 'YYYY-MM',
        scriptTemplatePath: '/tmp/local-build/AOSP_Kernel_A14_6.1',
        scriptTemplateAvailable: true,
      ),
    ];
  }

  @override
  Future<LocalBuildSettings> updateLocalBuildSettings(
    Map<String, dynamic> request,
  ) async {
    return const LocalBuildSettings(
      globalDefaultBackendKind: LocalBuildBackendKind.script,
      activeSourceInstanceId: null,
      scriptRootDir: null,
      workspaceDir: null,
      profileStoreDir: null,
    );
  }

  @override
  Future<LocalBuildSourceInstancesResponse> getLocalBuildSourceInstances() async {
    return const LocalBuildSourceInstancesResponse(
      settings: LocalBuildSettings(
        globalDefaultBackendKind: LocalBuildBackendKind.script,
        activeSourceInstanceId: null,
        scriptRootDir: null,
        workspaceDir: null,
        profileStoreDir: null,
      ),
      sourceInstances: <LocalBuildSourceInstance>[],
    );
  }

  @override
  Future<LocalBuildSourceInstance> createLocalBuildSourceInstance(
    Map<String, dynamic> request,
  ) async {
    return const LocalBuildSourceInstance(
      id: 'android14-6.1@2026-07',
      displayName: 'android14/6.1@2026-07',
      kernelLineId: 'android14/6.1',
      androidVersion: 'android14',
      kernelVersion: '6.1',
      branchMonth: '2026-07',
      cacheRoot: '/tmp/local-build/cache/android14-6.1@2026-07',
      workingTreeRoot: '/tmp/local-build/worktrees/android14-6.1@2026-07',
      state: 'draft',
      createdAtMs: 1,
      updatedAtMs: 1,
      lastSyncedAtMs: null,
      activeBackendKind: null,
      lastTaskId: null,
      lastError: null,
      materialized: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> syncLocalBuildSourceInstance(
    String sourceInstanceId,
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-source-sync',
      kind: 'local.build.source.sync',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<LocalBuildProfilesResponse> getLocalBuildProfiles() async {
    return const LocalBuildProfilesResponse(
      settings: LocalBuildSettings(
        globalDefaultBackendKind: LocalBuildBackendKind.script,
        activeSourceInstanceId: null,
        scriptRootDir: null,
        workspaceDir: null,
        profileStoreDir: null,
      ),
      profiles: <LocalBuildProfile>[],
    );
  }

  @override
  Future<LocalBuildProfile> saveLocalBuildProfile(
    Map<String, dynamic> request,
  ) async {
    return LocalBuildProfile(
      id: 'profile-1',
      name: (request['name'] as String?) ?? 'Profile 1',
      sourceInstanceId: (request['sourceInstanceId'] as String?) ?? '',
      backendKind: LocalBuildBackendKind.script,
      build: Map<String, dynamic>.from(
        request['build'] as Map? ?? const <String, dynamic>{},
      ),
      createdAtMs: 1,
      updatedAtMs: 1,
      lastBuiltAtMs: null,
      lastTaskId: null,
      lastError: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> buildLocalBuildProfile(
    String profileId,
    Map<String, dynamic> request,
  ) async {
    return const DesktopTaskSnapshot(
      id: 'task-local-profile-build',
      kind: 'local.build.profile.build',
      state: 'pending',
      message: 'pending',
      output: <String>[],
      result: <String, dynamic>{},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<List<LocalBuildArtifactEntry>> getLocalBuildArtifacts() async =>
      const <LocalBuildArtifactEntry>[];

  @override
  Future<List<LocalBuildLogEntry>> getLocalBuildLogs() async =>
      const <LocalBuildLogEntry>[];

  @override
  Future<LocalBuildStatus> getLocalBuildStatus() async {
    return const LocalBuildStatus(
      available: true,
      scriptRoot: '/tmp/local-build',
      initScriptPath: '/tmp/local-build/init.sh',
      rebuildScriptPath: '/tmp/local-build/rebuild.sh',
      envFilePath: '/tmp/local-build/.local-build/env.sh',
      stateDir: '/tmp/local-build/.local-build',
      sourcesDir: '/tmp/local-build/.local-build/sources',
      workspaceDir: '/tmp/local-build/.local-build/workspace',
      artifactsDir: '/tmp/local-build/.local-build/workspace/artifacts',
      logsDir: '/tmp/local-build/.local-build/workspace/logs',
      cacheDir: '/tmp/local-build/.local-build/workspace/cache',
      kernelRoot: '/tmp/local-build/.local-build/workspace/kernel',
      hasEnvFile: false,
      workspaceReady: false,
      templateRoot: null,
      templateName: null,
      templateAndroidVersion: null,
      templateKernelVersion: null,
      subLevel: null,
      osPatchLevel: null,
      templateBranch: null,
      templateCommonBranch: null,
      branchMonth: null,
      customExternalModulesRoot: null,
      customExternalModulesManifest: null,
      latestLogPath: null,
      supportedTemplates: <LocalBuildTemplate>[],
    );
  }

  @override
  Future<DesktopTaskSnapshot> initLocalBuild(Map<String, dynamic> request) {
    throw UnimplementedError();
  }

  @override
  Future<DesktopTaskSnapshot> rebuildLocalBuild(Map<String, dynamic> request) {
    throw UnimplementedError();
  }

  @override
  Future<BuildDispatchResult> listBuildRuns({int limit = 20}) async {
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
  Future<BuildDispatchResult> getBuildRun(int runId) {
    throw UnimplementedError();
  }

  @override
  Future<List<BuildArtifactSummary>> listBuildArtifacts(int runId) async =>
      const <BuildArtifactSummary>[];

  @override
  Future<DesktopTaskSnapshot> downloadBuildArtifact({
    required int runId,
    required int artifactId,
    String? outputDir,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<DesktopTaskSnapshot> getTask(String taskId) {
    throw UnimplementedError();
  }

  @override
  Future<DesktopTaskSnapshot> cancelTask(String taskId) async {
    return DesktopTaskSnapshot(
      id: taskId,
      kind: 'local.build.source.sync',
      state: 'cancelled',
      message: 'cancelled',
      output: const <String>[],
      result: const <String, dynamic>{'cancelable': false},
      downloadName: null,
      downloadContentType: null,
    );
  }

  @override
  Future<DesktopTaskSnapshot> exportDiagnostics() {
    throw UnimplementedError();
  }

  @override
  Uri taskDownloadUri(String taskId) => Uri.parse('http://127.0.0.1/$taskId');

  @override
  Future<SidecarHealth> getHealth() {
    throw UnimplementedError();
  }

  @override
  Future<DeviceConnectionState> getDeviceState() {
    throw UnimplementedError();
  }

  @override
  Future<DeviceDetectionResult> detectDevices() {
    throw UnimplementedError();
  }

  @override
  Future<ConnectResult> connectDevice(String serial) {
    throw UnimplementedError();
  }

  @override
  Future<DeviceConnectionState> disconnectDevice() {
    throw UnimplementedError();
  }

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
  Future<KernelFeaturesEnvelope> getKernelFeatures() async {
    return const KernelFeaturesEnvelope(
      rootGranted: false,
      managerAccessKind: 'no_root',
      managerDiagnostic: null,
      items: <KernelFeatureItem>[],
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
  Future<DesktopTaskSnapshot> applySusfs(Map<String, dynamic> config) {
    throw UnimplementedError();
  }

  @override
  Future<ShellOperationResult> setKernelFeatureEnabled(
    String featureId,
    bool enabled,
  ) async {
    return const ShellOperationResult(success: true, output: <String>['ok']);
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
  Future<DesktopTaskSnapshot> runRuntimeModuleAction(String moduleId) {
    throw UnimplementedError();
  }

  @override
  Future<DesktopTaskSnapshot> installModule(String zipPath) {
    throw UnimplementedError();
  }

  @override
  Uri runtimeModuleWebUiUri(String moduleId, {String? relativePath}) {
    return Uri.parse('http://127.0.0.1/$moduleId');
  }
}

const GitHubSessionStatus _loggedInSession = GitHubSessionStatus(
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
