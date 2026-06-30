/// TaxReturn2024 — the single source of truth that flows through the entire
/// engine: state -> engine -> result -> UI. Every input the calculator reads
/// lives here. The companion `makeEmptyReturn()` produces a clean starting
/// state.
library;

import 'adjustments.dart';
import 'credits.dart';
import 'deductions.dart';
import 'filing.dart';
import 'income.dart';
import 'payments.dart';

/// Return metadata block.
class ReturnMeta {
  ReturnMeta({this.taxYear = 2024, this.lastEdited = ''});

  /// Always 2024 for this engine.
  int taxYear;
  String lastEdited;
}

/// Residency block (drives the state engine, later phase).
class Residency {
  Residency({
    this.state = '',
    this.partYearResident = false,
    this.stateWithholding = 0,
  });

  /// [StateCode] or empty string.
  String state;
  bool partYearResident;
  double stateWithholding;
}

class TaxReturn2024 {
  TaxReturn2024({
    required this.meta,
    required this.taxpayer,
    this.spouse,
    required this.address,
    required this.filingStatus,
    required this.dependents,
    required this.residency,
    required this.livedApartFromSpouse,
    required this.income,
    required this.adjustments,
    required this.itemized,
    required this.forceItemize,
    required this.credits,
    required this.payments,
    this.bank,
  });

  ReturnMeta meta;
  TaxpayerInfo taxpayer;
  TaxpayerInfo? spouse;
  Address address;
  FilingStatus filingStatus;
  List<Dependent> dependents;
  Residency residency;

  /// MFS only: lived apart from spouse for ALL of 2024 (affects SS taxability
  /// base amounts).
  bool livedApartFromSpouse;
  IncomeData income;
  Adjustments adjustments;
  ItemizedDeductions itemized;

  /// Force itemizing even when standard is larger (e.g. MFS spouse itemized).
  bool forceItemize;
  CreditInputs credits;
  Payments payments;

  /// SENSITIVE — never persisted.
  BankInfo? bank;
}

TaxpayerInfo _emptyTaxpayer() {
  return TaxpayerInfo(
    firstName: '',
    lastName: '',
    ssn: '',
    dateOfBirth: '',
    occupation: '',
    blind: false,
    claimedAsDependentByAnother: false,
  );
}

/// Factory for a fresh, empty 2024 return.
TaxReturn2024 makeEmptyReturn() {
  return TaxReturn2024(
    meta: ReturnMeta(taxYear: 2024, lastEdited: ''),
    taxpayer: _emptyTaxpayer(),
    spouse: null,
    address: Address(line1: '', city: '', state: '', zip: ''),
    filingStatus: FilingStatus.single,
    dependents: [],
    residency: Residency(state: '', partYearResident: false, stateWithholding: 0),
    livedApartFromSpouse: false,
    income: IncomeData(
      w2: [],
      f1099Int: [],
      f1099Div: [],
      f1099B: [],
      f1099R: [],
      f1099Ssa: [],
      f1099Nec: [],
      f1099Misc: [],
      f1099G: [],
      f1099Sa: [],
      scheduleC: [],
      scheduleE: [],
      otherIncome: 0,
      capitalLossCarryoverShort: 0,
      capitalLossCarryoverLong: 0,
      flags: IncomeFlags(
        hasW2: false,
        hasInterest: false,
        hasDividends: false,
        hasCapitalGains: false,
        hasRetirementDistributions: false,
        hasSocialSecurity: false,
        hasSelfEmployment: false,
        hasRental: false,
        hasUnemployment: false,
        hasOtherIncome: false,
      ),
    ),
    adjustments: Adjustments(
      educatorExpenses: 0,
      hsaContribution: 0,
      hsaCoverage: 'none',
      sepSimpleContribution: 0,
      selfEmployedHealthInsurance: 0,
      traditionalIraContribution: 0,
      coveredByWorkplacePlan: false,
      spouseCoveredByWorkplacePlan: false,
      studentLoanInterest: 0,
    ),
    itemized: ItemizedDeductions(
      medicalExpenses: 0,
      stateLocalIncomeOrSalesTax: 0,
      realEstateTaxes: 0,
      personalPropertyTaxes: 0,
      mortgageInterest: 0,
      mortgageBalance: 0,
      mortgageAfterDec2017: true,
      charitableCash: 0,
      charitableNonCash: 0,
      casualtyLosses: 0,
    ),
    forceItemize: false,
    credits: CreditInputs(
      students: [],
      hasEducationExpenses: false,
      care: CareCredit(
        expenses: 0,
        taxpayerEarnedIncome: 0,
        spouseEarnedIncome: 0,
        employerBenefits: 0,
      ),
      hasCareExpenses: false,
      retirementContributions: 0,
      isFullTimeStudent: false,
      cleanEnergyCost: 0,
      evCreditAmount: 0,
      foreignTaxPaid: 0,
      hasMarketplaceCoverage: false,
      advancePremiumTaxCredit: 0,
      premiumTaxCreditAllowed: 0,
    ),
    payments: Payments(
      additionalWithholding: 0,
      estimatedPayments: 0,
    ),
    bank: null,
  );
}
