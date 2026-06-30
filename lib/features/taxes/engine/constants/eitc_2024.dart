/// Earned Income Tax Credit — 2024 parameters.
///
/// Source: Rev. Proc. 2023-34 §2.06. The IRS publishes an EITC table, but it is
/// the $50-bucket evaluation of this piecewise-linear formula:
///   phase-in:  rate × earned income (up to the maximum credit)
///   plateau:   the maximum credit
///   phase-out: maximum − phaseoutRate × (income − phaseout threshold)
/// Non-MFJ thresholds; MFJ adds the marriage bump to the phase-out threshold.
library;

class EitcBracket {
  /// Earned income at which the maximum credit is reached (phase-in ceiling).
  final double earnedIncomeAmount;
  final double maxCredit;
  final double phaseInRate;
  final double phaseoutRate;

  /// Phase-out start (AGI/earned income) for non-MFJ filers.
  final double phaseoutThreshold;

  /// Phase-out start for MFJ filers.
  final double phaseoutThresholdMfj;

  const EitcBracket({
    required this.earnedIncomeAmount,
    required this.maxCredit,
    required this.phaseInRate,
    required this.phaseoutRate,
    required this.phaseoutThreshold,
    required this.phaseoutThresholdMfj,
  });
}

/// Indexed by number of qualifying children (0, 1, 2, 3 = "3 or more").
const List<EitcBracket> eitc2024 = [
  EitcBracket(
    earnedIncomeAmount: 8260,
    maxCredit: 632,
    phaseInRate: 0.0765,
    phaseoutRate: 0.0765,
    phaseoutThreshold: 10330,
    phaseoutThresholdMfj: 17250,
  ),
  EitcBracket(
    earnedIncomeAmount: 12390,
    maxCredit: 4213,
    phaseInRate: 0.34,
    phaseoutRate: 0.1598,
    phaseoutThreshold: 22720,
    phaseoutThresholdMfj: 29640,
  ),
  EitcBracket(
    earnedIncomeAmount: 17400,
    maxCredit: 6960,
    phaseInRate: 0.4,
    phaseoutRate: 0.2106,
    phaseoutThreshold: 22720,
    phaseoutThresholdMfj: 29640,
  ),
  EitcBracket(
    earnedIncomeAmount: 17400,
    maxCredit: 7830,
    phaseInRate: 0.45,
    phaseoutRate: 0.2106,
    phaseoutThreshold: 22720,
    phaseoutThresholdMfj: 29640,
  ),
];

/// Disqualifying investment income limit (2024).
const double eitcInvestmentIncomeLimit2024 = 11600;

/// Age bounds for the childless EITC (at least 25, under 65).
class EitcChildlessAge {
  static const double min = 25;
  static const double maxExclusive = 65;
}
