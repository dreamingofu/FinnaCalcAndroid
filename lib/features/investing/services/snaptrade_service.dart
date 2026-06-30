import '../../../core/networking/api_client.dart';
import '../../../core/networking/api_endpoints.dart';
import '../models/snaptrade_models.dart';

/// Drives SnapTrade: POST /connect returns a hosted portal URL (opened in a
/// Custom Tab); GET /accounts lists holdings; POST /disconnect unlinks. The
/// per-user session rides the `snaptrade_session` cookie (kept by ApiClient's
/// cookie jar).
class SnapTradeService {
  SnapTradeService(this._api);

  final ApiClient _api;

  /// Returns the SnapTrade connection portal URL to open in a Custom Tab.
  Future<String> connect() async {
    final data = await _api.postJson(ApiEndpoints.snaptradeConnect);
    final uri = (data is Map) ? data['redirectURI'] as String? : null;
    if (uri == null || uri.isEmpty) {
      throw ApiException('No connection link returned.');
    }
    return uri;
  }

  Future<SnapTradeAccounts> getAccounts() async {
    final data = await _api.getJson(ApiEndpoints.snaptradeAccounts);
    return SnapTradeAccounts.fromJson((data as Map).cast<String, dynamic>());
  }

  Future<void> disconnect() async {
    await _api.postJson(ApiEndpoints.snaptradeDisconnect);
  }
}
