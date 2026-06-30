import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Holds the app's [ThemeMode] and persists it. Defaults to light (matching the
/// live web app); the dark palette already exists in the design system.
class ThemeController extends ChangeNotifier {
  ThemeController([this._prefs]);

  static const _key = 'finnacalc.themeMode';

  SharedPreferences? _prefs;
  ThemeMode _mode = ThemeMode.light;

  ThemeMode get mode => _mode;
  bool get isDark => _mode == ThemeMode.dark;

  Future<void> load() async {
    _prefs ??= await SharedPreferences.getInstance();
    final raw = _prefs?.getString(_key);
    _mode = switch (raw) {
      'dark' => ThemeMode.dark,
      'light' => ThemeMode.light,
      'system' => ThemeMode.system,
      _ => ThemeMode.light,
    };
    notifyListeners();
  }

  void setMode(ThemeMode mode) {
    if (_mode == mode) return;
    _mode = mode;
    _prefs?.setString(_key, mode.name);
    notifyListeners();
  }

  void toggle() =>
      setMode(_mode == ThemeMode.dark ? ThemeMode.light : ThemeMode.dark);
}
