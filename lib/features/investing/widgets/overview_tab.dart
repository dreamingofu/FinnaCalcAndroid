import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/fc_segmented_tabs.dart';
import '../models/market_models.dart';
import '../services/market_data_service.dart';
import 'tradingview_webview.dart';

/// The Market Overview tab of the Investing feature — a port of the OVERVIEW
/// behaviour of the web's `components/investing-options.tsx`.
///
/// Fetches the market overview once on init, exposes a universal stock search,
/// a "Browse by Industry" sector explorer, a "Market Movers" card, and a live
/// "Market News" feed. Selecting any stock invokes [onSelectSymbol].
class OverviewTab extends StatefulWidget {
  const OverviewTab({super.key, required this.onSelectSymbol});

  /// Called with the ticker symbol when the user picks a stock anywhere in the
  /// tab (a search result, a sector card, or a mover row).
  final void Function(String symbol) onSelectSymbol;

  @override
  State<OverviewTab> createState() => _OverviewTabState();
}

class _OverviewTabState extends State<OverviewTab> {
  // ── Market overview load state ──────────────────────────────────────────
  MarketOverview? _data;
  bool _loading = true;
  String? _error;

  // ── Selections ──────────────────────────────────────────────────────────
  String? _activeSectorId;
  int _moverTab = 0; // 0 = Gainers, 1 = Losers, 2 = Most Active

  // ── Search ──────────────────────────────────────────────────────────────
  final _searchController = TextEditingController();
  Timer? _debounce;
  int _searchSeq = 0; // guards against out-of-order async results
  List<StockSearchResult> _searchResults = const [];
  bool _searching = false;
  bool _showResults = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await context.read<MarketDataService>().getMarketOverview();
      if (!mounted) return;
      setState(() {
        _data = data;
        _activeSectorId = _defaultSectorId(data.sectorSummary);
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

  /// Defaults to the "technology" sector if present (matching the web), else the
  /// first available sector.
  String? _defaultSectorId(List<SectorSummary> sectors) {
    if (sectors.isEmpty) return null;
    for (final s in sectors) {
      if (s.id == 'technology') return s.id;
    }
    return sectors.first.id;
  }

  // ── Search handling ───────────────────────────────────────────────────────

  void _onSearchChanged(String value) {
    final term = value.trim();
    _debounce?.cancel();
    if (term.length < 2) {
      setState(() {
        _searchResults = const [];
        _searching = false;
        _showResults = term.isNotEmpty; // keep panel open to hint "keep typing"
      });
      return;
    }
    setState(() {
      _showResults = true;
      _searching = true;
    });
    _debounce = Timer(const Duration(milliseconds: 250), () => _runSearch(term));
  }

  Future<void> _runSearch(String term) async {
    final seq = ++_searchSeq;
    try {
      final results =
          await context.read<MarketDataService>().searchStocks(term);
      if (!mounted || seq != _searchSeq) return;
      setState(() {
        _searchResults = results;
        _searching = false;
      });
    } on ApiException {
      if (!mounted || seq != _searchSeq) return;
      setState(() {
        _searchResults = const [];
        _searching = false;
      });
    }
  }

  void _selectSymbol(String symbol) {
    _debounce?.cancel();
    _searchSeq++; // invalidate any in-flight search
    _searchController.clear();
    setState(() {
      _searchResults = const [];
      _searching = false;
      _showResults = false;
    });
    FocusScope.of(context).unfocus();
    widget.onSelectSymbol(symbol);
  }

  // ── Build ───────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        _searchBar(context),
        const SizedBox(height: 24),
        if (_loading)
          _loadingState(context)
        else if (_error != null)
          _errorState(context)
        else ...[
          _browseByIndustry(context, _data!),
          const SizedBox(height: 24),
          _marketMovers(context, _data!),
          const SizedBox(height: 24),
          _marketNews(context),
        ],
      ],
    );
  }

  // ── Loading / error ───────────────────────────────────────────────────────

  Widget _loadingState(BuildContext context) {
    final c = context.colors;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 64),
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
          Text(
            'Loading market data…',
            style: TextStyle(
                fontSize: FCFontSizes.sm, color: c.mutedForeground),
          ),
        ],
      ),
    );
  }

  Widget _errorState(BuildContext context) {
    final c = context.colors;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.destructive.withValues(alpha: 0.1),
        borderRadius: FCRadii.lgAll,
        border: Border.all(color: c.destructive.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.error_outline, size: 18, color: c.destructive),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _error!,
                  style: TextStyle(
                      fontSize: FCFontSizes.sm, color: c.destructive),
                ),
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

  // ── Universal search ──────────────────────────────────────────────────────

  Widget _searchBar(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        FCTextField(
          controller: _searchController,
          hintText: 'Search any stock by name or ticker — e.g. Apple, TSLA',
          textInputAction: TextInputAction.search,
          onChanged: _onSearchChanged,
          prefix: const Icon(Icons.search),
        ),
        if (_showResults) ...[
          const SizedBox(height: 8),
          Container(
            decoration: BoxDecoration(
              color: c.popover,
              borderRadius: FCRadii.lgAll,
              border: Border.all(color: c.border),
              boxShadow: kShadowSm,
            ),
            clipBehavior: Clip.antiAlias,
            constraints: const BoxConstraints(maxHeight: 288),
            child: _searchResultsList(context),
          ),
        ],
      ],
    );
  }

  Widget _searchResultsList(BuildContext context) {
    final c = context.colors;
    if (_searching) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Text('Searching…',
            style: TextStyle(
                fontSize: FCFontSizes.sm, color: c.mutedForeground)),
      );
    }
    if (_searchResults.isEmpty) {
      final term = _searchController.text.trim();
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          term.length < 2 ? 'Keep typing to search…' : 'No results found.',
          style:
              TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground),
        ),
      );
    }
    return ListView.separated(
      shrinkWrap: true,
      padding: EdgeInsets.zero,
      itemCount: _searchResults.length,
      separatorBuilder: (_, _) => Divider(height: 1, color: c.border),
      itemBuilder: (context, i) {
        final r = _searchResults[i];
        return _SymbolLogoTile(
          symbol: r.symbol,
          name: r.name,
          onTap: () => _selectSymbol(r.symbol),
        );
      },
    );
  }

  // ── Browse by Industry ────────────────────────────────────────────────────

  Widget _browseByIndustry(BuildContext context, MarketOverview data) {
    final c = context.colors;
    final active = _activeSector(data);
    final stocks = active == null
        ? const <StockQuote>[]
        : data.stocks.where((s) => s.sector == active.name).toList();

    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        'Browse by Industry',
                        style: TextStyle(
                          fontSize: FCFontSizes.lg,
                          fontWeight: FCFontWeights.semibold,
                          color: c.cardForeground,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        "Average sector performance based on today's top "
                        'holdings',
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground),
                      ),
                    ],
                  ),
                ),
                if (active != null) ...[
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text('Sector avg',
                          style: TextStyle(
                              fontSize: FCFontSizes.xs,
                              color: c.mutedForeground)),
                      const SizedBox(height: 2),
                      Text(
                        _fmtPct(active.avgChange),
                        style: TextStyle(
                          fontSize: FCFontSizes.lg,
                          fontWeight: FCFontWeights.bold,
                          color: _changeColor(active.avgChange),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
            if (data.sectorSummary.isNotEmpty)
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final sector in data.sectorSummary)
                    _sectorPill(context, sector,
                        selected: sector.id == _activeSectorId),
                ],
              ),
          ]),
          FCCardContent(
            child: stocks.isEmpty
                ? Padding(
                    padding: const EdgeInsets.symmetric(vertical: 24),
                    child: Center(
                      child: Text(
                        'No data available for this sector.',
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground),
                      ),
                    ),
                  )
                : _stockGrid(context, stocks),
          ),
        ],
      ),
    );
  }

  SectorSummary? _activeSector(MarketOverview data) {
    for (final s in data.sectorSummary) {
      if (s.id == _activeSectorId) return s;
    }
    return data.sectorSummary.isNotEmpty ? data.sectorSummary.first : null;
  }

  Widget _sectorPill(BuildContext context, SectorSummary sector,
      {required bool selected}) {
    final c = context.colors;
    final accent = _sectorColor(sector.color);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => setState(() => _activeSectorId = sector.id),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        decoration: BoxDecoration(
          color: selected ? accent : c.background,
          borderRadius: FCRadii.fullAll,
          border: Border.all(color: selected ? accent : c.border),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              sector.name,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                fontWeight: FCFontWeights.medium,
                color: selected ? Colors.white : c.foreground,
              ),
            ),
            const SizedBox(width: 6),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: selected
                    ? Colors.white.withValues(alpha: 0.2)
                    : _changeColor(sector.avgChange).withValues(alpha: 0.12),
                borderRadius: FCRadii.fullAll,
              ),
              child: Text(
                _fmtPct(sector.avgChange),
                style: TextStyle(
                  fontSize: FCFontSizes.xs,
                  fontWeight: FCFontWeights.semibold,
                  color: selected
                      ? Colors.white
                      : _changeColor(sector.avgChange),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _stockGrid(BuildContext context, List<StockQuote> stocks) {
    return LayoutBuilder(builder: (context, constraints) {
      const spacing = 12.0;
      final cols = constraints.maxWidth >= 520 ? 3 : 2;
      final itemWidth =
          (constraints.maxWidth - spacing * (cols - 1)) / cols;
      return Wrap(
        spacing: spacing,
        runSpacing: spacing,
        children: [
          for (final stock in stocks)
            SizedBox(
              width: itemWidth > 0 ? itemWidth : constraints.maxWidth,
              child: _StockCard(
                stock: stock,
                onTap: () => _selectSymbol(stock.symbol),
              ),
            ),
        ],
      );
    });
  }

  // ── Market Movers ─────────────────────────────────────────────────────────

  Widget _marketMovers(BuildContext context, MarketOverview data) {
    final c = context.colors;
    final movers = switch (_moverTab) {
      1 => data.losers,
      2 => data.mostActive,
      _ => data.gainers,
    };
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Text(
              'Market Movers',
              style: TextStyle(
                fontSize: FCFontSizes.lg,
                fontWeight: FCFontWeights.semibold,
                color: c.cardForeground,
              ),
            ),
            FCSegmentedTabs(
              tabs: const ['Gainers', 'Losers', 'Most Active'],
              index: _moverTab,
              onChanged: (i) => setState(() => _moverTab = i),
            ),
          ]),
          FCCardContent(
            padding: const EdgeInsets.fromLTRB(8, 0, 8, 8),
            child: movers.isEmpty
                ? Padding(
                    padding: const EdgeInsets.symmetric(vertical: 32),
                    child: Center(
                      child: Text(
                        'No data available.',
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground),
                      ),
                    ),
                  )
                : Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (var i = 0; i < movers.length; i++)
                        _MoverRow(
                          stock: movers[i],
                          rank: i + 1,
                          onTap: () => _selectSymbol(movers[i].symbol),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  // ── Market News ───────────────────────────────────────────────────────────

  Widget _marketNews(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              children: [
                const Icon(Icons.newspaper, size: 18, color: FCPalette.blue600),
                const SizedBox(width: 8),
                Text(
                  'Market News',
                  style: TextStyle(
                    fontSize: FCFontSizes.lg,
                    fontWeight: FCFontWeights.semibold,
                    color: c.cardForeground,
                  ),
                ),
              ],
            ),
            Text('Real-time headlines · live feed',
                style: TextStyle(
                    fontSize: FCFontSizes.sm, color: c.mutedForeground)),
          ]),
          const FCCardContent(
            child: TradingViewWidget(
              kind: TradingViewKind.news,
              height: 480,
            ),
          ),
        ],
      ),
    );
  }

  // ── Formatting helpers ────────────────────────────────────────────────────

  static String _fmtPct(double n) =>
      '${n >= 0 ? '+' : '-'}${Fmt.fixed(n.abs(), 2)}%';

  static Color _changeColor(double n) =>
      n >= 0 ? FCPalette.green600 : FCPalette.red600;
}

// ── Shared private widgets ───────────────────────────────────────────────────

/// Maps the web's Tailwind sector colour names to the design-system palette.
Color _sectorColor(String name) {
  switch (name) {
    case 'blue':
      return FCPalette.blue600;
    case 'emerald':
      return FCPalette.green600;
    case 'violet':
      return FCPalette.purple600;
    case 'orange':
      return FCPalette.orange600;
    case 'amber':
      return FCPalette.yellow600;
    case 'indigo':
      return FCPalette.blue700;
    default: // slate / unknown
      return FCPalette.gray500;
  }
}

/// A circular brand logo from Financial Modeling Prep, falling back to a
/// coloured initial circle when the image fails to load. Mirrors the web `Logo`.
class _StockLogo extends StatelessWidget {
  const _StockLogo({required this.symbol, required this.size});

  final String symbol;
  final double size;

  static const _fallbackColors = <Color>[
    FCPalette.blue600,
    FCPalette.purple600,
    FCPalette.green600,
    FCPalette.orange600,
    FCPalette.red600,
    FCPalette.teal600,
    FCPalette.yellow600,
  ];

  Color get _fallbackColor {
    if (symbol.isEmpty) return _fallbackColors.first;
    return _fallbackColors[symbol.codeUnitAt(0) % _fallbackColors.length];
  }

  Widget _fallback() => Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: _fallbackColor,
          shape: BoxShape.circle,
        ),
        child: Text(
          symbol.isNotEmpty ? symbol.characters.first.toUpperCase() : '?',
          style: TextStyle(
            color: Colors.white,
            fontWeight: FCFontWeights.bold,
            fontSize: size * 0.38,
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return ClipOval(
      child: Container(
        width: size,
        height: size,
        color: Colors.white,
        child: Image.network(
          'https://financialmodelingprep.com/image-stock/$symbol.png',
          width: size,
          height: size,
          fit: BoxFit.contain,
          errorBuilder: (context, error, stackTrace) => _fallback(),
        ),
      ),
    ).withBorder(c.border);
  }
}

extension on Widget {
  /// Wraps a [ClipOval] logo in a matching circular border.
  Widget withBorder(Color color) => Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: color),
        ),
        child: this,
      );
}

/// A search-result / generic row: logo + symbol (bold) + name (muted).
class _SymbolLogoTile extends StatelessWidget {
  const _SymbolLogoTile({
    required this.symbol,
    required this.name,
    required this.onTap,
  });

  final String symbol;
  final String name;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            _StockLogo(symbol: symbol, size: 32),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    symbol,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.bold,
                      color: c.foreground,
                    ),
                  ),
                  if (name.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: FCFontSizes.xs,
                          color: c.mutedForeground),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A sector stock card: logo, % badge, symbol, name, price, daily change, H/L.
class _StockCard extends StatelessWidget {
  const _StockCard({required this.stock, required this.onTap});

  final StockQuote stock;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final pos = stock.changesPercentage >= 0;
    final changeColor =
        stock.change >= 0 ? FCPalette.green600 : FCPalette.red600;
    final badgeColor = pos ? FCPalette.green600 : FCPalette.red600;

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: c.background,
          borderRadius: FCRadii.lgAll,
          border: Border.all(color: c.border),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _StockLogo(symbol: stock.symbol, size: 40),
                const Spacer(),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: badgeColor.withValues(alpha: 0.12),
                    borderRadius: FCRadii.fullAll,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        pos
                            ? Icons.arrow_outward
                            : Icons.south_east,
                        size: 12,
                        color: badgeColor,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        '${Fmt.fixed(stock.changesPercentage.abs(), 2)}%',
                        style: TextStyle(
                          fontSize: FCFontSizes.xs,
                          fontWeight: FCFontWeights.semibold,
                          color: badgeColor,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              stock.symbol,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                fontWeight: FCFontWeights.bold,
                color: c.foreground,
                letterSpacing: 0.3,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              stock.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  fontSize: FCFontSizes.xs, color: c.mutedForeground),
            ),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.only(top: 10),
              decoration: BoxDecoration(
                border: Border(top: BorderSide(color: c.border)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    Fmt.money2(stock.price),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.bold,
                      color: c.foreground,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${stock.change >= 0 ? '+' : '-'}'
                    '${Fmt.money2(stock.change.abs())} today',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: FCFontSizes.xs,
                      fontWeight: FCFontWeights.medium,
                      color: changeColor,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Flexible(
                  child: Text(
                    'H: ${Fmt.money2(stock.high)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: FCFontSizes.xs,
                        color: c.mutedForeground),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  child: Text('|',
                      style: TextStyle(
                          fontSize: FCFontSizes.xs, color: c.border)),
                ),
                Flexible(
                  child: Text(
                    'L: ${Fmt.money2(stock.low)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: FCFontSizes.xs,
                        color: c.mutedForeground),
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

/// A market-mover row: rank, logo, symbol+name, price, % change (coloured).
class _MoverRow extends StatelessWidget {
  const _MoverRow({
    required this.stock,
    required this.rank,
    required this.onTap,
  });

  final StockQuote stock;
  final int rank;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final changeColor = stock.changesPercentage >= 0
        ? FCPalette.green600
        : FCPalette.red600;
    return InkWell(
      onTap: onTap,
      borderRadius: FCRadii.mdAll,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
        child: Row(
          children: [
            SizedBox(
              width: 20,
              child: Text(
                '$rank',
                textAlign: TextAlign.center,
                style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontFeatures: const [FontFeature.tabularFigures()],
                  color: c.mutedForeground,
                ),
              ),
            ),
            const SizedBox(width: 10),
            _StockLogo(symbol: stock.symbol, size: 32),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    stock.symbol,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.bold,
                      color: c.foreground,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    stock.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: FCFontSizes.xs,
                        color: c.mutedForeground),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  Fmt.money2(stock.price),
                  style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    fontWeight: FCFontWeights.bold,
                    color: c.foreground,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${stock.changesPercentage >= 0 ? '+' : '-'}'
                  '${Fmt.fixed(stock.changesPercentage.abs(), 2)}%',
                  style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    fontWeight: FCFontWeights.semibold,
                    color: changeColor,
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
