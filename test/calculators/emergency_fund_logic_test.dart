import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/emergency_fund/emergency_fund_logic.dart';

void main() {
  group('Emergency fund', () {
    test('months target with APY computes target, progress and time to goal',
        () {
      final r = EmergencyFundCalculator.calculate(
        monthlyExpenses: 5000,
        currentSavings: 10000,
        targetType: 'months',
        targetValue: 6,
        monthlySavings: 500,
        interestRate: 4.5,
      );
      expect(r.targetAmount, closeTo(30000, 0.0001));
      expect(r.stillNeeded, closeTo(20000, 0.0001));
      expect(r.percentComplete, closeTo(33.3333, 0.001));
      expect(r.monthsOfExpensesCovered, closeTo(2.0, 0.0001));
      expect(r.targetMonths, closeTo(6.0, 0.0001));
      expect(r.timeToGoal, 38); // ceil of ~37.34
      expect(r.projectedInterest, closeTo(1000, 0.0001));
    });

    test('specific dollar amount with 0% APY uses linear time to goal', () {
      final r = EmergencyFundCalculator.calculate(
        monthlyExpenses: 4000,
        currentSavings: 5000,
        targetType: 'amount',
        targetValue: 20000,
        monthlySavings: 1000,
        interestRate: 0,
      );
      expect(r.targetAmount, closeTo(20000, 0.0001));
      expect(r.stillNeeded, closeTo(15000, 0.0001));
      expect(r.targetMonths, closeTo(5.0, 0.0001)); // 20000 / 4000
      expect(r.timeToGoal, 15); // ceil(15000 / 1000)
      expect(r.projectedInterest, closeTo(0, 0.0001));
    });

    test('already funded clamps progress to 100 and needs nothing', () {
      final r = EmergencyFundCalculator.calculate(
        monthlyExpenses: 3000,
        currentSavings: 25000,
        targetType: 'months',
        targetValue: 6,
        monthlySavings: 500,
        interestRate: 4.5,
      );
      expect(r.targetAmount, closeTo(18000, 0.0001));
      expect(r.stillNeeded, closeTo(0, 0.0001));
      expect(r.percentComplete, closeTo(100, 0.0001));
      expect(r.timeToGoal, 0); // stillNeeded == 0 short-circuits
      expect(r.projectedInterest, closeTo(0, 0.0001));
    });

    test('no monthly contribution leaves time to goal at zero', () {
      final r = EmergencyFundCalculator.calculate(
        monthlyExpenses: 5000,
        currentSavings: 0,
        targetType: 'months',
        targetValue: 6,
        monthlySavings: 0,
        interestRate: 4.5,
      );
      expect(r.stillNeeded, closeTo(30000, 0.0001));
      expect(r.timeToGoal, 0);
      expect(r.projectedInterest, closeTo(0, 0.0001));
    });

    test('non-positive monthly expenses throws CalcException', () {
      expect(
        () => EmergencyFundCalculator.calculate(
          monthlyExpenses: 0,
          currentSavings: 1000,
          targetType: 'months',
          targetValue: 6,
          monthlySavings: 500,
          interestRate: 4.5,
        ),
        throwsA(isA<CalcException>()),
      );
    });
  });
}
