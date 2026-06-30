import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

/// The shared chrome for every standalone calculator: a `bg-muted/40` page with
/// a back button, and a centred max-w-4xl [FCCard] whose header is an icon +
/// title + description (matching the web calculators' layout).
class CalculatorScaffold extends StatelessWidget {
  const CalculatorScaffold({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    required this.children,
  });

  final IconData icon;
  final String title;
  final String description;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final pageBg = Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.background);

    return Scaffold(
      backgroundColor: pageBg,
      appBar: AppBar(
        backgroundColor: pageBg,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        foregroundColor: c.foreground,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).maybePop(),
          tooltip: 'Back',
        ),
      ),
      body: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 896),
              child: FCCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    FCCardHeader(children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(icon, color: FCPalette.blue600, size: 28),
                          const SizedBox(width: 10),
                          Expanded(child: FCCardTitle(title)),
                        ],
                      ),
                      FCCardDescription(description),
                    ]),
                    FCCardContent(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        mainAxisSize: MainAxisSize.min,
                        children: children,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
