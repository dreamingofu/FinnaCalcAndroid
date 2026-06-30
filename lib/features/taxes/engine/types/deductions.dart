/// Itemized deductions — Schedule A.
/// The engine compares the itemized total (with all IRS limits applied) against
/// the standard deduction and uses the larger, unless MFS forces a match.
library;

class ItemizedDeductions {
  ItemizedDeductions({
    this.medicalExpenses = 0,
    this.stateLocalIncomeOrSalesTax = 0,
    this.realEstateTaxes = 0,
    this.personalPropertyTaxes = 0,
    this.mortgageInterest = 0,
    this.mortgageBalance = 0,
    this.mortgageAfterDec2017 = true,
    this.charitableCash = 0,
    this.charitableNonCash = 0,
    this.casualtyLosses = 0,
  });

  /// Unreimbursed medical/dental expenses (subject to 7.5%-of-AGI floor). Sch A
  /// line 1.
  double medicalExpenses;

  /// State & local income (or sales) taxes paid. Part of the SALT cap. Sch A
  /// line 5a.
  double stateLocalIncomeOrSalesTax;

  /// Real estate (property) taxes. Part of the SALT cap. Sch A line 5b.
  double realEstateTaxes;

  /// Personal property taxes. Part of the SALT cap. Sch A line 5c.
  double personalPropertyTaxes;

  /// Home mortgage interest (subject to the \$750k acquisition-debt limit).
  /// Sch A line 8.
  double mortgageInterest;

  /// Mortgage balance — used to apply the \$750k interest-deductibility limit.
  double mortgageBalance;

  /// Whether the mortgage originated after 12/15/2017 (\$750k limit vs \$1M
  /// grandfathered).
  bool mortgageAfterDec2017;

  /// Cash charitable contributions (60%-of-AGI limit). Sch A line 11.
  double charitableCash;

  /// Non-cash / appreciated-property contributions (30%-of-AGI limit). Sch A
  /// line 12.
  double charitableNonCash;

  /// Casualty/theft losses from a federally declared disaster. Sch A line 15.
  double casualtyLosses;
}
