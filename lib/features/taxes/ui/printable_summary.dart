import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../export/build_1040_summary.dart';
import '../engine/types/result.dart';

/// Clean 1040 summary shown on the filing screen. Renders
/// [build1040Summary] groups/lines plus an educational disclaimer.
/// Mirrors `ui/PrintableSummary.tsx`.
class PrintableSummary extends StatelessWidget {
  const PrintableSummary({super.key, required this.result});

  final TaxCalculationResult result;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final summary = build1040Summary(result);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: c.card,
        borderRadius: FCRadii.lgAll,
        border: Border.all(color: c.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          // Header
          Container(
            padding: const EdgeInsets.only(bottom: 16),
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(color: c.border)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'FinnaCalc · Tax Year ${summary.taxYear} estimate',
                  style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    color: c.mutedForeground,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  summary.filingStatusLabel,
                  style: TextStyle(
                    fontSize: FCFontSizes.lg,
                    fontWeight: FCFontWeights.bold,
                    color: c.foreground,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  summary.headline.label,
                  style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    color: c.mutedForeground,
                  ),
                ),
                Text(
                  Fmt.money0(summary.headline.amount),
                  style: TextStyle(
                    fontSize: FCFontSizes.xl3,
                    fontWeight: FCFontWeights.bold,
                    color: c.foreground,
                    height: 1.1,
                  ),
                ),
              ],
            ),
          ),

          // Groups
          for (var gi = 0; gi < summary.groups.length; gi++) ...[
            if (gi > 0) const SizedBox(height: 20),
            _group(c, summary.groups[gi]),
          ],

          // State
          if (summary.state != null) ...[
            const SizedBox(height: 20),
            _stateBlock(c, summary.state!),
          ],

          // Disclaimer
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.only(top: 16),
            decoration: BoxDecoration(
              border: Border(top: BorderSide(color: c.border)),
            ),
            child: Text(
              'Educational federal estimate.',
              style: TextStyle(
                fontSize: FCFontSizes.xs,
                color: c.mutedForeground,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _group(FCColors c, SummaryGroup g) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          g.title,
          style: TextStyle(
            fontSize: FCFontSizes.sm,
            fontWeight: FCFontWeights.semibold,
            color: c.foreground,
          ),
        ),
        const SizedBox(height: 6),
        for (final l in g.lines) ...[
          _summaryLine(c, l.label, Fmt.money2(l.amount), formRef: l.formRef),
          const SizedBox(height: 4),
        ],
      ],
    );
  }

  Widget _stateBlock(FCColors c, Form1040SummaryState state) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          '${state.name} state',
          style: TextStyle(
            fontSize: FCFontSizes.sm,
            fontWeight: FCFontWeights.semibold,
            color: c.foreground,
          ),
        ),
        const SizedBox(height: 6),
        if (state.hasIncomeTax) ...[
          _summaryLine(c, 'State income tax', Fmt.money2(state.tax)),
          const SizedBox(height: 4),
          _summaryLine(
            c,
            'State ${state.refundOrOwed < 0 ? 'balance due' : 'refund'}',
            Fmt.money2(state.refundOrOwed.abs()),
          ),
        ] else
          Text(
            'No state income tax.',
            style: TextStyle(
              fontSize: FCFontSizes.sm,
              color: c.mutedForeground,
            ),
          ),
      ],
    );
  }

  Widget _summaryLine(FCColors c, String label, String value, {String? formRef}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Text.rich(
            TextSpan(
              text: label,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.mutedForeground,
              ),
              children: [
                if (formRef != null)
                  TextSpan(
                    text: '  $formRef',
                    style: TextStyle(
                      fontSize: FCFontSizes.xs,
                      color: c.mutedForeground.withValues(alpha: 0.7),
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(width: 12),
        Text(
          value,
          style: TextStyle(
            fontSize: FCFontSizes.sm,
            fontWeight: FCFontWeights.medium,
            color: c.foreground,
          ),
        ),
      ],
    );
  }
}
