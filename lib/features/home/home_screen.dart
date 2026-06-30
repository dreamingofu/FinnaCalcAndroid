import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import '../../shared/widgets/page_scaffold.dart';
import '../calculators/calculators.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return FCPageBody(
      title: 'FinnaCalc',
      description:
          'Free calculators and tools for your finances and business.',
      children: [
        for (final calc in kCalculators) ...[
          _CalculatorCard(info: calc),
          const SizedBox(height: 12),
        ],
      ],
    );
  }
}

class _CalculatorCard extends StatelessWidget {
  const _CalculatorCard({required this.info});

  final CalculatorInfo info;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(builder: info.builder),
      ),
      child: FCCard(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: FCPalette.blue600.withValues(alpha: 0.1),
                  borderRadius: FCRadii.mdAll,
                ),
                child: Icon(info.icon, color: FCPalette.blue600, size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      info.title,
                      style: TextStyle(
                        fontSize: FCFontSizes.base,
                        fontWeight: FCFontWeights.semibold,
                        color: c.cardForeground,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      info.description,
                      style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        color: c.mutedForeground,
                        height: 1.35,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              Icon(Icons.chevron_right, color: c.mutedForeground, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}
