import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/adjustments_calc.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'fixtures/builders.dart';

/// Traditional IRA deduction phaseouts (IRC §219(g), 2024 ranges).
void main() {
  group('IRA deduction', () {
    test('not covered by a plan → fully deductible', () {
      expect(
        iraDeduction(7000, 200000, FilingStatus.single, false, false, false),
        closeTo(7000, 0.5),
      );
    });
    test('covered, single, below phaseout → full \$7,000', () {
      expect(
        iraDeduction(7000, 50000, FilingStatus.single, true, false, false),
        closeTo(7000, 0.5),
      );
    });
    test('covered, single, midpoint of \$77k–\$87k → \$3,500', () {
      expect(
        iraDeduction(7000, 82000, FilingStatus.single, true, false, false),
        closeTo(3500, 0.5),
      );
    });
    test('covered, single, above \$87k → \$0', () {
      expect(
        iraDeduction(7000, 90000, FilingStatus.single, true, false, false),
        closeTo(0, 0.5),
      );
    });
    test('covered, single, tiny remaining deduction floors at \$200', () {
      // MAGI 86,900 → ratio 0.01 → \$70 → floored up to the \$200 minimum
      expect(
        iraDeduction(7000, 86900, FilingStatus.single, true, false, false),
        closeTo(200, 0.5),
      );
    });
    test('age 50+ catch-up raises the limit to \$8,000', () {
      expect(
        iraDeduction(8000, 50000, FilingStatus.single, true, false, true),
        closeTo(8000, 0.5),
      );
    });
    test('MFJ, not covered but spouse is, midpoint of \$230k–\$240k → \$3,500',
        () {
      expect(
        iraDeduction(7000, 235000, FilingStatus.mfj, false, true, false),
        closeTo(3500, 0.5),
      );
    });
    test('MFS covered, midpoint of \$0–\$10k → \$3,500', () {
      expect(
        iraDeduction(7000, 5000, FilingStatus.mfs, true, false, false),
        closeTo(3500, 0.5),
      );
    });
  });

  /// HSA deduction (Form 8889) — capped by coverage limit + 55+ catch-up.
  group('HSA deduction', () {
    test('self-only coverage caps at \$4,150', () {
      final r = baseReturn(FilingStatus.single);
      r.adjustments.hsaCoverage = 'self-only';
      r.adjustments.hsaContribution = 5000;
      expect(hsaDeduction(r), closeTo(4150, 0.5));
    });
    test('family coverage caps at \$8,300', () {
      final r = baseReturn(FilingStatus.single);
      r.adjustments.hsaCoverage = 'family';
      r.adjustments.hsaContribution = 9000;
      expect(hsaDeduction(r), closeTo(8300, 0.5));
    });
    test('age 55+ adds the \$1,000 catch-up', () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.dateOfBirth = '1965-01-01';
      r.adjustments.hsaCoverage = 'self-only';
      r.adjustments.hsaContribution = 5000;
      expect(hsaDeduction(r), closeTo(5000, 0.5)); // limit 5,150, contribution 5,000
    });
    test('no HDHP coverage → \$0', () {
      expect(hsaDeduction(baseReturn(FilingStatus.single)), closeTo(0, 0.5));
    });
  });

  /// Student loan interest deduction phaseout (2024).
  group('student loan interest deduction', () {
    test('below phaseout → up to \$2,500', () {
      expect(studentLoanInterestDeduction(2000, 50000, FilingStatus.single),
          closeTo(2000, 0.5));
      expect(studentLoanInterestDeduction(3000, 50000, FilingStatus.single),
          closeTo(2500, 0.5));
    });
    test('MFS may never claim it', () {
      expect(studentLoanInterestDeduction(2000, 10000, FilingStatus.mfs),
          closeTo(0, 0.5));
    });
    test('single midpoint of \$80k–\$95k phaseout → half', () {
      expect(studentLoanInterestDeduction(2500, 87500, FilingStatus.single),
          closeTo(1250, 0.01));
    });
  });
}
