import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/networking/api_endpoints.dart';
import '../../../core/util/formatters.dart';
import '../budget_controller.dart';

/// The AI budget advisor card, mirroring the web's `<BudgetAdvisor>`. Builds a
/// snapshot from the [BudgetController] and streams a quick/deep analysis from
/// `/api/budget-advisor`.
class BudgetAdvisor extends StatefulWidget {
  const BudgetAdvisor({super.key});

  @override
  State<BudgetAdvisor> createState() => _BudgetAdvisorState();
}

class _BudgetAdvisorState extends State<BudgetAdvisor> {
  StreamSubscription<String>? _sub;
  String _buffer = '';
  bool _loading = false;
  String? _error;
  String _depth = 'quick';

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  Map<String, dynamic> _snapshot(BudgetController ctrl) {
    final income = ctrl.monthlyIncome;
    final expenses = ctrl.monthlyExpenses;
    final net = ctrl.monthlyNet;
    final totalSaved =
        ctrl.goals.fold<double>(0, (s, g) => s + g.currentAmount);

    return {
      'budgetType': ctrl.budgetType.name,
      'monthlyIncome': income.round(),
      'monthlyExpenses': expenses.round(),
      'monthlyNet': net.round(),
      'savingsRatePct':
          income > 0 ? ((net / income * 100) * 10).round() / 10 : 0,
      'expenseByCategory': ctrl.expenseByCategory
          .map((c) => {
                'category': c.name,
                'amount': c.value.round(),
                'pctOfIncome':
                    income > 0 ? (c.value / income * 1000).round() / 10 : null,
              })
          .toList(),
      'incomeByCategory': ctrl.incomeByCategory
          .map((c) => {'source': c.name, 'amount': c.value.round()})
          .toList(),
      'savingsGoals': ctrl.goals
          .map((g) => {
                'name': g.name,
                'target': g.targetAmount,
                'saved': g.currentAmount,
                'monthlyContribution': g.monthlyContribution,
                'targetDate': g.targetDate,
                'pctComplete': g.targetAmount > 0
                    ? (g.currentAmount / g.targetAmount * 100).round()
                    : 0,
              })
          .toList(),
      'totalSavedAcrossGoals': totalSaved.round(),
      'emergencyFundMonthsCovered':
          expenses > 0 ? ((totalSaved / expenses) * 10).round() / 10 : 0,
    };
  }

  Future<void> _run(String depth) async {
    if (_loading) return;
    final ctrl = context.read<BudgetController>();
    final api = context.read<ApiClient>();
    final snapshot = _snapshot(ctrl);

    await _sub?.cancel();
    setState(() {
      _depth = depth;
      _loading = true;
      _error = null;
      _buffer = '';
    });

    try {
      final stream = await api.streamText(
        ApiEndpoints.budgetAdvisor,
        body: {
          'snapshot': snapshot,
          'depth': depth,
          'messages': [
            {
              'role': 'user',
              'content': depth == 'deep'
                  ? 'Give me a full, deep analysis of my budget with your best '
                      'personalized recommendations.'
                  : 'Give me a quick, concise summary of my budget with the top '
                      'quick wins.',
            },
          ],
        },
      );
      _sub = stream.listen(
        (chunk) {
          if (!mounted) return;
          setState(() => _buffer += chunk);
        },
        onError: (Object e) {
          if (!mounted) return;
          setState(() {
            _error = e is ApiException ? e.message : 'Something went wrong.';
            _loading = false;
          });
        },
        onDone: () {
          if (!mounted) return;
          setState(() {
            if (_buffer.trim().isEmpty && _error == null) {
              _error = 'No response received. Please try again.';
            }
            _loading = false;
          });
        },
        cancelOnError: true,
      );
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    final ctrl = context.watch<BudgetController>();
    final hasData = ctrl.monthlyIncome != 0 || ctrl.monthlyExpenses != 0;
    final topExpense =
        ctrl.expenseByCategory.isNotEmpty ? ctrl.expenseByCategory.first : null;

    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: FCPalette.blue600,
                    borderRadius: FCRadii.smAll,
                  ),
                  child: const Icon(Icons.auto_awesome,
                      size: 16, color: Colors.white),
                ),
                const SizedBox(width: 10),
                const Expanded(child: FCCardTitle('Budget Analysis')),
              ],
            ),
            FCCardDescription(
                'Personalized insights for your ${ctrl.budgetType.label.toLowerCase()} budget'),
          ]),
          FCCardContent(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (!hasData) ...[
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 24),
                    child: Column(
                      children: [
                        Icon(Icons.trending_up,
                            size: 36, color: c.mutedForeground),
                        const SizedBox(height: 12),
                        Text(
                          'Add some income and expenses in the Budget tab, then '
                          'come back for a personalized analysis.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                              fontSize: FCFontSizes.sm,
                              color: c.mutedForeground,
                              height: 1.5),
                        ),
                      ],
                    ),
                  ),
                ] else ...[
                  if (topExpense != null)
                    Text.rich(
                      TextSpan(
                        text: 'Top expense: ',
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground),
                        children: [
                          TextSpan(
                            text: topExpense.name,
                            style: TextStyle(
                                fontWeight: FCFontWeights.semibold,
                                color: c.foreground),
                          ),
                          TextSpan(
                              text:
                                  ' · ${Fmt.money0(topExpense.value)}/mo'),
                        ],
                      ),
                    ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: FCButton(
                          label: 'Quick analysis',
                          icon: const Icon(Icons.bolt_outlined),
                          onPressed: _loading ? null : () => _run('quick'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: FCButton(
                          label: 'Deep analysis',
                          variant: FCButtonVariant.outline,
                          icon: const Icon(Icons.auto_awesome),
                          onPressed: _loading ? null : () => _run('deep'),
                        ),
                      ),
                    ],
                  ),
                  if (_loading || _buffer.isNotEmpty || _error != null) ...[
                    const SizedBox(height: 16),
                    _output(context),
                  ],
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _output(BuildContext context) {
    final c = context.colors;
    return Container(
      width: double.infinity,
      constraints: const BoxConstraints(maxHeight: 420),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.card),
        borderRadius: FCRadii.lgAll,
      ),
      child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_buffer.isNotEmpty)
              Text(
                _buffer,
                style: TextStyle(
                    fontSize: FCFontSizes.sm, color: c.foreground, height: 1.5),
              ),
            if (_loading) ...[
              if (_buffer.isNotEmpty) const SizedBox(height: 12),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _depth == 'deep'
                        ? 'Running a deep analysis…'
                        : 'Analyzing your budget…',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground),
                  ),
                ],
              ),
            ],
            if (_error != null) ...[
              if (_buffer.isNotEmpty) const SizedBox(height: 12),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.error_outline,
                      size: 16, color: FCPalette.red600),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(_error!,
                        style: const TextStyle(
                            fontSize: FCFontSizes.xs, color: FCPalette.red600)),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}
