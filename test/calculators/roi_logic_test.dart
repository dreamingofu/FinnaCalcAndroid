import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/roi/roi_logic.dart';

void main() {
  group('ROI calculate', () {
    test('annualized 10k->15k over 5yr with dividends, inflation, tax', () {
      final r = RoiCalculator.calculate(
        initial: 10000,
        finalValue: 15000,
        time: 5,
        calculationType: 'annualized',
        dividend: 2,
        inflation: 3,
        tax: 20,
      );
      expect(r.totalReturn, closeTo(5000, 0.0001));
      expect(r.simpleROI, closeTo(50, 0.0001));
      expect(r.cagr, closeTo(8.447177, 0.0001));
      expect(r.displayedROI, closeTo(8.447177, 0.0001));
      expect(r.dividendIncome, closeTo(1000, 0.0001));
      expect(r.afterTaxReturn, closeTo(4800, 0.0001));
      expect(r.realROI, closeTo(5.288521, 0.0001));
      expect(r.realValue, closeTo(12939.13177, 0.001));
      expect(r.totalTaxes, closeTo(1200, 0.0001));
    });

    test('simple type shows total ROI, not CAGR', () {
      final r = RoiCalculator.calculate(
        initial: 10000,
        finalValue: 15000,
        time: 5,
        calculationType: 'simple',
        dividend: 0,
        inflation: 3,
        tax: 20,
      );
      expect(r.displayedROI, closeTo(50, 0.0001));
      // cagr still computed even when not displayed
      expect(r.cagr, closeTo(8.447177, 0.0001));
      expect(r.afterTaxReturn, closeTo(4000, 0.0001));
      expect(r.totalTaxes, closeTo(1000, 0.0001));
    });

    test('loss: no capital gains tax applied on a negative return', () {
      final r = RoiCalculator.calculate(
        initial: 10000,
        finalValue: 8000,
        time: 2,
        calculationType: 'annualized',
        dividend: 0,
        inflation: 0,
        tax: 20,
      );
      expect(r.totalReturn, closeTo(-2000, 0.0001));
      expect(r.displayedROI, closeTo(-10.557281, 0.0001));
      // tax not applied to losses, no dividends => zero total taxes
      expect(r.totalTaxes, closeTo(0, 0.0001));
      expect(r.afterTaxReturn, closeTo(-2000, 0.0001));
    });

    test('initial <= 0 throws CalcException', () {
      expect(
        () => RoiCalculator.calculate(
          initial: 0,
          finalValue: 15000,
          time: 5,
          calculationType: 'annualized',
          dividend: 0,
          inflation: 0,
          tax: 0,
        ),
        throwsA(isA<CalcException>()),
      );
    });

    test('time parses with fallback of 1, not 0', () {
      // Empty time field -> fallback 1, so CAGR uses time=1 (== simple growth %).
      expect(parseNum('', fallback: 1), 1);
      final r = RoiCalculator.calculate(
        initial: 10000,
        finalValue: 11000,
        time: parseNum('', fallback: 1),
        calculationType: 'annualized',
        dividend: 0,
        inflation: 0,
        tax: 0,
      );
      // time=1 => CAGR == simple ROI == 10%
      expect(r.cagr, closeTo(10, 0.0001));
      expect(r.time, 1);
    });
  });
}
