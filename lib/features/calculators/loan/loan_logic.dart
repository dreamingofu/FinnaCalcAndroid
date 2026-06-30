import 'dart:math' as math;

import '../../../core/util/parse.dart';

/// Payment frequency for the loan payment tab. `periods` is payments/year;
/// `termPeriods(months)` converts a month-denominated term to that frequency,
/// exactly as the web's `freq` table.
enum PaymentFrequency {
  monthly(12, 'Monthly (12/year)'),
  biweekly(26, 'Bi-weekly (26/year)'),
  weekly(52, 'Weekly (52/year)'),
  quarterly(4, 'Quarterly (4/year)'),
  annually(1, 'Annually (1/year)');

  const PaymentFrequency(this.periods, this.label);
  final int periods;
  final String label;

  int termPeriods(double termMonths) {
    switch (this) {
      case PaymentFrequency.monthly:
        return termMonths.round();
      case PaymentFrequency.biweekly:
        return (termMonths * 26 / 12).round();
      case PaymentFrequency.weekly:
        return (termMonths * 52 / 12).round();
      case PaymentFrequency.quarterly:
        return (termMonths * 4 / 12).round();
      case PaymentFrequency.annually:
        return (termMonths / 12).round();
    }
  }
}

class LoanPaymentResult {
  const LoanPaymentResult({
    required this.basePayment,
    required this.totalPayment,
    required this.totalInterest,
    required this.principal,
  });
  final double basePayment;
  final double totalPayment;
  final double totalInterest;
  final double principal;
}

class LoanAprResult {
  const LoanAprResult({
    required this.apr,
    required this.totalCost,
    required this.principal,
    required this.term,
  });
  final double apr;
  final double totalCost;
  final double principal;
  final double term;
}

class LoanAmountResult {
  const LoanAmountResult({
    required this.maxLoan,
    required this.payment,
    required this.term,
  });
  final double maxLoan;
  final double payment;
  final double term;
}

class LoanRemainingResult {
  const LoanRemainingResult({
    required this.remainingBalance,
    required this.remainingPayments,
    required this.monthlyPayment,
    required this.totalPaid,
  });
  final double remainingBalance;
  final double remainingPayments;
  final double monthlyPayment;
  final double totalPaid;
}

/// Pure loan math, transcribed 1:1 from `app/loan-calculator/page.tsx`.
class LoanCalculator {
  const LoanCalculator._();

  /// True APR via Newton-Raphson — US Regulation Z method.
  static double computeAPR(
      double principal, double interest, double fees, double termYears) {
    final n = (termYears * 12).round();
    if (n <= 0 || principal <= 0) return 0;
    final pmt = (principal + interest) / n;
    final net = principal - fees;
    if (net <= 0 || pmt <= 0) return 0;

    double m = 0.005;
    for (var i = 0; i < 300; i++) {
      final f = math.pow(1 + m, -n).toDouble();
      final pv = pmt * (1 - f) / m;
      final dpv = pmt * (n * f * m / ((1 + m) * m) - (1 - f)) / (m * m);
      final dm = -(pv - net) / dpv;
      m += dm;
      if (!m.isFinite || m <= 0) return 0;
      if (dm.abs() < 1e-10) break;
    }
    return m * 12 * 100;
  }

  static LoanPaymentResult payment({
    required double loanAmount,
    required double downPayment,
    required double interestRate,
    required double loanTerm,
    required PaymentFrequency frequency,
  }) {
    final principal = loanAmount - downPayment;
    final annualRate = interestRate / 100;
    final periods = frequency.periods;
    final termPeriods = frequency.termPeriods(loanTerm);
    final rate = annualRate / periods;

    if (principal < 0 || termPeriods <= 0) {
      throw const CalcException(
          'Please enter valid positive numbers for Loan Amount and Term.');
    }

    double basePayment;
    if (rate == 0) {
      basePayment = termPeriods > 0 ? principal / termPeriods : 0;
    } else {
      basePayment = (principal * rate * math.pow(1 + rate, termPeriods)) /
          (math.pow(1 + rate, termPeriods) - 1);
    }
    if (!basePayment.isFinite) basePayment = 0;

    return LoanPaymentResult(
      basePayment: basePayment,
      totalPayment: basePayment * termPeriods,
      totalInterest: basePayment * termPeriods - principal,
      principal: principal,
    );
  }

  static LoanAprResult apr({
    required double loanAmount,
    required double totalInterest,
    required double fees,
    required double termYears,
  }) {
    if (loanAmount <= 0 || termYears <= 0) {
      throw const CalcException('Please enter valid positive numbers');
    }
    final aprValue = computeAPR(loanAmount, totalInterest, fees, termYears);
    return LoanAprResult(
      apr: aprValue,
      totalCost: totalInterest + fees,
      principal: loanAmount,
      term: termYears,
    );
  }

  static LoanAmountResult loanAmount({
    required double monthlyPayment,
    required double annualRate,
    required double termMonths,
  }) {
    final payment = monthlyPayment;
    final rate = annualRate / 100 / 12;
    final term = termMonths;
    if (payment <= 0 || term <= 0) {
      throw const CalcException('Please enter valid positive numbers');
    }
    final maxLoan = rate == 0
        ? payment * term
        : payment * ((1 - math.pow(1 + rate, -term)) / rate);
    return LoanAmountResult(
        maxLoan: maxLoan.toDouble(), payment: payment, term: term);
  }

  static LoanRemainingResult remaining({
    required double originalAmount,
    required double annualRate,
    required double termMonths,
    required double paymentsMade,
  }) {
    final principal = originalAmount;
    final rate = annualRate / 100 / 12;
    final term = termMonths;
    final payments = paymentsMade;
    if (principal <= 0 || term <= 0 || payments < 0) {
      throw const CalcException('Please enter valid positive numbers');
    }
    final pmt = rate == 0
        ? principal / term
        : (principal * rate * math.pow(1 + rate, term)) /
            (math.pow(1 + rate, term) - 1);
    final balance = rate == 0
        ? principal - pmt * payments
        : principal * math.pow(1 + rate, payments) -
            pmt * ((math.pow(1 + rate, payments) - 1) / rate);
    return LoanRemainingResult(
      remainingBalance: math.max(0, balance.toDouble()),
      remainingPayments: math.max(0, term - payments),
      monthlyPayment: pmt.toDouble(),
      totalPaid: pmt.toDouble() * payments,
    );
  }
}
