import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import 'page_widgets.dart';

/// Static "About" page, ported from the web `app/about/page.tsx`.
class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return PageScaffold(
      title: 'About',
      children: [
        // Hero.
        Text(
          'Empowering Smart Financial Decisions',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.xl3,
            fontWeight: FCFontWeights.bold,
            color: c.foreground,
            height: 1.15,
            letterSpacing: -0.5,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          'FinnaCalc is your trusted partner in financial planning, providing '
          'professional-grade calculators and planning tools to help '
          'individuals and businesses make informed financial decisions.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.lg,
            color: c.mutedForeground,
            height: 1.5,
          ),
        ),
        const SizedBox(height: 32),

        // Mission & Vision.
        const _IconCard(
          icon: Icons.adjust,
          iconColor: FCPalette.blue600,
          title: 'Our Mission',
          body:
              'To democratize financial planning by providing free, accurate, '
              'and easy-to-use financial calculators and personal finance '
              'tools that empower everyone to make better financial decisions, '
              'regardless of their background or experience level.',
        ),
        const SizedBox(height: 16),
        const _IconCard(
          icon: Icons.favorite_border,
          iconColor: FCPalette.red600,
          title: 'Our Vision',
          body:
              "To become the world's most trusted platform for financial "
              'calculations and personal finance planning tools, helping '
              'millions of people achieve their financial goals through '
              'informed decision-making.',
        ),
        const SizedBox(height: 32),

        // What We Offer.
        PageSectionHeading(
          title: 'What We Offer',
          subtitle:
              'Comprehensive financial tools designed for real-world applications',
        ),
        const SizedBox(height: 16),
        const _IconCard(
          icon: Icons.calculate_outlined,
          iconColor: FCPalette.green600,
          title: 'Business Calculators',
          body:
              'Startup costs, break-even analysis, ROI calculations, cash flow '
              'projections, and pricing strategies to help businesses plan and '
              'grow.',
        ),
        const SizedBox(height: 16),
        const _IconCard(
          icon: Icons.people_outline,
          iconColor: FCPalette.purple600,
          title: 'Personal Finance',
          body:
              'Tax calculators, loan analyzers, investment tools, and budgeting '
              'calculators designed for individuals and families.',
        ),
        const SizedBox(height: 16),
        const _IconCard(
          icon: Icons.shield_outlined,
          iconColor: FCPalette.blue600,
          title: 'Professional Grade',
          body:
              'All calculations are based on current financial formulas and '
              'regulations, ensuring accuracy and reliability for professional '
              'use.',
        ),
        const SizedBox(height: 32),

        // Core Values.
        PageSectionHeading(title: 'Our Core Values'),
        const SizedBox(height: 16),
        const _ValueRow(
          icon: Icons.shield_outlined,
          iconColor: FCPalette.blue600,
          title: 'Accuracy',
          body:
              'Every calculation is thoroughly tested and based on current '
              'financial standards and regulations.',
        ),
        const SizedBox(height: 16),
        const _ValueRow(
          icon: Icons.favorite_border,
          iconColor: FCPalette.green600,
          title: 'Accessibility',
          body:
              'Financial planning tools should be available to everyone, '
              'regardless of their economic background.',
        ),
        const SizedBox(height: 16),
        const _ValueRow(
          icon: Icons.people_outline,
          iconColor: FCPalette.purple600,
          title: 'Simplicity',
          body:
              'Complex financial concepts made simple and understandable for '
              'users of all experience levels.',
        ),
        const SizedBox(height: 16),
        const _ValueRow(
          icon: Icons.emoji_events_outlined,
          iconColor: FCPalette.orange600,
          title: 'Excellence',
          body:
              'Continuous improvement and innovation to provide the best '
              'possible user experience.',
        ),
        const SizedBox(height: 32),

        // Why Choose FinnaCalc.
        PageSectionHeading(
          title: 'Why Choose FinnaCalc?',
          subtitle:
              "We're committed to providing the most reliable and user-friendly "
              'financial tools available',
        ),
        const SizedBox(height: 16),
        const _Reason(
          title: 'Free & Accessible',
          body:
              'All basic calculations and personal finance tools are completely '
              'free to use. No hidden fees, no subscriptions, no barriers to '
              'financial planning.',
        ),
        const SizedBox(height: 16),
        const _Reason(
          title: 'Professional Quality',
          body:
              'Our calculators use the same formulas and methodologies employed '
              'by financial professionals and institutions worldwide.',
        ),
        const SizedBox(height: 16),
        const _Reason(
          title: 'User-Friendly Design',
          body:
              'Clean, intuitive interfaces that make complex financial '
              'calculations simple and straightforward for everyone to use.',
        ),
        const SizedBox(height: 16),
        const _Reason(
          title: 'Constantly Updated',
          body:
              'We regularly update our calculators to reflect current tax '
              'rates, interest rates, and financial regulations.',
        ),
        const SizedBox(height: 32),

        // Get in Touch.
        PageSectionHeading(title: 'Get in Touch'),
        const SizedBox(height: 12),
        Text(
          'Have questions, suggestions, or feedback? We\'d love to hear from '
          'you. Our team is committed to continuously improving FinnaCalc '
          'based on user needs and feedback.',
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.base,
            color: c.mutedForeground,
            height: 1.5,
          ),
        ),
        const SizedBox(height: 16),
        const _ContactCard(),
      ],
    );
  }
}

class _IconCard extends StatelessWidget {
  const _IconCard({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: iconColor, size: 24),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    style: TextStyle(
                      fontSize: FCFontSizes.xl2,
                      fontWeight: FCFontWeights.semibold,
                      color: c.cardForeground,
                      letterSpacing: -0.6,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              body,
              style: TextStyle(
                fontSize: FCFontSizes.base,
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

class _ValueRow extends StatelessWidget {
  const _ValueRow({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(20),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: iconColor.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: iconColor, size: 24),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.semibold,
                      color: c.foreground,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    body,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      color: c.mutedForeground,
                      height: 1.45,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Reason extends StatelessWidget {
  const _Reason({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: FCFontSizes.xl,
                fontWeight: FCFontWeights.semibold,
                color: c.foreground,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              body,
              style: TextStyle(
                fontSize: FCFontSizes.base,
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

class _ContactCard extends StatelessWidget {
  const _ContactCard();

  @override
  Widget build(BuildContext context) {
    return const FCCard(
      child: FCCardContent(
        padding: EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ContactLine(label: 'Help & Assistance', value: 'helpfinnacalc@gmail.com'),
            SizedBox(height: 8),
            ContactLine(label: 'Business Inquiries', value: 'finnacalc@gmail.com'),
          ],
        ),
      ),
    );
  }
}
