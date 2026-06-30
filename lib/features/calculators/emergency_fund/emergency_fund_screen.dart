import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'emergency_fund_logic.dart';

class EmergencyFundCalculatorScreen extends StatefulWidget {
  const EmergencyFundCalculatorScreen({super.key});

  @override
  State<EmergencyFundCalculatorScreen> createState() =>
      _EmergencyFundCalculatorScreenState();
}

class _EmergencyFundCalculatorScreenState
    extends State<EmergencyFundCalculatorScreen> {
  final _monthlyExpenses = TextEditingController();
  final _currentSavings = TextEditingController();
  String _targetType = 'months';
  final _targetValue = TextEditingController(text: '6');
  final _monthlySavings = TextEditingController();
  final _interestRate = TextEditingController(text: '4.5');
  String _savingsGoal = 'emergency';

  EmergencyFundResult? _result;
  String? _error;

  @override
  void dispose() {
    for (final c in [
      _monthlyExpenses,
      _currentSavings,
      _targetValue,
      _monthlySavings,
      _interestRate,
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
      _result = EmergencyFundCalculator.calculate(
        monthlyExpenses: parseNum(_monthlyExpenses.text),
        currentSavings: parseNum(_currentSavings.text),
        targetType: _targetType,
        targetValue: parseNum(_targetValue.text),
        monthlySavings: parseNum(_monthlySavings.text),
        interestRate: parseNum(_interestRate.text),
      );
    } on CalcException catch (e) {
      _error = e.message;
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final isMonths = _targetType == 'months';
    return CalculatorScaffold(
      icon: Icons.savings_outlined,
      title: 'Emergency Fund Calculator',
      description:
          'Calculate your emergency fund target and how long it will take to '
          'reach it',
      children: [
        FCResultGrid(columns: 2, children: [
          _num(_monthlyExpenses, 'Monthly Expenses (\$)', '5000'),
          _num(_currentSavings, 'Current Emergency Savings (\$)', '0'),
          FCSelectField<String>(
            label: 'Target Type',
            value: _targetType,
            items: const [
              FCSelectItem('months', 'Months of Expenses'),
              FCSelectItem('amount', 'Specific Dollar Amount'),
            ],
            onChanged: (v) => setState(() => _targetType = v),
          ),
          _num(
            _targetValue,
            isMonths ? 'Number of Months' : 'Target Amount (\$)',
            isMonths ? '6' : '30000',
            helper: isMonths
                ? 'Recommended: 3–6 months for most people'
                : 'Enter your desired fund total',
          ),
          _num(_monthlySavings, 'Monthly Savings Contribution (\$)', '500'),
          _num(
            _interestRate,
            'Savings Account APY (%)',
            '4.5',
            helper: 'HYSAs currently offer 4–5% APY',
          ),
          FCSelectField<String>(
            label: 'Goal Type',
            value: _savingsGoal,
            items: const [
              FCSelectItem('emergency', 'Emergency Fund'),
              FCSelectItem('vacation', 'Vacation Fund'),
              FCSelectItem('home', 'Home Down Payment'),
              FCSelectItem('car', 'Car Purchase'),
              FCSelectItem('other', 'Other Goal'),
            ],
            onChanged: (v) => setState(() => _savingsGoal = v),
          ),
        ]),
        const SizedBox(height: 20),
        FCButton(
          label: 'Calculate Emergency Fund',
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
      Text('Emergency Fund Analysis',
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: c.foreground)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: [
        FCResultTile(
          label: 'Target Emergency Fund',
          value: Fmt.money(r.targetAmount),
          valueColor: FCPalette.green600,
          sub: '${Fmt.fixed(r.targetMonths, 1)} months of expenses',
        ),
        FCResultTile(
          label: 'Still Need to Save',
          value: Fmt.money(r.stillNeeded),
          valueColor: FCPalette.red600,
          valueSize: 24,
        ),
        _progressTile(r.percentComplete),
        FCResultTile(
          label: 'Current Coverage',
          value: '${Fmt.fixed(r.monthsOfExpensesCovered, 1)} months',
          valueSize: 24,
        ),
        if (r.monthlyContribution > 0 && r.stillNeeded > 0) ...[
          FCResultTile(
            label: 'Time to Goal',
            value: _timeToGoalText(r.timeToGoal),
            valueColor: FCPalette.orange600,
            valueSize: 24,
          ),
          FCResultTile(
            label: 'Interest Earned',
            value: '\$${Fmt.fixed(r.projectedInterest, 2)}',
            valueColor: FCPalette.green600,
            valueSize: 24,
          ),
        ],
      ]),
      const SizedBox(height: 16),
      FCResultPanel(
        title: 'Tips',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: const [
            _Tip('Keep your fund in a high-yield savings account (HYSA)'),
            _Tip('Automate monthly contributions to stay consistent'),
            _Tip('Only use it for true emergencies — job loss, medical, car'),
            _Tip('Replenish it immediately after any withdrawal'),
            _Tip('Start with a \$1,000 starter fund if your goal feels far away'),
          ],
        ),
      ),
    ];
  }

  Widget _progressTile(double percentComplete) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text('Progress',
            style:
                TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground)),
        const SizedBox(height: 4),
        Text(
          Fmt.pct(percentComplete, 1),
          style: TextStyle(
            fontSize: 24,
            fontWeight: FCFontWeights.bold,
            color: FCPalette.blue600,
            height: 1.1,
          ),
        ),
        const SizedBox(height: 8),
        FCProgressBar(value: percentComplete, color: FCPalette.blue600),
      ],
    );
  }

  String _timeToGoalText(double timeToGoal) {
    final months = timeToGoal.toInt();
    final plural = timeToGoal != 1 ? 's' : '';
    final yrs = timeToGoal >= 12
        ? ' (${Fmt.fixed(timeToGoal / 12, 1)} yrs)'
        : '';
    return '$months month$plural$yrs';
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

class _Tip extends StatelessWidget {
  const _Tip(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('• ',
              style: TextStyle(
                  fontSize: FCFontSizes.sm, color: c.mutedForeground)),
          Expanded(
            child: Text(text,
                style: TextStyle(
                    fontSize: FCFontSizes.sm, color: c.mutedForeground)),
          ),
        ],
      ),
    );
  }
}
