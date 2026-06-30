import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/profit_margin/profit_margin_logic.dart';

void main() {
  group('Profit margin', () {
    test('standard income statement computes margins and line items', () {
      final r = ProfitMarginCalculator.calculate(
        revenue: 100000,
        costOfGoodsSold: 60000,
        operatingExpenses: 20000,
        interestExpenses: 2000,
        taxExpenses: 3000,
      );
      // grossProfit = 100000 - 60000 = 40000
      expect(r.grossProfit, closeTo(40000, 0.0001));
      // operatingIncome = 40000 - 20000 = 20000
      expect(r.operatingIncome, closeTo(20000, 0.0001));
      // ebt = 20000 - 2000 = 18000
      expect(r.ebt, closeTo(18000, 0.0001));
      // net = 18000 - 3000 = 15000
      expect(r.netProfit, closeTo(15000, 0.0001));
      // margins = each / revenue * 100
      expect(r.grossMargin, closeTo(40, 0.0001));
      expect(r.operatingMargin, closeTo(20, 0.0001));
      expect(r.ebtMargin, closeTo(18, 0.0001));
      expect(r.netMargin, closeTo(15, 0.0001));
    });

    test('echoes collected inputs for the income statement', () {
      final r = ProfitMarginCalculator.calculate(
        revenue: 50000,
        costOfGoodsSold: 30000,
        operatingExpenses: 10000,
        interestExpenses: 0,
        taxExpenses: 0,
      );
      expect(r.totalRevenue, 50000);
      expect(r.cogs, 30000);
      expect(r.opex, 10000);
      expect(r.interest, 0);
      expect(r.taxes, 0);
    });

    test('costs exceeding revenue produce negative net and margin', () {
      final r = ProfitMarginCalculator.calculate(
        revenue: 10000,
        costOfGoodsSold: 8000,
        operatingExpenses: 5000,
        interestExpenses: 0,
        taxExpenses: 0,
      );
      // grossProfit = 2000, operatingIncome = -3000
      expect(r.operatingIncome, closeTo(-3000, 0.0001));
      expect(r.netProfit, closeTo(-3000, 0.0001));
      expect(r.netMargin, closeTo(-30, 0.0001));
    });

    test('revenue <= 0 throws CalcException', () {
      expect(
        () => ProfitMarginCalculator.calculate(
          revenue: 0,
          costOfGoodsSold: 0,
          operatingExpenses: 0,
          interestExpenses: 0,
          taxExpenses: 0,
        ),
        throwsA(isA<CalcException>()),
      );
    });

    test('negative revenue throws CalcException', () {
      expect(
        () => ProfitMarginCalculator.calculate(
          revenue: -5000,
          costOfGoodsSold: 1000,
          operatingExpenses: 0,
          interestExpenses: 0,
          taxExpenses: 0,
        ),
        throwsA(isA<CalcException>()),
      );
    });
  });
}
