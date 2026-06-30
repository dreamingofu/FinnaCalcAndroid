import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/taxes/engine/pipeline/deduction_compare.dart';
import 'fixtures/builders.dart';
import 'package:finnacalc/features/taxes/engine/types/filing.dart';

/// Standard deduction matrix — 2024 base amounts, age-65/blind additions, and
/// the dependent cap. Verified against the 2024 Form 1040 Standard Deduction
/// Chart.
void main() {
  group('isConsidered65For2024 (born before Jan 2, 1960)', () {
    test('born 1955 → treated as 65+', () {
      expect(isConsidered65For2024('1955-06-15'), true);
    });
    test('born 1960-01-01 → treated as 65+ (inclusive)', () {
      expect(isConsidered65For2024('1960-01-01'), true);
    });
    test('born 1960-01-02 → not 65+', () {
      expect(isConsidered65For2024('1960-01-02'), false);
    });
    test('born 1990 → not 65+', () {
      expect(isConsidered65For2024('1990-01-01'), false);
    });
  });

  group('standard deduction base amounts (2024)', () {
    test('single = \$14,600', () {
      expect(computeStandardDeduction(baseReturn(FilingStatus.single), 0),
          closeTo(14600, 0.5));
    });
    test('mfj = \$29,200', () {
      expect(computeStandardDeduction(baseReturn(FilingStatus.mfj), 0),
          closeTo(29200, 0.5));
    });
    test('hoh = \$21,900', () {
      expect(computeStandardDeduction(baseReturn(FilingStatus.hoh), 0),
          closeTo(21900, 0.5));
    });
    test('mfs = \$14,600', () {
      expect(computeStandardDeduction(baseReturn(FilingStatus.mfs), 0),
          closeTo(14600, 0.5));
    });
    test('qss = \$29,200', () {
      expect(computeStandardDeduction(baseReturn(FilingStatus.qss), 0),
          closeTo(29200, 0.5));
    });
  });

  group('additional standard deduction (age 65+ / blind)', () {
    test('single, blind → 14,600 + 1,950 = 16,550', () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.blind = true;
      expect(computeStandardDeduction(r, 0), closeTo(16550, 0.5));
    });
    test('single, 65+ and blind → 14,600 + 2×1,950 = 18,500', () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.dateOfBirth = '1950-01-01';
      r.taxpayer.blind = true;
      expect(computeStandardDeduction(r, 0), closeTo(18500, 0.5));
    });
    test('mfj, both 65+ → 29,200 + 2×1,550 = 32,300', () {
      final r = baseReturn(FilingStatus.mfj);
      r.taxpayer.dateOfBirth = '1950-01-01';
      r.spouse = TaxpayerInfo(
        firstName: r.taxpayer.firstName,
        lastName: r.taxpayer.lastName,
        ssn: r.taxpayer.ssn,
        dateOfBirth: r.taxpayer.dateOfBirth,
        occupation: r.taxpayer.occupation,
        blind: r.taxpayer.blind,
        claimedAsDependentByAnother: r.taxpayer.claimedAsDependentByAnother,
      );
      expect(computeStandardDeduction(r, 0), closeTo(32300, 0.5));
    });
    test('mfj, taxpayer 65+, spouse 65+ and blind → 29,200 + 3×1,550 = 33,850',
        () {
      final r = baseReturn(FilingStatus.mfj);
      r.taxpayer.dateOfBirth = '1950-01-01';
      r.spouse = TaxpayerInfo(
        firstName: r.taxpayer.firstName,
        lastName: r.taxpayer.lastName,
        ssn: r.taxpayer.ssn,
        dateOfBirth: r.taxpayer.dateOfBirth,
        occupation: r.taxpayer.occupation,
        blind: true,
        claimedAsDependentByAnother: r.taxpayer.claimedAsDependentByAnother,
      );
      expect(computeStandardDeduction(r, 0), closeTo(33850, 0.5));
    });
  });

  group('dependent standard deduction cap', () {
    test('dependent with \$5,000 earned income → max(1,300, 5,450) = 5,450', () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.claimedAsDependentByAnother = true;
      expect(computeStandardDeduction(r, 5000), closeTo(5450, 0.5));
    });
    test('dependent with \$200 earned income → floor of \$1,300', () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.claimedAsDependentByAnother = true;
      expect(computeStandardDeduction(r, 200), closeTo(1300, 0.5));
    });
    test('dependent with \$20,000 earned income → capped at the \$14,600 base',
        () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.claimedAsDependentByAnother = true;
      expect(computeStandardDeduction(r, 20000), closeTo(14600, 0.5));
    });
    test('dependent (\$5,000 earned) who is blind → 5,450 + 1,950 = 7,400', () {
      final r = baseReturn(FilingStatus.single);
      r.taxpayer.claimedAsDependentByAnother = true;
      r.taxpayer.blind = true;
      expect(computeStandardDeduction(r, 5000), closeTo(7400, 0.5));
    });
  });
}
