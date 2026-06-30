/// Paths for the existing Next.js backend's `/api/*` routes (joined onto
/// `AppConfig.apiBaseUrl`). Same 14 routes the iOS/web clients use.
class ApiEndpoints {
  const ApiEndpoints._();

  // Plaid (budgeting)
  static const plaidCreateLinkToken = '/api/plaid/create-link-token';
  static const plaidTransactions = '/api/plaid/transactions';
  static const plaidHoldings = '/api/plaid/holdings';
  static const plaidLiabilities = '/api/plaid/liabilities';

  // Budget advisor (AI, streaming)
  static const budgetAdvisor = '/api/budget-advisor';

  // SnapTrade (investing)
  static const snaptradeConnect = '/api/snaptrade/connect';
  static const snaptradeAccounts = '/api/snaptrade/accounts';
  static const snaptradeDisconnect = '/api/snaptrade/disconnect';

  // Market data
  static const stock = '/api/stock';
  static const stockSearch = '/api/stock-search';
  static const screener = '/api/screener';
  static const topMovers = '/api/top-movers';
  static const marketOverview = '/api/market-overview';

  // Chat (FinnaBot, streaming)
  static const chat = '/api/chat';

  // Taxes
  static const efile = '/api/efile';
}
