import '../../../core/util/parse.dart';

/// Result of a profit-margin computation. Mirrors the `result` object the web
/// `app/profit-margin-calculator/page.tsx` builds, including the collected
/// input echoes (cogs/opex/interest/taxes) used by the income statement.
class ProfitMarginResult {
  const ProfitMarginResult({
    required this.totalRevenue,
    required this.grossProfit,
    required this.operatingIncome,
    required this.ebt,
    required this.netProfit,
    required this.grossMargin,
    required this.operatingMargin,
    required this.ebtMargin,
    required this.netMargin,
    required this.cogs,
    required this.opex,
    required this.interest,
    required this.taxes,
  });

  final double totalRevenue;
  final double grossProfit;
  final double operatingIncome;
  final double ebt;
  final double netProfit;
  final double grossMargin;
  final double operatingMargin;
  final double ebtMargin;
  final double netMargin;
  final double cogs;
  final double opex;
  final double interest;
  final double taxes;
}

/// Pure profit-margin math, transcribed 1:1 from
/// `app/profit-margin-calculator/page.tsx`.
class ProfitMarginCalculator {
  const ProfitMarginCalculator._();

  static ProfitMarginResult calculate({
    required double revenue,
    required double costOfGoodsSold,
    required double operatingExpenses,
    required double interestExpenses,
    required double taxExpenses,
  }) {
    final totalRevenue = revenue;
    final cogs = costOfGoodsSold;
    final opex = operatingExpenses;
    final interest = interestExpenses;
    final taxes = taxExpenses;

    if (totalRevenue <= 0) {
      throw const CalcException('Revenue must be greater than 0.');
    }

    final grossProfit = totalRevenue - cogs;
    final operatingIncome = grossProfit - opex;
    final ebt = operatingIncome - interest;
    final netProfit = ebt - taxes;

    return ProfitMarginResult(
      totalRevenue: totalRevenue,
      grossProfit: grossProfit,
      operatingIncome: operatingIncome,
      ebt: ebt,
      netProfit: netProfit,
      grossMargin: (grossProfit / totalRevenue) * 100,
      operatingMargin: (operatingIncome / totalRevenue) * 100,
      ebtMargin: (ebt / totalRevenue) * 100,
      netMargin: (netProfit / totalRevenue) * 100,
      cogs: cogs,
      opex: opex,
      interest: interest,
      taxes: taxes,
    );
  }
}
