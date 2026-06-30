import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/pricing/pricing_logic.dart';

void main() {
  group('Service pricing', () {
    test('standard consulting inputs compute revenue, net & required rate', () {
      final r = PricingCalculator.service(
        rate: 75,
        hours: 30,
        weeks: 50,
        annualExpenses: 25000,
        salary: 80000,
        tax: 25,
        industryType: 'consulting',
      );
      expect(r.totalHours, 1500);
      expect(r.annualRevenue, closeTo(112500, 0.001));
      expect(r.grossProfit, closeTo(87500, 0.001));
      expect(r.netIncome, closeTo(65625, 0.001));
      // requiredRevenue = 80000/0.75 + 25000 = 131666.6667 ; /1500
      expect(r.requiredHourlyRate, closeTo(87.7778, 0.001));
      expect(r.breakEvenRate, closeTo(16.6667, 0.001));
      expect(r.profitMarginActual, closeTo(77.7778, 0.001));
    });

    test('industry benchmark competitiveness reflects the hourly range', () {
      // 75 is inside consulting [75, 200] -> competitive
      final inRange = PricingCalculator.service(
        rate: 75,
        hours: 30,
        weeks: 50,
        annualExpenses: 0,
        salary: 0,
        tax: 25,
        industryType: 'consulting',
      );
      expect(inRange.isCompetitive, isTrue);
      expect(inRange.industryData!.hourlyRange, [75, 200]);

      // 20 is below consulting range -> not competitive
      final below = PricingCalculator.service(
        rate: 20,
        hours: 30,
        weeks: 50,
        annualExpenses: 0,
        salary: 0,
        tax: 25,
        industryType: 'consulting',
      );
      expect(below.isCompetitive, isFalse);

      // No industry selected -> null industryData and null isCompetitive
      final none = PricingCalculator.service(
        rate: 75,
        hours: 30,
        weeks: 50,
        annualExpenses: 0,
        salary: 0,
        tax: 25,
        industryType: null,
      );
      expect(none.industryData, isNull);
      expect(none.isCompetitive, isNull);
    });

    test('scenarios apply 0.8 / 1.0 / 1.2 / 1.5 multipliers', () {
      final r = PricingCalculator.service(
        rate: 75,
        hours: 30,
        weeks: 50,
        annualExpenses: 25000,
        salary: 0,
        tax: 25,
        industryType: 'consulting',
      );
      expect(r.scenarios.length, 4);
      final conservative = r.scenarios[0];
      expect(conservative.name, 'Conservative');
      expect(conservative.rate, closeTo(60, 0.001)); // 75 * 0.8
      expect(conservative.annualRevenue, closeTo(90000, 0.001)); // 60 * 1500
      // (90000 - 25000) * 0.75 = 48750
      expect(conservative.netIncome, closeTo(48750, 0.001));
      expect(r.scenarios[3].rate, closeTo(112.5, 0.001)); // 75 * 1.5
    });
  });

  group('Product pricing', () {
    test('selling price, profit, markup & competitive advantage', () {
      final r = PricingCalculator.product(
        cost: 25,
        margin: 50,
        competitor: 60,
        discount: 10,
        shipping: 5,
      );
      expect(r.sellingPrice, closeTo(50, 0.001)); // 25 / (1 - 0.5)
      expect(r.profit, closeTo(25, 0.001));
      expect(r.markupPercentage, closeTo(100, 0.001)); // 25/25 * 100
      expect(r.marginPercentage, 50);
      // (60 - 50) / 60 * 100 = 16.6667
      expect(r.competitiveAdvantage, closeTo(16.6667, 0.001));
    });

    test('strategies are filtered to price > 0 and carry profit & margin', () {
      final r = PricingCalculator.product(
        cost: 25,
        margin: 50,
        competitor: 60,
        discount: 0,
        shipping: 0,
      );
      // Cost-Plus, Competitive, Premium, Penetration all > 0
      expect(r.strategies.length, 4);
      final costPlus = r.strategies.firstWhere((s) => s.name == 'Cost-Plus');
      expect(costPlus.price, closeTo(50, 0.001));
      expect(costPlus.profit, closeTo(25, 0.001));
      expect(costPlus.margin, closeTo(50, 0.001)); // (50-25)/50 * 100
      final competitive =
          r.strategies.firstWhere((s) => s.name == 'Competitive');
      expect(competitive.price, closeTo(57, 0.001)); // 60 * 0.95
    });

    test('zero competitor price drops the Competitive strategy', () {
      final r = PricingCalculator.product(
        cost: 25,
        margin: 50,
        competitor: 0,
        discount: 0,
        shipping: 0,
      );
      expect(r.strategies.any((s) => s.name == 'Competitive'), isFalse);
      expect(r.strategies.length, 3);
    });

    test('margin >= 100 throws CalcException', () {
      expect(
        () => PricingCalculator.product(
          cost: 25,
          margin: 100,
          competitor: 60,
          discount: 0,
          shipping: 0,
        ),
        throwsA(isA<CalcException>()),
      );
    });

    test('negative cost throws CalcException', () {
      expect(
        () => PricingCalculator.product(
          cost: -1,
          margin: 50,
          competitor: 60,
          discount: 0,
          shipping: 0,
        ),
        throwsA(isA<CalcException>()),
      );
    });
  });
}
