/// Pure mapping from a computed return to the neutral EfileBundle. (Real IRS
/// MeF submission is a large XML schema; this captures the summary a
/// transmitter needs and can be extended per provider.)
library;

import 'package:finnacalc/features/taxes/engine/types/result.dart';
import 'efile_provider.dart';

EfileBundle buildEfileBundle(TaxCalculationResult result) {
  return EfileBundle(
    filingStatus: result.filingStatus,
    agi: result.agi,
    taxableIncome: result.taxableIncome,
    totalTax: result.totalTax,
    totalPayments: result.totalPayments,
    refundOrOwed: result.refundOrOwed,
    state:
        result.state != null &&
            result.state!.supported &&
            result.state!.hasIncomeTax
        ? EfileBundleState(
            code: result.state!.code,
            tax: result.state!.tax,
            refundOrOwed: result.state!.refundOrOwed,
          )
        : null,
    lines: result.trace
        .map((t) => EfileBundleLine(id: t.id, label: t.label, amount: t.amount))
        .toList(),
  );
}
