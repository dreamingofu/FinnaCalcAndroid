/// Alternative Minimum Tax — 2024 (Form 6251).
/// Source: Rev. Proc. 2023-34 §2.11; IRC §55.
library;

import '../types/filing.dart';

class Amt2024 {
  static const Map<FilingStatus, double> exemption = {
    FilingStatus.single: 85700,
    FilingStatus.hoh: 85700,
    FilingStatus.mfj: 133300,
    FilingStatus.qss: 133300,
    FilingStatus.mfs: 66650,
  };

  /// Exemption phases out at 25¢ per $1 of AMTI over this threshold.
  static const Map<FilingStatus, double> exemptionPhaseoutThreshold = {
    FilingStatus.single: 609350,
    FilingStatus.hoh: 609350,
    FilingStatus.mfs: 609350,
    FilingStatus.mfj: 1218700,
    FilingStatus.qss: 1218700,
  };

  static const double exemptionPhaseoutRate = 0.25;

  /// AMT is 26% up to this AMT base, 28% above (halved for MFS).
  static const double rate28Threshold = 232600;
  static const double rate28ThresholdMfs = 116300;
  static const double lowRate = 0.26;
  static const double highRate = 0.28;
}
