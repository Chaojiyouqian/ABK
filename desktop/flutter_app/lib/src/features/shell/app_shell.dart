import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/localization/app_strings.dart';
import '../../core/state/dashboard_controller.dart';
import '../../widgets/status_pill.dart';

final sidebarExpandedProvider = StateProvider<bool>((ref) => false);

class AppShell extends ConsumerWidget {
  const AppShell({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(dashboardControllerProvider);
    final expanded = ref.watch(sidebarExpandedProvider);
    final scheme = Theme.of(context).colorScheme;
    final strings = context.strings;
    final sidecarHost = state.health?.sidecar.host ?? '127.0.0.1';
    final sidecarPort = state.health?.sidecar.port ?? 38765;
    final location = GoRouterState.of(context).uri.path;
    final destinations = <_ShellDestination>[
      _ShellDestination(
        route: '/home',
        label: strings.navHome,
        icon: Icons.space_dashboard_rounded,
      ),
      _ShellDestination(
        route: '/detect',
        label: strings.navDetection,
        icon: Icons.usb_rounded,
      ),
      _ShellDestination(
        route: '/device',
        label: strings.navDevice,
        icon: Icons.smartphone_rounded,
      ),
      _ShellDestination(
        route: '/build',
        label: strings.buildTitle,
        icon: Icons.construction_rounded,
      ),
      _ShellDestination(
        route: '/settings',
        label: strings.navSettings,
        icon: Icons.settings_rounded,
      ),
    ];
    final selectedIndex = destinations.indexWhere(
      (destination) => location.startsWith(destination.route),
    );

    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: <Color>[
              scheme.surface,
              scheme.primaryContainer.withValues(alpha: 0.34),
              scheme.tertiaryContainer.withValues(alpha: 0.26),
            ],
          ),
        ),
        child: SafeArea(
          minimum: const EdgeInsets.all(18),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 940;
              if (compact) {
                return Column(
                  children: <Widget>[
                    _CompactHeader(state: state),
                    const SizedBox(height: 16),
                    Expanded(child: _ContentSurface(child: child)),
                    const SizedBox(height: 12),
                    NavigationBar(
                      selectedIndex: selectedIndex < 0 ? 0 : selectedIndex,
                      onDestinationSelected: (index) {
                        context.go(destinations[index].route);
                      },
                      destinations: destinations
                          .map(
                            (destination) => NavigationDestination(
                              icon: Icon(destination.icon),
                              label: destination.label,
                            ),
                          )
                          .toList(growable: false),
                    ),
                  ],
                );
              }

              return Row(
                children: <Widget>[
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    curve: Curves.easeOutCubic,
                    width: expanded ? 176 : 104,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: scheme.surface.withValues(alpha: 0.88),
                        borderRadius: BorderRadius.circular(34),
                        border: Border.all(
                          color: scheme.outlineVariant.withValues(alpha: 0.42),
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(14, 16, 14, 16),
                        child: Column(
                          children: <Widget>[
                            _SidebarHeader(expanded: expanded),
                            const SizedBox(height: 18),
                            Expanded(
                              child: Column(
                                children: <Widget>[
                                  for (
                                    var index = 0;
                                    index < destinations.length;
                                    index++
                                  )
                                    Padding(
                                      padding: const EdgeInsets.only(bottom: 8),
                                      child: _SidebarDestination(
                                        destination: destinations[index],
                                        selected:
                                            (selectedIndex < 0
                                                ? 0
                                                : selectedIndex) ==
                                            index,
                                        expanded: expanded,
                                        onTap: () => context.go(
                                          destinations[index].route,
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            ),
                            _SidebarStatus(
                              state: state,
                              expanded: expanded,
                              sidecarHost: sidecarHost,
                              sidecarPort: sidecarPort,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 18),
                  Expanded(child: _ContentSurface(child: child)),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

class _SidebarHeader extends ConsumerWidget {
  const _SidebarHeader({required this.expanded});

  final bool expanded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final strings = context.strings;
    final buttonConstraints = const BoxConstraints.tightFor(
      width: 32,
      height: 32,
    );
    return Row(
      children: <Widget>[
        Container(
          width: 40,
          height: 40,
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: Theme.of(
              context,
            ).colorScheme.primaryContainer.withValues(alpha: 0.88),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Image.asset('assets/images/android_abk_foreground.png'),
        ),
        if (expanded) ...<Widget>[
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              strings.brandWordmark,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
        ],
        IconButton(
          onPressed: () {
            ref.read(sidebarExpandedProvider.notifier).state = !expanded;
          },
          tooltip: expanded ? strings.collapseSidebar : strings.openSidebar,
          padding: EdgeInsets.zero,
          constraints: buttonConstraints,
          iconSize: 18,
          icon: Icon(
            expanded
                ? Icons.keyboard_double_arrow_left_rounded
                : Icons.keyboard_double_arrow_right_rounded,
          ),
        ),
      ],
    );
  }
}

class _SidebarDestination extends StatelessWidget {
  const _SidebarDestination({
    required this.destination,
    required this.selected,
    required this.expanded,
    required this.onTap,
  });

  final _ShellDestination destination;
  final bool selected;
  final bool expanded;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(22),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          padding: EdgeInsets.symmetric(
            horizontal: expanded ? 14 : 0,
            vertical: 14,
          ),
          decoration: BoxDecoration(
            color: selected
                ? scheme.primaryContainer.withValues(alpha: 0.84)
                : Colors.transparent,
            borderRadius: BorderRadius.circular(22),
          ),
          child: Row(
            mainAxisAlignment: expanded
                ? MainAxisAlignment.start
                : MainAxisAlignment.center,
            children: <Widget>[
              Icon(
                destination.icon,
                color: selected ? scheme.primary : scheme.onSurfaceVariant,
              ),
              if (expanded) ...<Widget>[
                const SizedBox(width: 12),
                Flexible(
                  child: Text(
                    destination.label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelLarge?.copyWith(
                      color: selected
                          ? scheme.primary
                          : scheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _SidebarStatus extends StatelessWidget {
  const _SidebarStatus({
    required this.state,
    required this.expanded,
    required this.sidecarHost,
    required this.sidecarPort,
  });

  final DashboardState state;
  final bool expanded;
  final String sidecarHost;
  final int sidecarPort;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final strings = context.strings;
    final color = _shellStatusColor(state, scheme);
    final icon = _shellStatusIcon(state);
    final label = strings.connectionStatusLabel(state.flow);
    if (!expanded) {
      return Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Icon(icon, color: color, size: 18),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        StatusPill(label: label, color: color, icon: icon),
        const SizedBox(height: 12),
        Text(
          strings.sidecarAddress(sidecarHost, sidecarPort),
          style: Theme.of(
            context,
          ).textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
        ),
      ],
    );
  }
}

class _CompactHeader extends StatelessWidget {
  const _CompactHeader({required this.state});

  final DashboardState state;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final strings = context.strings;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: scheme.surface.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(30),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.42),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
        child: Row(
          children: <Widget>[
            Container(
              width: 44,
              height: 44,
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: scheme.primaryContainer.withValues(alpha: 0.88),
                borderRadius: BorderRadius.circular(14),
              ),
              child: Image.asset('assets/images/android_abk_foreground.png'),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    strings.brandWordmark,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    strings.shellCompactSubtitle,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            StatusPill(
              label: strings.connectionStatusLabel(state.flow),
              color: _shellStatusColor(state, scheme),
              icon: _shellStatusIcon(state),
            ),
          ],
        ),
      ),
    );
  }
}

class _ContentSurface extends StatelessWidget {
  const _ContentSurface({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: scheme.surface.withValues(alpha: 0.76),
        borderRadius: BorderRadius.circular(36),
        border: Border.all(
          color: scheme.outlineVariant.withValues(alpha: 0.34),
        ),
      ),
      child: ClipRRect(borderRadius: BorderRadius.circular(36), child: child),
    );
  }
}

class _ShellDestination {
  const _ShellDestination({
    required this.route,
    required this.label,
    required this.icon,
  });

  final String route;
  final String label;
  final IconData icon;
}

IconData _shellStatusIcon(DashboardState state) {
  return switch (state.flow) {
    ConnectionFlow.connectedAbk => Icons.verified_rounded,
    ConnectionFlow.connectedAdbFallback => Icons.usb_rounded,
    ConnectionFlow.sidecarUnavailable => Icons.wifi_tethering_error_rounded,
    ConnectionFlow.connecting => Icons.sync_rounded,
    ConnectionFlow.detecting => Icons.radar_rounded,
    ConnectionFlow.failed => Icons.warning_amber_rounded,
    ConnectionFlow.idle => Icons.play_circle_outline_rounded,
  };
}

Color _shellStatusColor(DashboardState state, ColorScheme scheme) {
  return switch (state.flow) {
    ConnectionFlow.connectedAbk => scheme.primary,
    ConnectionFlow.connectedAdbFallback => scheme.secondary,
    ConnectionFlow.sidecarUnavailable => scheme.error,
    ConnectionFlow.connecting => scheme.tertiary,
    ConnectionFlow.detecting => scheme.tertiary,
    ConnectionFlow.failed => scheme.error,
    ConnectionFlow.idle => scheme.onSurfaceVariant,
  };
}
