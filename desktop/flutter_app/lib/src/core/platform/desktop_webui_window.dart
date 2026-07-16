import 'package:desktop_multi_window/desktop_multi_window.dart';

import 'desktop_window_launch.dart';

abstract interface class DesktopWebUiWindow {
  Future<bool> open({required String url, required String title});
}

class DesktopMultiWindowWebUiWindow implements DesktopWebUiWindow {
  @override
  Future<bool> open({required String url, required String title}) async {
    try {
      final controller = await WindowController.create(
        WindowConfiguration(
          hiddenAtLaunch: true,
          arguments: DesktopWindowLaunch.webui(
            url: url,
            title: title,
          ).toWindowArguments(),
        ),
      );
      await controller.show();
      return true;
    } catch (_) {
      return false;
    }
  }
}
