import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/core/design_system/design_system.dart';

/// Wraps [child] in a themed [MaterialApp] + [Scaffold] for widget testing.
Widget _harness(Widget child, {ThemeMode mode = ThemeMode.light}) {
  return MaterialApp(
    theme: FCTheme.light(),
    darkTheme: FCTheme.dark(),
    themeMode: mode,
    home: Scaffold(body: Center(child: child)),
  );
}

void main() {
  group('FCColors', () {
    test('light tokens match globals.css :root', () {
      // --primary: 221.2 83.2% 53.3% -> HSL conversion
      expect(FCColors.light.primary, hsl(221.2, 83.2, 53.3));
      // --background: 0 0% 100% -> white
      expect(FCColors.light.background, hsl(0, 0, 100));
    });

    test('dark tokens differ from light for primary', () {
      expect(FCColors.dark.primary, isNot(FCColors.light.primary));
      expect(FCColors.dark.background, hsl(222.2, 84, 4.9));
    });

    test('lerp(t=0) returns start, lerp(t=1) returns end', () {
      final lerped0 = FCColors.light.lerp(FCColors.dark, 0);
      final lerped1 = FCColors.light.lerp(FCColors.dark, 1);
      expect(lerped0.primary, FCColors.light.primary);
      expect(lerped1.primary, FCColors.dark.primary);
    });
  });

  group('FCTheme', () {
    testWidgets('exposes FCColors via context.colors', (tester) async {
      late FCColors captured;
      await tester.pumpWidget(_harness(Builder(builder: (context) {
        captured = context.colors;
        return const SizedBox();
      })));
      expect(captured.primary, FCColors.light.primary);
    });
  });

  group('FCButton', () {
    testWidgets('renders label and fires onPressed', (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        _harness(FCButton(label: 'Calculate', onPressed: () => taps++)),
      );
      expect(find.text('Calculate'), findsOneWidget);
      await tester.tap(find.text('Calculate'));
      expect(taps, 1);
    });

    testWidgets('disabled button does not fire', (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        _harness(const FCButton(label: 'Disabled', onPressed: null)),
      );
      await tester.tap(find.text('Disabled'));
      expect(taps, 0);
    });

    testWidgets('loading button shows spinner and blocks taps', (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        _harness(FCButton(label: 'Save', loading: true, onPressed: () => taps++)),
      );
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      await tester.tap(find.byType(FCButton));
      expect(taps, 0);
    });

    testWidgets('all variants build without error', (tester) async {
      for (final variant in FCButtonVariant.values) {
        await tester.pumpWidget(
          _harness(FCButton(label: variant.name, variant: variant, onPressed: () {})),
        );
        expect(find.text(variant.name), findsOneWidget);
      }
    });
  });

  group('FCBadge', () {
    testWidgets('renders label for every variant', (tester) async {
      for (final variant in FCBadgeVariant.values) {
        await tester.pumpWidget(_harness(FCBadge(variant.name, variant: variant)));
        expect(find.text(variant.name), findsOneWidget);
      }
    });
  });

  group('FCTextField', () {
    testWidgets('accepts input and reports changes', (tester) async {
      final controller = TextEditingController();
      String? lastChange;
      await tester.pumpWidget(_harness(FCTextField(
        controller: controller,
        onChanged: (v) => lastChange = v,
      )));
      await tester.enterText(find.byType(TextField), 'hello');
      expect(controller.text, 'hello');
      expect(lastChange, 'hello');
    });

    testWidgets('shows label, helper and error text', (tester) async {
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(_harness(const FCTextField(
        label: 'Email',
        helperText: 'optional',
      )));
      expect(find.text('Email'), findsOneWidget);
      expect(find.text('optional'), findsOneWidget);

      await tester.pumpWidget(_harness(const FCTextField(
        label: 'Email',
        errorText: 'required',
      )));
      expect(find.text('required'), findsOneWidget);
    });
  });

  group('FCCard', () {
    testWidgets('composes header/content/footer', (tester) async {
      await tester.pumpWidget(_harness(
        FCCard(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const FCCardHeader(children: [
                FCCardTitle('Title'),
                FCCardDescription('Description'),
              ]),
              const FCCardContent(child: Text('Body')),
              FCCardFooter(children: [FCButton(label: 'OK', onPressed: () {})]),
            ],
          ),
        ),
      ));
      expect(find.text('Title'), findsOneWidget);
      expect(find.text('Description'), findsOneWidget);
      expect(find.text('Body'), findsOneWidget);
      expect(find.text('OK'), findsOneWidget);
    });
  });
}
