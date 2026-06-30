import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'cash_flow_logic.dart';

class CashFlowCalculatorScreen extends StatefulWidget {
  const CashFlowCalculatorScreen({super.key});

  @override
  State<CashFlowCalculatorScreen> createState() =>
      _CashFlowCalculatorScreenState();
}

class _CashFlowCalculatorScreenState extends State<CashFlowCalculatorScreen> {
  final _monthlyRevenue = TextEditingController();
  final _monthlyExpenses = TextEditingController();
  final _startingCash = TextEditingController();
  final _revenueGrowthRate = TextEditingController(text: '5');
  final _expenseGrowthRate = TextEditingController(text: '2');
  final _months = TextEditingController(text: '12');

  CashFlowResult? _result;

  @override
  void dispose() {
    for (final c in [
      _monthlyRevenue,
      _monthlyExpenses,
      _startingCash,
      _revenueGrowthRate,
      _expenseGrowthRate,
      _months,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _calculate() {
    // `Number.parseInt(months) || 12` — 0/empty/invalid falls back to 12.
    var monthsParsed = parseInt(_months.text, fallback: 12);
    if (monthsParsed == 0) monthsParsed = 12;

    final result = CashFlowCalculator.project(
      monthlyRevenue: parseNum(_monthlyRevenue.text),
      monthlyExpenses: parseNum(_monthlyExpenses.text),
      startingCash: parseNum(_startingCash.text),
      revenueGrowthRate: parseNum(_revenueGrowthRate.text),
      expenseGrowthRate: parseNum(_expenseGrowthRate.text),
      months: monthsParsed,
    );
    setState(() => _result = result);
  }

  /// `fmtK` — transcribed exactly: >=1000 -> like 25.0k with sign, else full
  /// grouped value with sign.
  String _fmtK(double n) {
    if (n.abs() >= 1000) {
      return '${n < 0 ? "-" : ""}\$${Fmt.fixed(n.abs() / 1000, 1)}k';
    }
    return '${n < 0 ? "-\$" : "\$"}${Fmt.group(n.abs())}';
  }

  @override
  Widget build(BuildContext context) {
    return CalculatorScaffold(
      icon: Icons.trending_up,
      title: 'Cash Flow Projector',
      description:
          'Project business cash flow with separate revenue and expense '
          'growth rates',
      children: [
        FCResultGrid(columns: 2, children: [
          _num(_monthlyRevenue, 'Starting Monthly Revenue (\$)', '25000'),
          _num(_monthlyExpenses, 'Starting Monthly Expenses (\$)', '20000'),
          _num(_startingCash, 'Starting Cash Balance (\$)', '50000'),
          _num(_months, 'Projection Period (months)', '12'),
          _num(
            _revenueGrowthRate,
            'Monthly Revenue Growth (%)',
            '5',
            helper: 'Month-over-month revenue growth rate',
          ),
          _num(
            _expenseGrowthRate,
            'Monthly Expense Growth (%)',
            '2',
            helper: 'Month-over-month expense growth rate',
          ),
        ]),
        const SizedBox(height: 20),
        FCButton(
          label: 'Calculate Cash Flow Projection',
          fullWidth: true,
          size: FCButtonSize.lg,
          onPressed: _calculate,
        ),
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
      Text(
        'Cash Flow Projection',
        style: TextStyle(
          fontSize: FCFontSizes.lg,
          fontWeight: FCFontWeights.semibold,
          color: c.foreground,
        ),
      ),
      const SizedBox(height: 16),
      FCResultGrid(columns: 3, children: [
        FCResultTile(
          label: 'Final Cash Balance',
          value: Fmt.money(r.finalCash),
          valueColor: r.finalCash >= 0 ? FCPalette.green600 : FCPalette.red600,
          valueSize: 24,
        ),
        FCResultTile(
          label: 'Total Revenue',
          value: Fmt.money(r.totalRevenue),
          valueColor: FCPalette.blue600,
          valueSize: 24,
        ),
        FCResultTile(
          label: 'Net Cash Flow',
          value: Fmt.money(r.netCashFlow),
          valueColor: r.netCashFlow >= 0 ? FCPalette.green600 : FCPalette.red600,
          valueSize: 24,
        ),
      ]),
      if (r.negativeMonths > 0) ...[
        const SizedBox(height: 16),
        FCCalloutBanner(
          background: FCPalette.yellow50,
          border: FCPalette.yellow200,
          child: RichText(
            text: TextSpan(
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: FCPalette.yellow800,
                height: 1.4,
              ),
              children: [
                const TextSpan(
                  text: 'Cash runway warning: ',
                  style: TextStyle(fontWeight: FCFontWeights.bold),
                ),
                TextSpan(
                  text:
                      '${r.negativeMonths} month${r.negativeMonths != 1 ? "s" : ""} '
                      'with negative cumulative cash balance.'
                      '${r.breakEvenMonth != null ? " Cash turns positive in Month ${r.breakEvenMonth}." : ""}',
                ),
              ],
            ),
          ),
        ),
      ],
      const SizedBox(height: 16),
      FCResultPanel(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Monthly Breakdown',
              style: TextStyle(
                fontSize: FCFontSizes.base,
                fontWeight: FCFontWeights.semibold,
                color: c.foreground,
              ),
            ),
            const SizedBox(height: 12),
            _tableHeader(c),
            const SizedBox(height: 8),
            SizedBox(
              height: 240,
              child: SingleChildScrollView(
                child: Column(
                  children: [
                    for (final m in r.projections) _tableRow(c, m),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    ];
  }

  Widget _tableHeader(FCColors c) {
    Widget cell(String t) => Expanded(
          child: Text(
            t,
            style: TextStyle(
              fontSize: FCFontSizes.xs,
              fontWeight: FCFontWeights.medium,
              color: c.mutedForeground,
            ),
          ),
        );
    return Row(children: [
      cell('Month'),
      cell('Revenue'),
      cell('Expenses'),
      cell('Cash Balance'),
    ]);
  }

  Widget _tableRow(FCColors c, CashFlowRow m) {
    final negative = m.cumulativeCash < 0;
    final rowColor = negative ? FCPalette.red600 : null;
    Widget cell(String t, {Color? color, FontWeight? weight}) => Expanded(
          child: Text(
            t,
            style: TextStyle(
              fontSize: FCFontSizes.sm,
              color: color ?? rowColor ?? c.foreground,
              fontWeight: weight,
            ),
          ),
        );
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 2),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: c.border.withValues(alpha: 0.4)),
        ),
      ),
      child: Row(children: [
        cell('Month ${m.month}'),
        cell(_fmtK(m.revenue)),
        cell(_fmtK(m.expenses)),
        cell(
          _fmtK(m.cumulativeCash),
          color: negative ? FCPalette.red600 : FCPalette.green600,
          weight: FCFontWeights.medium,
        ),
      ]),
    );
  }

  Widget _num(
    TextEditingController controller,
    String label,
    String hint, {
    String? helper,
  }) {
    return FCTextField(
      controller: controller,
      label: label,
      hintText: hint,
      helperText: helper,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
    );
  }
}
