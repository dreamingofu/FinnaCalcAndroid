import 'dart:math' as math;

import '../../../core/util/parse.dart';

class EmergencyFundResult {
  const EmergencyFundResult({
    required this.targetAmount,
    required this.currentSavings,
    required this.stillNeeded,
    required this.percentComplete,
    required this.monthsOfExpensesCovered,
    required this.timeToGoal,
    required this.monthlyContribution,
    required this.projectedInterest,
    required this.targetMonths,
  });

  final double targetAmount;
  final double currentSavings;
  final double stillNeeded;
  final double percentComplete;
  final double monthsOfExpensesCovered;
  final double timeToGoal;
  final double monthlyContribution;
  final double projectedInterest;
  final double targetMonths;
}

/// Pure emergency-fund math, transcribed 1:1 from
/// `app/emergency-fund-calculator/page.tsx`.
class EmergencyFundCalculator {
  const EmergencyFundCalculator._();

  /// [targetType] is `'months'` or `'amount'`.
  static EmergencyFundResult calculate({
    required double monthlyExpenses,
    required double currentSavings,
    required String targetType,
    required double targetValue,
    required double monthlySavings,
    required double interestRate,
  }) {
    final expenses = monthlyExpenses;
    final savings = currentSavings;
    final value = targetValue;
    final monthlyContribution = monthlySavings;
    final rate = interestRate;

    if (expenses <= 0) {
      throw const CalcException('Monthly expenses must be greater than 0.');
    }

    final targetAmount = targetType == 'months' ? expenses * value : value;
    final stillNeeded = math.max(0, targetAmount - savings).toDouble();
    final percentComplete = targetAmount > 0
        ? math.min(100, savings / targetAmount * 100).toDouble()
        : 0.0;

    // Time to reach goal with compound interest (solve for n in FV-of-annuity)
    var timeToGoal = 0.0;
    var projectedInterest = 0.0;
    if (stillNeeded > 0 && monthlyContribution > 0) {
      final monthlyRate = rate / 100 / 12;
      if (monthlyRate > 0) {
        // log(1 + stillNeeded * r / PMT) / log(1 + r)
        timeToGoal =
            math.log(1 + stillNeeded * monthlyRate / monthlyContribution) /
                math.log(1 + monthlyRate);
      } else {
        timeToGoal = stillNeeded / monthlyContribution;
      }
      timeToGoal = timeToGoal.ceilToDouble();
      // Interest earned = total FV minus total contributions
      // FV ≈ stillNeeded (by definition), total contributions = PMT * timeToGoal
      projectedInterest =
          math.max(0, stillNeeded - monthlyContribution * timeToGoal)
              .toDouble();
    }

    return EmergencyFundResult(
      targetAmount: targetAmount,
      currentSavings: savings,
      stillNeeded: stillNeeded,
      percentComplete: percentComplete,
      monthsOfExpensesCovered: expenses > 0 ? savings / expenses : 0,
      timeToGoal: timeToGoal,
      monthlyContribution: monthlyContribution,
      projectedInterest: projectedInterest,
      targetMonths: targetType == 'months'
          ? value
          : (expenses > 0 ? targetAmount / expenses : 0),
    );
  }
}
