import 'package:flutter/material.dart';

import '../../core/design_system/design_system.dart';

/// A segmented control matching shadcn/ui's `<TabsList>` / `<TabsTrigger>`:
/// a `bg-muted` rounded track with equal-width triggers; the active trigger is
/// `bg-background` with `shadow-sm`.
class FCSegmentedTabs extends StatelessWidget {
  const FCSegmentedTabs({
    super.key,
    required this.tabs,
    required this.index,
    required this.onChanged,
  });

  final List<String> tabs;
  final int index;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Container(
      height: 40,
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: c.muted,
        borderRadius: FCRadii.mdAll,
      ),
      child: Row(
        children: [
          for (var i = 0; i < tabs.length; i++)
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onChanged(i),
                child: AnimatedContainer(
                  duration: FCDurations.transition,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: i == index ? c.background : Colors.transparent,
                    borderRadius: FCRadii.smAll,
                    boxShadow: i == index ? kShadowSm : null,
                  ),
                  child: Text(
                    tabs[i],
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.medium,
                      color: i == index ? c.foreground : c.mutedForeground,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
