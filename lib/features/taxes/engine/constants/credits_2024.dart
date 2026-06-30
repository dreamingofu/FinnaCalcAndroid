/// 2024 constants for the credit suite: Child & Dependent Care (2441), education
/// (8863), Saver's (8880), Residential Clean Energy (5695), Clean Vehicle (8936).
/// Sources: IRC §21/§25A/§25B/§25D/§30D; Rev. Proc. 2023-34 (education/saver's).
library;

import '../types/filing.dart';

/// Child & Dependent Care Credit (Form 2441) — not inflation-indexed.
class CareCredit2024 {
  static const double expenseCapOnePerson = 3000;
  static const double expenseCapTwoPlus = 6000;
  static const double maxRate = 0.35;
  static const double minRate = 0.2;

  /// AGI at/below which the 35% rate applies.
  static const double fullRateAgiCeiling = 15000;

  /// Rate drops 1% per $2,000 of AGI over the ceiling, to a 20% floor.
  static const double rateStepIncome = 2000;
  static const double rateStep = 0.01;
}

/// American Opportunity Tax Credit parameters (Form 8863).
class AotcParams {
  const AotcParams();

  /// 100% of the first $2,000 + 25% of the next $2,000 = $2,500 max.
  static const double firstTier = 2000;
  static const double secondTier = 2000;
  static const double secondTierRate = 0.25;
  static const double max = 2500;
  static const double refundablePortion = 0.4;
  static const double maxPriorYears = 4;
}

/// Lifetime Learning Credit parameters (Form 8863).
class LlcParams {
  const LlcParams();

  /// 20% of up to $10,000 of expenses (aggregate), max $2,000.
  static const double rate = 0.2;
  static const double expenseCap = 10000;
  static const double max = 2000;
}

/// Education credits (Form 8863).
class EducationCredits2024 {
  static const AotcParams aotc = AotcParams();
  static const LlcParams llc = LlcParams();

  /// MAGI phaseout (same range for AOTC and LLC in 2024).
  static const Map<FilingStatus, ({double start, double end})> phaseout = {
    FilingStatus.single: (start: 80000, end: 90000),
    FilingStatus.hoh: (start: 80000, end: 90000),
    FilingStatus.qss: (start: 80000, end: 90000),
    FilingStatus.mfj: (start: 160000, end: 180000),
    FilingStatus.mfs: (start: 0, end: 0), // MFS cannot claim education credits
  };
}

/// One tier of the Saver's Credit rate schedule.
class SaversCreditTier {
  final double rate;
  final double agiCeiling;
  const SaversCreditTier({required this.rate, required this.agiCeiling});
}

/// Retirement Savings Contributions Credit (Saver's Credit, Form 8880).
class SaversCredit2024 {
  static const double contributionCap = 2000; // per person; $4,000 combined for MFJ

  /// AGI ceilings for the 50% / 20% / 10% rate tiers (above the last → 0%).
  static const Map<FilingStatus, List<SaversCreditTier>> tiers = {
    FilingStatus.single: [
      SaversCreditTier(rate: 0.5, agiCeiling: 23000),
      SaversCreditTier(rate: 0.2, agiCeiling: 25000),
      SaversCreditTier(rate: 0.1, agiCeiling: 38250),
    ],
    FilingStatus.mfs: [
      SaversCreditTier(rate: 0.5, agiCeiling: 23000),
      SaversCreditTier(rate: 0.2, agiCeiling: 25000),
      SaversCreditTier(rate: 0.1, agiCeiling: 38250),
    ],
    FilingStatus.qss: [
      SaversCreditTier(rate: 0.5, agiCeiling: 23000),
      SaversCreditTier(rate: 0.2, agiCeiling: 25000),
      SaversCreditTier(rate: 0.1, agiCeiling: 38250),
    ],
    FilingStatus.hoh: [
      SaversCreditTier(rate: 0.5, agiCeiling: 34500),
      SaversCreditTier(rate: 0.2, agiCeiling: 37500),
      SaversCreditTier(rate: 0.1, agiCeiling: 57375),
    ],
    FilingStatus.mfj: [
      SaversCreditTier(rate: 0.5, agiCeiling: 46000),
      SaversCreditTier(rate: 0.2, agiCeiling: 50000),
      SaversCreditTier(rate: 0.1, agiCeiling: 76500),
    ],
  };
}

/// Residential Clean Energy Credit (Form 5695) — 30% of qualified property cost.
class CleanEnergy2024 {
  static const double rate = 0.3;
}

/// New Clean Vehicle Credit (Form 8936).
class EvCredit2024 {
  static const double max = 7500;
  static const Map<FilingStatus, double> magiCap = {
    FilingStatus.single: 150000,
    FilingStatus.hoh: 225000,
    FilingStatus.mfs: 150000,
    FilingStatus.qss: 150000,
    FilingStatus.mfj: 300000,
  };
}
