import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/design_system/design_system.dart';
import '../../core/networking/api_client.dart';
import '../../core/util/formatters.dart';
import '../../core/util/parse.dart';
import '../../shared/widgets/fc_segmented_tabs.dart';
import '../../shared/widgets/fc_select_field.dart';
import '../../shared/widgets/result_widgets.dart';
import 'budget_controller.dart';
import 'models/budget_models.dart';
import 'plaid_service.dart';
import 'widgets/budget_advisor.dart';
import 'widgets/debt_card.dart';
import 'widgets/expense_pie.dart';

class BudgetingScreen extends StatefulWidget {
  const BudgetingScreen({super.key});

  @override
  State<BudgetingScreen> createState() => _BudgetingScreenState();
}

class _BudgetingScreenState extends State<BudgetingScreen> {
  int _tab = 0;
  bool _importing = false;

  // Add-item form state.
  BudgetEntryType _type = BudgetEntryType.expense;
  BudgetFrequency _frequency = BudgetFrequency.monthly;
  String? _category;
  bool _isFixed = false;
  final _description = TextEditingController();
  final _amount = TextEditingController();

  @override
  void dispose() {
    _description.dispose();
    _amount.dispose();
    super.dispose();
  }

  void _snack(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _bankActions(BudgetController ctrl) async {
    if (_importing) return;
    setState(() => _importing = true);
    try {
      final txns = await context.read<PlaidService>().importTransactions();
      if (txns == null) {
        _snack('Bank connection cancelled.');
      } else {
        ctrl.importPlaidTransactions(txns);
        _snack('Imported ${txns.length} transactions to your budget history.');
      }
    } on ApiException catch (e) {
      _snack(e.message);
    } finally {
      if (mounted) setState(() => _importing = false);
    }
  }

  void _addItem(BudgetController ctrl) {
    final category = _category;
    final amount = parseNum(_amount.text);
    if (category == null || category.isEmpty) {
      _snack('Please choose a category.');
      return;
    }
    if (amount <= 0) {
      _snack('Please enter an amount greater than zero.');
      return;
    }
    ctrl.addItem(
      category: category,
      description: _description.text.trim(),
      amount: amount,
      frequency: _frequency,
      type: _type,
      isFixed: _isFixed,
    );
    setState(() {
      _description.clear();
      _amount.clear();
      _isFixed = false;
      _category = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final ctrl = context.watch<BudgetController>();
    final c = context.colors;
    final title = ctrl.budgetType == BudgetType.personal
        ? 'Personal Budget Planner'
        : 'Business Budget Planner';

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 40),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 896),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Title + Bank Actions
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          title,
                          style: TextStyle(
                            fontSize: FCFontSizes.xl3,
                            fontWeight: FCFontWeights.bold,
                            color: c.foreground,
                            letterSpacing: -0.5,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Take control of your finances with our '
                          'comprehensive budgeting tool.',
                          style: TextStyle(
                            fontSize: FCFontSizes.base,
                            color: c.mutedForeground,
                            height: 1.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  FCButton(
                    label: 'Bank Actions',
                    variant: FCButtonVariant.outline,
                    size: FCButtonSize.sm,
                    icon: const Icon(Icons.account_balance_outlined),
                    loading: _importing,
                    onPressed: () => _bankActions(ctrl),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Personal / Business toggle
              FCSegmentedTabs(
                tabs: const ['Personal', 'Business'],
                index: ctrl.budgetType == BudgetType.personal ? 0 : 1,
                onChanged: (i) => ctrl.setBudgetType(
                    i == 0 ? BudgetType.personal : BudgetType.business),
              ),
              const SizedBox(height: 20),

              // Summary cards
              _summary(context, ctrl),
              const SizedBox(height: 20),

              // Section tabs
              FCSegmentedTabs(
                tabs: const ['Budget', 'Analysis', 'Goals', 'Credit', 'History'],
                index: _tab,
                onChanged: (i) => setState(() => _tab = i),
              ),
              const SizedBox(height: 20),

              ..._tabContent(context, ctrl),
            ],
          ),
        ),
      ),
    );
  }

  Widget _summary(BuildContext context, BudgetController ctrl) {
    final net = ctrl.monthlyNet;
    final rate = ctrl.savingsRate;
    return FCResultGrid(columns: 2, children: [
      _summaryCard(context, 'Monthly Income', Fmt.money2(ctrl.monthlyIncome),
          FCPalette.green600, Icons.trending_up),
      _summaryCard(context, 'Monthly Expenses', Fmt.money2(ctrl.monthlyExpenses),
          FCPalette.red600, Icons.trending_down),
      _summaryCard(context, 'Net Income', Fmt.money2(net),
          net >= 0 ? FCPalette.green600 : FCPalette.red600, Icons.attach_money),
      _summaryCard(
          context,
          'Savings Rate',
          rate == null ? '—' : Fmt.pct(rate, 1),
          FCPalette.blue600,
          Icons.adjust),
    ]);
  }

  Widget _summaryCard(BuildContext context, String label, String value,
      Color color, IconData icon) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(label,
                      style: TextStyle(
                          fontSize: FCFontSizes.sm,
                          color: c.mutedForeground)),
                  const SizedBox(height: 4),
                  Text(value,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: FCFontSizes.xl2,
                          fontWeight: FCFontWeights.bold,
                          color: color)),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Icon(icon, size: 28, color: color),
          ],
        ),
      ),
    );
  }

  List<Widget> _tabContent(BuildContext context, BudgetController ctrl) {
    switch (_tab) {
      case 0:
        return _budgetTab(context, ctrl);
      case 1:
        return _analysisTab(context, ctrl);
      case 2:
        return _goalsTab(context, ctrl);
      case 3:
        return const [DebtCard()];
      default:
        return _historyTab(context, ctrl);
    }
  }

  // ── Budget tab ────────────────────────────────────────────────────────────

  List<Widget> _budgetTab(BuildContext context, BudgetController ctrl) {
    final c = context.colors;
    final categories = BudgetCategories.forType(ctrl.budgetType, _type);
    final grouped = <String, List<BudgetItem>>{};
    for (final item in ctrl.items) {
      grouped.putIfAbsent(item.category, () => []).add(item);
    }

    return [
      // Add-item form
      FCCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            const FCCardHeader(children: [
              FCCardTitle('Add Income or Expense'),
              FCCardDescription('Track your financial inflows and outflows.'),
            ]),
            FCCardContent(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  FCResultGrid(columns: 2, children: [
                    FCSelectField<BudgetEntryType>(
                      label: 'Type',
                      value: _type,
                      items: [
                        for (final t in BudgetEntryType.values)
                          FCSelectItem(t, t.label),
                      ],
                      onChanged: (v) => setState(() {
                        _type = v;
                        _category = null;
                      }),
                    ),
                    FCSelectField<BudgetFrequency>(
                      label: 'Frequency',
                      value: _frequency,
                      items: [
                        for (final f in BudgetFrequency.values)
                          FCSelectItem(f, f.label),
                      ],
                      onChanged: (v) => setState(() => _frequency = v),
                    ),
                  ]),
                  const SizedBox(height: 16),
                  FCSelectField<String>(
                    label: 'Category',
                    value: _category,
                    hintText: 'Select category',
                    items: [
                      for (final cat in categories) FCSelectItem(cat, cat),
                    ],
                    onChanged: (v) => setState(() => _category = v),
                  ),
                  const SizedBox(height: 16),
                  FCTextField(
                    controller: _description,
                    label: 'Description',
                    hintText: 'e.g., Rent, Groceries, Netflix',
                  ),
                  const SizedBox(height: 16),
                  FCTextField(
                    controller: _amount,
                    label: 'Amount (\$)',
                    hintText: '0.00',
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                  ),
                  const SizedBox(height: 16),
                  InkWell(
                    onTap: () => setState(() => _isFixed = !_isFixed),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 24,
                          height: 24,
                          child: Checkbox(
                            value: _isFixed,
                            onChanged: (v) =>
                                setState(() => _isFixed = v ?? false),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            "Fixed amount (doesn't vary month to month)",
                            style: TextStyle(
                                fontSize: FCFontSizes.sm, color: c.foreground),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  FCButton(
                    label: 'Add to Budget',
                    fullWidth: true,
                    onPressed: () => _addItem(ctrl),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      const SizedBox(height: 20),

      // Expense breakdown chart
      FCCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            const FCCardHeader(children: [
              FCCardTitle('Expense Summary'),
              FCCardDescription('A visual breakdown of your monthly expenses.'),
            ]),
            FCCardContent(
              child: ExpensePie(
                data: ctrl.expenseByCategory,
                emptyNote: 'Your expense summary chart will appear here.',
              ),
            ),
          ],
        ),
      ),
      const SizedBox(height: 20),

      // Items list
      FCCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            FCCardHeader(children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: FCCardTitle('Budget Items (${ctrl.items.length})'),
                  ),
                  FCButton(
                    label: 'Save to History',
                    variant: FCButtonVariant.outline,
                    size: FCButtonSize.sm,
                    icon: const Icon(Icons.save_outlined),
                    onPressed: ctrl.items.isEmpty
                        ? null
                        : () => _saveToHistory(ctrl),
                  ),
                ],
              ),
              FCCardDescription(
                  'All your income and expenses for your '
                  '${ctrl.budgetType.label.toLowerCase()} budget.'),
            ]),
            FCCardContent(
              child: grouped.isEmpty
                  ? Padding(
                      padding: const EdgeInsets.symmetric(vertical: 24),
                      child: Text(
                        'No budget items yet. Add your first income or '
                        'expense above!',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                            fontSize: FCFontSizes.sm,
                            color: c.mutedForeground),
                      ),
                    )
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (final entry in grouped.entries) ...[
                          Text(entry.key,
                              style: TextStyle(
                                  fontSize: FCFontSizes.lg,
                                  fontWeight: FCFontWeights.semibold,
                                  color: c.foreground)),
                          const SizedBox(height: 8),
                          for (final item in entry.value)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: _itemRow(context, ctrl, item),
                            ),
                          const SizedBox(height: 8),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
    ];
  }

  Widget _itemRow(
      BuildContext context, BudgetController ctrl, BudgetItem item) {
    final c = context.colors;
    final isIncome = item.type == BudgetEntryType.income;
    final amountColor = isIncome ? FCPalette.green600 : FCPalette.red600;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Color.alphaBlend(c.muted.withValues(alpha: 0.4), c.card),
        borderRadius: FCRadii.lgAll,
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  item.subcategory.isNotEmpty
                      ? item.subcategory
                      : 'No description',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.semibold,
                      color: FCPalette.blue600),
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Text(item.frequency.label,
                        style: TextStyle(
                            fontSize: FCFontSizes.xs,
                            color: c.mutedForeground)),
                    if (item.isFixed) ...[
                      const SizedBox(width: 8),
                      const FCBadge('Fixed',
                          variant: FCBadgeVariant.secondary),
                    ],
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '${isIncome ? '+' : '-'}${Fmt.money2(item.amount)}',
                style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    fontWeight: FCFontWeights.bold,
                    color: amountColor),
              ),
              Text('${item.monthlyAmount.toStringAsFixed(2)}/month',
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: c.mutedForeground)),
            ],
          ),
          FCButton(
            icon: const Icon(Icons.delete_outline),
            variant: FCButtonVariant.ghost,
            size: FCButtonSize.icon,
            onPressed: () => ctrl.deleteItem(item.id),
          ),
        ],
      ),
    );
  }

  Future<void> _saveToHistory(BudgetController ctrl) async {
    final nameController = TextEditingController();
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) {
        final c = dialogContext.colors;
        return AlertDialog(
          backgroundColor: c.popover,
          title: Text('Save Budget Snapshot',
              style: TextStyle(color: c.popoverForeground)),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Give your budget snapshot a name.',
                  style: TextStyle(
                      fontSize: FCFontSizes.sm, color: c.mutedForeground)),
              const SizedBox(height: 16),
              FCTextField(
                controller: nameController,
                hintText: 'e.g., Budget for this month',
                autofocus: true,
              ),
            ],
          ),
          actions: [
            FCButton(
              label: 'Cancel',
              variant: FCButtonVariant.outline,
              size: FCButtonSize.sm,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
            FCButton(
              label: 'Save Snapshot',
              size: FCButtonSize.sm,
              onPressed: () =>
                  Navigator.of(dialogContext).pop(nameController.text.trim()),
            ),
          ],
        );
      },
    );
    nameController.dispose();
    if (name == null) return;
    final now = DateTime.now();
    final defaultName =
        'Budget: ${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
    ctrl.saveToHistory(
      name.isNotEmpty ? name : defaultName,
      now.toIso8601String(),
      now.toIso8601String(),
    );
    _snack('Budget snapshot saved to history.');
  }

  // ── Analysis tab ──────────────────────────────────────────────────────────

  List<Widget> _analysisTab(BuildContext context, BudgetController ctrl) {
    final analysis = ctrl.analysis;
    return [
      const BudgetAdvisor(),
      const SizedBox(height: 20),
      if (analysis.isEmpty)
        _mutedNote(context,
            'Add income and expenses to see automated budget insights.')
      else
        for (final item in analysis) ...[
          _analysisCard(context, item),
          const SizedBox(height: 12),
        ],
    ];
  }

  Widget _analysisCard(BuildContext context, AnalysisItem item) {
    final (bg, fg) = switch (item.tone) {
      AnalysisTone.destructive => (FCPalette.red50, FCPalette.red700),
      AnalysisTone.warning => (FCPalette.yellow50, FCPalette.yellow800),
      AnalysisTone.success => (FCPalette.green50, FCPalette.green700),
      AnalysisTone.info => (FCPalette.blue50, FCPalette.blue700),
    };
    final icon = switch (item.tone) {
      AnalysisTone.destructive => Icons.error_outline,
      AnalysisTone.warning => Icons.warning_amber_outlined,
      AnalysisTone.success => Icons.check_circle_outline,
      AnalysisTone.info => Icons.lightbulb_outline,
    };
    return FCCalloutBanner(
      background: bg,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: fg),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(item.title,
                    style: TextStyle(
                        fontSize: FCFontSizes.sm,
                        fontWeight: FCFontWeights.semibold,
                        color: fg)),
                const SizedBox(height: 2),
                Text(item.message,
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: fg, height: 1.4)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ── Goals tab ─────────────────────────────────────────────────────────────

  List<Widget> _goalsTab(BuildContext context, BudgetController ctrl) {
    return [
      _AddGoalForm(onAdd: (name, target, current, date, monthly) {
        ctrl.addGoal(
          name: name,
          targetAmount: target,
          currentAmount: current,
          targetDate: date,
          monthlyContribution: monthly,
        );
        _snack('Savings goal added.');
      }),
      const SizedBox(height: 20),
      if (ctrl.goals.isEmpty)
        _mutedNote(context, 'No savings goals yet. Add one to start tracking!')
      else
        for (final goal in ctrl.goals) ...[
          _goalCard(context, ctrl, goal),
          const SizedBox(height: 12),
        ],
    ];
  }

  Widget _goalCard(
      BuildContext context, BudgetController ctrl, SavingsGoal goal) {
    final c = context.colors;
    final progress = goal.progressPct;
    final remaining = goal.remaining;
    final reached = progress >= 100;

    int monthsLeft = 0;
    final target = DateTime.tryParse(goal.targetDate);
    if (target != null) {
      final days = target.difference(DateTime.now()).inDays;
      if (days > 0) monthsLeft = (days / 30.44).ceil();
    }
    final neededPerMonth = monthsLeft > 0 ? remaining / monthsLeft : remaining;
    final underfunded = goal.monthlyContribution > 0 &&
        neededPerMonth > 0 &&
        goal.monthlyContribution < neededPerMonth;

    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(goal.name,
                      style: TextStyle(
                          fontSize: FCFontSizes.lg,
                          fontWeight: FCFontWeights.semibold,
                          color: c.foreground)),
                ),
                FCButton(
                  label: 'Add Funds',
                  variant: FCButtonVariant.outline,
                  size: FCButtonSize.sm,
                  onPressed: () => _addFunds(ctrl, goal),
                ),
                const SizedBox(width: 4),
                FCButton(
                  icon: const Icon(Icons.delete_outline),
                  variant: FCButtonVariant.ghost,
                  size: FCButtonSize.icon,
                  onPressed: () => ctrl.deleteGoal(goal.id),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Target: ${Fmt.money2(goal.targetAmount)}',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground)),
                Text('Saved: ${Fmt.money2(goal.currentAmount)}',
                    style: TextStyle(
                        fontSize: FCFontSizes.sm, color: c.mutedForeground)),
              ],
            ),
            const SizedBox(height: 8),
            FCProgressBar(value: progress),
            const SizedBox(height: 8),
            if (reached)
              const Text('Goal Reached!',
                  style: TextStyle(
                      fontSize: FCFontSizes.sm,
                      fontWeight: FCFontWeights.semibold,
                      color: FCPalette.green600))
            else ...[
              Text.rich(
                TextSpan(
                  text: 'Remaining: ',
                  style: TextStyle(
                      fontSize: FCFontSizes.sm, color: c.mutedForeground),
                  children: [
                    TextSpan(
                      text: Fmt.money2(remaining),
                      style: const TextStyle(
                          fontWeight: FCFontWeights.semibold,
                          color: FCPalette.red600),
                    ),
                  ],
                ),
              ),
              if (monthsLeft > 0 && remaining > 0) ...[
                const SizedBox(height: 4),
                Text(
                  'To reach target by date, need to save '
                  '${Fmt.money2(neededPerMonth)}/month.',
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: c.mutedForeground),
                ),
              ],
              if (goal.monthlyContribution > 0) ...[
                const SizedBox(height: 4),
                Text(
                  'Your planned monthly contribution: '
                  '${Fmt.money2(goal.monthlyContribution)}.',
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: c.mutedForeground),
                ),
              ],
              if (underfunded) ...[
                const SizedBox(height: 8),
                FCCalloutBanner(
                  background: FCPalette.red50,
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.error_outline,
                          size: 16, color: FCPalette.red700),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: Text(
                          'Your planned monthly contribution is less than '
                          "what's needed to reach your goal by the target date!",
                          style: TextStyle(
                              fontSize: FCFontSizes.xs,
                              color: FCPalette.red700,
                              height: 1.4),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _addFunds(BudgetController ctrl, SavingsGoal goal) async {
    final amountController = TextEditingController();
    final amount = await showDialog<double>(
      context: context,
      builder: (dialogContext) {
        final c = dialogContext.colors;
        return AlertDialog(
          backgroundColor: c.popover,
          title: Text('Add Funds to ${goal.name}',
              style: TextStyle(color: c.popoverForeground)),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Enter the amount you want to add to this savings goal.',
                  style: TextStyle(
                      fontSize: FCFontSizes.sm, color: c.mutedForeground)),
              const SizedBox(height: 16),
              FCTextField(
                controller: amountController,
                label: 'Amount (\$)',
                hintText: '0.00',
                autofocus: true,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
              ),
            ],
          ),
          actions: [
            FCButton(
              label: 'Cancel',
              variant: FCButtonVariant.outline,
              size: FCButtonSize.sm,
              onPressed: () => Navigator.of(dialogContext).pop(),
            ),
            FCButton(
              label: 'Add Funds',
              size: FCButtonSize.sm,
              onPressed: () => Navigator.of(dialogContext)
                  .pop(parseNum(amountController.text)),
            ),
          ],
        );
      },
    );
    amountController.dispose();
    if (amount == null || amount <= 0) return;
    ctrl.addFundsToGoal(goal.id, amount);
  }

  // ── History tab ───────────────────────────────────────────────────────────

  List<Widget> _historyTab(BuildContext context, BudgetController ctrl) {
    return [
      FCCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            const FCCardHeader(children: [
              FCCardTitle('Budget History'),
              FCCardDescription('Review your past budget snapshots.'),
            ]),
            FCCardContent(
              child: ctrl.history.isEmpty
                  ? _mutedNote(context,
                      'No budget history saved yet. Save a snapshot from the '
                      'Budget tab!')
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (final entry in ctrl.history) ...[
                          _historyRow(context, ctrl, entry),
                          const SizedBox(height: 12),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
    ];
  }

  Widget _historyRow(
      BuildContext context, BudgetController ctrl, BudgetHistoryEntry entry) {
    final c = context.colors;
    final net = entry.monthlyNet;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.lgAll,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(entry.name,
                        style: TextStyle(
                            fontSize: FCFontSizes.base,
                            fontWeight: FCFontWeights.semibold,
                            color: c.foreground)),
                    const SizedBox(height: 2),
                    Text(_dateRange(entry.startDate, entry.endDate),
                        style: TextStyle(
                            fontSize: FCFontSizes.xs,
                            color: c.mutedForeground)),
                    Text('${entry.budgetType.label} Budget',
                        style: TextStyle(
                            fontSize: FCFontSizes.xs,
                            color: c.mutedForeground)),
                  ],
                ),
              ),
              FCButton(
                icon: const Icon(Icons.delete_outline),
                variant: FCButtonVariant.ghost,
                size: FCButtonSize.icon,
                onPressed: () => ctrl.deleteHistory(entry.id),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: _historyStat(context, 'Income',
                    Fmt.money2(entry.monthlyIncome), FCPalette.green600),
              ),
              Expanded(
                child: _historyStat(context, 'Expenses',
                    Fmt.money2(entry.monthlyExpenses), FCPalette.red600),
              ),
              Expanded(
                child: _historyStat(context, 'Net', Fmt.money2(net),
                    net >= 0 ? FCPalette.green600 : FCPalette.red600),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _historyStat(
      BuildContext context, String label, String value, Color color) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(label,
            style:
                TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground)),
        const SizedBox(height: 2),
        Text(value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
                fontSize: FCFontSizes.sm,
                fontWeight: FCFontWeights.semibold,
                color: color)),
      ],
    );
  }

  String _dateRange(String start, String end) {
    final s = DateTime.tryParse(start);
    final e = DateTime.tryParse(end);
    if (s == null) return '';
    String fmt(DateTime d) =>
        '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
    if (e == null || fmt(s) == fmt(e)) return fmt(s);
    return '${fmt(s)} – ${fmt(e)}';
  }

  // ── Shared ────────────────────────────────────────────────────────────────

  Widget _mutedNote(BuildContext context, String text) {
    final c = context.colors;
    return FCCard(
      child: FCCardContent(
        padding: const EdgeInsets.all(24),
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: TextStyle(
              fontSize: FCFontSizes.sm, color: c.mutedForeground, height: 1.5),
        ),
      ),
    );
  }
}

/// The collapsible add-goal form (mirrors the web's inline goal form).
class _AddGoalForm extends StatefulWidget {
  const _AddGoalForm({required this.onAdd});

  final void Function(
    String name,
    double target,
    double current,
    String targetDate,
    double monthly,
  ) onAdd;

  @override
  State<_AddGoalForm> createState() => _AddGoalFormState();
}

class _AddGoalFormState extends State<_AddGoalForm> {
  bool _open = false;
  final _name = TextEditingController();
  final _target = TextEditingController();
  final _current = TextEditingController();
  final _monthly = TextEditingController();
  DateTime? _date;

  @override
  void dispose() {
    _name.dispose();
    _target.dispose();
    _current.dispose();
    _monthly.dispose();
    super.dispose();
  }

  void _reset() {
    _name.clear();
    _target.clear();
    _current.clear();
    _monthly.clear();
    _date = null;
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _date ?? now,
      firstDate: now.subtract(const Duration(days: 1)),
      lastDate: DateTime(now.year + 20),
    );
    if (picked != null) setState(() => _date = picked);
  }

  void _submit() {
    final name = _name.text.trim();
    final target = parseNum(_target.text);
    if (name.isEmpty || target <= 0) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(
            content: Text('Please enter a goal name and target amount.')));
      return;
    }
    final targetDate = _date != null
        ? '${_date!.year}-${_date!.month.toString().padLeft(2, '0')}-${_date!.day.toString().padLeft(2, '0')}'
        : '';
    widget.onAdd(
      name,
      target,
      parseNum(_current.text),
      targetDate,
      parseNum(_monthly.text),
    );
    setState(() {
      _reset();
      _open = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final c = context.colors;
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              children: [
                const Expanded(child: FCCardTitle('Savings Goals')),
                FCButton(
                  label: _open ? 'Cancel' : 'Add Goal',
                  variant: _open
                      ? FCButtonVariant.outline
                      : FCButtonVariant.primary,
                  size: FCButtonSize.sm,
                  icon: Icon(_open ? Icons.close : Icons.add_circle_outline),
                  onPressed: () => setState(() {
                    _open = !_open;
                    if (!_open) _reset();
                  }),
                ),
              ],
            ),
          ]),
          if (_open)
            FCCardContent(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  FCTextField(
                    controller: _name,
                    label: 'Goal Name',
                    hintText: 'e.g., New Car Fund, Down Payment',
                  ),
                  const SizedBox(height: 16),
                  FCTextField(
                    controller: _target,
                    label: 'Target Amount (\$)',
                    hintText: '0.00',
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                  ),
                  const SizedBox(height: 16),
                  FCTextField(
                    controller: _current,
                    label: 'Current Amount Saved (\$)',
                    hintText: '0.00',
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                  ),
                  const SizedBox(height: 16),
                  FCTextField(
                    controller: _monthly,
                    label: 'Planned Monthly Contribution (\$)',
                    hintText: '0.00',
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                  ),
                  const SizedBox(height: 16),
                  Text('Target Date',
                      style: TextStyle(
                          fontSize: FCFontSizes.sm,
                          fontWeight: FCFontWeights.medium,
                          color: c.foreground)),
                  const SizedBox(height: 8),
                  GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: _pickDate,
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
                              _date != null
                                  ? '${_date!.year}-${_date!.month.toString().padLeft(2, '0')}-${_date!.day.toString().padLeft(2, '0')}'
                                  : 'Select a date',
                              style: TextStyle(
                                fontSize: FCFontSizes.base,
                                color: _date != null
                                    ? c.foreground
                                    : c.mutedForeground,
                              ),
                            ),
                          ),
                          Icon(Icons.calendar_today_outlined,
                              size: 16, color: c.mutedForeground),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  FCButton(
                    label: 'Add Goal',
                    fullWidth: true,
                    onPressed: _submit,
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
