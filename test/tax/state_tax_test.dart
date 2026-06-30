import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/state/state_tax.dart';
import 'package:finnacalc/features/taxes/engine/state/state_types.dart';

StateInput _input({
  required String code,
  double federalAgi = 0,
  double taxableSocialSecurity = 0,
  double retirementDistributions = 0,
  FilingStatus filingStatus = FilingStatus.single,
  double dependents = 0,
  double stateWithholding = 0,
  bool age65 = false,
}) =>
    StateInput(
      code: code,
      federalAgi: federalAgi,
      taxableSocialSecurity: taxableSocialSecurity,
      retirementDistributions: retirementDistributions,
      filingStatus: filingStatus,
      dependents: dependents,
      stateWithholding: stateWithholding,
      age65: age65,
    );

void main() {
  group('no-income-tax states', () {
    test('Texas has no income tax', () {
      final res = computeStateTax(_input(code: 'TX', federalAgi: 100000))!;
      expect(res.hasIncomeTax, false);
      expect(res.tax, closeTo(0, 0.5));
    });
    test('Washington notes its separate capital-gains excise', () {
      final res = computeStateTax(_input(code: 'WA', federalAgi: 100000))!;
      expect(res.hasIncomeTax, false);
      expect(res.note, matches(RegExp(r'capital-gains|excise', caseSensitive: false)));
    });
  });

  group('flat-tax states', () {
    test('Illinois: 4.95% after the \$2,775 exemption', () {
      // (60,000 − 2,775) × 4.95% = 2,832.64 → \$2,833
      final res = computeStateTax(_input(code: 'IL', federalAgi: 60000))!;
      expect(res.tax, closeTo(2833, 0.5));
    });
    test('North Carolina: 4.5% after the \$12,750 standard deduction', () {
      // (50,000 − 12,750) × 4.5% = 1,676.25 → \$1,676
      expect(computeStateTax(_input(code: 'NC', federalAgi: 50000))!.tax,
          closeTo(1676, 0.5));
    });
    test('Pennsylvania excludes retirement income (3.07% flat)', () {
      // (60,000 − 20,000 retirement) × 3.07% = 1,228
      final res = computeStateTax(_input(
          code: 'PA', federalAgi: 60000, retirementDistributions: 20000))!;
      expect(res.tax, closeTo(1228, 0.5));
    });
  });

  group('progressive states', () {
    test('California: single \$100,000 AGI → \$5,289 after the exemption credit',
        () {
      expect(computeStateTax(_input(code: 'CA', federalAgi: 100000))!.tax,
          closeTo(5289, 0.5));
    });
    test('New York: single \$80,000 AGI → \$3,795', () {
      expect(computeStateTax(_input(code: 'NY', federalAgi: 80000))!.tax,
          closeTo(3795, 0.5));
    });
  });

  group('common rules', () {
    test('subtracts taxable Social Security from state AGI', () {
      // NC: (50,000 − 10,000 SS − 12,750) × 4.5% = 1,226.25 → \$1,226
      final res = computeStateTax(_input(
          code: 'NC', federalAgi: 50000, taxableSocialSecurity: 10000))!;
      expect(res.tax, closeTo(1226, 0.5));
    });
    test('computes state refund vs balance due from withholding', () {
      final res = computeStateTax(
          _input(code: 'NC', federalAgi: 50000, stateWithholding: 2000))!;
      expect(res.refundOrOwed, closeTo(324, 0.5)); // 2,000 − 1,676
    });
    test('marks unsupported states rather than guessing', () {
      final res = computeStateTax(_input(code: 'CO', federalAgi: 50000))!;
      expect(res.supported, false);
      expect(res.tax, closeTo(0, 0.5));
    });
    test('returns null when no state is set', () {
      expect(computeStateTax(_input(code: '')), isNull);
    });
  });
}
