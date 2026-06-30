/// Tax1099 e-file adapter.
///
/// WARNING: Tax1099's documented API files INFORMATION returns (1099-series,
/// W-2, 1042-S, 94x, ACA) — NOT individual Form 1040 income-tax returns.
/// Individual 1040 e-file goes through IRS Modernized e-File (MeF) and requires
/// an authorized provider + EFIN. This adapter therefore reports the capability
/// gap rather than pretending to transmit. Swap in a 1040-MeF provider behind
/// the same interface when one is available.
///
/// The API key is read server-side only (see app/api/efile/route.ts) and is
/// never shipped to the browser.
library;

import 'efile_provider.dart';

EfileProvider createTax1099Provider([String? apiKey]) {
  return _Tax1099Provider(apiKey);
}

class _Tax1099Provider implements EfileProvider {
  _Tax1099Provider(this._apiKey);

  final String? _apiKey;

  @override
  final String name = 'Tax1099';

  @override
  final bool supportsIndividual1040 = false;

  @override
  Future<EfileSubmissionResult> submit(EfileBundle bundle) async {
    if (_apiKey == null) {
      return EfileSubmissionResult(
        status: 'unsupported',
        message: 'Tax1099 API key is not configured on the server.',
      );
    }
    return EfileSubmissionResult(
      status: 'unsupported',
      message:
          "Tax1099's API files information returns (1099 / W-2 / ACA), not individual Form 1040 income-tax returns. Federal 1040 e-file requires an IRS MeF-authorized provider. Your return was not transmitted.",
    );
  }
}
