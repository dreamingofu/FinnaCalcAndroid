import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/types/credits.dart';
import 'package:finnacalc/features/taxes/engine/types/tax_return.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/care_credit.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/education_credits.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/other_credits.dart';
import 'fixtures/builders.dart';

TaxReturn2024 withCarePerson(TaxReturn2024 r) {
  r.dependents.add(Dependent(
    id: 'care-${r.dependents.length}',
    firstName: 'Kid',
    lastName: '',
    ssn: '',
    dateOfBirth: '2020-01-01',
    relationshipType: 'child',
    relationship: 'child',
    monthsLivedWithTaxpayer: 12,
    taxpayerProvidedOverHalfSupport: true,
    qualifiesForCTC: true,
    qualifiesForODC: false,
    qualifiesForEITC: true,
    qualifiesForCareCredit: true,
  ));
  return r;
}

TaxReturn2024 careReturn(
    FilingStatus status, double expenses, int persons) {
  var r = baseReturn(status);
  r.credits.hasCareExpenses = true;
  r.credits.care = CareCredit(
    expenses: expenses,
    taxpayerEarnedIncome: 100000,
    spouseEarnedIncome: 100000,
    employerBenefits: 0,
  );
  for (var i = 0; i < persons; i++) {
    r = withCarePerson(r);
  }
  return r;
}

TaxReturn2024 eduReturn(FilingStatus status, double expenses, bool aotc) {
  final r = baseReturn(status);
  r.credits.hasEducationExpenses = true;
  r.credits.students = [
    EducationStudent(
      id: 's0',
      name: 'Student',
      qualifiedExpenses: expenses,
      aotcEligible: aotc,
      priorAotcYears: 0,
      felonyDrugConviction: false,
    ),
  ];
  return r;
}

TaxReturn2024 saverReturn(double contribution) {
  final r = baseReturn(FilingStatus.single);
  r.credits.retirementContributions = contribution;
  return r;
}

void main() {
  group('Child & Dependent Care Credit (Form 2441)', () {
    test('1 person, \$4,000 expenses, AGI \$20,000 → 32% of the \$3,000 cap = \$960',
        () {
      expect(computeCareCredit(careReturn(FilingStatus.single, 4000, 1), 20000),
          closeTo(960, 0.5));
    });
    test('2 persons, \$7,000 expenses, AGI \$50,000 → 20% of the \$6,000 cap = \$1,200',
        () {
      expect(computeCareCredit(careReturn(FilingStatus.single, 7000, 2), 50000),
          closeTo(1200, 0.5));
    });
    test('AGI ≤ \$15,000 → full 35% rate', () {
      expect(computeCareCredit(careReturn(FilingStatus.single, 3000, 1), 10000),
          closeTo(1050, 0.5));
    });
  });

  group('Education credits (Form 8863)', () {
    test('AOTC: \$4,000 expenses → \$2,500 (\$1,500 nonrefundable + \$1,000 refundable)',
        () {
      final res =
          computeEducationCredits(eduReturn(FilingStatus.single, 4000, true), 50000);
      expect(res.nonrefundable, closeTo(1500, 0.5));
      expect(res.refundable, closeTo(1000, 0.5));
    });
    test('LLC: \$10,000 expenses → \$2,000 nonrefundable', () {
      final res = computeEducationCredits(
          eduReturn(FilingStatus.single, 10000, false), 50000);
      expect(res.nonrefundable, closeTo(2000, 0.5));
      expect(res.refundable, closeTo(0, 0.5));
    });
    test('AOTC halved at the MAGI phaseout midpoint (\$85,000)', () {
      final res =
          computeEducationCredits(eduReturn(FilingStatus.single, 4000, true), 85000);
      expect(res.nonrefundable, closeTo(750, 0.5));
      expect(res.refundable, closeTo(500, 0.5));
    });
    test('MFS cannot claim education credits', () {
      final res =
          computeEducationCredits(eduReturn(FilingStatus.mfs, 4000, true), 50000);
      expect(res.nonrefundable, closeTo(0, 0.5));
      expect(res.refundable, closeTo(0, 0.5));
    });
  });

  group("Saver's Credit (Form 8880)", () {
    test('50% tier (AGI ≤ \$23,000)', () {
      expect(computeSaversCredit(saverReturn(2000), 20000), closeTo(1000, 0.5));
    });
    test('20% tier', () {
      expect(computeSaversCredit(saverReturn(2000), 24000), closeTo(400, 0.5));
    });
    test('10% tier with the \$2,000 contribution cap', () {
      expect(computeSaversCredit(saverReturn(3000), 30000), closeTo(200, 0.5));
    });
    test('above the top AGI tier → \$0', () {
      expect(computeSaversCredit(saverReturn(2000), 40000), closeTo(0, 0.5));
    });
    test('full-time student is ineligible', () {
      final r = saverReturn(2000);
      r.credits.isFullTimeStudent = true;
      expect(computeSaversCredit(r, 20000), closeTo(0, 0.5));
    });
  });

  group('Energy & EV credits', () {
    test('Residential Clean Energy = 30% of cost', () {
      final r = baseReturn(FilingStatus.single);
      r.credits.cleanEnergyCost = 10000;
      expect(computeCleanEnergyCredit(r), closeTo(3000, 0.5));
    });
    test('EV credit allowed under the MAGI cap', () {
      final r = baseReturn(FilingStatus.single);
      r.credits.evCreditAmount = 7500;
      expect(computeEvCredit(r, 100000), closeTo(7500, 0.5));
    });
    test('EV credit denied above the MAGI cap (\$150,000 single)', () {
      final r = baseReturn(FilingStatus.single);
      r.credits.evCreditAmount = 7500;
      expect(computeEvCredit(r, 200000), closeTo(0, 0.5));
    });
  });
}
