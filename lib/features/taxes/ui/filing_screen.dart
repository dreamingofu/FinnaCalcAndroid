import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../engine/types/result.dart';
import 'printable_summary.dart';

/// Filing step. E-file is a clearly-labeled stub gated behind an
/// acknowledgement; the printable estimate is shown below. Mirrors
/// `ui/FilingScreen.tsx`.
class FilingScreen extends StatefulWidget {
  const FilingScreen({super.key, required this.result, required this.onBack});

  final TaxCalculationResult result;
  final VoidCallback onBack;

  @override
  State<FilingScreen> createState() => _FilingScreenState();
}

class _FilingScreenState extends State<FilingScreen> {
  bool _acknowledged = false;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final r = widget.result;
    final headline = r.owes
        ? 'Estimated balance due: ${Fmt.money0(r.refundOrOwed.abs())}'
        : 'Estimated refund: ${Fmt.money0(r.refundOrOwed)}';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: FCButton(
            label: 'Back to review',
            variant: FCButtonVariant.outline,
            size: FCButtonSize.sm,
            icon: const Icon(Icons.arrow_back),
            onPressed: widget.onBack,
          ),
        ),
        const SizedBox(height: 24),

        FCCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              FCCardHeader(children: [
                FCCardTitle(headline),
                const FCCardDescription(
                  'Save a copy or print your estimate for your records.',
                ),
              ]),
              FCCardContent(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // E-file coming-soon callout
                    FCCalloutBanner(
                      background: Color.alphaBlend(
                          c.muted.withValues(alpha: 0.4), c.card),
                      border: c.border,
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(Icons.info_outline,
                              size: 18, color: c.foreground),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(
                                  'E-file is coming soon',
                                  style: TextStyle(
                                    fontSize: FCFontSizes.sm,
                                    fontWeight: FCFontWeights.semibold,
                                    color: c.foreground,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  'Electronic filing of your federal return goes '
                                  'through the IRS Modernized e-File system and '
                                  'requires an authorized provider. This is wired '
                                  'as a stub for now — your data stays on your '
                                  'device and is never transmitted.',
                                  style: TextStyle(
                                    fontSize: FCFontSizes.sm,
                                    color: c.mutedForeground,
                                    height: 1.4,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),

                    // Acknowledgement
                    GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () =>
                          setState(() => _acknowledged = !_acknowledged),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          SizedBox(
                            width: 24,
                            height: 24,
                            child: Checkbox(
                              value: _acknowledged,
                              onChanged: (v) =>
                                  setState(() => _acknowledged = v ?? false),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                'I understand this is an estimate and want to '
                                'continue.',
                                style: TextStyle(
                                  fontSize: FCFontSizes.sm,
                                  color: c.mutedForeground,
                                  height: 1.4,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),

                    Align(
                      alignment: Alignment.centerLeft,
                      child: FCButton(
                        label: 'E-file (coming soon)',
                        icon: const Icon(Icons.send_outlined),
                        onPressed: _acknowledged ? () {} : null,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),

        PrintableSummary(result: r),
      ],
    );
  }
}
