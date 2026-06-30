/// 2024 standard deduction amounts.
///
/// Source: Rev. Proc. 2023-34 §2.15; 2024 Form 1040 instructions
/// "Standard Deduction Chart" and "Standard Deduction Worksheet for Dependents".
library;

import '../types/filing.dart';

/// Base standard deduction by filing status.
const Map<FilingStatus, double> standardDeduction2024 = {
  FilingStatus.single: 14600,
  FilingStatus.mfj: 29200,
  FilingStatus.qss: 29200,
  FilingStatus.mfs: 14600,
  FilingStatus.hoh: 21900,
};

/// Additional standard deduction per "box" checked (age 65+ and/or blind).
/// Unmarried (single, HOH) get the larger amount; married statuses the smaller.
class AdditionalStdDeduction2024 {
  static const double unmarried = 1950; // single, hoh
  static const double married = 1550; // mfj, mfs, qss
}

/// Dependent standard deduction floor and earned-income bump (2024).
class DependentStdDeduction2024 {
  /// Minimum standard deduction for someone claimed as a dependent.
  static const double floor = 1300;

  /// Earned income plus this amount (capped at the regular standard deduction).
  static const double earnedIncomeBump = 450;
}

/// Returns true if the filing status uses the "married" additional-amount.
bool isMarriedStatus(FilingStatus status) {
  return status == FilingStatus.mfj ||
      status == FilingStatus.mfs ||
      status == FilingStatus.qss;
}
