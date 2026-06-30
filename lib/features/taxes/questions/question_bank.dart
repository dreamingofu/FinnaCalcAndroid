/// The declarative question bank. Each question reads/writes one answer keyed by
/// its id; `dependsOn` runs over the live answers so the visible set is
/// adaptive. `buildReturn` (sibling file) maps these answer ids into a
/// TaxReturn2024.
library;

import '../engine/state/state_tax.dart';
import '../engine/types/question.dart';

final List<QuestionOption> _stateOptions = supportedStates
    .map((s) => QuestionOption(value: s.code, label: s.name))
    .toList();

bool _isMFS(Answers a) => a['q_filing'] == 'mfs';
bool _isMarried(Answers a) => a['q_filing'] == 'mfj' || a['q_filing'] == 'qss';
double _numAns(Answers a, String id) =>
    a[id] is num ? (a[id] as num).toDouble() : 0;

final List<Question> questionBank = [
  // ---- About you ----
  Question(
    id: 'q_filing',
    sectionId: 'about-you',
    text: "What's your filing status?",
    inputType: 'select',
    options: [
      QuestionOption(value: 'single', label: 'Single'),
      QuestionOption(value: 'mfj', label: 'Married filing jointly'),
      QuestionOption(value: 'mfs', label: 'Married filing separately'),
      QuestionOption(value: 'hoh', label: 'Head of household'),
      QuestionOption(value: 'qss', label: 'Qualifying surviving spouse'),
    ],
    helpText: 'Your status sets your tax brackets and standard deduction.',
  ),
  Question(
    id: 'q_lived_apart',
    sectionId: 'about-you',
    text: 'Did you live apart from your spouse for the entire tax year?',
    inputType: 'boolean',
    dependsOn: _isMFS,
    helpText:
        'Affects how your Social Security benefits and some credits are treated.',
  ),
  Question(
    id: 'q_age',
    sectionId: 'about-you',
    text: 'How old were you at the end of the tax year?',
    inputType: 'integer',
    placeholder: 'e.g. 40',
  ),
  Question(
    id: 'q_blind',
    sectionId: 'about-you',
    text: 'Are you legally blind?',
    inputType: 'boolean',
    helpText: 'Adds to your standard deduction.',
  ),
  Question(
    id: 'q_spouse_age',
    sectionId: 'about-you',
    text: 'How old was your spouse at the end of the tax year?',
    inputType: 'integer',
    dependsOn: _isMarried,
  ),
  Question(
    id: 'q_spouse_blind',
    sectionId: 'about-you',
    text: 'Is your spouse legally blind?',
    inputType: 'boolean',
    dependsOn: _isMarried,
  ),
  Question(
    id: 'q_claimed_dependent',
    sectionId: 'about-you',
    text: 'Can someone else claim you as a dependent?',
    inputType: 'boolean',
    helpText: 'If yes, your standard deduction may be limited.',
  ),
  Question(
    id: 'q_state',
    sectionId: 'about-you',
    text: 'Which state did you live in?',
    inputType: 'select',
    options: _stateOptions,
    helpText:
        'Adds a state income tax estimate (top 15 states). Leave blank to skip.',
  ),

  // ---- Dependents ----
  Question(
    id: 'q_qual_children',
    sectionId: 'dependents',
    text: 'How many qualifying children under 17 did you support?',
    inputType: 'integer',
    placeholder: '0',
    helpText: 'Each can qualify for the \$2,000 Child Tax Credit.',
  ),
  Question(
    id: 'q_other_deps',
    sectionId: 'dependents',
    text: 'How many other dependents did you support?',
    inputType: 'integer',
    placeholder: '0',
    helpText: 'Each can qualify for the \$500 Credit for Other Dependents.',
  ),

  // ---- Job income ----
  Question(
    id: 'q_wages',
    sectionId: 'income-job',
    text: 'Total wages (W-2 box 1)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_withholding',
    sectionId: 'income-job',
    text: 'Federal income tax withheld (W-2 box 2)',
    inputType: 'dollar',
  ),

  // ---- Self-employment ----
  Question(
    id: 'q_se_profit',
    sectionId: 'income-self',
    text: 'Net self-employment profit (after expenses)',
    inputType: 'dollar',
    allowNegative: true,
  ),
  Question(
    id: 'q_se_sstb',
    sectionId: 'income-self',
    text:
        'Is this a professional-service business (law, health, consulting, finance, etc.)?',
    inputType: 'boolean',
    helpText:
        'Specified service businesses lose the QBI deduction at higher incomes.',
  ),
  Question(
    id: 'q_se_health',
    sectionId: 'income-self',
    text: 'Self-employed health insurance premiums',
    inputType: 'dollar',
  ),

  // ---- Investments ----
  Question(
    id: 'q_interest',
    sectionId: 'income-investments',
    text: 'Taxable interest (1099-INT box 1)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_tax_exempt',
    sectionId: 'income-investments',
    text: 'Tax-exempt interest (1099-INT box 8)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_ord_div',
    sectionId: 'income-investments',
    text: 'Ordinary dividends (1099-DIV box 1a)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_qual_div',
    sectionId: 'income-investments',
    text: 'Qualified dividends (1099-DIV box 1b)',
    inputType: 'dollar',
    helpText: 'Taxed at lower capital-gains rates.',
  ),
  Question(
    id: 'q_ltcg',
    sectionId: 'income-investments',
    text: 'Long-term capital gain or loss',
    inputType: 'dollar',
    allowNegative: true,
  ),
  Question(
    id: 'q_stcg',
    sectionId: 'income-investments',
    text: 'Short-term capital gain or loss',
    inputType: 'dollar',
    allowNegative: true,
  ),
  Question(
    id: 'q_capgain_dist',
    sectionId: 'income-investments',
    text: 'Capital gain distributions (1099-DIV box 2a)',
    inputType: 'dollar',
  ),

  // ---- Retirement & Social Security ----
  Question(
    id: 'q_ss_benefits',
    sectionId: 'income-retirement',
    text: 'Social Security benefits (1099-SSA box 5)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_retire_taxable',
    sectionId: 'income-retirement',
    text: 'Taxable pension/IRA distributions (1099-R box 2a)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_retire_early',
    sectionId: 'income-retirement',
    text: 'Was any of that an early withdrawal (under 59½, no exception)?',
    inputType: 'boolean',
    dependsOn: (a) => _numAns(a, 'q_retire_taxable') > 0,
    helpText: 'Early withdrawals usually add a 10% penalty.',
  ),

  // ---- Other income ----
  Question(
    id: 'q_unemployment',
    sectionId: 'income-other',
    text: 'Unemployment compensation (1099-G)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_other_income',
    sectionId: 'income-other',
    text: 'Other taxable income',
    inputType: 'dollar',
  ),

  // ---- Adjustments ----
  Question(
    id: 'q_student_loan',
    sectionId: 'adjustments',
    text: 'Student loan interest paid',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_educator',
    sectionId: 'adjustments',
    text: 'Educator (K-12) classroom expenses',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_hsa_coverage',
    sectionId: 'adjustments',
    text: 'Did you have a high-deductible health plan (HSA)?',
    inputType: 'select',
    options: [
      QuestionOption(value: 'none', label: 'No HSA'),
      QuestionOption(value: 'self-only', label: 'Self-only coverage'),
      QuestionOption(value: 'family', label: 'Family coverage'),
    ],
  ),
  Question(
    id: 'q_hsa_contribution',
    sectionId: 'adjustments',
    text: 'HSA contribution',
    inputType: 'dollar',
    dependsOn: (a) =>
        a['q_hsa_coverage'] == 'self-only' || a['q_hsa_coverage'] == 'family',
  ),
  Question(
    id: 'q_ira_contribution',
    sectionId: 'adjustments',
    text: 'Traditional IRA contribution',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_ira_covered',
    sectionId: 'adjustments',
    text: 'Are you covered by a workplace retirement plan?',
    inputType: 'boolean',
    dependsOn: (a) => _numAns(a, 'q_ira_contribution') > 0,
  ),

  // ---- Deductions ----
  Question(
    id: 'q_itemize',
    sectionId: 'deductions',
    text: 'Do you want to enter itemized deductions?',
    inputType: 'boolean',
    helpText:
        "We'll automatically use whichever is larger — standard or itemized.",
  ),
  Question(
    id: 'q_mortgage_interest',
    sectionId: 'deductions',
    text: 'Home mortgage interest',
    inputType: 'dollar',
    dependsOn: (a) => a['q_itemize'] == true,
  ),
  Question(
    id: 'q_mortgage_balance',
    sectionId: 'deductions',
    text: 'Mortgage balance',
    inputType: 'dollar',
    dependsOn: (a) => a['q_itemize'] == true,
  ),
  Question(
    id: 'q_salt',
    sectionId: 'deductions',
    text: 'State & local income (or sales) tax',
    inputType: 'dollar',
    dependsOn: (a) => a['q_itemize'] == true,
  ),
  Question(
    id: 'q_property_tax',
    sectionId: 'deductions',
    text: 'Property taxes',
    inputType: 'dollar',
    dependsOn: (a) => a['q_itemize'] == true,
  ),
  Question(
    id: 'q_charitable',
    sectionId: 'deductions',
    text: 'Charitable contributions (cash)',
    inputType: 'dollar',
    dependsOn: (a) => a['q_itemize'] == true,
  ),
  Question(
    id: 'q_medical',
    sectionId: 'deductions',
    text: 'Medical & dental expenses',
    inputType: 'dollar',
    dependsOn: (a) => a['q_itemize'] == true,
  ),

  // ---- Credits ----
  Question(
    id: 'q_care_expenses',
    sectionId: 'credits',
    text: 'Child/dependent care expenses paid',
    inputType: 'dollar',
    dependsOn: (a) => a['ls_care'] == true,
  ),
  Question(
    id: 'q_care_children',
    sectionId: 'credits',
    text: 'How many children under 13 were in care?',
    inputType: 'integer',
    placeholder: '0',
    dependsOn: (a) => a['ls_care'] == true,
  ),
  Question(
    id: 'q_edu_expenses',
    sectionId: 'credits',
    text: 'Qualified tuition & fees paid',
    inputType: 'dollar',
    dependsOn: (a) => a['ls_education'] == true,
  ),
  Question(
    id: 'q_edu_aotc',
    sectionId: 'credits',
    text: 'Is the student in their first 4 years of an undergraduate degree?',
    inputType: 'boolean',
    dependsOn: (a) => a['ls_education'] == true,
    helpText:
        'If yes, the more generous American Opportunity Credit applies.',
  ),
  Question(
    id: 'q_savers_contrib',
    sectionId: 'credits',
    text: "Retirement contributions (for the Saver's Credit)",
    inputType: 'dollar',
    dependsOn: (a) => a['ls_savings'] == true,
  ),
  Question(
    id: 'q_clean_energy',
    sectionId: 'credits',
    text: 'Home clean-energy property cost (solar, etc.)',
    inputType: 'dollar',
    dependsOn: (a) => a['ls_energy'] == true,
  ),
  Question(
    id: 'q_ev_credit',
    sectionId: 'credits',
    text: 'Clean vehicle (EV) credit amount',
    inputType: 'dollar',
    dependsOn: (a) => a['ls_energy'] == true,
  ),

  // ---- Payments ----
  Question(
    id: 'q_est_payments',
    sectionId: 'payments',
    text: 'Estimated tax payments made',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_extra_withholding',
    sectionId: 'payments',
    text: 'Other federal tax withheld (not on your W-2)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_state_withholding',
    sectionId: 'payments',
    text: 'State income tax withheld (W-2 box 17)',
    inputType: 'dollar',
    dependsOn: (a) => a['q_state'] is String && a['q_state'] != '',
  ),
  Question(
    id: 'q_prior_tax',
    sectionId: 'payments',
    text: 'Your 2023 total tax (for the underpayment check)',
    inputType: 'dollar',
  ),
  Question(
    id: 'q_prior_agi',
    sectionId: 'payments',
    text: 'Your 2023 AGI',
    inputType: 'dollar',
  ),
];
