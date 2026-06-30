/// Standard vs itemized deduction (Form 1040 line 12; Schedule A).
///
/// Computes the full standard deduction (base + age-65/blind additions, with
/// the dependent cap) and a Schedule A itemized total (medical 7.5% floor, SALT
/// cap, mortgage interest, charitable AGI limits), then picks the larger —
/// unless the return forces itemizing (e.g. an MFS spouse who itemized).
library;

import '../constants/filing_thresholds_2024.dart';
import '../constants/standard_deductions_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import '../types/tax_return.dart';
import 'regular_tax.dart';

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

/// For TY2024, a person is treated as 65+ if born before January 2, 1960.
bool isConsidered65For2024(String dateOfBirth) {
  final dob = _parseDob(dateOfBirth);
  if (dob == null) return false;
  // Born on or before 1960-01-01 → considered 65 by year-end 2024.
  return dob.millisecondsSinceEpoch <=
      DateTime.utc(1960, 1, 1).millisecondsSinceEpoch;
}

/// Count the age-65/blind "boxes" that drive the additional standard deduction.
int _countAdditionalBoxes(TaxReturn2024 r) {
  var boxes = 0;
  if (isConsidered65For2024(r.taxpayer.dateOfBirth)) boxes++;
  if (r.taxpayer.blind) boxes++;
  // Spouse's boxes count for MFJ / QSS (and MFS only in narrow cases not
  // modeled here).
  if ((r.filingStatus == FilingStatus.mfj ||
          r.filingStatus == FilingStatus.qss) &&
      r.spouse != null) {
    if (isConsidered65For2024(r.spouse!.dateOfBirth)) boxes++;
    if (r.spouse!.blind) boxes++;
  }
  return boxes;
}

double computeStandardDeduction(TaxReturn2024 r, double earnedIncome) {
  final status = r.filingStatus;
  final base = standardDeduction2024[status]!;
  final additionalPerBox = isMarriedStatus(status)
      ? AdditionalStdDeduction2024.married
      : AdditionalStdDeduction2024.unmarried;
  final additional = _countAdditionalBoxes(r) * additionalPerBox;

  // Dependent standard-deduction cap: limited to the greater of $1,300 or
  // (earned income + $450), but never more than the regular base.
  var baseDeduction = base;
  if (r.taxpayer.claimedAsDependentByAnother) {
    final limited = [
      DependentStdDeduction2024.floor,
      earnedIncome + DependentStdDeduction2024.earnedIncomeBump,
    ].reduce((a, b) => a > b ? a : b);
    baseDeduction = base < limited ? base : limited;
  }

  return baseDeduction + additional;
}

double computeItemizedDeduction(TaxReturn2024 r, double agi) {
  final it = r.itemized;
  final status = r.filingStatus;

  final medical = nonNeg(it.medicalExpenses - agi * medicalAgiFloor2024);

  final saltRaw =
      it.stateLocalIncomeOrSalesTax + it.realEstateTaxes + it.personalPropertyTaxes;
  final saltCap = status == FilingStatus.mfs ? SaltCap2024.mfs : SaltCap2024.standard;
  final salt = saltRaw < saltCap ? saltRaw : saltCap;

  // Mortgage interest: limited to the acquisition-debt cap. When the loan
  // balance exceeds the limit, only the proportional share of interest is
  // deductible ($750k for loans after 12/15/2017, $1M grandfathered; halved
  // MFS).
  final double mortgageLimit = it.mortgageAfterDec2017
      ? (status == FilingStatus.mfs
          ? MortgageDebtLimit2024.postDec2017Mfs
          : MortgageDebtLimit2024.postDec2017)
      : (status == FilingStatus.mfs
          ? MortgageDebtLimit2024.grandfatheredMfs
          : MortgageDebtLimit2024.grandfathered);
  final mortgage = it.mortgageBalance > mortgageLimit
      ? nonNeg(it.mortgageInterest) * (mortgageLimit / it.mortgageBalance)
      : nonNeg(it.mortgageInterest);

  final charitableCashCap = agi * CharitableLimits2024.cashPctOfAgi;
  final charitableCash =
      it.charitableCash < charitableCashCap ? it.charitableCash : charitableCashCap;
  final charitableNonCashCap = agi * CharitableLimits2024.nonCashPctOfAgi;
  final charitableNonCash = it.charitableNonCash < charitableNonCashCap
      ? it.charitableNonCash
      : charitableNonCashCap;

  final casualty = nonNeg(it.casualtyLosses);

  return medical + salt + mortgage + charitableCash + charitableNonCash + casualty;
}

class DeductionResult {
  final double standard;
  final double itemized;
  final String used; // "standard" | "itemized"
  final double amount;

  /// Federal tax saved by the chosen deduction vs the alternative (estimate).
  final double itemizedSavings;

  const DeductionResult({
    required this.standard,
    required this.itemized,
    required this.used,
    required this.amount,
    required this.itemizedSavings,
  });
}

DeductionResult computeDeduction(
  TaxReturn2024 r,
  double agi,
  double earnedIncome,
) {
  final FilingStatus status = r.filingStatus;
  final standard = computeStandardDeduction(r, earnedIncome);
  final itemized = computeItemizedDeduction(r, agi);

  final useItemized = r.forceItemize || itemized > standard;
  final String used = useItemized ? 'itemized' : 'standard';
  final amount = used == 'itemized' ? itemized : standard;

  // Estimate the tax difference between the two deductions (ignoring QBI /
  // preferential rates, which Phase 1 doesn't apply) for the optimizer display.
  final taxStandard = computeRegularTax(nonNeg(agi - standard), status).tax;
  final taxItemized = computeRegularTax(nonNeg(agi - itemized), status).tax;
  final itemizedSavings = nonNeg(taxStandard - taxItemized);

  return DeductionResult(
    standard: standard,
    itemized: itemized,
    used: used,
    amount: amount,
    itemizedSavings: itemizedSavings,
  );
}
