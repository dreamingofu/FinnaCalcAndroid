import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../core/design_system/design_system.dart';
import 'tradingview_webview.dart';

/// Persisted ticker watchlist, mirroring the web's `<Watchlist>`. Each tile is a
/// TradingView mini chart with a remove button; an inline add field appends new
/// tickers. The list is stored in shared_preferences under [_storageKey].
class Watchlist extends StatefulWidget {
  const Watchlist({super.key});

  static const String _storageKey = 'finnacalc.watchlist';
  static const List<String> _defaults = [
    'AAPL',
    'TSLA',
    'NVDA',
    'MSFT',
    'AMZN',
    'META',
    'GOOGL',
    'BINANCE:BTCUSDT',
  ];

  @override
  State<Watchlist> createState() => _WatchlistState();
}

class _WatchlistState extends State<Watchlist> {
  List<String> _symbols = List.of(Watchlist._defaults);
  bool _adding = false;
  final _draft = TextEditingController();
  final _draftFocus = FocusNode();
  SharedPreferences? _prefs;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _draft.dispose();
    _draftFocus.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    _prefs = prefs;
    final raw = prefs.getString(Watchlist._storageKey);
    if (raw != null && raw.isNotEmpty) {
      try {
        final parsed = jsonDecode(raw);
        if (parsed is List && parsed.isNotEmpty) {
          final list = parsed.whereType<String>().toList();
          if (list.isNotEmpty && mounted) {
            setState(() => _symbols = list);
          }
        }
      } catch (_) {
        /* ignore malformed cache */
      }
    }
  }

  void _persist(List<String> next) {
    setState(() => _symbols = next);
    _prefs?.setString(Watchlist._storageKey, jsonEncode(next));
  }

  void _addSymbol() {
    final s = _draft.text.trim().toUpperCase();
    if (s.isEmpty) {
      setState(() => _adding = false);
      return;
    }
    if (!_symbols.contains(s)) {
      _persist([..._symbols, s]);
    }
    _draft.clear();
    setState(() => _adding = false);
  }

  void _remove(String s) => _persist(_symbols.where((x) => x != s).toList());

  void _startAdding() {
    setState(() => _adding = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _draftFocus.requestFocus();
    });
  }

  void _cancelAdding() {
    _draft.clear();
    setState(() => _adding = false);
  }

  @override
  Widget build(BuildContext context) {
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [_header(context)]),
          FCCardContent(child: _grid(context)),
        ],
      ),
    );
  }

  Widget _header(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        const Icon(Icons.star_border, size: 20, color: FCPalette.blue600),
        const SizedBox(width: 8),
        const Expanded(child: FCCardTitle('My Watchlist')),
        const SizedBox(width: 8),
        if (_adding)
          _addControls(context)
        else
          FCButton(
            label: 'Add',
            variant: FCButtonVariant.outline,
            size: FCButtonSize.sm,
            icon: const Icon(Icons.add),
            onPressed: _startAdding,
          ),
      ],
    );
  }

  Widget _addControls(BuildContext context) {
    return Flexible(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Flexible(
            child: SizedBox(
              width: 140,
              child: FCTextField(
                controller: _draft,
                focusNode: _draftFocus,
                hintText: 'Ticker e.g. AMD',
                textCapitalization: TextCapitalization.characters,
                textInputAction: TextInputAction.done,
                inputFormatters: [
                  TextInputFormatter.withFunction(
                    (oldValue, newValue) => newValue.copyWith(
                      text: newValue.text.toUpperCase(),
                    ),
                  ),
                ],
                onSubmitted: (_) => _addSymbol(),
              ),
            ),
          ),
          const SizedBox(width: 8),
          FCButton(
            label: 'Add',
            size: FCButtonSize.sm,
            onPressed: _addSymbol,
          ),
          const SizedBox(width: 6),
          FCButton(
            label: 'Cancel',
            variant: FCButtonVariant.ghost,
            size: FCButtonSize.sm,
            onPressed: _cancelAdding,
          ),
        ],
      ),
    );
  }

  Widget _grid(BuildContext context) {
    final c = context.colors;
    if (_symbols.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 32),
        child: Text(
          'Your watchlist is empty. Add a ticker to track it here.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground),
        ),
      );
    }

    return LayoutBuilder(builder: (context, constraints) {
      const spacing = 12.0;
      final cols = constraints.maxWidth >= 640
          ? 4
          : constraints.maxWidth >= 420
              ? 2
              : 1;
      final tileWidth =
          (constraints.maxWidth - spacing * (cols - 1)) / cols;
      return Wrap(
        spacing: spacing,
        runSpacing: spacing,
        children: [
          for (final s in _symbols)
            SizedBox(
              width: tileWidth > 0 ? tileWidth : constraints.maxWidth,
              child: _tile(context, s),
            ),
        ],
      );
    });
  }

  Widget _tile(BuildContext context, String symbol) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.lgAll,
      ),
      child: Stack(
        children: [
          TradingViewWidget(
            kind: TradingViewKind.mini,
            symbol: symbol,
            height: 140,
          ),
          Positioned(
            top: 4,
            right: 4,
            child: GestureDetector(
              onTap: () => _remove(symbol),
              behavior: HitTestBehavior.opaque,
              child: Container(
                width: 22,
                height: 22,
                decoration: BoxDecoration(
                  color: c.muted,
                  shape: BoxShape.circle,
                ),
                child: Icon(Icons.close, size: 13, color: c.mutedForeground),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
