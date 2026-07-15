import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/abk_sidecar_api.dart';
import '../../core/localization/app_strings.dart';
import '../../core/models/build_models.dart';
import '../../core/models/device_models.dart';
import '../../core/models/sidecar_models.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/panel_card.dart';
import '../../widgets/status_pill.dart';
import 'device_page_controller.dart';
import 'runtime_module_catalog.dart';

class DevicePage extends ConsumerStatefulWidget {
  const DevicePage({super.key});

  @override
  ConsumerState<DevicePage> createState() => _DevicePageState();
}

class _DevicePageState extends ConsumerState<DevicePage> {
  bool _requestedInitialLoad = false;

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(dashboardControllerProvider);
    final deviceState = ref.watch(devicePageControllerProvider);
    final controller = ref.read(devicePageControllerProvider.notifier);
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    final abkReady =
        dashboard.connection?.connected == true &&
        dashboard.connection?.mode == DeviceConnectionMode.abk;

    if (abkReady && !_requestedInitialLoad) {
      _requestedInitialLoad = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        controller.refreshAll();
      });
    }
    if (!abkReady) {
      _requestedInitialLoad = false;
    }

    return DefaultTabController(
      length: 3,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 24, 28, 0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        strings.deviceTitle,
                        style: Theme.of(context).textTheme.headlineLarge,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        strings.deviceIntro,
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                FilledButton.tonalIcon(
                  onPressed: abkReady && !deviceState.isRefreshing
                      ? controller.refreshAll
                      : null,
                  icon: const Icon(Icons.refresh_rounded),
                  label: Text(strings.deviceRefreshAll),
                ),
              ],
            ),
          ),
          if (deviceState.lastError != null) ...<Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
              child: _MessageBanner(
                title: strings.errorCardTitle,
                message: deviceState.lastError!,
                color: scheme.errorContainer,
                foreground: scheme.onErrorContainer,
              ),
            ),
          ],
          if (deviceState.infoMessage != null) ...<Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(28, 16, 28, 0),
              child: _MessageBanner(
                title: strings.deviceTaskTitle,
                message: deviceState.infoMessage!,
                color: scheme.primaryContainer,
                foreground: scheme.onPrimaryContainer,
              ),
            ),
          ],
          const SizedBox(height: 16),
          if (!abkReady)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(28, 0, 28, 28),
                child: _BlockedDeviceState(
                  dashboard: dashboard,
                  onOpenDetection: () => context.go('/detect'),
                ),
              ),
            )
          else
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(28, 0, 28, 28),
                child: Column(
                  children: <Widget>[
                    Align(
                      alignment: Alignment.centerLeft,
                      child: TabBar(
                        isScrollable: true,
                        tabAlignment: TabAlignment.start,
                        tabs: <Tab>[
                          Tab(text: strings.deviceTabRoot),
                          Tab(text: strings.deviceTabModules),
                          Tab(text: strings.deviceTabKernel),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Expanded(
                      child: TabBarView(
                        children: <Widget>[
                          _RootGrantsTab(state: deviceState, controller: controller),
                          _ModulesTab(state: deviceState, controller: controller),
                          _KernelTab(state: deviceState, controller: controller),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _BlockedDeviceState extends StatelessWidget {
  const _BlockedDeviceState({
    required this.dashboard,
    required this.onOpenDetection,
  });

  final DashboardState dashboard;
  final VoidCallback onOpenDetection;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return PanelCard(
      title: strings.deviceBlockedTitle,
      subtitle: strings.deviceBlockedSubtitle,
      icon: Icons.phonelink_erase_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: <Widget>[
              StatusPill(
                label: strings.connectionStatusLabel(dashboard.flow),
                color: scheme.primary,
                icon: Icons.route_rounded,
              ),
              StatusPill(
                label: strings.connectionModeLabel(
                  dashboard.connection?.mode ?? DeviceConnectionMode.disconnected,
                ),
                color: scheme.secondary,
                icon: Icons.usb_rounded,
              ),
            ],
          ),
          const SizedBox(height: 16),
          FilledButton.tonalIcon(
            onPressed: onOpenDetection,
            icon: const Icon(Icons.open_in_new_rounded),
            label: Text(strings.deviceOpenDetection),
          ),
        ],
      ),
    );
  }
}

class _MessageBanner extends StatelessWidget {
  const _MessageBanner({
    required this.title,
    required this.message,
    required this.color,
    required this.foreground,
  });

  final String title;
  final String message;
  final Color color;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return PanelCard(
      title: title,
      subtitle: message,
      icon: Icons.info_rounded,
      backgroundColor: color,
      foregroundColor: foreground,
      subtitleColor: foreground.withValues(alpha: 0.82),
      borderColor: color,
      iconBackgroundColor: foreground.withValues(alpha: 0.12),
      iconColor: foreground,
      child: const SizedBox.shrink(),
    );
  }
}

class _RootGrantsTab extends ConsumerStatefulWidget {
  const _RootGrantsTab({required this.state, required this.controller});

  final DevicePageState state;
  final DevicePageController controller;

  @override
  ConsumerState<_RootGrantsTab> createState() => _RootGrantsTabState();
}

class _RootGrantsTabState extends ConsumerState<_RootGrantsTab> {
  String _query = '';
  bool _showSystemApps = false;
  String? _selectedPackage;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final api = ref.read(sidecarApiProvider);
    final rootGrants = widget.state.rootGrants;
    final apps = rootGrants?.apps.where((app) {
      if (!_showSystemApps && app.isSystemApp) {
        return false;
      }
      final needle = _query.trim().toLowerCase();
      if (needle.isEmpty) return true;
      return app.label.toLowerCase().contains(needle) ||
          app.packageName.toLowerCase().contains(needle) ||
          app.uid.toString().contains(needle);
    }).toList(growable: false) ??
        const <RootGrantApp>[];
    final selectedApp = _selectedPackage == null
        ? null
        : apps.where((app) => app.packageName == _selectedPackage).firstOrNull;
    if (selectedApp != null &&
        !widget.state.packageInfoByPackage.containsKey(selectedApp.packageName) &&
        widget.state.packageInfoLoadingPackage != selectedApp.packageName) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.controller.loadPackageInfo(selectedApp.packageName);
      });
    }
    final packageInfo = selectedApp == null
        ? null
        : widget.state.packageInfoByPackage[selectedApp.packageName];
    final wide = MediaQuery.sizeOf(context).width >= 1200;

    final listPanel = PanelCard(
      title: strings.deviceRootListTitle,
      subtitle: strings.deviceRootListSubtitle,
      icon: Icons.admin_panel_settings_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          TextField(
            decoration: InputDecoration(
              labelText: strings.deviceRootSearch,
              prefixIcon: const Icon(Icons.search_rounded),
            ),
            onChanged: (value) => setState(() => _query = value),
          ),
          const SizedBox(height: 12),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.deviceRootShowSystem),
            value: _showSystemApps,
            onChanged: (value) => setState(() => _showSystemApps = value),
          ),
          const SizedBox(height: 8),
          if (widget.state.rootGrantLoading && apps.isEmpty)
            const Center(child: CircularProgressIndicator())
          else if (rootGrants == null || apps.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(strings.deviceRootNoApps),
            )
          else
            Column(
              children: apps
                  .map(
                    (app) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _RootGrantListTile(
                        api: api,
                        app: app,
                        selected: app.packageName == _selectedPackage,
                        onTap: () => setState(() {
                          _selectedPackage = app.packageName;
                        }),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
        ],
      ),
    );

    final detailPanel = PanelCard(
      title: strings.deviceRootDetailTitle,
      subtitle: selectedApp?.label ?? strings.deviceRootDetailEmpty,
      icon: Icons.account_circle_rounded,
      child: selectedApp == null
          ? Text(strings.deviceRootDetailEmpty)
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: <Widget>[
                    StatusPill(
                      label: selectedApp.packageName,
                      color: Theme.of(context).colorScheme.primary,
                      icon: Icons.apps_rounded,
                    ),
                    StatusPill(
                      label: 'UID ${selectedApp.uid}',
                      color: Theme.of(context).colorScheme.secondary,
                      icon: Icons.tag_rounded,
                    ),
                    if (selectedApp.isSystemApp)
                      StatusPill(
                        label: strings.deviceRootShowSystem,
                        color: Theme.of(context).colorScheme.tertiary,
                        icon: Icons.security_rounded,
                      ),
                  ],
                ),
                if (packageInfo != null) ...<Widget>[
                  const SizedBox(height: 14),
                  Text(
                    '${packageInfo.appLabel} · ${packageInfo.versionName} (${packageInfo.versionCode})',
                  ),
                ],
                const SizedBox(height: 14),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(strings.deviceRootAllow),
                  value: selectedApp.profile.allowSu,
                  onChanged: widget.state.rootGrantSavingPackage == selectedApp.packageName
                      ? null
                      : (value) => widget.controller.setRootGrantAllowed(
                          selectedApp.packageName,
                          value,
                        ),
                ),
                if (widget.state.packageInfoLoadingPackage == selectedApp.packageName)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: CircularProgressIndicator(),
                  ),
              ],
            ),
    );

    if (!wide) {
      return SingleChildScrollView(
        child: Column(
          children: <Widget>[
            listPanel,
            const SizedBox(height: 16),
            detailPanel,
          ],
        ),
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(flex: 6, child: SingleChildScrollView(child: listPanel)),
        const SizedBox(width: 16),
        Expanded(flex: 4, child: SingleChildScrollView(child: detailPanel)),
      ],
    );
  }
}

class _RootGrantListTile extends StatelessWidget {
  const _RootGrantListTile({
    required this.api,
    required this.app,
    required this.selected,
    required this.onTap,
  });

  final AbkSidecarApi api;
  final RootGrantApp app;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Container(
          decoration: BoxDecoration(
            color: selected
                ? scheme.primaryContainer.withValues(alpha: 0.7)
                : scheme.surfaceContainerHighest.withValues(alpha: 0.28),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: selected
                  ? scheme.primary
                  : scheme.outlineVariant.withValues(alpha: 0.34),
            ),
          ),
          padding: const EdgeInsets.all(12),
          child: Row(
            children: <Widget>[
              _RootGrantIcon(api: api, packageName: app.packageName),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      app.label,
                      style: Theme.of(context).textTheme.titleSmall,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${app.packageName} · UID ${app.uid}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              StatusPill(
                label: app.profile.allowSu
                    ? context.strings.deviceRootAllow
                    : context.strings.deviceRootDenied,
                color: app.profile.allowSu ? scheme.primary : scheme.outline,
                icon: app.profile.allowSu
                    ? Icons.check_circle_rounded
                    : Icons.circle_outlined,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RootGrantIcon extends StatefulWidget {
  const _RootGrantIcon({required this.api, required this.packageName});

  final AbkSidecarApi api;
  final String packageName;

  @override
  State<_RootGrantIcon> createState() => _RootGrantIconState();
}

class _RootGrantIconState extends State<_RootGrantIcon> {
  Future<Uint8List?>? _future;

  @override
  void initState() {
    super.initState();
    _future = widget.api.getRootGrantIcon(widget.packageName);
  }

  @override
  void didUpdateWidget(covariant _RootGrantIcon oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.packageName != widget.packageName) {
      _future = widget.api.getRootGrantIcon(widget.packageName);
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Uint8List?>(
      future: _future,
      builder: (context, snapshot) {
        final bytes = snapshot.data;
        return ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: 40,
            height: 40,
            color: Theme.of(
              context,
            ).colorScheme.primaryContainer.withValues(alpha: 0.8),
            child: bytes == null
                ? const Icon(Icons.apps_rounded)
                : Image.memory(bytes, fit: BoxFit.cover),
          ),
        );
      },
    );
  }
}

class _ModulesTab extends ConsumerStatefulWidget {
  const _ModulesTab({required this.state, required this.controller});

  final DevicePageState state;
  final DevicePageController controller;

  @override
  ConsumerState<_ModulesTab> createState() => _ModulesTabState();
}

class _ModulesTabState extends ConsumerState<_ModulesTab> {
  String _installedQuery = '';
  String _repositoryQuery = '';

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final api = ref.read(sidecarApiProvider);
    return DefaultTabController(
      length: 3,
      child: Column(
        children: <Widget>[
          Align(
            alignment: Alignment.centerLeft,
            child: TabBar(
              isScrollable: true,
              tabAlignment: TabAlignment.start,
              tabs: <Tab>[
                Tab(text: strings.deviceModuleTabInstalled),
                Tab(text: strings.deviceModuleTabRepository),
                Tab(text: strings.deviceModuleTabLocalInstall),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Expanded(
            child: TabBarView(
              children: <Widget>[
                SingleChildScrollView(
                  child: Column(
                    children: <Widget>[
                      TextField(
                        decoration: InputDecoration(
                          labelText: strings.deviceModuleSearch,
                          prefixIcon: const Icon(Icons.search_rounded),
                        ),
                        onChanged: (value) => setState(() => _installedQuery = value),
                      ),
                      const SizedBox(height: 12),
                      if (widget.state.installedModules.isEmpty)
                        PanelCard(
                          title: strings.deviceModuleTabInstalled,
                          subtitle: strings.deviceModuleNoInstalled,
                          icon: Icons.extension_rounded,
                          child: const SizedBox.shrink(),
                        )
                      else
                        ...widget.state.installedModules
                            .where((module) {
                              final needle = _installedQuery.trim().toLowerCase();
                              if (needle.isEmpty) return true;
                              return [
                                module.id,
                                module.name,
                                module.author,
                                module.description,
                              ].join(' ').toLowerCase().contains(needle);
                            })
                            .map(
                              (module) => Padding(
                                padding: const EdgeInsets.only(bottom: 12),
                                child: _InstalledModuleCard(
                                  module: module,
                                  state: widget.state,
                                  api: api,
                                  onEnabledChange: (enabled) => widget.controller
                                      .setRuntimeModuleEnabled(module.id, enabled),
                                  onPendingUninstallChange: (pending) => widget
                                      .controller
                                      .setRuntimeModulePendingUninstall(
                                        module.id,
                                        pending,
                                      ),
                                  onRunAction: module.actionSupported
                                      ? () => widget.controller
                                            .runRuntimeModuleAction(module.id)
                                      : null,
                                ),
                              ),
                            ),
                      const SizedBox(height: 16),
                      _DeviceTasksCard(state: widget.state),
                    ],
                  ),
                ),
                SingleChildScrollView(
                  child: Column(
                    children: <Widget>[
                      Row(
                        children: <Widget>[
                          Expanded(
                            child: TextField(
                              decoration: InputDecoration(
                                labelText: strings.deviceModuleRepoUrl,
                              ),
                              onChanged: widget.controller.updateRepositoryUrlDraft,
                              controller: TextEditingController(
                                text: widget.state.repositoryUrlDraft,
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          FilledButton.tonal(
                            onPressed: widget.state.repositoryLoading
                                ? null
                                : widget.controller.addRuntimeModuleRepository,
                            child: Text(strings.deviceModuleAddRepo),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        decoration: InputDecoration(
                          labelText: strings.deviceModuleSearch,
                          prefixIcon: const Icon(Icons.search_rounded),
                        ),
                        onChanged: (value) => setState(() => _repositoryQuery = value),
                      ),
                      const SizedBox(height: 12),
                      ...widget.state.runtimeModuleRepositories.map(
                        (repository) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _RepositoryCard(
                            repository: repository,
                            state: widget.state,
                            query: _repositoryQuery,
                            onRefresh: () => widget.controller.refreshRuntimeModuleRepository(
                              repository.id,
                            ),
                            onDelete: repository.id == officialRuntimeModuleRepositoryId
                                ? null
                                : () => widget.controller.removeRuntimeModuleRepository(
                                    repository.id,
                                  ),
                            onOpenModule: (module) {
                              final url = module.module.website.isNotEmpty
                                  ? module.module.website
                                  : (module.module.support.isNotEmpty
                                        ? module.module.support
                                        : module.module.zipUrl);
                              if (url.isNotEmpty) {
                                _openUrl(url);
                              }
                            },
                            onInstallModule: (module) =>
                                widget.controller.installRepositoryModule(module),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                _LocalInstallTab(controller: widget.controller, state: widget.state),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _InstalledModuleCard extends StatelessWidget {
  const _InstalledModuleCard({
    required this.module,
    required this.state,
    required this.api,
    required this.onEnabledChange,
    required this.onPendingUninstallChange,
    required this.onRunAction,
  });

  final AbkRuntimeModule module;
  final DevicePageState state;
  final AbkSidecarApi api;
  final ValueChanged<bool> onEnabledChange;
  final ValueChanged<bool> onPendingUninstallChange;
  final VoidCallback? onRunAction;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return PanelCard(
      title: module.displayName,
      subtitle: module.description.ifEmpty(module.id),
      icon: Icons.extension_rounded,
      actions: <Widget>[
        if (module.hasWebUi)
          IconButton(
            onPressed: () => _openUrl(api.runtimeModuleWebUiUri(module.id).toString()),
            icon: const Icon(Icons.open_in_browser_rounded),
            tooltip: strings.deviceModuleWebUi,
          ),
      ],
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: <Widget>[
              if (module.version.isNotEmpty)
                StatusPill(
                  label: 'v${module.version}',
                  color: scheme.primary,
                  icon: Icons.tag_rounded,
                ),
              if (module.author.isNotEmpty)
                StatusPill(
                  label: module.author,
                  color: scheme.secondary,
                  icon: Icons.person_rounded,
                ),
              if (module.readonly)
                StatusPill(
                  label: 'readonly',
                  color: scheme.outline,
                  icon: Icons.lock_outline_rounded,
                ),
              if (module.hasActionScript || module.actionSupported)
                StatusPill(
                  label: strings.deviceModuleAction,
                  color: scheme.tertiary,
                  icon: Icons.play_arrow_rounded,
                ),
            ],
          ),
          const SizedBox(height: 12),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(strings.deviceModuleEnable),
            value: module.enabled,
            onChanged: state.moduleBusyIds.contains(module.id)
                ? null
                : onEnabledChange,
          ),
          if (module.remove)
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(strings.deviceModulePendingUninstall),
              value: true,
              onChanged: state.modulePendingBusyIds.contains(module.id)
                  ? null
                  : (value) => onPendingUninstallChange(value),
            ),
          Row(
            children: <Widget>[
              if (onRunAction != null)
                FilledButton.tonalIcon(
                  onPressed: state.moduleActionBusyIds.contains(module.id)
                      ? null
                      : onRunAction,
                  icon: state.moduleActionBusyIds.contains(module.id)
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : const Icon(Icons.play_arrow_rounded),
                  label: Text(strings.deviceModuleAction),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _RepositoryCard extends StatelessWidget {
  const _RepositoryCard({
    required this.repository,
    required this.state,
    required this.query,
    required this.onRefresh,
    required this.onDelete,
    required this.onOpenModule,
    required this.onInstallModule,
  });

  final RuntimeModuleRepository repository;
  final DevicePageState state;
  final String query;
  final VoidCallback onRefresh;
  final VoidCallback? onDelete;
  final ValueChanged<MergedRuntimeCatalogModule> onOpenModule;
  final ValueChanged<MergedRuntimeCatalogModule> onInstallModule;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final modules = mergeRuntimeCatalogModules(
      <RuntimeModuleRepository>[repository],
    ).where((module) => module.matchesQuery(query)).toList(growable: false);
    return PanelCard(
      title: repository.name,
      subtitle: repository.url,
      icon: Icons.library_books_rounded,
      actions: <Widget>[
        IconButton(
          onPressed: state.refreshingRepositoryIds.contains(repository.id)
              ? null
              : onRefresh,
          icon: state.refreshingRepositoryIds.contains(repository.id)
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2.2),
                )
              : const Icon(Icons.refresh_rounded),
        ),
        if (onDelete != null)
          IconButton(
            onPressed: onDelete,
            icon: const Icon(Icons.delete_outline_rounded),
          ),
      ],
      child: !repository.isReady
          ? Text(repository.error ?? strings.deviceModuleNoCatalogModules)
          : modules.isEmpty
          ? Text(strings.deviceModuleNoCatalogResults)
          : Column(
              children: modules
                  .map(
                    (module) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: Container(
                        decoration: BoxDecoration(
                          color: Theme.of(
                            context,
                          ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.26),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          children: <Widget>[
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: <Widget>[
                                  Text(
                                    module.module.name,
                                    style: Theme.of(context).textTheme.titleSmall,
                                  ),
                                  if (module.module.metaLine().isNotEmpty) ...<Widget>[
                                    const SizedBox(height: 4),
                                    Text(
                                      module.module.metaLine(),
                                      style: Theme.of(context).textTheme.bodySmall,
                                    ),
                                  ],
                                  if (module.module.description.isNotEmpty) ...<Widget>[
                                    const SizedBox(height: 4),
                                    Text(
                                      module.module.description,
                                      style: Theme.of(context).textTheme.bodySmall,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ],
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              onPressed: () => onOpenModule(module),
                              icon: const Icon(Icons.open_in_browser_rounded),
                              tooltip: strings.deviceModuleOpenRepo,
                            ),
                            FilledButton.tonal(
                              onPressed: state.installingCatalogModuleIds.contains(
                                module.module.id.ifEmpty(module.module.zipUrl),
                              )
                                  ? null
                                  : () => onInstallModule(module),
                              child: state.installingCatalogModuleIds.contains(
                                      module.module.id.ifEmpty(module.module.zipUrl),
                                    )
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2.2,
                                      ),
                                    )
                                  : Text(strings.deviceModuleInstall),
                            ),
                          ],
                        ),
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
    );
  }
}

class _LocalInstallTab extends StatefulWidget {
  const _LocalInstallTab({required this.controller, required this.state});

  final DevicePageController controller;
  final DevicePageState state;

  @override
  State<_LocalInstallTab> createState() => _LocalInstallTabState();
}

class _LocalInstallTabState extends State<_LocalInstallTab> {
  Future<void> _pickZip() async {
    final path = await _pickZipPath();
    if (path == null || path.isEmpty) return;
    widget.controller.updateLocalModulePath(path);
  }

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    return SingleChildScrollView(
      child: PanelCard(
        title: strings.deviceModuleTabLocalInstall,
        subtitle: strings.deviceModuleNoLocalZip,
        icon: Icons.upload_file_rounded,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            TextField(
              readOnly: true,
              controller: TextEditingController(
                text: widget.state.localModulePath ?? '',
              ),
              decoration: InputDecoration(
                labelText: strings.deviceModuleChooseZip,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: <Widget>[
                FilledButton.tonalIcon(
                  onPressed: _pickZip,
                  icon: const Icon(Icons.folder_open_rounded),
                  label: Text(strings.deviceModuleChooseZip),
                ),
                const SizedBox(width: 12),
                FilledButton(
                  onPressed: widget.state.localModulePath == null ||
                          widget.state.localInstallBusy
                      ? null
                      : widget.controller.installLocalModule,
                  child: widget.state.localInstallBusy
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2.2),
                        )
                      : Text(strings.deviceModuleInstall),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _KernelTab extends StatefulWidget {
  const _KernelTab({required this.state, required this.controller});

  final DevicePageState state;
  final DevicePageController controller;

  @override
  State<_KernelTab> createState() => _KernelTabState();
}

class _KernelTabState extends State<_KernelTab> {
  late final TextEditingController _susfsDraftController;

  @override
  void initState() {
    super.initState();
    _susfsDraftController = TextEditingController(
      text: widget.state.susfsConfigDraft,
    );
  }

  @override
  void didUpdateWidget(covariant _KernelTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_susfsDraftController.text != widget.state.susfsConfigDraft) {
      _susfsDraftController.value = _susfsDraftController.value.copyWith(
        text: widget.state.susfsConfigDraft,
        selection: TextSelection.collapsed(
          offset: widget.state.susfsConfigDraft.length,
        ),
        composing: TextRange.empty,
      );
    }
  }

  @override
  void dispose() {
    _susfsDraftController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final runtime = widget.state.runtime?.runtimeStatus;
    final susfs = widget.state.susfs;
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          PanelCard(
            title: strings.deviceKernelSummaryTitle,
            subtitle: strings.deviceKernelSummarySubtitle,
            icon: Icons.memory_rounded,
            child: runtime == null
                ? Text(strings.deviceKernelNoRuntime)
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: <Widget>[
                          StatusPill(
                            label: runtime.abkVersion.isEmpty
                                ? strings.unknownValue
                                : runtime.abkVersion,
                            color: Theme.of(context).colorScheme.primary,
                            icon: Icons.info_rounded,
                          ),
                          StatusPill(
                            label: runtime.manager?.displayName.isNotEmpty == true
                                ? runtime.manager!.displayName
                                : strings.unknownValue,
                            color: Theme.of(context).colorScheme.secondary,
                            icon: Icons.extension_rounded,
                          ),
                          StatusPill(
                            label: '${runtime.modules.length} modules',
                            color: Theme.of(context).colorScheme.tertiary,
                            icon: Icons.layers_rounded,
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      if (runtime.abkCommit.isNotEmpty) Text(runtime.abkCommit),
                      if (runtime.build != null) ...<Widget>[
                        const SizedBox(height: 8),
                        Text(
                          '${runtime.build!.androidVersion} · ${runtime.build!.kernelVersion} · ${runtime.build!.subLevel} · ${runtime.build!.osPatchLevel}',
                        ),
                      ],
                    ],
                  ),
          ),
          const SizedBox(height: 16),
          PanelCard(
            title: strings.deviceSusfsTitle,
            subtitle: strings.deviceSusfsSubtitle,
            icon: Icons.tune_rounded,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                if (susfs?.status != null) ...<Widget>[
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: <Widget>[
                      StatusPill(
                        label: susfs!.status!.available ? 'available' : 'unavailable',
                        color: Theme.of(context).colorScheme.primary,
                        icon: Icons.check_circle_rounded,
                      ),
                      StatusPill(
                        label: susfs.status!.kernelVersion.ifEmpty(strings.unknownValue),
                        color: Theme.of(context).colorScheme.secondary,
                        icon: Icons.memory_rounded,
                      ),
                    ],
                  ),
                  if (susfs.status!.diagnostics.isNotEmpty) ...<Widget>[
                    const SizedBox(height: 12),
                    ...susfs.status!.diagnostics.map((line) => Text(line)),
                  ],
                ],
                if (widget.state.susfsError != null) ...<Widget>[
                  const SizedBox(height: 12),
                  Text(
                    widget.state.susfsError!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error),
                  ),
                ],
                const SizedBox(height: 12),
                TextField(
                  controller: _susfsDraftController,
                  onChanged: widget.controller.updateSusfsDraft,
                  minLines: 10,
                  maxLines: 20,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                  ),
                  style: const TextStyle(fontFamily: 'monospace'),
                ),
                const SizedBox(height: 12),
                Row(
                  children: <Widget>[
                    FilledButton.tonal(
                      onPressed: widget.state.susfsSaving
                          ? null
                          : widget.controller.resetSusfsDraft,
                      child: Text(strings.deviceSusfsReset),
                    ),
                    const SizedBox(width: 12),
                    FilledButton(
                      onPressed: widget.state.susfsSaving
                          ? null
                          : widget.controller.applySusfsDraft,
                      child: widget.state.susfsSaving
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2.2),
                            )
                          : Text(strings.deviceSusfsApply),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                _DeviceTasksCard(
                  state: widget.state,
                  kinds: const <String>{'susfs.apply'},
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _DeviceTasksCard extends StatelessWidget {
  const _DeviceTasksCard({
    required this.state,
    this.kinds,
  });

  final DevicePageState state;
  final Set<String>? kinds;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final tasks = state.taskOrder
        .map((id) => state.taskById(id))
        .whereType<DesktopTaskSnapshot>()
        .where((task) => kinds == null || kinds!.contains(task.kind))
        .toList(growable: false);
    return PanelCard(
      title: strings.deviceTaskTitle,
      subtitle: strings.deviceTaskSubtitle,
      icon: Icons.terminal_rounded,
      child: tasks.isEmpty
          ? Text(strings.deviceTaskNoTasks)
          : Column(
              children: tasks
                  .map(
                    (task) => Padding(
                      padding: const EdgeInsets.only(bottom: 10),
                      child: _DeviceTaskTile(task: task),
                    ),
                  )
                  .toList(growable: false),
            ),
    );
  }
}

class _DeviceTaskTile extends StatelessWidget {
  const _DeviceTaskTile({required this.task});

  final DesktopTaskSnapshot task;

  @override
  Widget build(BuildContext context) {
    final strings = context.strings;
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.surfaceContainerHighest.withValues(alpha: 0.28),
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => showDialog<void>(
          context: context,
          builder: (context) => _DeviceTaskLogDialog(task: task),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      strings.buildTaskLabel(task.kind),
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 4),
                    Text(task.message ?? task.id),
                  ],
                ),
              ),
              StatusPill(
                label: strings.buildTaskStateLabel(task.state),
                color: switch (task.state) {
                  'succeeded' => scheme.primary,
                  'failed' => scheme.error,
                  'running' => scheme.secondary,
                  _ => scheme.outline,
                },
                icon: task.isTerminal
                    ? Icons.check_circle_rounded
                    : Icons.sync_rounded,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DeviceTaskLogDialog extends StatelessWidget {
  const _DeviceTaskLogDialog({required this.task});

  final DesktopTaskSnapshot task;

  @override
  Widget build(BuildContext context) {
    final output = <String>[
      if (task.message != null) '## ${task.message}',
      ...task.output,
      if (task.result.isNotEmpty) ...<String>[
        '',
        const JsonEncoder.withIndent('  ').convert(task.result),
      ],
    ];
    return AlertDialog(
      title: Text(context.strings.buildTaskDetailsTitle),
      content: SizedBox(
        width: 760,
        child: SingleChildScrollView(
          child: SelectableText(
            output.join('\n'),
            style: const TextStyle(fontFamily: 'monospace'),
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(context.strings.commonCancel),
        ),
      ],
    );
  }
}

Future<void> _openUrl(String url) async {
  await Process.start('xdg-open', <String>[url]);
}

Future<String?> _pickZipPath() async {
  final zenity = await Process.run('sh', <String>[
    '-lc',
    'command -v zenity >/dev/null 2>&1 && zenity --file-selection --file-filter="*.zip"',
  ]);
  final zenityPath = (zenity.stdout as String).trim();
  if (zenity.exitCode == 0 && zenityPath.isNotEmpty) {
    return zenityPath;
  }

  final kdialog = await Process.run('sh', <String>[
    '-lc',
    'command -v kdialog >/dev/null 2>&1 && kdialog --getopenfilename . "*.zip"',
  ]);
  final kdialogPath = (kdialog.stdout as String).trim();
  if (kdialog.exitCode == 0 && kdialogPath.isNotEmpty) {
    return kdialogPath;
  }
  return null;
}

extension<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}

extension on String {
  String ifEmpty(String fallback) => isEmpty ? fallback : this;
}
