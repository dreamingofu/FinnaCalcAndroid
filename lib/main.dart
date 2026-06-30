import 'package:flutter/material.dart';

import 'core/design_system/design_system.dart';
import 'features/dev/design_gallery.dart';

void main() {
  runApp(const FinnaCalcApp());
}

/// Phase 1 entry point. Applies the design-system theme and shows the design
/// gallery. The real navigation shell replaces `home` in Phase 2.
class FinnaCalcApp extends StatefulWidget {
  const FinnaCalcApp({super.key});

  @override
  State<FinnaCalcApp> createState() => _FinnaCalcAppState();
}

class _FinnaCalcAppState extends State<FinnaCalcApp> {
  ThemeMode _themeMode = ThemeMode.light;

  void _toggleTheme() {
    setState(() {
      _themeMode =
          _themeMode == ThemeMode.light ? ThemeMode.dark : ThemeMode.light;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FinnaCalc',
      debugShowCheckedModeBanner: false,
      theme: FCTheme.light(),
      darkTheme: FCTheme.dark(),
      themeMode: _themeMode,
      home: DesignGallery(onToggleTheme: _toggleTheme),
    );
  }
}
