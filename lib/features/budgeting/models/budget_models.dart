/// How often a budget line recurs. Monthly-normalisation multipliers match the
/// web's `convertToMonthly` exactly: daily ×30, weekly ×4.33, monthly ×1,
/// yearly ÷12.
enum BudgetFrequency {
  daily(30, 'Daily'),
  weekly(4.33, 'Weekly'),
  monthly(1, 'Monthly'),
  yearly(1 / 12, 'Yearly');

  const BudgetFrequency(this.monthlyMultiplier, this.label);
  final double monthlyMultiplier;
  final String label;

  static BudgetFrequency fromName(String? name) =>
      BudgetFrequency.values.firstWhere((f) => f.name == name,
          orElse: () => BudgetFrequency.monthly);
}

enum BudgetEntryType {
  income('Income'),
  expense('Expense');

  const BudgetEntryType(this.label);
  final String label;

  static BudgetEntryType fromName(String? name) =>
      BudgetEntryType.values.firstWhere((t) => t.name == name,
          orElse: () => BudgetEntryType.expense);
}

enum BudgetType {
  personal('Personal'),
  business('Business');

  const BudgetType(this.label);
  final String label;

  static BudgetType fromName(String? name) =>
      BudgetType.values.firstWhere((t) => t.name == name,
          orElse: () => BudgetType.personal);
}

/// A single budget line. Mirrors the web's `BudgetItem`.
class BudgetItem {
  const BudgetItem({
    required this.id,
    required this.category,
    required this.subcategory,
    required this.amount,
    required this.frequency,
    required this.type,
    required this.isFixed,
    required this.budgetType,
    this.importDate,
  });

  final String id;
  final String category;
  final String subcategory; // shown as "Description" in the UI
  final double amount;
  final BudgetFrequency frequency;
  final BudgetEntryType type;
  final bool isFixed;
  final BudgetType budgetType;
  final String? importDate;

  /// `amount * multiplier(frequency)`.
  double get monthlyAmount => amount * frequency.monthlyMultiplier;

  BudgetItem copyWith({
    String? category,
    String? subcategory,
    double? amount,
    BudgetFrequency? frequency,
    BudgetEntryType? type,
    bool? isFixed,
    BudgetType? budgetType,
  }) {
    return BudgetItem(
      id: id,
      category: category ?? this.category,
      subcategory: subcategory ?? this.subcategory,
      amount: amount ?? this.amount,
      frequency: frequency ?? this.frequency,
      type: type ?? this.type,
      isFixed: isFixed ?? this.isFixed,
      budgetType: budgetType ?? this.budgetType,
      importDate: importDate,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'category': category,
        'subcategory': subcategory,
        'amount': amount,
        'frequency': frequency.name,
        'type': type.name,
        'isFixed': isFixed,
        'budgetType': budgetType.name,
        if (importDate != null) 'importDate': importDate,
      };

  factory BudgetItem.fromJson(Map<String, dynamic> j) => BudgetItem(
        id: j['id'] as String,
        category: j['category'] as String? ?? 'Other',
        subcategory: j['subcategory'] as String? ?? '',
        amount: (j['amount'] as num?)?.toDouble() ?? 0,
        frequency: BudgetFrequency.fromName(j['frequency'] as String?),
        type: BudgetEntryType.fromName(j['type'] as String?),
        isFixed: j['isFixed'] as bool? ?? false,
        budgetType: BudgetType.fromName(j['budgetType'] as String?),
        importDate: j['importDate'] as String?,
      );
}

/// A savings goal. Mirrors the web's `SavingsGoal`.
class SavingsGoal {
  const SavingsGoal({
    required this.id,
    required this.name,
    required this.targetAmount,
    required this.currentAmount,
    required this.targetDate,
    required this.monthlyContribution,
  });

  final String id;
  final String name;
  final double targetAmount;
  final double currentAmount;
  final String targetDate; // ISO yyyy-MM-dd
  final double monthlyContribution;

  double get progressPct =>
      targetAmount > 0 ? (currentAmount / targetAmount) * 100 : 0;
  double get remaining => targetAmount - currentAmount;

  SavingsGoal copyWith({double? currentAmount}) => SavingsGoal(
        id: id,
        name: name,
        targetAmount: targetAmount,
        currentAmount: currentAmount ?? this.currentAmount,
        targetDate: targetDate,
        monthlyContribution: monthlyContribution,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'targetAmount': targetAmount,
        'currentAmount': currentAmount,
        'targetDate': targetDate,
        'monthlyContribution': monthlyContribution,
      };

  factory SavingsGoal.fromJson(Map<String, dynamic> j) => SavingsGoal(
        id: j['id'] as String,
        name: j['name'] as String? ?? '',
        targetAmount: (j['targetAmount'] as num?)?.toDouble() ?? 0,
        currentAmount: (j['currentAmount'] as num?)?.toDouble() ?? 0,
        targetDate: j['targetDate'] as String? ?? '',
        monthlyContribution:
            (j['monthlyContribution'] as num?)?.toDouble() ?? 0,
      );
}

/// A saved budget snapshot. Mirrors the web's `BudgetHistoryEntry`.
class BudgetHistoryEntry {
  const BudgetHistoryEntry({
    required this.id,
    required this.name,
    required this.startDate,
    required this.endDate,
    required this.budgetItems,
    required this.monthlyIncome,
    required this.monthlyExpenses,
    required this.monthlyNet,
    required this.budgetType,
  });

  final String id;
  final String name;
  final String startDate;
  final String endDate;
  final List<BudgetItem> budgetItems;
  final double monthlyIncome;
  final double monthlyExpenses;
  final double monthlyNet;
  final BudgetType budgetType;

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'startDate': startDate,
        'endDate': endDate,
        'budgetItems': budgetItems.map((i) => i.toJson()).toList(),
        'monthlyIncome': monthlyIncome,
        'monthlyExpenses': monthlyExpenses,
        'monthlyNet': monthlyNet,
        'budgetType': budgetType.name,
      };

  factory BudgetHistoryEntry.fromJson(Map<String, dynamic> j) =>
      BudgetHistoryEntry(
        id: j['id'] as String,
        name: j['name'] as String? ?? '',
        startDate: j['startDate'] as String? ?? '',
        endDate: j['endDate'] as String? ?? '',
        budgetItems: ((j['budgetItems'] as List?) ?? [])
            .map((e) => BudgetItem.fromJson(e as Map<String, dynamic>))
            .toList(),
        monthlyIncome: (j['monthlyIncome'] as num?)?.toDouble() ?? 0,
        monthlyExpenses: (j['monthlyExpenses'] as num?)?.toDouble() ?? 0,
        monthlyNet: (j['monthlyNet'] as num?)?.toDouble() ?? 0,
        budgetType: BudgetType.fromName(j['budgetType'] as String?),
      );
}

/// The category lists, verbatim from the web.
class BudgetCategories {
  const BudgetCategories._();

  static const personalIncome = [
    'Salary', 'Freelance', 'Investments', 'Gift', 'Other',
  ];
  static const personalExpense = [
    'Housing', 'Utilities', 'Food', 'Transportation', 'Entertainment',
    'Healthcare', 'Insurance', 'Debt Payments', 'Savings', 'Retirement',
    'Other',
  ];
  static const businessIncome = [
    'Sales Revenue', 'Service Revenue', 'Subscriptions', 'Interest Earned',
    'Other Fees', 'Total Revenue', 'Other Revenue',
  ];
  static const businessExpense = [
    'Cost of Goods Sold (COGS)', 'Salaries/Wages', 'Marketing & Advertising',
    'Rent/Lease', 'Utilities', 'Software & Subscriptions', 'Supplies',
    'Repairs & Maintenance', 'Insurance', 'Professional Fees', 'Taxes',
    'Travel', 'Depreciation', 'Loan Payments', 'Other Operating Costs',
  ];

  static List<String> forType(BudgetType budgetType, BudgetEntryType type) {
    if (budgetType == BudgetType.personal) {
      return type == BudgetEntryType.income ? personalIncome : personalExpense;
    }
    return type == BudgetEntryType.income ? businessIncome : businessExpense;
  }
}
