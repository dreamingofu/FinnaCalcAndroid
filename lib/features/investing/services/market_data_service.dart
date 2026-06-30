import '../../../core/networking/api_client.dart';
import '../../../core/networking/api_endpoints.dart';
import '../models/market_models.dart';

/// Reads the market-data routes (`/api/stock*`, `/api/screener`,
/// `/api/market-overview`). These take no auth on the web.
class MarketDataService {
  MarketDataService(this._api);

  final ApiClient _api;

  Future<StockDetail> getStock(String symbol) async {
    final data = await _api.getJson(ApiEndpoints.stock, query: {'symbol': symbol});
    return StockDetail.fromJson((data as Map).cast<String, dynamic>());
  }

  Future<List<StockSearchResult>> searchStocks(String keywords) async {
    final data =
        await _api.getJson(ApiEndpoints.stockSearch, query: {'keywords': keywords});
    final list = (data as List?) ?? [];
    return list
        .map((e) =>
            StockSearchResult.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  Future<List<ScreenerRow>> getScreener() async {
    final data = await _api.getJson(ApiEndpoints.screener);
    final rows = (data is Map) ? (data['rows'] as List? ?? []) : [];
    return rows
        .map((e) => ScreenerRow.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
  }

  Future<MarketOverview> getMarketOverview() async {
    final data = await _api.getJson(ApiEndpoints.marketOverview);
    return MarketOverview.fromJson((data as Map).cast<String, dynamic>());
  }
}
