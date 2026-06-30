import '../../../core/util/parse.dart';

/// Industry benchmark entry. Only [hourlyRange] (`[low, high]`) is used by the
/// math; `profitMargin` is transcribed for fidelity but not consumed.
class IndustryBenchmark {
  const IndustryBenchmark(this.hourlyRange, this.profitMargin);
  final List<double> hourlyRange;
  final double profitMargin;
}

/// `industryBenchmarks` map, transcribed 1:1 from
/// `app/pricing-calculator/page.tsx`. Keys match the select option values.
const Map<String, IndustryBenchmark> industryBenchmarks = {
  'consulting': IndustryBenchmark([75, 200], 25),
  'design': IndustryBenchmark([50, 150], 30),
  'development': IndustryBenchmark([60, 180], 35),
  'marketing': IndustryBenchmark([40, 120], 28),
  'legal': IndustryBenchmark([150, 500], 40),
  'accounting': IndustryBenchmark([50, 150], 30),
  'coaching': IndustryBenchmark([75, 300], 45),
  'freelance': IndustryBenchmark([25, 100], 20),
  'healthcare': IndustryBenchmark([80, 250], 20),
  'trades': IndustryBenchmark([45, 120], 25),
  'realestate': IndustryBenchmark([50, 150], 30),
  'education': IndustryBenchmark([30, 100], 20),
  'other': IndustryBenchmark([20, 80], 15),
};

/// One row of the service "Pricing Scenarios" panel.
class PricingScenario {
  const PricingScenario({
    required this.name,
    required this.description,
    required this.rate,
    required this.annualRevenue,
    required this.netIncome,
  });
  final String name;
  final String description;
  final double rate;
  final double annualRevenue;
  final double netIncome;
}

/// One row of the product "Pricing Strategies" panel.
class PricingStrategy {
  const PricingStrategy({
    required this.name,
    required this.description,
    required this.price,
    required this.profit,
    required this.margin,
  });
  final String name;
  final String description;
  final double price;
  final double profit;
  final double margin;
}

class ServicePricingResult {
  const ServicePricingResult({
    required this.annualRevenue,
    required this.netIncome,
    required this.grossProfit,
    required this.requiredHourlyRate,
    required this.breakEvenRate,
    required this.currentRate,
    required this.totalHours,
    required this.effectiveHourlyRate,
    required this.industryData,
    required this.isCompetitive,
    required this.scenarios,
    required this.profitMarginActual,
  });

  final double annualRevenue;
  final double netIncome;
  final double grossProfit;
  final double requiredHourlyRate;
  final double breakEvenRate;
  final double currentRate;
  final double totalHours;
  final double effectiveHourlyRate;

  /// Null when no industry was selected.
  final IndustryBenchmark? industryData;

  /// Null when no industry was selected (web's `null`), else whether the current
  /// rate falls inside the industry hourly range.
  final bool? isCompetitive;

  final List<PricingScenario> scenarios;
  final double profitMarginActual;
}

class ProductPricingResult {
  const ProductPricingResult({
    required this.cost,
    required this.sellingPrice,
    required this.profit,
    required this.markupPercentage,
    required this.marginPercentage,
    required this.totalPrice,
    required this.netProfit,
    required this.volumePrice,
    required this.volumeProfit,
    required this.competitiveAdvantage,
    required this.strategies,
  });

  final double cost;
  final double sellingPrice;
  final double profit;
  final double markupPercentage;
  final double marginPercentage;

  // Computed but NOT rendered (kept for fidelity with the web).
  final double totalPrice;
  final double netProfit;
  final double volumePrice;
  final double volumeProfit;

  final double competitiveAdvantage;
  final List<PricingStrategy> strategies;
}

/// Pure pricing math, transcribed 1:1 from `app/pricing-calculator/page.tsx`.
class PricingCalculator {
  const PricingCalculator._();

  /// Mirrors `calculateServicePricing`. `profitMargin` is collected by the UI
  /// but not used in the math (matches the web exactly).
  static ServicePricingResult service({
    required double rate,
    required double hours,
    required double weeks,
    required double annualExpenses,
    required double salary,
    required double tax,
    String? industryType,
  }) {
    final totalBillableHours = hours * weeks;
    final annualRevenue = rate * totalBillableHours;
    final grossProfit = annualRevenue - annualExpenses;
    final netIncome = grossProfit * (1 - tax / 100);

    final requiredGrossIncome = salary / (1 - tax / 100);
    final requiredRevenue = requiredGrossIncome + annualExpenses;
    final requiredHourlyRate =
        totalBillableHours > 0 ? requiredRevenue / totalBillableHours : 0.0;

    final breakEvenRate =
        totalBillableHours > 0 ? annualExpenses / totalBillableHours : 0.0;

    final industryData = (industryType != null && industryType.isNotEmpty)
        ? industryBenchmarks[industryType]
        : null;
    final bool? isCompetitive = industryData != null
        ? rate >= industryData.hourlyRange[0] &&
            rate <= industryData.hourlyRange[1]
        : null;

    final scenarioDefs = <List<Object>>[
      ['Conservative', rate * 0.8, '20% below current rate'],
      ['Current', rate, 'Your current rate'],
      ['Optimistic', rate * 1.2, '20% above current rate'],
      ['Premium', rate * 1.5, '50% premium pricing'],
    ];
    final scenarios = scenarioDefs.map((def) {
      final sRate = def[1] as double;
      return PricingScenario(
        name: def[0] as String,
        description: def[2] as String,
        rate: sRate,
        annualRevenue: sRate * totalBillableHours,
        netIncome:
            (sRate * totalBillableHours - annualExpenses) * (1 - tax / 100),
      );
    }).toList();

    return ServicePricingResult(
      annualRevenue: annualRevenue,
      netIncome: netIncome,
      grossProfit: grossProfit,
      requiredHourlyRate: requiredHourlyRate.toDouble(),
      breakEvenRate: breakEvenRate.toDouble(),
      currentRate: rate,
      totalHours: totalBillableHours,
      effectiveHourlyRate:
          totalBillableHours > 0 ? netIncome / totalBillableHours : 0.0,
      industryData: industryData,
      isCompetitive: isCompetitive,
      scenarios: scenarios,
      profitMarginActual:
          annualRevenue > 0 ? (grossProfit / annualRevenue) * 100 : 0.0,
    );
  }

  /// Mirrors `calculateProductPricing`. Throws [CalcException] for the two
  /// validation branches (margin >= 100, cost < 0).
  static ProductPricingResult product({
    required double cost,
    required double margin,
    required double competitor,
    required double discount,
    required double shipping,
  }) {
    if (margin >= 100) {
      throw const CalcException('Profit margin must be less than 100%');
    }
    if (cost < 0) {
      throw const CalcException('Product cost cannot be negative.');
    }

    final sellingPrice = cost > 0 ? cost / (1 - margin / 100) : 0.0;
    final profit = sellingPrice - cost;
    final markupPercentage = cost > 0 ? (profit / cost) * 100 : 0.0;

    final totalPrice = sellingPrice + shipping;
    final netProfit = profit - shipping;

    final volumePrice = sellingPrice * (1 - discount / 100);
    final volumeProfit = volumePrice - cost;

    final competitiveAdvantage =
        competitor > 0 ? ((competitor - sellingPrice) / competitor) * 100 : 0.0;

    final strategyDefs = <List<Object>>[
      ['Cost-Plus', sellingPrice, 'Standard markup pricing'],
      ['Competitive', competitor * 0.95, '5% below competitor'],
      ['Premium', sellingPrice * 1.3, '30% premium positioning'],
      ['Penetration', sellingPrice * 0.8, '20% below standard for market entry'],
    ];
    final strategies = strategyDefs
        .where((def) => (def[1] as double) > 0)
        .map((def) {
      final price = def[1] as double;
      return PricingStrategy(
        name: def[0] as String,
        description: def[2] as String,
        price: price,
        profit: price - cost,
        margin: price > 0 ? ((price - cost) / price) * 100 : 0.0,
      );
    }).toList();

    return ProductPricingResult(
      cost: cost,
      sellingPrice: sellingPrice,
      profit: profit,
      markupPercentage: markupPercentage.toDouble(),
      marginPercentage: margin,
      totalPrice: totalPrice,
      netProfit: netProfit,
      volumePrice: volumePrice,
      volumeProfit: volumeProfit,
      competitiveAdvantage: competitiveAdvantage.toDouble(),
      strategies: strategies,
    );
  }
}
