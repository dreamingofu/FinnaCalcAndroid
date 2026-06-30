import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/social_security.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';

/// Social Security Benefits Worksheet — 0% / 50% / 85% tiers.
double ss({
  double benefits = 0,
  double otherIncome = 0,
  double taxExemptInterest = 0,
  double adjustmentsForProvisional = 0,
  FilingStatus status = FilingStatus.single,
  bool livedApartFromSpouse = false,
}) =>
    computeTaxableSocialSecurity(
      benefits: benefits,
      otherIncome: otherIncome,
      taxExemptInterest: taxExemptInterest,
      adjustmentsForProvisional: adjustmentsForProvisional,
      status: status,
      livedApartFromSpouse: livedApartFromSpouse,
    );

void main() {
  group('Social Security taxability', () {
    test('low income → 0% taxable (provisional ≤ \$25,000 single)', () {
      expect(ss(benefits: 20000, otherIncome: 10000), 0);
    });

    test('middle tier → 50% of the excess over base1, capped at half the benefits',
        () {
      // provisional = 20,000 + 10,000 = 30,000 → 0.5 × (30,000 − 25,000) = 2,500
      expect(ss(benefits: 20000, otherIncome: 20000), 2500);
    });

    test('high income → 85% cap on benefits', () {
      // provisional = 60,000 → 0.85 × 20,000 = 17,000 (cap binds)
      expect(ss(benefits: 20000, otherIncome: 50000), 17000);
    });

    test('MFJ tier computation', () {
      // provisional = 55,000; tier1 = 6,000; 0.85×(55,000−44,000)+6,000 = 15,350
      expect(
        ss(benefits: 30000, otherIncome: 40000, status: FilingStatus.mfj),
        15350,
      );
    });

    test('above-the-line adjustments reduce provisional income', () {
      // provisional = 50,000 + 10,000 − 30,000 = 30,000 → 50% tier = 2,500
      expect(
        ss(benefits: 20000, otherIncome: 50000, adjustmentsForProvisional: 30000),
        2500,
      );
    });

    test('tax-exempt interest counts toward provisional income', () {
      // provisional = 20,000 + 10,000(tax-exempt) + 10,000(half) = 40,000 →
      // 0.85×6,000+4,500 = 9,600
      expect(ss(benefits: 20000, otherIncome: 20000, taxExemptInterest: 10000), 9600);
    });
  });
}
