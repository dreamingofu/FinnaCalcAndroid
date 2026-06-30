import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'engine/calculator.dart';
import 'engine/types/question.dart';
import 'engine/types/result.dart';
import 'engine/types/tax_return.dart';
import 'questions/build_return.dart';
import 'questions/question_router.dart';

/// The interview state controller — the Flutter equivalent of `useTaxEngine`.
/// Holds the [Answers], derives the [TaxReturn2024] via [buildReturn] and the
/// live [TaxCalculationResult] via [calculateFederalTax], and persists answers
/// to shared_preferences (sensitive identifiers redacted), mirroring the web.
class TaxController extends ChangeNotifier {
  TaxController([this._prefs]);

  static const _key = 'finnacalc:taxReturn:2024:answers';

  /// Answer keys whose values are sensitive and must never be persisted.
  bool _isSensitive(String key) {
    final k = key.toLowerCase();
    return k.contains('ssn') ||
        k.contains('routing') ||
        k.contains('account') ||
        k.contains('bank');
  }

  SharedPreferences? _prefs;
  Answers _answers = {};
  TaxCalculationResult? _cachedResult;

  Answers get answers => Map.unmodifiable(_answers);

  TaxReturn2024 get taxReturn => buildReturn(_answers);

  TaxCalculationResult get result =>
      _cachedResult ??= calculateFederalTax(taxReturn);

  Future<void> load() async {
    _prefs ??= await SharedPreferences.getInstance();
    final raw = _prefs?.getString(_key);
    if (raw != null && raw.isNotEmpty) {
      try {
        final decoded = (jsonDecode(raw) as Map).cast<String, Object>();
        _answers = pruneHidden(decoded);
      } catch (_) {
        _answers = {};
      }
    }
    _cachedResult = null;
    notifyListeners();
  }

  void setAnswer(String id, Object? value) {
    if (value == null) {
      _answers.remove(id);
    } else {
      _answers[id] = value;
    }
    _answers = pruneHidden(_answers);
    _cachedResult = null;
    _persist();
    notifyListeners();
  }

  void reset() {
    _answers = {};
    _cachedResult = null;
    _persist();
    notifyListeners();
  }

  void _persist() {
    final safe = <String, Object>{
      for (final e in _answers.entries)
        if (!_isSensitive(e.key)) e.key: e.value,
    };
    _prefs?.setString(_key, jsonEncode(safe));
  }
}
