import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/employee_contractor/employee_contractor_logic.dart';

void main() {
  group('EmployeeContractorCalculator.compare', () {
    test('60k salary, \$40/hr, 40h x 50wks -> employee is cheaper', () {
      final r = EmployeeContractorCalculator.compare(
        annualSalary: 60000,
        hourlyRate: 40,
        hours: 40,
        weeks: 50,
      );
      expect(r.employee.employerSS, closeTo(3720, 0.001));
      expect(r.employee.employerMedicare, closeTo(870, 0.001));
      expect(r.employee.ficaTotal, closeTo(4590, 0.001));
      expect(r.employee.futaNet, closeTo(42, 0.001));
      expect(r.employee.suta, closeTo(140, 0.001));
      expect(r.employee.workersComp, closeTo(1200, 0.001));
      expect(r.employee.healthDentalVision, closeTo(7200, 0.001));
      expect(r.employee.retirement401k, closeTo(1800, 0.001));
      expect(r.employee.ptoValue, closeTo(3461.5385, 0.01));
      expect(r.employee.otherBenefits, closeTo(1200, 0.001));
      expect(r.employee.totalBenefits, closeTo(13661.5385, 0.01));
      expect(r.employee.totalCost, closeTo(79633.5385, 0.01));
      expect(r.contractor.annualCost, closeTo(80000, 0.001));
      expect(r.contractor.equivalentHourly, closeTo(39.8168, 0.01));
      expect(r.savings, closeTo(-366.4615, 0.01));
      expect(r.recommendation, 'employee');
    });

    test('high salary, low rate -> contractor is cheaper', () {
      final r = EmployeeContractorCalculator.compare(
        annualSalary: 100000,
        hourlyRate: 30,
        hours: 40,
        weeks: 50,
      );
      // contractorAnnualCost = 30 * 40 * 50 = 60000, far below employee cost.
      expect(r.contractor.annualCost, closeTo(60000, 0.001));
      expect(r.savings, greaterThan(0));
      expect(r.recommendation, 'contractor');
    });

    test('SS contribution caps at the 2024 wage base', () {
      final r = EmployeeContractorCalculator.compare(
        annualSalary: 200000,
        hourlyRate: 100,
        hours: 40,
        weeks: 50,
      );
      // min(200000, 168600) * 0.062
      expect(r.employee.employerSS, closeTo(168600 * 0.062, 0.001));
      // health caps at 10800 (200000 * 0.12 = 24000 > cap)
      expect(r.employee.healthDentalVision, closeTo(10800, 0.001));
      // futa & suta cap at the 7000 base
      expect(r.employee.futaNet, closeTo(42, 0.001));
      expect(r.employee.suta, closeTo(140, 0.001));
    });

    test('burden rate matches the cost-over-salary ratio', () {
      final r = EmployeeContractorCalculator.compare(
        annualSalary: 60000,
        hourlyRate: 40,
        hours: 40,
        weeks: 50,
      );
      final expected = ((r.employee.totalCost - 60000) / 60000) * 100;
      expect(r.employee.burdenRate, closeTo(expected, 0.0001));
    });

    test('non-positive salary throws CalcException', () {
      expect(
        () => EmployeeContractorCalculator.compare(
          annualSalary: 0,
          hourlyRate: 40,
          hours: 40,
          weeks: 50,
        ),
        throwsA(isA<CalcException>()),
      );
    });

    test('non-positive hourly rate throws CalcException', () {
      expect(
        () => EmployeeContractorCalculator.compare(
          annualSalary: 60000,
          hourlyRate: 0,
          hours: 40,
          weeks: 50,
        ),
        throwsA(isA<CalcException>()),
      );
    });
  });
}
