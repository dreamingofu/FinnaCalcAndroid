/// Alternative Minimum Tax — Form 6251 (simplified).
///
/// AMTI = taxable income (after QBI) + add-backs (the SALT itemized deduction,
/// or the standard deduction if not itemizing). The AMT exemption phases out at
/// 25% of AMTI over the threshold. The tentative minimum tax applies 26%/28% to
/// the ordinary portion of the base and the regular 0/15/20% rates to
/// preferential income. AMT = max(0, TMT − regular tax).
///
/// Other AMT preferences (ISO exercise, depletion, private-activity bond
/// interest) are not tracked; this captures the common SALT/standard-deduction
/// driver.
library;

import 'dart:math' as math;

import '../constants/amt_2024.dart';
import '../round.dart';
import '../types/filing.dart';
import 'qualified_div_cap_gain.dart';

class AmtResult {
  final double amt;
  final double tentativeMinimumTax;
  final double amti;
  final double exemption;

  const AmtResult({
    required this.amt,
    required this.tentativeMinimumTax,
    required this.amti,
    required this.exemption,
  });
}

AmtResult computeAmt({
  required double taxableIncome,
  required double addBacks,
  required double preferentialIncome,
  required double regularTax,
  required FilingStatus status,
}) {
  final amti = math.max(0.0, taxableIncome + addBacks);

  final fullExemption = Amt2024.exemption[status]!;
  final phaseStart = Amt2024.exemptionPhaseoutThreshold[status]!;
  final exemption = amti > phaseStart
      ? math.max(
          0.0,
          fullExemption - Amt2024.exemptionPhaseoutRate * (amti - phaseStart),
        )
      : fullExemption;

  final base = math.max(0.0, amti - exemption);
  final pref = math.max(0.0, math.min(preferentialIncome, base));
  final ordinaryBase = base - pref;

  final bk =
      status == FilingStatus.mfs ? Amt2024.rate28ThresholdMfs : Amt2024.rate28Threshold;
  final ordinaryTmt = ordinaryBase <= bk
      ? ordinaryBase * Amt2024.lowRate
      : bk * Amt2024.lowRate + (ordinaryBase - bk) * Amt2024.highRate;
  final prefTmt = preferentialStackTax(ordinaryBase, pref, status).tax;

  final tentativeMinimumTax = dollar(ordinaryTmt + prefTmt);
  return AmtResult(
    amt: math.max(0.0, tentativeMinimumTax - regularTax),
    tentativeMinimumTax: tentativeMinimumTax,
    amti: amti,
    exemption: exemption,
  );
}
