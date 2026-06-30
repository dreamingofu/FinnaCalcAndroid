double? _dn(dynamic v) => v == null ? null : (v as num).toDouble();

/// A connected brokerage account from `GET /api/snaptrade/accounts`.
class BrokerageAccount {
  const BrokerageAccount({
    required this.id,
    required this.name,
    required this.institution,
    required this.number,
    required this.totalValue,
    required this.currency,
  });

  final String id;
  final String name;
  final String institution;
  final String number;
  final double? totalValue;
  final String currency;

  factory BrokerageAccount.fromJson(Map<String, dynamic> j) => BrokerageAccount(
        id: j['id'] as String? ?? '',
        name: j['name'] as String? ?? '',
        institution: j['institution'] as String? ?? '',
        number: j['number'] as String? ?? '',
        totalValue: _dn(j['totalValue']),
        currency: j['currency'] as String? ?? 'USD',
      );
}

/// A position within a brokerage account.
class BrokeragePosition {
  const BrokeragePosition({
    required this.accountId,
    required this.symbol,
    required this.description,
    required this.units,
    required this.price,
    required this.marketValue,
    required this.openPnl,
  });

  final String accountId;
  final String symbol;
  final String description;
  final double units;
  final double? price;
  final double? marketValue;
  final double? openPnl;

  factory BrokeragePosition.fromJson(Map<String, dynamic> j) =>
      BrokeragePosition(
        accountId: j['accountId'] as String? ?? '',
        symbol: j['symbol'] as String? ?? '—',
        description: j['description'] as String? ?? '',
        units: (j['units'] as num?)?.toDouble() ?? 0,
        price: _dn(j['price']),
        marketValue: _dn(j['marketValue']),
        openPnl: _dn(j['openPnl']),
      );
}

/// The full `GET /api/snaptrade/accounts` response.
class SnapTradeAccounts {
  const SnapTradeAccounts({
    required this.configured,
    required this.connected,
    required this.accounts,
    required this.positions,
    required this.totalValue,
    required this.currency,
    this.error,
  });

  final bool configured;
  final bool connected;
  final List<BrokerageAccount> accounts;
  final List<BrokeragePosition> positions;
  final double totalValue;
  final String currency;
  final String? error;

  factory SnapTradeAccounts.fromJson(Map<String, dynamic> j) {
    final accounts = ((j['accounts'] as List?) ?? [])
        .map((e) => BrokerageAccount.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
    return SnapTradeAccounts(
      configured: j['configured'] as bool? ?? false,
      connected: j['connected'] as bool? ?? accounts.isNotEmpty,
      accounts: accounts,
      positions: ((j['positions'] as List?) ?? [])
          .map((e) =>
              BrokeragePosition.fromJson((e as Map).cast<String, dynamic>()))
          .toList(),
      totalValue: (j['totalValue'] as num?)?.toDouble() ?? 0,
      currency: j['currency'] as String? ?? 'USD',
      error: j['error'] as String?,
    );
  }
}
