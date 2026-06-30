import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

/// A standard scaffold for the static content pages (About, Advising, Premium,
/// Privacy, Terms): a transparent [AppBar] with a back button + title over a
/// muted background, and a centred, max-width scrollable body.
class PageScaffold extends StatelessWidget {
  const PageScaffold({
    super.key,
    required this.title,
    required this.children,
    this.maxWidth = 896,
  });

  final String title;
  final List<Widget> children;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Scaffold(
      backgroundColor:
          Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.background),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        foregroundColor: c.foreground,
        title: Text(title),
      ),
      body: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
          child: Center(
            child: ConstrainedBox(
              constraints: BoxConstraints(maxWidth: maxWidth),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: children,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A centred section heading with an optional subtitle, matching the web's
/// `text-3xl font-bold` headers with a `text-lg text-muted-foreground` lead.
class PageSectionHeading extends StatelessWidget {
  const PageSectionHeading({super.key, required this.title, this.subtitle});

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      children: [
        Text(
          title,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontSize: FCFontSizes.xl2,
            fontWeight: FCFontWeights.bold,
            color: c.foreground,
            letterSpacing: -0.4,
          ),
        ),
        if (subtitle != null) ...[
          const SizedBox(height: 8),
          Text(
            subtitle!,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: FCFontSizes.base,
              color: c.mutedForeground,
              height: 1.5,
            ),
          ),
        ],
      ],
    );
  }
}

/// A card with an icon + title header and an arbitrary body, used by the legal
/// pages (Privacy/Terms) for each titled section.
class PageContentCard extends StatelessWidget {
  const PageContentCard({
    super.key,
    required this.title,
    required this.children,
    this.icon,
    this.iconColor,
  });

  final String title;
  final IconData? icon;
  final Color? iconColor;
  final List<Widget> children;

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
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (icon != null) ...[
                  Icon(icon, color: iconColor ?? c.foreground, size: 24),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: Text(
                    title,
                    style: TextStyle(
                      fontSize: FCFontSizes.xl2,
                      fontWeight: FCFontWeights.semibold,
                      color: c.cardForeground,
                      letterSpacing: -0.6,
                      height: 1.15,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ...children,
          ],
        ),
      ),
    );
  }
}

/// A body paragraph for content cards (`text-muted-foreground`).
class PageParagraph extends StatelessWidget {
  const PageParagraph(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: TextStyle(
        fontSize: FCFontSizes.base,
        color: context.colors.mutedForeground,
        height: 1.55,
      ),
    );
  }
}

/// A sub-heading inside a content card (`text-lg font-semibold`).
class PageSubheading extends StatelessWidget {
  const PageSubheading(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: TextStyle(
        fontSize: FCFontSizes.lg,
        fontWeight: FCFontWeights.semibold,
        color: context.colors.foreground,
      ),
    );
  }
}

/// A simple bullet list (`• item`), used throughout the legal pages.
///
/// Items may contain a leading bold lead-in (e.g. "Access:") followed by a
/// description; pass it as a [BulletItem] to render the lead-in bold.
class PageBulletList extends StatelessWidget {
  const PageBulletList(this.items, {super.key});

  final List<BulletItem> items;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < items.length; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                  '• ',
                  style: TextStyle(
                    fontSize: FCFontSizes.base,
                    color: c.mutedForeground,
                    height: 1.45,
                  ),
                ),
              ),
              Expanded(
                child: Text.rich(
                  TextSpan(
                    children: [
                      if (items[i].lead != null)
                        TextSpan(
                          text: '${items[i].lead}: ',
                          style: TextStyle(
                            fontWeight: FCFontWeights.semibold,
                            color: c.foreground,
                          ),
                        ),
                      TextSpan(text: items[i].text),
                    ],
                  ),
                  style: TextStyle(
                    fontSize: FCFontSizes.base,
                    color: c.mutedForeground,
                    height: 1.45,
                  ),
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}

/// A bullet entry: an optional bold [lead] (rendered "Lead: ") plus [text].
class BulletItem {
  const BulletItem(this.text, {this.lead});

  final String text;
  final String? lead;
}

/// A tinted callout note (the web's `bg-blue-50` / `bg-yellow-50` boxes).
class PageNoteBox extends StatelessWidget {
  const PageNoteBox({
    super.key,
    required this.text,
    required this.background,
    required this.foreground,
    this.lead,
  });

  final String text;
  final String? lead;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: background,
        borderRadius: FCRadii.lgAll,
      ),
      child: Text.rich(
        TextSpan(
          children: [
            if (lead != null)
              TextSpan(
                text: '$lead ',
                style: const TextStyle(fontWeight: FCFontWeights.bold),
              ),
            TextSpan(text: text),
          ],
        ),
        style: TextStyle(
          fontSize: FCFontSizes.sm,
          color: foreground,
          height: 1.5,
        ),
      ),
    );
  }
}

/// A "Label: value" contact line (the web's `<strong>Label:</strong> value`).
class ContactLine extends StatelessWidget {
  const ContactLine({super.key, required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: '$label: ',
            style: TextStyle(
              fontWeight: FCFontWeights.semibold,
              color: c.foreground,
            ),
          ),
          TextSpan(text: value),
        ],
      ),
      style: TextStyle(
        fontSize: FCFontSizes.base,
        color: c.mutedForeground,
        height: 1.5,
      ),
    );
  }
}
