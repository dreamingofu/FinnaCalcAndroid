/// Premium Tax Credit reconciliation — Form 8962 (simplified).
///
/// Compares the allowed PTC against advance payments. A net positive is a
/// refundable credit; a net negative is excess advance PTC that must be repaid.
/// NOTE: the income-based repayment limitation (which caps the repayment for
/// filers under 400% of the federal poverty line) is NOT modeled — the
/// orchestrator surfaces a warning so the figure is treated as an estimate.
library;

import '../round.dart';
import '../types/tax_return.dart';

class PtcResult {
  final double netRefundable;
  final double repayment;

  const PtcResult({required this.netRefundable, required this.repayment});
}

PtcResult computePremiumTaxCredit(TaxReturn2024 r) {
  if (!r.credits.hasMarketplaceCoverage) {
    return const PtcResult(netRefundable: 0, repayment: 0);
  }
  final net =
      r.credits.premiumTaxCreditAllowed - r.credits.advancePremiumTaxCredit;
  if (net >= 0) return PtcResult(netRefundable: dollar(net), repayment: 0);
  return PtcResult(netRefundable: 0, repayment: dollar(-net));
}
