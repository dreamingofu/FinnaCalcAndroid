/// Pure converter: interview answers → canonical TaxReturn2024 (the single
/// source of truth the calculation engine consumes). Keeping this conversion in
/// one pure function means the engine never depends on the UI shape.
library;

import '../engine/types/credits.dart';
import '../engine/types/filing.dart';
import '../engine/types/income.dart';
import '../engine/types/question.dart';
import '../engine/types/tax_return.dart';

double _n(Answers a, String id) => a[id] is num ? (a[id] as num).toDouble() : 0;
bool _b(Answers a, String id) => a[id] == true;
double _intOf(Answers a, String id) {
  final v = _n(a, id).floor().toDouble();
  return v > 0 ? v : 0;
}

/// Convert an age at year-end 2024 into a mid-year DOB the engine can use.
String dobFromAge(double age) {
  if (age == 0 || age <= 0) return '';
  return '${2024 - age.toInt()}-06-15';
}

TaxReturn2024 buildReturn(Answers a) {
  final r = makeEmptyReturn();

  // ---- About you ----
  r.filingStatus = _filingStatusOf(a['q_filing']);
  r.livedApartFromSpouse = _b(a, 'q_lived_apart');
  r.taxpayer.dateOfBirth = dobFromAge(_n(a, 'q_age'));
  r.taxpayer.blind = _b(a, 'q_blind');
  r.taxpayer.claimedAsDependentByAnother = _b(a, 'q_claimed_dependent');
  if (r.filingStatus == FilingStatus.mfj ||
      r.filingStatus == FilingStatus.qss) {
    r.spouse = TaxpayerInfo(
      firstName: r.taxpayer.firstName,
      lastName: r.taxpayer.lastName,
      ssn: r.taxpayer.ssn,
      dateOfBirth: dobFromAge(_n(a, 'q_spouse_age')),
      occupation: r.taxpayer.occupation,
      blind: _b(a, 'q_spouse_blind'),
      claimedAsDependentByAnother: r.taxpayer.claimedAsDependentByAnother,
    );
  }

  // ---- Dependents ----
  final kids = _intOf(a, 'q_qual_children');
  for (var i = 0; i < kids; i++) {
    r.dependents.add(Dependent(
      id: 'qc-$i',
      firstName: 'Child',
      lastName: '',
      ssn: '',
      dateOfBirth: '2018-01-01',
      relationshipType: 'child',
      relationship: 'child',
      monthsLivedWithTaxpayer: 12,
      taxpayerProvidedOverHalfSupport: true,
      qualifiesForCTC: true,
      qualifiesForODC: false,
      qualifiesForEITC: true,
      qualifiesForCareCredit: false,
    ));
  }
  final otherDeps = _intOf(a, 'q_other_deps');
  for (var i = 0; i < otherDeps; i++) {
    r.dependents.add(Dependent(
      id: 'od-$i',
      firstName: 'Dependent',
      lastName: '',
      ssn: '',
      dateOfBirth: '1990-01-01',
      relationshipType: 'relative',
      relationship: 'relative',
      monthsLivedWithTaxpayer: 12,
      taxpayerProvidedOverHalfSupport: true,
      qualifiesForCTC: false,
      qualifiesForODC: true,
      qualifiesForEITC: false,
      qualifiesForCareCredit: false,
    ));
  }

  // ---- Job income (W-2) ----
  final wages = _n(a, 'q_wages');
  final withholding = _n(a, 'q_withholding');
  if (wages > 0 || withholding > 0) {
    r.income.flags.hasW2 = true;
    r.income.w2.add(W2(
      id: 'w2-0',
      owner: 'taxpayer',
      employerName: 'Employer',
      box1Wages: wages,
      box2FederalWithholding: withholding,
      box3SsWages: wages,
      box4SsWithheld: 0,
      box5MedicareWages: wages,
      box6MedicareWithheld: 0,
      box12: [],
      statutoryEmployee: false,
      box17StateWithholding: 0,
    ));
  }

  // ---- Self-employment ----
  if (_n(a, 'q_se_profit') != 0) {
    r.income.flags.hasSelfEmployment = true;
    r.income.scheduleC.add(ScheduleC(
      id: 'c-0',
      owner: 'taxpayer',
      businessName: 'Self-employment',
      description: 'Business',
      grossReceipts: _n(a, 'q_se_profit'),
      costOfGoodsSold: 0,
      expenses: {},
      homeOfficeDeduction: 0,
      vehicleExpense: 0,
      isSSTB: _b(a, 'q_se_sstb'),
    ));
  }
  r.adjustments.selfEmployedHealthInsurance = _n(a, 'q_se_health');

  // ---- Investments ----
  final interest = _n(a, 'q_interest');
  final taxExempt = _n(a, 'q_tax_exempt');
  if (interest > 0 || taxExempt > 0) {
    r.income.flags.hasInterest = true;
    r.income.f1099Int.add(Form1099Int(
      id: 'int-0',
      payer: 'Bank',
      box1Interest: interest,
      box3UsTreasuryInterest: 0,
      box8TaxExemptInterest: taxExempt,
      box4FederalWithholding: 0,
    ));
  }
  final ordDiv = _n(a, 'q_ord_div');
  final qualDiv = _n(a, 'q_qual_div');
  final capDist = _n(a, 'q_capgain_dist');
  if (ordDiv > 0 || qualDiv > 0 || capDist > 0) {
    r.income.flags.hasDividends = true;
    r.income.f1099Div.add(Form1099Div(
      id: 'div-0',
      payer: 'Brokerage',
      box1aOrdinaryDividends: ordDiv > qualDiv ? ordDiv : qualDiv,
      box1bQualifiedDividends: qualDiv,
      box2aCapitalGainDistributions: capDist,
      box4FederalWithholding: 0,
    ));
  }
  final ltcg = _n(a, 'q_ltcg');
  final stcg = _n(a, 'q_stcg');
  if (ltcg != 0 || stcg != 0) {
    r.income.flags.hasCapitalGains = true;
    if (ltcg != 0) {
      r.income.f1099B.add(CapitalTransaction(
        id: 'b-lt',
        description: 'Long-term',
        proceeds: ltcg > 0 ? ltcg : 0,
        costBasis: ltcg < 0 ? -ltcg : 0,
        longTerm: true,
      ));
    }
    if (stcg != 0) {
      r.income.f1099B.add(CapitalTransaction(
        id: 'b-st',
        description: 'Short-term',
        proceeds: stcg > 0 ? stcg : 0,
        costBasis: stcg < 0 ? -stcg : 0,
        longTerm: false,
      ));
    }
  }

  // ---- Retirement & Social Security ----
  if (_n(a, 'q_ss_benefits') > 0) {
    r.income.flags.hasSocialSecurity = true;
    r.income.f1099Ssa.add(Form1099Ssa(
      id: 'ssa-0',
      owner: 'taxpayer',
      box5NetBenefits: _n(a, 'q_ss_benefits'),
      federalWithholding: 0,
    ));
  }
  if (_n(a, 'q_retire_taxable') > 0) {
    r.income.flags.hasRetirementDistributions = true;
    r.income.f1099R.add(Form1099R(
      id: 'r-0',
      payer: 'Plan',
      box1GrossDistribution: _n(a, 'q_retire_taxable'),
      box2aTaxableAmount: _n(a, 'q_retire_taxable'),
      box4FederalWithholding: 0,
      box7DistributionCode: _b(a, 'q_retire_early') ? '1' : '7',
      iraSepSimple: false,
    ));
  }

  // ---- Other income ----
  if (_n(a, 'q_unemployment') > 0) {
    r.income.flags.hasUnemployment = true;
    r.income.f1099G.add(Form1099G(
      id: 'g-0',
      payer: 'State',
      box1Unemployment: _n(a, 'q_unemployment'),
      box2StateRefund: 0,
      box4FederalWithholding: 0,
    ));
  }
  if (_n(a, 'q_other_income') > 0) {
    r.income.flags.hasOtherIncome = true;
    r.income.otherIncome = _n(a, 'q_other_income');
  }

  // ---- Adjustments ----
  r.adjustments.studentLoanInterest = _n(a, 'q_student_loan');
  r.adjustments.educatorExpenses = _n(a, 'q_educator');
  final hsa = a['q_hsa_coverage'];
  r.adjustments.hsaCoverage =
      hsa == 'self-only' || hsa == 'family' ? hsa as String : 'none';
  r.adjustments.hsaContribution = _n(a, 'q_hsa_contribution');
  r.adjustments.traditionalIraContribution = _n(a, 'q_ira_contribution');
  r.adjustments.coveredByWorkplacePlan = _b(a, 'q_ira_covered');

  // ---- Deductions ----
  if (_b(a, 'q_itemize')) {
    r.itemized.mortgageInterest = _n(a, 'q_mortgage_interest');
    r.itemized.mortgageBalance = _n(a, 'q_mortgage_balance');
    r.itemized.stateLocalIncomeOrSalesTax = _n(a, 'q_salt');
    r.itemized.realEstateTaxes = _n(a, 'q_property_tax');
    r.itemized.charitableCash = _n(a, 'q_charitable');
    r.itemized.medicalExpenses = _n(a, 'q_medical');
  }

  // ---- Credits ----
  final careChildren = _intOf(a, 'q_care_children');
  if (_n(a, 'q_care_expenses') > 0 && careChildren > 0) {
    r.credits.hasCareExpenses = true;
    final seProfit = _n(a, 'q_se_profit');
    final earned = wages + (seProfit > 0 ? seProfit : 0);
    r.credits.care = CareCredit(
      expenses: _n(a, 'q_care_expenses'),
      taxpayerEarnedIncome: earned,
      spouseEarnedIncome: earned,
      employerBenefits: 0,
    );
    var marked = 0;
    for (final d in r.dependents) {
      if (marked >= careChildren) break;
      if (d.qualifiesForCTC) {
        d.qualifiesForCareCredit = true;
        marked++;
      }
    }
    for (var i = marked; i < careChildren; i++) {
      r.dependents.add(Dependent(
        id: 'care-$i',
        firstName: 'Child',
        lastName: '',
        ssn: '',
        dateOfBirth: '2020-01-01',
        relationshipType: 'child',
        relationship: 'child',
        monthsLivedWithTaxpayer: 12,
        taxpayerProvidedOverHalfSupport: true,
        qualifiesForCTC: false,
        qualifiesForODC: false,
        qualifiesForEITC: false,
        qualifiesForCareCredit: true,
      ));
    }
  }
  if (_n(a, 'q_edu_expenses') > 0) {
    r.credits.hasEducationExpenses = true;
    r.credits.students = [
      EducationStudent(
        id: 's0',
        name: 'Student',
        qualifiedExpenses: _n(a, 'q_edu_expenses'),
        aotcEligible: _b(a, 'q_edu_aotc'),
        priorAotcYears: 0,
        felonyDrugConviction: false,
      ),
    ];
  }
  r.credits.retirementContributions = _n(a, 'q_savers_contrib');
  r.credits.cleanEnergyCost = _n(a, 'q_clean_energy');
  r.credits.evCreditAmount = _n(a, 'q_ev_credit');

  // ---- State residency ----
  if (a['q_state'] is String && a['q_state'] != '') {
    r.residency.state = a['q_state'] as StateCode;
    r.residency.stateWithholding = _n(a, 'q_state_withholding');
  }

  // ---- Payments ----
  r.payments.estimatedPayments = _n(a, 'q_est_payments');
  r.payments.additionalWithholding = _n(a, 'q_extra_withholding');
  if (_n(a, 'q_prior_tax') > 0) r.payments.priorYearTax = _n(a, 'q_prior_tax');
  if (_n(a, 'q_prior_agi') > 0) r.payments.priorYearAgi = _n(a, 'q_prior_agi');

  return r;
}

FilingStatus _filingStatusOf(Object? v) {
  switch (v) {
    case 'single':
      return FilingStatus.single;
    case 'mfj':
      return FilingStatus.mfj;
    case 'mfs':
      return FilingStatus.mfs;
    case 'hoh':
      return FilingStatus.hoh;
    case 'qss':
      return FilingStatus.qss;
    default:
      return FilingStatus.single;
  }
}
