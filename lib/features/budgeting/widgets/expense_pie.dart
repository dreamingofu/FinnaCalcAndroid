import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../budget_controller.dart';

/// A pie chart of monthly category totals (mirrors the web's expense/income
/// summary chart), with a coloured legend. Shows a muted note when empty.
class ExpensePie extends StatelessWidget {
  const ExpensePie({
    super.key,
    required this.data,
    this.emptyNote = 'No expenses yet.',
  });

  final List<CategoryTotal> data;
  final String emptyNote;

  /// The recharts palette from the web, cycled per slice.
  static const List<Color> colors = [
    Color(0xFF0088FE),
    Color(0xFF00C49F),
    Color(0xFFFFBB28),
    Color(0xFFFF8042),
    Color(0xFFAF19FF),
    Color(0xFFFF1943),
    Color(0xFF19D7FF),
  ];

  @override
  Widget build(BuildContext context) {
    final c = context.colors;

    if (data.isEmpty) {
      return SizedBox(
        height: 200,
        child: Center(
          child: Text(
            emptyNote,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: FCFontSizes.sm,
              color: c.mutedForeground,
            ),
          ),
        ),
      );
    }

    final total = data.fold<double>(0, (s, e) => s + e.value);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          height: 220,
          child: PieChart(
            PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 48,
              sections: [
                for (var i = 0; i < data.length; i++)
                  PieChartSectionData(
                    value: data[i].value,
                    color: colors[i % colors.length],
                    radius: 56,
                    title: total > 0
                        ? '${(data[i].value / total * 100).toStringAsFixed(0)}%'
                        : '',
                    titleStyle: const TextStyle(
                      fontSize: FCFontSizes.xs,
                      fontWeight: FCFontWeights.semibold,
                      color: Colors.white,
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 16,
          runSpacing: 8,
          children: [
            for (var i = 0; i < data.length; i++)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 12,
                    height: 12,
                    decoration: BoxDecoration(
                      color: colors[i % colors.length],
                      borderRadius: BorderRadius.circular(3),
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    '${data[i].name} · ${Fmt.money0(data[i].value)}',
                    style: TextStyle(
                      fontSize: FCFontSizes.xs,
                      color: c.foreground,
                    ),
                  ),
                ],
              ),
          ],
        ),
      ],
    );
  }
}
