/// Total federal payments (Form 1040 lines 25–26) — withholding from all forms
/// plus estimated payments. Refundable credits (EITC/ACTC/etc.) are added by
/// the orchestrator into total payments per the 1040 ordering.
library;

import '../round.dart';
import '../types/tax_return.dart';

class PaymentsResult {
  final double withholding;
  final double estimatedPayments;

  /// Withholding + estimated (excludes refundable credits).
  final double total;

  const PaymentsResult({
    required this.withholding,
    required this.estimatedPayments,
    required this.total,
  });
}

PaymentsResult computeWithholdingAndPayments(TaxReturn2024 r) {
  final f = r.income.flags;
  final w2Withholding =
      f.hasW2 ? sumBy(r.income.w2, (w) => w.box2FederalWithholding) : 0.0;
  final intWithholding = f.hasInterest
      ? sumBy(r.income.f1099Int, (i) => i.box4FederalWithholding)
      : 0.0;
  final divWithholding = f.hasDividends
      ? sumBy(r.income.f1099Div, (d) => d.box4FederalWithholding)
      : 0.0;
  final retirementWithholding = f.hasRetirementDistributions
      ? sumBy(r.income.f1099R, (x) => x.box4FederalWithholding)
      : 0.0;
  final unemploymentWithholding = f.hasUnemployment
      ? sumBy(r.income.f1099G, (g) => g.box4FederalWithholding)
      : 0.0;
  final ssaWithholding = f.hasSocialSecurity
      ? sumBy(r.income.f1099Ssa, (s) => s.federalWithholding)
      : 0.0;

  final withholding = w2Withholding +
      intWithholding +
      divWithholding +
      retirementWithholding +
      unemploymentWithholding +
      ssaWithholding +
      r.payments.additionalWithholding;

  final estimatedPayments = r.payments.estimatedPayments;
  return PaymentsResult(
    withholding: withholding,
    estimatedPayments: estimatedPayments,
    total: withholding + estimatedPayments,
  );
}
