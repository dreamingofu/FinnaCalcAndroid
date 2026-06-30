import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import '../../shared/widgets/fc_segmented_tabs.dart';
import 'widgets/brokerage_connect.dart';
import 'widgets/markets_dashboard.dart';
import 'widgets/overview_tab.dart';
import 'widgets/safe_investments_screen.dart';
import 'widgets/screener_table.dart';
import 'widgets/stocks_page.dart';

class InvestingScreen extends StatefulWidget {
  const InvestingScreen({super.key});

  @override
  State<InvestingScreen> createState() => _InvestingScreenState();
}

class _InvestingScreenState extends State<InvestingScreen> {
  int _tab = 0;

  void _openStock(String symbol) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _StockDetailPage(symbol: symbol),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Investing',
                  style: TextStyle(
                      fontSize: FCFontSizes.xl3,
                      fontWeight: FCFontWeights.bold,
                      color: c.foreground,
                      letterSpacing: -0.5)),
              const SizedBox(height: 12),
              FCSegmentedTabs(
                tabs: const ['Overview', 'Portfolio', 'Screener'],
                index: _tab,
                onChanged: (i) => setState(() => _tab = i),
              ),
            ],
          ),
        ),
        Expanded(
          child: IndexedStack(
            index: _tab,
            children: [
              _scroll([
                OverviewTab(onSelectSymbol: _openStock),
                const SizedBox(height: 24),
                const _ExploreSection(),
              ]),
              _scroll(const [
                BrokerageConnect(),
                SizedBox(height: 16),
                MarketsDashboard(),
              ]),
              _scroll([ScreenerTable(onSelectSymbol: _openStock)]),
            ],
          ),
        ),
      ],
    );
  }

  Widget _scroll(List<Widget> children) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 40),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: children,
        ),
      );
}

class _StockDetailPage extends StatelessWidget {
  const _StockDetailPage({required this.symbol});

  final String symbol;

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
        title: Text(symbol),
      ),
      body: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
          child: StocksPage(initialSymbol: symbol),
        ),
      ),
    );
  }
}

class _ExploreSection extends StatelessWidget {
  const _ExploreSection();

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Explore',
            style: TextStyle(
                fontSize: FCFontSizes.lg,
                fontWeight: FCFontWeights.semibold,
                color: c.foreground)),
        const SizedBox(height: 12),
        _ExploreCard(
          icon: Icons.shield_outlined,
          title: 'Safe Investments',
          subtitle: 'Lower-risk options to grow steadily.',
          onTap: () => Navigator.of(context).push(MaterialPageRoute(
              builder: (_) => const SafeInvestmentsScreen())),
        ),
        const SizedBox(height: 12),
        _ExploreCard(
          icon: Icons.account_balance_outlined,
          title: 'Bonds',
          subtitle: 'Fixed-income basics.',
          onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const BondsScreen())),
        ),
      ],
    );
  }
}

class _ExploreCard extends StatelessWidget {
  const _ExploreCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: FCCard(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(icon, color: FCPalette.blue600),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title,
                        style: TextStyle(
                            fontSize: FCFontSizes.base,
                            fontWeight: FCFontWeights.semibold,
                            color: c.cardForeground)),
                    const SizedBox(height: 2),
                    Text(subtitle,
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground)),
                  ],
                ),
              ),
              Icon(Icons.chevron_right, color: c.mutedForeground, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}
