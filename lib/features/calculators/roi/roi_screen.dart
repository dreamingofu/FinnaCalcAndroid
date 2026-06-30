import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'roi_logic.dart';

class RoiCalculatorScreen extends StatefulWidget {
  const RoiCalculatorScreen({super.key});

  @override
  State<RoiCalculatorScreen> createState() => _RoiCalculatorScreenState();
}

class _RoiCalculatorScreenState extends State<RoiCalculatorScreen> {
  final _initialInvestment = TextEditingController();
  final _finalValue = TextEditingController();
  final _timeHorizon = TextEditingController();
  String _calculationType = 'annualized';
  String _investmentType = 'stocks';
  final _dividendYield = TextEditingController(text: '0');
  final _inflationRate = TextEditingController(text: '3.0');
  final _taxRate = TextEditingController(text: '20');

  RoiResult? _result;
  String? _error;

  @override
  void dispose() {
    for (final c in [
      _initialInvestment,
      _finalValue,
      _timeHorizon,
      _dividendYield,
      _inflationRate,
      _taxRate,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _calculate() {
    setState(() {
      _result = null;
      _error = null;
    });
    try {
      _result = RoiCalculator.calculate(
        initial: parseNum(_initialInvestment.text),
        finalValue: parseNum(_finalValue.text),
        time: parseNum(_timeHorizon.text, fallback: 1),
        calculationType: _calculationType,
        dividend: parseNum(_dividendYield.text),
        inflation: parseNum(_inflationRate.text),
        tax: parseNum(_taxRate.text),
      );
    } on CalcException catch (e) {
      _error = e.message;
    }
    setState(() {});
  }

  /// Mirrors JS `${n}` default number-to-string (no trailing `.0` for ints).
  String _plainNum(double n) {
    if (n == n.roundToDouble() && n.isFinite) return n.toInt().toString();
    return Fmt.group(n);
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return CalculatorScaffold(
      icon: Icons.trending_up,
      title: 'Return on Investment (ROI) Calculator',
      description: 'Calculate the return on your investments and business projects',
      children: [
        FCResultGrid(columns: 2, children: [
          FCSelectField<String>(
            label: 'Calculation Type',
            value: _calculationType,
            items: const [
              FCSelectItem('simple', 'Simple ROI (total %)'),
              FCSelectItem('annualized', 'Annualized ROI / CAGR'),
            ],
            onChanged: (v) => setState(() => _calculationType = v),
          ),
          FCSelectField<String>(
            label: 'Investment Type',
            value: _investmentType,
            items: const [
              FCSelectItem('stocks', 'Stocks / ETFs'),
              FCSelectItem('realestate', 'Real Estate'),
              FCSelectItem('business', 'Business Investment'),
              FCSelectItem('bonds', 'Bonds'),
              FCSelectItem('crypto', 'Cryptocurrency'),
              FCSelectItem('other', 'Other'),
            ],
            onChanged: (v) => setState(() => _investmentType = v),
          ),
          _num(_initialInvestment, 'Initial Investment (\$)', '10000'),
          _num(_finalValue, 'Final Value (\$)', '15000'),
          _num(_timeHorizon, 'Time Period (years)', '5'),
          _num(_dividendYield, 'Annual Dividend / Income Yield (%)', '2.0'),
          _num(_inflationRate, 'Expected Inflation Rate (%)', '3.0'),
          _num(_taxRate, 'Tax Rate on Gains (%)', '20'),
        ]),
        const SizedBox(height: 20),
        FCButton(
          label: 'Calculate ROI',
          fullWidth: true,
          size: FCButtonSize.lg,
          onPressed: _calculate,
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(_error!,
              style: TextStyle(color: c.destructive, fontSize: FCFontSizes.sm)),
        ],
        ..._results(),
      ],
    );
  }

  List<Widget> _results() {
    final r = _result;
    if (r == null) return const [];
    final isAnnualized = _calculationType == 'annualized';
    return [
      const SizedBox(height: 24),
      Text('ROI Analysis',
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: context.colors.foreground)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: [
        FCResultTile(
          label: isAnnualized ? 'CAGR (Annualized ROI)' : 'Simple ROI (Total)',
          value: Fmt.pct(r.displayedROI, 2),
          valueColor: r.displayedROI >= 0 ? FCPalette.green600 : FCPalette.red600,
        ),
        FCResultTile(
          label: 'Total Return',
          value: Fmt.money0(r.totalReturn),
          valueColor: r.totalReturn >= 0 ? FCPalette.green600 : FCPalette.red600,
        ),
        FCResultTile(
          label: 'Real ROI (inflation-adjusted, Fisher eq.)',
          value: Fmt.pct(r.realROI, 2),
          valueColor: r.realROI >= 0 ? FCPalette.blue600 : FCPalette.red600,
        ),
        FCResultTile(
          label: 'After-Tax Return',
          value: Fmt.money2(r.afterTaxReturn),
          valueColor:
              r.afterTaxReturn >= 0 ? FCPalette.blue600 : FCPalette.red600,
        ),
      ]),
      const SizedBox(height: 16),
      FCResultPanel(
        title: 'Full Summary',
        child: FCResultGrid(columns: 2, children: [
          FCResultRow(label: 'Initial Investment', value: Fmt.money(r.initial)),
          FCResultRow(label: 'Final Value', value: Fmt.money(r.finalValue)),
          FCResultRow(label: 'Dividend Income', value: Fmt.money2(r.dividendIncome)),
          FCResultRow(
            label: 'Total Taxes',
            value: Fmt.money2(r.totalTaxes),
            valueColor: FCPalette.red600,
          ),
          FCResultRow(label: "Real Value (today's \$)", value: Fmt.money2(r.realValue)),
          FCResultRow(
            label: 'Investment Period',
            value: '${_plainNum(r.time)} yr${r.time != 1 ? 's' : ''}',
          ),
        ]),
      ),
    ];
  }

  Widget _num(TextEditingController controller, String label, String hint) {
    return FCTextField(
      controller: controller,
      label: label,
      hintText: hint,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
    );
  }
}
