import 'package:flutter/material.dart';

import '../../shared/widgets/page_scaffold.dart';

class TaxesScreen extends StatelessWidget {
  const TaxesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const FCPageBody(
      title: 'Taxes',
      description:
          'Estimate your federal and state taxes and explore tax education.',
      children: [
        FCComingSoon(
          icon: Icons.receipt_long_outlined,
          note: 'The tax estimator and calculators arrive in a later phase.',
        ),
      ],
    );
  }
}
