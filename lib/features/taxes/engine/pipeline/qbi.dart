/// Qualified Business Income deduction — §199A (Forms 8995 / 8995-A).
///
/// Below the taxable-income threshold: simply 20% of QBI, capped at 20% of
/// (taxable income − net capital gain). Above the threshold an SSTB is phased
/// out over the next \$50k (\$100k MFJ); for a non-SSTB the W-2 wage / UBIA
/// limit applies — we don't track business W-2 wages, so the orchestrator flags
/// this for high earners rather than silently over-deducting.
library;

import '../constants/qbi_2024.dart';
import '../types/filing.dart';

class QbiResult {
  final double deduction;

  /// True when a non-SSTB filer is over the threshold and the (untracked)
  /// W-2/UBIA limit could reduce the deduction.
  final bool wageLimitMayApply;

  const QbiResult({required this.deduction, required this.wageLimitMayApply});
}

QbiResult computeQbiDeduction({
  required double qbiIncome,
  required double taxableIncomeBeforeQbi,
  required double netCapitalGain,
  required bool isSSTB,
  required FilingStatus status,
}) {
  if (qbiIncome <= 0) {
    return const QbiResult(deduction: 0, wageLimitMayApply: false);
  }

  final overallLimit =
      Qbi2024.rate * _max(0, taxableIncomeBeforeQbi - netCapitalGain);
  final threshold = Qbi2024.threshold[status]!;
  final range = Qbi2024.phaseInRange[status]!;

  // Below threshold: simple 20%, capped by the overall taxable-income limit.
  if (taxableIncomeBeforeQbi <= threshold) {
    return QbiResult(
      deduction: _min(Qbi2024.rate * qbiIncome, overallLimit),
      wageLimitMayApply: false,
    );
  }

  final over = taxableIncomeBeforeQbi - threshold;

  if (isSSTB) {
    // Fully phased out at threshold + range.
    if (over >= range) {
      return const QbiResult(deduction: 0, wageLimitMayApply: false);
    }
    final applicablePct = 1 - over / range;
    final deduction =
        _min(Qbi2024.rate * qbiIncome * applicablePct, overallLimit);
    return QbiResult(deduction: deduction, wageLimitMayApply: false);
  }

  // Non-SSTB above threshold: the W-2/UBIA limit governs but isn't tracked here.
  return QbiResult(
    deduction: _min(Qbi2024.rate * qbiIncome, overallLimit),
    wageLimitMayApply: true,
  );
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
