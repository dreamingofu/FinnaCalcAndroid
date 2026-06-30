double? _dn(dynamic v) => v == null ? null : (v as num).toDouble();
double _d(dynamic v) => (v as num?)?.toDouble() ?? 0;

/// A holding from `POST /api/plaid/holdings`.
class PortfolioHolding {
  const PortfolioHolding({
    required this.securityId,
    required this.name,
    required this.fullName,
    required this.type,
    required this.value,
    required this.quantity,
    required this.price,
    required this.avgCost,
    required this.costBasis,
    required this.totalReturn,
    required this.totalReturnPct,
    required this.weight,
  });

  final String securityId;
  final String name;
  final String fullName;
  final String type;
  final double value;
  final double quantity;
  final double price;
  final double avgCost;
  final double costBasis;
  final double totalReturn;
  final double? totalReturnPct;
  final double weight;

  factory PortfolioHolding.fromJson(Map<String, dynamic> j) => PortfolioHolding(
        securityId: j['securityId'] as String? ?? '',
        name: j['name'] as String? ?? '',
        fullName: j['fullName'] as String? ?? '',
        type: j['type'] as String? ?? 'Other',
        value: _d(j['value']),
        quantity: _d(j['quantity']),
        price: _d(j['price']),
        avgCost: _d(j['avgCost']),
        costBasis: _d(j['costBasis']),
        totalReturn: _d(j['totalReturn']),
        totalReturnPct: _dn(j['totalReturnPct']),
        weight: _d(j['weight']),
      );
}

class AllocationSlice {
  const AllocationSlice({required this.type, required this.value});
  final String type;
  final double value;

  factory AllocationSlice.fromJson(Map<String, dynamic> j) => AllocationSlice(
        type: j['type'] as String? ?? 'Other',
        value: _d(j['value']),
      );
}

/// The full `POST /api/plaid/holdings` response.
class PortfolioResponse {
  const PortfolioResponse({
    required this.holdings,
    required this.allocation,
    required this.totalValue,
    required this.totalCostBasis,
    required this.totalReturn,
    required this.totalReturnPct,
    required this.accountCount,
    required this.currency,
  });

  final List<PortfolioHolding> holdings;
  final List<AllocationSlice> allocation;
  final double totalValue;
  final double totalCostBasis;
  final double totalReturn;
  final double? totalReturnPct;
  final int accountCount;
  final String currency;

  bool get isEmpty => holdings.isEmpty;

  factory PortfolioResponse.fromJson(Map<String, dynamic> j) =>
      PortfolioResponse(
        holdings: ((j['holdings'] as List?) ?? [])
            .map((e) =>
                PortfolioHolding.fromJson((e as Map).cast<String, dynamic>()))
            .toList(),
        allocation: ((j['allocation'] as List?) ?? [])
            .map((e) =>
                AllocationSlice.fromJson((e as Map).cast<String, dynamic>()))
            .toList(),
        totalValue: _d(j['totalValue']),
        totalCostBasis: _d(j['totalCostBasis']),
        totalReturn: _d(j['totalReturn']),
        totalReturnPct: _dn(j['totalReturnPct']),
        accountCount: (j['accountCount'] as num?)?.toInt() ?? 0,
        currency: j['currency'] as String? ?? 'USD',
      );
}
