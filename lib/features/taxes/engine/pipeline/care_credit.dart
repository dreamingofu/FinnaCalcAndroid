/// Child & Dependent Care Credit — Form 2441 (nonrefundable).
///
/// Eligible expenses are limited to \$3,000 (one qualifying person) or \$6,000
/// (two or more), reduced by any employer dependent-care benefits, and capped at
/// the taxpayer's (and spouse's, if MFJ) earned income. The credit rate runs
/// from 35% (AGI ≤ \$15,000) down to 20% (AGI > \$43,000). MFS who lived with
/// their spouse cannot claim it.
library;

import '../constants/credits_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import '../types/tax_return.dart';

double computeCareCredit(TaxReturn2024 r, double agi) {
  if (!r.credits.hasCareExpenses) return 0;
  if (r.filingStatus == FilingStatus.mfs && !r.livedApartFromSpouse) return 0;

  final qualifyingPersons =
      r.dependents.where((d) => d.qualifiesForCareCredit).length;
  if (qualifyingPersons == 0) return 0;

  final cap = qualifyingPersons == 1
      ? CareCredit2024.expenseCapOnePerson
      : CareCredit2024.expenseCapTwoPlus;
  final care = r.credits.care;
  final effectiveCap = _max(0, cap - _max(0, care.employerBenefits));

  final earnedLimit = r.filingStatus == FilingStatus.mfj
      ? _min(care.taxpayerEarnedIncome, care.spouseEarnedIncome)
      : care.taxpayerEarnedIncome;

  final eligible =
      _min(_min(_max(0, care.expenses), effectiveCap), earnedLimit);
  if (eligible <= 0) return 0;

  var rate = CareCredit2024.maxRate;
  if (agi > CareCredit2024.fullRateAgiCeiling) {
    final steps =
        ((agi - CareCredit2024.fullRateAgiCeiling) / CareCredit2024.rateStepIncome)
            .ceil();
    rate = _max(CareCredit2024.minRate,
        CareCredit2024.maxRate - steps * CareCredit2024.rateStep);
  }

  return dollar(eligible * rate);
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
