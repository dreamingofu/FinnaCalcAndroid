import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import 'page_widgets.dart';

/// Static "Personal Financial Advising" page, ported from the web
/// `app/advising/page.tsx`. The web's email signup is rendered as a simple note
/// (no network calls).
class AdvisingScreen extends StatelessWidget {
  const AdvisingScreen({super.key});

  static const _features = [
    'AI-Powered Financial Planning',
    'Personalized Budget Recommendations',
    'Debt Consolidation Strategies',
    'Retirement Planning Guidance',
    'Tax Optimization Strategies',
    'Business Growth Financial Planning',
  ];

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return PageScaffold(
      title: 'Advising',
      children: [
        const SizedBox(height: 8),
        Icon(Icons.people_outline, size: 56, color: FCPalette.blue600),
        const SizedBox(height: 16),
        Text(
          'Personal Financial Advising',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.xl3,
            fontWeight: FCFontWeights.bold,
            color: c.foreground,
            letterSpacing: -0.5,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Coming Soon to Premium Version',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.lg,
            color: c.mutedForeground,
          ),
        ),
        const SizedBox(height: 24),
        FCCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const FCCardHeader(
                children: [
                  FCCardTitle("What's Coming to Premium"),
                  FCCardDescription(
                    'Personalized financial guidance and advisory services',
                  ),
                ],
              ),
              FCCardContent(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final f in _features) ...[
                      _FeatureRow(f),
                      const SizedBox(height: 12),
                    ],
                    const SizedBox(height: 4),
                    Divider(color: c.border, height: 1),
                    const SizedBox(height: 16),
                    Text(
                      'Get notified when our premium advisory services become '
                      'available!',
                      style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        color: c.mutedForeground,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 12),
                    const FCButton(
                      label: 'Notify Me',
                      fullWidth: true,
                      onPressed: null,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Email notifications are coming soon in the app.',
                      style: TextStyle(
                        fontSize: FCFontSizes.xs,
                        color: c.mutedForeground,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text(
          'Start with our free financial calculators today.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.base,
            color: c.mutedForeground,
            height: 1.5,
          ),
        ),
      ],
    );
  }
}

class _FeatureRow extends StatelessWidget {
  const _FeatureRow(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          margin: const EdgeInsets.only(top: 7),
          width: 8,
          height: 8,
          decoration: const BoxDecoration(
            color: FCPalette.blue600,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            label,
            style: TextStyle(
              fontSize: FCFontSizes.base,
              color: c.foreground,
              height: 1.4,
            ),
          ),
        ),
      ],
    );
  }
}
