class GitHubSessionStatus {
  const GitHubSessionStatus({
    required this.ok,
    required this.loggedIn,
    required this.repo,
    required this.needsFork,
    required this.needsSync,
    required this.behindBy,
    required this.aheadBy,
    required this.userLogin,
    required this.forkFullName,
    required this.signingKeyAvailable,
    required this.signingKeySource,
    required this.downloadDir,
  });

  final bool ok;
  final bool loggedIn;
  final String repo;
  final bool needsFork;
  final bool needsSync;
  final int behindBy;
  final int aheadBy;
  final String? userLogin;
  final String? forkFullName;
  final bool signingKeyAvailable;
  final String? signingKeySource;
  final String? downloadDir;

  factory GitHubSessionStatus.fromJson(Map<String, dynamic> json) {
    final fork = _readMap(json['fork']);
    final user = _readMap(json['user']);
    return GitHubSessionStatus(
      ok: json['ok'] == true,
      loggedIn: json['loggedIn'] == true,
      repo: _readString(json['repo']),
      needsFork: json['needsFork'] == true,
      needsSync: json['needsSync'] == true,
      behindBy: _readInt(json['behindBy']),
      aheadBy: _readInt(json['aheadBy']),
      userLogin: _nullableString(user['login']),
      forkFullName: _nullableString(fork['fullName']),
      signingKeyAvailable: json['signingKeyAvailable'] == true,
      signingKeySource: _nullableString(json['signingKeySource']),
      downloadDir: _nullableString(json['downloadDir']),
    );
  }
}

class GitHubLoginChallenge {
  const GitHubLoginChallenge({
    required this.deviceCode,
    required this.userCode,
    required this.verificationUri,
    required this.verificationUriComplete,
    required this.expiresIn,
    required this.interval,
  });

  final String deviceCode;
  final String userCode;
  final String verificationUri;
  final String? verificationUriComplete;
  final int expiresIn;
  final int interval;

  factory GitHubLoginChallenge.fromJson(Map<String, dynamic> json) {
    return GitHubLoginChallenge(
      deviceCode: _readString(json['deviceCode']),
      userCode: _readString(json['userCode']),
      verificationUri: _readString(json['verificationUri']),
      verificationUriComplete: _nullableString(json['verificationUriComplete']),
      expiresIn: _readInt(json['expiresIn'], fallback: 900),
      interval: _readInt(json['interval'], fallback: 5),
    );
  }
}

class GitHubLoginResult {
  const GitHubLoginResult({
    required this.state,
    required this.session,
    required this.error,
  });

  final String state;
  final GitHubSessionStatus? session;
  final String? error;

  factory GitHubLoginResult.fromJson(Map<String, dynamic> json) {
    return GitHubLoginResult(
      state: _readString(json['state'], fallback: 'unknown'),
      session: json['session'] is Map<String, dynamic>
          ? GitHubSessionStatus.fromJson(
              Map<String, dynamic>.from(json['session'] as Map),
            )
          : null,
      error: _nullableString(json['error']),
    );
  }
}

class RuntimeBuildSummary {
  const RuntimeBuildSummary({
    required this.androidVersion,
    required this.kernelVersion,
    required this.subLevel,
    required this.osPatchLevel,
    required this.revision,
  });

  final String androidVersion;
  final String kernelVersion;
  final String subLevel;
  final String osPatchLevel;
  final String revision;

  factory RuntimeBuildSummary.fromJson(Map<String, dynamic> json) {
    return RuntimeBuildSummary(
      androidVersion: _readString(json['androidVersion']),
      kernelVersion: _readString(json['kernelVersion']),
      subLevel: _readString(json['subLevel']),
      osPatchLevel: _readString(json['osPatchLevel']),
      revision: _readString(json['revision']),
    );
  }
}

class BuildRunSummary {
  const BuildRunSummary({
    required this.id,
    required this.name,
    required this.displayTitle,
    required this.status,
    required this.conclusion,
    required this.event,
    required this.headBranch,
    required this.htmlUrl,
    required this.createdAt,
    required this.updatedAt,
    required this.runNumber,
  });

  final int id;
  final String name;
  final String displayTitle;
  final String status;
  final String? conclusion;
  final String? event;
  final String? headBranch;
  final String? htmlUrl;
  final String? createdAt;
  final String? updatedAt;
  final int runNumber;

  bool get isRunning => status == 'queued' || status == 'in_progress';
  bool get isSuccess => status == 'completed' && conclusion == 'success';
  bool get isFailure => status == 'completed' && conclusion == 'failure';
  bool get looksLikeKernelBuild {
    final haystack = '${name.toLowerCase()} ${displayTitle.toLowerCase()}';
    return (haystack.contains('kernel') || haystack.contains('内核')) &&
        !haystack.contains('build abk app');
  }

  factory BuildRunSummary.fromJson(Map<String, dynamic> json) {
    return BuildRunSummary(
      id: _readInt(json['id']),
      name: _readString(json['name']),
      displayTitle: _readString(
        json['displayTitle'],
        fallback: _readString(json['name']),
      ),
      status: _readString(json['status']),
      conclusion: _nullableString(json['conclusion']),
      event: _nullableString(json['event']),
      headBranch: _nullableString(json['headBranch']),
      htmlUrl: _nullableString(json['htmlUrl']),
      createdAt: _nullableString(json['createdAt']),
      updatedAt: _nullableString(json['updatedAt']),
      runNumber: _readInt(json['runNumber']),
    );
  }
}

class BuildArtifactSummary {
  const BuildArtifactSummary({
    required this.id,
    required this.name,
    required this.sizeBytes,
    required this.expired,
    required this.archiveDownloadUrl,
  });

  final int id;
  final String name;
  final int sizeBytes;
  final bool expired;
  final String? archiveDownloadUrl;

  factory BuildArtifactSummary.fromJson(Map<String, dynamic> json) {
    return BuildArtifactSummary(
      id: _readInt(json['id']),
      name: _readString(json['name']),
      sizeBytes: _readInt(json['sizeBytes']),
      expired: json['expired'] == true,
      archiveDownloadUrl: _nullableString(json['archiveDownloadUrl']),
    );
  }
}

enum BuildArtifactType {
  kernelPackage,
  kernelImage,
  anyKernel3,
  abkManager,
  ksuManager,
  susfsModule,
  other,
}

enum BuildArtifactCategory { kernel, manager, module }

extension BuildArtifactSummaryClassify on BuildArtifactSummary {
  BuildArtifactType get artifactType {
    final lower = name.trim().toLowerCase();
    if (lower.contains('reject') || lower.contains('-rej')) {
      return BuildArtifactType.other;
    }
    if (lower.contains('_kernel-android') || lower.contains('kernel-android')) {
      return BuildArtifactType.kernelPackage;
    }
    if (lower.endsWith('.img') &&
        (lower.contains('boot') ||
            lower.contains('kernel') ||
            lower.contains('gki'))) {
      return BuildArtifactType.kernelImage;
    }
    if (lower.contains('boot-img') ||
        lower.contains('boot_img') ||
        lower.contains('kernel-img')) {
      return BuildArtifactType.kernelImage;
    }
    if (lower.contains('raw-image') || lower.contains('raw_image')) {
      return BuildArtifactType.kernelImage;
    }
    if (lower.contains('anykernel') || lower.contains('ak3')) {
      return BuildArtifactType.anyKernel3;
    }
    if (lower.endsWith('.zip') && _isLikelyModuleZipName(lower)) {
      return BuildArtifactType.susfsModule;
    }
    if (_isLikelyModuleZipName(lower) && !lower.contains('anykernel')) {
      return BuildArtifactType.susfsModule;
    }
    if (lower == 'abk-apks' || lower.contains('abk-apks')) {
      return BuildArtifactType.abkManager;
    }
    if (lower.contains('abk') && lower.endsWith('.apk')) {
      return BuildArtifactType.abkManager;
    }
    if (lower.endsWith('.apk') &&
        (lower.contains('manager') ||
            lower.contains('kernelsu') ||
            lower.contains('ksu') ||
            lower.contains('suki'))) {
      return BuildArtifactType.ksuManager;
    }
    if (lower.contains('manager') &&
        (lower.contains('kernelsu') ||
            lower.contains('ksu') ||
            lower.contains('suki'))) {
      return BuildArtifactType.ksuManager;
    }
    if (lower.contains('sukisu-ultra') || lower.contains('sukisu_ultra')) {
      return BuildArtifactType.ksuManager;
    }
    return BuildArtifactType.other;
  }

  BuildArtifactCategory? get artifactCategory {
    return switch (artifactType) {
      BuildArtifactType.kernelPackage ||
      BuildArtifactType.kernelImage ||
      BuildArtifactType.anyKernel3 => BuildArtifactCategory.kernel,
      BuildArtifactType.abkManager || BuildArtifactType.ksuManager =>
        BuildArtifactCategory.manager,
      BuildArtifactType.susfsModule => BuildArtifactCategory.module,
      BuildArtifactType.other => null,
    };
  }
}

bool _isLikelyModuleZipName(String lower) =>
    lower.contains('susfs') ||
    lower.contains('module') ||
    lower.contains('magisk') ||
    lower.contains('zygisk') ||
    lower.contains('kpm');

class DesktopTaskSnapshot {
  const DesktopTaskSnapshot({
    required this.id,
    required this.kind,
    required this.state,
    required this.message,
    required this.output,
    required this.result,
    required this.downloadName,
    required this.downloadContentType,
  });

  final String id;
  final String kind;
  final String state;
  final String? message;
  final List<String> output;
  final Map<String, dynamic> result;
  final String? downloadName;
  final String? downloadContentType;

  bool get isTerminal => state == 'succeeded' || state == 'failed';
  String? get primaryDownloadPath {
    final downloads = result['downloads'];
    if (downloads is List && downloads.isNotEmpty) {
      final first = downloads.first;
      if (first is Map) {
        final path = first['path'];
        if (path is String && path.trim().isNotEmpty) {
          return path;
        }
      }
    }
    final outputDir = result['outputDir'];
    if (outputDir is String && outputDir.trim().isNotEmpty) {
      return outputDir;
    }
    return null;
  }

  factory DesktopTaskSnapshot.fromJson(Map<String, dynamic> json) {
    return DesktopTaskSnapshot(
      id: _readString(json['id']),
      kind: _readString(json['kind']),
      state: _readString(json['state']),
      message: _nullableString(json['message']),
      output: _readStringList(json['output']),
      result: _readMap(json['result']),
      downloadName: _nullableString(json['downloadName']),
      downloadContentType: _nullableString(json['downloadContentType']),
    );
  }
}

class BuildDispatchItem {
  const BuildDispatchItem({
    required this.workflowFile,
    required this.workflowName,
    required this.target,
    required this.ksuVariant,
    required this.ref,
    required this.inputs,
  });

  final String workflowFile;
  final String workflowName;
  final String target;
  final String? ksuVariant;
  final String ref;
  final Map<String, dynamic> inputs;

  factory BuildDispatchItem.fromJson(Map<String, dynamic> json) {
    return BuildDispatchItem(
      workflowFile: _readString(json['workflowFile']),
      workflowName: _readString(json['workflowName']),
      target: _readString(json['target']),
      ksuVariant: _nullableString(json['ksuVariant']),
      ref: _readString(json['ref']),
      inputs: _readMap(json['inputs']),
    );
  }
}

class BuildDispatchResult {
  const BuildDispatchResult({
    required this.ok,
    required this.repo,
    required this.dryRun,
    required this.total,
    required this.run,
    required this.runs,
    required this.dispatches,
    required this.warnings,
    required this.error,
  });

  final bool ok;
  final String? repo;
  final bool dryRun;
  final int total;
  final BuildRunSummary? run;
  final List<BuildRunSummary> runs;
  final List<BuildDispatchItem> dispatches;
  final List<String> warnings;
  final String? error;

  factory BuildDispatchResult.fromJson(Map<String, dynamic> json) {
    return BuildDispatchResult(
      ok: json['ok'] == true,
      repo: _nullableString(json['repo']),
      dryRun: json['dryRun'] == true,
      total: _readInt(json['total']),
      run: json['run'] is Map<String, dynamic>
          ? BuildRunSummary.fromJson(
              Map<String, dynamic>.from(json['run'] as Map),
            )
          : null,
      runs: _readMapList(
        json['runs'],
      ).map(BuildRunSummary.fromJson).toList(growable: false),
      dispatches: _readMapList(
        json['dispatches'],
      ).map(BuildDispatchItem.fromJson).toList(growable: false),
      warnings: _readStringList(json['warnings']),
      error: _nullableString(json['error']),
    );
  }
}

String _readString(dynamic value, {String fallback = ''}) {
  if (value is String) {
    return value;
  }
  return fallback;
}

String? _nullableString(dynamic value) {
  if (value is String && value.isNotEmpty) {
    return value;
  }
  return null;
}

int _readInt(dynamic value, {int fallback = 0}) {
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  if (value is String) {
    return int.tryParse(value) ?? fallback;
  }
  return fallback;
}

Map<String, dynamic> _readMap(dynamic value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return Map<String, dynamic>.from(value);
  }
  return const <String, dynamic>{};
}

List<Map<String, dynamic>> _readMapList(dynamic value) {
  if (value is! List) {
    return const <Map<String, dynamic>>[];
  }
  return value
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}

List<String> _readStringList(dynamic value) {
  if (value is! List) {
    return const <String>[];
  }
  return value
      .whereType<String>()
      .map((item) => item.trim())
      .where((item) => item.isNotEmpty)
      .toList(growable: false);
}
