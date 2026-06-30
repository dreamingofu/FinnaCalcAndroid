/// Additional Medicare Tax — 0.9% on Medicare wages + SE earnings over the
/// filing-status threshold (Form 8959). The threshold is applied to wages first,
/// then the remainder to self-employment earnings.
library;

import '../constants/filing_thresholds_2024.dart';
import '../types/filing.dart';

double computeAdditionalMedicareTax(
  double medicareWages,
  double seNetEarnings,
  FilingStatus status,
) {
  final threshold = AdditionalMedicare2024.thresholds[status]!;
  final rate = AdditionalMedicare2024.rate;
  final onWages = _max(0, medicareWages - threshold) * rate;
  final remainingThreshold = _max(0, threshold - medicareWages);
  final onSe = _max(0, _max(0, seNetEarnings) - remainingThreshold) * rate;
  return onWages + onSe;
}

double _max(double a, double b) => a > b ? a : b;
