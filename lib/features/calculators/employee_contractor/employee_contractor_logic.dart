import 'dart:math' as math;

import '../../../core/util/parse.dart';

/// 2024 Social Security wage base used to cap the employer SS contribution.
const double ssWageBase2024 = 168600;

/// Per-employee breakdown of the true employer cost.
class EmployeeBreakdown {
  const EmployeeBreakdown({
    required this.salary,
    required this.healthDentalVision,
    required this.retirement401k,
    required this.ptoValue,
    required this.otherBenefits,
    required this.totalBenefits,
    required this.employerSS,
    required this.employerMedicare,
    required this.ficaTotal,
    required this.futaNet,
    required this.suta,
    required this.workersComp,
    required this.totalCost,
    required this.burdenRate,
  });

  final double salary;
  final double healthDentalVision;
  final double retirement401k;
  final double ptoValue;
  final double otherBenefits;
  final double totalBenefits;
  final double employerSS;
  final double employerMedicare;
  final double ficaTotal;
  final double futaNet;
  final double suta;
  final double workersComp;
  final double totalCost;
  final double burdenRate;
}

/// Contractor cost details.
class ContractorBreakdown {
  const ContractorBreakdown({
    required this.hourlyRate,
    required this.annualCost,
    required this.equivalentHourly,
  });

  final double hourlyRate;
  final double annualCost;
  final double equivalentHourly;
}

/// Full comparison result.
class EmployeeContractorResult {
  const EmployeeContractorResult({
    required this.employee,
    required this.contractor,
    required this.savings,
    required this.savingsPercentage,
    required this.recommendation,
  });

  final EmployeeBreakdown employee;
  final ContractorBreakdown contractor;
  final double savings;
  final double savingsPercentage;

  /// `"contractor"` when hiring a contractor is cheaper, else `"employee"`.
  final String recommendation;
}

/// Pure comparison math, transcribed 1:1 from
/// `app/employee-contractor-calculator/page.tsx`.
class EmployeeContractorCalculator {
  const EmployeeContractorCalculator._();

  static EmployeeContractorResult compare({
    required double annualSalary,
    required double hourlyRate,
    required double hours,
    required double weeks,
  }) {
    if (annualSalary <= 0 || hourlyRate <= 0) {
      throw const CalcException(
          'Please enter valid salary and hourly rate values.');
    }

    // Employer payroll taxes (2024)
    final ssSalary = math.min(annualSalary, ssWageBase2024);
    final employerSS = ssSalary * 0.062; // Social Security: 6.2% up to $168,600
    final employerMedicare = annualSalary * 0.0145; // Medicare: 1.45% (no cap)
    final ficaTotal = employerSS + employerMedicare;

    // FUTA (net after standard 5.4% state credit): 0.6% on first $7,000
    final futaNet = math.min(annualSalary, 7000) * 0.006; // = $42 max
    // SUTA varies by state; use 2% estimate as common midpoint
    final suta = math.min(annualSalary, 7000) * 0.02; // ≈ $140 max

    // Workers' compensation (2% of salary, industry average)
    final workersComp = annualSalary * 0.02;

    // Benefits breakdown (employer costs, 2024 estimates)
    final healthDentalVision =
        math.min(annualSalary * 0.12, 10800); // ~$9k median employer health cost
    final retirement401k = annualSalary * 0.03; // 3% match (common baseline)
    final ptoValue = annualSalary * (15 / 260); // 15 PTO days ≈ 5.77% of salary
    final otherBenefits = annualSalary * 0.02; // Life/disability/misc
    final totalBenefits =
        healthDentalVision + retirement401k + ptoValue + otherBenefits;

    final totalEmployeeCost =
        annualSalary + totalBenefits + ficaTotal + workersComp + futaNet + suta;

    final contractorAnnualCost = hourlyRate * hours * weeks;
    final contractorEquivalentHourly = totalEmployeeCost / (hours * weeks);

    final savings = totalEmployeeCost - contractorAnnualCost;
    final savingsPercentage =
        totalEmployeeCost > 0 ? (savings / totalEmployeeCost) * 100 : 0.0;

    return EmployeeContractorResult(
      employee: EmployeeBreakdown(
        salary: annualSalary,
        healthDentalVision: healthDentalVision.toDouble(),
        retirement401k: retirement401k,
        ptoValue: ptoValue,
        otherBenefits: otherBenefits,
        totalBenefits: totalBenefits,
        employerSS: employerSS,
        employerMedicare: employerMedicare,
        ficaTotal: ficaTotal,
        futaNet: futaNet,
        suta: suta,
        workersComp: workersComp,
        totalCost: totalEmployeeCost,
        burdenRate:
            ((totalEmployeeCost - annualSalary) / annualSalary) * 100,
      ),
      contractor: ContractorBreakdown(
        hourlyRate: hourlyRate,
        annualCost: contractorAnnualCost,
        equivalentHourly: contractorEquivalentHourly,
      ),
      savings: savings,
      savingsPercentage: savingsPercentage,
      recommendation: savings > 0 ? 'contractor' : 'employee',
    );
  }
}
