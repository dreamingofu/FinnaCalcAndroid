/// Smaller nonrefundable credits: Saver's Credit (8880), Residential Clean
/// Energy (5695), Clean Vehicle (8936), and Foreign Tax Credit (1116, simplified
/// to the direct credit). Each is limited to remaining tax by the orchestrator.
library;

import '../constants/credits_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import '../types/tax_return.dart';

/// Retirement Savings Contributions Credit (Form 8880).
double computeSaversCredit(TaxReturn2024 r, double agi) {
  if (r.credits.isFullTimeStudent || r.taxpayer.claimedAsDependentByAnother) {
    return 0;
  }
  final contribution = _max(0, r.credits.retirementContributions);
  if (contribution <= 0) return 0;

  final perPersonCap = SaversCredit2024.contributionCap;
  final cap =
      r.filingStatus == FilingStatus.mfj ? perPersonCap * 2 : perPersonCap;
  final eligible = _min(contribution, cap);

  var rate = 0.0;
  for (final tier in SaversCredit2024.tiers[r.filingStatus]!) {
    if (agi <= tier.agiCeiling) {
      rate = tier.rate;
      break;
    }
  }
  return dollar(eligible * rate);
}

/// Residential Clean Energy Credit (Form 5695) — 30% of qualified property cost.
double computeCleanEnergyCredit(TaxReturn2024 r) {
  return dollar(_max(0, r.credits.cleanEnergyCost) * CleanEnergy2024.rate);
}

/// New Clean Vehicle Credit (Form 8936) — up to \$7,500, subject to MAGI caps.
double computeEvCredit(TaxReturn2024 r, double magi) {
  if (magi > EvCredit2024.magiCap[r.filingStatus]!) return 0;
  return _min(_max(0, r.credits.evCreditAmount), EvCredit2024.max);
}

/// Foreign Tax Credit (Form 1116) — simplified to the foreign tax paid.
double computeForeignTaxCredit(TaxReturn2024 r) {
  return _max(0, r.credits.foreignTaxPaid);
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
