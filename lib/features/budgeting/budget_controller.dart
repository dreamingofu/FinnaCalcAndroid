import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'models/budget_models.dart';
import 'models/plaid_models.dart';

enum AnalysisTone { destructive, warning, success, info }

class AnalysisItem {
  const AnalysisItem(this.tone, this.title, this.message);
  final AnalysisTone tone;
  final String title;
  final String message;
}

/// A named category total (for pie charts / breakdowns).
class CategoryTotal {
  const CategoryTotal(this.name, this.value);
  final String name;
  final double value;
}

/// Holds and persists the budgeting state, and exposes the web's budgeting math
/// (`convertToMonthly` totals, savings rate, category aggregation, analysis).
class BudgetController extends ChangeNotifier {
  BudgetController([this._prefs]);

  static const _kItems = 'finnacalc-budget-items';
  static const _kGoals = 'finnacalc-savings-goals';
  static const _kHistory = 'finnacalc-budget-history';

  SharedPreferences? _prefs;
  int _idSeq = 0;

  BudgetType _budgetType = BudgetType.personal;
  List<BudgetItem> _items = [];
  List<SavingsGoal> _goals = [];
  List<BudgetHistoryEntry> _history = [];

  BudgetType get budgetType => _budgetType;
  List<BudgetItem> get allItems => List.unmodifiable(_items);
  List<SavingsGoal> get goals => List.unmodifiable(_goals);
  List<BudgetHistoryEntry> get history => List.unmodifiable(_history);

  /// Items for the active [budgetType].
  List<BudgetItem> get items =>
      _items.where((i) => i.budgetType == _budgetType).toList();

  Future<void> load() async {
    _prefs ??= await SharedPreferences.getInstance();
    _items = _decodeList(_kItems, BudgetItem.fromJson);
    _goals = _decodeList(_kGoals, SavingsGoal.fromJson);
    _history = _decodeList(_kHistory, BudgetHistoryEntry.fromJson);
    notifyListeners();
  }

  List<T> _decodeList<T>(String key, T Function(Map<String, dynamic>) fromJson) {
    final raw = _prefs?.getString(key);
    if (raw == null || raw.isEmpty) return [];
    try {
      final list = jsonDecode(raw) as List;
      return list
          .map((e) => fromJson((e as Map).cast<String, dynamic>()))
          .toList();
    } catch (_) {
      return [];
    }
  }

  void _persist(String key, List<dynamic> jsonList) {
    _prefs?.setString(key, jsonEncode(jsonList));
  }

  String _newId() =>
      '${DateTime.now().microsecondsSinceEpoch}-${_idSeq++}';

  // ---- Mutations -----------------------------------------------------------

  void setBudgetType(BudgetType type) {
    if (_budgetType == type) return;
    _budgetType = type;
    notifyListeners();
  }

  void addItem({
    required String category,
    required String description,
    required double amount,
    required BudgetFrequency frequency,
    required BudgetEntryType type,
    required bool isFixed,
  }) {
    _items = [
      ..._items,
      BudgetItem(
        id: _newId(),
        category: category,
        subcategory: description,
        amount: amount,
        frequency: frequency,
        type: type,
        isFixed: isFixed,
        budgetType: _budgetType,
      ),
    ];
    _persistItems();
  }

  void updateItem(BudgetItem item) {
    _items = [for (final i in _items) if (i.id == item.id) item else i];
    _persistItems();
  }

  void deleteItem(String id) {
    _items = _items.where((i) => i.id != id).toList();
    _persistItems();
  }

  void addGoal({
    required String name,
    required double targetAmount,
    required double currentAmount,
    required String targetDate,
    required double monthlyContribution,
  }) {
    _goals = [
      ..._goals,
      SavingsGoal(
        id: _newId(),
        name: name,
        targetAmount: targetAmount,
        currentAmount: currentAmount,
        targetDate: targetDate,
        monthlyContribution: monthlyContribution,
      ),
    ];
    _persistGoals();
  }

  void addFundsToGoal(String id, double amount) {
    _goals = [
      for (final g in _goals)
        if (g.id == id) g.copyWith(currentAmount: g.currentAmount + amount) else g
    ];
    _persistGoals();
  }

  void deleteGoal(String id) {
    _goals = _goals.where((g) => g.id != id).toList();
    _persistGoals();
  }

  void saveToHistory(String name, String startDate, String endDate) {
    final entry = BudgetHistoryEntry(
      id: _newId(),
      name: name,
      startDate: startDate,
      endDate: endDate,
      budgetItems: items,
      monthlyIncome: monthlyIncome,
      monthlyExpenses: monthlyExpenses,
      monthlyNet: monthlyNet,
      budgetType: _budgetType,
    );
    _history = [..._history, entry];
    _persistHistory();
  }

  void deleteHistory(String id) {
    _history = _history.where((h) => h.id != id).toList();
    _persistHistory();
  }

  /// `'budget'` clears items only; `'all'` clears everything.
  void clear(String scope) {
    _items = [];
    if (scope == 'all') {
      _goals = [];
      _history = [];
    }
    _persistItems();
    if (scope == 'all') {
      _persistGoals();
      _persistHistory();
    }
  }

  /// Imports Plaid transactions as a history snapshot named "Bank Import
  /// (Plaid)" (NOT into live items), exactly like the web.
  void importPlaidTransactions(List<BankTransaction> transactions) {
    final imported = transactions.map((t) {
      final type =
          t.amount > 0 ? BudgetEntryType.expense : BudgetEntryType.income;
      return BudgetItem(
        id: _newId(),
        category: mapPlaidCategory(t.category, type, _budgetType),
        subcategory: t.name,
        amount: t.amount.abs(),
        frequency: BudgetFrequency.monthly,
        type: type,
        isFixed: false,
        budgetType: _budgetType,
        importDate: t.date,
      );
    }).toList();

    final income = imported
        .where((i) => i.type == BudgetEntryType.income)
        .fold<double>(0, (s, i) => s + i.monthlyAmount);
    final expenses = imported
        .where((i) => i.type == BudgetEntryType.expense)
        .fold<double>(0, (s, i) => s + i.monthlyAmount);

    _history = [
      ..._history,
      BudgetHistoryEntry(
        id: _newId(),
        name: 'Bank Import (Plaid)',
        startDate: DateTime.now().toIso8601String(),
        endDate: DateTime.now().toIso8601String(),
        budgetItems: imported,
        monthlyIncome: income,
        monthlyExpenses: expenses,
        monthlyNet: income - expenses,
        budgetType: _budgetType,
      ),
    ];
    _persistHistory();
  }

  void _persistItems() {
    _persist(_kItems, _items.map((i) => i.toJson()).toList());
    notifyListeners();
  }

  void _persistGoals() {
    _persist(_kGoals, _goals.map((g) => g.toJson()).toList());
    notifyListeners();
  }

  void _persistHistory() {
    _persist(_kHistory, _history.map((h) => h.toJson()).toList());
    notifyListeners();
  }

  // ---- Derived math --------------------------------------------------------

  double get monthlyIncome => items
      .where((i) => i.type == BudgetEntryType.income)
      .fold(0, (s, i) => s + i.monthlyAmount);

  double get monthlyExpenses => items
      .where((i) => i.type == BudgetEntryType.expense)
      .fold(0, (s, i) => s + i.monthlyAmount);

  double get monthlyNet => monthlyIncome - monthlyExpenses;

  /// Card savings rate: savings + retirement contributions / income.
  /// Null when income is 0 or no savings/retirement lines exist.
  double? get savingsRate {
    final contributions = items
        .where((i) =>
            i.type == BudgetEntryType.expense &&
            (i.category == 'Savings' || i.category == 'Retirement'))
        .fold<double>(0, (s, i) => s + i.monthlyAmount);
    if (monthlyIncome > 0 && contributions > 0) {
      return contributions / monthlyIncome * 100;
    }
    return null;
  }

  List<CategoryTotal> get expenseByCategory =>
      _byCategory(BudgetEntryType.expense);

  List<CategoryTotal> get incomeByCategory =>
      _byCategory(BudgetEntryType.income);

  List<CategoryTotal> _byCategory(BudgetEntryType type) {
    final map = <String, double>{};
    for (final i in items.where((i) => i.type == type)) {
      map[i.category] = (map[i.category] ?? 0) + i.monthlyAmount;
    }
    final list = map.entries.map((e) => CategoryTotal(e.key, e.value)).toList()
      ..sort((a, b) => b.value.compareTo(a.value));
    return list;
  }

  /// The auto-generated analysis feedback, mirroring `generateBudgetAnalysis`.
  List<AnalysisItem> get analysis {
    final out = <AnalysisItem>[];
    final income = monthlyIncome;
    final net = monthlyNet;
    final rate = income > 0 ? (net / income) * 100 : 0.0;

    if (net < 0) {
      out.add(AnalysisItem(
        AnalysisTone.destructive,
        'Spending Alert!',
        'You are spending \$${net.abs().toStringAsFixed(2)} more than you '
            'earn each month. Review your expenses to avoid debt.',
      ));
    } else if (rate < 10) {
      out.add(AnalysisItem(
        AnalysisTone.warning,
        'Low Savings Rate',
        'Your current savings rate is ${rate.toStringAsFixed(1)}%. Aim for at '
            'least 10–20% of your income.',
      ));
    } else if (rate <= 20) {
      out.add(const AnalysisItem(
        AnalysisTone.success,
        'Good Job!',
        "You're saving a healthy share of your income. Keep it up.",
      ));
    } else {
      out.add(const AnalysisItem(
        AnalysisTone.success,
        'Excellent Savings Rate!',
        "You're saving over 20% of your income — outstanding.",
      ));
    }

    final expenses = expenseByCategory;
    if (expenses.isNotEmpty && income > 0) {
      final top = expenses.first;
      final pct = top.value / income * 100;
      out.add(AnalysisItem(
        AnalysisTone.info,
        'Top Expense: ${top.name}',
        '${top.name} is your largest expense at ${pct.toStringAsFixed(1)}% of '
            'your income.',
      ));
    }

    final debt = items
        .where((i) =>
            i.type == BudgetEntryType.expense &&
            i.category == 'Debt Payments')
        .fold<double>(0, (s, i) => s + i.monthlyAmount);
    if (debt > 0 && income > 0) {
      final ratio = debt / income * 100;
      if (ratio > 15) {
        out.add(AnalysisItem(
          AnalysisTone.warning,
          'High Debt Payments',
          'Debt payments are ${ratio.toStringAsFixed(1)}% of your income. '
              'Consider strategies like the debt snowball or avalanche method.',
        ));
      }
    }

    final goalContrib =
        _goals.fold<double>(0, (s, g) => s + g.monthlyContribution);
    if (goalContrib > 0 && goalContrib > net) {
      out.add(AnalysisItem(
        AnalysisTone.warning,
        'Savings Goals Need Funding',
        'Your goal contributions (\$${goalContrib.toStringAsFixed(2)}/mo) '
            'exceed your monthly net of \$${net.toStringAsFixed(2)}.',
      ));
    }

    return out;
  }

  /// Maps a Plaid `personal_finance_category.primary` to a budget category.
  static String mapPlaidCategory(
      String plaidCategory, BudgetEntryType type, BudgetType budgetType) {
    if (type == BudgetEntryType.income) {
      if (budgetType == BudgetType.business) return 'Other Revenue';
      return RegExp('INCOME|PAYROLL|DEPOSIT').hasMatch(plaidCategory)
          ? 'Salary'
          : 'Other';
    }
    if (budgetType == BudgetType.business) return 'Other Operating Costs';
    const map = {
      'FOOD_AND_DRINK': 'Food',
      'RENT_AND_UTILITIES': 'Housing',
      'TRANSPORTATION': 'Transportation',
      'TRAVEL': 'Transportation',
      'ENTERTAINMENT': 'Entertainment',
      'MEDICAL': 'Healthcare',
      'LOAN_PAYMENTS': 'Debt Payments',
      'INSURANCE': 'Insurance',
    };
    return map[plaidCategory] ?? 'Other';
  }
}
