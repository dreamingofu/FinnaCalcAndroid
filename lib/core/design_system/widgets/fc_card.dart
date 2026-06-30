import 'package:flutter/material.dart';

import '../fc_theme.dart';
import '../fc_tokens.dart';

/// A custom card matching `components/ui/card.tsx`'s `<Card>`:
/// `rounded-lg border bg-card text-card-foreground shadow-sm`.
///
/// Compose with [FCCardHeader] / [FCCardContent] / [FCCardFooter], e.g.:
/// ```dart
/// FCCard(child: Column(children: [FCCardHeader(...), FCCardContent(...)]))
/// ```
class FCCard extends StatelessWidget {
  const FCCard({super.key, required this.child, this.margin});

  final Widget child;
  final EdgeInsetsGeometry? margin;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      margin: margin,
      decoration: BoxDecoration(
        color: c.card,
        borderRadius: FCRadii.lgAll,
        border: Border.all(color: c.border, width: 1),
        boxShadow: kShadowSm,
      ),
      clipBehavior: Clip.antiAlias,
      child: DefaultTextStyle.merge(
        style: TextStyle(color: c.cardForeground),
        child: child,
      ),
    );
  }
}

/// `flex flex-col space-y-1.5 p-6`.
class FCCardHeader extends StatelessWidget {
  const FCCardHeader({
    super.key,
    required this.children,
    this.crossAxisAlignment = CrossAxisAlignment.start,
    this.padding = const EdgeInsets.all(24),
  });

  final List<Widget> children;
  final CrossAxisAlignment crossAxisAlignment;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding,
      child: Column(
        crossAxisAlignment: crossAxisAlignment,
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) const SizedBox(height: 6), // space-y-1.5
            children[i],
          ],
        ],
      ),
    );
  }
}

/// `text-2xl font-semibold leading-none tracking-tight`.
class FCCardTitle extends StatelessWidget {
  const FCCardTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: TextStyle(
        fontSize: FCFontSizes.xl2,
        fontWeight: FCFontWeights.semibold,
        height: 1.0, // leading-none
        letterSpacing: -0.6, // tracking-tight (~ -0.025em @ 24px)
        color: context.colors.cardForeground,
      ),
    );
  }
}

/// `text-sm text-muted-foreground`.
class FCCardDescription extends StatelessWidget {
  const FCCardDescription(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: TextStyle(
        fontSize: FCFontSizes.sm,
        height: 1.4,
        color: context.colors.mutedForeground,
      ),
    );
  }
}

/// `p-6 pt-0`.
class FCCardContent extends StatelessWidget {
  const FCCardContent({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.fromLTRB(24, 0, 24, 24),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Padding(padding: padding, child: child);
  }
}

/// `flex items-center p-6 pt-0`.
class FCCardFooter extends StatelessWidget {
  const FCCardFooter({
    super.key,
    required this.children,
    this.mainAxisAlignment = MainAxisAlignment.start,
    this.padding = const EdgeInsets.fromLTRB(24, 0, 24, 24),
  });

  final List<Widget> children;
  final MainAxisAlignment mainAxisAlignment;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding,
      child: Row(
        mainAxisAlignment: mainAxisAlignment,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: children,
      ),
    );
  }
}
