/// Education credits — Form 8863.
///
/// AOTC: 100% of the first \$2,000 + 25% of the next \$2,000 (max \$2,500) per
/// eligible student, 40% refundable. Lifetime Learning Credit: 20% of up to
/// \$10,000 of expenses (aggregate, max \$2,000), nonrefundable. Both phase out
/// by MAGI; MFS cannot claim either. A student claimed for AOTC isn't also
/// counted for LLC.
library;

import '../constants/credits_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import '../types/tax_return.dart';

class EducationResult {
  final double nonrefundable;
  final double refundable;

  const EducationResult({required this.nonrefundable, required this.refundable});
}

EducationResult computeEducationCredits(TaxReturn2024 r, double magi) {
  if (!r.credits.hasEducationExpenses || r.filingStatus == FilingStatus.mfs) {
    return const EducationResult(nonrefundable: 0, refundable: 0);
  }

  final phase = EducationCredits2024.phaseout[r.filingStatus]!;
  final factor = magi <= phase.start
      ? 1.0
      : magi >= phase.end
          ? 0.0
          : (phase.end - magi) / (phase.end - phase.start);
  if (factor <= 0) {
    return const EducationResult(nonrefundable: 0, refundable: 0);
  }

  var aotc = 0.0;
  var llcExpenses = 0.0;
  for (final s in r.credits.students) {
    final aotcEligible = s.aotcEligible &&
        s.priorAotcYears < AotcParams.maxPriorYears &&
        !s.felonyDrugConviction;
    if (aotcEligible) {
      final first = _min(s.qualifiedExpenses, AotcParams.firstTier);
      final second = _min(_max(0, s.qualifiedExpenses - first),
              AotcParams.secondTier) *
          AotcParams.secondTierRate;
      aotc += _min(first + second, AotcParams.max);
    } else {
      llcExpenses += _max(0, s.qualifiedExpenses);
    }
  }

  final llc = _min(
    _min(llcExpenses, LlcParams.expenseCap) * LlcParams.rate,
    LlcParams.max,
  );

  final aotcAfter = aotc * factor;
  final llcAfter = llc * factor;

  final refundable = dollar(aotcAfter * AotcParams.refundablePortion);
  final nonrefundable =
      dollar(aotcAfter * (1 - AotcParams.refundablePortion) + llcAfter);
  return EducationResult(nonrefundable: nonrefundable, refundable: refundable);
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
