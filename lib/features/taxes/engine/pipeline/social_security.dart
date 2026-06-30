/// Taxable portion of Social Security benefits — IRS Social Security Benefits
/// Worksheet (closed form).
///
/// Provisional ("combined") income = all other income + tax-exempt interest +
/// ½ of benefits − certain above-the-line adjustments (Schedule 1 lines 11–20,
/// 23, 25; NOTABLY excluding student loan interest, line 21). Then:
///   - ≤ base1:        none taxable
///   - base1..base2:   min(50% of excess over base1, 50% of benefits)
///   - > base2:        min(85% of excess over base2 + tier1, 85% of benefits)
/// where tier1 = min(50% of benefits, 50% of (base2 − base1)).
library;

import 'dart:math' as math;

import '../constants/social_security_2024.dart';
import '../types/filing.dart';

double computeTaxableSocialSecurity({
  required double benefits,
  required double otherIncome,
  required double taxExemptInterest,

  /// Schedule 1 lines 11–20, 23, 25 (excludes student loan interest).
  required double adjustmentsForProvisional,
  required FilingStatus status,
  required bool livedApartFromSpouse,
}) {
  if (benefits <= 0) return 0;

  final half = SsTaxability2024.firstTierRate * benefits;
  final provisional =
      otherIncome + taxExemptInterest + half - adjustmentsForProvisional;
  final bases = ssBaseAmounts(status, livedApartFromSpouse);
  final base1 = bases.base1;
  final base2 = bases.base2;

  if (provisional <= base1) return 0;

  if (provisional <= base2) {
    return math.min(
      SsTaxability2024.firstTierRate * (provisional - base1),
      half,
    );
  }

  final tier1 =
      math.min(half, SsTaxability2024.firstTierRate * (base2 - base1));
  return math.min(
    SsTaxability2024.maxInclusionRate * (provisional - base2) + tier1,
    SsTaxability2024.maxInclusionRate * benefits,
  );
}
