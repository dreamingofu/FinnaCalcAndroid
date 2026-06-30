/// Gross income aggregation — the ordinary-rate income reported directly on the
/// front of Form 1040 (lines 1–9): wages, taxable interest, ordinary dividends
/// (which include the qualified subset for income purposes), unemployment, and
/// other income. Schedule C/E/SE, capital gains, and taxable Social Security are
/// added by the orchestrator. Also surfaces qualified dividends and tax-exempt
/// interest for the tax and Social Security worksheets.
///
/// The engine only reads a source when its interview flag is set, so hidden /
/// irrelevant income never affects the result.
library;

import '../round.dart';
import '../types/tax_return.dart';

class GrossIncomeResult {
  final double wages;
  final double taxableInterest;
  final double taxExemptInterest;
  final double ordinaryDividends;
  final double qualifiedDividends;
  final double unemployment;

  /// Taxable amount of pension/IRA/retirement distributions (1099-R box 2a).
  final double retirementDistributions;
  final double otherIncome;

  /// Wages + interest + ordinary dividends + retirement + unemployment + other
  /// (no Sch C/D/E/SS).
  final double ordinaryTotal;

  /// Earned income from wages (for CTC/ACTC and the dependent standard
  /// deduction).
  final double wageEarnedIncome;

  const GrossIncomeResult({
    required this.wages,
    required this.taxableInterest,
    required this.taxExemptInterest,
    required this.ordinaryDividends,
    required this.qualifiedDividends,
    required this.unemployment,
    required this.retirementDistributions,
    required this.otherIncome,
    required this.ordinaryTotal,
    required this.wageEarnedIncome,
  });
}

GrossIncomeResult computeGrossIncome(TaxReturn2024 r) {
  final f = r.income.flags;

  final wages = f.hasW2 ? sumBy(r.income.w2, (w) => w.box1Wages) : 0.0;
  final taxableInterest = f.hasInterest
      ? sumBy(r.income.f1099Int, (i) => i.box1Interest + i.box3UsTreasuryInterest)
      : 0.0;
  final taxExemptInterest = f.hasInterest
      ? sumBy(r.income.f1099Int, (i) => i.box8TaxExemptInterest)
      : 0.0;
  final ordinaryDividends = f.hasDividends
      ? sumBy(r.income.f1099Div, (d) => d.box1aOrdinaryDividends)
      : 0.0;
  final qualifiedDividends = f.hasDividends
      ? sumBy(r.income.f1099Div, (d) => d.box1bQualifiedDividends)
      : 0.0;
  final unemployment = f.hasUnemployment
      ? sumBy(r.income.f1099G, (g) => g.box1Unemployment)
      : 0.0;
  final retirementDistributions = f.hasRetirementDistributions
      ? sumBy(r.income.f1099R, (x) => x.box2aTaxableAmount)
      : 0.0;
  final otherIncome = f.hasOtherIncome ? r.income.otherIncome : 0.0;

  final ordinaryTotal = wages +
      taxableInterest +
      ordinaryDividends +
      retirementDistributions +
      unemployment +
      otherIncome;

  return GrossIncomeResult(
    wages: wages,
    taxableInterest: taxableInterest,
    taxExemptInterest: taxExemptInterest,
    ordinaryDividends: ordinaryDividends,
    qualifiedDividends: qualifiedDividends,
    unemployment: unemployment,
    retirementDistributions: retirementDistributions,
    otherIncome: otherIncome,
    ordinaryTotal: ordinaryTotal,
    wageEarnedIncome: wages,
  );
}
