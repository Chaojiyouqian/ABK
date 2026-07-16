import 'package:flutter/widgets.dart';
import 'package:desktop_multi_window/desktop_multi_window.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/app.dart';
import 'src/core/platform/desktop_window_launch.dart';

Future<void> main(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();
  final windowController = await WindowController.fromCurrentEngine();
  final launch = DesktopWindowLaunch.fromWindowArguments(
    windowController.arguments,
  );
  runApp(ProviderScope(child: AbkDesktopApp(launch: launch)));
}
