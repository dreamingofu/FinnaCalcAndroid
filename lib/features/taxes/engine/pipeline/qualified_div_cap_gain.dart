/// Qualified Dividends and Capital Gain Tax Worksheet (Form 1040 line 16).
///
/// Preferential income (qualified dividends + net long-term capital gain) is
/// STACKED ON TOP of ordinary-rate income and taxed at 0/15/20% using the 2024
/// breakpoints. Ordinary income is taxed first at regular rates. The final
/// result is floored at the all-ordinary tax (the worksheet's safety check), so
/// electing preferential treatment never costs more than ordinary rates.
///
/// (28% collectibles gain and unrecaptured §1250 gain route to the Schedule D
/// Tax Worksheet — not yet modeled; the simpler worksheet is exact when those
/// are zero.)
library;

import 'dart:math' as math;

import '../constants/brackets_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import 'regular_tax.dart';

class QualDivResult {
  final double tax;
  final double preferentialIncome;
  final double amountAt0;
  final double amountAt15;
  final double amountAt20;

  const QualDivResult({
    required this.tax,
    required this.preferentialIncome,
    required this.amountAt0,
    required this.amountAt15,
    required this.amountAt20,
  });
}

class PreferentialStackResult {
  final double tax;
  final double amountAt0;
  final double amountAt15;
  final double amountAt20;

  const PreferentialStackResult({
    required this.tax,
    required this.amountAt0,
    required this.amountAt15,
    required this.amountAt20,
  });
}

/// The 0/15/20% tax on `preferential` income stacked on top of `ordinaryBelow`.
/// Shared by the regular worksheet and the AMT computation (AMT uses the same
/// preferential capital-gains rates).
PreferentialStackResult preferentialStackTax(
  double ordinaryBelow,
  double preferential,
  FilingStatus status,
) {
  final breakpoints = capGainBreakpoints2024[status]!;
  final zeroRateMax = breakpoints.zeroRateMax;
  final fifteenRateMax = breakpoints.fifteenRateMax;
  final top = ordinaryBelow + preferential;
  final amountAt0 = math.max(0.0, math.min(top, zeroRateMax) - ordinaryBelow);
  final amountAt15 = math.max(
    0.0,
    math.min(top, fifteenRateMax) - math.max(ordinaryBelow, zeroRateMax),
  );
  final amountAt20 =
      math.max(0.0, top - math.max(ordinaryBelow, fifteenRateMax));
  return PreferentialStackResult(
    tax: amountAt15 * 0.15 + amountAt20 * 0.2,
    amountAt0: amountAt0,
    amountAt15: amountAt15,
    amountAt20: amountAt20,
  );
}

/// - [taxableIncome]: Form 1040 line 15 (total taxable income).
/// - [qualifiedDividends]: Qualified dividends (1099-DIV box 1b).
/// - [netCapitalGain]: Net capital gain eligible for preferential rates.
QualDivResult computeQualifiedDivCapGainTax(
  double taxableIncome,
  double qualifiedDividends,
  double netCapitalGain,
  FilingStatus status,
) {
  final ti = math.max(0.0, taxableIncome);
  final preferential =
      math.max(0.0, math.min(qualifiedDividends + netCapitalGain, ti));
  final ordinary = math.max(0.0, ti - preferential);

  final stack = preferentialStackTax(ordinary, preferential, status);
  final preferentialTax = stack.tax;
  final amountAt0 = stack.amountAt0;
  final amountAt15 = stack.amountAt15;
  final amountAt20 = stack.amountAt20;

  final ordinaryTax = computeRegularTax(ordinary, status).tax;
  final stacked = ordinaryTax + preferentialTax;

  // Safety floor: never more than taxing everything at ordinary rates.
  final allOrdinary = computeRegularTax(ti, status).tax;
  final tax = dollar(math.min(stacked, allOrdinary));

  return QualDivResult(
    tax: tax,
    preferentialIncome: preferential,
    amountAt0: amountAt0,
    amountAt15: amountAt15,
    amountAt20: amountAt20,
  );
}
