/// Retirement & HSA contribution/deduction limits — 2024.
///
/// Sources: Rev. Proc. 2023-23 (HSA, Form 8889); Notice 2023-75 / IRS 2024
/// limits (IRA); IRC §219(g) (IRA deduction phaseout). Phaseout ranges are MAGI.
library;

/// MAGI phaseout ranges for the traditional IRA deduction (only apply if the
/// contributor is an active plan participant, or — for the spouse range — if the
/// non-covered spouse's covered partner triggers it).
class Ira2024Phaseout {
  const Ira2024Phaseout();

  static const ({double start, double end}) coveredSingleHoh =
      (start: 77000, end: 87000);
  static const ({double start, double end}) coveredMfj =
      (start: 123000, end: 143000);

  /// Contributor NOT covered, but spouse IS covered (MFJ).
  static const ({double start, double end}) spouseCoveredMfj =
      (start: 230000, end: 240000);
  static const ({double start, double end}) coveredMfs = (start: 0, end: 10000);
}

/// Traditional IRA contribution limits and deduction phaseouts.
class Ira2024 {
  static const double contributionLimit = 7000;

  /// Age 50+ catch-up brings the limit to $8,000.
  static const double contributionLimitAge50 = 8000;

  /// MAGI phaseout ranges. See [Ira2024Phaseout].
  static const Ira2024Phaseout phaseout = Ira2024Phaseout();

  /// Special floor: a non-zero phased-out deduction is at least $200, rounded up to $10.
  static const double minPhasedDeduction = 200;
  static const double roundUpTo = 10;
}

/// HSA contribution limits (Form 8889).
class Hsa2024 {
  static const double selfOnly = 4150;
  static const double family = 8300;

  /// Age 55+ catch-up.
  static const double catchUp = 1000;
  static const double catchUpAge = 55;
}
