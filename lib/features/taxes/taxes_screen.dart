import 'package:flutter/material.dart';

import '../../shared/widgets/page_scaffold.dart';
import 'ui/tax_interview.dart';

/// The taxes tab — hosts the full adaptive interview (the estimator).
/// The pure engine + [TaxController] (provided app-wide via `package:provider`)
/// drive the live result; this screen only wires the UI.
class TaxesScreen extends StatelessWidget {
  const TaxesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const FCPageBody(
      title: 'Tax Estimator',
      description:
          'Answer a few questions and watch your estimated federal (and '
          'state) refund update live.',
      children: [
        TaxInterview(),
      ],
    );
  }
}
