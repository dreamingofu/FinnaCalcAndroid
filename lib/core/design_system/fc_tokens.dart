import 'package:flutter/material.dart';

/// Border radii — mirrors the Tailwind config's `borderRadius` map, which is
/// derived from `--radius: 0.75rem` (12px):
///   lg = var(--radius) = 12, md = radius-2 = 10, sm = radius-4 = 8.
class FCRadii {
  const FCRadii._();

  static const double lg = 12;
  static const double md = 10;
  static const double sm = 8;
  static const double full = 9999;

  static const BorderRadius lgAll = BorderRadius.all(Radius.circular(lg));
  static const BorderRadius mdAll = BorderRadius.all(Radius.circular(md));
  static const BorderRadius smAll = BorderRadius.all(Radius.circular(sm));
  static const BorderRadius fullAll = BorderRadius.all(Radius.circular(full));
}

/// Font sizes, transcribed from Tailwind's default type scale (px).
class FCFontSizes {
  const FCFontSizes._();

  static const double xs = 12;
  static const double sm = 14;
  static const double base = 16;
  static const double lg = 18;
  static const double xl = 20;
  static const double xl2 = 24; // text-2xl
  static const double xl3 = 30; // text-3xl
  static const double xl4 = 36; // text-4xl
}

/// Font weights matching Tailwind's named weights.
class FCFontWeights {
  const FCFontWeights._();

  static const FontWeight normal = FontWeight.w400;
  static const FontWeight medium = FontWeight.w500;
  static const FontWeight semibold = FontWeight.w600;
  static const FontWeight bold = FontWeight.w700;
}

/// Common animation durations.
class FCDurations {
  const FCDurations._();

  /// `transition-colors` default in the web app (~150ms).
  static const Duration transition = Duration(milliseconds: 150);
}

/// `shadow-sm` from Tailwind: `0 1px 2px 0 rgb(0 0 0 / 0.05)`.
const List<BoxShadow> kShadowSm = [
  BoxShadow(
    color: Color(0x0D000000), // black @ 5%
    offset: Offset(0, 1),
    blurRadius: 2,
  ),
];

/// A curated slice of Tailwind's default colour palette, used by the
/// calculators for semantic result colouring (green = good, red = cost, etc.)
/// and accents that are NOT part of the shadcn token set. Hex values are the
/// Tailwind v3 defaults.
class FCPalette {
  const FCPalette._();

  // Blue
  static const Color blue50 = Color(0xFFEFF6FF);
  static const Color blue100 = Color(0xFFDBEAFE);
  static const Color blue400 = Color(0xFF60A5FA);
  static const Color blue500 = Color(0xFF3B82F6);
  static const Color blue600 = Color(0xFF2563EB);
  static const Color blue700 = Color(0xFF1D4ED8);
  static const Color blue950 = Color(0xFF172554);

  // Green
  static const Color green50 = Color(0xFFF0FDF4);
  static const Color green400 = Color(0xFF4ADE80);
  static const Color green500 = Color(0xFF22C55E);
  static const Color green600 = Color(0xFF16A34A);
  static const Color green700 = Color(0xFF15803D);

  // Red
  static const Color red50 = Color(0xFFFEF2F2);
  static const Color red400 = Color(0xFFF87171);
  static const Color red500 = Color(0xFFEF4444);
  static const Color red600 = Color(0xFFDC2626);
  static const Color red700 = Color(0xFFB91C1C);

  // Purple / Violet
  static const Color purple600 = Color(0xFF9333EA);

  // Orange
  static const Color orange600 = Color(0xFFEA580C);

  // Teal
  static const Color teal600 = Color(0xFF0D9488);

  // Yellow / Amber (warning banners)
  static const Color yellow50 = Color(0xFFFEFCE8);
  static const Color yellow200 = Color(0xFFFEF08A);
  static const Color yellow600 = Color(0xFFCA8A04);
  static const Color yellow700 = Color(0xFFA16207);
  static const Color yellow800 = Color(0xFF854D0E);

  // Gray (neutral helpers)
  static const Color gray100 = Color(0xFFF3F4F6);
  static const Color gray300 = Color(0xFFD1D5DB);
  static const Color gray500 = Color(0xFF6B7280);
}
