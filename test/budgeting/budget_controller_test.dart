import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/budgeting/budget_controller.dart';
import 'package:finnacalc/features/budgeting/models/budget_models.dart';
import 'package:finnacalc/features/budgeting/models/plaid_models.dart';

void main() {
  BudgetController make() => BudgetController();

  group('convertToMonthly multipliers', () {
    test('match the web exactly', () {
      double m(double amt, BudgetFrequency f) => BudgetItem(
            id: 'x',
            category: 'c',
            subcategory: '',
            amount: amt,
            frequency: f,
            type: BudgetEntryType.expense,
            isFixed: false,
            budgetType: BudgetType.personal,
          ).monthlyAmount;
      expect(m(10, BudgetFrequency.daily), closeTo(300, 1e-9)); // x30
      expect(m(100, BudgetFrequency.weekly), closeTo(433, 1e-9)); // x4.33
      expect(m(100, BudgetFrequency.monthly), 100); // x1
      expect(m(1200, BudgetFrequency.yearly), closeTo(100, 1e-9)); // /12
    });
  });

  group('totals & savings rate', () {
    test('income/expenses/net and null savings rate', () {
      final c = make();
      c.addItem(
          category: 'Salary',
          description: 'job',
          amount: 5000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.income,
          isFixed: true);
      c.addItem(
          category: 'Housing',
          description: 'rent',
          amount: 2000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.expense,
          isFixed: true);
      expect(c.monthlyIncome, 5000);
      expect(c.monthlyExpenses, 2000);
      expect(c.monthlyNet, 3000);
      expect(c.savingsRate, isNull); // no Savings/Retirement line
    });

    test('savings rate uses Savings + Retirement lines only', () {
      final c = make();
      c.addItem(
          category: 'Salary',
          description: '',
          amount: 5000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.income,
          isFixed: true);
      c.addItem(
          category: 'Savings',
          description: '',
          amount: 500,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.expense,
          isFixed: true);
      expect(c.savingsRate, closeTo(10, 1e-9));
    });
  });

  group('budgetType filtering', () {
    test('only active type counts', () {
      final c = make();
      c.addItem(
          category: 'Salary',
          description: '',
          amount: 5000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.income,
          isFixed: true);
      c.setBudgetType(BudgetType.business);
      expect(c.monthlyIncome, 0); // personal item excluded
      c.setBudgetType(BudgetType.personal);
      expect(c.monthlyIncome, 5000);
    });
  });

  group('analysis', () {
    test('spending alert when net negative', () {
      final c = make();
      c.addItem(
          category: 'Salary',
          description: '',
          amount: 2000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.income,
          isFixed: true);
      c.addItem(
          category: 'Housing',
          description: '',
          amount: 3000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.expense,
          isFixed: true);
      final first = c.analysis.first;
      expect(first.tone, AnalysisTone.destructive);
      expect(first.title, 'Spending Alert!');
    });

    test('high debt payments warning over 15%', () {
      final c = make();
      c.addItem(
          category: 'Salary',
          description: '',
          amount: 5000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.income,
          isFixed: true);
      c.addItem(
          category: 'Debt Payments',
          description: '',
          amount: 1000,
          frequency: BudgetFrequency.monthly,
          type: BudgetEntryType.expense,
          isFixed: true);
      expect(
        c.analysis.any((a) => a.title == 'High Debt Payments'),
        isTrue,
      );
    });
  });

  group('mapPlaidCategory', () {
    test('personal expense + income mappings', () {
      expect(
          BudgetController.mapPlaidCategory(
              'FOOD_AND_DRINK', BudgetEntryType.expense, BudgetType.personal),
          'Food');
      expect(
          BudgetController.mapPlaidCategory(
              'UNKNOWN', BudgetEntryType.expense, BudgetType.personal),
          'Other');
      expect(
          BudgetController.mapPlaidCategory(
              'PAYROLL', BudgetEntryType.income, BudgetType.personal),
          'Salary');
    });

    test('business mappings', () {
      expect(
          BudgetController.mapPlaidCategory(
              'FOOD_AND_DRINK', BudgetEntryType.expense, BudgetType.business),
          'Other Operating Costs');
      expect(
          BudgetController.mapPlaidCategory(
              'PAYROLL', BudgetEntryType.income, BudgetType.business),
          'Other Revenue');
    });
  });

  group('importPlaidTransactions', () {
    test('saves a history snapshot, leaves live items empty', () {
      final c = make();
      c.importPlaidTransactions(const [
        BankTransaction(
            date: '2024-01-01',
            name: 'Rent',
            amount: 1500, // > 0 => expense
            category: 'RENT_AND_UTILITIES',
            currency: 'USD'),
        BankTransaction(
            date: '2024-01-02',
            name: 'Paycheck',
            amount: -3000, // < 0 => income
            category: 'INCOME',
            currency: 'USD'),
      ]);
      expect(c.items, isEmpty); // not added to live items
      expect(c.history, hasLength(1));
      final snap = c.history.first;
      expect(snap.name, 'Bank Import (Plaid)');
      expect(snap.monthlyIncome, 3000);
      expect(snap.monthlyExpenses, 1500);
      expect(snap.budgetItems.first.category, 'Housing'); // RENT_AND_UTILITIES
    });
  });
}
