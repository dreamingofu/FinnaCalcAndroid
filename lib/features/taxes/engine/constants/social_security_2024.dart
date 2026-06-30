/// Social Security benefit taxability — 2024.
///
/// Source: IRC §86; 2024 Form 1040 instructions "Social Security Benefits
/// Worksheet"; IRS Pub 915. Base amounts are NOT inflation-indexed (fixed since
/// 1993). Up to 85% of benefits can be taxable.
library;

import '../types/filing.dart';

class SsTaxability2024 {
  static const double maxInclusionRate = 0.85;
  static const double firstTierRate = 0.5;
}

/// Base amounts (worksheet lines 8 and 11):
///  - base1: below this, no benefits are taxable.
///  - base2: above this, the 85% tier applies.
/// MFS taxpayers who lived WITH their spouse use $0/$0 (almost always 85% taxable).
({double base1, double base2}) ssBaseAmounts(
  FilingStatus status,
  bool livedApartFromSpouse,
) {
  if (status == FilingStatus.mfj) return (base1: 32000, base2: 44000);
  if (status == FilingStatus.mfs && !livedApartFromSpouse) {
    return (base1: 0, base2: 0);
  }
  // single, hoh, qss, and mfs-who-lived-apart-all-year
  return (base1: 25000, base2: 34000);
}
