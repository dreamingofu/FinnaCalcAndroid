import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/break_even/break_even_logic.dart';

void main() {
  group('Break-even analysis', () {
    test('standard 10k fixed, \$25 vc, \$50 price, 20% target', () {
      final r = BreakEvenCalculator.calculate(
        fixedCosts: 10000,
        variableCostPerUnit: 25,
        pricePerUnit: 50,
        seasonalityFactor: 0,
        targetProfitMargin: 20,
      );
      expect(r.contributionMargin, closeTo(25, 1e-9));
      expect(r.contributionMarginRatio, closeTo(50, 1e-9));
      expect(r.breakEvenUnits, 400);
      expect(r.breakEvenRevenue, closeTo(20000, 1e-6));
      expect(r.adjustedCMValid, isTrue);
      // adjustedCM = 50*0.8 - 25 = 15 ; 10000/15 = 666.67 -> ceil 667
      expect(r.unitsForTargetProfit, 667);
      expect(r.targetProfitRevenue, closeTo(33333.333, 0.01));
      expect(r.marginOfSafety, closeTo(40, 1e-6));
      // seasonality 0 -> factor 1, so seasonal equals base
      expect(r.seasonalBreakEven, 400);
      expect(r.seasonalTargetUnits, 667);
    });

    test('break-even units use Math.ceil', () {
      // 10000 / (50-43) = 10000/7 = 1428.57 -> ceil 1429
      final r = BreakEvenCalculator.calculate(
        fixedCosts: 10000,
        variableCostPerUnit: 43,
        pricePerUnit: 50,
        seasonalityFactor: 0,
        targetProfitMargin: 20,
      );
      expect(r.breakEvenUnits, 1429);
    });

    test('seasonality scales break-even by 1 + seasonality/100', () {
      // base be units = 400 ; seasonalFactor = 1.25 ; 400*1.25 = 500
      final r = BreakEvenCalculator.calculate(
        fixedCosts: 10000,
        variableCostPerUnit: 25,
        pricePerUnit: 50,
        seasonalityFactor: 25,
        targetProfitMargin: 20,
      );
      expect(r.seasonalBreakEven, 500);
      // seasonal target = 666.67 * 1.25 = 833.33 -> ceil 834
      expect(r.seasonalTargetUnits, 834);
    });

    test('target margin too high makes adjustedCM invalid (null target units)',
        () {
      // adjustedCM = 50*(1-0.9) - 25 = 5 - 25 = -20 <= 0
      final r = BreakEvenCalculator.calculate(
        fixedCosts: 10000,
        variableCostPerUnit: 25,
        pricePerUnit: 50,
        seasonalityFactor: 0,
        targetProfitMargin: 90,
      );
      expect(r.adjustedCMValid, isFalse);
      expect(r.unitsForTargetProfit, isNull);
      expect(r.seasonalTargetUnits, isNull);
      expect(r.targetProfitRevenue, 0);
      expect(r.marginOfSafety, 0);
    });

    test('price <= variable cost throws CalcException', () {
      expect(
        () => BreakEvenCalculator.calculate(
          fixedCosts: 10000,
          variableCostPerUnit: 50,
          pricePerUnit: 50,
          seasonalityFactor: 0,
          targetProfitMargin: 20,
        ),
        throwsA(isA<CalcException>()),
      );
    });

    test('target margin >= 100 throws CalcException', () {
      expect(
        () => BreakEvenCalculator.calculate(
          fixedCosts: 10000,
          variableCostPerUnit: 25,
          pricePerUnit: 50,
          seasonalityFactor: 0,
          targetProfitMargin: 100,
        ),
        throwsA(isA<CalcException>()),
      );
    });
  });
}
