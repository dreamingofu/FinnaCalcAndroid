import 'dart:async';

import 'package:plaid_flutter/plaid_flutter.dart';

import '../../core/networking/api_client.dart';
import '../../core/networking/api_endpoints.dart';
import 'models/plaid_models.dart';

/// Drives the stateless Plaid flow used by the web app:
/// create-link-token → run Plaid Link → POST the resulting `public_token` to a
/// data route that exchanges it and returns data in one request.
class PlaidService {
  PlaidService(this._api);

  final ApiClient _api;

  /// POST /api/plaid/create-link-token with the given product
  /// (`transactions` | `liabilities` | `investments`).
  Future<String> createLinkToken(String product) async {
    final data = await _api.postJson(
      ApiEndpoints.plaidCreateLinkToken,
      body: {'product': product},
    );
    final token = (data is Map) ? data['link_token'] as String? : null;
    if (token == null || token.isEmpty) {
      throw ApiException('Could not start the bank connection.');
    }
    return token;
  }

  /// Runs the Plaid Link SDK and resolves to the `public_token`, or null if the
  /// user exited without connecting.
  Future<String?> _linkAndGetPublicToken(String product) async {
    final linkToken = await createLinkToken(product);
    final completer = Completer<String?>();
    late final StreamSubscription<LinkSuccess> successSub;
    late final StreamSubscription<LinkExit> exitSub;

    successSub = PlaidLink.onSuccess.listen((event) {
      if (!completer.isCompleted) completer.complete(event.publicToken);
    });
    exitSub = PlaidLink.onExit.listen((event) {
      if (!completer.isCompleted) completer.complete(null);
    });

    try {
      await PlaidLink.create(
        configuration: LinkTokenConfiguration(token: linkToken),
      );
      await PlaidLink.open();
      return await completer.future;
    } finally {
      await successSub.cancel();
      await exitSub.cancel();
    }
  }

  /// Returns imported transactions, or null if the user cancelled. Throws
  /// [ApiException] on failure or when no transactions are found.
  Future<List<BankTransaction>?> importTransactions() async {
    final publicToken = await _linkAndGetPublicToken('transactions');
    if (publicToken == null) return null;
    final data = await _api.postJson(
      ApiEndpoints.plaidTransactions,
      body: {'public_token': publicToken},
    );
    final list = (data is Map) ? (data['transactions'] as List? ?? []) : [];
    final txns = list
        .map((e) =>
            BankTransaction.fromJson((e as Map).cast<String, dynamic>()))
        .toList();
    if (txns.isEmpty) {
      throw ApiException('No transactions were found on this account.');
    }
    return txns;
  }

  /// Returns liabilities, or null if the user cancelled. Throws [ApiException]
  /// on failure or when no credit/loans are found.
  Future<LiabilitiesResponse?> importLiabilities() async {
    final publicToken = await _linkAndGetPublicToken('liabilities');
    if (publicToken == null) return null;
    final data = await _api.postJson(
      ApiEndpoints.plaidLiabilities,
      body: {'public_token': publicToken},
    );
    final resp = LiabilitiesResponse.fromJson(
      (data as Map).cast<String, dynamic>(),
    );
    if (resp.isEmpty) {
      throw ApiException('No credit cards or loans were found on this account.');
    }
    return resp;
  }
}
