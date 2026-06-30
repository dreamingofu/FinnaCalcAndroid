import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../models/market_models.dart';
import '../services/market_data_service.dart';
import 'tradingview_webview.dart';

/// Stock search + detail, mirroring the web's `components/stocks-page.tsx`.
///
/// A debounced typeahead drives [MarketDataService.searchStocks]; selecting a
/// result (or [initialSymbol] on init) loads the full quote via
/// [MarketDataService.getStock] and renders a logo, price/change, description,
/// P/E + market cap, an embedded TradingView chart, and demo Buy/Sell actions.
class StocksPage extends StatefulWidget {
  const StocksPage({super.key, this.initialSymbol});

  final String? initialSymbol;

  @override
  State<StocksPage> createState() => _StocksPageState();
}

class _StocksPageState extends State<StocksPage> {
  final _searchController = TextEditingController();
  Timer? _debounce;

  List<StockSearchResult> _results = const [];
  StockDetail? _selected;
  bool _loadingDetail = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final initial = widget.initialSymbol;
    if (initial != null && initial.trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _fetchDetail(initial.trim());
      });
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    final term = value.trim();
    if (term.length < 2) {
      if (_results.isNotEmpty) setState(() => _results = const []);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 250), () => _search(term));
  }

  Future<void> _search(String term) async {
    try {
      final results =
          await context.read<MarketDataService>().searchStocks(term);
      if (!mounted) return;
      // Ignore stale results if the field changed since this lookup started.
      if (_searchController.text.trim() != term) return;
      setState(() => _results = results);
    } on ApiException {
      // Silently ignore failed typeahead lookups (matches the web).
    }
  }

  Future<void> _fetchDetail(String symbol) async {
    _debounce?.cancel();
    setState(() {
      _loadingDetail = true;
      _error = null;
      _selected = null;
      _results = const [];
    });
    try {
      final detail = await context.read<MarketDataService>().getStock(symbol);
      if (!mounted) return;
      setState(() {
        _selected = detail;
        _loadingDetail = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loadingDetail = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        FCCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              FCCardHeader(children: [
                const FCCardTitle('Search Stocks'),
                const SizedBox(height: 6),
                FCTextField(
                  controller: _searchController,
                  hintText: 'e.g., AAPL, Microsoft',
                  textInputAction: TextInputAction.search,
                  prefix: const Icon(Icons.search),
                  onChanged: _onSearchChanged,
                  onSubmitted: (_) {
                    if (_results.isNotEmpty) {
                      _fetchDetail(_results.first.symbol);
                    }
                  },
                ),
              ]),
              FCCardContent(child: _content(context, c)),
            ],
          ),
        ),
      ],
    );
  }

  Widget _content(BuildContext context, FCColors c) {
    if (_error != null) {
      return _errorBlock(context, c);
    }
    if (_results.isNotEmpty) {
      return _resultsList(context, c);
    }
    if (_loadingDetail) {
      return _loadingBlock(context, c);
    }
    if (_selected != null) {
      return _detail(context, c, _selected!);
    }
    return _emptyBlock(context, c);
  }

  Widget _resultsList(BuildContext context, FCColors c) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final r in _results)
          InkWell(
            onTap: () {
              _searchController.text = r.symbol;
              _fetchDetail(r.symbol);
            },
            borderRadius: FCRadii.mdAll,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(r.symbol,
                      style: TextStyle(
                          fontSize: FCFontSizes.base,
                          fontWeight: FCFontWeights.bold,
                          color: c.foreground)),
                  const SizedBox(height: 2),
                  Text('${r.name} · ${r.region}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: FCFontSizes.sm,
                          color: c.mutedForeground)),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _detail(BuildContext context, FCColors c, StockDetail s) {
    final up = s.change >= 0;
    final changeColor = up ? FCPalette.green600 : FCPalette.red600;
    final sign = up ? '+' : '';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _logo(context, c, s),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text('${s.name} (${s.symbol})',
                      style: TextStyle(
                          fontSize: FCFontSizes.xl,
                          fontWeight: FCFontWeights.bold,
                          color: c.foreground)),
                  const SizedBox(height: 4),
                  Text(Fmt.money2(s.price),
                      style: TextStyle(
                          fontSize: FCFontSizes.xl2,
                          fontWeight: FCFontWeights.bold,
                          color: c.foreground)),
                  const SizedBox(height: 2),
                  Text(
                    '$sign${Fmt.fixed(s.change, 2)} (${Fmt.pct(s.changePercent, 2)})',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        fontWeight: FCFontWeights.medium,
                        color: changeColor),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: FCButton(
                label: 'Buy',
                icon: const Icon(Icons.trending_up),
                onPressed: () => _openTradeDialog(context, s, _TradeType.buy),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FCButton(
                label: 'Sell',
                variant: FCButtonVariant.outline,
                icon: const Icon(Icons.trending_down),
                onPressed: () => _openTradeDialog(context, s, _TradeType.sell),
              ),
            ),
          ],
        ),
        if (s.description.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text(
            s.description,
            maxLines: 6,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.mutedForeground,
                height: 1.5),
          ),
        ],
        const SizedBox(height: 16),
        FCResultGrid(columns: 2, spacing: 12, children: [
          _stat(context, c, 'P/E Ratio', s.peRatio),
          _stat(context, c, 'Market Cap', _fmtMarketCap(s.marketCap)),
        ]),
        const SizedBox(height: 16),
        TradingViewWidget(
          kind: TradingViewKind.chart,
          symbol: s.symbol,
          height: 420,
        ),
      ],
    );
  }

  Widget _logo(BuildContext context, FCColors c, StockDetail s) {
    final fallback = Container(
      width: 48,
      height: 48,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: c.muted,
        shape: BoxShape.circle,
        border: Border.all(color: c.border),
      ),
      child: Text(
        s.symbol.isNotEmpty ? s.symbol.characters.first.toUpperCase() : '?',
        style: TextStyle(
            fontSize: FCFontSizes.lg,
            fontWeight: FCFontWeights.bold,
            color: c.mutedForeground),
      ),
    );
    if (s.logo.isEmpty) return fallback;
    return ClipOval(
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: c.background,
          shape: BoxShape.circle,
          border: Border.all(color: c.border),
        ),
        child: Image.network(
          s.logo,
          width: 48,
          height: 48,
          fit: BoxFit.cover,
          errorBuilder: (context, error, stack) => fallback,
        ),
      ),
    );
  }

  Widget _stat(BuildContext context, FCColors c, String label, String value) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.lgAll,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label,
              style: TextStyle(
                  fontSize: FCFontSizes.xs, color: c.mutedForeground)),
          const SizedBox(height: 4),
          Text(value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  fontSize: FCFontSizes.lg,
                  fontWeight: FCFontWeights.bold,
                  color: c.foreground)),
        ],
      ),
    );
  }

  Widget _loadingBlock(BuildContext context, FCColors c) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40),
      child: Column(
        children: [
          const SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              valueColor: AlwaysStoppedAnimation<Color>(FCPalette.blue600),
            ),
          ),
          const SizedBox(height: 12),
          Text('Loading stock data…',
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.medium,
                  color: c.foreground)),
        ],
      ),
    );
  }

  Widget _errorBlock(BuildContext context, FCColors c) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.error_outline, size: 16, color: c.destructive),
            const SizedBox(width: 8),
            Expanded(
              child: Text(_error!,
                  style:
                      TextStyle(fontSize: FCFontSizes.sm, color: c.destructive)),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerLeft,
          child: FCButton(
            label: 'Try again',
            variant: FCButtonVariant.outline,
            size: FCButtonSize.sm,
            icon: const Icon(Icons.refresh),
            onPressed: () {
              final term = _searchController.text.trim();
              if (term.isNotEmpty) {
                _fetchDetail(term);
              } else {
                setState(() => _error = null);
              }
            },
          ),
        ),
      ],
    );
  }

  Widget _emptyBlock(BuildContext context, FCColors c) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(
        children: [
          Icon(Icons.search, size: 36, color: c.mutedForeground),
          const SizedBox(height: 12),
          Text(
            'Search for a stock by symbol or company name to see live pricing '
            'and an interactive chart.',
            textAlign: TextAlign.center,
            style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.mutedForeground,
                height: 1.5),
          ),
        ],
      ),
    );
  }

  Future<void> _openTradeDialog(
      BuildContext context, StockDetail s, _TradeType type) async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => _TradeDialog(stock: s, type: type),
    );
  }

  static String _fmtMarketCap(String raw) {
    final n = double.tryParse(raw.trim());
    if (n == null || n <= 0) return raw.isEmpty ? '—' : raw;
    if (n >= 1e12) return '\$${(n / 1e12).toStringAsFixed(2)}T';
    if (n >= 1e9) return '\$${(n / 1e9).toStringAsFixed(1)}B';
    if (n >= 1e6) return '\$${(n / 1e6).toStringAsFixed(0)}M';
    return Fmt.money0(n);
  }
}

enum _TradeType { buy, sell }

/// A demo Buy/Sell dialog: a quantity field, a live total, and a Confirm that
/// only shows a "Demo only" SnackBar (trading is a demo in the web app).
class _TradeDialog extends StatefulWidget {
  const _TradeDialog({required this.stock, required this.type});

  final StockDetail stock;
  final _TradeType type;

  @override
  State<_TradeDialog> createState() => _TradeDialogState();
}

class _TradeDialogState extends State<_TradeDialog> {
  final _quantityController = TextEditingController(text: '1');

  @override
  void dispose() {
    _quantityController.dispose();
    super.dispose();
  }

  int get _quantity {
    final q = int.tryParse(_quantityController.text.trim()) ?? 0;
    return q < 0 ? 0 : q;
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final isBuy = widget.type == _TradeType.buy;
    final total = widget.stock.price * _quantity;
    return Dialog(
      backgroundColor: c.popover,
      shape: const RoundedRectangleBorder(borderRadius: FCRadii.lgAll),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('${isBuy ? 'Buy' : 'Sell'} ${widget.stock.symbol}',
                style: TextStyle(
                    fontSize: FCFontSizes.lg,
                    fontWeight: FCFontWeights.semibold,
                    color: c.popoverForeground)),
            const SizedBox(height: 6),
            Text('Current price: ${Fmt.money2(widget.stock.price)}',
                style: TextStyle(
                    fontSize: FCFontSizes.sm, color: c.mutedForeground)),
            const SizedBox(height: 16),
            FCTextField(
              controller: _quantityController,
              label: 'Quantity',
              hintText: '1',
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Total',
                    style: TextStyle(
                        fontSize: FCFontSizes.base,
                        fontWeight: FCFontWeights.semibold,
                        color: c.popoverForeground)),
                Text(Fmt.money2(total),
                    style: TextStyle(
                        fontSize: FCFontSizes.base,
                        fontWeight: FCFontWeights.bold,
                        color: c.popoverForeground)),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: FCButton(
                    label: 'Cancel',
                    variant: FCButtonVariant.outline,
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FCButton(
                    label: 'Confirm ${isBuy ? 'Buy' : 'Sell'}',
                    variant: isBuy
                        ? FCButtonVariant.primary
                        : FCButtonVariant.destructive,
                    onPressed: () {
                      final messenger = ScaffoldMessenger.of(context);
                      Navigator.of(context).pop();
                      messenger.showSnackBar(
                        const SnackBar(content: Text('Demo only')),
                      );
                    },
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
