import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/child_tax_credit.dart';
import 'fixtures/builders.dart';

/// Child Tax Credit / ODC / Additional CTC — verified against Schedule 8812
/// (2024).
/// Signature: computeChildTaxCredit(return, magi, taxAvailable, earnedIncome).
void main() {
  group('Child Tax Credit (Schedule 8812)', () {
    test(
        '2 children, MFJ, below phaseout, ample tax → \$4,000 nonrefundable, no ACTC',
        () {
      var r = baseReturn(FilingStatus.mfj);
      r = addQualifyingChild(r);
      r = addQualifyingChild(r);
      final res = computeChildTaxCredit(r, 100000, 20000, 100000);
      expect(res.tentativeCredit, closeTo(4000, 0.5));
      expect(res.nonrefundable, closeTo(4000, 0.5));
      expect(res.additionalChildTaxCredit, closeTo(0, 0.5));
    });

    test('phaseout: single, MAGI \$210,000, 1 child → \$1,500', () {
      var r = baseReturn(FilingStatus.single);
      r = addQualifyingChild(r);
      final res = computeChildTaxCredit(r, 210000, 20000, 100000);
      // \$2,000 − ceil(10,000/1,000)×\$50 = 2,000 − 500 = 1,500
      expect(res.creditAfterPhaseout, closeTo(1500, 0.5));
      expect(res.nonrefundable, closeTo(1500, 0.5));
    });

    test('phaseout rounds the excess UP to the next \$1,000', () {
      var r = baseReturn(FilingStatus.single);
      r = addQualifyingChild(r);
      // MAGI \$210,500 → excess \$10,500 → ceil = 11 steps × \$50 = \$550 → \$1,450
      final res = computeChildTaxCredit(r, 210500, 20000, 100000);
      expect(res.creditAfterPhaseout, closeTo(1450, 0.5));
    });

    test(
        'low income: 1 child, earned \$10,000, no tax → ACTC = 15%×(10,000−2,500) = \$1,125',
        () {
      var r = baseReturn(FilingStatus.single);
      r = addQualifyingChild(r);
      final res = computeChildTaxCredit(r, 10000, 0, 10000);
      expect(res.nonrefundable, closeTo(0, 0.5));
      expect(res.additionalChildTaxCredit, closeTo(1125, 0.5));
    });

    test('ACTC is capped at \$1,700 per qualifying child', () {
      var r = baseReturn(FilingStatus.single);
      r = addQualifyingChild(r);
      // earned \$50,000 → 15% formula = \$7,125, but cap = 1×\$1,700 and leftover = \$2,000
      final res = computeChildTaxCredit(r, 50000, 0, 50000);
      expect(res.additionalChildTaxCredit, closeTo(1700, 0.5));
    });

    test('ODC (\$500) is never refundable', () {
      var r = baseReturn(FilingStatus.single);
      r = addOtherDependent(r);
      final res = computeChildTaxCredit(r, 50000, 0, 50000);
      expect(res.tentativeCredit, closeTo(500, 0.5));
      expect(res.nonrefundable, closeTo(0, 0.5));
      expect(res.additionalChildTaxCredit, closeTo(0, 0.5));
    });

    test('partial nonrefundable + ACTC splits correctly (total benefit = \$2,000)',
        () {
      var r = baseReturn(FilingStatus.single);
      r = addQualifyingChild(r);
      // tax available \$500 → nonrefundable \$500, leftover \$1,500, ACTC = min(1,500, 1,700, 2,625)
      final res = computeChildTaxCredit(r, 20000, 500, 20000);
      expect(res.nonrefundable, closeTo(500, 0.5));
      expect(res.additionalChildTaxCredit, closeTo(1500, 0.5));
    });
  });
}
