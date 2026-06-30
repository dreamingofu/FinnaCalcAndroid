import 'package:intl/intl.dart';

/// Number/currency formatters replicating the JS `toLocaleString` / `toFixed`
/// patterns the web calculators use (en-US grouping). Kept in one place so every
/// calculator formats identically.
class Fmt {
  const Fmt._();

  static final NumberFormat _group = NumberFormat('#,##0.###', 'en_US');
  static final NumberFormat _group0 = NumberFormat('#,##0', 'en_US');
  static final NumberFormat _group2 = NumberFormat('#,##0.00', 'en_US');

  /// `n.toLocaleString()` — grouped, up to 3 fraction digits (JS default cap).
  static String group(num n) => n.isFinite ? _group.format(n) : n.toString();

  /// `n.toLocaleString(undefined, {maximumFractionDigits: 0})` — grouped, rounded.
  static String group0(num n) => n.isFinite ? _group0.format(n) : n.toString();

  /// `n.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})`.
  static String group2(num n) => n.isFinite ? _group2.format(n) : n.toString();

  /// `$` + [group].
  static String money(num n) => '\$${group(n)}';

  /// `$` + [group0].
  static String money0(num n) => '\$${group0(n)}';

  /// `$` + [group2].
  static String money2(num n) => '\$${group2(n)}';

  /// `n.toFixed(d)` — fixed decimals, NO grouping.
  static String fixed(num n, int d) =>
      n.isFinite ? n.toStringAsFixed(d) : n.toString();

  /// `${n.toFixed(d)}%`.
  static String pct(num n, int d) => '${fixed(n, d)}%';
}
