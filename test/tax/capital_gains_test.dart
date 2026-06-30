import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/capital_gains.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';

import 'fixtures/builders.dart';

/// Schedule D netting, the \$3,000 (\$1,500 MFS) loss limit, and carryover
/// character.
void main() {
  group('capital gains (Schedule D)', () {
    test('net long-term gain → included in income and fully preferential', () {
      final r =
          withCapitalTransaction(baseReturn(FilingStatus.single), 20000, 12000, true);
      final res = computeCapitalGains(r);
      expect(res.netLongTerm, 8000);
      expect(res.includedInIncome, 8000);
      expect(res.preferentialLTCG, 8000);
    });

    test('ST gain offset by LT loss → net is ordinary, no preferential', () {
      var r = withCapitalTransaction(
          baseReturn(FilingStatus.single), 10000, 5000, false); // +5,000 ST
      r = withCapitalTransaction(r, 5000, 8000, true); // −3,000 LT
      final res = computeCapitalGains(r);
      expect(res.totalNet, 2000);
      expect(res.includedInIncome, 2000);
      expect(res.preferentialLTCG, 0);
    });

    test('large LT loss → deduction capped at \$3,000, \$17,000 carries to long-term',
        () {
      final r =
          withCapitalTransaction(baseReturn(FilingStatus.single), 0, 20000, true);
      final res = computeCapitalGains(r);
      expect(res.includedInIncome, -3000);
      expect(res.carryoverLong, 17000);
      expect(res.carryoverShort, 0);
    });

    test('MFS loss limit is \$1,500', () {
      final r = withCapitalTransaction(
          baseReturn(FilingStatus.mfs), 0, 5000, false); // −5,000 ST
      final res = computeCapitalGains(r);
      expect(res.includedInIncome, -1500);
      expect(res.carryoverShort, 3500);
    });

    test('both ST and LT losses → allowed loss applied to short-term first', () {
      var r = withCapitalTransaction(
          baseReturn(FilingStatus.single), 0, 2000, false); // −2,000 ST
      r = withCapitalTransaction(r, 0, 5000, true); // −5,000 LT
      final res = computeCapitalGains(r);
      expect(res.includedInIncome, -3000);
      expect(res.carryoverShort, 0);
      expect(res.carryoverLong, 4000);
    });

    test('a wash-sale adjustment disallows the loss', () {
      final r = withCapitalTransaction(
          baseReturn(FilingStatus.single), 5000, 8000, true, 3000);
      final res = computeCapitalGains(r);
      expect(res.includedInIncome, 0);
      expect(res.carryoverLong, 0);
    });
  });
}
