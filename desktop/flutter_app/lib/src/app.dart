import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/localization/app_strings.dart';
import 'core/platform/desktop_window_launch.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/desktop_theme_provider.dart';
import 'features/build/build_page.dart';
import 'features/detect/detection_page.dart';
import 'features/device/device_page.dart';
import 'features/home/home_page.dart';
import 'features/settings/settings_page.dart';
import 'features/shell/app_shell.dart';
import 'features/webui/webui_window_page.dart';

class AbkDesktopApp extends ConsumerWidget {
  const AbkDesktopApp({
    super.key,
    this.launch = const DesktopWindowLaunch.main(),
  });

  final DesktopWindowLaunch launch;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeAsync = ref.watch(desktopThemeProvider);
    if (launch.isWebUi) {
      return MaterialApp(
        onGenerateTitle: (context) => AppStrings.of(context).appTitle,
        debugShowCheckedModeBanner: false,
        locale: const Locale('zh', 'CN'),
        supportedLocales: AppStrings.supportedLocales,
        localizationsDelegates: const [
          AppStrings.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        theme:
            themeAsync.valueOrNull ??
            AppTheme.light(seedColor: AppTheme.fallbackSeedColor),
        home: WebUiWindowPage(
          url: launch.url!,
          title: launch.title ?? 'ABK WebUI',
        ),
      );
    }
    final router = GoRouter(
      initialLocation: '/home',
      routes: [
        ShellRoute(
          builder: (context, state, child) => AppShell(child: child),
          routes: [
            GoRoute(
              path: '/home',
              builder: (context, state) => const HomePage(),
            ),
            GoRoute(
              path: '/detect',
              builder: (context, state) => const DetectionPage(),
            ),
            GoRoute(
              path: '/build',
              builder: (context, state) => const BuildPage(),
            ),
            GoRoute(
              path: '/device',
              builder: (context, state) => const DevicePage(),
            ),
            GoRoute(
              path: '/device/kernel',
              builder: (context, state) => const KernelFeaturesPage(),
            ),
            GoRoute(
              path: '/device/susfs',
              builder: (context, state) => const SusfsPage(),
            ),
            GoRoute(
              path: '/settings',
              builder: (context, state) => const SettingsPage(),
            ),
          ],
        ),
      ],
    );

    return MaterialApp.router(
      onGenerateTitle: (context) => AppStrings.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      locale: const Locale('zh', 'CN'),
      supportedLocales: AppStrings.supportedLocales,
      localizationsDelegates: const [
        AppStrings.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme:
          themeAsync.valueOrNull ??
          AppTheme.light(seedColor: AppTheme.fallbackSeedColor),
      routerConfig: router,
    );
  }
}
