import 'dart:async';
import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/abk_sidecar_api.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/build_models.dart';
import 'package:abk_desktop/src/core/state/dashboard_controller.dart';

const _missing = Object();

final settingsPageControllerProvider =
    StateNotifierProvider<SettingsPageController, SettingsPageState>((ref) {
      return SettingsPageController(api: ref.read(sidecarApiProvider));
    });

class SettingsPageState {
  const SettingsPageState({
    required this.isRefreshing,
    required this.session,
    required this.downloadDirDraft,
    required this.logoutBusy,
    required this.saveDownloadDirBusy,
    required this.exportDiagnosticsBusy,
    required this.tasks,
    required this.taskOrder,
    required this.lastError,
    required this.infoMessage,
  });

  factory SettingsPageState.initial() {
    return const SettingsPageState(
      isRefreshing: false,
      session: null,
      downloadDirDraft: '',
      logoutBusy: false,
      saveDownloadDirBusy: false,
      exportDiagnosticsBusy: false,
      tasks: <DesktopTaskSnapshot>[],
      taskOrder: <String>[],
      lastError: null,
      infoMessage: null,
    );
  }

  final bool isRefreshing;
  final GitHubSessionStatus? session;
  final String downloadDirDraft;
  final bool logoutBusy;
  final bool saveDownloadDirBusy;
  final bool exportDiagnosticsBusy;
  final List<DesktopTaskSnapshot> tasks;
  final List<String> taskOrder;
  final String? lastError;
  final String? infoMessage;

  DesktopTaskSnapshot? taskById(String taskId) {
    for (final task in tasks) {
      if (task.id == taskId) return task;
    }
    return null;
  }

  DesktopTaskSnapshot? get latestDiagnosticsTask {
    for (final id in taskOrder) {
      final task = taskById(id);
      if (task?.kind == 'diagnostics.export') {
        return task;
      }
    }
    return null;
  }

  SettingsPageState copyWith({
    bool? isRefreshing,
    Object? session = _missing,
    String? downloadDirDraft,
    bool? logoutBusy,
    bool? saveDownloadDirBusy,
    bool? exportDiagnosticsBusy,
    List<DesktopTaskSnapshot>? tasks,
    List<String>? taskOrder,
    Object? lastError = _missing,
    Object? infoMessage = _missing,
  }) {
    return SettingsPageState(
      isRefreshing: isRefreshing ?? this.isRefreshing,
      session: identical(session, _missing)
          ? this.session
          : session as GitHubSessionStatus?,
      downloadDirDraft: downloadDirDraft ?? this.downloadDirDraft,
      logoutBusy: logoutBusy ?? this.logoutBusy,
      saveDownloadDirBusy: saveDownloadDirBusy ?? this.saveDownloadDirBusy,
      exportDiagnosticsBusy: exportDiagnosticsBusy ?? this.exportDiagnosticsBusy,
      tasks: tasks ?? this.tasks,
      taskOrder: taskOrder ?? this.taskOrder,
      lastError: identical(lastError, _missing)
          ? this.lastError
          : lastError as String?,
      infoMessage: identical(infoMessage, _missing)
          ? this.infoMessage
          : infoMessage as String?,
    );
  }
}

class SettingsPageController extends StateNotifier<SettingsPageState> {
  SettingsPageController({required this.api}) : super(SettingsPageState.initial());

  final AbkSidecarApi api;

  AppStrings get _strings => AppStrings.fromLocale(PlatformDispatcher.instance.locale);

  Future<void> refresh() async {
    if (state.isRefreshing) return;
    state = state.copyWith(isRefreshing: true, lastError: null, infoMessage: null);
    try {
      final session = await api.getGitHubSession();
      if (!mounted) return;
      state = state.copyWith(
        isRefreshing: false,
        session: session,
        downloadDirDraft: state.downloadDirDraft.isEmpty
            ? (session.downloadDir ?? '')
            : state.downloadDirDraft,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(isRefreshing: false, lastError: error.message);
    }
  }

  void updateDownloadDirDraft(String value) {
    state = state.copyWith(downloadDirDraft: value);
  }

  Future<void> logout() async {
    if (state.logoutBusy) return;
    state = state.copyWith(logoutBusy: true, lastError: null, infoMessage: null);
    try {
      final session = await api.logoutGitHub();
      if (!mounted) return;
      state = state.copyWith(
        logoutBusy: false,
        session: session,
        infoMessage: _strings.settingsLoggedOut,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(logoutBusy: false, lastError: error.message);
    }
  }

  Future<void> saveDownloadDirectory() async {
    final path = state.downloadDirDraft.trim();
    if (path.isEmpty || state.saveDownloadDirBusy) return;
    state = state.copyWith(
      saveDownloadDirBusy: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final saved = await api.setDownloadDirectory(path);
      if (!mounted) return;
      state = state.copyWith(
        saveDownloadDirBusy: false,
        downloadDirDraft: saved ?? path,
        session: state.session == null
            ? null
            : GitHubSessionStatus(
                ok: state.session!.ok,
                loggedIn: state.session!.loggedIn,
                repo: state.session!.repo,
                needsFork: state.session!.needsFork,
                needsSync: state.session!.needsSync,
                behindBy: state.session!.behindBy,
                aheadBy: state.session!.aheadBy,
                userLogin: state.session!.userLogin,
                forkFullName: state.session!.forkFullName,
                signingKeyAvailable: state.session!.signingKeyAvailable,
                signingKeySource: state.session!.signingKeySource,
                downloadDir: saved ?? path,
              ),
        infoMessage: _strings.settingsDirectorySaved,
      );
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(
        saveDownloadDirBusy: false,
        lastError: error.message,
      );
    }
  }

  Future<void> exportDiagnostics() async {
    if (state.exportDiagnosticsBusy) return;
    state = state.copyWith(
      exportDiagnosticsBusy: true,
      lastError: null,
      infoMessage: null,
    );
    try {
      final accepted = await api.exportDiagnostics();
      if (!mounted) return;
      _upsertTask(accepted);
      state = state.copyWith(
        exportDiagnosticsBusy: false,
        infoMessage: _strings.deviceTaskQueued,
      );
      unawaited(_trackTask(accepted.id));
    } on SidecarException catch (error) {
      if (!mounted) return;
      final message = error.statusCode == 404
          ? _strings.settingsDiagnosticsUnsupported
          : error.message;
      state = state.copyWith(
        exportDiagnosticsBusy: false,
        lastError: message,
      );
    }
  }

  Future<void> _trackTask(String taskId) async {
    try {
      while (true) {
        await Future<void>.delayed(const Duration(seconds: 1));
        if (!mounted) return;
        final task = await api.getTask(taskId);
        if (!mounted) return;
        _upsertTask(task);
        if (task.isTerminal) {
          return;
        }
      }
    } on SidecarException catch (error) {
      if (!mounted) return;
      state = state.copyWith(lastError: error.message);
    }
  }

  void _upsertTask(DesktopTaskSnapshot task) {
    final tasks = <DesktopTaskSnapshot>[
      task,
      ...state.tasks.where((existing) => existing.id != task.id),
    ];
    final order = <String>[
      task.id,
      ...state.taskOrder.where((id) => id != task.id),
    ];
    state = state.copyWith(tasks: tasks, taskOrder: order);
  }
}
