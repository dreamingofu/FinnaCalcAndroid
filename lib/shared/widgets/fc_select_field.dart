import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

class FCSelectItem<T> {
  const FCSelectItem(this.value, this.label);
  final T value;
  final String label;
}

/// A custom select matching the look of [FCTextField] (the shadcn `<Select>`):
/// a bordered field showing the current value + chevron, opening a bottom-sheet
/// list of options.
class FCSelectField<T> extends StatelessWidget {
  const FCSelectField({
    super.key,
    required this.value,
    required this.items,
    required this.onChanged,
    this.label,
    this.hintText = 'Select…',
    this.enabled = true,
  });

  final T? value;
  final List<FCSelectItem<T>> items;
  final ValueChanged<T> onChanged;
  final String? label;
  final String hintText;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final selected = items.where((i) => i.value == value).toList();
    final text = selected.isNotEmpty ? selected.first.label : hintText;
    final isPlaceholder = selected.isEmpty;

    final field = GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: enabled ? () => _open(context) : null,
      child: Container(
        height: 40,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        decoration: BoxDecoration(
          color: c.background,
          borderRadius: FCRadii.mdAll,
          border: Border.all(color: c.input),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                text,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: FCFontSizes.base,
                  color: isPlaceholder ? c.mutedForeground : c.foreground,
                ),
              ),
            ),
            Icon(Icons.keyboard_arrow_down,
                size: 18, color: c.mutedForeground),
          ],
        ),
      ),
    );

    if (label == null) {
      return Opacity(opacity: enabled ? 1 : 0.5, child: field);
    }
    return Opacity(
      opacity: enabled ? 1 : 0.5,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label!,
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.medium,
                  color: c.foreground)),
          const SizedBox(height: 8),
          field,
        ],
      ),
    );
  }

  void _open(BuildContext context) {
    final c = context.colors;
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: c.popover,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (sheetContext) {
        return SafeArea(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(sheetContext).size.height * 0.7,
            ),
            child: ListView(
              shrinkWrap: true,
              padding: const EdgeInsets.only(bottom: 12),
              children: [
                if (label != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                    child: Text(label!,
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            fontWeight: FCFontWeights.semibold,
                            color: c.mutedForeground)),
                  ),
                for (final item in items)
                  InkWell(
                    onTap: () {
                      Navigator.of(sheetContext).pop();
                      onChanged(item.value);
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 20, vertical: 14),
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(item.label,
                                style: TextStyle(
                                    fontSize: FCFontSizes.base,
                                    color: c.popoverForeground)),
                          ),
                          if (item.value == value)
                            Icon(Icons.check,
                                size: 18, color: FCPalette.blue600),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}
