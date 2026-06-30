/// 2024 Child Tax Credit / Credit for Other Dependents / Additional CTC.
///
/// Source: Schedule 8812 (2024) and its instructions; IRC §24.
/// The $1,700 refundable cap per child for 2024 is from Rev. Proc. 2023-34 §2.06.
library;

import '../types/filing.dart';

class Ctc2024 {
  /// Maximum Child Tax Credit per qualifying child (under 17).
  static const double perChild = 2000;

  /// Credit for Other Dependents (non-CTC dependents).
  static const double perOtherDependent = 500;

  /// Maximum REFUNDABLE Additional CTC per qualifying child (2024).
  static const double refundableCapPerChild = 1700;

  /// Earned income above this amount counts toward the 15% ACTC formula.
  static const double earnedIncomeThreshold = 2500;

  /// Refundable ACTC accrues at 15% of earned income over the threshold.
  static const double earnedIncomeRate = 0.15;

  /// Phaseout: credit drops $50 for each $1,000 (or fraction) of MAGI over the threshold.
  static const double phaseoutPer1000 = 50;
  static const double phaseoutIncrement = 1000;
}

/// MAGI phaseout threshold where CTC/ODC begins to reduce.
const Map<FilingStatus, double> ctcPhaseoutThreshold2024 = {
  FilingStatus.single: 200000,
  FilingStatus.hoh: 200000,
  FilingStatus.mfs: 200000,
  FilingStatus.qss: 200000,
  FilingStatus.mfj: 400000,
};
