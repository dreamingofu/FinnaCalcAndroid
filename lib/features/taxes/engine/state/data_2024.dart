/// 2024 state income tax data for the 15 most populous states.
/// Sources: each state's 2024 income tax instructions / rate schedules and DOR
/// inflation announcements. No-income-tax states return zero.
library;

import '../types/filing.dart';
import 'state_types.dart';

List<StateBracket> _flat(double rate) =>
    [StateBracket(rate: rate, min: 0, max: double.infinity)];

/// Same brackets for every filing status.
Map<FilingStatus, List<StateBracket>> _uniform(List<StateBracket> b) {
  return {
    FilingStatus.single: b,
    FilingStatus.mfj: b,
    FilingStatus.mfs: b,
    FilingStatus.hoh: b,
    FilingStatus.qss: b,
  };
}

/// Map single/mfj/hoh → all statuses (mfs uses single; qss uses mfj).
Map<FilingStatus, List<StateBracket>> _byStatus({
  required List<StateBracket> single,
  required List<StateBracket> mfj,
  required List<StateBracket> hoh,
}) {
  return {
    FilingStatus.single: single,
    FilingStatus.mfs: single,
    FilingStatus.mfj: mfj,
    FilingStatus.qss: mfj,
    FilingStatus.hoh: hoh,
  };
}

/// Standard deduction record from the common (single, mfj, hoh) values.
Map<FilingStatus, double> _std(
  double single,
  double mfj,
  double hoh, [
  double? mfs,
  double? qss,
]) {
  return {
    FilingStatus.single: single,
    FilingStatus.mfj: mfj,
    FilingStatus.hoh: hoh,
    FilingStatus.mfs: mfs ?? single,
    FilingStatus.qss: qss ?? mfj,
  };
}

const String _noTaxNote = 'No state income tax.';

final Map<String, StateConfig> stateConfigs = {
  // ---- No income tax ----
  'TX': const StateConfig(
      code: 'TX', name: 'Texas', hasIncomeTax: false, note: _noTaxNote),
  'FL': const StateConfig(
      code: 'FL', name: 'Florida', hasIncomeTax: false, note: _noTaxNote),
  'TN': const StateConfig(
      code: 'TN', name: 'Tennessee', hasIncomeTax: false, note: _noTaxNote),
  'WA': const StateConfig(
    code: 'WA',
    name: 'Washington',
    hasIncomeTax: false,
    note:
        'No state income tax on wages. (Washington has a separate 7% excise on large long-term capital gains, not modeled here.)',
  ),

  // ---- Flat rate ----
  'PA': StateConfig(
    code: 'PA',
    name: 'Pennsylvania',
    hasIncomeTax: true,
    brackets: _uniform(_flat(0.0307)),
    taxesSocialSecurity: false,
    excludesRetirement: true,
    note:
        "Flat 3.07%. Retirement income and Social Security aren't taxed; PA's class-of-income rules are approximated from federal AGI.",
  ),
  'IL': StateConfig(
    code: 'IL',
    name: 'Illinois',
    hasIncomeTax: true,
    brackets: _uniform(_flat(0.0495)),
    personalExemption: 2775,
    dependentExemption: 2775,
    excludesRetirement: true,
    note: 'Flat 4.95%; retirement income and Social Security excluded.',
  ),
  'MI': StateConfig(
    code: 'MI',
    name: 'Michigan',
    hasIncomeTax: true,
    brackets: _uniform(_flat(0.0425)),
    personalExemption: 5600,
    dependentExemption: 5600,
    note:
        'Flat 4.25%. Social Security excluded; age-based retirement subtractions are not modeled.',
  ),
  'NC': StateConfig(
    code: 'NC',
    name: 'North Carolina',
    hasIncomeTax: true,
    brackets: _uniform(_flat(0.045)),
    standardDeduction: _std(12750, 25500, 19125),
    note: 'Flat 4.5%. Social Security excluded.',
  ),
  'AZ': StateConfig(
    code: 'AZ',
    name: 'Arizona',
    hasIncomeTax: true,
    brackets: _uniform(_flat(0.025)),
    standardDeduction: _std(14600, 29200, 21900),
    note:
        'Flat 2.5% (standard deduction matches federal). Social Security excluded.',
  ),
  'GA': StateConfig(
    code: 'GA',
    name: 'Georgia',
    hasIncomeTax: true,
    brackets: _uniform(_flat(0.0539)),
    standardDeduction: _std(12000, 24000, 12000),
    dependentExemption: 4000,
    note:
        "Flat 5.39% (2024). Social Security excluded; the 62+ retirement exclusion isn't modeled.",
  ),
  'OH': StateConfig(
    code: 'OH',
    name: 'Ohio',
    hasIncomeTax: true,
    brackets: _uniform(const [
      StateBracket(rate: 0, min: 0, max: 26050),
      StateBracket(rate: 0.0275, min: 26050, max: 100000),
      StateBracket(rate: 0.035, min: 100000, max: double.infinity),
    ]),
    personalExemption: 2400,
    dependentExemption: 2400,
    note:
        '2024 brackets (0% up to \$26,050, then 2.75%/3.5%). Social Security excluded.',
  ),

  // ---- Progressive ----
  'VA': StateConfig(
    code: 'VA',
    name: 'Virginia',
    hasIncomeTax: true,
    brackets: _uniform(const [
      StateBracket(rate: 0.02, min: 0, max: 3000),
      StateBracket(rate: 0.03, min: 3000, max: 5000),
      StateBracket(rate: 0.05, min: 5000, max: 17000),
      StateBracket(rate: 0.0575, min: 17000, max: double.infinity),
    ]),
    standardDeduction: _std(8500, 17000, 8500),
    personalExemption: 930,
    dependentExemption: 930,
    note: "Social Security excluded; the age deduction isn't modeled.",
  ),
  'CA': StateConfig(
    code: 'CA',
    name: 'California',
    hasIncomeTax: true,
    brackets: _byStatus(
      single: const [
        StateBracket(rate: 0.01, min: 0, max: 10412),
        StateBracket(rate: 0.02, min: 10412, max: 24684),
        StateBracket(rate: 0.04, min: 24684, max: 38959),
        StateBracket(rate: 0.06, min: 38959, max: 54081),
        StateBracket(rate: 0.08, min: 54081, max: 68350),
        StateBracket(rate: 0.093, min: 68350, max: 349137),
        StateBracket(rate: 0.103, min: 349137, max: 418961),
        StateBracket(rate: 0.113, min: 418961, max: 698271),
        StateBracket(rate: 0.123, min: 698271, max: double.infinity),
      ],
      mfj: const [
        StateBracket(rate: 0.01, min: 0, max: 20824),
        StateBracket(rate: 0.02, min: 20824, max: 49368),
        StateBracket(rate: 0.04, min: 49368, max: 77918),
        StateBracket(rate: 0.06, min: 77918, max: 108162),
        StateBracket(rate: 0.08, min: 108162, max: 136700),
        StateBracket(rate: 0.093, min: 136700, max: 698274),
        StateBracket(rate: 0.103, min: 698274, max: 837922),
        StateBracket(rate: 0.113, min: 837922, max: 1396542),
        StateBracket(rate: 0.123, min: 1396542, max: double.infinity),
      ],
      hoh: const [
        StateBracket(rate: 0.01, min: 0, max: 20839),
        StateBracket(rate: 0.02, min: 20839, max: 49371),
        StateBracket(rate: 0.04, min: 49371, max: 63644),
        StateBracket(rate: 0.06, min: 63644, max: 78765),
        StateBracket(rate: 0.08, min: 78765, max: 93037),
        StateBracket(rate: 0.093, min: 93037, max: 474824),
        StateBracket(rate: 0.103, min: 474824, max: 569790),
        StateBracket(rate: 0.113, min: 569790, max: 949649),
        StateBracket(rate: 0.123, min: 949649, max: double.infinity),
      ],
    ),
    standardDeduction: _std(5540, 11080, 11080),
    exemptionCredit: 149,
    dependentExemptionCredit: 461,
    note:
        "Social Security excluded. The 1% mental-health surcharge over \$1M isn't modeled.",
  ),
  'NY': StateConfig(
    code: 'NY',
    name: 'New York',
    hasIncomeTax: true,
    brackets: _byStatus(
      single: const [
        StateBracket(rate: 0.04, min: 0, max: 8500),
        StateBracket(rate: 0.045, min: 8500, max: 11700),
        StateBracket(rate: 0.0525, min: 11700, max: 13900),
        StateBracket(rate: 0.055, min: 13900, max: 80650),
        StateBracket(rate: 0.06, min: 80650, max: 215400),
        StateBracket(rate: 0.0685, min: 215400, max: 1077550),
        StateBracket(rate: 0.0965, min: 1077550, max: 5000000),
        StateBracket(rate: 0.103, min: 5000000, max: 25000000),
        StateBracket(rate: 0.109, min: 25000000, max: double.infinity),
      ],
      mfj: const [
        StateBracket(rate: 0.04, min: 0, max: 17150),
        StateBracket(rate: 0.045, min: 17150, max: 23600),
        StateBracket(rate: 0.0525, min: 23600, max: 27900),
        StateBracket(rate: 0.055, min: 27900, max: 161550),
        StateBracket(rate: 0.06, min: 161550, max: 323200),
        StateBracket(rate: 0.0685, min: 323200, max: 2155350),
        StateBracket(rate: 0.0965, min: 2155350, max: 5000000),
        StateBracket(rate: 0.103, min: 5000000, max: 25000000),
        StateBracket(rate: 0.109, min: 25000000, max: double.infinity),
      ],
      hoh: const [
        StateBracket(rate: 0.04, min: 0, max: 12800),
        StateBracket(rate: 0.045, min: 12800, max: 17650),
        StateBracket(rate: 0.0525, min: 17650, max: 20900),
        StateBracket(rate: 0.055, min: 20900, max: 107650),
        StateBracket(rate: 0.06, min: 107650, max: 269300),
        StateBracket(rate: 0.0685, min: 269300, max: 1616450),
        StateBracket(rate: 0.0965, min: 1616450, max: 5000000),
        StateBracket(rate: 0.103, min: 5000000, max: 25000000),
        StateBracket(rate: 0.109, min: 25000000, max: double.infinity),
      ],
    ),
    standardDeduction: _std(8000, 16050, 11200),
    dependentExemption: 1000,
    note:
        "Social Security excluded; pension exclusion and tax-benefit recapture aren't modeled.",
  ),
  'NJ': StateConfig(
    code: 'NJ',
    name: 'New Jersey',
    hasIncomeTax: true,
    brackets: _byStatus(
      single: const [
        StateBracket(rate: 0.014, min: 0, max: 20000),
        StateBracket(rate: 0.0175, min: 20000, max: 35000),
        StateBracket(rate: 0.035, min: 35000, max: 40000),
        StateBracket(rate: 0.05525, min: 40000, max: 75000),
        StateBracket(rate: 0.0637, min: 75000, max: 500000),
        StateBracket(rate: 0.0897, min: 500000, max: 1000000),
        StateBracket(rate: 0.1075, min: 1000000, max: double.infinity),
      ],
      mfj: const [
        StateBracket(rate: 0.014, min: 0, max: 20000),
        StateBracket(rate: 0.0175, min: 20000, max: 50000),
        StateBracket(rate: 0.0245, min: 50000, max: 70000),
        StateBracket(rate: 0.035, min: 70000, max: 80000),
        StateBracket(rate: 0.05525, min: 80000, max: 150000),
        StateBracket(rate: 0.0637, min: 150000, max: 500000),
        StateBracket(rate: 0.0897, min: 500000, max: 1000000),
        StateBracket(rate: 0.1075, min: 1000000, max: double.infinity),
      ],
      hoh: const [
        StateBracket(rate: 0.014, min: 0, max: 20000),
        StateBracket(rate: 0.0175, min: 20000, max: 50000),
        StateBracket(rate: 0.0245, min: 50000, max: 70000),
        StateBracket(rate: 0.035, min: 70000, max: 80000),
        StateBracket(rate: 0.05525, min: 80000, max: 150000),
        StateBracket(rate: 0.0637, min: 150000, max: 500000),
        StateBracket(rate: 0.0897, min: 500000, max: 1000000),
        StateBracket(rate: 0.1075, min: 1000000, max: double.infinity),
      ],
    ),
    personalExemption: 1000,
    dependentExemption: 1500,
    note:
        "No standard deduction. Social Security excluded; the retirement-income exclusion isn't modeled.",
  ),
};
