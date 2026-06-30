import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_segmented_tabs.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'pricing_logic.dart';

class PricingCalculatorScreen extends StatefulWidget {
  const PricingCalculatorScreen({super.key});

  @override
  State<PricingCalculatorScreen> createState() =>
      _PricingCalculatorScreenState();
}

class _PricingCalculatorScreenState extends State<PricingCalculatorScreen> {
  int _tab = 0; // 0 = Service, 1 = Product

  // Service Pricing
  final _hourlyRate = TextEditingController();
  final _hoursPerWeek = TextEditingController();
  final _weeksPerYear = TextEditingController(text: '50');
  final _expenses = TextEditingController();
  final _profitMargin = TextEditingController(text: '20'); // collected, unused
  final _desiredSalary = TextEditingController();
  final _taxRate = TextEditingController(text: '25');
  String? _industryType;

  // Product Pricing
  final _productCost = TextEditingController();
  final _productMargin = TextEditingController(text: '50');
  final _competitorPrice = TextEditingController();
  final _volumeDiscount = TextEditingController();
  final _shippingCost = TextEditingController();

  ServicePricingResult? _serviceResult;
  ProductPricingResult? _productResult;
  String? _error;

  @override
  void dispose() {
    for (final c in [
      _hourlyRate,
      _hoursPerWeek,
      _weeksPerYear,
      _expenses,
      _profitMargin,
      _desiredSalary,
      _taxRate,
      _productCost,
      _productMargin,
      _competitorPrice,
      _volumeDiscount,
      _shippingCost,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _clearResults() {
    _serviceResult = null;
    _productResult = null;
    _error = null;
  }

  void _onTabChanged(int i) => setState(() {
        _tab = i;
        _clearResults();
      });

  /// Mirrors JS `Number.parseFloat(text) || fallback`: empty, invalid, NaN, and
  /// 0 all fall through to [fallback].
  double _orDefault(TextEditingController c, double fallback) {
    final v = parseNum(c.text);
    return v != 0 ? v : fallback;
  }

  void _calculateService() {
    setState(_clearResults);
    try {
      _serviceResult = PricingCalculator.service(
        rate: parseNum(_hourlyRate.text),
        hours: parseNum(_hoursPerWeek.text),
        weeks: _orDefault(_weeksPerYear, 50),
        annualExpenses: parseNum(_expenses.text),
        salary: parseNum(_desiredSalary.text),
        tax: _orDefault(_taxRate, 25),
        industryType: _industryType,
      );
    } on CalcException catch (e) {
      _error = e.message;
    }
    setState(() {});
  }

  void _calculateProduct() {
    setState(_clearResults);
    try {
      _productResult = PricingCalculator.product(
        cost: parseNum(_productCost.text),
        margin: _orDefault(_productMargin, 50),
        competitor: parseNum(_competitorPrice.text),
        discount: parseNum(_volumeDiscount.text),
        shipping: parseNum(_shippingCost.text),
      );
    } on CalcException catch (e) {
      _error = e.message;
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return CalculatorScaffold(
      icon: Icons.attach_money,
      title: 'Advanced Pricing Calculator',
      description:
          'Strategic pricing with competitive analysis and industry benchmarks',
      children: [
        FCSegmentedTabs(
          tabs: const ['Service Pricing', 'Product Pricing'],
          index: _tab,
          onChanged: _onTabChanged,
        ),
        const SizedBox(height: 20),
        if (_tab == 0) ..._serviceInputs() else ..._productInputs(),
        const SizedBox(height: 20),
        FCButton(
          label: _tab == 0
              ? 'Calculate Advanced Service Pricing'
              : 'Calculate Advanced Product Pricing',
          fullWidth: true,
          size: FCButtonSize.lg,
          icon: const Icon(Icons.calculate_outlined),
          onPressed: _tab == 0 ? _calculateService : _calculateProduct,
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          FCCalloutBanner(
            background: FCPalette.red50,
            border: FCPalette.red400,
            child: Text(
              _error!,
              style: TextStyle(
                color: FCPalette.red600,
                fontSize: FCFontSizes.sm,
                fontWeight: FCFontWeights.semibold,
              ),
            ),
          ),
        ],
        ..._results(),
      ],
    );
  }

  // ---------------------------------------------------------------- inputs

  List<Widget> _serviceInputs() {
    return [
      FCResultGrid(columns: 2, children: [
        FCSelectField<String>(
          label: 'Industry Type',
          hintText: 'Select your industry',
          value: _industryType,
          items: const [
            FCSelectItem('consulting', 'Consulting'),
            FCSelectItem('design', 'Design & Creative'),
            FCSelectItem('development', 'Software Development'),
            FCSelectItem('marketing', 'Marketing & Advertising'),
            FCSelectItem('legal', 'Legal Services'),
            FCSelectItem('accounting', 'Accounting & Finance'),
            FCSelectItem('coaching', 'Coaching & Training'),
            FCSelectItem('healthcare', 'Healthcare & Wellness'),
            FCSelectItem('trades', 'Trades (Plumbing, Electrical, etc.)'),
            FCSelectItem('realestate', 'Real Estate'),
            FCSelectItem('education', 'Education & Tutoring'),
            FCSelectItem('freelance', 'General Freelance'),
            FCSelectItem('other', 'Other'),
          ],
          onChanged: (v) => setState(() => _industryType = v),
        ),
        _num(_hourlyRate, 'Current Hourly Rate (\$)', '75'),
        _num(_hoursPerWeek, 'Billable Hours per Week', '30'),
        _num(_weeksPerYear, 'Working Weeks per Year', '50'),
        _num(_expenses, 'Annual Business Expenses (\$)', '25000'),
        _num(_desiredSalary, 'Desired Annual Salary (\$)', '80000'),
        _num(_taxRate, 'Tax Rate (%)', '25'),
        _num(_profitMargin, 'Target Profit Margin (%)', '20'),
      ]),
    ];
  }

  List<Widget> _productInputs() {
    return [
      FCResultGrid(columns: 2, children: [
        _num(_productCost, 'Product Cost (\$)', '25',
            helper: 'Total cost to make/acquire'),
        _num(_productMargin, 'Desired Profit Margin (%)', '50',
            helper: 'Percentage of selling price'),
        _num(_competitorPrice, 'Competitor Price (\$)', '60',
            helper: 'For competitive analysis'),
        _num(_shippingCost, 'Shipping Cost (\$)', '5'),
        _num(_volumeDiscount, 'Volume Discount (%)', '10',
            helper: 'For bulk orders'),
      ]),
    ];
  }

  // --------------------------------------------------------------- results

  List<Widget> _results() {
    if (_tab == 0 && _serviceResult != null) {
      return _serviceResults(_serviceResult!);
    }
    if (_tab == 1 && _productResult != null) {
      return _productResults(_productResult!);
    }
    return const [];
  }

  List<Widget> _serviceResults(ServicePricingResult r) {
    final c = context.colors;
    final widgets = <Widget>[
      const SizedBox(height: 24),
      _analysisHeading('Your Service Pricing Analysis'),
      const SizedBox(height: 16),
      _metricGrid([
        FCResultTile(
          label: 'Current Annual Revenue',
          value: Fmt.money2(r.annualRevenue),
          valueColor: FCPalette.green600,
          valueSize: 22,
        ),
        FCResultTile(
          label: 'Net Income (After Tax)',
          value: Fmt.money2(r.netIncome),
          valueColor: r.netIncome >= 0 ? FCPalette.blue600 : FCPalette.red600,
          valueSize: 22,
        ),
        FCResultTile(
          label: 'Required Hourly Rate',
          value: '\$${Fmt.fixed(r.requiredHourlyRate, 2)}',
          valueColor: FCPalette.purple600,
          valueSize: 22,
        ),
        FCResultTile(
          label: 'Break-Even Rate',
          value: '\$${Fmt.fixed(r.breakEvenRate, 2)}',
          valueColor: FCPalette.orange600,
          valueSize: 22,
        ),
      ]),
    ];

    final industry = r.industryData;
    if (industry != null) {
      final competitive = r.isCompetitive == true;
      widgets.addAll([
        const SizedBox(height: 16),
        FCCalloutBanner(
          background: FCPalette.blue50,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Industry Benchmark Analysis',
                style: TextStyle(
                    fontSize: FCFontSizes.base,
                    fontWeight: FCFontWeights.semibold,
                    color: c.foreground),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      'Industry Range: \$${Fmt.group(industry.hourlyRange[0])} - '
                      '\$${Fmt.group(industry.hourlyRange[1])}',
                      style: TextStyle(
                          fontSize: FCFontSizes.sm, color: c.foreground),
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: competitive ? FCPalette.green50 : FCPalette.yellow50,
                      borderRadius: FCRadii.smAll,
                    ),
                    child: Text(
                      competitive ? 'Competitive' : 'Outside Range',
                      style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        color: competitive
                            ? FCPalette.green700
                            : FCPalette.yellow800,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              FCProgressBar(
                value: ((r.currentRate / industry.hourlyRange[1]) * 100)
                    .clamp(0, 100)
                    .toDouble(),
              ),
            ],
          ),
        ),
      ]);
    }

    widgets.addAll([
      const SizedBox(height: 16),
      FCResultPanel(
        title: 'Pricing Scenarios:',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < r.scenarios.length; i++) ...[
              if (i > 0) const SizedBox(height: 12),
              _scenarioRow(r.scenarios[i]),
            ],
          ],
        ),
      ),
    ]);

    return widgets;
  }

  Widget _scenarioRow(PricingScenario s) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: c.background,
        borderRadius: FCRadii.smAll,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Flexible(
                  child: Text(s.name,
                      style: TextStyle(
                          fontSize: FCFontSizes.base,
                          fontWeight: FCFontWeights.medium,
                          color: c.foreground)),
                ),
                const SizedBox(width: 8),
                Text('\$${Fmt.fixed(s.rate, 2)}/hr',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(Fmt.money2(s.annualRevenue),
                  style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.semibold,
                      color: c.foreground)),
              const SizedBox(height: 2),
              Text('Net: ${Fmt.money2(s.netIncome)}',
                  style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      color: s.netIncome >= 0
                          ? FCPalette.green600
                          : FCPalette.red600)),
            ],
          ),
        ],
      ),
    );
  }

  List<Widget> _productResults(ProductPricingResult r) {
    final c = context.colors;
    final widgets = <Widget>[
      const SizedBox(height: 24),
      _analysisHeading('Your Product Pricing Analysis'),
      const SizedBox(height: 16),
      _metricGrid([
        FCResultTile(
          label: 'Recommended Selling Price',
          value: '\$${Fmt.fixed(r.sellingPrice, 2)}',
          valueColor: FCPalette.green600,
          valueSize: 22,
        ),
        FCResultTile(
          label: 'Profit per Unit',
          value: '\$${Fmt.fixed(r.profit, 2)}',
          valueColor: FCPalette.blue600,
          valueSize: 22,
        ),
        FCResultTile(
          label: 'Markup Percentage',
          value: Fmt.pct(r.markupPercentage, 1),
          valueColor: FCPalette.purple600,
          valueSize: 22,
        ),
        FCResultTile(
          label: 'Profit Margin',
          value: '${Fmt.group(r.marginPercentage)}%',
          valueColor: FCPalette.orange600,
          valueSize: 22,
        ),
      ]),
    ];

    if (r.competitiveAdvantage != 0) {
      final positive = r.competitiveAdvantage > 0;
      widgets.addAll([
        const SizedBox(height: 16),
        FCCalloutBanner(
          background: FCPalette.blue50,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'Competitive Analysis',
                style: TextStyle(
                    fontSize: FCFontSizes.base,
                    fontWeight: FCFontWeights.semibold,
                    color: c.foreground),
              ),
              const SizedBox(height: 8),
              Text(
                'Your price is '
                '${Fmt.fixed(r.competitiveAdvantage.abs(), 1)}%'
                '${positive ? ' below' : ' above'} competitor pricing',
                style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  color: positive ? FCPalette.green600 : FCPalette.red600,
                ),
              ),
            ],
          ),
        ),
      ]);
    }

    widgets.addAll([
      const SizedBox(height: 16),
      FCResultPanel(
        title: 'Pricing Strategies:',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < r.strategies.length; i++) ...[
              if (i > 0) const SizedBox(height: 12),
              _strategyRow(r.strategies[i]),
            ],
          ],
        ),
      ),
    ]);

    return widgets;
  }

  Widget _strategyRow(PricingStrategy s) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: c.background,
        borderRadius: FCRadii.smAll,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(s.name,
                    style: TextStyle(
                        fontSize: FCFontSizes.base,
                        fontWeight: FCFontWeights.medium,
                        color: c.foreground)),
                const SizedBox(height: 2),
                Text(s.description,
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground)),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text('\$${Fmt.fixed(s.price, 2)}',
                  style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.semibold,
                      color: c.foreground)),
              const SizedBox(height: 2),
              Text(
                'Profit: \$${Fmt.fixed(s.profit, 2)} '
                '(${Fmt.fixed(s.margin, 1)}%)',
                style: TextStyle(
                    fontSize: FCFontSizes.sm, color: FCPalette.green600),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ----------------------------------------------------------- shared bits

  Widget _analysisHeading(String text) {
    return Row(
      children: [
        Icon(Icons.trending_up, size: 20, color: FCPalette.blue700),
        const SizedBox(width: 8),
        Expanded(
          child: Text(text,
              style: TextStyle(
                  fontSize: FCFontSizes.lg,
                  fontWeight: FCFontWeights.semibold,
                  color: FCPalette.blue700)),
        ),
      ],
    );
  }

  Widget _metricGrid(List<Widget> tiles) {
    return LayoutBuilder(builder: (context, constraints) {
      final cols = constraints.maxWidth >= 520 ? 4 : 2;
      return FCResultGrid(columns: cols, children: tiles);
    });
  }

  Widget _num(TextEditingController controller, String label, String hint,
      {String? helper}) {
    return FCTextField(
      controller: controller,
      label: label,
      hintText: hint,
      helperText: helper,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
    );
  }
}
