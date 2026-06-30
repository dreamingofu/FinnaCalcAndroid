/// Output of the pure calculation engine. Every meaningful 1040 line is
/// exposed, plus a `trace[]` that the Review screen and tests read line-by-line.
library;

import 'filing.dart';

/// One line in the computed return — drives the Review screen and golden tests.
class LineTrace {
  LineTrace({
    required this.id,
    required this.label,
    required this.formRef,
    required this.amount,
  });

  /// Stable key, e.g. "agi", "regularTax", "ctc".
  String id;

  /// Human label, e.g. "Adjusted gross income".
  String label;

  /// IRS form/line reference, e.g. "Form 1040, line 11".
  String formRef;

  /// Dollar amount (whole dollars after IRS rounding).
  double amount;
}

/// A user-facing warning for a path the engine does not fully model.
class Warning {
  Warning({required this.code, required this.message});

  String code;
  String message;
}

/// An audit-risk / data-quality flag surfaced in the UI.
class AuditFlag {
  AuditFlag({
    required this.severity,
    required this.message,
    this.relatedLine,
  });

  /// One of "info" | "warn" | "high".
  String severity;
  String message;

  /// Related trace line id, if any.
  String? relatedLine;
}

/// The MAGI variants different rules require (they are NOT all the same
/// number).
class MagiBreakdown {
  MagiBreakdown({
    required this.niit,
    required this.ira,
    required this.studentLoan,
    required this.ptc,
    required this.ctc,
    required this.aotc,
  });

  double niit;
  double ira;
  double studentLoan;
  double ptc;
  double ctc;
  double aotc;
}

/// State result (absent for federal-only or states not yet supported).
class StateResult {
  StateResult({
    required this.code,
    required this.name,
    required this.hasIncomeTax,
    required this.supported,
    required this.stateAgi,
    required this.taxableIncome,
    required this.tax,
    required this.withheld,
    required this.refundOrOwed,
    this.note,
  });

  StateCode code;
  String name;

  /// False for no-income-tax states (TX, FL, WA, TN, …).
  bool hasIncomeTax;

  /// False when the state isn't in the supported set yet (tax left at 0).
  bool supported;
  double stateAgi;
  double taxableIncome;
  double tax;
  double withheld;

  /// Positive = refund; negative = balance due.
  double refundOrOwed;
  String? note;
}

/// Short-term / long-term capital loss carryover into next year.
class CapitalLossCarryover {
  CapitalLossCarryover({
    required this.shortTerm,
    required this.longTerm,
  });

  double shortTerm;
  double longTerm;
}

class TaxCalculationResult {
  TaxCalculationResult({
    required this.filingStatus,
    required this.totalIncome,
    required this.totalAdjustments,
    required this.agi,
    required this.magi,
    required this.standardDeduction,
    required this.itemizedDeduction,
    required this.deductionUsed,
    required this.deductionAmount,
    required this.itemizedSavings,
    required this.qbiDeduction,
    required this.taxableIncomeBeforeQbi,
    required this.taxableIncome,
    required this.regularTax,
    required this.usedTaxTable,
    required this.usedQualDivWorksheet,
    required this.amt,
    required this.additionalMedicareTax,
    required this.niit,
    required this.seTax,
    required this.nonrefundableCredits,
    required this.totalNonrefundableCredits,
    required this.refundableCredits,
    required this.totalRefundableCredits,
    required this.otherTaxes,
    required this.totalTax,
    required this.totalPayments,
    required this.refundOrOwed,
    required this.owes,
    required this.underpaymentPenalty,
    required this.marginalRate,
    required this.effectiveRate,
    required this.capitalLossCarryover,
    required this.trace,
    required this.warnings,
    required this.auditFlags,
    this.state,
  });

  FilingStatus filingStatus;

  // Income & AGI
  double totalIncome;
  double totalAdjustments;
  double agi;
  MagiBreakdown magi;

  // Deductions
  double standardDeduction;
  double itemizedDeduction;

  /// One of "standard" | "itemized".
  String deductionUsed;
  double deductionAmount;

  /// Extra tax saved by itemizing vs standard (0 if standard chosen).
  double itemizedSavings;

  // QBI & taxable income
  double qbiDeduction;
  double taxableIncomeBeforeQbi;
  double taxableIncome;

  // Tax computation
  double regularTax;
  bool usedTaxTable;
  bool usedQualDivWorksheet;
  double amt;
  double additionalMedicareTax;
  double niit;
  double seTax;

  // Credits
  Map<String, double> nonrefundableCredits;
  double totalNonrefundableCredits;
  Map<String, double> refundableCredits;
  double totalRefundableCredits;

  // Totals
  double otherTaxes;
  double totalTax;
  double totalPayments;

  /// Positive = refund; negative = balance due.
  double refundOrOwed;
  bool owes;
  double underpaymentPenalty;

  // Rates
  double marginalRate;
  double effectiveRate;

  // Carryovers & diagnostics
  CapitalLossCarryover capitalLossCarryover;
  List<LineTrace> trace;
  List<Warning> warnings;
  List<AuditFlag> auditFlags;

  // State (optional)
  StateResult? state;
}
