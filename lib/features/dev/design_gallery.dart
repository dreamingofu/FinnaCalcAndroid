import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

/// A living style guide that exercises every `FC*` widget in both light and
/// dark mode. Used during Phase 1 to verify the design system; the real app
/// shell replaces it as `home` in a later phase.
class DesignGallery extends StatefulWidget {
  const DesignGallery({super.key, required this.onToggleTheme});

  final VoidCallback onToggleTheme;

  @override
  State<DesignGallery> createState() => _DesignGalleryState();
}

class _DesignGalleryState extends State<DesignGallery> {
  final _textController = TextEditingController();

  @override
  void dispose() {
    _textController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: c.background,
        foregroundColor: c.foreground,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        shape: Border(bottom: BorderSide(color: c.border)),
        title: const Text('FinnaCalc Design System',
            style: TextStyle(fontWeight: FCFontWeights.bold)),
        actions: [
          IconButton(
            tooltip: isDark ? 'Switch to light' : 'Switch to dark',
            onPressed: widget.onToggleTheme,
            icon: Icon(isDark ? Icons.light_mode : Icons.dark_mode),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _section('Buttons — variants'),
            Wrap(spacing: 12, runSpacing: 12, children: const [
              FCButton(label: 'Primary'),
              FCButton(label: 'Secondary', variant: FCButtonVariant.secondary),
              FCButton(
                  label: 'Destructive',
                  variant: FCButtonVariant.destructive),
              FCButton(label: 'Outline', variant: FCButtonVariant.outline),
              FCButton(label: 'Ghost', variant: FCButtonVariant.ghost),
              FCButton(label: 'Link', variant: FCButtonVariant.link),
            ]),
            const SizedBox(height: 24),
            _section('Buttons — sizes & states'),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const FCButton(label: 'Small', size: FCButtonSize.sm),
                const FCButton(label: 'Medium'),
                const FCButton(label: 'Large', size: FCButtonSize.lg),
                const FCButton(
                    size: FCButtonSize.icon, icon: Icon(Icons.add)),
                const FCButton(
                    label: 'With icon', icon: Icon(Icons.calculate_outlined)),
                const FCButton(label: 'Loading', loading: true),
                const FCButton(label: 'Disabled', onPressed: null),
                FCButton(label: 'Tap me', onPressed: () {}),
              ],
            ),
            const SizedBox(height: 16),
            FCButton(
              label: 'Full width',
              fullWidth: true,
              icon: const Icon(Icons.check),
              onPressed: () {},
            ),
            const SizedBox(height: 24),
            _section('Badges'),
            Wrap(spacing: 12, runSpacing: 12, children: const [
              FCBadge('Default'),
              FCBadge('Secondary', variant: FCBadgeVariant.secondary),
              FCBadge('Destructive', variant: FCBadgeVariant.destructive),
              FCBadge('Outline', variant: FCBadgeVariant.outline),
            ]),
            const SizedBox(height: 24),
            _section('Text fields'),
            FCTextField(
              controller: _textController,
              label: 'Email',
              hintText: 'you@example.com',
              helperText: "We'll never share it.",
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 16),
            const FCTextField(
              label: 'Amount',
              hintText: '50000',
              prefix: Text(r'$'),
              keyboardType: TextInputType.number,
            ),
            const SizedBox(height: 16),
            const FCTextField(
              label: 'With error',
              hintText: 'Enter a value',
              errorText: 'This field is required.',
            ),
            const SizedBox(height: 16),
            const FCTextField(
              label: 'Disabled',
              hintText: 'Cannot edit',
              enabled: false,
            ),
            const SizedBox(height: 24),
            _section('Card'),
            FCCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const FCCardHeader(children: [
                    FCCardTitle('Loan Calculator'),
                    FCCardDescription(
                        'Calculate payments, true APR, and remaining balances.'),
                  ]),
                  FCCardContent(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: const [
                        Text('Card content goes here — inputs, results, etc.'),
                        SizedBox(height: 12),
                        Row(children: [
                          FCBadge('New', variant: FCBadgeVariant.secondary),
                        ]),
                      ],
                    ),
                  ),
                  FCCardFooter(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      FCButton(
                          label: 'Cancel',
                          variant: FCButtonVariant.ghost,
                          onPressed: () {}),
                      const SizedBox(width: 8),
                      FCButton(label: 'Calculate', onPressed: () {}),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  Widget _section(String title) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Text(
          title,
          style: TextStyle(
            fontSize: FCFontSizes.lg,
            fontWeight: FCFontWeights.semibold,
            color: context.colors.foreground,
          ),
        ),
      );
}
