import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

/// A single result metric: small muted label, a large coloured value, and an
/// optional sub-line. Mirrors the web calculators' result tiles.
class FCResultTile extends StatelessWidget {
  const FCResultTile({
    super.key,
    required this.label,
    required this.value,
    this.valueColor,
    this.sub,
    this.valueSize = 28,
  });

  final String label;
  final String value;
  final Color? valueColor;
  final String? sub;
  final double valueSize;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label,
            style: TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground)),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            fontSize: valueSize,
            fontWeight: FCFontWeights.bold,
            color: valueColor ?? c.foreground,
            height: 1.1,
          ),
        ),
        if (sub != null) ...[
          const SizedBox(height: 4),
          Text(sub!,
              style:
                  TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground)),
        ],
      ],
    );
  }
}

/// Lays children out in an N-column responsive grid (equal widths), wrapping to
/// new rows as needed.
class FCResultGrid extends StatelessWidget {
  const FCResultGrid({
    super.key,
    required this.children,
    this.columns = 2,
    this.spacing = 16,
    this.runSpacing = 16,
  });

  final List<Widget> children;
  final int columns;
  final double spacing;
  final double runSpacing;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, constraints) {
      final cols = columns;
      final itemWidth =
          (constraints.maxWidth - spacing * (cols - 1)) / cols;
      return Wrap(
        spacing: spacing,
        runSpacing: runSpacing,
        children: [
          for (final child in children)
            SizedBox(width: itemWidth > 0 ? itemWidth : constraints.maxWidth, child: child),
        ],
      );
    });
  }
}

/// A `bg-muted/40` rounded panel with an optional title.
class FCResultPanel extends StatelessWidget {
  const FCResultPanel({
    super.key,
    this.title,
    required this.child,
    this.padding = const EdgeInsets.all(16),
  });

  final String? title;
  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      width: double.infinity,
      padding: padding,
      decoration: BoxDecoration(
        color: Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.card),
        borderRadius: FCRadii.lgAll,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (title != null) ...[
            Text(title!,
                style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    fontWeight: FCFontWeights.semibold,
                    color: c.foreground)),
            const SizedBox(height: 12),
          ],
          child,
        ],
      ),
    );
  }
}

/// A label-left / value-right line for statements & breakdowns, with optional
/// top border, bold, and value colour.
class FCResultRow extends StatelessWidget {
  const FCResultRow({
    super.key,
    required this.label,
    required this.value,
    this.valueColor,
    this.labelColor,
    this.bold = false,
    this.topBorder = false,
    this.large = false,
  });

  final String label;
  final String value;
  final Color? valueColor;
  final Color? labelColor;
  final bool bold;
  final bool topBorder;
  final bool large;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final size = large ? FCFontSizes.base : FCFontSizes.sm;
    final weight = bold ? FCFontWeights.bold : FCFontWeights.normal;
    return Container(
      padding: EdgeInsets.only(top: topBorder ? 8 : 0),
      margin: EdgeInsets.only(top: topBorder ? 8 : 0),
      decoration: topBorder
          ? BoxDecoration(border: Border(top: BorderSide(color: c.border)))
          : null,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Text(label,
                style: TextStyle(
                    fontSize: size,
                    fontWeight: weight,
                    color: labelColor ?? c.foreground)),
          ),
          const SizedBox(width: 12),
          Text(value,
              style: TextStyle(
                  fontSize: size,
                  fontWeight: bold ? FCFontWeights.bold : FCFontWeights.medium,
                  color: valueColor ?? c.foreground)),
        ],
      ),
    );
  }
}

/// A coloured callout banner (e.g. the cash-flow warning, competitive-analysis).
class FCCalloutBanner extends StatelessWidget {
  const FCCalloutBanner({
    super.key,
    required this.child,
    required this.background,
    this.border,
  });

  final Widget child;
  final Color background;
  final Color? border;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: background,
        borderRadius: FCRadii.mdAll,
        border: border != null ? Border.all(color: border!) : null,
      ),
      child: child,
    );
  }
}

/// A simple 0–100 progress bar (rounded track + fill).
class FCProgressBar extends StatelessWidget {
  const FCProgressBar({
    super.key,
    required this.value,
    this.color,
    this.height = 8,
  });

  /// 0–100.
  final double value;
  final Color? color;
  final double height;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final clamped = value.clamp(0, 100) / 100.0;
    return ClipRRect(
      borderRadius: FCRadii.fullAll,
      child: Stack(
        children: [
          Container(height: height, color: c.muted),
          FractionallySizedBox(
            widthFactor: clamped.toDouble(),
            child: Container(
              height: height,
              color: color ?? FCPalette.blue600,
            ),
          ),
        ],
      ),
    );
  }
}
