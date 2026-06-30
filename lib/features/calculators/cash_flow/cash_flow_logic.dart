import 'dart:math' as math;

/// A single month's projected row. Revenue / expenses / net / cumulative are all
/// `Math.round`-ed integers, exactly as the web records them.
class CashFlowRow {
  const CashFlowRow({
    required this.month,
    required this.revenue,
    required this.expenses,
    required this.netCashFlow,
    required this.cumulativeCash,
  });

  final int month;
  final double revenue;
  final double expenses;
  final double netCashFlow;
  final double cumulativeCash;
}

/// The full cash-flow projection result.
class CashFlowResult {
  const CashFlowResult({
    required this.projections,
    required this.totalRevenue,
    required this.totalExpenses,
    required this.finalCash,
    required this.netCashFlow,
    required this.breakEvenMonth,
    required this.negativeMonths,
  });

  final List<CashFlowRow> projections;
  final double totalRevenue;
  final double totalExpenses;
  final double finalCash;
  final double netCashFlow;

  /// First month where cumulative cash turns non-negative after being negative;
  /// `null` if that never happens.
  final int? breakEvenMonth;
  final int negativeMonths;
}

/// Pure cash-flow projection math, transcribed 1:1 from
/// `app/cash-flow-calculator/page.tsx`.
class CashFlowCalculator {
  const CashFlowCalculator._();

  static CashFlowResult project({
    required double monthlyRevenue,
    required double monthlyExpenses,
    required double startingCash,
    required double revenueGrowthRate,
    required double expenseGrowthRate,
    required int months,
  }) {
    final revenue = monthlyRevenue;
    final expenses = monthlyExpenses;
    final cash = startingCash;
    final revGrowth = revenueGrowthRate;
    final expGrowth = expenseGrowthRate;
    final period = math.min(math.max(months, 1), 60);

    final projections = <CashFlowRow>[];
    var currentCash = cash;
    var currentRevenue = revenue;
    var currentExpenses = expenses;
    int? breakEvenMonth;

    for (var month = 1; month <= period; month++) {
      final netCashFlow = currentRevenue - currentExpenses;
      currentCash += netCashFlow;

      if (breakEvenMonth == null &&
          currentCash >= 0 &&
          (month == 1
              ? cash < 0
              : projections[projections.length - 1].cumulativeCash < 0)) {
        breakEvenMonth = month;
      }

      projections.add(CashFlowRow(
        month: month,
        revenue: currentRevenue.roundToDouble(),
        expenses: currentExpenses.roundToDouble(),
        netCashFlow: netCashFlow.roundToDouble(),
        cumulativeCash: currentCash.roundToDouble(),
      ));

      currentRevenue = currentRevenue * (1 + revGrowth / 100);
      currentExpenses = currentExpenses * (1 + expGrowth / 100);
    }

    final totalRevenue =
        projections.fold<double>(0, (sum, p) => sum + p.revenue);
    final totalExpenses =
        projections.fold<double>(0, (sum, p) => sum + p.expenses);
    final finalCash = projections.isNotEmpty
        ? projections[projections.length - 1].cumulativeCash
        : 0.0;
    final negativeMonths =
        projections.where((p) => p.cumulativeCash < 0).length;

    return CashFlowResult(
      projections: projections,
      totalRevenue: totalRevenue,
      totalExpenses: totalExpenses,
      finalCash: finalCash,
      netCashFlow: totalRevenue - totalExpenses,
      breakEvenMonth: breakEvenMonth,
      negativeMonths: negativeMonths,
    );
  }
}
