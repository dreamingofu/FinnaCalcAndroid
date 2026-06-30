import 'package:flutter/material.dart';

import '../../shared/widgets/page_scaffold.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const FCPageBody(
      title: 'FinnaCalc',
      description:
          'Free calculators and tools for your finances and business — '
          'budgeting, investing, taxes, and more.',
      children: [
        FCComingSoon(
          icon: Icons.calculate_outlined,
          note: 'The standalone calculators land here next.',
        ),
      ],
    );
  }
}
