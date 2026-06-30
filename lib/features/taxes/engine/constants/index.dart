/// 2024 tax constants — single source of truth.
///
/// MASTER SOURCES (Tax Year 2024, returns filed in 2025):
///  - Rev. Proc. 2023-34 — annual inflation adjustments (brackets, standard
///    deduction, cap-gain breakpoints, student loan / educator phaseouts, etc.)
///  - 2024 Form 1040 and Instructions; Tax Rate Schedules; Tax Table.
///  - Schedule 8812 (2024) — Child Tax Credit / ACTC.
///  - Schedule SE / Form 8959 / Form 8960 (2024) — SE tax, Add'l Medicare, NIIT.
///  - Social Security Administration — 2024 wage base ($168,600).
///
/// RULE: no calculation module may contain a numeric tax literal. Every IRS value
/// lives here, annotated with its source, so accuracy is auditable in one place.
library;

export 'brackets_2024.dart';
export 'standard_deductions_2024.dart';
export 'ctc_2024.dart';
export 'filing_thresholds_2024.dart';
export 'social_security_2024.dart';
export 'retirement_2024.dart';
export 'eitc_2024.dart';
export 'qbi_2024.dart';
export 'amt_2024.dart';
export 'credits_2024.dart';
