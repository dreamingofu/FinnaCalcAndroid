import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/niit.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/additional_medicare.dart';

/// NIIT (8960), Additional Medicare Tax (8959).
void main() {
  group('NIIT (Form 8960)', () {
    test('3.8% of NII when MAGI is well over the threshold', () {
      expect(computeNiit(50000, 250000, FilingStatus.single), closeTo(1900, 0.01));
    });
    test('limited to MAGI over the threshold', () {
      expect(computeNiit(50000, 210000, FilingStatus.single),
          closeTo(380, 0.01)); // 3.8% × 10,000
    });
    test('no NIIT below the threshold', () {
      expect(computeNiit(50000, 150000, FilingStatus.single), closeTo(0, 0.01));
    });
  });

  group('Additional Medicare Tax (Form 8959)', () {
    test('0.9% on wages over \$200,000 (single)', () {
      expect(computeAdditionalMedicareTax(250000, 0, FilingStatus.single),
          closeTo(450, 0.01));
    });
    test('MFJ threshold is \$250,000', () {
      expect(computeAdditionalMedicareTax(300000, 0, FilingStatus.mfj),
          closeTo(450, 0.01));
    });
    test('threshold applied to wages first, then to SE earnings', () {
      // wages 150k (under 200k); remaining threshold 50k; SE 100k → (100k−50k)×0.9%
      expect(computeAdditionalMedicareTax(150000, 100000, FilingStatus.single),
          closeTo(450, 0.01));
    });
    test('no tax below the threshold', () {
      expect(computeAdditionalMedicareTax(100000, 0, FilingStatus.single),
          closeTo(0, 0.01));
    });
  });
}
