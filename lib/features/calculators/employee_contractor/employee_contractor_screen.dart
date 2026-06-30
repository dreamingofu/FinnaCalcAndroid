import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_segmented_tabs.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'employee_contractor_logic.dart';

class EmployeeContractorCalculatorScreen extends StatefulWidget {
  const EmployeeContractorCalculatorScreen({super.key});

  @override
  State<EmployeeContractorCalculatorScreen> createState() =>
      _EmployeeContractorCalculatorScreenState();
}

class _EmployeeContractorCalculatorScreenState
    extends State<EmployeeContractorCalculatorScreen> {
  final _salary = TextEditingController();
  final _contractorRate = TextEditingController();
  final _hoursPerWeek = TextEditingController(text: '40');
  final _weeksPerYear = TextEditingController(text: '50');

  int _breakdownTab = 0;

  EmployeeContractorResult? _result;
  String? _error;

  @override
  void dispose() {
    for (final c in [
      _salary,
      _contractorRate,
      _hoursPerWeek,
      _weeksPerYear,
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
      _result = EmployeeContractorCalculator.compare(
        annualSalary: parseNum(_salary.text),
        hourlyRate: parseNum(_contractorRate.text),
        hours: parseNum(_hoursPerWeek.text, fallback: 40),
        weeks: parseNum(_weeksPerYear.text, fallback: 50),
      );
    } on CalcException catch (e) {
      _error = e.message;
    }
    setState(() {});
  }

  /// `$${Math.round(n).toLocaleString()}`.
  String _dollar(num n) => Fmt.money0(n);

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return CalculatorScaffold(
      icon: Icons.group_outlined,
      title: 'Employee vs Contractor Calculator',
      description:
          'Compare the true total cost of employees vs contractors using 2024 '
          'tax rates and benefit benchmarks',
      children: [
        FCResultGrid(columns: 2, children: [
          _num(_salary, 'Employee Annual Salary (\$)', '60000'),
          _num(_contractorRate, 'Contractor Hourly Rate (\$)', '40'),
          _num(_hoursPerWeek, 'Hours per Week', '40'),
          _num(_weeksPerYear, 'Weeks per Year', '50'),
        ]),
        const SizedBox(height: 20),
        FCButton(
          label: 'Compare Costs',
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
    final c = context.colors;
    final hoursText = Fmt.group(parseNum(_hoursPerWeek.text, fallback: 40));
    final weeksText = Fmt.group(parseNum(_weeksPerYear.text, fallback: 50));
    final annualHours = parseNum(_hoursPerWeek.text, fallback: 40) *
        parseNum(_weeksPerYear.text, fallback: 50);

    return [
      const SizedBox(height: 24),
      Text('Cost Comparison',
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: c.foreground)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: [
        FCResultTile(
          label: 'Total Employee Cost',
          value: _dollar(r.employee.totalCost),
          valueColor: FCPalette.red600,
          sub: '+${Fmt.fixed(r.employee.burdenRate, 0)}% burden above salary',
        ),
        FCResultTile(
          label: 'Total Contractor Cost',
          value: _dollar(r.contractor.annualCost),
          valueColor: FCPalette.blue600,
          sub:
              '\$${Fmt.group(r.contractor.hourlyRate)}/hr × ${hoursText}h × ${weeksText}wks',
        ),
        FCResultTile(
          label: r.savings > 0 ? 'Contractor Saves' : 'Employee Saves',
          value: '${_dollar(r.savings.abs())} / yr',
          valueColor: r.savings > 0 ? FCPalette.green600 : FCPalette.orange600,
          valueSize: 24,
          sub:
              '${Fmt.fixed(r.savingsPercentage.abs(), 1)}% ${r.savings > 0 ? "cheaper" : "more expensive"} vs employee',
        ),
        FCResultTile(
          label: 'Equivalent Employee Hourly Rate',
          value: '\$${Fmt.fixed(r.contractor.equivalentHourly, 2)}/hr',
          valueSize: 24,
          sub: 'Total employee cost ÷ total hours',
        ),
      ]),
      const SizedBox(height: 16),
      FCSegmentedTabs(
        tabs: const ['Employee Breakdown', 'Contractor Details'],
        index: _breakdownTab,
        onChanged: (i) => setState(() => _breakdownTab = i),
      ),
      const SizedBox(height: 16),
      if (_breakdownTab == 0)
        _employeeBreakdown(r.employee)
      else
        ..._contractorDetails(r, hoursText, weeksText, annualHours),
    ];
  }

  Widget _employeeBreakdown(EmployeeBreakdown e) {
    final c = context.colors;
    return FCResultPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCResultRow(
              label: 'Base Salary',
              value: _dollar(e.salary),
              bold: true),
          const SizedBox(height: 8),
          Text('Benefits:',
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.medium,
                  color: c.mutedForeground)),
          const SizedBox(height: 4),
          _indentRow('Health / Dental / Vision', _dollar(e.healthDentalVision)),
          _indentRow('401(k) Match (3%)', _dollar(e.retirement401k)),
          _indentRow('PTO Value (15 days)', _dollar(e.ptoValue)),
          _indentRow('Life / Disability / Other', _dollar(e.otherBenefits)),
          FCResultRow(
              label: 'Total Benefits',
              value: _dollar(e.totalBenefits),
              topBorder: true),
          const SizedBox(height: 8),
          Text('Employer Taxes (2024):',
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.medium,
                  color: c.mutedForeground)),
          const SizedBox(height: 4),
          _indentRow(
              'Social Security (6.2%, up to \$168,600)', _dollar(e.employerSS)),
          _indentRow('Medicare (1.45%)', _dollar(e.employerMedicare)),
          _indentRow('FUTA (net 0.6% on first \$7k)', _dollar(e.futaNet)),
          _indentRow('SUTA (est. 2% on first \$7k)', _dollar(e.suta)),
          _indentRow('Workers Comp (est. 2%)', _dollar(e.workersComp)),
          FCResultRow(
            label: 'Total Employer Cost',
            value: _dollar(e.totalCost),
            bold: true,
            large: true,
            topBorder: true,
          ),
        ],
      ),
    );
  }

  List<Widget> _contractorDetails(
    EmployeeContractorResult r,
    String hoursText,
    String weeksText,
    double annualHours,
  ) {
    final c = context.colors;
    return [
      FCResultPanel(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            FCResultRow(
                label: 'Hourly Rate',
                value: '\$${Fmt.group(r.contractor.hourlyRate)}/hr'),
            const SizedBox(height: 8),
            FCResultRow(
                label: 'Annual Hours (${hoursText}h × ${weeksText}wks)',
                value: '${Fmt.group(annualHours)} hrs'),
            FCResultRow(
              label: 'Annual Contractor Cost',
              value: _dollar(r.contractor.annualCost),
              bold: true,
              topBorder: true,
            ),
            const SizedBox(height: 8),
            Text(
              'No payroll taxes, benefits, or workers comp required. Contractor '
              'is responsible for their own SE tax, insurance, and retirement.',
              style: TextStyle(
                  fontSize: FCFontSizes.xs, color: c.mutedForeground),
            ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      FCCalloutBanner(
        background:
            r.recommendation == 'contractor' ? FCPalette.green50 : FCPalette.blue50,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Recommendation',
                style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    fontWeight: FCFontWeights.medium,
                    color: c.foreground)),
            const SizedBox(height: 4),
            Text(
              r.recommendation == 'contractor'
                  ? 'Hiring a contractor saves ${_dollar(r.savings)} per year '
                      '(${Fmt.fixed(r.savingsPercentage, 1)}%). Best for '
                      'short-term, specialized, or variable-hour work.'
                  : 'An employee is ${_dollar(r.savings.abs())} cheaper per year '
                      'at this rate. Better for long-term, high-commitment roles '
                      'with training investment.',
              style: TextStyle(
                  fontSize: FCFontSizes.sm, color: c.mutedForeground),
            ),
          ],
        ),
      ),
    ];
  }

  Widget _indentRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(left: 16),
      child: FCResultRow(label: label, value: value),
    );
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
