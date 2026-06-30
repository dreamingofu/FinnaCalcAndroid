import 'package:flutter/material.dart';

import 'fc_colors.dart';

/// Builds the app's [ThemeData] from the shadcn token set.
///
/// Material's own colour/shape/ripple defaults are overridden aggressively
/// rather than relied upon — the `FC*` widgets are custom and the theme exists
/// mostly to back the few Material primitives we do reuse (Scaffold, text
/// selection, scrollbars) and to carry [FCColors] as an extension.
class FCTheme {
  const FCTheme._();

  static ThemeData light() => _build(FCColors.light, Brightness.light);
  static ThemeData dark() => _build(FCColors.dark, Brightness.dark);

  static ThemeData _build(FCColors c, Brightness brightness) {
    final colorScheme = ColorScheme(
      brightness: brightness,
      primary: c.primary,
      onPrimary: c.primaryForeground,
      secondary: c.secondary,
      onSecondary: c.secondaryForeground,
      error: c.destructive,
      onError: c.destructiveForeground,
      surface: c.background,
      onSurface: c.foreground,
      surfaceContainerHighest: c.muted,
      onSurfaceVariant: c.mutedForeground,
      outline: c.border,
      outlineVariant: c.border,
      tertiary: c.accent,
      onTertiary: c.accentForeground,
    );

    final baseTextTheme = (brightness == Brightness.light
            ? Typography.blackMountainView
            : Typography.whiteMountainView)
        .apply(
      bodyColor: c.foreground,
      displayColor: c.foreground,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: c.background,
      canvasColor: c.background,
      dividerColor: c.border,
      dividerTheme: DividerThemeData(
        color: c.border,
        thickness: 1,
        space: 1,
      ),
      // shadcn buttons/cards don't ripple — kill the Material ink splash globally.
      splashFactory: NoSplash.splashFactory,
      highlightColor: Colors.transparent,
      splashColor: Colors.transparent,
      hoverColor: Colors.transparent,
      textTheme: baseTextTheme,
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: c.foreground,
        selectionColor: c.primary.withValues(alpha: 0.25),
        selectionHandleColor: c.primary,
      ),
      scrollbarTheme: ScrollbarThemeData(
        thumbColor: WidgetStatePropertyAll(c.mutedForeground.withValues(alpha: 0.4)),
      ),
      extensions: [c],
    );
  }
}

/// Ergonomic access to the shadcn token set: `context.colors.primary`.
extension FCThemeContext on BuildContext {
  FCColors get colors => Theme.of(this).extension<FCColors>()!;
}
