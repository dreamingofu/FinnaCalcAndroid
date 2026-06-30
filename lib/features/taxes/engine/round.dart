/// IRS rounding helpers.
///
/// The IRS lets filers round to whole dollars: amounts under 50 cents round
/// down, 50 cents and over round up (away from zero for negatives). Apply
/// [dollar] only at the 1040 line boundaries the IRS rounds at — keep cents
/// internally.
library;

/// Round to a whole dollar, half away from zero (IRS convention).
///
/// Dart's [double.round] already rounds half away from zero, so this is
/// equivalent to `x.round().toDouble()`.
double dollar(double x) {
  if (!x.isFinite) return 0;
  return x.round().toDouble();
}

/// Clamp to non-negative (many 1040 lines are floored at zero).
double nonNeg(double x) {
  return x > 0 ? x : 0;
}

/// Sum a numeric field across a list.
double sumBy<T>(Iterable<T> items, double Function(T item) fn) {
  var acc = 0.0;
  for (final it in items) {
    final v = fn(it);
    acc += v.isNaN ? 0 : v;
  }
  return acc;
}
