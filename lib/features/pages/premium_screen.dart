import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import 'page_widgets.dart';

/// Static "FinnaCalc Premium" page, ported from the web `app/premium/page.tsx`.
///
/// The web's email waitlist signup hits `/api/send-email`; here the CTA is a
/// disabled "coming soon" button and the signup is a simple note — no network
/// calls.
class PremiumScreen extends StatelessWidget {
  const PremiumScreen({super.key});

  static const _premiumFeatures = [
    'Personal financial advising and AI-powered planning',
    'Advanced retirement and tax optimization tools',
    'Save and sync your calculations across devices',
    'Export reports to PDF and spreadsheets',
    'Ad-free experience with priority support',
  ];

  static const _freeFeatures = [
    'All core financial calculators',
    'Personal finance and budgeting tools',
    'Business planning calculators',
  ];

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return PageScaffold(
      title: 'Premium',
      children: [
        const SizedBox(height: 8),
        const Icon(Icons.workspace_premium, size: 56, color: FCPalette.yellow600),
        const SizedBox(height: 16),
        Text(
          'FinnaCalc Premium',
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
          'Coming Soon',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.lg,
            color: c.mutedForeground,
          ),
        ),
        const SizedBox(height: 24),

        // Premium features card.
        FCCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              FCCardHeader(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.star, size: 18, color: FCPalette.yellow600),
                      const SizedBox(width: 8),
                      Text(
                        'Premium Features',
                        style: TextStyle(
                          fontSize: FCFontSizes.xl2,
                          fontWeight: FCFontWeights.semibold,
                          color: c.cardForeground,
                          letterSpacing: -0.6,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(Icons.star, size: 18, color: FCPalette.yellow600),
                    ],
                  ),
                  const FCCardDescription(
                    'Advanced tools and features for serious financial planning',
                  ),
                ],
              ),
              FCCardContent(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final f in _premiumFeatures) ...[
                      _CheckRow(f),
                      const SizedBox(height: 12),
                    ],
                    const SizedBox(height: 4),
                    Divider(color: c.border, height: 1),
                    const SizedBox(height: 16),
                    Text(
                      'Join our exclusive early access list and be the first to '
                      'experience FinnaCalc Premium!',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        color: c.mutedForeground,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 12),
                    const FCButton(
                      label: 'Join Waitlist',
                      fullWidth: true,
                      onPressed: null,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'No spam. Unsubscribe anytime. Early access members get '
                      '60% off forever.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: FCFontSizes.xs,
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

        // Tiers.
        _TierCard(
          name: 'Free',
          price: r'$0',
          period: 'forever',
          features: _freeFeatures,
          badge: 'Current plan',
        ),
        const SizedBox(height: 16),
        _TierCard(
          name: 'Premium',
          price: 'Coming soon',
          period: '',
          features: _premiumFeatures,
          highlighted: true,
        ),
        const SizedBox(height: 24),
        Text(
          'Continue using our free calculators while you wait.',
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

class _CheckRow extends StatelessWidget {
  const _CheckRow(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(top: 2),
          child: Icon(Icons.check, size: 18, color: FCPalette.green600),
        ),
        const SizedBox(width: 10),
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

class _TierCard extends StatelessWidget {
  const _TierCard({
    required this.name,
    required this.price,
    required this.period,
    required this.features,
    this.badge,
    this.highlighted = false,
  });

  final String name;
  final String price;
  final String period;
  final List<String> features;
  final String? badge;
  final bool highlighted;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(
                  name,
                  style: TextStyle(
                    fontSize: FCFontSizes.xl,
                    fontWeight: FCFontWeights.semibold,
                    color: c.foreground,
                  ),
                ),
                const SizedBox(width: 8),
                if (badge != null)
                  FCBadge(badge!, variant: FCBadgeVariant.secondary),
                if (highlighted) ...[
                  const FCBadge('Best value'),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(
                  price,
                  style: TextStyle(
                    fontSize: FCFontSizes.xl2,
                    fontWeight: FCFontWeights.bold,
                    color: c.foreground,
                  ),
                ),
                if (period.isNotEmpty) ...[
                  const SizedBox(width: 6),
                  Text(
                    period,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      color: c.mutedForeground,
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 16),
            for (final f in features) ...[
              _CheckRow(f),
              const SizedBox(height: 12),
            ],
            if (highlighted) ...[
              const SizedBox(height: 4),
              const FCButton(
                label: 'Coming soon',
                fullWidth: true,
                onPressed: null,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
