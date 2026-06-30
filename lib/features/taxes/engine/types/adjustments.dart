/// Above-the-line adjustments to income — Schedule 1 Part II.
/// These reduce gross income to arrive at AGI.
library;

class Adjustments {
  Adjustments({
    this.educatorExpenses = 0,
    this.hsaContribution = 0,
    this.hsaCoverage = 'none',
    this.sepSimpleContribution = 0,
    this.selfEmployedHealthInsurance = 0,
    this.traditionalIraContribution = 0,
    this.coveredByWorkplacePlan = false,
    this.spouseCoveredByWorkplacePlan = false,
    this.studentLoanInterest = 0,
  });

  /// Educator expenses — up to \$300 (\$600 MFJ if both educators). Sch 1
  /// line 11.
  double educatorExpenses;

  /// HSA contributions (Form 8889), excluding employer/cafeteria-plan amounts.
  /// Sch 1 line 13.
  double hsaContribution;

  /// Whether HSA coverage is self-only or family (limits differ).
  /// One of "self-only" | "family" | "none".
  String hsaCoverage;

  /// Deductible self-employed SEP/SIMPLE/qualified plan contributions. Sch 1
  /// line 16.
  double sepSimpleContribution;

  /// Self-employed health insurance premiums. Sch 1 line 17.
  double selfEmployedHealthInsurance;

  /// Traditional IRA contributions the filer wants to deduct. Sch 1 line 20.
  double traditionalIraContribution;

  /// Whether the taxpayer is covered by a workplace retirement plan (affects
  /// IRA deductibility).
  bool coveredByWorkplacePlan;

  /// Whether the spouse is covered by a workplace plan (MFJ).
  bool spouseCoveredByWorkplacePlan;

  /// Student loan interest paid — up to \$2,500, MAGI phaseout. Sch 1 line 21.
  double studentLoanInterest;
}
