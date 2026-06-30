/// Miscellaneous 2024 thresholds shared across pipeline steps.
/// Sources cited per constant. (Phase 1 uses the capital-loss limit; the SE /
/// Medicare / NIIT values are wired in Phases 2-3.)
library;

import '../types/filing.dart';

/// Self-employment tax (Schedule SE). Source: 2024 Schedule SE; SSA wage base.
class SeTax2024 {
  /// Net earnings multiplier (Schedule SE line 4a): 92.35%.
  static const double netEarningsFactor = 0.9235;

  /// Social Security portion rate (12.4%).
  static const double socialSecurityRate = 0.124;

  /// Medicare portion rate (2.9%).
  static const double medicareRate = 0.029;

  /// 2024 Social Security wage base (max earnings subject to the 12.4%).
  static const double socialSecurityWageBase = 168600;

  /// Deductible fraction of SE tax (above-the-line).
  static const double deductibleFraction = 0.5;
}

/// Additional Medicare Tax (Form 8959). Source: IRC §3101(b)(2); Form 8959 (2024).
class AdditionalMedicare2024 {
  static const double rate = 0.009;
  static const Map<FilingStatus, double> thresholds = {
    FilingStatus.single: 200000,
    FilingStatus.hoh: 200000,
    FilingStatus.qss: 200000,
    FilingStatus.mfj: 250000,
    FilingStatus.mfs: 125000,
  };
}

/// Net Investment Income Tax (Form 8960). Source: IRC §1411; Form 8960 (2024).
class Niit2024 {
  static const double rate = 0.038;
  static const Map<FilingStatus, double> thresholds = {
    FilingStatus.single: 200000,
    FilingStatus.hoh: 200000,
    FilingStatus.qss: 250000,
    FilingStatus.mfj: 250000,
    FilingStatus.mfs: 125000,
  };
}

/// Annual capital loss deduction limit (Schedule D). Source: IRC §1211(b).
const Map<FilingStatus, double> capitalLossLimit2024 = {
  FilingStatus.single: 3000,
  FilingStatus.mfj: 3000,
  FilingStatus.qss: 3000,
  FilingStatus.hoh: 3000,
  FilingStatus.mfs: 1500,
};

/// Medical expense AGI floor for itemized deductions. Source: IRC §213(a).
const double medicalAgiFloor2024 = 0.075;

/// SALT (state & local tax) deduction cap. Source: IRC §164(b)(6).
class SaltCap2024 {
  static const double standard = 10000;
  static const double mfs = 5000;
}

/// Charitable AGI limits. Source: IRC §170(b).
class CharitableLimits2024 {
  static const double cashPctOfAgi = 0.6;
  static const double nonCashPctOfAgi = 0.3;
}

/// Mortgage acquisition-debt limits for interest deductibility. Source: IRC §163(h)(3).
class MortgageDebtLimit2024 {
  /// Loans after 12/15/2017.
  static const double postDec2017 = 750000;
  static const double postDec2017Mfs = 375000;

  /// Grandfathered loans on/before 12/15/2017.
  static const double grandfathered = 1000000;
  static const double grandfatheredMfs = 500000;
}

/// Student loan interest deduction. Source: IRC §221; Rev. Proc. 2023-34 §2.21.
class StudentLoanInterest2024 {
  static const double maxDeduction = 2500;
  static const Map<FilingStatus, ({double start, double end})> phaseout = {
    FilingStatus.single: (start: 80000, end: 95000),
    FilingStatus.hoh: (start: 80000, end: 95000),
    FilingStatus.qss: (start: 80000, end: 95000),
    FilingStatus.mfj: (start: 165000, end: 195000),
    // MFS cannot claim the student loan interest deduction.
    FilingStatus.mfs: (start: 0, end: 0),
  };
}

/// Educator expense above-the-line deduction. Source: IRC §62(a)(2)(D); Rev. Proc. 2023-34.
class EducatorExpense2024 {
  static const double perEducator = 300;
}

/// Additional tax on early retirement distributions. Source: IRC §72(t); Form 5329.
class EarlyWithdrawalPenalty2024 {
  static const double rate = 0.1;

  /// Box 7 codes that mean "early distribution, no known exception applies".
  static const List<String> earlyNoExceptionCodes = ['1', 'J', 'S'];
}
