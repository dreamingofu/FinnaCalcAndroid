import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../core/util/parse.dart';
import '../../../shared/widgets/calculator_scaffold.dart';
import '../../../shared/widgets/fc_segmented_tabs.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../../../shared/widgets/result_widgets.dart';
import 'loan_logic.dart';

class LoanCalculatorScreen extends StatefulWidget {
  const LoanCalculatorScreen({super.key});

  @override
  State<LoanCalculatorScreen> createState() => _LoanCalculatorScreenState();
}

class _LoanCalculatorScreenState extends State<LoanCalculatorScreen> {
  int _tab = 0;

  // Payment tab
  final _loanAmount = TextEditingController();
  final _downPayment = TextEditingController();
  final _interestRate = TextEditingController();
  final _loanTerm = TextEditingController();
  String _loanType = 'personal';
  PaymentFrequency _frequency = PaymentFrequency.monthly;

  // APR tab
  final _loanAmountApr = TextEditingController();
  final _totalInterest = TextEditingController();
  final _fees = TextEditingController();
  final _termApr = TextEditingController();

  // Loan amount tab
  final _monthlyPayment = TextEditingController();
  final _rateForAmount = TextEditingController();
  final _termForAmount = TextEditingController();

  // Remaining tab
  final _originalAmount = TextEditingController();
  final _originalRate = TextEditingController();
  final _originalTerm = TextEditingController();
  final _paymentsMade = TextEditingController();

  LoanPaymentResult? _paymentResult;
  LoanAprResult? _aprResult;
  LoanAmountResult? _amountResult;
  LoanRemainingResult? _remainingResult;
  String? _error;

  static const _calcLabels = [
    'Calculate Payment',
    'Calculate True APR',
    'Calculate Loan Amount',
    'Calculate Remaining Balance',
  ];

  @override
  void dispose() {
    for (final c in [
      _loanAmount, _downPayment, _interestRate, _loanTerm,
      _loanAmountApr, _totalInterest, _fees, _termApr,
      _monthlyPayment, _rateForAmount, _termForAmount,
      _originalAmount, _originalRate, _originalTerm, _paymentsMade,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  void _clearResults() {
    _paymentResult = null;
    _aprResult = null;
    _amountResult = null;
    _remainingResult = null;
    _error = null;
  }

  void _onTabChanged(int i) => setState(() {
        _tab = i;
        _clearResults();
      });

  void _calculate() {
    setState(_clearResults);
    try {
      switch (_tab) {
        case 0:
          _paymentResult = LoanCalculator.payment(
            loanAmount: parseNum(_loanAmount.text),
            downPayment: parseNum(_downPayment.text),
            interestRate: parseNum(_interestRate.text),
            loanTerm: parseNum(_loanTerm.text),
            frequency: _frequency,
          );
        case 1:
          _aprResult = LoanCalculator.apr(
            loanAmount: parseNum(_loanAmountApr.text),
            totalInterest: parseNum(_totalInterest.text),
            fees: parseNum(_fees.text),
            termYears: parseNum(_termApr.text),
          );
        case 2:
          _amountResult = LoanCalculator.loanAmount(
            monthlyPayment: parseNum(_monthlyPayment.text),
            annualRate: parseNum(_rateForAmount.text),
            termMonths: parseNum(_termForAmount.text),
          );
        case 3:
          _remainingResult = LoanCalculator.remaining(
            originalAmount: parseNum(_originalAmount.text),
            annualRate: parseNum(_originalRate.text),
            termMonths: parseNum(_originalTerm.text),
            paymentsMade: parseNum(_paymentsMade.text),
          );
      }
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
      title: 'Loan Calculator',
      description:
          'Calculate payments, true APR (IRR method), loan amounts, and '
          'remaining balances',
      children: [
        FCSegmentedTabs(
          tabs: const ['Payment', 'True APR', 'Loan Amount', 'Remaining'],
          index: _tab,
          onChanged: _onTabChanged,
        ),
        const SizedBox(height: 20),
        ..._inputsForTab(),
        const SizedBox(height: 20),
        FCButton(
          label: _calcLabels[_tab],
          fullWidth: true,
          size: FCButtonSize.lg,
          onPressed: _calculate,
        ),
        if (_error != null) ...[
          const SizedBox(height: 16),
          Text(_error!, style: TextStyle(color: c.destructive, fontSize: FCFontSizes.sm)),
        ],
        ..._results(),
      ],
    );
  }

  List<Widget> _inputsForTab() {
    switch (_tab) {
      case 0:
        return [
          FCResultGrid(columns: 2, children: [
            FCSelectField<String>(
              label: 'Loan Type',
              value: _loanType,
              items: const [
                FCSelectItem('personal', 'Personal Loan'),
                FCSelectItem('business', 'Business Loan'),
                FCSelectItem('auto', 'Auto Loan'),
                FCSelectItem('mortgage', 'Mortgage'),
                FCSelectItem('student', 'Student Loan'),
              ],
              onChanged: (v) => setState(() => _loanType = v),
            ),
            _num(_loanAmount, 'Loan Amount (\$)', '50000'),
            _num(_interestRate, 'Annual Interest Rate (%)', '5.5'),
            _num(_loanTerm, 'Loan Term (months)', '60'),
            FCSelectField<PaymentFrequency>(
              label: 'Payment Frequency',
              value: _frequency,
              items: [
                for (final f in PaymentFrequency.values) FCSelectItem(f, f.label),
              ],
              onChanged: (v) => setState(() => _frequency = v),
            ),
            _num(_downPayment, 'Down Payment (\$)', '0'),
          ]),
        ];
      case 1:
        return [
          FCResultGrid(columns: 2, children: [
            _num(_loanAmountApr, 'Loan Amount (\$)', '50000'),
            _num(_totalInterest, 'Total Interest Paid (\$)', '5000'),
            _num(_fees, 'Total Upfront Fees (\$)', '500'),
            _num(_termApr, 'Loan Term (years)', '5'),
          ]),
          const SizedBox(height: 12),
          Text(
            'APR computed using Newton-Raphson IRR to match US Regulation Z — '
            'more accurate than the simple average-cost method.',
            style: TextStyle(
                fontSize: FCFontSizes.xs, color: context.colors.mutedForeground),
          ),
        ];
      case 2:
        return [
          FCResultGrid(columns: 2, children: [
            _num(_monthlyPayment, 'Monthly Payment (\$)', '500'),
            _num(_rateForAmount, 'Annual Interest Rate (%)', '5.5'),
            _num(_termForAmount, 'Loan Term (months)', '60'),
          ]),
        ];
      default:
        return [
          FCResultGrid(columns: 2, children: [
            _num(_originalAmount, 'Original Loan Amount (\$)', '50000'),
            _num(_originalRate, 'Annual Interest Rate (%)', '5.5'),
            _num(_originalTerm, 'Original Term (months)', '60'),
            _num(_paymentsMade, 'Payments Made', '12'),
          ]),
        ];
    }
  }

  List<Widget> _results() {
    if (_tab == 0 && _paymentResult != null) {
      final r = _paymentResult!;
      return _resultSection('Results', [
        FCResultTile(
            label: 'Payment per Period',
            value: Fmt.money2(r.basePayment),
            valueColor: FCPalette.green600),
        FCResultTile(
            label: 'Total Amount Paid', value: Fmt.money2(r.totalPayment)),
        FCResultTile(
            label: 'Total Interest Cost',
            value: Fmt.money2(r.totalInterest),
            valueColor: FCPalette.red600),
        FCResultTile(
            label: 'Principal Financed', value: Fmt.money(r.principal)),
      ]);
    }
    if (_tab == 1 && _aprResult != null) {
      final r = _aprResult!;
      return _resultSection('Results', [
        FCResultTile(
            label: 'True APR (incl. fees)',
            value: Fmt.pct(r.apr, 3),
            valueColor: FCPalette.green600),
        FCResultTile(
            label: 'Total Loan Cost',
            value: Fmt.money(r.totalCost),
            valueColor: FCPalette.red600),
      ]);
    }
    if (_tab == 2 && _amountResult != null) {
      final r = _amountResult!;
      return _resultSection('Results', [
        FCResultTile(
            label: 'Maximum Loan Amount',
            value: Fmt.money2(r.maxLoan),
            valueColor: FCPalette.green600),
        FCResultTile(label: 'Monthly Payment', value: Fmt.money(r.payment)),
      ]);
    }
    if (_tab == 3 && _remainingResult != null) {
      final r = _remainingResult!;
      return _resultSection('Results', [
        FCResultTile(
            label: 'Remaining Balance',
            value: Fmt.money2(r.remainingBalance),
            valueColor: FCPalette.green600),
        FCResultTile(
            label: 'Payments Remaining', value: Fmt.group(r.remainingPayments)),
        FCResultTile(
            label: 'Total Paid So Far', value: Fmt.money2(r.totalPaid)),
        FCResultTile(
            label: 'Monthly Payment', value: Fmt.money2(r.monthlyPayment)),
      ]);
    }
    return const [];
  }

  List<Widget> _resultSection(String title, List<Widget> tiles) {
    return [
      const SizedBox(height: 24),
      Text(title,
          style: TextStyle(
              fontSize: FCFontSizes.lg,
              fontWeight: FCFontWeights.semibold,
              color: context.colors.foreground)),
      const SizedBox(height: 16),
      FCResultGrid(columns: 2, children: tiles),
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
