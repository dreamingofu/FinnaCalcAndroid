/// Child Tax Credit, Credit for Other Dependents, and the refundable Additional
/// Child Tax Credit — Schedule 8812 (2024).
///
/// Flow:
///  1. Tentative credit = \$2,000 × qualifying children + \$500 × other
///     dependents.
///  2. MAGI phaseout: −\$50 per \$1,000 (or fraction) over the threshold.
///  3. Nonrefundable part = min(credit after phaseout, tax available).
///  4. ACTC (refundable) = min(leftover credit, \$1,700 × qualifying children,
///     15% × (earned income − \$2,500)). ODC is never refundable.
///
/// The 3-or-more-children Social-Security-tax alternative for ACTC is added in
/// Phase 3; the 15% earned-income method governs the common case.
library;

import '../constants/ctc_2024.dart';
import '../round.dart';
import '../types/tax_return.dart';

class ChildTaxCreditResult {
  final double qualifyingChildren;
  final double otherDependents;
  final double tentativeCredit;
  final double creditAfterPhaseout;

  /// Nonrefundable CTC/ODC actually applied against tax.
  final double nonrefundable;

  /// Refundable Additional Child Tax Credit.
  final double additionalChildTaxCredit;

  const ChildTaxCreditResult({
    required this.qualifyingChildren,
    required this.otherDependents,
    required this.tentativeCredit,
    required this.creditAfterPhaseout,
    required this.nonrefundable,
    required this.additionalChildTaxCredit,
  });
}

ChildTaxCreditResult computeChildTaxCredit(
  TaxReturn2024 r,
  double magi,
  double taxAvailable,
  double earnedIncome,
) {
  final qualifyingChildren =
      r.dependents.where((d) => d.qualifiesForCTC).length.toDouble();
  final otherDependents =
      r.dependents.where((d) => d.qualifiesForODC).length.toDouble();

  final tentativeCredit = qualifyingChildren * Ctc2024.perChild +
      otherDependents * Ctc2024.perOtherDependent;

  // MAGI phaseout — excess rounded UP to the next \$1,000 before applying \$50.
  final threshold = ctcPhaseoutThreshold2024[r.filingStatus]!;
  var creditAfterPhaseout = tentativeCredit;
  if (magi > threshold) {
    final steps = ((magi - threshold) / Ctc2024.phaseoutIncrement).ceil();
    creditAfterPhaseout =
        nonNeg(tentativeCredit - steps * Ctc2024.phaseoutPer1000);
  }

  // Nonrefundable part limited to tax available.
  final nonrefundable = _min(creditAfterPhaseout, nonNeg(taxAvailable));

  // Refundable ACTC on the leftover (qualifying children only).
  final leftover = nonNeg(creditAfterPhaseout - nonrefundable);
  final refundableCap = qualifyingChildren * Ctc2024.refundableCapPerChild;
  final earnedFormula = nonNeg(
    (earnedIncome - Ctc2024.earnedIncomeThreshold) * Ctc2024.earnedIncomeRate,
  );
  final additionalChildTaxCredit =
      _min(_min(leftover, refundableCap), earnedFormula);

  return ChildTaxCreditResult(
    qualifyingChildren: qualifyingChildren,
    otherDependents: otherDependents,
    tentativeCredit: tentativeCredit,
    creditAfterPhaseout: creditAfterPhaseout,
    nonrefundable: nonrefundable,
    additionalChildTaxCredit: additionalChildTaxCredit,
  );
}

double _min(double a, double b) => a < b ? a : b;
