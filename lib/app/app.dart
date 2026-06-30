import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_service.dart';
import '../core/design_system/design_system.dart';
import 'navigation_shell.dart';

/// Root widget. Provides [AuthService] to the tree and applies the design
/// system theme. Defaults to light mode to match the live web app; the dark
/// palette is wired and ready for a Phase 8 toggle.
class FinnaCalcApp extends StatelessWidget {
  const FinnaCalcApp({super.key, required this.authService});

  final AuthService authService;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<AuthService>.value(
      value: authService,
      child: MaterialApp(
        title: 'FinnaCalc',
        debugShowCheckedModeBanner: false,
        theme: FCTheme.light(),
        darkTheme: FCTheme.dark(),
        themeMode: ThemeMode.light,
        home: const NavigationShell(),
      ),
    );
  }
}
