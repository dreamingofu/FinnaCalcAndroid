/// Mirrors the web calculators' `Number.parseFloat(x) || fallback` semantics:
/// an empty or invalid field becomes [fallback] (usually 0). The `|| 0` quirk
/// also means values that parse to 0 stay 0, and `NaN`/empty → fallback.
double parseNum(String? s, {double fallback = 0}) {
  if (s == null) return fallback;
  final v = double.tryParse(s.trim());
  if (v == null || v.isNaN) return fallback;
  return v;
}

/// Mirrors `Number.parseInt(x) || fallback`.
int parseInt(String? s, {int fallback = 0}) {
  if (s == null) return fallback;
  final v = int.tryParse(s.trim());
  if (v != null) return v;
  // parseInt also accepts leading numerics of a float string ("12.5" -> 12).
  final d = double.tryParse(s.trim());
  if (d == null || d.isNaN) return fallback;
  return d.truncate();
}

/// A user-facing validation failure raised by calculator logic; the screen
/// shows [message] in place of results (mirrors the web's `{ error }`).
class CalcException implements Exception {
  const CalcException(this.message);
  final String message;
  @override
  String toString() => message;
}
