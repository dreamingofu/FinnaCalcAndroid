import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';
import 'page_widgets.dart';

/// Static "Privacy Policy" page, ported faithfully from the web
/// `app/privacy/page.tsx`.
class PrivacyScreen extends StatelessWidget {
  const PrivacyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return PageScaffold(
      title: 'Privacy Policy',
      children: [
        const SizedBox(height: 4),
        const PageSectionHeading(title: 'Privacy Policy'),
        const SizedBox(height: 24),

        // Introduction.
        const PageContentCard(
          title: 'Introduction',
          icon: Icons.visibility_outlined,
          iconColor: FCPalette.blue600,
          children: [
            PageParagraph(
              'FinnaCalc is committed to protecting your privacy. This Privacy '
              'Policy explains how information is collected, used, disclosed, '
              'and safeguarded when you visit the website and use the financial '
              'calculators and tools.',
            ),
            SizedBox(height: 16),
            PageParagraph(
              'By using FinnaCalc, you agree to the collection and use of '
              'information in accordance with this policy. If you do not agree '
              'with the policies and practices outlined, please do not use the '
              'services.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Information Collected.
        PageContentCard(
          title: 'Information Collected',
          icon: Icons.storage_outlined,
          iconColor: FCPalette.green600,
          children: [
            const PageSubheading('Information You Provide'),
            const SizedBox(height: 12),
            const PageBulletList([
              BulletItem(
                'Calculator inputs and financial data (processed locally, not '
                'stored)',
              ),
              BulletItem('Contact information when you reach out'),
              BulletItem('Feedback and suggestions you provide'),
            ]),
            const SizedBox(height: 20),
            const PageSubheading('Automatically Collected Information'),
            const SizedBox(height: 12),
            const PageBulletList([
              BulletItem(
                'Usage data and analytics (page views, time spent, features '
                'used)',
              ),
              BulletItem(
                'Device information (browser type, operating system, screen '
                'resolution)',
              ),
              BulletItem('IP address and general location information'),
              BulletItem('Cookies and similar tracking technologies'),
            ]),
            const SizedBox(height: 16),
            PageNoteBox(
              lead: 'Important:',
              text:
                  'All financial calculations are performed locally in your '
                  'browser. Your personal financial data entered into the '
                  'calculators is not stored, transmitted, or accessed.',
              background: FCPalette.blue50,
              foreground: FCPalette.blue700,
            ),
          ],
        ),
        const SizedBox(height: 16),

        // How Information Is Used.
        const PageContentCard(
          title: 'How Information Is Used',
          icon: Icons.people_outline,
          iconColor: FCPalette.purple600,
          children: [
            PageBulletList([
              BulletItem(
                'To provide and maintain financial calculators and tools',
                lead: 'Service Provision',
              ),
              BulletItem(
                'To analyze usage patterns and improve services',
                lead: 'Improvement',
              ),
              BulletItem(
                'To respond to inquiries and provide customer support',
                lead: 'Communication',
              ),
              BulletItem(
                'To detect, prevent, and address technical issues and security '
                'threats',
                lead: 'Security',
              ),
              BulletItem(
                'To comply with applicable laws and regulations',
                lead: 'Legal Compliance',
              ),
            ]),
          ],
        ),
        const SizedBox(height: 16),

        // Information Sharing and Disclosure.
        const PageContentCard(
          title: 'Information Sharing and Disclosure',
          icon: Icons.lock_outline,
          iconColor: FCPalette.red600,
          children: [
            PageParagraph(
              'Personal information is not sold, traded, or otherwise '
              'transferred to third parties except in the following '
              'circumstances:',
            ),
            SizedBox(height: 16),
            PageBulletList([
              BulletItem(
                'Trusted third parties who assist in operating the website and '
                'conducting business',
                lead: 'Service Providers',
              ),
              BulletItem(
                'When required by law or to protect rights and safety',
                lead: 'Legal Requirements',
              ),
              BulletItem(
                'In connection with a merger, acquisition, or sale of assets',
                lead: 'Business Transfers',
              ),
              BulletItem(
                'When you have given explicit consent for sharing',
                lead: 'Consent',
              ),
            ]),
          ],
        ),
        const SizedBox(height: 16),

        // Data Security.
        PageContentCard(
          title: 'Data Security',
          icon: Icons.shield_outlined,
          iconColor: FCPalette.orange600,
          children: [
            const PageParagraph(
              'Appropriate technical and organizational security measures are '
              'implemented to protect your information against unauthorized '
              'access, alteration, disclosure, or destruction.',
            ),
            const SizedBox(height: 16),
            const PageBulletList([
              BulletItem('SSL encryption for data transmission'),
              BulletItem('Regular security assessments and updates'),
              BulletItem(
                'Limited access to personal information on a need-to-know basis',
              ),
              BulletItem('Secure hosting infrastructure'),
            ]),
            const SizedBox(height: 16),
            PageNoteBox(
              lead: 'Note:',
              text:
                  'While efforts are made to protect your information, no method '
                  'of transmission over the internet or electronic storage is '
                  '100% secure. Absolute security cannot be guaranteed.',
              background: FCPalette.yellow50,
              foreground: FCPalette.yellow800,
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Cookies and Tracking.
        const PageContentCard(
          title: 'Cookies and Tracking Technologies',
          children: [
            PageParagraph(
              'Cookies and similar tracking technologies are used to enhance '
              'your experience on the website:',
            ),
            SizedBox(height: 16),
            PageBulletList([
              BulletItem(
                'Required for basic website functionality',
                lead: 'Essential Cookies',
              ),
              BulletItem(
                'To understand how visitors use the site',
                lead: 'Analytics Cookies',
              ),
              BulletItem(
                'To remember your settings and preferences',
                lead: 'Preference Cookies',
              ),
            ]),
            SizedBox(height: 16),
            PageParagraph(
              'You can control cookies through your browser settings. However, '
              'disabling certain cookies may affect website functionality.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Your Privacy Rights.
        const PageContentCard(
          title: 'Your Privacy Rights',
          children: [
            PageParagraph(
              'Depending on your location, you may have the following rights:',
            ),
            SizedBox(height: 16),
            PageBulletList([
              BulletItem(
                'Request information about the personal data held about you',
                lead: 'Access',
              ),
              BulletItem(
                'Request correction of inaccurate or incomplete information',
                lead: 'Correction',
              ),
              BulletItem(
                'Request deletion of your personal information',
                lead: 'Deletion',
              ),
              BulletItem(
                'Request a copy of your data in a structured format',
                lead: 'Portability',
              ),
              BulletItem(
                'Object to certain processing of your information',
                lead: 'Objection',
              ),
            ]),
          ],
        ),
        const SizedBox(height: 16),

        // Children's Privacy.
        const PageContentCard(
          title: "Children's Privacy",
          children: [
            PageParagraph(
              'The services are not intended for children under 13 years of '
              'age. Personal information from children under 13 is not '
              'knowingly collected. If you are a parent or guardian and believe '
              'your child has provided personal information, please make '
              'contact immediately.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Changes to This Privacy Policy.
        const PageContentCard(
          title: 'Changes to This Privacy Policy',
          icon: Icons.description_outlined,
          iconColor: FCPalette.blue600,
          children: [
            PageParagraph(
              'This Privacy Policy may be updated from time to time. You will '
              'be notified of any changes by posting the new Privacy Policy on '
              'this page. You are advised to review this Privacy Policy '
              'periodically for any changes.',
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Contact Us.
        const PageContentCard(
          title: 'Contact Us',
          children: [
            PageParagraph(
              'If you have any questions about this Privacy Policy or privacy '
              'practices, please make contact:',
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
