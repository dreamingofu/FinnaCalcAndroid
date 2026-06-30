/// Provider-agnostic e-file contract. Any transmitter (Tax1099, or a true IRS
/// MeF 1040 provider) implements [EfileProvider]. The actual network call
/// happens server-side (see app/api/efile/route.ts) so the API key never
/// reaches the client.
library;

import 'package:finnacalc/features/taxes/engine/types/filing.dart';

/// A neutral, serializable payload mapped from a computed return.
class EfileBundle {
  EfileBundle({
    required this.filingStatus,
    required this.agi,
    required this.taxableIncome,
    required this.totalTax,
    required this.totalPayments,
    required this.refundOrOwed,
    this.state,
    required this.lines,
  });

  final int taxYear = 2024;
  FilingStatus filingStatus;
  double agi;
  double taxableIncome;
  double totalTax;
  double totalPayments;
  double refundOrOwed;
  EfileBundleState? state;

  /// Line-by-line trace for the transmitter to map into its own schema.
  List<EfileBundleLine> lines;
}

class EfileBundleState {
  EfileBundleState({
    required this.code,
    required this.tax,
    required this.refundOrOwed,
  });

  StateCode code;
  double tax;
  double refundOrOwed;
}

class EfileBundleLine {
  EfileBundleLine({
    required this.id,
    required this.label,
    required this.amount,
  });

  String id;
  String label;
  double amount;
}

class EfileSubmissionResult {
  EfileSubmissionResult({
    required this.status,
    this.providerRef,
    required this.message,
  });

  /// One of "accepted" | "rejected" | "queued" | "unsupported".
  String status;
  String? providerRef;
  String message;
}

abstract class EfileProvider {
  String get name;

  /// Whether this provider can transmit an individual Form 1040 income-tax
  /// return.
  bool get supportsIndividual1040;

  Future<EfileSubmissionResult> submit(EfileBundle bundle);
}
