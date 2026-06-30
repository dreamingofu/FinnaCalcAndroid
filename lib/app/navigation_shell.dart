import 'package:flutter/material.dart';

import '../core/design_system/design_system.dart';
import '../features/budgeting/budgeting_screen.dart';
import '../features/education/education_screen.dart';
import '../features/home/home_screen.dart';
import '../features/investing/investing_screen.dart';
import '../features/taxes/taxes_screen.dart';
import 'fc_header.dart';

class _Tab {
  const _Tab(this.label, this.icon, this.activeIcon, this.screen);
  final String label;
  final IconData icon;
  final IconData activeIcon;
  final Widget screen;
}

/// The root navigation shell: the shared [FCHeader], an [IndexedStack] of the
/// five top-level destinations (mirroring `header.tsx`'s nav), and a custom
/// bottom navigation bar.
class NavigationShell extends StatefulWidget {
  const NavigationShell({super.key});

  @override
  State<NavigationShell> createState() => _NavigationShellState();
}

class _NavigationShellState extends State<NavigationShell> {
  int _index = 0;

  static const _tabs = <_Tab>[
    _Tab('Home', Icons.home_outlined, Icons.home, HomeScreen()),
    _Tab('Budgeting', Icons.account_balance_wallet_outlined,
        Icons.account_balance_wallet, BudgetingScreen()),
    _Tab('Investing', Icons.trending_up, Icons.trending_up, InvestingScreen()),
    _Tab('Taxes', Icons.receipt_long_outlined, Icons.receipt_long,
        TaxesScreen()),
    _Tab('Education', Icons.school_outlined, Icons.school, EducationScreen()),
  ];

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      backgroundColor: c.background,
      appBar: const FCHeader(),
      body: IndexedStack(
        index: _index,
        children: [for (final tab in _tabs) tab.screen],
      ),
      bottomNavigationBar: _FCBottomNav(
        index: _index,
        tabs: _tabs,
        onSelect: (i) => setState(() => _index = i),
      ),
    );
  }
}

class _FCBottomNav extends StatelessWidget {
  const _FCBottomNav({
    required this.index,
    required this.tabs,
    required this.onSelect,
  });

  final int index;
  final List<_Tab> tabs;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      decoration: BoxDecoration(
        color: c.background,
        border: Border(top: BorderSide(color: c.border)),
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 60,
          child: Row(
            children: [
              for (var i = 0; i < tabs.length; i++)
                Expanded(
                  child: _NavItem(
                    tab: tabs[i],
                    selected: i == index,
                    onTap: () => onSelect(i),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.tab,
    required this.selected,
    required this.onTap,
  });

  final _Tab tab;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final color = selected ? FCPalette.blue600 : c.mutedForeground;
    return Semantics(
      button: true,
      selected: selected,
      label: tab.label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(selected ? tab.activeIcon : tab.icon, size: 22, color: color),
            const SizedBox(height: 4),
            Text(
              tab.label,
              style: TextStyle(
                fontSize: 11,
                fontWeight:
                    selected ? FCFontWeights.semibold : FCFontWeights.medium,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
