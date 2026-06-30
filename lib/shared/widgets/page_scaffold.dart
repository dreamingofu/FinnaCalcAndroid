import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

/// A standard scrollable page body used by tab screens and detail pages: a
/// centred, max-width column with a title, optional description, and content.
class FCPageBody extends StatelessWidget {
  const FCPageBody({
    super.key,
    required this.title,
    this.description,
    required this.children,
    this.maxWidth = 896, // max-w-4xl
    this.padding = const EdgeInsets.fromLTRB(16, 24, 16, 40),
  });

  final String title;
  final String? description;
  final List<Widget> children;
  final double maxWidth;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return SingleChildScrollView(
      padding: padding,
      child: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: maxWidth),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                title,
                style: TextStyle(
                  fontSize: FCFontSizes.xl3,
                  fontWeight: FCFontWeights.bold,
                  color: c.foreground,
                  letterSpacing: -0.5,
                ),
              ),
              if (description != null) ...[
                const SizedBox(height: 8),
                Text(
                  description!,
                  style: TextStyle(
                    fontSize: FCFontSizes.base,
                    color: c.mutedForeground,
                    height: 1.5,
                  ),
                ),
              ],
              const SizedBox(height: 24),
              ...children,
            ],
          ),
        ),
      ),
    );
  }
}

/// Temporary "coming soon" content for tabs whose feature lands in a later
/// phase. Replaced as each phase is implemented.
class FCComingSoon extends StatelessWidget {
  const FCComingSoon({super.key, required this.icon, required this.note});

  final IconData icon;
  final String note;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(32),
        child: Column(
          children: [
            Icon(icon, size: 40, color: c.mutedForeground),
            const SizedBox(height: 16),
            Text(
              note,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.mutedForeground,
                height: 1.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
