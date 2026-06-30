/// Credit inputs. CTC/ODC and EITC are derived from `dependents`; the blocks
/// here capture the extra data the other credits need.
library;

/// A student for education credits (Form 8863).
class EducationStudent {
  EducationStudent({
    required this.id,
    this.name = '',
    this.qualifiedExpenses = 0,
    this.aotcEligible = false,
    this.priorAotcYears = 0,
    this.felonyDrugConviction = false,
  });

  String id;
  String name;

  /// Qualified tuition & related expenses paid in 2024.
  double qualifiedExpenses;

  /// At least half-time, in a degree program, first 4 years → AOTC eligible.
  bool aotcEligible;

  /// Number of prior years AOTC has been claimed (4-year lifetime limit).
  double priorAotcYears;

  /// Has a felony drug conviction (disqualifies AOTC).
  bool felonyDrugConviction;
}

/// Child & Dependent Care Credit inputs (Form 2441).
class CareCredit {
  CareCredit({
    this.expenses = 0,
    this.taxpayerEarnedIncome = 0,
    this.spouseEarnedIncome = 0,
    this.employerBenefits = 0,
  });

  /// Total qualifying care expenses paid.
  double expenses;

  /// Taxpayer's earned income (limits the credit).
  double taxpayerEarnedIncome;

  /// Spouse's earned income (MFJ; both must have earned income).
  double spouseEarnedIncome;

  /// Dependent care benefits received from an employer (W-2 box 10).
  double employerBenefits;
}

/// Container for all credit-specific inputs.
class CreditInputs {
  CreditInputs({
    List<EducationStudent>? students,
    this.hasEducationExpenses = false,
    CareCredit? care,
    this.hasCareExpenses = false,
    this.retirementContributions = 0,
    this.isFullTimeStudent = false,
    this.cleanEnergyCost = 0,
    this.evCreditAmount = 0,
    this.foreignTaxPaid = 0,
    this.hasMarketplaceCoverage = false,
    this.advancePremiumTaxCredit = 0,
    this.premiumTaxCreditAllowed = 0,
  })  : students = students ?? [],
        care = care ?? CareCredit();

  /// Education credits.
  List<EducationStudent> students;
  bool hasEducationExpenses;

  /// Child & dependent care.
  CareCredit care;
  bool hasCareExpenses;

  /// Retirement Savings Contributions Credit (Form 8880) — voluntary
  /// contributions.
  double retirementContributions;
  bool isFullTimeStudent;

  /// Residential Clean Energy Credit (Form 5695) — qualified property cost.
  double cleanEnergyCost;

  /// New clean vehicle / EV credit (Form 8936).
  double evCreditAmount;

  /// Foreign tax paid (Form 1116 / direct credit).
  double foreignTaxPaid;

  /// ACA marketplace: advance Premium Tax Credit reconciliation
  /// (Form 8962 / 1095-A).
  bool hasMarketplaceCoverage;
  double advancePremiumTaxCredit;
  double premiumTaxCreditAllowed;
}
