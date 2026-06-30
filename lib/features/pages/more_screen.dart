import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_service.dart';
import '../../core/design_system/design_system.dart';
import '../../core/theme/theme_controller.dart';
import '../../shared/widgets/page_scaffold.dart';
import '../auth/sign_in_screen.dart';
import '../auth/sign_up_screen.dart';
import 'about_screen.dart';
import 'advising_screen.dart';
import 'premium_screen.dart';
import 'privacy_screen.dart';
import 'terms_screen.dart';

/// The app version line shown in the footer. Mirrors `pubspec.yaml`'s `version`.
const String _kAppVersion = '1.0.0';

/// The "More" tab: a settings hub with appearance, account, links to the
/// static content pages, and an about footer.
class MoreScreen extends StatelessWidget {
  const MoreScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return FCPageBody(
      title: 'More',
      description: 'Settings, your account, and information about FinnaCalc.',
      children: const [
        _SectionLabel('Appearance'),
        SizedBox(height: 8),
        _AppearanceCard(),
        SizedBox(height: 24),
        _SectionLabel('Account'),
        SizedBox(height: 8),
        _AccountCard(),
        SizedBox(height: 24),
        _SectionLabel('Information'),
        SizedBox(height: 8),
        _LinksCard(),
        SizedBox(height: 24),
        _AboutFooter(),
      ],
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: TextStyle(
        fontSize: FCFontSizes.sm,
        fontWeight: FCFontWeights.semibold,
        color: context.colors.mutedForeground,
        letterSpacing: 0.2,
      ),
    );
  }
}

class _AppearanceCard extends StatelessWidget {
  const _AppearanceCard();

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final isDark = context.watch<ThemeController>().isDark;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            Icon(
              isDark ? Icons.dark_mode_outlined : Icons.light_mode_outlined,
              size: 22,
              color: c.mutedForeground,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Dark mode',
                    style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.medium,
                      color: c.foreground,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Switch between light and dark themes.',
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      color: c.mutedForeground,
                    ),
                  ),
                ],
              ),
            ),
            Switch(
              value: isDark,
              activeThumbColor: c.primary,
              onChanged: (_) => context.read<ThemeController>().toggle(),
            ),
          ],
        ),
      ),
    );
  }
}

class _AccountCard extends StatelessWidget {
  const _AccountCard();

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final auth = context.watch<AuthService>();
    final user = auth.user;

    if (user != null) {
      final name = user.name.isNotEmpty ? user.name : user.email;
      return FCCard(
        child: FCCardContent(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: c.primary.withValues(alpha: 0.1),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.person_outline,
                        color: c.primary, size: 24),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: FCFontSizes.base,
                            fontWeight: FCFontWeights.semibold,
                            color: c.foreground,
                          ),
                        ),
                        if (user.email.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            user.email,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: FCFontSizes.sm,
                              color: c.mutedForeground,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              FCButton(
                label: 'Sign out',
                variant: FCButtonVariant.outline,
                fullWidth: true,
                icon: const Icon(Icons.logout),
                onPressed: () => context.read<AuthService>().signOut(),
              ),
            ],
          ),
        ),
      );
    }

    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Sign in to save your work',
              style: TextStyle(
                fontSize: FCFontSizes.base,
                fontWeight: FCFontWeights.semibold,
                color: c.foreground,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Create a free account or sign in to your FinnaCalc account.',
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.mutedForeground,
                height: 1.4,
              ),
            ),
            const SizedBox(height: 16),
            FCButton(
              label: 'Sign in',
              fullWidth: true,
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SignInScreen()),
              ),
            ),
            const SizedBox(height: 8),
            FCButton(
              label: 'Sign up',
              variant: FCButtonVariant.outline,
              fullWidth: true,
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SignUpScreen()),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LinksCard extends StatelessWidget {
  const _LinksCard();

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final entries = <_LinkEntry>[
      _LinkEntry(
        Icons.info_outline,
        'About',
        (_) => const AboutScreen(),
      ),
      _LinkEntry(
        Icons.people_outline,
        'Advising',
        (_) => const AdvisingScreen(),
      ),
      _LinkEntry(
        Icons.workspace_premium_outlined,
        'Premium',
        (_) => const PremiumScreen(),
      ),
      _LinkEntry(
        Icons.lock_outline,
        'Privacy Policy',
        (_) => const PrivacyScreen(),
      ),
      _LinkEntry(
        Icons.description_outlined,
        'Terms of Service',
        (_) => const TermsScreen(),
      ),
    ];

    return FCCard(
      child: Column(
        children: [
          for (var i = 0; i < entries.length; i++) ...[
            if (i > 0) Divider(height: 1, color: c.border),
            _LinkRow(entry: entries[i]),
          ],
        ],
      ),
    );
  }
}

class _LinkEntry {
  const _LinkEntry(this.icon, this.label, this.builder);

  final IconData icon;
  final String label;
  final WidgetBuilder builder;
}

class _LinkRow extends StatelessWidget {
  const _LinkRow({required this.entry});

  final _LinkEntry entry;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute(builder: entry.builder),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        child: Row(
          children: [
            Icon(entry.icon, size: 20, color: c.mutedForeground),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                entry.label,
                style: TextStyle(
                  fontSize: FCFontSizes.base,
                  color: c.foreground,
                ),
              ),
            ),
            Icon(Icons.chevron_right, size: 20, color: c.mutedForeground),
          ],
        ),
      ),
    );
  }
}

class _AboutFooter extends StatelessWidget {
  const _AboutFooter();

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      children: [
        Text(
          'FinnaCalc',
          style: TextStyle(
            fontSize: FCFontSizes.base,
            fontWeight: FCFontWeights.semibold,
            color: c.foreground,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Free calculators and tools for your finances and business.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.sm,
            color: c.mutedForeground,
            height: 1.4,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Version $_kAppVersion',
          style: TextStyle(
            fontSize: FCFontSizes.xs,
            color: c.mutedForeground,
          ),
        ),
      ],
    );
  }
}
