import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/amt.dart';

/// AMT (Form 6251).
void main() {
  group('AMT (Form 6251)', () {
    test('normal case → no AMT (regular tax exceeds the tentative minimum)', () {
      final res = computeAmt(
        taxableIncome: 50000,
        addBacks: 14600,
        preferentialIncome: 0,
        regularTax: 6059,
        status: FilingStatus.single,
      );
      expect(res.amt, closeTo(0, 0.5));
    });

    test('26%/28% breakpoint applied to the AMT base', () {
      // AMTI 350,000 − 85,700 exemption = 264,300 base.
      // 232,600×26% + (264,300−232,600)×28% = 60,476 + 8,876 = 69,352
      final res = computeAmt(
        taxableIncome: 300000,
        addBacks: 50000,
        preferentialIncome: 0,
        regularTax: 1000,
        status: FilingStatus.single,
      );
      expect(res.tentativeMinimumTax, closeTo(69352, 0.5));
      expect(res.amt, closeTo(68352, 0.5));
    });

    test('exemption phases out 25¢ per \$1 over the threshold', () {
      // AMTI 700,000 > 609,350 → exemption 85,700 − 25%×90,650 = 63,037.50
      final res = computeAmt(
        taxableIncome: 700000,
        addBacks: 0,
        preferentialIncome: 0,
        regularTax: 0,
        status: FilingStatus.single,
      );
      expect(res.exemption, closeTo(63037.5, 0.01));
      expect(res.tentativeMinimumTax, closeTo(173698, 0.5));
    });
  });
}
