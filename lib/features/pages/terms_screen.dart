import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import 'page_widgets.dart';

/// Static "Terms of Service" page, ported faithfully from the web
/// `app/terms/page.tsx`.
class TermsScreen extends StatelessWidget {
  const TermsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return PageScaffold(
      title: 'Terms of Service',
      children: [
        const SizedBox(height: 4),
        const PageSectionHeading(title: 'Terms of Service'),
        const SizedBox(height: 24),

        // Agreement.
        const PageContentCard(
          title: 'Terms of Service Agreement',
          icon: Icons.description_outlined,
          iconColor: FCPalette.blue600,
          children: [
            PageParagraph(
              "These Terms of Service govern your use of FinnaCalc's website "
              'and services. By accessing or using the services, you agree to '
              'be bound by these Terms. If you disagree with any part of these '
              'terms, then you may and should not access the services.',
            ),
            SizedBox(height: 16),
            PageParagraph(
              'The right to update these Terms at any time is reserved. Changes '
              'will be effective immediately upon posting. Your continued use '
              'of the services after changes are posted constitutes acceptance '
              'of the new Terms.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Description of Service.
        PageContentCard(
          title: 'Description of Service',
          icon: Icons.people_outline,
          iconColor: FCPalette.green600,
          children: [
            const PageParagraph(
              'FinnaCalc provides free financial calculators and planning tools '
              'for personal and business use. The services include but are not '
              'limited to:',
            ),
            const SizedBox(height: 16),
            const PageBulletList([
              BulletItem(
                'Business financial calculators (startup costs, break-even '
                'analysis, ROI, etc.)',
              ),
              BulletItem(
                'Personal finance tools (tax calculators, loan analyzers, etc.)',
              ),
              BulletItem('Educational content and financial planning resources'),
              BulletItem('Data export and sharing capabilities'),
            ]),
            const SizedBox(height: 16),
            PageNoteBox(
              lead: 'Important:',
              text:
                  'The calculators provide estimates for planning purposes '
                  'only. Results should not be considered as professional '
                  'financial, tax, or legal advice.',
              background: FCPalette.blue50,
              foreground: FCPalette.blue700,
            ),
          ],
        ),
        const SizedBox(height: 16),

        // User Responsibilities.
        const PageContentCard(
          title: 'User Responsibilities',
          icon: Icons.shield_outlined,
          iconColor: FCPalette.purple600,
          children: [
            PageParagraph('By using the services, you agree to:'),
            SizedBox(height: 16),
            PageBulletList([
              BulletItem(
                'Use the service only for lawful purposes and in accordance '
                'with these Terms',
              ),
              BulletItem('Provide accurate information when using the calculators'),
              BulletItem('Not attempt to interfere with or disrupt the services'),
              BulletItem(
                'Not use automated systems to access the services without '
                'permission',
              ),
              BulletItem('Respect intellectual property rights'),
              BulletItem('Not share or distribute malicious content'),
              BulletItem('Comply with all applicable laws and regulations'),
            ]),
          ],
        ),
        const SizedBox(height: 16),

        // Important Disclaimers.
        const PageContentCard(
          title: 'Important Disclaimers',
          icon: Icons.warning_amber_outlined,
          iconColor: FCPalette.yellow600,
          children: [
            PageSubheading('Financial Advice Disclaimer'),
            SizedBox(height: 12),
            PageParagraph(
              'FinnaCalc does not provide financial, investment, tax, or legal '
              'advice. The calculators and tools are for informational and '
              'educational purposes only. Results are estimates based on the '
              'information you provide and should not be relied upon for making '
              'financial decisions without consulting qualified professionals.',
            ),
            SizedBox(height: 20),
            PageSubheading('Accuracy Disclaimer'),
            SizedBox(height: 12),
            PageParagraph(
              'While efforts are made for accuracy, no warranties are made '
              'about the completeness, reliability, or accuracy of the '
              'calculators or information. Financial regulations, tax laws, and '
              'market conditions change frequently, and the tools may not '
              'reflect the most current information.',
            ),
            SizedBox(height: 20),
            PageSubheading('No Warranty'),
            SizedBox(height: 12),
            PageParagraph(
              'The services are provided "as is" without any warranty of any '
              'kind, either express or implied, including but not limited to '
              'warranties of merchantability, fitness for a particular purpose, '
              'or non-infringement.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Limitation of Liability.
        const PageContentCard(
          title: 'Limitation of Liability',
          icon: Icons.gavel_outlined,
          iconColor: FCPalette.red600,
          children: [
            PageParagraph(
              'To the fullest extent permitted by law, FinnaCalc shall not be '
              'liable for any indirect, incidental, special, consequential, or '
              'punitive damages, including but not limited to:',
            ),
            SizedBox(height: 16),
            PageBulletList([
              BulletItem('Financial losses resulting from use of the calculators'),
              BulletItem('Business interruption or loss of profits'),
              BulletItem('Data loss or corruption'),
              BulletItem('Third-party claims or damages'),
            ]),
            SizedBox(height: 16),
            PageParagraph(
              'Total liability for any claims arising from your use of the '
              'services shall not exceed the amount paid for the services '
              '(which is \$0 for free services).',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Intellectual Property Rights.
        const PageContentCard(
          title: 'Intellectual Property Rights',
          children: [
            PageParagraph(
              'The FinnaCalc website, including its content, features, and '
              'functionality, is owned by FinnaCalc and is protected by '
              'copyright, trademark, and other intellectual property laws.',
            ),
            SizedBox(height: 16),
            PageParagraph(
              'You may use the services for personal and business purposes, but '
              'you may not:',
            ),
            SizedBox(height: 16),
            PageBulletList([
              BulletItem('Copy, modify, or distribute content without permission'),
              BulletItem('Use trademarks or branding without authorization'),
              BulletItem('Create derivative works based on the services'),
              BulletItem('Reverse engineer or attempt to extract source code'),
            ]),
          ],
        ),
        const SizedBox(height: 16),

        // Privacy and Data Protection.
        const PageContentCard(
          title: 'Privacy and Data Protection',
          children: [
            PageParagraph(
              'Your privacy is important. The collection and use of personal '
              'information is governed by the Privacy Policy, which is '
              'incorporated into these Terms by reference. By using the '
              'services, you consent to the collection and use of information '
              'as described in the Privacy Policy.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Termination.
        const PageContentCard(
          title: 'Termination',
          children: [
            PageParagraph(
              'Access to the services may be terminated or suspended '
              'immediately, without prior notice or liability, for any reason, '
              'including breach of these Terms. Upon termination, your right to '
              'use the services will cease immediately.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Governing Law and Jurisdiction.
        const PageContentCard(
          title: 'Governing Law and Jurisdiction',
          children: [
            PageParagraph(
              'These Terms shall be governed by and construed in accordance '
              'with the laws of the United States, without regard to conflict '
              'of law principles. Any disputes arising from these Terms or your '
              'use of the services shall be resolved through binding '
              'arbitration or in the courts of competent jurisdiction.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Severability and Entire Agreement.
        const PageContentCard(
          title: 'Severability and Entire Agreement',
          children: [
            PageParagraph(
              'If any provision of these Terms is held to be invalid or '
              'unenforceable, the remaining provisions will remain in full '
              'force and effect.',
            ),
            SizedBox(height: 16),
            PageParagraph(
              'These Terms, together with the Privacy Policy, constitute the '
              'entire agreement between you and FinnaCalc regarding your use of '
              'the services.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Contact Information.
        const PageContentCard(
          title: 'Contact Information',
          children: [
            PageParagraph(
              'If you have any questions about these Terms of Service, please '
              'make contact:',
            ),
            SizedBox(height: 16),
            ContactLine(label: 'Help & Assistance', value: 'helpfinnacalc@gmail.com'),
            SizedBox(height: 8),
            ContactLine(label: 'Inquiries', value: 'finnacalc@gmail.com'),
          ],
        ),
      ],
    );
  }
}
