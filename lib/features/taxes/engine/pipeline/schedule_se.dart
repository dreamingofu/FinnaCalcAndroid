/// Self-employment tax — Schedule SE.
///
/// Net earnings = net SE profit × 92.35%. If under \$400, no SE tax. The 12.4%
/// Social Security portion applies up to the wage base, REDUCED by W-2 Social
/// Security wages already taxed (so a high W-2 earner with a side business
/// doesn't pay SS tax twice). The 2.9% Medicare portion has no cap. Computed
/// per person. Half of the total SE tax is deductible above the line
/// (Schedule 1 line 15).
library;

import 'dart:math' as math;

import '../constants/filing_thresholds_2024.dart';

class SeTaxResult {
  final double seTax;
  final double deduction;

  /// Net SE earnings (after the 92.35% factor) — used by QBI later.
  final double netEarnings;

  const SeTaxResult({
    required this.seTax,
    required this.deduction,
    required this.netEarnings,
  });
}

SeTaxResult computeSelfEmploymentTax(
  ({double taxpayer, double spouse}) netSeByOwner,
  ({double taxpayer, double spouse}) w2SsWagesByOwner,
) {
  var seTax = 0.0;
  var netEarnings = 0.0;

  for (final owner in const ['taxpayer', 'spouse']) {
    final net =
        owner == 'spouse' ? netSeByOwner.spouse : netSeByOwner.taxpayer;
    if (net <= 0) continue;
    final earnings = net * SeTax2024.netEarningsFactor;
    if (earnings < 400) continue;
    netEarnings += earnings;

    final w2SsWages =
        owner == 'spouse' ? w2SsWagesByOwner.spouse : w2SsWagesByOwner.taxpayer;
    final ssWageRemaining = math.max(
      0.0,
      SeTax2024.socialSecurityWageBase - w2SsWages,
    );
    final ssBase = math.min(earnings, ssWageRemaining);
    final ssPortion = ssBase * SeTax2024.socialSecurityRate;
    final medicarePortion = earnings * SeTax2024.medicareRate;
    seTax += ssPortion + medicarePortion;
  }

  return SeTaxResult(
    seTax: seTax,
    deduction: seTax * SeTax2024.deductibleFraction,
    netEarnings: netEarnings,
  );
}
