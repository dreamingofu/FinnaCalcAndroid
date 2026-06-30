import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/eitc.dart';
import 'fixtures/builders.dart';

/// Earned Income Tax Credit — 2024 tables, disqualifiers, and phaseout.
void main() {
  group('EITC', () {
    test('2 children at the plateau → maximum credit \$6,960', () {
      var r = addQualifyingChild(baseReturn(FilingStatus.single));
      r = addQualifyingChild(r);
      final res = computeEitc(
        r: r,
        earnedIncome: 18000,
        agi: 18000,
        investmentIncome: 0,
      );
      expect(res.credit, closeTo(6960, 0.5));
    });

    test('1 child at the phase-in ceiling → maximum credit \$4,213', () {
      final r = addQualifyingChild(baseReturn(FilingStatus.single));
      final res = computeEitc(
        r: r,
        earnedIncome: 12390,
        agi: 12390,
        investmentIncome: 0,
      );
      expect(res.credit, closeTo(4213, 0.5));
    });

    test('childless (age 30) at the plateau → maximum credit \$632', () {
      final res = computeEitc(
        r: baseReturn(FilingStatus.single),
        earnedIncome: 8260,
        agi: 8260,
        investmentIncome: 0,
        taxpayerAge: 30,
      );
      expect(res.credit, closeTo(632, 0.5));
    });

    test('investment income over \$11,600 disqualifies the credit', () {
      var r = addQualifyingChild(baseReturn(FilingStatus.single));
      r = addQualifyingChild(r);
      final res = computeEitc(
        r: r,
        earnedIncome: 18000,
        agi: 18000,
        investmentIncome: 12000,
      );
      expect(res.credit, closeTo(0, 0.5));
      expect(res.eligible, false);
    });

    test('phaseout: 1 child, \$40,000 income → \$1,452', () {
      final r = addQualifyingChild(baseReturn(FilingStatus.single));
      // 4,213 − 15.98% × (40,000 − 22,720) = 4,213 − 2,761.34 = 1,451.66 → \$1,452
      final res = computeEitc(
        r: r,
        earnedIncome: 40000,
        agi: 40000,
        investmentIncome: 0,
      );
      expect(res.credit, closeTo(1452, 0.5));
    });

    test('MFS who did not live apart is ineligible', () {
      final r = addQualifyingChild(baseReturn(FilingStatus.mfs));
      final res = computeEitc(
        r: r,
        earnedIncome: 20000,
        agi: 20000,
        investmentIncome: 0,
      );
      expect(res.credit, closeTo(0, 0.5));
    });

    test('childless filer outside age 25–64 is ineligible', () {
      final young = computeEitc(
        r: baseReturn(FilingStatus.single),
        earnedIncome: 8260,
        agi: 8260,
        investmentIncome: 0,
        taxpayerAge: 22,
      );
      final old = computeEitc(
        r: baseReturn(FilingStatus.single),
        earnedIncome: 8260,
        agi: 8260,
        investmentIncome: 0,
        taxpayerAge: 70,
      );
      expect(young.credit, closeTo(0, 0.5));
      expect(old.credit, closeTo(0, 0.5));
    });
  });
}
