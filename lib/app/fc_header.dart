import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_service.dart';
import '../core/design_system/design_system.dart';
import '../features/auth/sign_in_screen.dart';
import '../features/auth/sign_up_screen.dart';
import '../features/pages/more_screen.dart';

/// The FinnaCalc wordmark — "Finna" in the foreground colour, "Calc" in blue,
/// mirroring `components/header.tsx`.
class FCWordmark extends StatelessWidget {
  const FCWordmark({super.key, this.fontSize = 20});

  final double fontSize;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(Icons.calculate_rounded, color: FCPalette.blue600, size: fontSize + 6),
        const SizedBox(width: 8),
        Text.rich(
          TextSpan(
            style: TextStyle(
              fontSize: fontSize,
              fontWeight: FCFontWeights.bold,
              color: c.foreground,
            ),
            children: const [
              TextSpan(text: 'Finna'),
              TextSpan(text: 'Calc', style: TextStyle(color: FCPalette.blue600)),
            ],
          ),
        ),
      ],
    );
  }
}

/// The shared top app bar: wordmark on the left, auth-state-dependent actions on
/// the right (Sign in / Sign up when signed-out; a user menu when signed-in).
/// Sticky, with a bottom border — matching the web header.
class FCHeader extends StatelessWidget implements PreferredSizeWidget {
  const FCHeader({super.key});

  @override
  Size get preferredSize => const Size.fromHeight(64);

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      decoration: BoxDecoration(
        color: c.background,
        border: Border(bottom: BorderSide(color: c.border)),
      ),
      child: SafeArea(
        bottom: false,
        child: SizedBox(
          height: 64,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                const FCWordmark(),
                const Spacer(),
                const _AuthActions(),
                const SizedBox(width: 4),
                IconButton(
                  icon: const Icon(Icons.more_horiz),
                  tooltip: 'More',
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (ctx) => Scaffold(
                        backgroundColor: ctx.colors.background,
                        appBar: AppBar(
                          backgroundColor: ctx.colors.background,
                          surfaceTintColor: Colors.transparent,
                          elevation: 0,
                          foregroundColor: ctx.colors.foreground,
                        ),
                        body: const SafeArea(top: false, child: MoreScreen()),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _AuthActions extends StatelessWidget {
  const _AuthActions();

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthService>();

    if (auth.loading) {
      return const SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(strokeWidth: 2),
      );
    }

    final user = auth.user;
    if (user != null) {
      return FCButton(
        label: user.name.isNotEmpty ? user.name : user.email,
        variant: FCButtonVariant.outline,
        size: FCButtonSize.sm,
        icon: const Icon(Icons.person_outline),
        onPressed: () => _showUserMenu(context, auth),
      );
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        FCButton(
          label: 'Sign in',
          variant: FCButtonVariant.ghost,
          size: FCButtonSize.sm,
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const SignInScreen()),
          ),
        ),
        const SizedBox(width: 8),
        FCButton(
          label: 'Sign up',
          size: FCButtonSize.sm,
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const SignUpScreen()),
          ),
        ),
      ],
    );
  }

  void _showUserMenu(BuildContext context, AuthService auth) {
    final c = context.colors;
    final user = auth.user!;
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: c.popover,
      showDragHandle: true,
      builder: (sheetContext) {
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  user.name.isNotEmpty ? user.name : 'Account',
                  style: TextStyle(
                    fontSize: FCFontSizes.base,
                    fontWeight: FCFontWeights.semibold,
                    color: c.popoverForeground,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  user.email,
                  style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    color: c.mutedForeground,
                  ),
                ),
                const SizedBox(height: 16),
                Divider(color: c.border, height: 1),
                const SizedBox(height: 16),
                FCButton(
                  label: 'Sign out',
                  variant: FCButtonVariant.outline,
                  fullWidth: true,
                  icon: const Icon(Icons.logout),
                  onPressed: () async {
                    Navigator.of(sheetContext).pop();
                    await auth.signOut();
                  },
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
