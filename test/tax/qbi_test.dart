import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/qbi.dart';

/// QBI deduction (§199A) — below threshold, overall limit, and SSTB phaseout.
void main() {
  group('QBI deduction', () {
    test('below threshold → 20% of QBI', () {
      final res = computeQbiDeduction(
        qbiIncome: 50000,
        taxableIncomeBeforeQbi: 100000,
        netCapitalGain: 0,
        isSSTB: false,
        status: FilingStatus.single,
      );
      expect(res.deduction, closeTo(10000, 0.01));
      expect(res.wageLimitMayApply, false);
    });

    test('capped at 20% of (taxable income − net capital gain)', () {
      final res = computeQbiDeduction(
        qbiIncome: 50000,
        taxableIncomeBeforeQbi: 40000,
        netCapitalGain: 0,
        isSSTB: false,
        status: FilingStatus.single,
      );
      expect(res.deduction, closeTo(8000, 0.01)); // 20% × 40,000
    });

    test('net capital gain reduces the overall limit', () {
      final res = computeQbiDeduction(
        qbiIncome: 50000,
        taxableIncomeBeforeQbi: 100000,
        netCapitalGain: 30000,
        isSSTB: false,
        status: FilingStatus.single,
      );
      expect(res.deduction, closeTo(10000, 0.01)); // min(10,000, 20%×70,000=14,000)
    });

    test('SSTB fully phased out above threshold + range', () {
      final res = computeQbiDeduction(
        qbiIncome: 50000,
        taxableIncomeBeforeQbi: 250000, // > 191,950 + 50,000
        netCapitalGain: 0,
        isSSTB: true,
        status: FilingStatus.single,
      );
      expect(res.deduction, closeTo(0, 0.01));
    });

    test('SSTB partial phaseout at the midpoint of the range', () {
      final res = computeQbiDeduction(
        qbiIncome: 50000,
        taxableIncomeBeforeQbi: 216950, // threshold + 25,000 (half of 50,000)
        netCapitalGain: 0,
        isSSTB: true,
        status: FilingStatus.single,
      );
      expect(res.deduction, closeTo(5000, 0.01)); // 20% × 50,000 × 50%
    });

    test('non-SSTB above threshold flags the untracked W-2/UBIA limit', () {
      final res = computeQbiDeduction(
        qbiIncome: 50000,
        taxableIncomeBeforeQbi: 250000,
        netCapitalGain: 0,
        isSSTB: false,
        status: FilingStatus.single,
      );
      expect(res.wageLimitMayApply, true);
    });
  });
}
