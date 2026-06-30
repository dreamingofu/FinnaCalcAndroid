import 'dart:math' as math;

/// One detailed-cost-breakdown bucket: a display [name], its summed [value], and
/// the hex [color] used for the colour dot in the list. Transcribed from the
/// web's `costCategories` array.
class CostCategory {
  const CostCategory({
    required this.name,
    required this.value,
    required this.color,
  });

  final String name;
  final double value;

  /// Hex colour string (e.g. `#3B82F6`) exactly as the web source.
  final String color;
}

/// The full computed startup-cost analysis.
class StartupCostResult {
  const StartupCostResult({
    required this.totalCosts,
    required this.recommendedBuffer,
    required this.totalWithBuffer,
    required this.totalFunding,
    required this.fundingGap,
    required this.costCategories,
  });

  final double totalCosts;
  final double recommendedBuffer;
  final double totalWithBuffer;
  final double totalFunding;
  final double fundingGap;
  final List<CostCategory> costCategories;
}

/// One business-type template: the 10 pre-fillable cost fields. `employees`,
/// `salaries` and `other` are intentionally NOT part of any template, matching
/// the web's `businessTemplates`.
class BusinessTemplate {
  const BusinessTemplate({
    required this.equipment,
    required this.inventory,
    required this.marketing,
    required this.legal,
    required this.rent,
    required this.utilities,
    required this.insurance,
    required this.permits,
    required this.website,
    required this.workingCapital,
  });

  final double equipment;
  final double inventory;
  final double marketing;
  final double legal;
  final double rent;
  final double utilities;
  final double insurance;
  final double permits;
  final double website;
  final double workingCapital;
}

/// Pure startup-cost math, transcribed 1:1 from
/// `app/startup-cost-calculator/page.tsx`.
class StartupCostCalculator {
  const StartupCostCalculator._();

  /// The `businessTemplates` map, keyed by business-type value.
  static const Map<String, BusinessTemplate> businessTemplates = {
    'retail': BusinessTemplate(
      equipment: 25000,
      inventory: 15000,
      marketing: 8000,
      legal: 3500,
      rent: 12000,
      utilities: 2000,
      insurance: 3000,
      permits: 1500,
      website: 3000,
      workingCapital: 10000,
    ),
    'restaurant': BusinessTemplate(
      equipment: 50000,
      inventory: 8000,
      marketing: 10000,
      legal: 5000,
      rent: 18000,
      utilities: 3000,
      insurance: 4000,
      permits: 3000,
      website: 2000,
      workingCapital: 15000,
    ),
    'service': BusinessTemplate(
      equipment: 8000,
      inventory: 2000,
      marketing: 5000,
      legal: 2500,
      rent: 6000,
      utilities: 1000,
      insurance: 2000,
      permits: 500,
      website: 4000,
      workingCapital: 8000,
    ),
    'online': BusinessTemplate(
      equipment: 5000,
      inventory: 10000,
      marketing: 12000,
      legal: 2000,
      rent: 0,
      utilities: 500,
      insurance: 1500,
      permits: 200,
      website: 8000,
      workingCapital: 12000,
    ),
    'manufacturing': BusinessTemplate(
      equipment: 75000,
      inventory: 25000,
      marketing: 8000,
      legal: 5000,
      rent: 15000,
      utilities: 4000,
      insurance: 6000,
      permits: 5000,
      website: 3000,
      workingCapital: 25000,
    ),
    'consulting': BusinessTemplate(
      equipment: 3000,
      inventory: 0,
      marketing: 6000,
      legal: 2000,
      rent: 3000,
      utilities: 800,
      insurance: 1500,
      permits: 300,
      website: 5000,
      workingCapital: 5000,
    ),
  };

  /// Computes the full startup analysis. All 13 cost inputs and 3 funding
  /// inputs are read regardless of which tab is active (matching the web).
  static StartupCostResult calculate({
    required double equipment,
    required double inventory,
    required double marketing,
    required double legal,
    required double rent,
    required double utilities,
    required double insurance,
    required double other,
    required double employees,
    required double salaries,
    required double permits,
    required double website,
    required double workingCapital,
    required double personalSavings,
    required double loanAmount,
    required double investorFunding,
  }) {
    final totalCosts = equipment +
        inventory +
        marketing +
        legal +
        rent +
        utilities +
        insurance +
        other +
        employees +
        salaries +
        permits +
        website +
        workingCapital;
    final recommendedBuffer = totalCosts * 0.2; // 20% buffer
    final totalWithBuffer = totalCosts + recommendedBuffer;

    final totalFunding = personalSavings + loanAmount + investorFunding;
    final fundingGap = totalWithBuffer - totalFunding;

    final costCategories = <CostCategory>[
      CostCategory(
          name: 'Equipment & Technology', value: equipment, color: '#3B82F6'),
      CostCategory(name: 'Inventory', value: inventory, color: '#10B981'),
      CostCategory(name: 'Marketing', value: marketing, color: '#F59E0B'),
      CostCategory(
          name: 'Legal & Professional', value: legal, color: '#EF4444'),
      CostCategory(
          name: 'Rent & Utilities',
          value: rent + utilities,
          color: '#8B5CF6'),
      CostCategory(
          name: 'Insurance & Permits',
          value: insurance + permits,
          color: '#06B6D4'),
      CostCategory(
          name: 'Website & Digital', value: website, color: '#84CC16'),
      CostCategory(
          name: 'Working Capital', value: workingCapital, color: '#F97316'),
      CostCategory(
          name: 'Salaries & Staff',
          value: salaries + employees,
          color: '#EC4899'),
      CostCategory(name: 'Other', value: other, color: '#6B7280'),
    ].where((category) => category.value > 0).toList();

    return StartupCostResult(
      totalCosts: totalCosts,
      recommendedBuffer: recommendedBuffer,
      totalWithBuffer: totalWithBuffer,
      totalFunding: totalFunding,
      fundingGap: fundingGap,
      costCategories: costCategories,
    );
  }

  /// `Math.min((totalFunding / totalWithBuffer) * 100, 100)` — the funding
  /// progress-bar value (0..100). May be NaN when `totalWithBuffer` is 0,
  /// exactly as the web (the bar then renders empty).
  static double progressValue(double totalFunding, double totalWithBuffer) {
    return math.min((totalFunding / totalWithBuffer) * 100, 100);
  }
}
