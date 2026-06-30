import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/schedule_se.dart';

/// Schedule SE — 92.35% net-earnings factor, 12.4% SS up to the \$168,600 wage
/// base (reduced by W-2 SS wages), 2.9% uncapped Medicare, 50% deductible.
void main() {
  group('self-employment tax (Schedule SE)', () {
    test('\$50,000 net SE, no W-2 → SE tax \$7,064.78, deduction \$3,532.39',
        () {
      final res = computeSelfEmploymentTax(
        (taxpayer: 50000, spouse: 0),
        (taxpayer: 0, spouse: 0),
      );
      // 50,000 × 0.9235 = 46,175 net earnings; × 0.153 = 7,064.775
      expect(res.netEarnings, closeTo(46175, 0.01));
      expect(res.seTax, closeTo(7064.775, 0.01));
      expect(res.deduction, closeTo(3532.3875, 0.01));
    });

    test('W-2 SS wages at the wage base → only the 2.9% Medicare portion applies',
        () {
      final res = computeSelfEmploymentTax(
        (taxpayer: 50000, spouse: 0),
        (taxpayer: 168600, spouse: 0),
      );
      // SS portion fully absorbed by W-2 wages; 46,175 × 0.029 = 1,339.075
      expect(res.seTax, closeTo(1339.075, 0.01));
    });

    test('net earnings under \$400 → no SE tax', () {
      final res = computeSelfEmploymentTax(
        (taxpayer: 400, spouse: 0),
        (taxpayer: 0, spouse: 0),
      );
      expect(res.seTax, 0);
    });

    test('computes SE tax per owner and sums', () {
      final res = computeSelfEmploymentTax(
        (taxpayer: 50000, spouse: 30000),
        (taxpayer: 0, spouse: 0),
      );
      // 46,175×0.153 + 27,705×0.153 = 7,064.775 + 4,238.865
      expect(res.seTax, closeTo(11303.64, 0.01));
    });
  });
}
