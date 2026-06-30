import 'package:flutter/material.dart';

import '../../shared/widgets/page_scaffold.dart';

class EducationScreen extends StatelessWidget {
  const EducationScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const FCPageBody(
      title: 'Education',
      description:
          'Learn the essentials: credit, investing, budgeting, retirement, '
          'and taxes.',
      children: [
        FCComingSoon(
          icon: Icons.school_outlined,
          note: 'The financial education hub arrives in a later phase.',
        ),
      ],
    );
  }
}
