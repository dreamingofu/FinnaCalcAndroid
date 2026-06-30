import 'dart:math' as math;

import '../../../core/util/parse.dart';

/// Result of an ROI calculation, transcribed 1:1 from
/// `app/roi-calculator/page.tsx`.
class RoiResult {
  const RoiResult({
    required this.totalReturn,
    required this.simpleROI,
    required this.cagr,
    required this.displayedROI,
    required this.initial,
    required this.finalValue,
    required this.time,
    required this.dividendIncome,
    required this.afterTaxReturn,
    required this.realROI,
    required this.realValue,
    required this.totalTaxes,
  });

  final double totalReturn;
  final double simpleROI;
  final double cagr;
  final double displayedROI;
  final double initial;
  final double finalValue;
  final double time;
  final double dividendIncome;
  final double afterTaxReturn;
  final double realROI;
  final double realValue;
  final double totalTaxes;
}

/// Pure ROI math, transcribed 1:1 from `app/roi-calculator/page.tsx`.
class RoiCalculator {
  const RoiCalculator._();

  /// [calculationType] is "annualized" (CAGR) or "simple" (total %).
  /// [investmentType] is collected by the UI but unused in the math, exactly
  /// as the web does.
  static RoiResult calculate({
    required double initial,
    required double finalValue,
    required double time,
    required String calculationType,
    required double dividend,
    required double inflation,
    required double tax,
  }) {
    if (initial <= 0) {
      throw const CalcException('Initial investment must be greater than 0');
    }

    final totalReturn = finalValue - initial;
    final simpleROI = (totalReturn / initial) * 100;

    // CAGR — correct annualized ROI
    final cagr = time > 0
        ? (math.pow(finalValue / initial, 1 / time) - 1) * 100
        : simpleROI;

    final displayedROI = calculationType == 'annualized' ? cagr : simpleROI;

    // Dividend income
    final annualDividendIncome = initial * (dividend / 100);
    final totalDividendIncome = annualDividendIncome * time;

    // After-tax returns
    final capitalGainsTax = totalReturn > 0 ? totalReturn * (tax / 100) : 0;
    final dividendTax = totalDividendIncome * (tax / 100);
    final afterTaxReturn =
        totalReturn + totalDividendIncome - capitalGainsTax - dividendTax;

    // Fisher equation for real ROI (inflation-adjusted) — more accurate than
    // simple subtraction
    final realROI = ((1 + cagr / 100) / (1 + inflation / 100) - 1) * 100;
    final realValue = initial * math.pow(1 + realROI / 100, time);

    return RoiResult(
      totalReturn: totalReturn,
      simpleROI: simpleROI,
      cagr: cagr.toDouble(),
      displayedROI: displayedROI.toDouble(),
      initial: initial,
      finalValue: finalValue,
      time: time,
      dividendIncome: totalDividendIncome,
      afterTaxReturn: afterTaxReturn.toDouble(),
      realROI: realROI.toDouble(),
      realValue: realValue.toDouble(),
      totalTaxes: (capitalGainsTax + dividendTax).toDouble(),
    );
  }
}
