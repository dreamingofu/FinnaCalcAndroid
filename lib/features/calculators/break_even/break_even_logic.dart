import '../../../core/util/parse.dart';

/// Result of a break-even analysis, transcribed 1:1 from
/// `app/break-even-calculator/page.tsx`.
class BreakEvenResult {
  const BreakEvenResult({
    required this.breakEvenUnits,
    required this.breakEvenRevenue,
    required this.contributionMargin,
    required this.contributionMarginRatio,
    required this.unitsForTargetProfit,
    required this.targetProfitRevenue,
    required this.seasonalBreakEven,
    required this.seasonalTargetUnits,
    required this.marginOfSafety,
    required this.adjustedCMValid,
  });

  /// `Math.ceil(breakEvenUnits)`.
  final int breakEvenUnits;
  final double breakEvenRevenue;
  final double contributionMargin;
  final double contributionMarginRatio;

  /// `Math.ceil(unitsForTargetProfit)` or null when `adjustedCM <= 0`.
  final int? unitsForTargetProfit;
  final double targetProfitRevenue;

  /// `Math.ceil(seasonalBreakEven)`.
  final int seasonalBreakEven;

  /// `Math.ceil(seasonalTargetUnits)` or null when `adjustedCM <= 0`.
  final int? seasonalTargetUnits;
  final double marginOfSafety;
  final bool adjustedCMValid;
}

/// Pure break-even math, transcribed 1:1 from
/// `app/break-even-calculator/page.tsx`.
class BreakEvenCalculator {
  const BreakEvenCalculator._();

  static BreakEvenResult calculate({
    required double fixedCosts,
    required double variableCostPerUnit,
    required double pricePerUnit,
    required double seasonalityFactor,
    required double targetProfitMargin,
  }) {
    final fixed = fixedCosts;
    final variableCost = variableCostPerUnit;
    final price = pricePerUnit;
    final seasonality = seasonalityFactor;
    final targetMargin = targetProfitMargin;

    if (price <= variableCost) {
      throw const CalcException(
          'Selling price must be greater than variable cost per unit.');
    }
    if (targetMargin >= 100) {
      throw const CalcException('Target profit margin must be less than 100%.');
    }

    final contributionMargin = price - variableCost;
    final contributionMarginRatio = (contributionMargin / price) * 100;

    // Standard CVP break-even
    final breakEvenUnits = fixed / contributionMargin;
    final breakEvenRevenue = breakEvenUnits * price;

    // Units for target net profit margin on revenue (standard accounting
    // definition).
    final adjustedCM = price * (1 - targetMargin / 100) - variableCost;
    double unitsForTargetProfit = 0;
    double targetProfitRevenueAmount = 0;
    if (adjustedCM > 0) {
      unitsForTargetProfit = fixed / adjustedCM;
      targetProfitRevenueAmount = unitsForTargetProfit * price;
    }

    // Seasonality adjustments
    final seasonalFactor = 1 + seasonality / 100;
    final seasonalBreakEven = breakEvenUnits * seasonalFactor;
    final seasonalTargetUnits = unitsForTargetProfit * seasonalFactor;

    // Margin of safety = how much above break-even the target is, as % of
    // target.
    final marginOfSafety = unitsForTargetProfit > 0
        ? ((unitsForTargetProfit - breakEvenUnits) / unitsForTargetProfit) * 100
        : 0.0;

    return BreakEvenResult(
      breakEvenUnits: breakEvenUnits.ceil(),
      breakEvenRevenue: breakEvenRevenue,
      contributionMargin: contributionMargin,
      contributionMarginRatio: contributionMarginRatio,
      unitsForTargetProfit:
          unitsForTargetProfit > 0 ? unitsForTargetProfit.ceil() : null,
      targetProfitRevenue: targetProfitRevenueAmount,
      seasonalBreakEven: seasonalBreakEven.ceil(),
      seasonalTargetUnits:
          unitsForTargetProfit > 0 ? seasonalTargetUnits.ceil() : null,
      marginOfSafety: marginOfSafety.toDouble(),
      adjustedCMValid: adjustedCM > 0,
    );
  }
}
