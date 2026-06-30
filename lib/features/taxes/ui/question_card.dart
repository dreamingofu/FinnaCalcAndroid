import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../core/design_system/design_system.dart';
import '../../../shared/widgets/fc_select_field.dart';
import '../engine/types/question.dart';

/// Renders a single interview question by its input type. Mirrors
/// `ui/QuestionCard.tsx`:
/// - boolean -> a labeled toggle row with a [Switch]
/// - select  -> [FCSelectField]
/// - dollar  -> numeric [FCTextField] with a "$" prefix
/// - integer -> numeric [FCTextField]
/// - text    -> plain [FCTextField]
///
/// Reads from [value]; reports changes through [onChanged] (-> setAnswer).
class QuestionCard extends StatefulWidget {
  const QuestionCard({
    super.key,
    required this.question,
    required this.value,
    required this.onChanged,
  });

  final Question question;
  final AnswerValue? value;
  final ValueChanged<Object?> onChanged;

  @override
  State<QuestionCard> createState() => _QuestionCardState();
}

class _QuestionCardState extends State<QuestionCard> {
  TextEditingController? _controller;
  FocusNode? _focusNode;
  bool _focused = false;

  bool get _isNumeric =>
      widget.question.inputType == 'dollar' ||
      widget.question.inputType == 'integer';

  bool get _isText =>
      widget.question.inputType == 'text' || _isNumeric;

  String _valueText(Object? v) {
    if (v == null) return '';
    if (v is num) {
      // Mirror JS `String(value)` — drop a trailing ".0" so 1000.0 -> "1000".
      if (v is double && v == v.truncateToDouble()) {
        return v.toInt().toString();
      }
      return v.toString();
    }
    return v.toString();
  }

  @override
  void initState() {
    super.initState();
    if (_isText) {
      _controller = TextEditingController(text: _valueText(widget.value));
      _focusNode = FocusNode()..addListener(_onFocusChange);
    }
  }

  void _onFocusChange() {
    final node = _focusNode;
    if (node != null && _focused != node.hasFocus) {
      setState(() => _focused = node.hasFocus);
    }
  }

  @override
  void didUpdateWidget(covariant QuestionCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Keep the field in sync when the answer changes externally (e.g. reset),
    // without clobbering an in-progress edit.
    final ctrl = _controller;
    if (ctrl != null) {
      final incoming = _valueText(widget.value);
      if (incoming != ctrl.text && !_focused) {
        ctrl.text = incoming;
      }
    }
  }

  @override
  void dispose() {
    _controller?.dispose();
    _focusNode?.removeListener(_onFocusChange);
    _focusNode?.dispose();
    super.dispose();
  }

  void _onNumericChanged(String raw) {
    if (raw.trim().isEmpty) {
      widget.onChanged(0);
      return;
    }
    final parsed = double.tryParse(raw.trim());
    if (parsed == null || !parsed.isFinite) {
      widget.onChanged(0);
      return;
    }
    widget.onChanged(parsed);
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final q = widget.question;

    Widget container({required List<Widget> children}) => Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            border: Border.all(color: c.border),
            borderRadius: FCRadii.lgAll,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: children,
          ),
        );

    Widget label() => Text(
          q.text,
          style: TextStyle(
            fontSize: FCFontSizes.sm,
            fontWeight: FCFontWeights.medium,
            color: c.foreground,
            height: 1.4,
          ),
        );

    Widget? help() => q.helpText == null
        ? null
        : Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              q.helpText!,
              style: TextStyle(
                fontSize: FCFontSizes.xs,
                color: c.mutedForeground,
                height: 1.4,
              ),
            ),
          );

    if (q.inputType == 'boolean') {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          border: Border.all(color: c.border),
          borderRadius: FCRadii.lgAll,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  label(),
                  if (help() != null) help()!,
                ],
              ),
            ),
            const SizedBox(width: 16),
            Switch(
              value: widget.value == true,
              onChanged: (v) => widget.onChanged(v),
            ),
          ],
        ),
      );
    }

    if (q.inputType == 'select') {
      return container(children: [
        label(),
        if (help() != null) help()!,
        const SizedBox(height: 8),
        FCSelectField<String>(
          value: widget.value is String ? widget.value as String : null,
          hintText: 'Select…',
          items: [
            for (final o in (q.options ?? const <QuestionOption>[]))
              FCSelectItem(o.value, o.label),
          ],
          onChanged: (v) => widget.onChanged(v),
        ),
      ]);
    }

    // dollar / integer / text
    final keyboardType = q.inputType == 'dollar'
        ? const TextInputType.numberWithOptions(decimal: true)
        : q.inputType == 'integer'
            ? const TextInputType.numberWithOptions(decimal: false)
            : TextInputType.text;

    final formatters = <TextInputFormatter>[];
    if (_isNumeric) {
      final allowNeg = q.allowNegative == true;
      if (q.inputType == 'integer') {
        formatters.add(FilteringTextInputFormatter.allow(
            RegExp(allowNeg ? r'[0-9-]' : r'[0-9]')));
      } else {
        formatters.add(FilteringTextInputFormatter.allow(
            RegExp(allowNeg ? r'[0-9.\-]' : r'[0-9.]')));
      }
    }

    return container(children: [
      label(),
      if (help() != null) help()!,
      const SizedBox(height: 8),
      FCTextField(
        controller: _controller,
        focusNode: _focusNode,
        keyboardType: keyboardType,
        inputFormatters: formatters.isEmpty ? null : formatters,
        hintText: q.placeholder ?? (q.inputType == 'dollar' ? '0.00' : ''),
        prefix: q.inputType == 'dollar' ? const Text('\$') : null,
        onChanged: (raw) {
          if (_isNumeric) {
            _onNumericChanged(raw);
          } else {
            widget.onChanged(raw);
          }
        },
      ),
    ]);
  }
}
