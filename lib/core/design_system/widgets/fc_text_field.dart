import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../fc_theme.dart';
import '../fc_tokens.dart';

/// A custom text field matching `components/ui/input.tsx`:
/// `h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-base`
/// with a `focus-visible:ring-2 ring-ring ring-offset-2` focus ring.
///
/// `label` / `helperText` / `errorText` are additive conveniences (the web
/// pairs `<Input>` with a separate `<Label>`); they style to the design system.
class FCTextField extends StatefulWidget {
  const FCTextField({
    super.key,
    this.controller,
    this.focusNode,
    this.hintText,
    this.label,
    this.helperText,
    this.errorText,
    this.keyboardType,
    this.textInputAction,
    this.inputFormatters,
    this.obscureText = false,
    this.enabled = true,
    this.autofocus = false,
    this.maxLines = 1,
    this.minLines,
    this.onChanged,
    this.onSubmitted,
    this.prefix,
    this.suffix,
  });

  final TextEditingController? controller;
  final FocusNode? focusNode;
  final String? hintText;
  final String? label;
  final String? helperText;
  final String? errorText;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final List<TextInputFormatter>? inputFormatters;
  final bool obscureText;
  final bool enabled;
  final bool autofocus;
  final int? maxLines;
  final int? minLines;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final Widget? prefix;
  final Widget? suffix;

  @override
  State<FCTextField> createState() => _FCTextFieldState();
}

class _FCTextFieldState extends State<FCTextField> {
  late FocusNode _focusNode;
  bool _ownsFocusNode = false;
  bool _focused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = widget.focusNode ?? FocusNode();
    _ownsFocusNode = widget.focusNode == null;
    _focusNode.addListener(_onFocusChange);
  }

  @override
  void didUpdateWidget(covariant FCTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.focusNode != oldWidget.focusNode) {
      _focusNode.removeListener(_onFocusChange);
      if (_ownsFocusNode) _focusNode.dispose();
      _focusNode = widget.focusNode ?? FocusNode();
      _ownsFocusNode = widget.focusNode == null;
      _focusNode.addListener(_onFocusChange);
    }
  }

  void _onFocusChange() {
    if (_focused != _focusNode.hasFocus) {
      setState(() => _focused = _focusNode.hasFocus);
    }
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    if (_ownsFocusNode) _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final hasError = widget.errorText != null;
    final isMultiline = (widget.maxLines == null) || (widget.maxLines! > 1);
    final ringColor = hasError ? c.destructive : c.ring;

    final field = AnimatedContainer(
      duration: FCDurations.transition,
      constraints: const BoxConstraints(minHeight: 40),
      decoration: BoxDecoration(
        color: c.background,
        borderRadius: FCRadii.mdAll,
        border: Border.all(
          color: hasError ? c.destructive : c.input,
          width: 1,
        ),
        boxShadow: _focused
            ? [
                // ring (outer) — painted first/behind
                BoxShadow(color: ringColor, spreadRadius: 4),
                // ring-offset gap (background) — covers the inner 2px of the ring
                BoxShadow(color: c.background, spreadRadius: 2),
              ]
            : null,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (widget.prefix != null)
            Padding(
              padding: const EdgeInsets.only(left: 12),
              child: IconTheme.merge(
                data: IconThemeData(color: c.mutedForeground, size: 16),
                child: widget.prefix!,
              ),
            ),
          Expanded(
            child: TextField(
              controller: widget.controller,
              focusNode: _focusNode,
              enabled: widget.enabled,
              autofocus: widget.autofocus,
              obscureText: widget.obscureText,
              keyboardType: widget.keyboardType,
              textInputAction: widget.textInputAction,
              inputFormatters: widget.inputFormatters,
              maxLines: widget.obscureText ? 1 : widget.maxLines,
              minLines: widget.minLines,
              onChanged: widget.onChanged,
              onSubmitted: widget.onSubmitted,
              textAlignVertical: TextAlignVertical.center,
              cursorColor: c.foreground,
              style: TextStyle(
                fontSize: FCFontSizes.base,
                color: c.foreground,
                height: 1.3,
              ),
              decoration: InputDecoration(
                isCollapsed: true,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                hintText: widget.hintText,
                hintStyle: TextStyle(
                  fontSize: FCFontSizes.base,
                  color: c.mutedForeground,
                  height: 1.3,
                ),
                contentPadding: EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: isMultiline ? 8 : 9,
                ),
              ),
            ),
          ),
          if (widget.suffix != null)
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: IconTheme.merge(
                data: IconThemeData(color: c.mutedForeground, size: 16),
                child: widget.suffix!,
              ),
            ),
        ],
      ),
    );

    if (widget.label == null &&
        widget.helperText == null &&
        widget.errorText == null) {
      return Opacity(opacity: widget.enabled ? 1.0 : 0.5, child: field);
    }

    return Opacity(
      opacity: widget.enabled ? 1.0 : 0.5,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (widget.label != null) ...[
            Text(
              widget.label!,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                fontWeight: FCFontWeights.medium,
                color: c.foreground,
              ),
            ),
            const SizedBox(height: 8),
          ],
          field,
          if (widget.errorText != null) ...[
            const SizedBox(height: 6),
            Text(
              widget.errorText!,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.destructive,
              ),
            ),
          ] else if (widget.helperText != null) ...[
            const SizedBox(height: 6),
            Text(
              widget.helperText!,
              style: TextStyle(
                fontSize: FCFontSizes.sm,
                color: c.mutedForeground,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
