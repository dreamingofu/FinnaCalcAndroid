import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:finnacalc/core/design_system/design_system.dart';
import 'package:finnacalc/features/calculators/loan/loan_screen.dart';
import 'package:finnacalc/features/home/home_screen.dart';

Widget _app(Widget home) => MaterialApp(theme: FCTheme.light(), home: home);

void main() {
  testWidgets('Loan screen computes a payment end-to-end', (tester) async {
    await tester.pumpWidget(_app(const LoanCalculatorScreen()));
    await tester.pumpAndSettle();

    // Fields in tree order on the Payment tab: loanAmount, interestRate,
    // loanTerm, downPayment.
    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), '50000');
    await tester.enterText(fields.at(1), '5.5');
    await tester.enterText(fields.at(2), '60');

    final calcBtn = find.text('Calculate Payment');
    await tester.ensureVisible(calcBtn);
    await tester.tap(calcBtn);
    await tester.pumpAndSettle();

    expect(find.text('Payment per Period'), findsOneWidget);
    expect(find.textContaining(r'$955'), findsOneWidget);
  });

  testWidgets('Home lists calculators and navigates on tap', (tester) async {
    await tester.pumpWidget(_app(const HomeScreen()));
    await tester.pumpAndSettle();

    expect(find.text('Loan Calculator'), findsOneWidget);
    expect(find.text('Pricing Calculator'), findsOneWidget);

    await tester.tap(find.text('Loan Calculator'));
    await tester.pumpAndSettle();

    // Landed on the loan screen (its description appears in the card header).
    expect(find.textContaining('true APR'), findsOneWidget);
  });
}
