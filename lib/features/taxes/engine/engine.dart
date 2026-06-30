/// Public engine barrel — the single import surface for the tax engine.
///
/// Re-exports the orchestrator (calculateFederalTax), every input/output type,
/// the 2024 constants, and the state-tax dispatcher (computeStateTax) plus the
/// supported-state list.
library;

export 'calculator.dart';
export 'constants/index.dart';
export 'round.dart';
export 'state/state_tax.dart' show computeStateTax, supportedStates, SupportedState;
export 'state/state_types.dart';
export 'types/adjustments.dart';
export 'types/credits.dart';
export 'types/deductions.dart';
export 'types/filing.dart';
export 'types/income.dart';
export 'types/payments.dart';
export 'types/question.dart';
export 'types/result.dart';
export 'types/tax_return.dart';
