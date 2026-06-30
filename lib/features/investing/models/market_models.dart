double _d(dynamic v) {
  if (v is num) return v.toDouble();
  if (v is String) return double.tryParse(v.replaceAll('%', '').trim()) ?? 0;
  return 0;
}

double? _dn(dynamic v) {
  if (v == null) return null;
  if (v is num) return v.toDouble();
  if (v is String) return double.tryParse(v.trim());
  return null;
}

/// A stock quote + profile from `GET /api/stock?symbol=` (Alpha-Vantage-style
/// numbered string keys).
class StockDetail {
  const StockDetail({
    required this.symbol,
    required this.name,
    required this.price,
    required this.change,
    required this.changePercent,
    required this.marketCap,
    required this.description,
    required this.logo,
    required this.peRatio,
  });

  final String symbol;
  final String name;
  final double price;
  final double change;
  final double changePercent;
  final String marketCap;
  final String description;
  final String logo;
  final String peRatio;

  factory StockDetail.fromJson(Map<String, dynamic> j) {
    final quote = (j['quote'] as Map?)?.cast<String, dynamic>() ?? {};
    final overview = (j['overview'] as Map?)?.cast<String, dynamic>() ?? {};
    return StockDetail(
      symbol: quote['01. symbol'] as String? ?? '',
      name: overview['Name'] as String? ?? (quote['01. symbol'] as String? ?? ''),
      price: _d(quote['05. price']),
      change: _d(quote['09. change']),
      changePercent: _d(quote['10. change percent']),
      marketCap: overview['MarketCapitalization'] as String? ?? '0',
      description: overview['Description'] as String? ?? '',
      logo: overview['Logo'] as String? ?? '',
      peRatio: overview['PERatio'] as String? ?? 'N/A',
    );
  }
}

/// One result from `GET /api/stock-search` (bare array of numbered-key objects).
class StockSearchResult {
  const StockSearchResult(
      {required this.symbol, required this.name, required this.region});

  final String symbol;
  final String name;
  final String region;

  factory StockSearchResult.fromJson(Map<String, dynamic> j) =>
      StockSearchResult(
        symbol: j['1. symbol'] as String? ?? '',
        name: j['2. name'] as String? ?? '',
        region: j['4. region'] as String? ?? 'United States',
      );
}

/// A row from `GET /api/screener` (`{ rows: [...] }`).
class ScreenerRow {
  const ScreenerRow({
    required this.symbol,
    required this.company,
    required this.sector,
    required this.price,
    required this.changePercent,
    required this.marketCap,
    required this.peRatio,
    required this.dividendYield,
    required this.beta,
  });

  final String symbol;
  final String company;
  final String sector;
  final double price;
  final double changePercent;
  final double? marketCap;
  final double? peRatio;
  final double? dividendYield;
  final double? beta;

  factory ScreenerRow.fromJson(Map<String, dynamic> j) => ScreenerRow(
        symbol: j['symbol'] as String? ?? '',
        company: j['company'] as String? ?? '',
        sector: j['sector'] as String? ?? '',
        price: _d(j['price']),
        changePercent: _d(j['changePercent']),
        marketCap: _dn(j['marketCap']),
        peRatio: _dn(j['peRatio']),
        dividendYield: _dn(j['dividendYield']),
        beta: _dn(j['beta']),
      );
}

/// A quote from `GET /api/market-overview`.
class StockQuote {
  const StockQuote({
    required this.symbol,
    required this.name,
    required this.sector,
    required this.sectorColor,
    required this.price,
    required this.change,
    required this.changesPercentage,
    required this.high,
    required this.low,
    required this.open,
    required this.previousClose,
    required this.logo,
  });

  final String symbol;
  final String name;
  final String sector;
  final String sectorColor;
  final double price;
  final double change;
  final double changesPercentage;
  final double high;
  final double low;
  final double open;
  final double previousClose;
  final String logo;

  factory StockQuote.fromJson(Map<String, dynamic> j) => StockQuote(
        symbol: j['symbol'] as String? ?? '',
        name: j['name'] as String? ?? '',
        sector: j['sector'] as String? ?? '',
        sectorColor: j['sectorColor'] as String? ?? 'slate',
        price: _d(j['price']),
        change: _d(j['change']),
        changesPercentage: _d(j['changesPercentage']),
        high: _d(j['high']),
        low: _d(j['low']),
        open: _d(j['open']),
        previousClose: _d(j['previousClose']),
        logo: j['logo'] as String? ?? '',
      );
}

class SectorSummary {
  const SectorSummary({
    required this.id,
    required this.name,
    required this.color,
    required this.avgChange,
    required this.stockCount,
  });

  final String id;
  final String name;
  final String color;
  final double avgChange;
  final int stockCount;

  factory SectorSummary.fromJson(Map<String, dynamic> j) => SectorSummary(
        id: j['id'] as String? ?? '',
        name: j['name'] as String? ?? '',
        color: j['color'] as String? ?? 'slate',
        avgChange: _d(j['avgChange']),
        stockCount: (j['stockCount'] as num?)?.toInt() ?? 0,
      );
}

/// The full `GET /api/market-overview` response.
class MarketOverview {
  const MarketOverview({
    required this.stocks,
    required this.gainers,
    required this.losers,
    required this.mostActive,
    required this.sectorSummary,
  });

  final List<StockQuote> stocks;
  final List<StockQuote> gainers;
  final List<StockQuote> losers;
  final List<StockQuote> mostActive;
  final List<SectorSummary> sectorSummary;

  static List<StockQuote> _quotes(dynamic v) =>
      ((v as List?) ?? [])
          .map((e) => StockQuote.fromJson((e as Map).cast<String, dynamic>()))
          .toList();

  factory MarketOverview.fromJson(Map<String, dynamic> j) => MarketOverview(
        stocks: _quotes(j['stocks']),
        gainers: _quotes(j['gainers']),
        losers: _quotes(j['losers']),
        mostActive: _quotes(j['mostActive']),
        sectorSummary: ((j['sectorSummary'] as List?) ?? [])
            .map((e) =>
                SectorSummary.fromJson((e as Map).cast<String, dynamic>()))
            .toList(),
      );
}
