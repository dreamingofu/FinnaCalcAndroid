import 'package:flutter/material.dart';

/// Converts an HSL triple (matching the CSS `hsl(H S% L%)` tokens in the web
/// app's `globals.css`) into a Flutter [Color].
///
/// Flutter ships HSL→RGB conversion natively, so no custom math is needed.
Color hsl(double h, double s, double l) =>
    HSLColor.fromAHSL(1.0, h, s / 100, l / 100).toColor();

/// The full shadcn/ui semantic colour token set, carried as a [ThemeExtension]
/// because Material's [ColorScheme] has no slot for `muted`, `accent`,
/// `border`, `input`, `ring`, `card`, or their `*-foreground` pairs.
///
/// Read it anywhere with `Theme.of(context).extension<FCColors>()!` or the
/// `context.colors` helper in `fc_theme.dart`.
///
/// Values are transcribed 1:1 from `app/globals.css` (`:root` = light,
/// `.dark` = dark) — do not reinterpret them.
@immutable
class FCColors extends ThemeExtension<FCColors> {
  const FCColors({
    required this.background,
    required this.foreground,
    required this.card,
    required this.cardForeground,
    required this.popover,
    required this.popoverForeground,
    required this.primary,
    required this.primaryForeground,
    required this.secondary,
    required this.secondaryForeground,
    required this.muted,
    required this.mutedForeground,
    required this.accent,
    required this.accentForeground,
    required this.destructive,
    required this.destructiveForeground,
    required this.border,
    required this.input,
    required this.ring,
  });

  final Color background;
  final Color foreground;
  final Color card;
  final Color cardForeground;
  final Color popover;
  final Color popoverForeground;
  final Color primary;
  final Color primaryForeground;
  final Color secondary;
  final Color secondaryForeground;
  final Color muted;
  final Color mutedForeground;
  final Color accent;
  final Color accentForeground;
  final Color destructive;
  final Color destructiveForeground;
  final Color border;
  final Color input;
  final Color ring;

  /// Light theme — `:root` in `globals.css` (the live web theme).
  static final FCColors light = FCColors(
    background: hsl(0, 0, 100),
    foreground: hsl(222.2, 84, 4.9),
    card: hsl(0, 0, 100),
    cardForeground: hsl(222.2, 84, 4.9),
    popover: hsl(0, 0, 100),
    popoverForeground: hsl(222.2, 84, 4.9),
    primary: hsl(221.2, 83.2, 53.3),
    primaryForeground: hsl(210, 40, 98),
    secondary: hsl(210, 40, 96),
    secondaryForeground: hsl(222.2, 84, 4.9),
    muted: hsl(210, 40, 96),
    mutedForeground: hsl(215.4, 16.3, 46.9),
    accent: hsl(210, 40, 96),
    accentForeground: hsl(222.2, 84, 4.9),
    destructive: hsl(0, 84.2, 60.2),
    destructiveForeground: hsl(210, 40, 98),
    border: hsl(214.3, 31.8, 91.4),
    input: hsl(214.3, 31.8, 91.4),
    ring: hsl(221.2, 83.2, 53.3),
  );

  /// Dark theme — `.dark` in `globals.css`.
  static final FCColors dark = FCColors(
    background: hsl(222.2, 84, 4.9),
    foreground: hsl(210, 40, 98),
    card: hsl(222.2, 84, 4.9),
    cardForeground: hsl(210, 40, 98),
    popover: hsl(222.2, 84, 4.9),
    popoverForeground: hsl(210, 40, 98),
    primary: hsl(217.2, 91.2, 59.8),
    primaryForeground: hsl(222.2, 47.4, 11.2),
    secondary: hsl(217.2, 32.6, 17.5),
    secondaryForeground: hsl(210, 40, 98),
    muted: hsl(217.2, 32.6, 17.5),
    mutedForeground: hsl(215, 20.2, 65.1),
    accent: hsl(217.2, 32.6, 17.5),
    accentForeground: hsl(210, 40, 98),
    destructive: hsl(0, 62.8, 30.6),
    destructiveForeground: hsl(210, 40, 98),
    border: hsl(217.2, 32.6, 17.5),
    input: hsl(217.2, 32.6, 17.5),
    ring: hsl(224.3, 76.3, 48),
  );

  @override
  FCColors copyWith({
    Color? background,
    Color? foreground,
    Color? card,
    Color? cardForeground,
    Color? popover,
    Color? popoverForeground,
    Color? primary,
    Color? primaryForeground,
    Color? secondary,
    Color? secondaryForeground,
    Color? muted,
    Color? mutedForeground,
    Color? accent,
    Color? accentForeground,
    Color? destructive,
    Color? destructiveForeground,
    Color? border,
    Color? input,
    Color? ring,
  }) {
    return FCColors(
      background: background ?? this.background,
      foreground: foreground ?? this.foreground,
      card: card ?? this.card,
      cardForeground: cardForeground ?? this.cardForeground,
      popover: popover ?? this.popover,
      popoverForeground: popoverForeground ?? this.popoverForeground,
      primary: primary ?? this.primary,
      primaryForeground: primaryForeground ?? this.primaryForeground,
      secondary: secondary ?? this.secondary,
      secondaryForeground: secondaryForeground ?? this.secondaryForeground,
      muted: muted ?? this.muted,
      mutedForeground: mutedForeground ?? this.mutedForeground,
      accent: accent ?? this.accent,
      accentForeground: accentForeground ?? this.accentForeground,
      destructive: destructive ?? this.destructive,
      destructiveForeground:
          destructiveForeground ?? this.destructiveForeground,
      border: border ?? this.border,
      input: input ?? this.input,
      ring: ring ?? this.ring,
    );
  }

  @override
  FCColors lerp(covariant ThemeExtension<FCColors>? other, double t) {
    if (other is! FCColors) return this;
    return FCColors(
      background: Color.lerp(background, other.background, t)!,
      foreground: Color.lerp(foreground, other.foreground, t)!,
      card: Color.lerp(card, other.card, t)!,
      cardForeground: Color.lerp(cardForeground, other.cardForeground, t)!,
      popover: Color.lerp(popover, other.popover, t)!,
      popoverForeground:
          Color.lerp(popoverForeground, other.popoverForeground, t)!,
      primary: Color.lerp(primary, other.primary, t)!,
      primaryForeground:
          Color.lerp(primaryForeground, other.primaryForeground, t)!,
      secondary: Color.lerp(secondary, other.secondary, t)!,
      secondaryForeground:
          Color.lerp(secondaryForeground, other.secondaryForeground, t)!,
      muted: Color.lerp(muted, other.muted, t)!,
      mutedForeground: Color.lerp(mutedForeground, other.mutedForeground, t)!,
      accent: Color.lerp(accent, other.accent, t)!,
      accentForeground:
          Color.lerp(accentForeground, other.accentForeground, t)!,
      destructive: Color.lerp(destructive, other.destructive, t)!,
      destructiveForeground:
          Color.lerp(destructiveForeground, other.destructiveForeground, t)!,
      border: Color.lerp(border, other.border, t)!,
      input: Color.lerp(input, other.input, t)!,
      ring: Color.lerp(ring, other.ring, t)!,
    );
  }
}
