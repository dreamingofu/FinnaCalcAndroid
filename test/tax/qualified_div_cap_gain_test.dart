import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/qualified_div_cap_gain.dart';

/// Qualified Dividends & Capital Gain Tax Worksheet — preferential income
/// stacked on top of ordinary income, taxed at 2024 0/15/20% breakpoints.
void main() {
  group('Qualified Dividends & Capital Gain Tax Worksheet (single)', () {
    test('\$40k ordinary + \$10k qualified div spanning the 0% and 15% breakpoints',
        () {
      // ordinary \$40,000; preferential \$10,000; single 0% breakpoint = \$47,025.
      final res =
          computeQualifiedDivCapGainTax(50000, 10000, 0, FilingStatus.single);
      expect(res.amountAt0, closeTo(7025, 0.5)); // 47,025 − 40,000
      expect(res.amountAt15, closeTo(2975, 0.5)); // 50,000 − 47,025
      expect(res.amountAt20, closeTo(0, 0.5));
      // ordinary tax (Tax Table on \$40,000 = \$4,571) + 15% × 2,975 = 446.25 → \$5,017
      expect(res.tax, closeTo(5017, 0.5));
    });

    test('all preferential income falls in the 0% bracket', () {
      // ordinary \$30,000; preferential \$10,000; entirely below \$47,025.
      final res =
          computeQualifiedDivCapGainTax(40000, 10000, 0, FilingStatus.single);
      expect(res.amountAt0, closeTo(10000, 0.5));
      expect(res.amountAt15, closeTo(0, 0.5));
      // tax = ordinary tax on \$30,000 (Tax Table \$3,371), preferential taxed at 0%
      expect(res.tax, closeTo(3371, 0.5));
    });

    test('high income reaches the 20% rate', () {
      // ordinary \$500,000; preferential \$100,000 LTCG; single 15% breakpoint = \$518,900.
      final res =
          computeQualifiedDivCapGainTax(600000, 0, 100000, FilingStatus.single);
      expect(res.amountAt0, closeTo(0, 0.5));
      expect(res.amountAt15, closeTo(18900, 0.5)); // 518,900 − 500,000
      expect(res.amountAt20, closeTo(81100, 0.5)); // 600,000 − 518,900
      // ordinary tax on \$500,000 (\$145,375) + 15%×18,900 + 20%×81,100 = \$164,430
      expect(res.tax, closeTo(164430, 0.5));
    });

    test('never costs more than taxing everything at ordinary rates', () {
      const ti = 60000.0;
      final pref =
          computeQualifiedDivCapGainTax(ti, 5000, 0, FilingStatus.single).tax;
      // Compare against all-ordinary by passing zero preferential income.
      final ordinary =
          computeQualifiedDivCapGainTax(ti, 0, 0, FilingStatus.single).tax;
      expect(pref, lessThanOrEqualTo(ordinary));
    });
  });
}
