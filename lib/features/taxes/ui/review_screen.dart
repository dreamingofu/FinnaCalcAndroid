import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../engine/types/question.dart';
import '../engine/types/result.dart';

/// Heuristic "you might also qualify for…" nudges based on the answers + result.
/// Ported 1:1 from `computeSuggestions` in `ui/SmartSuggestions.tsx`.
List<String> computeSuggestions(Answers a, TaxCalculationResult result) {
  final out = <String>[];
  final qualChildren = a['q_qual_children'];
  final hasKids = (qualChildren is num ? qualChildren : 0) > 0;

  if (hasKids && a['ls_care'] != true) {
    out.add(
      'You have children — if you paid for daycare or after-school care, check '
      '"I paid for child or dependent care" to claim the Child & Dependent Care '
      'Credit.',
    );
  }
  if (hasKids && a['ls_education'] != true) {
    out.add(
      'Paying for college? Check "I paid for higher education" — the American '
      'Opportunity Credit is worth up to \$2,500 per student.',
    );
  }
  if (a['ls_self'] == true && a['ls_savings'] != true) {
    out.add(
      'As a self-employed filer, contributing to a SEP-IRA or solo 401(k) can '
      'lower your taxable income — check "I contributed to an IRA, HSA, or '
      'retirement plan."',
    );
  }
  if (a['ls_savings'] != true && result.agi > 0 && result.agi < 40000) {
    out.add(
      "At your income, retirement contributions may earn the Saver's Credit "
      '(up to 50% back). Check "I contributed to an IRA, HSA, or retirement '
      'plan."',
    );
  }
  if (result.deductionUsed == 'standard' && a['ls_itemize'] != true) {
    out.add(
      'We used the standard deduction. If you own a home or made large '
      'charitable gifts, check "I owned a home or have large deductions" to '
      'compare itemizing.',
    );
  }
  return out;
}

/// Review step: the running estimate, smart suggestions, deduction comparison,
/// the line-by-line 1040 trace, audit-risk panel, and a continue-to-file button.
/// Mirrors `ui/ReviewScreen.tsx`.
class ReviewScreen extends StatelessWidget {
  const ReviewScreen({
    super.key,
    required this.result,
    required this.answers,
    required this.sections,
    required this.onEdit,
    required this.onFile,
  });

  final TaxCalculationResult result;
  final Answers answers;
  final List<Section> sections;
  final ValueChanged<String> onEdit;
  final VoidCallback onFile;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final suggestions = computeSuggestions(answers, result);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        // Headline
        Column(
          children: [
            Text(
              'Review your estimate',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: FCFontSizes.xl2,
                fontWeight: FCFontWeights.bold,
                color: c.foreground,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              result.owes
                  ? 'You owe an estimated ${Fmt.money0(result.refundOrOwed.abs())}.'
                  : "You're getting an estimated "
                      '${Fmt.money0(result.refundOrOwed)} refund.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: FCFontSizes.base,
                color: c.mutedForeground,
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),

        // Smart suggestions
        if (suggestions.isNotEmpty) ...[
          _SmartSuggestions(suggestions: suggestions),
          const SizedBox(height: 24),
        ],

        // Deduction check
        FCCard(
          child: FCCardHeader(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
            children: [
              const FCCardTitle('Deduction check'),
              FCCardDescription(
                'Standard ${Fmt.money0(result.standardDeduction)} vs. itemized '
                '${Fmt.money0(result.itemizedDeduction)} — '
                '${result.deductionUsed == 'itemized' ? 'we itemized and saved you about ${Fmt.money0(result.itemizedSavings)}.' : 'the standard deduction is better for you.'}',
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Section edit list
        FCCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              const FCCardHeader(
                padding: EdgeInsets.fromLTRB(24, 24, 24, 16),
                children: [
                  FCCardTitle('Your answers'),
                  FCCardDescription(
                      'Jump back to any section to make changes.'),
                ],
              ),
              FCCardContent(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final s in sections)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                s.title,
                                style: TextStyle(
                                  fontSize: FCFontSizes.sm,
                                  color: c.foreground,
                                ),
                              ),
                            ),
                            FCButton(
                              label: 'Edit',
                              variant: FCButtonVariant.ghost,
                              size: FCButtonSize.sm,
                              icon: const Icon(Icons.edit_outlined),
                              onPressed: () => onEdit(s.id),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),

        // Line-by-line 1040
        FCCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              const FCCardHeader(
                padding: EdgeInsets.fromLTRB(24, 24, 24, 16),
                children: [FCCardTitle('Your 1040, line by line')],
              ),
              FCCardContent(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final t in result.trace)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 3),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(
                              child: Text.rich(
                                TextSpan(
                                  text: t.label,
                                  style: TextStyle(
                                    fontSize: FCFontSizes.sm,
                                    color: c.mutedForeground,
                                  ),
                                  children: [
                                    TextSpan(
                                      text: '  ${t.formRef}',
                                      style: TextStyle(
                                        fontSize: FCFontSizes.xs,
                                        color: c.mutedForeground
                                            .withValues(alpha: 0.7),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              Fmt.money2(t.amount),
                              style: TextStyle(
                                fontSize: FCFontSizes.sm,
                                fontWeight: FCFontWeights.medium,
                                color: c.foreground,
                              ),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),

        // Audit risk panel
        if (result.auditFlags.isNotEmpty || result.warnings.isNotEmpty) ...[
          const SizedBox(height: 24),
          _AuditRiskPanel(result: result),
        ],

        const SizedBox(height: 24),
        Container(height: 1, color: c.border),
        const SizedBox(height: 24),
        Align(
          alignment: Alignment.centerRight,
          child: FCButton(
            label: 'Continue to filing',
            trailingIcon: const Icon(Icons.arrow_forward),
            onPressed: onFile,
          ),
        ),
      ],
    );
  }
}

/// "You might be leaving money on the table" nudges. Mirrors the `SmartSuggestions`
/// component in `ui/SmartSuggestions.tsx`.
class _SmartSuggestions extends StatelessWidget {
  const _SmartSuggestions({required this.suggestions});

  final List<String> suggestions;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      decoration: BoxDecoration(
        color: c.card,
        borderRadius: FCRadii.lgAll,
        border: Border.all(color: c.primary.withValues(alpha: 0.3)),
        boxShadow: kShadowSm,
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
            child: Row(
              children: [
                Icon(Icons.lightbulb_outline, size: 20, color: c.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'You might be leaving money on the table',
                    style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.semibold,
                      color: c.cardForeground,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                for (var i = 0; i < suggestions.length; i++) ...[
                  if (i > 0) const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('•',
                          style: TextStyle(
                              fontSize: FCFontSizes.sm, color: c.primary)),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          suggestions[i],
                          style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground,
                            height: 1.4,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Surfaces the engine's audit flags and not-fully-modeled warnings as coloured
/// callouts by severity (info/warn/high). Mirrors `ui/AuditRiskPanel.tsx`.
class _AuditRiskPanel extends StatelessWidget {
  const _AuditRiskPanel({required this.result});

  final TaxCalculationResult result;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final blocks = <Widget>[];

    for (final flag in result.auditFlags) {
      final isHigh = flag.severity == 'high';
      final (bg, fg, title, icon) = isHigh
          ? (
              FCPalette.red50,
              FCPalette.red700,
              'Check this',
              Icons.gpp_maybe_outlined,
            )
          : flag.severity == 'warn'
              ? (
                  FCPalette.yellow50,
                  FCPalette.yellow800,
                  'Check this',
                  Icons.info_outline,
                )
              : (
                  FCPalette.blue50,
                  FCPalette.blue700,
                  'Heads up',
                  Icons.info_outline,
                );
      blocks.add(_alert(c, bg, fg, icon, title: title, message: flag.message));
    }

    for (final w in result.warnings) {
      blocks.add(_alert(
        c,
        FCPalette.blue50,
        FCPalette.blue700,
        Icons.info_outline,
        message: w.message,
      ));
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 0; i < blocks.length; i++) ...[
          if (i > 0) const SizedBox(height: 12),
          blocks[i],
        ],
      ],
    );
  }

  Widget _alert(
    FCColors c,
    Color bg,
    Color fg,
    IconData icon, {
    String? title,
    required String message,
  }) {
    return FCCalloutBanner(
      background: bg,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: fg),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (title != null) ...[
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.semibold,
                      color: fg,
                    ),
                  ),
                  const SizedBox(height: 2),
                ],
                Text(
                  message,
                  style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    color: fg,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
