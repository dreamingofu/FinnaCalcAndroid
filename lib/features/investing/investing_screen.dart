import 'package:flutter/material.dart';

import '../../shared/widgets/page_scaffold.dart';

class InvestingScreen extends StatelessWidget {
  const InvestingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const FCPageBody(
      title: 'Investing',
      description:
          'Stocks, bonds, safe options, market data, and brokerage '
          'connections.',
      children: [
        FCComingSoon(
          icon: Icons.trending_up,
          note: 'Investing, market data, and SnapTrade arrive in a later phase.',
        ),
      ],
    );
  }
}
