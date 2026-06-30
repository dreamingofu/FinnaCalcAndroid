/// A bank transaction from `/api/plaid/transactions`. Sign convention matches
/// Plaid/the web: `amount > 0` = money out (expense), `< 0` = money in (income).
class BankTransaction {
  const BankTransaction({
    required this.date,
    required this.name,
    required this.amount,
    required this.category,
    required this.currency,
  });

  final String date;
  final String name;
  final double amount;
  final String category; // Plaid personal_finance_category primary
  final String currency;

  factory BankTransaction.fromJson(Map<String, dynamic> j) => BankTransaction(
        date: j['date'] as String? ?? '',
        name: j['name'] as String? ?? 'Transaction',
        amount: (j['amount'] as num?)?.toDouble() ?? 0,
        category: j['category'] as String? ?? 'OTHER',
        currency: j['currency'] as String? ?? 'USD',
      );
}

/// A credit card from `/api/plaid/liabilities`.
class CreditLine {
  const CreditLine({
    required this.accountId,
    required this.name,
    required this.mask,
    required this.balance,
    required this.limit,
    required this.utilization,
    required this.apr,
    required this.minimumPayment,
    required this.lastStatementBalance,
    required this.nextDueDate,
    required this.isOverdue,
  });

  final String accountId;
  final String name;
  final String? mask;
  final double balance;
  final double? limit;
  final double? utilization;
  final double? apr;
  final double? minimumPayment;
  final double? lastStatementBalance;
  final String? nextDueDate;
  final bool isOverdue;

  factory CreditLine.fromJson(Map<String, dynamic> j) => CreditLine(
        accountId: j['accountId'] as String? ?? '',
        name: j['name'] as String? ?? 'Card',
        mask: j['mask'] as String?,
        balance: (j['balance'] as num?)?.toDouble() ?? 0,
        limit: (j['limit'] as num?)?.toDouble(),
        utilization: (j['utilization'] as num?)?.toDouble(),
        apr: (j['apr'] as num?)?.toDouble(),
        minimumPayment: (j['minimumPayment'] as num?)?.toDouble(),
        lastStatementBalance:
            (j['lastStatementBalance'] as num?)?.toDouble(),
        nextDueDate: j['nextDueDate'] as String?,
        isOverdue: j['isOverdue'] as bool? ?? false,
      );
}

/// A loan (student or mortgage) from `/api/plaid/liabilities`.
class OtherDebt {
  const OtherDebt({
    required this.accountId,
    required this.type,
    required this.name,
    required this.balance,
    required this.apr,
    required this.minimumPayment,
    required this.nextDueDate,
  });

  final String accountId;
  final String type; // "student" | "mortgage"
  final String name;
  final double balance;
  final double? apr;
  final double? minimumPayment;
  final String? nextDueDate;

  factory OtherDebt.fromJson(Map<String, dynamic> j) => OtherDebt(
        accountId: j['accountId'] as String? ?? '',
        type: j['type'] as String? ?? 'loan',
        name: j['name'] as String? ?? 'Loan',
        balance: (j['balance'] as num?)?.toDouble() ?? 0,
        apr: (j['apr'] as num?)?.toDouble(),
        minimumPayment: (j['minimumPayment'] as num?)?.toDouble(),
        nextDueDate: j['nextDueDate'] as String?,
      );
}

/// The full `/api/plaid/liabilities` response.
class LiabilitiesResponse {
  const LiabilitiesResponse({
    required this.creditLines,
    required this.otherDebts,
    required this.totalCreditBalance,
    required this.totalCreditLimit,
    required this.overallUtilization,
    required this.totalMinimumPayments,
    required this.totalDebt,
    required this.currency,
  });

  final List<CreditLine> creditLines;
  final List<OtherDebt> otherDebts;
  final double totalCreditBalance;
  final double totalCreditLimit;
  final double? overallUtilization;
  final double totalMinimumPayments;
  final double totalDebt;
  final String currency;

  bool get isEmpty => creditLines.isEmpty && otherDebts.isEmpty;

  factory LiabilitiesResponse.fromJson(Map<String, dynamic> j) =>
      LiabilitiesResponse(
        creditLines: ((j['creditLines'] as List?) ?? [])
            .map((e) => CreditLine.fromJson(e as Map<String, dynamic>))
            .toList(),
        otherDebts: ((j['otherDebts'] as List?) ?? [])
            .map((e) => OtherDebt.fromJson(e as Map<String, dynamic>))
            .toList(),
        totalCreditBalance:
            (j['totalCreditBalance'] as num?)?.toDouble() ?? 0,
        totalCreditLimit: (j['totalCreditLimit'] as num?)?.toDouble() ?? 0,
        overallUtilization: (j['overallUtilization'] as num?)?.toDouble(),
        totalMinimumPayments:
            (j['totalMinimumPayments'] as num?)?.toDouble() ?? 0,
        totalDebt: (j['totalDebt'] as num?)?.toDouble() ?? 0,
        currency: j['currency'] as String? ?? 'USD',
      );
}
