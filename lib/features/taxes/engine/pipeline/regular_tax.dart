/// Regular income tax (Form 1040 line 16, ordinary-income path).
///
/// For taxable income under \$100,000 the IRS REQUIRES the Tax Table, which
/// taxes the midpoint of a \$50 bucket — this differs from the straight bracket
/// formula by a few dollars. At/above \$100,000 the Tax Computation Worksheet
/// applies the rate schedule directly. Both are implemented here.
///
/// (Qualified dividends / long-term capital gains use a separate worksheet
/// added in Phase 2; this module is the ordinary-income computation.)
library;

import '../constants/brackets_2024.dart';
import '../round.dart';
import '../types/filing.dart';

/// Tax from the rate schedule (exact, in cents) on a positive amount.
double bracketTax(double amount, FilingStatus status) {
  if (amount <= 0) return 0;
  var tax = 0.0;
  for (final b in ordinaryBrackets2024[status]!) {
    if (amount > b.min) {
      final upper = amount < b.max ? amount : b.max;
      tax += (upper - b.min) * b.rate;
    }
  }
  return tax;
}

/// The marginal rate that applies at a given taxable income.
double marginalRate(double taxableIncome, FilingStatus status) {
  var rate = 0.0;
  for (final b in ordinaryBrackets2024[status]!) {
    if (taxableIncome > b.min) rate = b.rate;
  }
  return rate;
}

/// The IRS Tax Table taxes the midpoint of a \$50 bucket. Below \$50 the table
/// uses irregular small rows: \$0–5, \$5–15, \$15–25, \$25–50. Returns the
/// income amount the bracket formula should be applied to.
double _taxTableBasis(double ti) {
  if (ti < 5) return 2.5;
  if (ti < 15) return 10;
  if (ti < 25) return 20;
  if (ti < 50) return 37.5;
  return (ti / 50).floor() * 50 + 25;
}

class RegularTaxResult {
  final double tax;
  final bool usedTaxTable;
  final double marginalRate;

  const RegularTaxResult({
    required this.tax,
    required this.usedTaxTable,
    required this.marginalRate,
  });
}

/// Compute regular tax on ordinary taxable income (Tax Table vs Computation
/// Worksheet).
RegularTaxResult computeRegularTax(double taxableIncome, FilingStatus status) {
  final ti = taxableIncome > 0 ? taxableIncome : 0.0;
  final mr = marginalRate(ti, status);
  if (ti < 100000) {
    return RegularTaxResult(
      tax: dollar(bracketTax(_taxTableBasis(ti), status)),
      usedTaxTable: true,
      marginalRate: mr,
    );
  }
  return RegularTaxResult(
    tax: dollar(bracketTax(ti, status)),
    usedTaxTable: false,
    marginalRate: mr,
  );
}
