/// Earned Income Tax Credit — Schedule EIC (refundable).
///
/// The credit is figured from BOTH earned income and AGI; if AGI exceeds the
/// phase-out threshold, the smaller of the two results is used. Disqualifiers:
/// investment income over \$11,600; MFS who did not live apart from their
/// spouse; and (childless only) being outside the 25–64 age band.
library;

import '../constants/eitc_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import '../types/tax_return.dart';

/// Credit amount at a given income level for a bracket (piecewise-linear
/// formula).
double _eitcAtIncome(double income, int bracketIndex, FilingStatus status) {
  if (income <= 0) return 0;
  final b = eitc2024[bracketIndex];
  final threshold =
      status == FilingStatus.mfj ? b.phaseoutThresholdMfj : b.phaseoutThreshold;
  final phaseIn = _min(b.maxCredit, b.phaseInRate * income);
  if (income <= threshold) return phaseIn;
  return _max(0, b.maxCredit - b.phaseoutRate * (income - threshold));
}

class EitcResult {
  final double credit;
  final bool eligible;
  final String? disqualReason;

  const EitcResult({
    required this.credit,
    required this.eligible,
    this.disqualReason,
  });
}

EitcResult computeEitc({
  required TaxReturn2024 r,
  required double earnedIncome,
  required double agi,
  required double investmentIncome,
  double? taxpayerAge,
}) {
  final status = r.filingStatus;

  // MFS is eligible only if the taxpayer lived apart from their spouse.
  if (status == FilingStatus.mfs && !r.livedApartFromSpouse) {
    return const EitcResult(
      credit: 0,
      eligible: false,
      disqualReason: 'MFS filers must have lived apart from their spouse.',
    );
  }
  if (investmentIncome > eitcInvestmentIncomeLimit2024) {
    return EitcResult(
      credit: 0,
      eligible: false,
      disqualReason:
          'Investment income over \$${_toLocaleString(eitcInvestmentIncomeLimit2024)} disqualifies the EITC.',
    );
  }
  if (earnedIncome <= 0) {
    return const EitcResult(credit: 0, eligible: false);
  }

  final qualifyingChildren = r.dependents.where((d) => d.qualifiesForEITC).length;
  final bracketIndex = _minInt(qualifyingChildren, 3);

  // Childless filers must be 25–64; only enforced when age is known.
  if (bracketIndex == 0 &&
      taxpayerAge != null &&
      (taxpayerAge < EitcChildlessAge.min ||
          taxpayerAge >= EitcChildlessAge.maxExclusive)) {
    return const EitcResult(
      credit: 0,
      eligible: false,
      disqualReason: 'Childless EITC requires age 25–64.',
    );
  }

  final threshold = status == FilingStatus.mfj
      ? eitc2024[bracketIndex].phaseoutThresholdMfj
      : eitc2024[bracketIndex].phaseoutThreshold;

  final byEarned = _eitcAtIncome(earnedIncome, bracketIndex, status);
  final credit = agi <= threshold
      ? byEarned
      : _min(byEarned, _eitcAtIncome(agi, bracketIndex, status));

  return EitcResult(credit: dollar(credit), eligible: credit > 0);
}

/// Format a whole number with comma thousands separators (JS toLocaleString).
String _toLocaleString(double value) {
  final s = value.toStringAsFixed(0);
  final neg = s.startsWith('-');
  final digits = neg ? s.substring(1) : s;
  final buf = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buf.write(',');
    buf.write(digits[i]);
  }
  return neg ? '-$buf' : buf.toString();
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
int _minInt(int a, int b) => a < b ? a : b;
