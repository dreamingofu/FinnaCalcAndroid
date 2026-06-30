/// State tax module types. A config-driven engine: each state supplies its 2024
/// brackets, standard deduction, and exemptions; a generic computation applies
/// them starting from federal AGI (with the common Social Security / retirement
/// subtractions). State-specific subtractions, itemizing, and credits beyond
/// these are simplified — each state carries a `note` describing the estimate's
/// limits.
library;

import '../types/filing.dart';

class StateBracket {
  final double rate;
  final double min;
  final double max;

  const StateBracket({required this.rate, required this.min, required this.max});
}

class StateInput {
  StateInput({
    required this.code,
    this.federalAgi = 0,
    this.taxableSocialSecurity = 0,
    this.retirementDistributions = 0,
    this.filingStatus = FilingStatus.single,
    this.dependents = 0,
    this.stateWithholding = 0,
    this.age65 = false,
  });

  /// [StateCode] or empty string.
  String code;

  /// Federal AGI from the federal computation.
  double federalAgi;

  /// Taxable Social Security included in federal AGI (subtracted by most
  /// states).
  double taxableSocialSecurity;

  /// Taxable retirement/pension distributions (subtracted by states that
  /// exclude them).
  double retirementDistributions;
  FilingStatus filingStatus;

  /// Number of dependents claimed.
  double dependents;

  /// State income tax withheld (W-2 box 17 + any extra).
  double stateWithholding;
  bool age65;
}

class StateConfig {
  const StateConfig({
    required this.code,
    required this.name,
    required this.hasIncomeTax,
    this.brackets,
    this.standardDeduction,
    this.personalExemption,
    this.dependentExemption,
    this.exemptionCredit,
    this.dependentExemptionCredit,
    this.taxesSocialSecurity,
    this.excludesRetirement,
    this.note,
  });

  final StateCode code;
  final String name;
  final bool hasIncomeTax;

  /// Brackets per filing status (use the builders to share across statuses).
  final Map<FilingStatus, List<StateBracket>>? brackets;
  final Map<FilingStatus, double>? standardDeduction;

  /// Per-person deduction (taxpayer + spouse).
  final double? personalExemption;

  /// Per-dependent deduction.
  final double? dependentExemption;

  /// Per-person credit applied against tax (e.g. California).
  final double? exemptionCredit;

  /// Per-dependent credit applied against tax (e.g. California).
  final double? dependentExemptionCredit;

  /// Whether the state taxes Social Security benefits (all 15 here: false).
  final bool? taxesSocialSecurity;

  /// Whether the state excludes retirement/pension income (IL, PA).
  final bool? excludesRetirement;
  final String? note;
}
