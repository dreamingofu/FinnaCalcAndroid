import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:finnacalc/app/navigation_shell.dart';
import 'package:finnacalc/core/auth/auth_service.dart';
import 'package:finnacalc/core/design_system/design_system.dart';
import 'package:finnacalc/core/networking/api_client.dart';
import 'package:finnacalc/features/budgeting/budget_controller.dart';
import 'package:finnacalc/features/budgeting/plaid_service.dart';

void main() {
  group('AuthService (not configured)', () {
    test('start() resolves to signed-out without crashing', () {
      final auth = AuthService(configured: false);
      auth.start();
      expect(auth.loading, isFalse);
      expect(auth.user, isNull);
      expect(auth.isSignedIn, isFalse);
    });

    test('signIn throws a friendly AuthFailure when unconfigured', () async {
      final auth = AuthService(configured: false);
      await expectLater(
        auth.signIn('a@b.com', 'pw'),
        throwsA(isA<AuthFailure>()),
      );
    });

    test('signOut is a no-op when unconfigured', () async {
      final auth = AuthService(configured: false);
      await auth.signOut();
      expect(auth.user, isNull);
    });
  });

  group('NavigationShell', () {
    Widget harness() {
      final auth = AuthService(configured: false)..start();
      final api = ApiClient();
      return MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthService>.value(value: auth),
          Provider<ApiClient>.value(value: api),
          Provider<PlaidService>(create: (_) => PlaidService(api)),
          ChangeNotifierProvider<BudgetController>(
            create: (_) => BudgetController(),
          ),
        ],
        child: MaterialApp(
          theme: FCTheme.light(),
          home: const NavigationShell(),
        ),
      );
    }

    testWidgets('renders all five tab labels and the wordmark', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      for (final label in ['Home', 'Budgeting', 'Investing', 'Taxes', 'Education']) {
        expect(find.text(label), findsWidgets);
      }
      // Wordmark "Calc" span.
      expect(find.textContaining('Calc'), findsWidgets);
    });

    testWidgets('signed-out header shows Sign in / Sign up', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      expect(find.text('Sign in'), findsOneWidget);
      expect(find.text('Sign up'), findsOneWidget);
    });

    testWidgets('tapping a tab switches the visible page', (tester) async {
      await tester.pumpWidget(harness());
      await tester.pumpAndSettle();
      // Education tab is local-data only (no network/webview/providers).
      await tester.tap(find.text('Education').last);
      await tester.pumpAndSettle();
      expect(find.text('Financial Education Hub'), findsOneWidget);
    });
  });
}
