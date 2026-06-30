import 'package:flutter/material.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../engine/types/result.dart';

/// Live running refund / amount-owed estimate. Updates as the user answers
/// questions. Mirrors `ui/RefundMeter.tsx`: refunds emphasize with the primary
/// colour, balances due with the destructive colour.
class RefundMeter extends StatelessWidget {
  const RefundMeter({super.key, required this.result});

  final TaxCalculationResult result;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final owes = result.owes;
    final amount = result.refundOrOwed.abs();
    final headlineColor = owes ? c.destructive : c.primary;
    final state = result.state;

    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            // Headline
            Column(
              children: [
                Text(
                  owes ? 'Estimated amount you owe' : 'Estimated federal refund',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    fontWeight: FCFontWeights.medium,
                    color: c.mutedForeground,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  Fmt.money0(amount),
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: FCFontSizes.xl4,
                    fontWeight: FCFontWeights.bold,
                    color: headlineColor,
                    height: 1.05,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Federal estimate',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    color: c.mutedForeground,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Metric grid (2 columns)
            FCResultGrid(
              columns: 2,
              spacing: 8,
              runSpacing: 12,
              children: [
                _MeterRow(label: 'Total income', value: result.totalIncome),
                _MeterRow(label: 'AGI', value: result.agi),
                _MeterRow(
                  label: result.deductionUsed == 'itemized'
                      ? 'Itemized ded.'
                      : 'Standard ded.',
                  value: result.deductionAmount,
                ),
                _MeterRow(label: 'Taxable income', value: result.taxableIncome),
                _MeterRow(label: 'Tax before credits', value: result.regularTax),
                _MeterRow(
                  label: 'Credits',
                  value: result.totalNonrefundableCredits +
                      result.totalRefundableCredits,
                ),
                _MeterRow(label: 'Total tax', value: result.totalTax),
                _MeterRow(label: 'Payments', value: result.totalPayments),
              ],
            ),

            // State section
            if (state != null && state.supported && state.hasIncomeTax) ...[
              const SizedBox(height: 12),
              _topBorder(c),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '${state.name} ${state.refundOrOwed < 0 ? 'owed' : 'refund'}',
                      style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        color: c.mutedForeground,
                      ),
                    ),
                  ),
                  Text(
                    Fmt.money0(state.refundOrOwed.abs()),
                    style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.medium,
                      color: state.refundOrOwed < 0 ? c.destructive : c.primary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'State tax ${Fmt.money0(state.tax)}',
                style: TextStyle(
                  fontSize: FCFontSizes.xs,
                  color: c.mutedForeground,
                ),
              ),
            ] else if (state != null && !state.hasIncomeTax) ...[
              const SizedBox(height: 12),
              _topBorder(c),
              const SizedBox(height: 12),
              Text(
                '${state.name}: no state income tax. 🎉',
                style: TextStyle(
                  fontSize: FCFontSizes.xs,
                  color: c.mutedForeground,
                ),
              ),
            ],

            // Rate footer
            const SizedBox(height: 12),
            _topBorder(c),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Marginal rate: ${Fmt.pct(result.marginalRate, 0)}',
                  style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    color: c.mutedForeground,
                  ),
                ),
                Text(
                  'Effective rate: ${Fmt.pct(result.effectiveRate, 1)}',
                  style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    color: c.mutedForeground,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _topBorder(FCColors c) =>
      Container(height: 1, color: c.border);
}

class _MeterRow extends StatelessWidget {
  const _MeterRow({required this.label, required this.value});

  final String label;
  final double value;

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(fontSize: FCFontSizes.sm, color: c.mutedForeground),
        ),
        const SizedBox(height: 2),
        Text(
          Fmt.money2(value),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(
            fontSize: FCFontSizes.sm,
            fontWeight: FCFontWeights.medium,
            color: c.foreground,
          ),
        ),
      ],
    );
  }
}
