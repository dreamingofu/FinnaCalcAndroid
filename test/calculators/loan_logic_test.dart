import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/util/parse.dart';
import 'package:finnacalc/features/calculators/loan/loan_logic.dart';

void main() {
  group('Loan payment', () {
    test('standard 50k @5.5% over 60 months yields ~955.06/mo', () {
      final r = LoanCalculator.payment(
        loanAmount: 50000,
        downPayment: 0,
        interestRate: 5.5,
        loanTerm: 60,
        frequency: PaymentFrequency.monthly,
      );
      expect(r.basePayment, closeTo(955.06, 1.0));
      expect(r.principal, 50000);
      expect(r.totalInterest, closeTo(r.basePayment * 60 - 50000, 0.001));
    });

    test('0% interest splits principal evenly', () {
      final r = LoanCalculator.payment(
        loanAmount: 1200,
        downPayment: 0,
        interestRate: 0,
        loanTerm: 12,
        frequency: PaymentFrequency.monthly,
      );
      expect(r.basePayment, closeTo(100, 0.0001));
      expect(r.totalInterest, closeTo(0, 0.0001));
    });

    test('down payment reduces financed principal', () {
      final r = LoanCalculator.payment(
        loanAmount: 50000,
        downPayment: 10000,
        interestRate: 5.5,
        loanTerm: 60,
        frequency: PaymentFrequency.monthly,
      );
      expect(r.principal, 40000);
    });

    test('term <= 0 throws CalcException', () {
      expect(
        () => LoanCalculator.payment(
          loanAmount: 50000,
          downPayment: 0,
          interestRate: 5.5,
          loanTerm: 0,
          frequency: PaymentFrequency.monthly,
        ),
        throwsA(isA<CalcException>()),
      );
    });
  });

  group('True APR (Newton-Raphson)', () {
    // NB: the web's solver uses a deliberately approximate derivative; this port
    // reproduces it verbatim. We assert sanity invariants, not a hand-derived
    // exact rate, to stay faithful to the original behaviour.
    test('produces a finite, plausible positive rate', () {
      final r = LoanCalculator.apr(
          loanAmount: 50000, totalInterest: 5000, fees: 500, termYears: 5);
      expect(r.apr.isFinite, isTrue);
      expect(r.apr, greaterThan(0));
      expect(r.apr, lessThan(20));
      expect(r.totalCost, 5500);
    });

    test('degenerate inputs return 0 APR', () {
      // fees >= principal => net <= 0 => 0
      expect(LoanCalculator.computeAPR(1000, 100, 1000, 5), 0);
      // n <= 0 => 0
      expect(LoanCalculator.computeAPR(50000, 5000, 500, 0), 0);
    });

    test('apr() validates non-positive inputs', () {
      expect(
        () => LoanCalculator.apr(
            loanAmount: 0, totalInterest: 5000, fees: 500, termYears: 5),
        throwsA(isA<CalcException>()),
      );
    });
  });

  group('Loan amount from payment', () {
    test('matches annuity present value', () {
      final r = LoanCalculator.loanAmount(
          monthlyPayment: 500, annualRate: 5.5, termMonths: 60);
      expect(r.maxLoan, closeTo(26172.8, 50));
    });
  });

  group('Remaining balance', () {
    test('zero payments made leaves full term and ~full balance', () {
      final r = LoanCalculator.remaining(
          originalAmount: 50000,
          annualRate: 5.5,
          termMonths: 60,
          paymentsMade: 0);
      expect(r.remainingPayments, 60);
      expect(r.remainingBalance, closeTo(50000, 1.0));
      expect(r.totalPaid, 0);
    });
  });

  group('parseNum', () {
    test('empty / invalid -> fallback', () {
      expect(parseNum(''), 0);
      expect(parseNum('abc'), 0);
      expect(parseNum('12.5'), 12.5);
      expect(parseNum(null, fallback: 1), 1);
    });
  });
}
