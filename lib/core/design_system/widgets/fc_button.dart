import 'package:flutter/material.dart';

import '../fc_colors.dart';
import '../fc_theme.dart';
import '../fc_tokens.dart';

/// Visual variants — 1:1 with `components/ui/button.tsx`'s `buttonVariants`.
enum FCButtonVariant { primary, destructive, outline, secondary, ghost, link }

/// Sizes — 1:1 with `buttonVariants.size`. (`primary` == cva `default`.)
enum FCButtonSize { md, sm, lg, icon }

/// A custom button matching shadcn/ui's `<Button>` — no Material elevation or
/// ripple. Hover states from the web (`hover:bg-primary/90`, `/80`) are mapped
/// to the pressed state for touch via alpha-composited colours.
class FCButton extends StatefulWidget {
  const FCButton({
    super.key,
    this.label,
    this.onPressed,
    this.variant = FCButtonVariant.primary,
    this.size = FCButtonSize.md,
    this.icon,
    this.trailingIcon,
    this.fullWidth = false,
    this.loading = false,
  }) : assert(label != null || icon != null,
            'FCButton needs a label or an icon');

  final String? label;
  final VoidCallback? onPressed;
  final FCButtonVariant variant;
  final FCButtonSize size;

  /// Leading icon (rendered at 16px, matching `[&_svg]:size-4`).
  final Widget? icon;

  /// Trailing icon (16px).
  final Widget? trailingIcon;

  /// Stretch to the parent's width (the web `w-full`).
  final bool fullWidth;

  /// Shows a spinner and disables interaction.
  final bool loading;

  bool get _enabled => onPressed != null && !loading;

  @override
  State<FCButton> createState() => _FCButtonState();
}

class _FCButtonState extends State<FCButton> {
  bool _pressed = false;

  void _setPressed(bool value) {
    if (_pressed != value) setState(() => _pressed = value);
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final style = _resolveStyle(c, _pressed && widget._enabled);
    final metrics = _sizeMetrics(widget.size);

    final textStyle = TextStyle(
      fontSize: FCFontSizes.sm,
      fontWeight: FCFontWeights.medium,
      color: style.foreground,
      decoration:
          widget.variant == FCButtonVariant.link && _pressed && widget._enabled
              ? TextDecoration.underline
              : TextDecoration.none,
      decorationColor: style.foreground,
      height: 1.0,
    );

    Widget content;
    if (widget.loading) {
      content = SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          valueColor: AlwaysStoppedAnimation<Color>(style.foreground),
        ),
      );
    } else {
      final children = <Widget>[
        if (widget.icon != null) widget.icon!,
        if (widget.label != null) Flexible(child: Text(widget.label!, maxLines: 1, overflow: TextOverflow.ellipsis)),
        if (widget.trailingIcon != null) widget.trailingIcon!,
      ];
      content = Row(
        mainAxisSize: widget.fullWidth ? MainAxisSize.max : MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) const SizedBox(width: 8), // gap-2
            children[i],
          ],
        ],
      );
    }

    final button = AnimatedContainer(
      duration: FCDurations.transition,
      curve: Curves.easeOut,
      height: metrics.height,
      width: widget.size == FCButtonSize.icon
          ? metrics.height
          : (widget.fullWidth ? double.infinity : null),
      padding: widget.size == FCButtonSize.icon
          ? EdgeInsets.zero
          : EdgeInsets.symmetric(horizontal: metrics.horizontalPadding),
      decoration: BoxDecoration(
        color: style.background,
        borderRadius: FCRadii.mdAll,
        border: style.border != null
            ? Border.all(color: style.border!, width: 1)
            : null,
      ),
      child: Center(
        widthFactor: widget.fullWidth ? null : 1,
        child: IconTheme.merge(
          data: IconThemeData(color: style.foreground, size: 16),
          child: DefaultTextStyle.merge(style: textStyle, child: content),
        ),
      ),
    );

    return Opacity(
      opacity: widget._enabled ? 1.0 : 0.5,
      child: Semantics(
        button: true,
        enabled: widget._enabled,
        label: widget.label,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: widget._enabled ? widget.onPressed : null,
          onTapDown: widget._enabled ? (_) => _setPressed(true) : null,
          onTapUp: widget._enabled ? (_) => _setPressed(false) : null,
          onTapCancel: widget._enabled ? () => _setPressed(false) : null,
          child: MouseRegion(
            cursor: widget._enabled
                ? SystemMouseCursors.click
                : SystemMouseCursors.basic,
            child: button,
          ),
        ),
      ),
    );
  }

  _ButtonStyle _resolveStyle(FCColors c, bool pressed) {
    switch (widget.variant) {
      case FCButtonVariant.primary:
        // default: bg-primary text-primary-foreground hover:bg-primary/90
        return _ButtonStyle(
          background: pressed ? c.primary.withValues(alpha: 0.9) : c.primary,
          foreground: c.primaryForeground,
        );
      case FCButtonVariant.destructive:
        return _ButtonStyle(
          background:
              pressed ? c.destructive.withValues(alpha: 0.9) : c.destructive,
          foreground: c.destructiveForeground,
        );
      case FCButtonVariant.outline:
        // border border-input bg-background hover:bg-accent hover:text-accent-foreground
        return _ButtonStyle(
          background: pressed ? c.accent : c.background,
          foreground: pressed ? c.accentForeground : c.foreground,
          border: c.input,
        );
      case FCButtonVariant.secondary:
        return _ButtonStyle(
          background:
              pressed ? c.secondary.withValues(alpha: 0.8) : c.secondary,
          foreground: c.secondaryForeground,
        );
      case FCButtonVariant.ghost:
        return _ButtonStyle(
          background: pressed ? c.accent : Colors.transparent,
          foreground: pressed ? c.accentForeground : c.foreground,
        );
      case FCButtonVariant.link:
        return _ButtonStyle(
          background: Colors.transparent,
          foreground: c.primary,
        );
    }
  }

  _SizeMetrics _sizeMetrics(FCButtonSize size) {
    switch (size) {
      case FCButtonSize.md:
        return const _SizeMetrics(height: 40, horizontalPadding: 16);
      case FCButtonSize.sm:
        return const _SizeMetrics(height: 36, horizontalPadding: 12);
      case FCButtonSize.lg:
        return const _SizeMetrics(height: 44, horizontalPadding: 32);
      case FCButtonSize.icon:
        return const _SizeMetrics(height: 40, horizontalPadding: 0);
    }
  }
}

class _ButtonStyle {
  const _ButtonStyle({
    required this.background,
    required this.foreground,
    this.border,
  });

  final Color background;
  final Color foreground;
  final Color? border;
}

class _SizeMetrics {
  const _SizeMetrics({required this.height, required this.horizontalPadding});

  final double height;
  final double horizontalPadding;
}
