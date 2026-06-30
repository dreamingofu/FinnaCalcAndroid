import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'profit_margin_logic.dart';

class ProfitMarginCalculatorScreen extends StatefulWidget {
  const ProfitMarginCalculatorScreen({super.key});

  @override
  State<ProfitMarginCalculatorScreen> createState() =>
      _ProfitMarginCalculatorScreenState();
}

class _ProfitMarginCalculatorScreenState
    extends State<ProfitMarginCalculatorScreen> {
  final _revenue = TextEditingController();
  final _costOfGoodsSold = TextEditingController();
  final _operatingExpenses = TextEditingController();
  final _interestExpenses = TextEditingController();
  final _taxExpenses = TextEditingController();

  ProfitMarginResult? _result;
  String? _error;

  static const _benchmarks = <_Benchmark>[
    _Benchmark('Retail', '20–50%', '2–6%'),
    _Benchmark('Software / SaaS', '70–90%', '15–25%'),
    _Benchmark('Restaurant', '60–70%', '3–7%'),
    _Benchmark('Manufacturing', '25–40%', '5–10%'),
    _Benchmark('Consulting', '60–75%', '15–25%'),
    _Benchmark('E-commerce', '30–50%', '3–8%'),
  ];

  @override
  void dispose() {
    for (final c in [
      _revenue,
      _costOfGoodsSold,
      _operatingExpenses,
      _interestExpenses,
      _taxExpenses,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  // dollar(n) -> `$` + toLocaleString({maximumFractionDigits: 0}).
  String _dollar(double n) => Fmt.money0(n);

  // pct(n) -> `${n.toFixed(2)}%`.
  String _pct(double n) => Fmt.pct(n, 2);

  // color(n) -> n >= 0 ? green : red.
  Color _color(double n) => n >= 0 ? FCPalette.green600 : FCPalette.red600;

  void _calculate() {
    setState(() {
      _result = null;
      _error = null;
    });
    try {
      _result = ProfitMarginCalculator.calculate(
        revenue: parseNum(_revenue.text),
        costOfGoodsSold: parseNum(_costOfGoodsSold.text),
        operatingExpenses: parseNum(_operatingExpenses.text),
        interestExpenses: parseNum(_interestExpenses.text),
        taxExpenses: parseNum(_taxExpenses.text),
      );
    } on CalcException catch (e) {
      _error = e.message;
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return CalculatorScaffold(
      icon: Icons.trending_up,
      title: 'Profit Margin Calculator',
      description:
          'Calculate gross, operating, EBT, and net profit margins with a '
          'full income statement breakdown',
      children: [
        FCResultGrid(columns: 2, children: [
          _num(_revenue, 'Total Revenue (\$)', '100000',
              'Total sales revenue for the period'),
          _num(_costOfGoodsSold, 'Cost of Goods Sold (COGS) (\$)', '60000',
              'Direct costs to produce goods/services'),
          _num(_operatingExpenses, 'Operating Expenses (\$)', '20000',
              'Rent, salaries, marketing, G&A'),
          _num(_interestExpenses, 'Interest Expenses (\$)', '2000',
              'Loan interest and financing costs'),
          _num(_taxExpenses, 'Income Tax Expense (\$)', '3000',
              'Actual taxes paid this period'),
        ]),
        const SizedBox(height: 20),
        FCButton(
          label: 'Calculate Profit Margins',
          fullWidth: true,
          size: FCButtonSize.lg,
          onPressed: _calculate,
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(_error!,
              style:
                  TextStyle(color: c.destructive, fontSize: FCFontSizes.sm)),
        ],
        ..._results(),
      ],
    );
  }

  List<Widget> _results() {
    final r = _result;
    if (r == null) return const [];
    final c = context.colors;
    return [
      const SizedBox(height: 24),
      Text('Profit Margin Analysis',
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: c.foreground)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: [
        FCResultTile(
          label: 'Gross Profit Margin',
          value: _pct(r.grossMargin),
          valueColor: _color(r.grossMargin),
          sub: '${_dollar(r.grossProfit)} gross profit',
          valueSize: FCFontSizes.xl3,
        ),
        FCResultTile(
          label: 'Operating Margin (EBIT)',
          value: _pct(r.operatingMargin),
          valueColor: _color(r.operatingMargin),
          sub: _dollar(r.operatingIncome),
          valueSize: FCFontSizes.xl2,
        ),
        FCResultTile(
          label: 'Pre-Tax Margin (EBT)',
          value: _pct(r.ebtMargin),
          valueColor: _color(r.ebtMargin),
          sub: _dollar(r.ebt),
          valueSize: FCFontSizes.xl2,
        ),
        FCResultTile(
          label: 'Net Profit Margin',
          value: _pct(r.netMargin),
          valueColor: _color(r.netMargin),
          sub: _dollar(r.netProfit),
          valueSize: FCFontSizes.xl2,
        ),
      ]),
      const SizedBox(height: 16),
      FCResultPanel(
        title: 'Income Statement',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            FCResultRow(
              label: 'Revenue',
              value: _dollar(r.totalRevenue),
              bold: true,
            ),
            FCResultRow(
              label: '− COGS',
              value: '(${_dollar(r.cogs)})',
              labelColor: c.mutedForeground,
              valueColor: c.mutedForeground,
            ),
            FCResultRow(
              label: 'Gross Profit',
              value: _dollar(r.grossProfit),
              valueColor: _color(r.grossProfit),
              topBorder: true,
            ),
            FCResultRow(
              label: '− Operating Expenses',
              value: '(${_dollar(r.opex)})',
              labelColor: c.mutedForeground,
              valueColor: c.mutedForeground,
            ),
            FCResultRow(
              label: 'Operating Income (EBIT)',
              value: _dollar(r.operatingIncome),
              valueColor: _color(r.operatingIncome),
              topBorder: true,
            ),
            if (r.interest > 0)
              FCResultRow(
                label: '− Interest Expenses',
                value: '(${_dollar(r.interest)})',
                labelColor: c.mutedForeground,
                valueColor: c.mutedForeground,
              ),
            if (r.interest > 0 || r.taxes > 0)
              FCResultRow(
                label: 'Pre-Tax Income (EBT)',
                value: _dollar(r.ebt),
                valueColor: _color(r.ebt),
                topBorder: true,
              ),
            if (r.taxes > 0)
              FCResultRow(
                label: '− Income Tax',
                value: '(${_dollar(r.taxes)})',
                labelColor: c.mutedForeground,
                valueColor: c.mutedForeground,
              ),
            FCResultRow(
              label: 'Net Profit',
              value: _dollar(r.netProfit),
              valueColor: _color(r.netProfit),
              bold: true,
              topBorder: true,
            ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      FCResultPanel(
        title: 'Industry Benchmarks',
        child: FCResultGrid(columns: 3, children: [
          for (final b in _benchmarks)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(b.name,
                    style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        fontWeight: FCFontWeights.medium,
                        color: c.foreground)),
                const SizedBox(height: 2),
                Text('Gross: ${b.gross}',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground)),
                Text('Net: ${b.net}',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground)),
              ],
            ),
        ]),
      ),
    ];
  }

  Widget _num(TextEditingController controller, String label, String hint,
      String helper) {
    return FCTextField(
      controller: controller,
      label: label,
      hintText: hint,
      helperText: helper,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
    );
  }
}

class _Benchmark {
  const _Benchmark(this.name, this.gross, this.net);
  final String name;
  final String gross;
  final String net;
}
