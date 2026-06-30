import 'package:flutter/material.dart';

import '../../app/fc_header.dart';
import '../../core/design_system/design_system.dart';

/// Shared chrome for the sign-in / sign-up screens: a back-navigable bar with
/// the wordmark, and a centred card holding the form.
class AuthScaffold extends StatelessWidget {
  const AuthScaffold({
    super.key,
    required this.title,
    this.description,
    this.error,
    required this.children,
  });

  final String title;
  final String? description;
  final String? error;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      backgroundColor: c.background,
      appBar: AppBar(
        backgroundColor: c.background,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        foregroundColor: c.foreground,
        centerTitle: true,
        title: const FCWordmark(fontSize: 18),
        shape: Border(bottom: BorderSide(color: c.border)),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: FCCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    FCCardHeader(children: [
                      FCCardTitle(title),
                      if (description != null) FCCardDescription(description!),
                    ]),
                    FCCardContent(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (error != null) ...[
                            _AuthError(error!),
                            const SizedBox(height: 16),
                          ],
                          ...children,
                        ],
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

class _AuthError extends StatelessWidget {
  const _AuthError(this.message);

  final String message;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: c.destructive.withValues(alpha: 0.1),
        borderRadius: FCRadii.mdAll,
        border: Border.all(color: c.destructive.withValues(alpha: 0.3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.error_outline, size: 16, color: c.destructive),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              message,
              style: TextStyle(fontSize: FCFontSizes.sm, color: c.destructive),
            ),
          ),
        ],
      ),
    );
  }
}

/// `Or continue with` divider used on the auth forms.
class AuthOrDivider extends StatelessWidget {
  const AuthOrDivider({super.key, this.label = 'Or continue with'});

  final String label;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Row(
      children: [
        Expanded(child: Divider(color: c.border, height: 1)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(
            label,
            style: TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
          ),
        ),
        Expanded(child: Divider(color: c.border, height: 1)),
      ],
    );
  }
}

/// A "leading text + tappable link" line (e.g. "Don't have an account? Sign up").
class AuthTextLink extends StatelessWidget {
  const AuthTextLink({
    super.key,
    required this.leading,
    required this.link,
    required this.onTap,
  });

  final String leading;
  final String link;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          leading,
          style: TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground),
        ),
        GestureDetector(
          onTap: onTap,
          child: Text(
            link,
            style: TextStyle(
              fontSize: FCFontSizes.sm,
              fontWeight: FCFontWeights.medium,
              color: FCPalette.blue600,
            ),
          ),
        ),
      ],
    );
  }
}
