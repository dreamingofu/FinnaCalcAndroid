import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/regular_tax.dart';

/// Regular tax — verified against the 2024 Tax Rate Schedules and the Tax Table
/// midpoint method. All expected values are hand-computed from Rev. Proc.
/// 2023-34 brackets.
void main() {
  group('bracketTax (exact 2024 rate schedule)', () {
    test('is zero at or below zero', () {
      expect(bracketTax(0, FilingStatus.single), 0);
      expect(bracketTax(-100, FilingStatus.single), 0);
    });

    test('single: top of the 10% bracket = \$1,160', () {
      expect(bracketTax(11600, FilingStatus.single), closeTo(1160, 0.01));
    });

    test('single: top of the 12% bracket (\$47,150) = \$5,426', () {
      expect(bracketTax(47150, FilingStatus.single), closeTo(5426, 0.01));
    });

    test('single: \$200,000 taxable = \$41,686.50 (pre-rounding)', () {
      expect(bracketTax(200000, FilingStatus.single), closeTo(41686.5, 0.01));
    });

    test('mfj: \$200,000 taxable = \$34,106', () {
      expect(bracketTax(200000, FilingStatus.mfj), closeTo(34106, 0.01));
    });

    test('hoh: \$100,000 taxable = \$15,359', () {
      expect(bracketTax(100000, FilingStatus.hoh), closeTo(15359, 0.01));
    });

    test('qss uses the mfj schedule', () {
      expect(
        bracketTax(200000, FilingStatus.qss),
        closeTo(bracketTax(200000, FilingStatus.mfj), 0.01),
      );
    });
  });

  group('marginalRate', () {
    test('single \$50k → 22%, \$10k → 10%', () {
      expect(marginalRate(50000, FilingStatus.single), 0.22);
      expect(marginalRate(10000, FilingStatus.single), 0.1);
    });
    test('mfj \$200k → 22% (below the \$201,050 step)', () {
      expect(marginalRate(200000, FilingStatus.mfj), 0.22);
    });
    test('zero income → 0% marginal', () {
      expect(marginalRate(0, FilingStatus.single), 0);
    });
  });

  group(
      'computeRegularTax (Tax Table < \$100k vs Computation Worksheet ≥ \$100k)',
      () {
    test('single \$40,000 uses the Tax Table midpoint (\$40,025) = \$4,571',
        () {
      final res = computeRegularTax(40000, FilingStatus.single);
      expect(res.usedTaxTable, true);
      expect(res.tax, closeTo(4571, 0.5));
    });

    test('single \$100,000 uses the Computation Worksheet = \$17,053', () {
      final res = computeRegularTax(100000, FilingStatus.single);
      expect(res.usedTaxTable, false);
      expect(res.tax, closeTo(17053, 0.5));
    });

    test('single \$200,000 = \$41,687 (rounded from 41,686.50)', () {
      expect(
        computeRegularTax(200000, FilingStatus.single).tax,
        closeTo(41687, 0.5),
      );
    });

    test('the \$100,000 boundary switches table → worksheet', () {
      expect(computeRegularTax(99999, FilingStatus.single).usedTaxTable, true);
      expect(computeRegularTax(100000, FilingStatus.single).usedTaxTable, false);
    });

    test('zero taxable income → \$0 tax', () {
      expect(computeRegularTax(0, FilingStatus.single).tax, closeTo(0, 0.5));
    });
  });
}
