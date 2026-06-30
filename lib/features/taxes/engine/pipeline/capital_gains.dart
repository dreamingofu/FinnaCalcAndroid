/// Capital gains & losses — Schedule D / Form 8949.
///
/// Nets short-term and long-term transactions (plus long-term capital gain
/// distributions from 1099-DIV box 2a and any prior-year carryovers), applies
/// the \$3,000 (\$1,500 MFS) annual loss-deduction limit, and computes:
///  - includedInIncome: the amount that flows to Form 1040 (a gain, or the
///    allowed loss as a negative).
///  - preferentialLTCG: "net capital gain" (net LT gain reduced by net ST loss),
///    the amount eligible for 0/15/20% rates.
///  - carryover: short- and long-term loss carried to next year.
///
/// Carryover character follows the Schedule D Capital Loss Carryover Worksheet
/// (the allowed loss is applied to short-term first, then long-term).
library;

import 'dart:math' as math;

import '../constants/filing_thresholds_2024.dart';
import '../round.dart';
import '../types/income.dart';
import '../types/tax_return.dart';

class CapitalGainsResult {
  final double netShortTerm;
  final double netLongTerm;
  final double totalNet;
  final double includedInIncome;
  final double preferentialLTCG;
  final double allowedLoss;
  final double carryoverShort;
  final double carryoverLong;

  const CapitalGainsResult({
    required this.netShortTerm,
    required this.netLongTerm,
    required this.totalNet,
    required this.includedInIncome,
    required this.preferentialLTCG,
    required this.allowedLoss,
    required this.carryoverShort,
    required this.carryoverLong,
  });
}

const CapitalGainsResult _empty = CapitalGainsResult(
  netShortTerm: 0,
  netLongTerm: 0,
  totalNet: 0,
  includedInIncome: 0,
  preferentialLTCG: 0,
  allowedLoss: 0,
  carryoverShort: 0,
  carryoverLong: 0,
);

CapitalGainsResult computeCapitalGains(TaxReturn2024 r) {
  final f = r.income.flags;
  if (!f.hasCapitalGains && !f.hasDividends) return _empty;

  final transactions = f.hasCapitalGains ? r.income.f1099B : <CapitalTransaction>[];
  var st = 0.0;
  var lt = 0.0;
  for (final t in transactions) {
    // A wash-sale adjustment adds a disallowed loss back (reduces the loss).
    final gain = t.proceeds - t.costBasis + (t.washSaleAdjustment ?? 0);
    if (t.longTerm) {
      lt += gain;
    } else {
      st += gain;
    }
  }

  // Long-term capital gain distributions (1099-DIV box 2a) flow to Schedule D.
  if (f.hasDividends) {
    lt += sumBy(r.income.f1099Div, (d) => d.box2aCapitalGainDistributions);
  }

  // Prior-year carryovers (stored as positive loss amounts) reduce this year.
  if (f.hasCapitalGains) {
    st -= r.income.capitalLossCarryoverShort;
    lt -= r.income.capitalLossCarryoverLong;
  }

  final netShortTerm = st;
  final netLongTerm = lt;
  final totalNet = netShortTerm + netLongTerm;

  if (totalNet >= 0) {
    // Net gain — "net capital gain" is the long-term portion not offset by ST
    // loss.
    final preferentialLTCG =
        netLongTerm > 0 ? math.min(netLongTerm, totalNet) : 0.0;
    return CapitalGainsResult(
      netShortTerm: netShortTerm,
      netLongTerm: netLongTerm,
      totalNet: totalNet,
      includedInIncome: totalNet,
      preferentialLTCG: preferentialLTCG,
      allowedLoss: _empty.allowedLoss,
      carryoverShort: _empty.carryoverShort,
      carryoverLong: _empty.carryoverLong,
    );
  }

  // Net loss — limited deduction this year, remainder carries over.
  final limit = capitalLossLimit2024[r.filingStatus]!;
  final allowedLoss = math.min(limit, totalNet.abs());

  // Carryover character (allowed loss applied to short-term first).
  var carryoverShort = 0.0;
  var carryoverLong = 0.0;
  var remainingAllowed = allowedLoss;
  if (netShortTerm < 0) {
    final stLoss = -netShortTerm;
    final ltGain = math.max(0.0, netLongTerm);
    carryoverShort = math.max(0.0, stLoss - ltGain - allowedLoss);
    final usedAgainstSt = math.min(allowedLoss, math.max(0.0, stLoss - ltGain));
    remainingAllowed = allowedLoss - usedAgainstSt;
  }
  if (netLongTerm < 0) {
    final ltLoss = -netLongTerm;
    final stGain = math.max(0.0, netShortTerm);
    carryoverLong = math.max(0.0, ltLoss - stGain - remainingAllowed);
  }

  return CapitalGainsResult(
    netShortTerm: netShortTerm,
    netLongTerm: netLongTerm,
    totalNet: totalNet,
    includedInIncome: -allowedLoss,
    preferentialLTCG: 0,
    allowedLoss: allowedLoss,
    carryoverShort: carryoverShort,
    carryoverLong: carryoverLong,
  );
}
