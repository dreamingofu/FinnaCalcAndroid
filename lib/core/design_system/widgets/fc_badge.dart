import 'package:flutter/material.dart';

import '../fc_theme.dart';
import '../fc_tokens.dart';

/// Variants — 1:1 with `components/ui/badge.tsx`. (`primary` == cva `default`.)
enum FCBadgeVariant { primary, secondary, destructive, outline }

/// A custom badge matching shadcn/ui's `<Badge>`:
/// `rounded-full border px-2.5 py-0.5 text-xs font-semibold`.
class FCBadge extends StatelessWidget {
  const FCBadge(
    this.label, {
    super.key,
    this.variant = FCBadgeVariant.primary,
    this.icon,
  });

  final String label;
  final FCBadgeVariant variant;
  final Widget? icon;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;

    late final Color background;
    late final Color foreground;
    late final Color border;
    switch (variant) {
      case FCBadgeVariant.primary:
        background = c.primary;
        foreground = c.primaryForeground;
        border = Colors.transparent;
      case FCBadgeVariant.secondary:
        background = c.secondary;
        foreground = c.secondaryForeground;
        border = Colors.transparent;
      case FCBadgeVariant.destructive:
        background = c.destructive;
        foreground = c.destructiveForeground;
        border = Colors.transparent;
      case FCBadgeVariant.outline:
        background = Colors.transparent;
        foreground = c.foreground;
        border = c.border;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
      decoration: BoxDecoration(
        color: background,
        borderRadius: FCRadii.fullAll,
        border: Border.all(color: border, width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            IconTheme.merge(
              data: IconThemeData(color: foreground, size: 12),
              child: icon!,
            ),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(
              fontSize: FCFontSizes.xs,
              fontWeight: FCFontWeights.semibold,
              color: foreground,
              height: 1.2,
            ),
          ),
        ],
      ),
    );
  }
}
