import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'break_even_logic.dart';

class BreakEvenCalculatorScreen extends StatefulWidget {
  const BreakEvenCalculatorScreen({super.key});

  @override
  State<BreakEvenCalculatorScreen> createState() =>
      _BreakEvenCalculatorScreenState();
}

class _BreakEvenCalculatorScreenState extends State<BreakEvenCalculatorScreen> {
  final _fixedCosts = TextEditingController();
  final _variableCostPerUnit = TextEditingController();
  final _pricePerUnit = TextEditingController();
  final _targetProfitMargin = TextEditingController(text: '20');
  final _seasonalityFactor = TextEditingController(text: '0');
  String _salesMix = 'single';

  BreakEvenResult? _result;
  String? _error;

  // The raw target-margin string captured at calc time, mirroring the web's
  // direct `{targetProfitMargin}` interpolation into result labels.
  String _targetMarginLabel = '20';

  @override
  void dispose() {
    _fixedCosts.dispose();
    _variableCostPerUnit.dispose();
    _pricePerUnit.dispose();
    _targetProfitMargin.dispose();
    _seasonalityFactor.dispose();
    super.dispose();
  }

  String get _unitLabel => _salesMix == 'service' ? 'services' : 'units';
  String get _unitWord => _salesMix == 'service' ? 'Service' : 'Unit';
  String _cap(String s) =>
      s.isEmpty ? s : '${s[0].toUpperCase()}${s.substring(1)}';

  void _calculate() {
    setState(() {
      _result = null;
      _error = null;
      _targetMarginLabel =
          _targetProfitMargin.text.isEmpty ? '0' : _targetProfitMargin.text;
    });
    try {
      _result = BreakEvenCalculator.calculate(
        fixedCosts: parseNum(_fixedCosts.text),
        variableCostPerUnit: parseNum(_variableCostPerUnit.text),
        pricePerUnit: parseNum(_pricePerUnit.text),
        seasonalityFactor: parseNum(_seasonalityFactor.text),
        targetProfitMargin: parseNum(_targetProfitMargin.text),
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
      icon: Icons.calculate_outlined,
      title: 'Break-Even Point Calculator',
      description:
          'Calculate how many $_unitLabel you need to sell to break even and '
          'hit profit targets',
      children: [
        FCResultGrid(columns: 2, children: [
          FCTextField(
            controller: _fixedCosts,
            label: 'Fixed Costs per Month (\$)',
            hintText: '10000',
            helperText: 'Rent, salaries, insurance, etc.',
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          FCTextField(
            controller: _variableCostPerUnit,
            label: 'Variable Cost per $_unitWord (\$)',
            hintText: '25',
            helperText: 'Materials, direct labor per unit',
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          FCTextField(
            controller: _pricePerUnit,
            label: 'Selling Price per $_unitWord (\$)',
            hintText: '50',
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          FCSelectField<String>(
            label: 'Business Type',
            value: _salesMix,
            items: const [
              FCSelectItem('single', 'Single Product'),
              FCSelectItem('multiple', 'Multiple Products'),
              FCSelectItem('service', 'Service Business'),
            ],
            onChanged: (v) => setState(() => _salesMix = v),
          ),
          FCTextField(
            controller: _targetProfitMargin,
            label: 'Target Net Profit Margin (%)',
            hintText: '20',
            helperText: '% of revenue you want as net profit',
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
          FCTextField(
            controller: _seasonalityFactor,
            label: 'Seasonality Adjustment (%)',
            hintText: '0',
            helperText: '+ for peak season, − for off-season',
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
          ),
        ]),
        const SizedBox(height: 20),
        FCButton(
          label: 'Calculate Break-Even Point',
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

    final unitLabel = _unitLabel;
    final showTarget = r.adjustedCMValid && r.unitsForTargetProfit != null;
    final showSeasonal = parseNum(_seasonalityFactor.text) != 0;

    final tiles = <Widget>[
      FCResultTile(
        label: 'Break-Even ${_cap(unitLabel)}',
        value: '${Fmt.group(r.breakEvenUnits)} $unitLabel',
        valueColor: FCPalette.green600,
        valueSize: FCFontSizes.xl3,
      ),
      FCResultTile(
        label: 'Break-Even Revenue',
        value: Fmt.money0(r.breakEvenRevenue),
        valueColor: FCPalette.blue600,
      ),
      FCResultTile(
        label: 'Contribution Margin / unit',
        value: '\$${Fmt.fixed(r.contributionMargin, 2)}',
      ),
      FCResultTile(
        label: 'Contribution Margin Ratio',
        value: Fmt.pct(r.contributionMarginRatio, 1),
      ),
      if (showTarget) ...[
        FCResultTile(
          label: '${_cap(unitLabel)} for $_targetMarginLabel% Net Margin',
          value: '${Fmt.group(r.unitsForTargetProfit!)} $unitLabel',
          valueColor: FCPalette.purple600,
        ),
        FCResultTile(
          label: 'Revenue for Target Margin',
          value: Fmt.money0(r.targetProfitRevenue),
          valueColor: FCPalette.purple600,
        ),
        FCResultTile(
          label: 'Margin of Safety',
          value: Fmt.pct(r.marginOfSafety, 1),
          valueColor: FCPalette.orange600,
        ),
      ],
      if (showSeasonal) ...[
        FCResultTile(
          label: 'Seasonal Break-Even',
          value: '${Fmt.group(r.seasonalBreakEven)} $unitLabel',
          valueColor: FCPalette.teal600,
        ),
        if (r.seasonalTargetUnits != null)
          FCResultTile(
            label: 'Seasonal Target ${_cap(unitLabel)}',
            value: '${Fmt.group(r.seasonalTargetUnits!)} $unitLabel',
            valueColor: FCPalette.teal600,
          ),
      ],
    ];

    final unitWord = _salesMix == 'service' ? 'service' : 'unit';
    final explanation = showTarget
        ? ' To achieve a $_targetMarginLabel% net profit margin, sell '
            '${Fmt.group(r.unitsForTargetProfit!)} $unitLabel.'
        : ' Target margin is unachievable at this price and cost structure — '
            'reduce costs or raise price.';

    return [
      const SizedBox(height: 24),
      Text('Break-Even Analysis',
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: context.colors.foreground)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: tiles),
      const SizedBox(height: 16),
      FCResultPanel(
        child: RichText(
          text: TextSpan(
            style: TextStyle(
              fontSize: FCFontSizes.sm,
              color: context.colors.foreground.withValues(alpha: 0.8),
              height: 1.5,
            ),
            children: [
              const TextSpan(text: 'You need to sell '),
              TextSpan(
                text: '${Fmt.group(r.breakEvenUnits)} $unitLabel',
                style:
                    const TextStyle(fontWeight: FCFontWeights.bold),
              ),
              const TextSpan(
                  text: ' to cover all fixed costs. Each '),
              TextSpan(text: '$unitWord contributes '),
              TextSpan(
                text: '\$${Fmt.fixed(r.contributionMargin, 2)}',
                style:
                    const TextStyle(fontWeight: FCFontWeights.bold),
              ),
              TextSpan(
                  text: ' toward fixed costs '
                      '(${Fmt.pct(r.contributionMarginRatio, 1)} of price).'),
              TextSpan(text: explanation),
            ],
          ),
        ),
      ),
    ];
  }
}
