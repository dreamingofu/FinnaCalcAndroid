/// Qualified Business Income deduction (§199A) — 2024 thresholds.
/// Source: Rev. Proc. 2023-34 §2.10; Forms 8995 / 8995-A.
library;

import '../types/filing.dart';

class Qbi2024 {
  static const double rate = 0.2;

  /// Taxable-income threshold where the SSTB/W-2 limitations begin to phase in.
  static const Map<FilingStatus, double> threshold = {
    FilingStatus.single: 191950,
    FilingStatus.hoh: 191950,
    FilingStatus.mfs: 191950,
    FilingStatus.qss: 191950,
    FilingStatus.mfj: 383900,
  };

  /// Phase-in range above the threshold (fully limited at threshold + range).
  static const Map<FilingStatus, double> phaseInRange = {
    FilingStatus.single: 50000,
    FilingStatus.hoh: 50000,
    FilingStatus.mfs: 50000,
    FilingStatus.qss: 50000,
    FilingStatus.mfj: 100000,
  };
}
