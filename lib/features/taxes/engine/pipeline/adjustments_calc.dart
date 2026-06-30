/// Above-the-line adjustments (Schedule 1 Part II) → reduce income to AGI.
///
/// Some adjustments are "fixed" (independent of AGI): educator, HSA, SE-tax 50%
/// deduction, self-employed health insurance, and SEP/SIMPLE. Two are MAGI-
/// dependent and therefore resolved inside the orchestrator's fixed-point loop
/// (because their MAGI includes taxable Social Security): the traditional IRA
/// deduction and the student loan interest deduction.
library;

import 'dart:math' as math;

import '../constants/filing_thresholds_2024.dart';
import '../constants/retirement_2024.dart';
import '../types/filing.dart';
import '../types/tax_return.dart';

/// Parse a date-of-birth string the way JavaScript's `new Date(str)` does for
/// the ISO date strings this engine uses: a bare `YYYY-MM-DD` is interpreted as
/// UTC midnight. Returns null for empty/invalid input (mirrors `NaN` getTime).
DateTime? _parseDob(String dateOfBirth) {
  if (dateOfBirth.isEmpty) return null;
  final dob = DateTime.tryParse(dateOfBirth);
  if (dob == null) return null;
  // DateTime.tryParse treats a bare date as LOCAL; JS treats it as UTC. Rebuild
  // in UTC from the calendar fields so the comparison matches the TS source.
  return DateTime.utc(
    dob.year,
    dob.month,
    dob.day,
    dob.hour,
    dob.minute,
    dob.second,
    dob.millisecond,
  );
}

/// Age 55+ at end of 2024 (HSA catch-up) → born on/before 1969-12-31.
bool _isAge55For2024(String dateOfBirth) {
  final dob = _parseDob(dateOfBirth);
  if (dob == null) return false;
  return dob.millisecondsSinceEpoch <=
      DateTime.utc(1969, 12, 31).millisecondsSinceEpoch;
}

/// Age 50+ at end of 2024 (IRA catch-up) → born on/before 1974-12-31.
bool isAge50For2024(String dateOfBirth) {
  final dob = _parseDob(dateOfBirth);
  if (dob == null) return false;
  return dob.millisecondsSinceEpoch <=
      DateTime.utc(1974, 12, 31).millisecondsSinceEpoch;
}

/// Educator expense — capped at $300 (per-educator); MFJ-both is Phase-later.
double educatorDeduction(TaxReturn2024 r) {
  return math.min(
    math.max(0.0, r.adjustments.educatorExpenses),
    EducatorExpense2024.perEducator,
  );
}

/// HSA deduction (Form 8889) — capped by the coverage limit plus 55+ catch-up.
double hsaDeduction(TaxReturn2024 r) {
  final cov = r.adjustments.hsaCoverage;
  if (cov == 'none') return 0;
  var limit = cov == 'family' ? Hsa2024.family : Hsa2024.selfOnly;
  if (_isAge55For2024(r.taxpayer.dateOfBirth)) limit += Hsa2024.catchUp;
  return math.min(math.max(0.0, r.adjustments.hsaContribution), limit);
}

/// Self-employed health insurance — limited to available net SE profit.
double seHealthDeduction(
  TaxReturn2024 r,
  double totalNetSe,
  double seTaxDeduction,
  double sepContribution,
) {
  final ceiling = math.max(
    0.0,
    totalNetSe - seTaxDeduction - math.max(0.0, sepContribution),
  );
  return math.min(
    math.max(0.0, r.adjustments.selfEmployedHealthInsurance),
    ceiling,
  );
}

/// Traditional IRA deduction with the 2024 MAGI phaseout. Only phases out if the
/// contributor is an active workplace-plan participant (or, for MFJ, the spouse
/// is). A non-zero phased deduction is rounded UP to $10 and floored at $200.
double iraDeduction(
  double contribution,
  double magi,
  FilingStatus status,
  bool coveredByPlan,
  bool spouseCoveredByPlan,
  bool age50,
) {
  final limit =
      age50 ? Ira2024.contributionLimitAge50 : Ira2024.contributionLimit;
  final eligible = math.min(math.max(0.0, contribution), limit);
  if (eligible <= 0) return 0;

  ({double start, double end})? range;
  if (coveredByPlan) {
    if (status == FilingStatus.mfj || status == FilingStatus.qss) {
      range = Ira2024Phaseout.coveredMfj;
    } else if (status == FilingStatus.mfs) {
      range = Ira2024Phaseout.coveredMfs;
    } else {
      range = Ira2024Phaseout.coveredSingleHoh;
    }
  } else if ((status == FilingStatus.mfj || status == FilingStatus.qss) &&
      spouseCoveredByPlan) {
    range = Ira2024Phaseout.spouseCoveredMfj;
  } else if (status == FilingStatus.mfs && spouseCoveredByPlan) {
    range = Ira2024Phaseout.coveredMfs;
  }

  // No coverage that triggers a phaseout → fully deductible.
  if (range == null) return eligible;

  if (magi <= range.start) return eligible;
  if (magi >= range.end) return 0;

  final ratio = (range.end - magi) / (range.end - range.start);
  var deduction = eligible * ratio;
  deduction = (deduction / Ira2024.roundUpTo).ceil() * Ira2024.roundUpTo;
  if (deduction > 0 && deduction < Ira2024.minPhasedDeduction) {
    deduction = Ira2024.minPhasedDeduction;
  }
  return math.min(deduction, eligible);
}

/// Student loan interest deduction with the 2024 MAGI phaseout (MFS ineligible).
double studentLoanInterestDeduction(
  double paid,
  double magi,
  FilingStatus status,
) {
  if (status == FilingStatus.mfs) return 0;
  final eligible =
      math.min(math.max(0.0, paid), StudentLoanInterest2024.maxDeduction);
  if (eligible <= 0) return 0;
  final range = StudentLoanInterest2024.phaseout[status]!;
  final start = range.start;
  final end = range.end;
  if (magi <= start) return eligible;
  if (magi >= end) return 0;
  return eligible - eligible * ((magi - start) / (end - start));
}
