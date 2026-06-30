import 'package:flutter/material.dart';

import '../../shared/widgets/page_scaffold.dart';

class BudgetingScreen extends StatelessWidget {
  const BudgetingScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const FCPageBody(
      title: 'Budgeting',
      description:
          'Track income and expenses, connect your bank, and get AI budget '
          'guidance.',
      children: [
        FCComingSoon(
          icon: Icons.account_balance_wallet_outlined,
          note: 'Budgeting + bank connections arrive in a later phase.',
        ),
      ],
    );
  }
}
