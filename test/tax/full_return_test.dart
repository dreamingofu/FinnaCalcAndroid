import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/calculator.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/types/income.dart';

import 'fixtures/builders.dart';

/// End-to-end golden fixtures — each value hand-computed from the 2024 rules and
/// asserted to the whole dollar (AGI → taxable income → tax → credits → refund).
void main() {
  group('Full return: simple single W-2 filer (refund)', () {
    // Wages \$60,000, withholding \$6,000, standard deduction, no dependents.
    final res = calculateFederalTax(withW2(baseReturn(FilingStatus.single), 60000, 6000));

    test('AGI = \$60,000', () => expect(res.agi, closeTo(60000, 0.5)));
    test('standard deduction = \$14,600',
        () => expect(res.deductionUsed, 'standard'));
    test('taxable income = \$45,400',
        () => expect(res.taxableIncome, closeTo(45400, 0.5)));
    test('regular tax (Tax Table, midpoint \$45,425) = \$5,219',
        () => expect(res.regularTax, closeTo(5219, 0.5)));
    test('uses the Tax Table', () => expect(res.usedTaxTable, true));
    test('total tax = \$5,219', () => expect(res.totalTax, closeTo(5219, 0.5)));
    test('refund = \$781', () {
      expect(res.refundOrOwed, closeTo(781, 0.5));
      expect(res.owes, false);
    });
  });

  group('Full return: MFJ with 2 children (CTC nonrefundable)', () {
    // Wages \$90,000, withholding \$5,000, 2 qualifying children.
    var r = withW2(baseReturn(FilingStatus.mfj), 90000, 5000);
    r = addQualifyingChild(r);
    r = addQualifyingChild(r);
    final res = calculateFederalTax(r);

    test('taxable income = \$60,800',
        () => expect(res.taxableIncome, closeTo(60800, 0.5)));
    test('regular tax (Tax Table, midpoint \$60,825) = \$6,835',
        () => expect(res.regularTax, closeTo(6835, 0.5)));
    test('CTC nonrefundable = \$4,000',
        () => expect(res.nonrefundableCredits['childTaxCredit'], closeTo(4000, 0.5)));
    test('total tax = \$2,835', () => expect(res.totalTax, closeTo(2835, 0.5)));
    test('refund = \$2,165', () => expect(res.refundOrOwed, closeTo(2165, 0.5)));
  });

  group('Full return: low-income single, 2 children (refundable ACTC)', () {
    // Wages \$18,000, no withholding, 2 qualifying children.
    var r = withW2(baseReturn(FilingStatus.single), 18000, 0);
    r = addQualifyingChild(r);
    r = addQualifyingChild(r);
    final res = calculateFederalTax(r);

    test('taxable income = \$3,400',
        () => expect(res.taxableIncome, closeTo(3400, 0.5)));
    test('regular tax (Tax Table, midpoint \$3,425) = \$343',
        () => expect(res.regularTax, closeTo(343, 0.5)));
    test('CTC nonrefundable absorbs the \$343 of tax',
        () => expect(res.nonrefundableCredits['childTaxCredit'], closeTo(343, 0.5)));
    test('total tax = \$0', () => expect(res.totalTax, closeTo(0, 0.5)));
    test('refundable ACTC = \$2,325 (15% × (18,000 − 2,500))',
        () => expect(res.refundableCredits['additionalChildTaxCredit'],
            closeTo(2325, 0.5)));
    test('EITC (2 children, \$18,000 earned) = \$6,960 max credit',
        () => expect(res.refundableCredits['earnedIncomeCredit'],
            closeTo(6960, 0.5)));
    test('refund = ACTC + EITC = \$9,285',
        () => expect(res.refundOrOwed, closeTo(9285, 0.5)));
  });

  group('Full return: high-income single, itemized deductions (balance due)', () {
    // Wages \$150,000, withholding \$20,000; itemize mortgage + SALT + charity.
    final r = withW2(baseReturn(FilingStatus.single), 150000, 20000);
    r.itemized.mortgageInterest = 15000;
    r.itemized.stateLocalIncomeOrSalesTax = 12000;
    r.itemized.realEstateTaxes = 6000; // SALT total \$18,000 → capped at \$10,000
    r.itemized.charitableCash = 3000;
    final res = calculateFederalTax(r);

    test('itemizes (28,000 > 14,600 standard)',
        () => expect(res.deductionUsed, 'itemized'));
    test('itemized deduction = \$28,000 (SALT capped)',
        () => expect(res.deductionAmount, closeTo(28000, 0.5)));
    test('taxable income = \$122,000',
        () => expect(res.taxableIncome, closeTo(122000, 0.5)));
    test('regular tax (Computation Worksheet) = \$22,323', () {
      expect(res.usedTaxTable, false);
      expect(res.regularTax, closeTo(22323, 0.5));
    });
    test('owes \$2,323', () {
      expect(res.owes, true);
      expect(res.refundOrOwed, closeTo(-2323, 0.5));
    });
  });

  group('Edge cases', () {
    test('zero income → zero tax, zero refund', () {
      final res = calculateFederalTax(baseReturn(FilingStatus.single));
      expect(res.totalTax, closeTo(0, 0.5));
      expect(res.refundOrOwed, closeTo(0, 0.5));
    });

    test('flags W-2 income with zero withholding', () {
      final res =
          calculateFederalTax(withW2(baseReturn(FilingStatus.single), 50000, 0));
      expect(
          res.auditFlags
              .any((f) => f.message.contains('no federal tax was withheld')),
          true);
    });
  });

  group('Phase 2/3 — Full return: self-employed single (SE tax + 50% deduction + QBI)',
      () {
    // Schedule C net \$80,000, no W-2, no withholding.
    final res =
        calculateFederalTax(withScheduleC(baseReturn(FilingStatus.single), 80000));

    test('self-employment tax = \$11,304 (73,880 net earnings × 15.3%)',
        () => expect(res.seTax, closeTo(11304, 0.5)));
    test('AGI = \$74,348 (income \$80,000 − half of SE tax \$5,652)',
        () => expect(res.agi, closeTo(74348, 0.5)));
    test('QBI deduction = \$11,950 (20% of QBI, capped by 20% of taxable income)',
        () => expect(res.qbiDeduction, closeTo(11950, 0.5)));
    test('taxable income = \$47,798 (after standard deduction and QBI)',
        () => expect(res.taxableIncome, closeTo(47798, 0.5)));
    test('regular income tax = \$5,564',
        () => expect(res.regularTax, closeTo(5564, 0.5)));
    test('total tax = income tax + SE tax = \$16,868',
        () => expect(res.totalTax, closeTo(16868, 0.5)));
    test('owes \$16,868 (no withholding)',
        () => expect(res.refundOrOwed, closeTo(-16868, 0.5)));
  });

  group('Phase 2 — Full return: retiree with Social Security + qualified dividends',
      () {
    // MFJ, \$40,000 SS benefits, \$20,000 dividends (all qualified), \$5,000 interest.
    var r = withQualifiedDividends(baseReturn(FilingStatus.mfj), 20000, 20000);
    r = withSocialSecurity(r, 40000);
    r.income.flags.hasInterest = true;
    r.income.f1099Int.add(Form1099Int(
      id: 'int-0',
      payer: 'Bank',
      box1Interest: 5000,
      box3UsTreasuryInterest: 0,
      box8TaxExemptInterest: 0,
      box4FederalWithholding: 0,
    ));
    final res = calculateFederalTax(r);

    test('total income includes \$6,850 of taxable Social Security → \$31,850',
        () => expect(res.totalIncome, closeTo(31850, 0.5)));
    test('taxable income = \$2,650 (after MFJ \$29,200 standard deduction)',
        () => expect(res.taxableIncome, closeTo(2650, 0.5)));
    test(
        'uses the Qualified Dividends worksheet; tax = \$0 (preferential income in the 0% bracket)',
        () {
      expect(res.usedQualDivWorksheet, true);
      expect(res.regularTax, closeTo(0, 0.5));
      expect(res.totalTax, closeTo(0, 0.5));
    });
  });

  group('Phase 3 — Full return: high earner (NIIT + Additional Medicare Tax)', () {
    // Single, \$300,000 wages (\$60,000 withheld), \$50,000 taxable interest.
    final r = withW2(baseReturn(FilingStatus.single), 300000, 60000);
    r.income.flags.hasInterest = true;
    r.income.f1099Int.add(Form1099Int(
      id: 'int-0',
      payer: 'Bank',
      box1Interest: 50000,
      box3UsTreasuryInterest: 0,
      box8TaxExemptInterest: 0,
      box4FederalWithholding: 0,
    ));
    final res = calculateFederalTax(r);

    test('regular tax = \$87,765',
        () => expect(res.regularTax, closeTo(87765, 0.5)));
    test('Additional Medicare Tax = \$900 (0.9% × wages over \$200k)',
        () => expect(res.additionalMedicareTax, closeTo(900, 0.5)));
    test('NIIT = \$1,900 (3.8% × \$50,000 investment income)',
        () => expect(res.niit, closeTo(1900, 0.5)));
    test('total tax = \$90,565', () => expect(res.totalTax, closeTo(90565, 0.5)));
    test('owes \$30,565', () => expect(res.refundOrOwed, closeTo(-30565, 0.5)));
  });

  group('Phase 2 — Full return: capital loss with carryover', () {
    // Single, \$60,000 wages (\$6,000 withheld), \$10,000 long-term capital loss.
    var r = withW2(baseReturn(FilingStatus.single), 60000, 6000);
    r = withCapitalTransaction(r, 0, 10000, true);
    final res = calculateFederalTax(r);

    test('only \$3,000 of the loss is deductible this year → AGI \$57,000',
        () => expect(res.agi, closeTo(57000, 0.5)));
    test('taxable income = \$42,400',
        () => expect(res.taxableIncome, closeTo(42400, 0.5)));
    test('regular tax = \$4,859',
        () => expect(res.regularTax, closeTo(4859, 0.5)));
    test('refund = \$1,141', () => expect(res.refundOrOwed, closeTo(1141, 0.5)));
    test('\$7,000 long-term loss carries over to next year',
        () => expect(res.capitalLossCarryover.longTerm, closeTo(7000, 0.5)));
  });
}
