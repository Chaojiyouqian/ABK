import 'package:flutter/material.dart';
import 'package:zikzak_inappwebview/zikzak_inappwebview.dart';

class WebUiWindowPage extends StatefulWidget {
  const WebUiWindowPage({super.key, required this.url, required this.title});

  final String url;
  final String title;

  @override
  State<WebUiWindowPage> createState() => _WebUiWindowPageState();
}

class _WebUiWindowPageState extends State<WebUiWindowPage> {
  InAppWebViewController? _controller;
  double _progress = 0;
  String? _lastError;

  @override
  void initState() {
    super.initState();
    debugPrint('[WebUIWindow] init ${widget.title} ${widget.url}');
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: <Widget>[
          IconButton(
            onPressed: _controller == null
                ? null
                : () {
                    setState(() => _lastError = null);
                    _controller!.reload();
                  },
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(3),
          child: _progress >= 1
              ? const SizedBox(height: 3)
              : LinearProgressIndicator(value: _progress),
        ),
      ),
      body: Stack(
        children: <Widget>[
          InAppWebView(
            initialUrlRequest: URLRequest(url: WebUri(widget.url)),
            initialSettings: InAppWebViewSettings(
              javaScriptEnabled: true,
              domStorageEnabled: true,
              databaseEnabled: true,
              isElementFullscreenEnabled: true,
            ),
            onWebViewCreated: (controller) {
              debugPrint('[WebUIWindow] created ${widget.title}');
              _controller = controller;
            },
            onProgressChanged: (controller, progress) {
              setState(() {
                _progress = progress / 100;
              });
            },
            onConsoleMessage: (controller, consoleMessage) {
              debugPrint(
                '[WebUI:${widget.title}] ${consoleMessage.messageLevel} ${consoleMessage.message}',
              );
            },
            onReceivedError: (controller, request, error) {
              debugPrint(
                '[WebUIWindow] load error ${widget.title} ${error.type} ${error.description}',
              );
              setState(() {
                _lastError =
                    'WebUI load failed: ${error.type} ${error.description}'
                        .trim();
              });
            },
            onReceivedHttpError: (controller, request, errorResponse) {
              debugPrint(
                '[WebUIWindow] http error ${widget.title} ${errorResponse.statusCode} ${errorResponse.reasonPhrase}',
              );
              setState(() {
                _lastError =
                    'WebUI HTTP ${errorResponse.statusCode ?? 0} ${errorResponse.reasonPhrase ?? ''}'
                        .trim();
              });
            },
          ),
          if (_lastError != null)
            Positioned(
              left: 16,
              right: 16,
              bottom: 16,
              child: Material(
                color: scheme.errorContainer,
                borderRadius: BorderRadius.circular(16),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Text(
                    _lastError!,
                    style: TextStyle(color: scheme.onErrorContainer),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
