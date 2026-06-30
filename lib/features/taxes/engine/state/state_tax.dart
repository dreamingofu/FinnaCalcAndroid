/// State tax dispatcher. Applies a state's config to the federal-derived inputs
/// and returns a StateResult. States not in the supported set return a result
/// with `supported: false` (tax left at 0) so the UI can be honest rather than
/// wrong.
library;

import '../round.dart';
import '../types/filing.dart';
import '../types/result.dart';
import 'data_2024.dart';
import 'state_types.dart';

double _bracketTax(double amount, List<StateBracket> brackets) {
  if (amount <= 0) return 0;
  var tax = 0.0;
  for (final b in brackets) {
    if (amount > b.min) {
      final upper = amount < b.max ? amount : b.max;
      tax += (upper - b.min) * b.rate;
    }
  }
  return tax;
}

bool _isMarried(FilingStatus s) =>
    s == FilingStatus.mfj || s == FilingStatus.qss;

StateResult _computeFromConfig(StateConfig cfg, StateInput input) {
  if (!cfg.hasIncomeTax || cfg.brackets == null) {
    return StateResult(
      code: cfg.code,
      name: cfg.name,
      withheld: dollar(input.stateWithholding),
      hasIncomeTax: false,
      supported: true,
      stateAgi: 0,
      taxableIncome: 0,
      tax: 0,
      refundOrOwed: dollar(input.stateWithholding),
      note: cfg.note,
    );
  }

  final persons = 1 + (_isMarried(input.filingStatus) ? 1 : 0);

  var stateAgi = input.federalAgi;
  if (!(cfg.taxesSocialSecurity ?? false)) {
    stateAgi -= input.taxableSocialSecurity;
  }
  if (cfg.excludesRetirement ?? false) {
    stateAgi -= input.retirementDistributions;
  }
  stateAgi = nonNeg(stateAgi);

  final standardDeduction = cfg.standardDeduction != null
      ? cfg.standardDeduction![input.filingStatus]!
      : 0.0;
  final exemptions = (cfg.personalExemption ?? 0) * persons +
      (cfg.dependentExemption ?? 0) * input.dependents;
  final taxableIncome = nonNeg(stateAgi - standardDeduction - exemptions);

  var tax = _bracketTax(taxableIncome, cfg.brackets![input.filingStatus]!);
  // Exemption credits (e.g. California) reduce tax directly.
  final credits = (cfg.exemptionCredit ?? 0) * persons +
      (cfg.dependentExemptionCredit ?? 0) * input.dependents;
  tax = nonNeg(dollar(tax) - credits);

  return StateResult(
    code: cfg.code,
    name: cfg.name,
    withheld: dollar(input.stateWithholding),
    hasIncomeTax: true,
    supported: true,
    stateAgi: dollar(stateAgi),
    taxableIncome: dollar(taxableIncome),
    tax: dollar(tax),
    refundOrOwed: dollar(input.stateWithholding - tax),
    note: cfg.note,
  );
}

/// Compute the state result, or null when no state of residence is set.
StateResult? computeStateTax(StateInput input) {
  if (input.code.isEmpty) return null;
  final cfg = stateConfigs[input.code];
  if (cfg == null) {
    return StateResult(
      code: input.code,
      name: input.code,
      hasIncomeTax: true,
      supported: false,
      stateAgi: 0,
      taxableIncome: 0,
      tax: 0,
      withheld: dollar(input.stateWithholding),
      refundOrOwed: dollar(input.stateWithholding),
      note: "State tax for this state isn't estimated yet.",
    );
  }
  return _computeFromConfig(cfg, input);
}

/// One entry in the supported-state dropdown.
class SupportedState {
  final StateCode code;
  final String name;
  final bool hasIncomeTax;

  const SupportedState({
    required this.code,
    required this.name,
    required this.hasIncomeTax,
  });
}

/// The set of state codes the engine has data for (the supported dropdown).
final List<SupportedState> supportedStates = stateConfigs.values
    .map((c) => SupportedState(
        code: c.code, name: c.name, hasIncomeTax: c.hasIncomeTax))
    .toList();
