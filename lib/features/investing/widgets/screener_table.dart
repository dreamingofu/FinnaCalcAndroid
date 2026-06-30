import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../models/market_models.dart';
import '../services/market_data_service.dart';

/// Sortable screener columns — 1:1 with the web's `SortKey`.
enum _SortKey { symbol, price, changePercent, marketCap, peRatio, dividendYield, beta }

/// A filterable, sortable stock screener table, mirroring the web's
/// `components/dashboard-screener.tsx`. Fetches [MarketDataService.getScreener]
/// once, then applies all filters/sorting client-side. Tapping a row calls
/// [onSelectSymbol] with that row's ticker.
class ScreenerTable extends StatefulWidget {
  const ScreenerTable({super.key, required this.onSelectSymbol});

  final void Function(String) onSelectSymbol;

  @override
  State<ScreenerTable> createState() => _ScreenerTableState();
}

class _ScreenerTableState extends State<ScreenerTable> {
  static const _capAll = 'All';
  static const _capMega = 'Mega (>\$200B)';
  static const _capLarge = 'Large (\$10B–\$200B)';
  static const _capMid = 'Mid (<\$10B)';
  static const _capBuckets = [_capAll, _capMega, _capLarge, _capMid];

  static const _perfAll = 'All';
  static const _perfGainers = 'Gainers';
  static const _perfLosers = 'Losers';
  static const _perfOptions = [_perfAll, _perfGainers, _perfLosers];

  final _minPriceController = TextEditingController();
  final _maxPriceController = TextEditingController();
  final _maxPeController = TextEditingController();

  List<ScreenerRow> _rows = const [];
  bool _loading = true;
  String? _error;

  String _sector = 'All';
  String _cap = _capAll;
  String _perf = _perfAll;
  double _minYield = 0;
  _SortKey _sortKey = _SortKey.marketCap;
  bool _sortAsc = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _minPriceController.dispose();
    _maxPriceController.dispose();
    _maxPeController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final rows = await context.read<MarketDataService>().getScreener();
      if (!mounted) return;
      setState(() {
        _rows = rows;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loading = false;
      });
    }
  }

  List<String> get _sectors {
    final seen = <String>{};
    for (final r in _rows) {
      if (r.sector.isNotEmpty) seen.add(r.sector);
    }
    final sorted = seen.toList()..sort();
    return ['All', ...sorted];
  }

  List<ScreenerRow> get _filtered {
    final lo = double.tryParse(_minPriceController.text.trim());
    final hi = double.tryParse(_maxPriceController.text.trim());
    final pe = double.tryParse(_maxPeController.text.trim());

    final r = _rows.where((row) {
      if (_sector != 'All' && row.sector != _sector) return false;
      if (_perf == _perfGainers && row.changePercent <= 0) return false;
      if (_perf == _perfLosers && row.changePercent >= 0) return false;
      if (lo != null && row.price < lo) return false;
      if (hi != null && row.price > hi) return false;
      if (pe != null && (row.peRatio == null || row.peRatio! > pe)) return false;
      if (_minYield > 0 && (row.dividendYield ?? 0) < _minYield) return false;
      if (_cap != _capAll && row.marketCap != null) {
        final mc = row.marketCap!;
        if (_cap == _capMega && mc < 200e9) return false;
        if (_cap == _capLarge && (mc < 10e9 || mc >= 200e9)) return false;
        if (_cap == _capMid && mc >= 10e9) return false;
      }
      return true;
    }).toList();

    r.sort((a, b) {
      final cmp = _compare(a, b);
      return _sortAsc ? cmp : -cmp;
    });
    return r;
  }

  int _compare(ScreenerRow a, ScreenerRow b) {
    switch (_sortKey) {
      case _SortKey.symbol:
        return a.symbol.compareTo(b.symbol);
      case _SortKey.price:
        return a.price.compareTo(b.price);
      case _SortKey.changePercent:
        return a.changePercent.compareTo(b.changePercent);
      case _SortKey.marketCap:
        return _cmpNullable(a.marketCap, b.marketCap);
      case _SortKey.peRatio:
        return _cmpNullable(a.peRatio, b.peRatio);
      case _SortKey.dividendYield:
        return _cmpNullable(a.dividendYield, b.dividendYield);
      case _SortKey.beta:
        return _cmpNullable(a.beta, b.beta);
    }
  }

  /// Mirrors the web's `av == null ? -Infinity` rule: nulls sort lowest.
  int _cmpNullable(double? a, double? b) {
    final av = a ?? double.negativeInfinity;
    final bv = b ?? double.negativeInfinity;
    return av.compareTo(bv);
  }

  void _toggleSort(_SortKey key) {
    setState(() {
      if (_sortKey == key) {
        _sortAsc = !_sortAsc;
      } else {
        _sortKey = key;
        _sortAsc = false;
      }
    });
  }

  void _reset() {
    setState(() {
      _sector = 'All';
      _cap = _capAll;
      _perf = _perfAll;
      _minPriceController.clear();
      _maxPriceController.clear();
      _maxPeController.clear();
      _minYield = 0;
      _sortKey = _SortKey.marketCap;
      _sortAsc = false;
    });
  }

  static String _fmtCap(double? n) {
    if (n == null) return '—';
    if (n >= 1e12) return '\$${(n / 1e12).toStringAsFixed(2)}T';
    if (n >= 1e9) return '\$${(n / 1e9).toStringAsFixed(1)}B';
    if (n >= 1e6) return '\$${(n / 1e6).toStringAsFixed(0)}M';
    return '\$${Fmt.group(n)}';
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              children: [
                const Icon(Icons.tune, size: 20, color: FCPalette.blue600),
                const SizedBox(width: 8),
                const Expanded(child: FCCardTitle('Stock Screener')),
                FCButton(
                  label: 'Reset filters',
                  variant: FCButtonVariant.outline,
                  size: FCButtonSize.sm,
                  onPressed: _reset,
                ),
              ],
            ),
            const SizedBox(height: 6),
            _filters(context, c),
          ]),
          FCCardContent(
            padding: const EdgeInsets.fromLTRB(0, 8, 0, 0),
            child: _body(context, c),
          ),
        ],
      ),
    );
  }

  Widget _filters(BuildContext context, FCColors c) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            Expanded(
              child: _labeled(
                c,
                'Sector',
                FCSelectField<String>(
                  value: _sectors.contains(_sector) ? _sector : 'All',
                  items: [
                    for (final s in _sectors) FCSelectItem(s, s),
                  ],
                  onChanged: (v) => setState(() => _sector = v),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _labeled(
                c,
                'Market Cap',
                FCSelectField<String>(
                  value: _cap,
                  items: [
                    for (final b in _capBuckets) FCSelectItem(b, b),
                  ],
                  onChanged: (v) => setState(() => _cap = v),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: _labeled(
                c,
                'Performance',
                FCSelectField<String>(
                  value: _perf,
                  items: [
                    for (final p in _perfOptions) FCSelectItem(p, p),
                  ],
                  onChanged: (v) => setState(() => _perf = v),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _labeled(
                c,
                'Max P/E',
                _filterInput(_maxPeController, 'Any'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        _label(c, 'Price range'),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(child: _filterInput(_minPriceController, 'Min')),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Text('–',
                  style: TextStyle(color: c.mutedForeground)),
            ),
            Expanded(child: _filterInput(_maxPriceController, 'Max')),
          ],
        ),
        const SizedBox(height: 12),
        _label(c, 'Min Div Yield: ${Fmt.fixed(_minYield, 1)}%'),
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            activeTrackColor: FCPalette.blue600,
            thumbColor: FCPalette.blue600,
            inactiveTrackColor: c.muted,
            overlayColor: FCPalette.blue600.withValues(alpha: 0.12),
          ),
          child: Slider(
            value: _minYield,
            min: 0,
            max: 5,
            divisions: 10,
            label: '${Fmt.fixed(_minYield, 1)}%',
            onChanged: (v) => setState(() => _minYield = v),
          ),
        ),
      ],
    );
  }

  Widget _labeled(FCColors c, String label, Widget child) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        _label(c, label),
        const SizedBox(height: 8),
        child,
      ],
    );
  }

  Widget _label(FCColors c, String text) {
    return Text(text,
        style: TextStyle(
            fontSize: FCFontSizes.xs,
            fontWeight: FCFontWeights.medium,
            color: c.mutedForeground));
  }

  Widget _filterInput(TextEditingController controller, String hint) {
    return FCTextField(
      controller: controller,
      hintText: hint,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      inputFormatters: [
        FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
      ],
      onChanged: (_) => setState(() {}),
    );
  }

  Widget _body(BuildContext context, FCColors c) {
    if (_loading) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 40),
        child: Center(
          child: SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              valueColor: const AlwaysStoppedAnimation<Color>(FCPalette.blue600),
            ),
          ),
        ),
      );
    }
    if (_error != null) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
        child: Column(
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.error_outline, size: 16, color: c.destructive),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(_error!,
                      style: TextStyle(
                          fontSize: FCFontSizes.sm, color: c.destructive)),
                ),
              ],
            ),
            const SizedBox(height: 12),
            FCButton(
              label: 'Retry',
              variant: FCButtonVariant.outline,
              size: FCButtonSize.sm,
              icon: const Icon(Icons.refresh),
              onPressed: _load,
            ),
          ],
        ),
      );
    }

    final rows = _filtered;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: _table(context, c, rows),
        ),
        Container(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
          decoration: BoxDecoration(
            border: Border(top: BorderSide(color: c.border)),
          ),
          child: Text(
            '${rows.length} of ${_rows.length} stocks · tap a row for the full chart',
            style: TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
          ),
        ),
      ],
    );
  }

  Widget _table(BuildContext context, FCColors c, List<ScreenerRow> rows) {
    const tickerW = 80.0;
    const companyW = 180.0;
    const sectorW = 120.0;
    const numW = 92.0;
    const chevW = 32.0;

    Widget headerCell(String label, _SortKey? key,
        {required double width, Alignment align = Alignment.centerRight}) {
      final active = key != null && _sortKey == key;
      final content = Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: align == Alignment.centerLeft
            ? MainAxisAlignment.start
            : MainAxisAlignment.end,
        children: [
          Flexible(
            child: Text(label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    fontWeight: FCFontWeights.semibold,
                    color: active ? c.foreground : c.mutedForeground)),
          ),
          if (key != null) ...[
            const SizedBox(width: 4),
            Icon(
              active
                  ? (_sortAsc ? Icons.arrow_upward : Icons.arrow_downward)
                  : Icons.unfold_more,
              size: 12,
              color: active
                  ? c.foreground
                  : c.mutedForeground.withValues(alpha: 0.5),
            ),
          ],
        ],
      );
      return SizedBox(
        width: width,
        child: key == null
            ? Align(alignment: align, child: content)
            : GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => _toggleSort(key),
                child: Align(alignment: align, child: content),
              ),
      );
    }

    final header = Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Color.alphaBlend(c.muted.withValues(alpha: 0.3), c.card),
        border: Border(bottom: BorderSide(color: c.border)),
      ),
      child: Row(
        children: [
          headerCell('Ticker', _SortKey.symbol,
              width: tickerW, align: Alignment.centerLeft),
          headerCell('Company', null,
              width: companyW, align: Alignment.centerLeft),
          headerCell('Sector', null,
              width: sectorW, align: Alignment.centerLeft),
          headerCell('Price', _SortKey.price, width: numW),
          headerCell('% Chg', _SortKey.changePercent, width: numW),
          headerCell('Mkt Cap', _SortKey.marketCap, width: numW),
          headerCell('P/E', _SortKey.peRatio, width: numW),
          headerCell('Div Yield', _SortKey.dividendYield, width: numW),
          headerCell('Beta', _SortKey.beta, width: numW),
          const SizedBox(width: chevW),
        ],
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        header,
        if (rows.isEmpty)
          Container(
            width: tickerW +
                companyW +
                sectorW +
                numW * 6 +
                chevW +
                24,
            padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 12),
            alignment: Alignment.center,
            child: Text('No stocks match these filters.',
                style: TextStyle(
                    fontSize: FCFontSizes.sm, color: c.mutedForeground)),
          )
        else
          for (final r in rows)
            _row(context, c, r,
                tickerW: tickerW,
                companyW: companyW,
                sectorW: sectorW,
                numW: numW,
                chevW: chevW),
      ],
    );
  }

  Widget _row(
    BuildContext context,
    FCColors c,
    ScreenerRow r, {
    required double tickerW,
    required double companyW,
    required double sectorW,
    required double numW,
    required double chevW,
  }) {
    final up = r.changePercent >= 0;
    final changeColor = up ? FCPalette.green600 : FCPalette.red600;
    final sign = up ? '+' : '';

    Widget cell(String text, double width,
        {Alignment align = Alignment.centerRight,
        Color? color,
        FontWeight? weight}) {
      return SizedBox(
        width: width,
        child: Align(
          alignment: align,
          child: Text(text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign:
                  align == Alignment.centerLeft ? TextAlign.left : TextAlign.right,
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: weight ?? FCFontWeights.normal,
                  color: color ?? c.foreground)),
        ),
      );
    }

    return InkWell(
      onTap: () => widget.onSelectSymbol(r.symbol),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          border: Border(
              bottom: BorderSide(color: c.border.withValues(alpha: 0.6))),
        ),
        child: Row(
          children: [
            cell(r.symbol, tickerW,
                align: Alignment.centerLeft, weight: FCFontWeights.bold),
            cell(r.company, companyW,
                align: Alignment.centerLeft, color: c.mutedForeground),
            cell(r.sector, sectorW,
                align: Alignment.centerLeft, color: c.mutedForeground),
            cell(Fmt.money2(r.price), numW, weight: FCFontWeights.medium),
            cell('$sign${Fmt.pct(r.changePercent, 2)}', numW,
                color: changeColor, weight: FCFontWeights.semibold),
            cell(_fmtCap(r.marketCap), numW),
            cell(r.peRatio != null ? Fmt.fixed(r.peRatio!, 1) : '—', numW),
            cell(
                r.dividendYield != null
                    ? '${Fmt.fixed(r.dividendYield!, 2)}%'
                    : '—',
                numW),
            cell(r.beta != null ? Fmt.fixed(r.beta!, 2) : '—', numW),
            SizedBox(
              width: chevW,
              child: Icon(Icons.chevron_right,
                  size: 16, color: c.mutedForeground),
            ),
          ],
        ),
      ),
    );
  }
}
