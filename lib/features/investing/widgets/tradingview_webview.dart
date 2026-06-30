import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../../core/design_system/design_system.dart';

enum TradingViewKind { chart, mini, news }

/// Wraps the TradingView embed widgets (advanced chart / mini symbol overview /
/// news timeline) in a WebView, matching the web app's configs. All use the
/// light theme, as the web hardcodes.
class TradingViewWidget extends StatefulWidget {
  const TradingViewWidget({
    super.key,
    required this.kind,
    this.symbol = 'AAPL',
    required this.height,
  });

  final TradingViewKind kind;
  final String symbol;
  final double height;

  @override
  State<TradingViewWidget> createState() => _TradingViewWidgetState();
}

class _TradingViewWidgetState extends State<TradingViewWidget> {
  late final WebViewController _controller;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(const Color(0x00000000))
      ..loadHtmlString(_html());
  }

  @override
  void didUpdateWidget(covariant TradingViewWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.symbol != widget.symbol || oldWidget.kind != widget.kind) {
      _controller.loadHtmlString(_html());
    }
  }

  String _html() {
    final h = widget.height.round();
    switch (widget.kind) {
      case TradingViewKind.chart:
        return '''
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:0">
<div id="tv_chart" style="height:100vh;width:100%"></div>
<script src="https://s3.tradingview.com/tv.js"></script>
<script>
new TradingView.widget({autosize:true,symbol:"${widget.symbol}",interval:"D",timezone:"Etc/UTC",theme:"light",style:"1",locale:"en",enable_publishing:false,allow_symbol_change:true,hide_side_toolbar:false,withdateranges:true,container_id:"tv_chart"});
</script>
</body></html>''';
      case TradingViewKind.mini:
        return '''
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:0">
<div class="tradingview-widget-container">
  <div class="tradingview-widget-container__widget"></div>
  <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-mini-symbol-overview.js" async>
  {"symbol":"${widget.symbol}","width":"100%","height":$h,"locale":"en","dateRange":"1M","colorTheme":"light","isTransparent":true,"autosize":false,"largeChartUrl":""}
  </script>
</div>
</body></html>''';
      case TradingViewKind.news:
        return '''
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
<body style="margin:0;padding:0">
<div class="tradingview-widget-container">
  <div class="tradingview-widget-container__widget"></div>
  <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-timeline.js" async>
  {"feedMode":"market","market":"stock","isTransparent":true,"displayMode":"regular","width":"100%","height":$h,"colorTheme":"light","locale":"en"}
  </script>
</div>
</body></html>''';
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      height: widget.height,
      decoration: BoxDecoration(
        color: c.card,
        borderRadius: FCRadii.lgAll,
        border: Border.all(color: c.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: WebViewWidget(controller: _controller),
    );
  }
}
