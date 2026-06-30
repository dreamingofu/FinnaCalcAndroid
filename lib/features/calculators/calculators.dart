import 'package:flutter/material.dart';

import 'break_even/break_even_screen.dart';
import 'cash_flow/cash_flow_screen.dart';
import 'emergency_fund/emergency_fund_screen.dart';
import 'employee_contractor/employee_contractor_screen.dart';
import 'loan/loan_screen.dart';
import 'pricing/pricing_screen.dart';
import 'profit_margin/profit_margin_screen.dart';
import 'roi/roi_screen.dart';
import 'startup_cost/startup_cost_screen.dart';

/// Metadata + builder for a standalone calculator, used by the Home grid.
class CalculatorInfo {
  const CalculatorInfo({
    required this.title,
    required this.description,
    required this.icon,
    required this.builder,
  });

  final String title;
  final String description;
  final IconData icon;
  final WidgetBuilder builder;
}

/// The nine standalone calculators (no backend/SDK dependency), in the order
/// they appear on the web home page.
const List<CalculatorInfo> kCalculators = [
  CalculatorInfo(
    title: 'Loan Calculator',
    description: 'Payments, true APR, loan amounts, and remaining balances.',
    icon: Icons.calculate_outlined,
    builder: _loan,
  ),
  CalculatorInfo(
    title: 'ROI Calculator',
    description: 'Return on investment, CAGR, and after-tax / real returns.',
    icon: Icons.trending_up,
    builder: _roi,
  ),
  CalculatorInfo(
    title: 'Break-Even Calculator',
    description: 'Units needed to cover costs and hit profit targets.',
    icon: Icons.calculate_outlined,
    builder: _breakEven,
  ),
  CalculatorInfo(
    title: 'Profit Margin Calculator',
    description: 'Gross, operating, EBT, and net margins with a full statement.',
    icon: Icons.trending_up,
    builder: _profitMargin,
  ),
  CalculatorInfo(
    title: 'Startup Cost Calculator',
    description: 'Estimate startup costs with templates and funding analysis.',
    icon: Icons.apartment,
    builder: _startupCost,
  ),
  CalculatorInfo(
    title: 'Pricing Calculator',
    description: 'Service and product pricing with competitive analysis.',
    icon: Icons.attach_money,
    builder: _pricing,
  ),
  CalculatorInfo(
    title: 'Cash Flow Calculator',
    description: 'Project cash flow with separate revenue & expense growth.',
    icon: Icons.trending_up,
    builder: _cashFlow,
  ),
  CalculatorInfo(
    title: 'Emergency Fund Calculator',
    description: 'Target fund and time to reach it with interest.',
    icon: Icons.savings_outlined,
    builder: _emergencyFund,
  ),
  CalculatorInfo(
    title: 'Employee vs Contractor',
    description: 'Compare the true total cost of employees vs contractors.',
    icon: Icons.group_outlined,
    builder: _employeeContractor,
  ),
];

Widget _loan(BuildContext _) => const LoanCalculatorScreen();
Widget _roi(BuildContext _) => const RoiCalculatorScreen();
Widget _breakEven(BuildContext _) => const BreakEvenCalculatorScreen();
Widget _profitMargin(BuildContext _) => const ProfitMarginCalculatorScreen();
Widget _startupCost(BuildContext _) => const StartupCostCalculatorScreen();
Widget _pricing(BuildContext _) => const PricingCalculatorScreen();
Widget _cashFlow(BuildContext _) => const CashFlowCalculatorScreen();
Widget _emergencyFund(BuildContext _) => const EmergencyFundCalculatorScreen();
Widget _employeeContractor(BuildContext _) =>
    const EmployeeContractorCalculatorScreen();
