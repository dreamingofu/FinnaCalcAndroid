/// Pure structuring of a TaxCalculationResult into print/PDF-ready groups. No
/// Flutter, no I/O — the printable view and any future PDF generator both read
/// this.
library;

import 'package:finnacalc/features/taxes/engine/types/filing.dart';
import 'package:finnacalc/features/taxes/engine/types/result.dart';

class SummaryLine {
  SummaryLine({required this.label, required this.amount, this.formRef});

  String label;
  double amount;
  String? formRef;
}

class SummaryGroup {
  SummaryGroup({required this.title, required this.lines});

  String title;
  List<SummaryLine> lines;
}

class Form1040SummaryHeadline {
  Form1040SummaryHeadline({
    required this.label,
    required this.amount,
    required this.owes,
  });

  String label;
  double amount;
  bool owes;
}

class Form1040SummaryState {
  Form1040SummaryState({
    required this.name,
    required this.hasIncomeTax,
    required this.tax,
    required this.refundOrOwed,
    this.note,
  });

  String name;
  bool hasIncomeTax;
  double tax;
  double refundOrOwed;
  String? note;
}

class Form1040Summary {
  Form1040Summary({
    required this.filingStatusLabel,
    required this.headline,
    required this.groups,
    this.state,
  });

  final int taxYear = 2024;
  String filingStatusLabel;
  Form1040SummaryHeadline headline;
  List<SummaryGroup> groups;
  Form1040SummaryState? state;
}

const Map<FilingStatus, String> _filingLabels = {
  FilingStatus.single: 'Single',
  FilingStatus.mfj: 'Married filing jointly',
  FilingStatus.mfs: 'Married filing separately',
  FilingStatus.hoh: 'Head of household',
  FilingStatus.qss: 'Qualifying surviving spouse',
};

/// Title-case a camelCase credit key, e.g. "childTaxCredit" → "Child tax
/// credit".
String labelizeCredit(String key) {
  final spaced = key
      .replaceAllMapped(RegExp(r'([A-Z])'), (m) => ' ${m[1]}')
      .toLowerCase();
  return spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
}

Form1040Summary build1040Summary(TaxCalculationResult r) {
  final List<SummaryGroup> groups = [];

  groups.add(
    SummaryGroup(
      title: 'Income',
      lines: [
        SummaryLine(
          label: 'Total income',
          amount: r.totalIncome,
          formRef: '1040 line 9',
        ),
        SummaryLine(
          label: 'Adjustments to income',
          amount: r.totalAdjustments,
          formRef: 'Schedule 1',
        ),
        SummaryLine(
          label: 'Adjusted gross income (AGI)',
          amount: r.agi,
          formRef: '1040 line 11',
        ),
      ],
    ),
  );

  final List<SummaryLine> deductionLines = [
    SummaryLine(
      label: r.deductionUsed == 'itemized'
          ? 'Itemized deductions'
          : 'Standard deduction',
      amount: r.deductionAmount,
      formRef: '1040 line 12',
    ),
  ];
  if (r.qbiDeduction > 0) {
    deductionLines.add(
      SummaryLine(
        label: 'QBI deduction',
        amount: r.qbiDeduction,
        formRef: '1040 line 13',
      ),
    );
  }
  deductionLines.add(
    SummaryLine(
      label: 'Taxable income',
      amount: r.taxableIncome,
      formRef: '1040 line 15',
    ),
  );
  groups.add(SummaryGroup(title: 'Deductions', lines: deductionLines));

  final List<SummaryLine> taxLines = [
    SummaryLine(
      label: 'Income tax',
      amount: r.regularTax,
      formRef: '1040 line 16',
    ),
  ];
  if (r.amt > 0) {
    taxLines.add(
      SummaryLine(
        label: 'Alternative minimum tax',
        amount: r.amt,
        formRef: 'Schedule 2',
      ),
    );
  }
  for (final entry in r.nonrefundableCredits.entries) {
    taxLines.add(
      SummaryLine(
        label: '− ${labelizeCredit(entry.key)}',
        amount: -entry.value,
      ),
    );
  }
  if (r.seTax > 0) {
    taxLines.add(
      SummaryLine(
        label: 'Self-employment tax',
        amount: r.seTax,
        formRef: 'Schedule 2',
      ),
    );
  }
  if (r.additionalMedicareTax > 0) {
    taxLines.add(
      SummaryLine(
        label: 'Additional Medicare tax',
        amount: r.additionalMedicareTax,
      ),
    );
  }
  if (r.niit > 0) {
    taxLines.add(
      SummaryLine(label: 'Net investment income tax', amount: r.niit),
    );
  }
  taxLines.add(
    SummaryLine(
      label: 'Total tax',
      amount: r.totalTax,
      formRef: '1040 line 24',
    ),
  );
  groups.add(SummaryGroup(title: 'Tax & credits', lines: taxLines));

  final List<SummaryLine> payLines = [];
  for (final entry in r.refundableCredits.entries) {
    payLines.add(
      SummaryLine(label: labelizeCredit(entry.key), amount: entry.value),
    );
  }
  payLines.add(
    SummaryLine(
      label: 'Total payments & refundable credits',
      amount: r.totalPayments,
      formRef: '1040 line 33',
    ),
  );
  groups.add(SummaryGroup(title: 'Payments', lines: payLines));

  return Form1040Summary(
    filingStatusLabel: _filingLabels[r.filingStatus]!,
    headline: Form1040SummaryHeadline(
      label: r.owes ? 'Estimated balance due' : 'Estimated federal refund',
      amount: r.refundOrOwed.abs(),
      owes: r.owes,
    ),
    groups: groups,
    state: r.state != null && r.state!.supported
        ? Form1040SummaryState(
            name: r.state!.name,
            hasIncomeTax: r.state!.hasIncomeTax,
            tax: r.state!.tax,
            refundOrOwed: r.state!.refundOrOwed,
            note: r.state!.note,
          )
        : null,
  );
}
