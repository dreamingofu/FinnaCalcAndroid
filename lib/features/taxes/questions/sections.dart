/// Interview sections. Each section appears only when its `dependsOn` gate
/// (driven by the Life Situations screen and prior answers) is satisfied. The
/// "life" and "review" screens are handled specially by the interview shell.
library;

import '../engine/types/question.dart';

bool _truthy(Object? v) => v == true;

final List<Section> sections = [
  Section(
    id: 'about-you',
    title: 'About you',
    icon: 'User',
    description: 'Filing status and a few basics.',
  ),
  Section(
    id: 'dependents',
    title: 'Family & dependents',
    icon: 'Users',
    description: 'Children and others you support.',
    dependsOn: (Answers a) => _truthy(a['ls_deps']),
  ),
  Section(
    id: 'income-job',
    title: 'Job income',
    icon: 'Briefcase',
    description: 'Wages from your W-2.',
    dependsOn: (Answers a) => _truthy(a['ls_job']),
  ),
  Section(
    id: 'income-self',
    title: 'Self-employment',
    icon: 'Store',
    description: 'Freelance or business income.',
    dependsOn: (Answers a) => _truthy(a['ls_self']),
  ),
  Section(
    id: 'income-investments',
    title: 'Investments',
    icon: 'TrendingUp',
    description: 'Interest, dividends, and sales.',
    dependsOn: (Answers a) => _truthy(a['ls_invest']),
  ),
  Section(
    id: 'income-retirement',
    title: 'Retirement & Social Security',
    icon: 'PiggyBank',
    description: 'Pensions, IRAs, and benefits.',
    dependsOn: (Answers a) => _truthy(a['ls_retire']),
  ),
  Section(
    id: 'income-other',
    title: 'Other income',
    icon: 'Coins',
    description: 'Anything else taxable.',
  ),
  Section(
    id: 'adjustments',
    title: 'Adjustments',
    icon: 'Sliders',
    description: 'Above-the-line deductions.',
  ),
  Section(
    id: 'deductions',
    title: 'Deductions',
    icon: 'Receipt',
    description: 'Standard vs. itemized.',
  ),
  Section(
    id: 'credits',
    title: 'Credits',
    icon: 'Gift',
    description: 'Care, education, savings, and energy.',
    dependsOn: (Answers a) =>
        _truthy(a['ls_care']) ||
        _truthy(a['ls_education']) ||
        _truthy(a['ls_energy']) ||
        _truthy(a['ls_savings']),
  ),
  Section(
    id: 'payments',
    title: 'Payments',
    icon: 'Wallet',
    description: 'Withholding and estimates.',
  ),
];

/// One entry in the Life Situations screen.
class LifeSituation {
  LifeSituation({required this.id, required this.label, required this.icon});

  String id;
  String label;
  String icon;
}

/// Life Situations options — checking these unlocks the relevant sections.
final List<LifeSituation> lifeSituations = [
  LifeSituation(
    id: 'ls_job',
    label: 'I earned wages from a job (W-2)',
    icon: 'Briefcase',
  ),
  LifeSituation(
    id: 'ls_self',
    label: 'I was self-employed or freelanced',
    icon: 'Store',
  ),
  LifeSituation(
    id: 'ls_invest',
    label: 'I had investments (interest, dividends, or sales)',
    icon: 'TrendingUp',
  ),
  LifeSituation(
    id: 'ls_retire',
    label: 'I received retirement income or Social Security',
    icon: 'PiggyBank',
  ),
  LifeSituation(
    id: 'ls_deps',
    label: 'I have children or other dependents',
    icon: 'Users',
  ),
  LifeSituation(
    id: 'ls_itemize',
    label: 'I owned a home or have large deductions',
    icon: 'Home',
  ),
  LifeSituation(
    id: 'ls_education',
    label: 'I paid for higher education',
    icon: 'GraduationCap',
  ),
  LifeSituation(
    id: 'ls_care',
    label: 'I paid for child or dependent care',
    icon: 'Baby',
  ),
  LifeSituation(
    id: 'ls_savings',
    label: 'I contributed to an IRA, HSA, or retirement plan',
    icon: 'Landmark',
  ),
  LifeSituation(
    id: 'ls_energy',
    label: 'I bought an EV or made home energy upgrades',
    icon: 'Zap',
  ),
];
