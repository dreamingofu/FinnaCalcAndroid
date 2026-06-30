/// Net Investment Income Tax — 3.8% on the lesser of net investment income or
/// the amount of MAGI over the filing-status threshold (Form 8960).
library;

import '../constants/filing_thresholds_2024.dart';
import '../types/filing.dart';

double computeNiit(
  double netInvestmentIncome,
  double magi,
  FilingStatus status,
) {
  final threshold = Niit2024.thresholds[status]!;
  final base = _min(_max(0, netInvestmentIncome), _max(0, magi - threshold));
  return base * Niit2024.rate;
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
