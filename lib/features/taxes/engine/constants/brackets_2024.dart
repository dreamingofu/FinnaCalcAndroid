/// 2024 ordinary income tax rate schedules (Tax Year 2024, filed in 2025).
///
/// Source: Rev. Proc. 2023-34 §2.01; 2024 Form 1040 Tax Rate Schedules /
/// 1040-ES (2024). Each bracket is [min, max) of TAXABLE income at `rate`.
/// QSS (qualifying surviving spouse) uses the MFJ schedule.
library;

import '../types/filing.dart';

class Bracket {
  /// Marginal rate as a decimal (0.22 = 22%).
  final double rate;

  /// Lower bound of taxable income for this bracket (inclusive).
  final double min;

  /// Upper bound of taxable income for this bracket (exclusive); Infinity for the top.
  final double max;

  const Bracket({required this.rate, required this.min, required this.max});
}

const List<Bracket> _single = [
  Bracket(rate: 0.1, min: 0, max: 11600),
  Bracket(rate: 0.12, min: 11600, max: 47150),
  Bracket(rate: 0.22, min: 47150, max: 100525),
  Bracket(rate: 0.24, min: 100525, max: 191950),
  Bracket(rate: 0.32, min: 191950, max: 243725),
  Bracket(rate: 0.35, min: 243725, max: 609350),
  Bracket(rate: 0.37, min: 609350, max: double.infinity),
];

const List<Bracket> _mfj = [
  Bracket(rate: 0.1, min: 0, max: 23200),
  Bracket(rate: 0.12, min: 23200, max: 94300),
  Bracket(rate: 0.22, min: 94300, max: 201050),
  Bracket(rate: 0.24, min: 201050, max: 383900),
  Bracket(rate: 0.32, min: 383900, max: 487450),
  Bracket(rate: 0.35, min: 487450, max: 731200),
  Bracket(rate: 0.37, min: 731200, max: double.infinity),
];

const List<Bracket> _mfs = [
  Bracket(rate: 0.1, min: 0, max: 11600),
  Bracket(rate: 0.12, min: 11600, max: 47150),
  Bracket(rate: 0.22, min: 47150, max: 100525),
  Bracket(rate: 0.24, min: 100525, max: 191950),
  Bracket(rate: 0.32, min: 191950, max: 243725),
  Bracket(rate: 0.35, min: 243725, max: 365600),
  Bracket(rate: 0.37, min: 365600, max: double.infinity),
];

const List<Bracket> _hoh = [
  Bracket(rate: 0.1, min: 0, max: 16550),
  Bracket(rate: 0.12, min: 16550, max: 63100),
  Bracket(rate: 0.22, min: 63100, max: 100500),
  Bracket(rate: 0.24, min: 100500, max: 191950),
  Bracket(rate: 0.32, min: 191950, max: 243700),
  Bracket(rate: 0.35, min: 243700, max: 609350),
  Bracket(rate: 0.37, min: 609350, max: double.infinity),
];

const Map<FilingStatus, List<Bracket>> ordinaryBrackets2024 = {
  FilingStatus.single: _single,
  FilingStatus.mfj: _mfj,
  FilingStatus.qss: _mfj, // QSS uses the MFJ schedule
  FilingStatus.mfs: _mfs,
  FilingStatus.hoh: _hoh,
};

/// 2024 long-term capital gains / qualified dividends rate breakpoints.
/// Source: Rev. Proc. 2023-34 §2.03. Values are the TAXABLE-income thresholds
/// where the 0%->15% and 15%->20% rates begin.
class CapGainBreakpoints {
  /// At/below this taxable income, preferential rate is 0%.
  final double zeroRateMax;

  /// Above `zeroRateMax` up to this amount, preferential rate is 15%; above is 20%.
  final double fifteenRateMax;

  const CapGainBreakpoints({
    required this.zeroRateMax,
    required this.fifteenRateMax,
  });
}

const Map<FilingStatus, CapGainBreakpoints> capGainBreakpoints2024 = {
  FilingStatus.single:
      CapGainBreakpoints(zeroRateMax: 47025, fifteenRateMax: 518900),
  FilingStatus.mfj:
      CapGainBreakpoints(zeroRateMax: 94050, fifteenRateMax: 583750),
  FilingStatus.qss:
      CapGainBreakpoints(zeroRateMax: 94050, fifteenRateMax: 583750),
  FilingStatus.mfs:
      CapGainBreakpoints(zeroRateMax: 47025, fifteenRateMax: 291850),
  FilingStatus.hoh:
      CapGainBreakpoints(zeroRateMax: 63000, fifteenRateMax: 551350),
};
