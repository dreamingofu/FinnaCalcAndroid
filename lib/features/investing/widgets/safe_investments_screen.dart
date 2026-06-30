import 'package:flutter/material.dart';
import 'package:flutter_custom_tabs/flutter_custom_tabs.dart';

import '../../../core/design_system/design_system.dart';
import '../../../shared/widgets/page_scaffold.dart';

class _SafeInvestment {
  const _SafeInvestment(this.name, this.symbol, this.avgReturn, this.risk,
      this.description, this.minInvestment, this.link);
  final String name;
  final String symbol;
  final String avgReturn;
  final String risk;
  final String description;
  final String minInvestment;
  final String link;
}

/// Curated safe investment options (the source's intended `safeInvestments`
/// list, surfaced here — the web routed page was only a stub).
const _safeInvestments = [
  _SafeInvestment(
    'S&P 500 Index Fund (IVV)', 'IVV', '10.5%', 'Low-Medium',
    'Tracks the 500 largest US companies.', '\$1',
    'https://www.ishares.com/us/products/239726/ishares-core-sp-500-etf',
  ),
  _SafeInvestment(
    'Total Stock Market (VTI)', 'VTI', '10.2%', 'Low-Medium',
    'Owns the entire US stock market.', '\$1',
    'https://investor.vanguard.com/investment-products/etfs/profile/vti',
  ),
  _SafeInvestment(
    'High-Yield Savings', 'HYSA', '4.5%+', 'None',
    'FDIC insured savings account.', '\$0',
    'https://www.nerdwallet.com/best/banking/high-yield-online-savings-accounts',
  ),
];

class SafeInvestmentsScreen extends StatelessWidget {
  const SafeInvestmentsScreen({super.key});

  Color _riskColor(String risk) {
    switch (risk) {
      case 'None':
      case 'Very Low':
        return FCPalette.green600;
      case 'Low':
        return FCPalette.blue600;
      case 'Low-Medium':
        return FCPalette.yellow600;
      case 'Medium':
        return FCPalette.orange600;
      default:
        return FCPalette.gray500;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      backgroundColor:
          Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.background),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        foregroundColor: c.foreground,
      ),
      body: FCPageBody(
        title: 'Safe Investments',
        description:
            'Lower-risk options to grow your money steadily.',
        children: [
          for (final s in _safeInvestments) ...[
            FCCard(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.trending_up, color: FCPalette.green600),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(s.name,
                              style: TextStyle(
                                  fontSize: FCFontSizes.base,
                                  fontWeight: FCFontWeights.semibold,
                                  color: c.cardForeground)),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 2),
                          decoration: BoxDecoration(
                            color: _riskColor(s.risk).withValues(alpha: 0.12),
                            borderRadius: FCRadii.fullAll,
                          ),
                          child: Text(s.risk,
                              style: TextStyle(
                                  fontSize: FCFontSizes.xs,
                                  fontWeight: FCFontWeights.semibold,
                                  color: _riskColor(s.risk))),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(s.description,
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground)),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Text('Min: ${s.minInvestment}',
                            style: TextStyle(
                                fontSize: FCFontSizes.sm,
                                color: c.mutedForeground)),
                        const Spacer(),
                        Text('${s.avgReturn} avg',
                            style: TextStyle(
                                fontSize: FCFontSizes.sm,
                                fontWeight: FCFontWeights.semibold,
                                color: FCPalette.green600)),
                      ],
                    ),
                    const SizedBox(height: 12),
                    FCButton(
                      label: 'Invest Now',
                      variant: FCButtonVariant.outline,
                      fullWidth: true,
                      trailingIcon: const Icon(Icons.open_in_new, size: 14),
                      onPressed: () => _open(s.link),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
          ],
        ],
      ),
    );
  }

  Future<void> _open(String url) async {
    try {
      await launchUrl(Uri.parse(url));
    } catch (_) {/* ignore */}
  }
}

/// Simple bonds education page (the web routed page was a stub).
class BondsScreen extends StatelessWidget {
  const BondsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      backgroundColor:
          Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.background),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        foregroundColor: c.foreground,
      ),
      body: const FCPageBody(
        title: 'Bonds',
        description: 'Fixed-income basics.',
        children: [
          FCCard(
            child: FCCardContent(
              padding: EdgeInsets.all(20),
              child: Text(
                'Bonds are loans you make to a government or company in exchange '
                'for regular interest and the return of principal at maturity. '
                'They are generally lower risk than stocks and can add stability '
                'to a portfolio. Treasuries, municipal bonds, and investment-grade '
                'corporate bonds are common starting points.',
                style: TextStyle(height: 1.6),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
