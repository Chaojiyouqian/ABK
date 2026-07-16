import 'dart:convert';

enum DesktopWindowKind { main, webui }

class DesktopWindowLaunch {
  const DesktopWindowLaunch._({required this.kind, this.url, this.title});

  const DesktopWindowLaunch.main() : this._(kind: DesktopWindowKind.main);

  const DesktopWindowLaunch.webui({required String url, required String title})
    : this._(kind: DesktopWindowKind.webui, url: url, title: title);

  final DesktopWindowKind kind;
  final String? url;
  final String? title;

  bool get isWebUi => kind == DesktopWindowKind.webui;

  factory DesktopWindowLaunch.fromWindowArguments(String? raw) {
    final clean = raw?.trim();
    if (clean == null || clean.isEmpty) {
      return const DesktopWindowLaunch.main();
    }

    final dynamic decoded;
    try {
      decoded = jsonDecode(clean);
    } catch (_) {
      return const DesktopWindowLaunch.main();
    }
    if (decoded is! Map) {
      return const DesktopWindowLaunch.main();
    }
    final map = Map<String, dynamic>.from(decoded);
    final type = (map['type'] as String?)?.trim().toLowerCase() ?? '';
    if (type != 'webui') {
      return const DesktopWindowLaunch.main();
    }
    final url = (map['url'] as String?)?.trim() ?? '';
    final title = (map['title'] as String?)?.trim() ?? 'ABK WebUI';
    if (url.isEmpty) {
      return const DesktopWindowLaunch.main();
    }
    return DesktopWindowLaunch.webui(url: url, title: title);
  }

  String toWindowArguments() {
    return jsonEncode(<String, dynamic>{
      'type': switch (kind) {
        DesktopWindowKind.main => 'main',
        DesktopWindowKind.webui => 'webui',
      },
      'url': url,
      'title': title,
    });
  }
}
