import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/calculator.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';

import 'fixtures/builders.dart';

void main() {
  group('state tax integrated through the federal engine', () {
    test('attaches a California result to a CA resident\'s return', () {
      final r = withW2(baseReturn(FilingStatus.single), 100000, 0);
      r.residency.state = 'CA';
      final res = calculateFederalTax(r);
      expect(res.state?.code, 'CA');
      expect(res.state?.hasIncomeTax, true);
      // AGI here is 100,000 (no adjustments) → same as the standalone CA test.
      expect(res.state?.tax, closeTo(5289, 0.5));
    });
  });
}
