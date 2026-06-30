/// Tax payments and prior-year data (Form 1040 lines 25–26 + Form 2210 safe
/// harbor).
library;

class Payments {
  Payments({
    this.additionalWithholding = 0,
    this.estimatedPayments = 0,
    this.priorYearTax,
    this.priorYearAgi,
  });

  /// Additional federal income tax withheld NOT already captured on W-2 box 2
  /// or 1099 withholding boxes (the engine sums those from the income forms).
  /// Use this for any withholding the user enters directly. Sch line / 1040
  /// line 25.
  double additionalWithholding;

  /// 2024 estimated tax payments made (Form 1040-ES). Line 26.
  double estimatedPayments;

  /// Prior-year (2023) total tax — for the 2210 safe harbor (100%/110%).
  double? priorYearTax;

  /// Prior-year (2023) AGI — determines whether the 110% safe harbor applies.
  double? priorYearAgi;
}

/// Bank info for direct deposit / payment. SENSITIVE — never persisted.
class BankInfo {
  BankInfo({
    this.routingNumber = '',
    this.accountNumber = '',
    this.accountType = 'checking',
  });

  String routingNumber;
  String accountNumber;

  /// One of "checking" | "savings".
  String accountType;
}
