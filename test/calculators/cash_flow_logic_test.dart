import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/calculators/cash_flow/cash_flow_logic.dart';

void main() {
  group('Cash flow projection', () {
    test('month 1 uses raw starting values; growth applied after the row', () {
      final r = CashFlowCalculator.project(
        monthlyRevenue: 25000,
        monthlyExpenses: 20000,
        startingCash: 50000,
        revenueGrowthRate: 5,
        expenseGrowthRate: 2,
        months: 12,
      );

      expect(r.projections.length, 12);

      // Month 1: raw values, no growth yet.
      final m1 = r.projections[0];
      expect(m1.month, 1);
      expect(m1.revenue, closeTo(25000, 0.0001));
      expect(m1.expenses, closeTo(20000, 0.0001));
      expect(m1.netCashFlow, closeTo(5000, 0.0001));
      expect(m1.cumulativeCash, closeTo(55000, 0.0001));

      // Month 2: revenue *1.05, expenses *1.02 (rounded).
      final m2 = r.projections[1];
      expect(m2.revenue, closeTo(26250, 0.0001)); // 25000 * 1.05
      expect(m2.expenses, closeTo(20400, 0.0001)); // 20000 * 1.02
      expect(m2.netCashFlow, closeTo(5850, 0.0001)); // 26250 - 20400
      expect(m2.cumulativeCash, closeTo(60850, 0.0001)); // 55000 + 5850
    });

    test('totals sum the rounded rows and net = revenue - expenses', () {
      final r = CashFlowCalculator.project(
        monthlyRevenue: 25000,
        monthlyExpenses: 20000,
        startingCash: 50000,
        revenueGrowthRate: 5,
        expenseGrowthRate: 2,
        months: 12,
      );

      final sumRev =
          r.projections.fold<double>(0, (s, p) => s + p.revenue);
      final sumExp =
          r.projections.fold<double>(0, (s, p) => s + p.expenses);
      expect(r.totalRevenue, closeTo(sumRev, 0.0001));
      expect(r.totalExpenses, closeTo(sumExp, 0.0001));
      expect(r.netCashFlow, closeTo(sumRev - sumExp, 0.0001));
      expect(r.finalCash, closeTo(r.projections.last.cumulativeCash, 0.0001));
      expect(r.negativeMonths, 0);
      expect(r.breakEvenMonth, isNull);
    });

    test('counts negative months when expenses outpace revenue', () {
      final r = CashFlowCalculator.project(
        monthlyRevenue: 10000,
        monthlyExpenses: 20000,
        startingCash: 5000,
        revenueGrowthRate: 0,
        expenseGrowthRate: 0,
        months: 3,
      );

      // Month 1: net -10000 -> cash -5000 (negative).
      expect(r.projections[0].cumulativeCash, closeTo(-5000, 0.0001));
      expect(r.negativeMonths, 3);
      expect(r.finalCash, lessThan(0));
      // Starting cash was positive at month 1, so no break-even is detected
      // (cash only goes further negative).
      expect(r.breakEvenMonth, isNull);
    });

    test('detects break-even month when cumulative turns positive', () {
      // Start negative, recover after a few positive net months.
      final r = CashFlowCalculator.project(
        monthlyRevenue: 5000,
        monthlyExpenses: 3000,
        startingCash: -5000,
        revenueGrowthRate: 0,
        expenseGrowthRate: 0,
        months: 6,
      );

      // Month 1: net 2000 -> cash -3000 (still negative).
      // Month 2: -1000. Month 3: +1000 (first non-negative after negative).
      expect(r.projections[0].cumulativeCash, closeTo(-3000, 0.0001));
      expect(r.projections[1].cumulativeCash, closeTo(-1000, 0.0001));
      expect(r.projections[2].cumulativeCash, closeTo(1000, 0.0001));
      expect(r.breakEvenMonth, 3);
    });

    test('projection period is clamped to [1, 60]', () {
      final high = CashFlowCalculator.project(
        monthlyRevenue: 1000,
        monthlyExpenses: 500,
        startingCash: 0,
        revenueGrowthRate: 0,
        expenseGrowthRate: 0,
        months: 100,
      );
      expect(high.projections.length, 60);

      final low = CashFlowCalculator.project(
        monthlyRevenue: 1000,
        monthlyExpenses: 500,
        startingCash: 0,
        revenueGrowthRate: 0,
        expenseGrowthRate: 0,
        months: 0,
      );
      expect(low.projections.length, 1);
    });
  });
}
