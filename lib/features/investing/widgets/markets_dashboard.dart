import 'dart:convert';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../models/portfolio_models.dart';
import '../services/portfolio_service.dart';
import 'watchlist.dart';

enum _Status { idle, loading, ready, error }

/// One persisted portfolio-value snapshot.
class _TrendPoint {
  const _TrendPoint(this.date, this.value);
  final String date; // YYYY-MM-DD
  final double value;
}

/// Portfolio + charts dashboard, mirroring the web's `<MarketsDashboard>`.
/// A "Connect your portfolio" button runs Plaid (via [PortfolioService]); on a
/// non-empty response it renders an allocation donut, a persisted value trend
/// line, and a holdings table. The [Watchlist] is always rendered below.
class MarketsDashboard extends StatefulWidget {
  const MarketsDashboard({super.key});

  static const String _trendKey = 'finnacalc.portfolioTrend';
  static const List<Color> _colors = [
    Color(0xFF2563EB),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF06B6D4),
    Color(0xFFEC4899),
    Color(0xFF84CC16),
  ];

  @override
  State<MarketsDashboard> createState() => _MarketsDashboardState();
}

class _MarketsDashboardState extends State<MarketsDashboard> {
  _Status _status = _Status.idle;
  PortfolioResponse? _portfolio;
  String? _error;
  List<_TrendPoint> _trend = const [];

  String _money(double n, String currency, {int decimals = 0}) {
    return NumberFormat.simpleCurrency(
      locale: 'en_US',
      name: currency,
      decimalDigits: decimals,
    ).format(n);
  }

  Color _changeColor(BuildContext context, double n) {
    if (n > 0) return FCPalette.green600;
    if (n < 0) return FCPalette.red600;
    return context.colors.mutedForeground;
  }

  Future<void> _connect() async {
    setState(() {
      _status = _Status.loading;
      _error = null;
    });
    try {
      final res = await context.read<PortfolioService>().importHoldings();
      if (!mounted) return;
      if (res == null) {
        // User cancelled the Plaid flow.
        setState(() => _status = _Status.idle);
        return;
      }
      if (res.isEmpty) {
        setState(() {
          _error = 'No investment holdings were found on this account.';
          _status = _Status.error;
        });
        return;
      }
      final trend = await _recordTrend(res.totalValue);
      if (!mounted) return;
      setState(() {
        _portfolio = res;
        _trend = trend;
        _status = _Status.ready;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _status = _Status.error;
      });
    }
  }

  void _disconnect() {
    setState(() {
      _portfolio = null;
      _trend = const [];
      _error = null;
      _status = _Status.idle;
    });
  }

  /// Appends today's value to the persisted trend (replacing any same-day
  /// entry), caps the history to the last 90 points, and returns it.
  Future<List<_TrendPoint>> _recordTrend(double value) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(MarketsDashboard._trendKey);
    final list = <_TrendPoint>[];
    if (raw != null && raw.isNotEmpty) {
      try {
        final parsed = jsonDecode(raw);
        if (parsed is List) {
          for (final e in parsed) {
            if (e is Map &&
                e['date'] is String &&
                e['value'] is num) {
              list.add(_TrendPoint(
                e['date'] as String,
                (e['value'] as num).toDouble(),
              ));
            }
          }
        }
      } catch (_) {
        /* ignore malformed cache */
      }
    }

    final now = DateTime.now();
    final today =
        '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    final idx = list.indexWhere((p) => p.date == today);
    if (idx >= 0) {
      list[idx] = _TrendPoint(today, value);
    } else {
      list.add(_TrendPoint(today, value));
    }
    final capped =
        list.length > 90 ? list.sublist(list.length - 90) : list;

    final encoded = jsonEncode(
      [for (final p in capped) {'date': p.date, 'value': p.value}],
    );
    await prefs.setString(MarketsDashboard._trendKey, encoded);
    return capped;
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_status == _Status.ready && _portfolio != null)
          _portfolioView(context, _portfolio!)
        else
          _connectCard(context),
        const SizedBox(height: 24),
        const Watchlist(),
      ],
    );
  }

  // ───────────────────────── connect / empty / loading ─────────────────────

  Widget _connectCard(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.symmetric(vertical: 40, horizontal: 24),
        child: Column(
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: const BoxDecoration(
                color: FCPalette.blue50,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.trending_up,
                  size: 28, color: FCPalette.blue600),
            ),
            const SizedBox(height: 16),
            Text('Connect your portfolio',
                style: TextStyle(
                    fontSize: FCFontSizes.base,
                    fontWeight: FCFontWeights.semibold,
                    color: c.foreground)),
            const SizedBox(height: 6),
            Text(
              'Securely link your brokerage to see total value, asset '
              'allocation, holdings, and returns. The watchlist below works '
              'without connecting.',
              textAlign: TextAlign.center,
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  color: c.mutedForeground,
                  height: 1.5),
            ),
            const SizedBox(height: 20),
            if (_error != null) ...[
              FCCalloutBanner(
                background: FCPalette.red500.withValues(alpha: 0.1),
                border: FCPalette.red500.withValues(alpha: 0.3),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.error_outline,
                        size: 16, color: FCPalette.red600),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(_error!,
                          style: const TextStyle(
                              fontSize: FCFontSizes.xs,
                              color: FCPalette.red600)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            if (_status == _Status.loading)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 8),
                  Text('Importing holdings…',
                      style: TextStyle(
                          fontSize: FCFontSizes.sm, color: c.mutedForeground)),
                ],
              )
            else
              FCButton(
                label: 'Connect brokerage',
                icon: const Icon(Icons.add_circle_outline),
                onPressed: _connect,
              ),
            const SizedBox(height: 12),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.verified_user_outlined,
                    size: 14, color: c.mutedForeground),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    'Bank-level encryption · we never see your login',
                    style: TextStyle(
                        fontSize: FCFontSizes.xs, color: c.mutedForeground),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // ───────────────────────────── portfolio view ────────────────────────────

  Widget _portfolioView(BuildContext context, PortfolioResponse p) {
    return LayoutBuilder(builder: (context, constraints) {
      final wide = constraints.maxWidth >= 720;
      final summary = _summaryCard(context, p);
      final allocation = _allocationCard(context, p);
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (wide)
            IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(flex: 2, child: summary),
                  const SizedBox(width: 16),
                  Expanded(flex: 1, child: allocation),
                ],
              ),
            )
          else ...[
            summary,
            const SizedBox(height: 16),
            allocation,
          ],
          const SizedBox(height: 16),
          _holdingsCard(context, p),
        ],
      );
    });
  }

  Widget _summaryCard(BuildContext context, PortfolioResponse p) {
    final c = context.colors;
    final currency = p.currency;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: FCPalette.blue600,
                    borderRadius: FCRadii.smAll,
                  ),
                  child: const Icon(Icons.account_balance_wallet,
                      size: 16, color: Colors.white),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const FCCardTitle('Total Portfolio Value'),
                      const SizedBox(height: 4),
                      Text('Live portfolio · Plaid',
                          style: TextStyle(
                              fontSize: FCFontSizes.xs,
                              color: c.mutedForeground)),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                FCButton(
                  label: 'Disconnect',
                  variant: FCButtonVariant.outline,
                  size: FCButtonSize.sm,
                  icon: const Icon(Icons.refresh),
                  onPressed: _disconnect,
                ),
              ],
            ),
          ]),
          FCCardContent(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _money(p.totalValue, currency, decimals: 2),
                  style: TextStyle(
                      fontSize: FCFontSizes.xl3,
                      fontWeight: FCFontWeights.bold,
                      color: c.foreground),
                ),
                if (p.totalReturnPct != null) ...[
                  const SizedBox(height: 2),
                  Text(
                    '${p.totalReturn >= 0 ? '+' : ''}${_money(p.totalReturn, currency, decimals: 2)} '
                    '(${p.totalReturnPct!.toStringAsFixed(2)}%) total return',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        fontWeight: FCFontWeights.semibold,
                        color: _changeColor(context, p.totalReturn)),
                  ),
                ],
                const SizedBox(height: 16),
                FCResultGrid(columns: 3, spacing: 12, children: [
                  _miniTile(context, 'Cost basis',
                      _money(p.totalCostBasis, currency)),
                  _miniTile(context, 'Holdings', '${p.holdings.length}'),
                  _miniTile(context, 'Accounts', '${p.accountCount}'),
                ]),
                const SizedBox(height: 20),
                Text('Portfolio value trend',
                    style: TextStyle(
                        fontSize: FCFontSizes.xs, color: c.mutedForeground)),
                const SizedBox(height: 8),
                _trendChart(context, currency),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _miniTile(BuildContext context, String label, String value) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.mdAll,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style:
                  TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground)),
          const SizedBox(height: 4),
          Text(value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  fontSize: FCFontSizes.base,
                  fontWeight: FCFontWeights.bold,
                  color: c.foreground)),
        ],
      ),
    );
  }

  Widget _trendChart(BuildContext context, String currency) {
    final c = context.colors;
    if (_trend.length < 2) {
      return Container(
        height: 160,
        alignment: Alignment.center,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        decoration: BoxDecoration(
          borderRadius: FCRadii.lgAll,
          border: Border.all(
            color: c.border,
            style: BorderStyle.solid,
          ),
        ),
        child: Text(
          'Your value trend builds as you check in over time. Come back '
          'tomorrow to see it grow.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
        ),
      );
    }

    final spots = [
      for (var i = 0; i < _trend.length; i++)
        FlSpot(i.toDouble(), _trend[i].value),
    ];
    final values = _trend.map((p) => p.value).toList();
    var minY = values.reduce((a, b) => a < b ? a : b);
    var maxY = values.reduce((a, b) => a > b ? a : b);
    if (minY == maxY) {
      // Avoid a zero-height range so the line is visible.
      final pad = minY.abs() < 1 ? 1.0 : minY.abs() * 0.05;
      minY -= pad;
      maxY += pad;
    }

    return SizedBox(
      height: 160,
      child: LineChart(
        LineChartData(
          minX: 0,
          maxX: (_trend.length - 1).toDouble(),
          minY: minY,
          maxY: maxY,
          gridData: const FlGridData(show: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            show: true,
            topTitles:
                const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            leftTitles:
                const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            rightTitles:
                const AxisTitles(sideTitles: SideTitles(showTitles: false)),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                reservedSize: 22,
                interval: _bottomInterval(_trend.length),
                getTitlesWidget: (value, meta) {
                  final i = value.round();
                  if (i < 0 || i >= _trend.length) {
                    return const SizedBox.shrink();
                  }
                  return Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      _shortDate(_trend[i].date),
                      style: TextStyle(
                          fontSize: 11, color: c.mutedForeground),
                    ),
                  );
                },
              ),
            ),
          ),
          lineTouchData: LineTouchData(
            touchTooltipData: LineTouchTooltipData(
              getTooltipColor: (_) => c.background,
              getTooltipItems: (spots) => [
                for (final s in spots)
                  LineTooltipItem(
                    _money(s.y, currency, decimals: 2),
                    TextStyle(
                        fontSize: FCFontSizes.xs,
                        color: c.foreground,
                        fontWeight: FCFontWeights.medium),
                  ),
              ],
            ),
          ),
          lineBarsData: [
            LineChartBarData(
              spots: spots,
              isCurved: true,
              color: const Color(0xFF2563EB),
              barWidth: 2.5,
              dotData: const FlDotData(show: false),
            ),
          ],
        ),
      ),
    );
  }

  double _bottomInterval(int count) {
    if (count <= 1) return 1;
    final step = (count - 1) / 4;
    return step < 1 ? 1 : step;
  }

  String _shortDate(String iso) {
    final d = DateTime.tryParse(iso);
    if (d == null) return iso;
    return DateFormat('MMM d', 'en_US').format(d);
  }

  Widget _allocationCard(BuildContext context, PortfolioResponse p) {
    final c = context.colors;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const FCCardHeader(children: [FCCardTitle('Asset Allocation')]),
          FCCardContent(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  height: 160,
                  child: p.allocation.isEmpty
                      ? Center(
                          child: Text('No allocation data.',
                              style: TextStyle(
                                  fontSize: FCFontSizes.sm,
                                  color: c.mutedForeground)),
                        )
                      : PieChart(
                          PieChartData(
                            sectionsSpace: 2,
                            centerSpaceRadius: 42,
                            sections: [
                              for (var i = 0; i < p.allocation.length; i++)
                                PieChartSectionData(
                                  value: p.allocation[i].value,
                                  color: MarketsDashboard._colors[
                                      i % MarketsDashboard._colors.length],
                                  radius: 28,
                                  showTitle: false,
                                ),
                            ],
                          ),
                        ),
                ),
                const SizedBox(height: 12),
                for (var i = 0; i < p.allocation.length; i++) ...[
                  if (i > 0) const SizedBox(height: 6),
                  _allocationLegendRow(
                    context,
                    p.allocation[i],
                    MarketsDashboard._colors[
                        i % MarketsDashboard._colors.length],
                    p.totalValue,
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _allocationLegendRow(
      BuildContext context, AllocationSlice a, Color color, double total) {
    final c = context.colors;
    final pct = total > 0 ? (a.value / total * 100) : 0.0;
    return Row(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(a.type,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontSize: FCFontSizes.sm, color: c.foreground)),
        ),
        const SizedBox(width: 8),
        Text('${pct.toStringAsFixed(0)}%',
            style: TextStyle(
                fontSize: FCFontSizes.sm, color: c.mutedForeground)),
      ],
    );
  }

  Widget _holdingsCard(BuildContext context, PortfolioResponse p) {
    final c = context.colors;
    final currency = p.currency;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          const FCCardHeader(children: [FCCardTitle('My Holdings')]),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minWidth: MediaQuery.sizeOf(context).width - 48,
              ),
              child: DataTable(
                headingRowHeight: 36,
                dataRowMinHeight: 44,
                dataRowMaxHeight: 60,
                horizontalMargin: 16,
                columnSpacing: 20,
                headingRowColor: WidgetStatePropertyAll(
                  Color.alphaBlend(c.muted.withValues(alpha: 0.3), c.card),
                ),
                headingTextStyle: TextStyle(
                    fontSize: FCFontSizes.xs,
                    fontWeight: FCFontWeights.semibold,
                    color: c.mutedForeground),
                dataTextStyle:
                    TextStyle(fontSize: FCFontSizes.sm, color: c.foreground),
                columns: const [
                  DataColumn(label: Text('#')),
                  DataColumn(label: Text('Ticker')),
                  DataColumn(label: Text('Shares'), numeric: true),
                  DataColumn(label: Text('Avg cost'), numeric: true),
                  DataColumn(label: Text('Price'), numeric: true),
                  DataColumn(label: Text('Market value'), numeric: true),
                  DataColumn(label: Text('Total return'), numeric: true),
                  DataColumn(label: Text('Weight'), numeric: true),
                ],
                rows: [
                  for (var i = 0; i < p.holdings.length; i++)
                    _holdingRow(context, i, p.holdings[i], currency),
                  _totalsRow(context, p, currency),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  DataRow _holdingRow(
      BuildContext context, int i, PortfolioHolding h, String currency) {
    final c = context.colors;
    final retColor = _changeColor(context, h.totalReturn);
    return DataRow(cells: [
      DataCell(Text('${i + 1}',
          style: TextStyle(color: c.mutedForeground))),
      DataCell(
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(h.name,
                style: const TextStyle(fontWeight: FCFontWeights.bold)),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 200),
              child: Text(
                h.fullName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                    fontSize: FCFontSizes.xs, color: c.mutedForeground),
              ),
            ),
          ],
        ),
      ),
      DataCell(Text(Fmt.group(h.quantity))),
      DataCell(Text(_money(h.avgCost, currency, decimals: 2))),
      DataCell(Text(_money(h.price, currency, decimals: 2))),
      DataCell(Text(
        _money(h.value, currency, decimals: 2),
        style: const TextStyle(fontWeight: FCFontWeights.medium),
      )),
      DataCell(
        Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '${h.totalReturn >= 0 ? '+' : ''}${_money(h.totalReturn, currency, decimals: 2)}',
              style: TextStyle(
                  fontWeight: FCFontWeights.semibold, color: retColor),
            ),
            if (h.totalReturnPct != null)
              Text('${h.totalReturnPct!.toStringAsFixed(1)}%',
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: retColor)),
          ],
        ),
      ),
      DataCell(Text('${h.weight.toStringAsFixed(1)}%')),
    ]);
  }

  DataRow _totalsRow(
      BuildContext context, PortfolioResponse p, String currency) {
    final c = context.colors;
    final retColor = _changeColor(context, p.totalReturn);
    final bold = const TextStyle(fontWeight: FCFontWeights.semibold);
    return DataRow(
      color: WidgetStatePropertyAll(
        Color.alphaBlend(c.muted.withValues(alpha: 0.3), c.card),
      ),
      cells: [
        DataCell(Text('Total', style: bold)),
        const DataCell(Text('')),
        const DataCell(Text('')),
        const DataCell(Text('')),
        const DataCell(Text('')),
        DataCell(Text(_money(p.totalValue, currency, decimals: 2), style: bold)),
        DataCell(Text(
          '${p.totalReturn >= 0 ? '+' : ''}${_money(p.totalReturn, currency, decimals: 2)}',
          style: bold.copyWith(color: retColor),
        )),
        DataCell(Text('100%', style: bold)),
      ],
    );
  }
}
