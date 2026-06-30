import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/calculators/startup_cost/startup_cost_logic.dart';

void main() {
  group('StartupCost.calculate', () {
    test('sums all 13 costs, applies 20% buffer, and computes funding gap', () {
      final r = StartupCostCalculator.calculate(
        equipment: 15000,
        inventory: 10000,
        marketing: 5000,
        legal: 3000,
        rent: 9000,
        utilities: 1500,
        insurance: 2400,
        other: 2000,
        employees: 2000,
        salaries: 15000,
        permits: 1500,
        website: 3000,
        workingCapital: 10000,
        personalSavings: 25000,
        loanAmount: 50000,
        investorFunding: 0,
      );
      // 15000+10000+5000+3000+9000+1500+2400+2000+2000+15000+1500+3000+10000
      expect(r.totalCosts, closeTo(79400, 0.001));
      expect(r.recommendedBuffer, closeTo(79400 * 0.2, 0.001));
      expect(r.totalWithBuffer, closeTo(95280, 0.001));
      expect(r.totalFunding, closeTo(75000, 0.001));
      // gap = 95280 - 75000 = 20280 (positive -> shortfall)
      expect(r.fundingGap, closeTo(20280, 0.001));
    });

    test('negative funding gap when funding exceeds required capital', () {
      final r = StartupCostCalculator.calculate(
        equipment: 1000,
        inventory: 0,
        marketing: 0,
        legal: 0,
        rent: 0,
        utilities: 0,
        insurance: 0,
        other: 0,
        employees: 0,
        salaries: 0,
        permits: 0,
        website: 0,
        workingCapital: 0,
        personalSavings: 5000,
        loanAmount: 0,
        investorFunding: 0,
      );
      expect(r.totalCosts, closeTo(1000, 0.001));
      expect(r.totalWithBuffer, closeTo(1200, 0.001));
      expect(r.totalFunding, closeTo(5000, 0.001));
      expect(r.fundingGap, closeTo(-3800, 0.001));
    });

    test('costCategories filters out zero buckets and sums combined buckets',
        () {
      final r = StartupCostCalculator.calculate(
        equipment: 5000,
        inventory: 0,
        marketing: 0,
        legal: 0,
        rent: 3000,
        utilities: 2000,
        insurance: 1000,
        other: 0,
        employees: 500,
        salaries: 1500,
        permits: 500,
        website: 0,
        workingCapital: 0,
        personalSavings: 0,
        loanAmount: 0,
        investorFunding: 0,
      );
      final byName = {for (final c in r.costCategories) c.name: c.value};
      // Only buckets with value > 0 survive.
      expect(byName.containsKey('Inventory'), isFalse);
      expect(byName.containsKey('Website & Digital'), isFalse);
      expect(byName.containsKey('Other'), isFalse);
      // Combined buckets sum their parts.
      expect(byName['Rent & Utilities'], closeTo(5000, 0.001));
      expect(byName['Insurance & Permits'], closeTo(1500, 0.001));
      expect(byName['Salaries & Staff'], closeTo(2000, 0.001));
      expect(byName['Equipment & Technology'], closeTo(5000, 0.001));
    });

    test('first category keeps its hex colour dot', () {
      final r = StartupCostCalculator.calculate(
        equipment: 100,
        inventory: 0,
        marketing: 0,
        legal: 0,
        rent: 0,
        utilities: 0,
        insurance: 0,
        other: 0,
        employees: 0,
        salaries: 0,
        permits: 0,
        website: 0,
        workingCapital: 0,
        personalSavings: 0,
        loanAmount: 0,
        investorFunding: 0,
      );
      expect(r.costCategories.first.name, 'Equipment & Technology');
      expect(r.costCategories.first.color, '#3B82F6');
    });
  });

  group('StartupCost.progressValue', () {
    test('caps funding ratio at 100', () {
      expect(StartupCostCalculator.progressValue(120000, 100000),
          closeTo(100, 0.001));
    });

    test('partial funding is the raw percentage', () {
      expect(StartupCostCalculator.progressValue(75000, 95280),
          closeTo(78.715, 0.01));
    });
  });

  group('businessTemplates', () {
    test('retail template fills the 10 cost fields', () {
      final t = StartupCostCalculator.businessTemplates['retail']!;
      expect(t.equipment, 25000);
      expect(t.inventory, 15000);
      expect(t.workingCapital, 10000);
    });

    test('online template has zero rent', () {
      expect(StartupCostCalculator.businessTemplates['online']!.rent, 0);
    });
  });
}
