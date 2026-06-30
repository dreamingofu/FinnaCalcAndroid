/// Test fixture builders — concise helpers to assemble a TaxReturn2024 from a
/// clean empty return. Keeps the golden tests readable.
library;

import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/types/income.dart';
import 'package:finnacalc/features/taxes/engine/types/tax_return.dart';

TaxReturn2024 baseReturn(FilingStatus status) {
  final r = makeEmptyReturn();
  r.filingStatus = status;
  return r;
}

TaxReturn2024 withW2(
  TaxReturn2024 r,
  double wages, [
  double withholding = 0,
  String owner = 'taxpayer',
]) {
  r.income.flags.hasW2 = true;
  r.income.w2.add(W2(
    id: 'w2-${r.income.w2.length}',
    owner: owner,
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
  return r;
}

TaxReturn2024 addQualifyingChild(TaxReturn2024 r) {
  r.dependents.add(Dependent(
    id: 'dep-${r.dependents.length}',
    firstName: 'Child',
    lastName: 'Test',
    ssn: '',
    dateOfBirth: '2018-01-01',
    relationshipType: 'child',
    relationship: 'son',
    monthsLivedWithTaxpayer: 12,
    taxpayerProvidedOverHalfSupport: true,
    qualifiesForCTC: true,
    qualifiesForODC: false,
    qualifiesForEITC: true,
    qualifiesForCareCredit: false,
  ));
  return r;
}

TaxReturn2024 withScheduleC(
  TaxReturn2024 r,
  double net, [
  String owner = 'taxpayer',
]) {
  r.income.flags.hasSelfEmployment = true;
  r.income.scheduleC.add(ScheduleC(
    id: 'c-${r.income.scheduleC.length}',
    owner: owner,
    businessName: 'Business',
    description: 'Consulting',
    grossReceipts: net,
    costOfGoodsSold: 0,
    expenses: {},
    homeOfficeDeduction: 0,
    vehicleExpense: 0,
    isSSTB: false,
  ));
  return r;
}

TaxReturn2024 withCapitalTransaction(
  TaxReturn2024 r,
  double proceeds,
  double costBasis,
  bool longTerm, [
  double washSaleAdjustment = 0,
]) {
  r.income.flags.hasCapitalGains = true;
  r.income.f1099B.add(CapitalTransaction(
    id: 'b-${r.income.f1099B.length}',
    description: 'Sale',
    proceeds: proceeds,
    costBasis: costBasis,
    longTerm: longTerm,
    washSaleAdjustment: washSaleAdjustment,
  ));
  return r;
}

TaxReturn2024 withSocialSecurity(TaxReturn2024 r, double benefits) {
  r.income.flags.hasSocialSecurity = true;
  r.income.f1099Ssa.add(Form1099Ssa(
    id: 'ssa-${r.income.f1099Ssa.length}',
    owner: 'taxpayer',
    box5NetBenefits: benefits,
    federalWithholding: 0,
  ));
  return r;
}

TaxReturn2024 withQualifiedDividends(
  TaxReturn2024 r,
  double ordinary,
  double qualified,
) {
  r.income.flags.hasDividends = true;
  r.income.f1099Div.add(Form1099Div(
    id: 'div-${r.income.f1099Div.length}',
    payer: 'Brokerage',
    box1aOrdinaryDividends: ordinary,
    box1bQualifiedDividends: qualified,
    box2aCapitalGainDistributions: 0,
    box4FederalWithholding: 0,
  ));
  return r;
}

TaxReturn2024 addOtherDependent(TaxReturn2024 r) {
  r.dependents.add(Dependent(
    id: 'dep-${r.dependents.length}',
    firstName: 'Parent',
    lastName: 'Test',
    ssn: '',
    dateOfBirth: '1955-01-01',
    relationshipType: 'relative',
    relationship: 'parent',
    monthsLivedWithTaxpayer: 12,
    taxpayerProvidedOverHalfSupport: true,
    qualifiesForCTC: false,
    qualifiesForODC: true,
    qualifiesForEITC: false,
    qualifiesForCareCredit: false,
  ));
  return r;
}
