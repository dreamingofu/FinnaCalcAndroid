import '../../../core/networking/api_client.dart';
import '../../../core/networking/api_endpoints.dart';
import '../../budgeting/plaid_service.dart';
import '../models/portfolio_models.dart';

/// Imports investment holdings via Plaid (`investments` product), reusing the
/// shared Plaid Link flow.
class PortfolioService {
  PortfolioService(this._api, this._plaid);

  final ApiClient _api;
  final PlaidService _plaid;

  /// Runs Plaid Link for investments and returns the portfolio, or null if the
  /// user cancelled. Throws [ApiException] on failure.
  Future<PortfolioResponse?> importHoldings() async {
    final publicToken = await _plaid.linkAndGetPublicToken('investments');
    if (publicToken == null) return null;
    final data = await _api.postJson(
      ApiEndpoints.plaidHoldings,
      body: {'public_token': publicToken},
    );
    return PortfolioResponse.fromJson((data as Map).cast<String, dynamic>());
  }
}
