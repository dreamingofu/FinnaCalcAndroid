import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_segmented_tabs.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'startup_cost_logic.dart';

class StartupCostCalculatorScreen extends StatefulWidget {
  const StartupCostCalculatorScreen({super.key});

  @override
  State<StartupCostCalculatorScreen> createState() =>
      _StartupCostCalculatorScreenState();
}

class _StartupCostCalculatorScreenState
    extends State<StartupCostCalculatorScreen> {
  int _tab = 0;

  // Cost selects/inputs
  String? _businessType;
  final _equipment = TextEditingController();
  final _inventory = TextEditingController();
  final _marketing = TextEditingController();
  final _legal = TextEditingController();
  final _rent = TextEditingController();
  final _utilities = TextEditingController();
  final _insurance = TextEditingController();
  final _permits = TextEditingController();
  final _website = TextEditingController();
  final _employees = TextEditingController();
  final _salaries = TextEditingController();
  final _workingCapital = TextEditingController();
  final _other = TextEditingController();

  // Funding inputs
  final _personalSavings = TextEditingController();
  final _loanAmount = TextEditingController();
  final _investorFunding = TextEditingController();

  StartupCostResult? _result;

  @override
  void dispose() {
    for (final c in [
      _equipment,
      _inventory,
      _marketing,
      _legal,
      _rent,
      _utilities,
      _insurance,
      _permits,
      _website,
      _employees,
      _salaries,
      _workingCapital,
      _other,
      _personalSavings,
      _loanAmount,
      _investorFunding,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _onTabChanged(int i) => setState(() {
        _result = null; // Clear previous results when changing tabs
        _tab = i;
      });

  void _loadTemplate() {
    final type = _businessType;
    if (type == null) return;
    final template = StartupCostCalculator.businessTemplates[type];
    if (template == null) return;
    setState(() {
      _equipment.text = _intStr(template.equipment);
      _inventory.text = _intStr(template.inventory);
      _marketing.text = _intStr(template.marketing);
      _legal.text = _intStr(template.legal);
      _rent.text = _intStr(template.rent);
      _utilities.text = _intStr(template.utilities);
      _insurance.text = _intStr(template.insurance);
      _permits.text = _intStr(template.permits);
      _website.text = _intStr(template.website);
      _workingCapital.text = _intStr(template.workingCapital);
      // Clear results when loading template
      _result = null;
    });
  }

  // The web stores template numbers as integers, so `.toString()` yields no
  // decimal point.
  String _intStr(double v) => v.toStringAsFixed(0);

  void _calculate() {
    setState(() {
      _result = StartupCostCalculator.calculate(
        equipment: parseNum(_equipment.text),
        inventory: parseNum(_inventory.text),
        marketing: parseNum(_marketing.text),
        legal: parseNum(_legal.text),
        rent: parseNum(_rent.text),
        utilities: parseNum(_utilities.text),
        insurance: parseNum(_insurance.text),
        other: parseNum(_other.text),
        employees: parseNum(_employees.text),
        salaries: parseNum(_salaries.text),
        permits: parseNum(_permits.text),
        website: parseNum(_website.text),
        workingCapital: parseNum(_workingCapital.text),
        personalSavings: parseNum(_personalSavings.text),
        loanAmount: parseNum(_loanAmount.text),
        investorFunding: parseNum(_investorFunding.text),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return CalculatorScaffold(
      icon: Icons.apartment,
      title: 'Enhanced Startup Cost Calculator',
      description: 'Comprehensive startup cost estimation with funding analysis',
      children: [
        FCSegmentedTabs(
          tabs: const ['Startup Costs', 'Funding Sources'],
          index: _tab,
          onChanged: _onTabChanged,
        ),
        const SizedBox(height: 20),
        if (_tab == 0) ..._costsTab() else ..._fundingTab(),
        const SizedBox(height: 20),
        FCButton(
          label: 'Calculate Comprehensive Startup Costs',
          fullWidth: true,
          size: FCButtonSize.lg,
          icon: const Icon(Icons.calculate_outlined, size: 16),
          onPressed: _calculate,
        ),
        ..._results(),
      ],
    );
  }

  List<Widget> _costsTab() {
    return [
      Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: FCSelectField<String>(
              label: 'Business Type',
              value: _businessType,
              hintText: 'Select business type',
              items: const [
                FCSelectItem('retail', 'Retail Store'),
                FCSelectItem('restaurant', 'Restaurant'),
                FCSelectItem('service', 'Service Business'),
                FCSelectItem('online', 'Online Business'),
                FCSelectItem('manufacturing', 'Manufacturing'),
                FCSelectItem('consulting', 'Consulting'),
              ],
              onChanged: (v) => setState(() => _businessType = v),
            ),
          ),
          if (_businessType != null) ...[
            const SizedBox(width: 16),
            FCButton(
              label: 'Load Template',
              variant: FCButtonVariant.outline,
              onPressed: _loadTemplate,
            ),
          ],
        ],
      ),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: [
        _num(_equipment, 'Equipment & Technology (\$)', '15000'),
        _num(_inventory, 'Initial Inventory (\$)', '10000'),
        _num(_marketing, 'Marketing & Advertising (\$)', '5000'),
        _num(_legal, 'Legal & Professional Fees (\$)', '3000'),
        _num(_rent, 'First 3 Months Rent (\$)', '9000'),
        _num(_utilities, 'Utilities Setup (\$)', '1500'),
        _num(_insurance, 'Insurance (Annual) (\$)', '2400'),
        _num(_permits, 'Permits & Licenses (\$)', '1500'),
        _num(_website, 'Website & Digital Setup (\$)', '3000'),
        _num(_employees, 'Employee Setup Costs (\$)', '2000'),
        _num(_salaries, 'First 3 Months Salaries (\$)', '15000'),
        _num(_workingCapital, 'Working Capital (\$)', '10000'),
        _num(_other, 'Other Expenses (\$)', '2000'),
      ]),
    ];
  }

  List<Widget> _fundingTab() {
    return [
      FCResultGrid(columns: 2, children: [
        _num(_personalSavings, 'Personal Savings (\$)', '25000'),
        _num(_loanAmount, 'Business Loan (\$)', '50000'),
        _num(_investorFunding, 'Investor Funding (\$)', '100000'),
      ]),
    ];
  }

  List<Widget> _results() {
    final r = _result;
    if (r == null) return const [];
    final c = context.colors;

    final progress = StartupCostCalculator.progressValue(
        r.totalFunding, r.totalWithBuffer);

    return [
      const SizedBox(height: 24),
      Text('Your Comprehensive Startup Analysis',
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: FCPalette.blue700)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 3, children: [
        FCResultTile(
          label: 'Total Startup Costs',
          value: Fmt.money(r.totalCosts),
          valueColor: FCPalette.green600,
          valueSize: FCFontSizes.xl3,
        ),
        FCResultTile(
          label: 'Recommended Total (with 20% buffer)',
          value: Fmt.money(r.totalWithBuffer),
          valueColor: FCPalette.blue600,
          valueSize: FCFontSizes.xl2,
        ),
        FCResultTile(
          label: 'Total Funding Available',
          value: Fmt.money(r.totalFunding),
          valueColor: FCPalette.purple600,
          valueSize: FCFontSizes.xl2,
        ),
      ]),
      const SizedBox(height: 24),
      FCResultPanel(
        title: 'Funding Analysis:',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            FCResultRow(
                label: 'Required Capital:',
                value: Fmt.money(r.totalWithBuffer),
                bold: true),
            const SizedBox(height: 8),
            FCResultRow(
                label: 'Available Funding:',
                value: Fmt.money(r.totalFunding),
                bold: true),
            FCResultRow(
              label: 'Funding Gap:',
              value:
                  '${r.fundingGap > 0 ? "-" : "+"}\$${Fmt.group(r.fundingGap.abs())}',
              valueColor: r.fundingGap > 0 ? FCPalette.red600 : FCPalette.green600,
              bold: true,
              topBorder: true,
            ),
            const SizedBox(height: 8),
            FCProgressBar(value: progress.isFinite ? progress : 0),
            const SizedBox(height: 8),
            Text(
              r.fundingGap > 0
                  ? 'You need an additional \$${Fmt.group(r.fundingGap)} in funding'
                  : 'You have sufficient funding with \$${Fmt.group(r.fundingGap.abs())} surplus',
              style: TextStyle(
                  fontSize: FCFontSizes.xs, color: c.mutedForeground),
            ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      FCResultPanel(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Icon(Icons.pie_chart_outline,
                    size: 16, color: c.foreground),
                const SizedBox(width: 8),
                Text('Detailed Cost Breakdown:',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        fontWeight: FCFontWeights.semibold,
                        color: c.foreground)),
              ],
            ),
            const SizedBox(height: 12),
            for (final category in r.costCategories) ...[
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Row(
                  children: [
                    Container(
                      width: 12,
                      height: 12,
                      decoration: BoxDecoration(
                        color: _hexColor(category.color),
                        shape: BoxShape.circle,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text('${category.name}:',
                          style: TextStyle(
                              fontSize: FCFontSizes.sm, color: c.foreground)),
                    ),
                    const SizedBox(width: 8),
                    Text('\$${Fmt.group(category.value)}',
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            fontWeight: FCFontWeights.semibold,
                            color: c.foreground)),
                    const SizedBox(width: 8),
                    Text(
                        '(${Fmt.fixed(category.value / r.totalCosts * 100, 1)}%)',
                        style: TextStyle(
                            fontSize: FCFontSizes.xs,
                            color: c.mutedForeground)),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    ];
  }

  Color _hexColor(String hex) {
    final cleaned = hex.replaceFirst('#', '');
    return Color(int.parse('FF$cleaned', radix: 16));
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
