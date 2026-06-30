import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../questions/sections.dart' show lifeSituations;
import '../tax_controller.dart';
import 'icons.dart';

/// "Check all that apply" intro screen. Selecting a situation unlocks the
/// matching interview sections. Mirrors `ui/LifeSituations.tsx`.
class LifeSituations extends StatelessWidget {
  const LifeSituations({super.key, required this.onContinue});

  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final ctrl = context.watch<TaxController>();
    final answers = ctrl.answers;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Column(
          children: [
            Text(
              "Let's start with your situation",
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: FCFontSizes.xl2,
                fontWeight: FCFontWeights.bold,
                color: c.foreground,
                letterSpacing: -0.5,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              "Pick everything that applies to you. We'll only ask about what's "
              'relevant — and your estimated refund updates as you go.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: FCFontSizes.base,
                color: c.mutedForeground,
                height: 1.5,
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),
        LayoutBuilder(builder: (context, constraints) {
          const spacing = 12.0;
          final twoCol = constraints.maxWidth >= 520;
          final itemWidth = twoCol
              ? (constraints.maxWidth - spacing) / 2
              : constraints.maxWidth;
          return Wrap(
            spacing: spacing,
            runSpacing: spacing,
            children: [
              for (final ls in lifeSituations)
                SizedBox(
                  width: itemWidth,
                  child: _LifeSituationCard(
                    label: ls.label,
                    icon: taxIconFor(ls.icon),
                    selected: answers[ls.id] == true,
                    onTap: () =>
                        ctrl.setAnswer(ls.id, answers[ls.id] == true ? null : true),
                  ),
                ),
            ],
          );
        }),
        const SizedBox(height: 24),
        Align(
          alignment: Alignment.centerRight,
          child: FCButton(
            label: 'Continue',
            trailingIcon: const Icon(Icons.arrow_forward),
            onPressed: onContinue,
          ),
        ),
      ],
    );
  }
}

class _LifeSituationCard extends StatelessWidget {
  const _LifeSituationCard({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onTap,
      child: AnimatedContainer(
        duration: FCDurations.transition,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: selected
              ? Color.alphaBlend(c.primary.withValues(alpha: 0.05), c.card)
              : c.card,
          borderRadius: FCRadii.lgAll,
          border: Border.all(
            color: selected ? c.primary : c.border,
          ),
          boxShadow: kShadowSm,
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: selected ? c.primary : c.muted,
                borderRadius: FCRadii.lgAll,
              ),
              child: Icon(
                icon,
                size: 20,
                color: selected ? c.primaryForeground : c.mutedForeground,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.medium,
                  color: c.foreground,
                  height: 1.3,
                ),
              ),
            ),
            if (selected) ...[
              const SizedBox(width: 8),
              Icon(Icons.check, size: 20, color: c.primary),
            ],
          ],
        ),
      ),
    );
  }
}
