import 'package:flutter_test/flutter_test.dart';
import 'package:finnacalc/features/investing/models/market_models.dart';
import 'package:finnacalc/features/investing/models/snaptrade_models.dart';
import 'package:finnacalc/features/investing/models/portfolio_models.dart';

void main() {
  test('StockDetail parses Alpha-Vantage-style numbered keys', () {
    final d = StockDetail.fromJson({
      'quote': {
        '01. symbol': 'AAPL',
        '05. price': '195.12',
        '09. change': '2.34',
        '10. change percent': '1.23%',
      },
      'overview': {
        'Name': 'Apple Inc.',
        'MarketCapitalization': '3000000000000',
        'Description': 'Apple makes phones.',
        'Logo': 'https://logo',
        'PERatio': '31.20',
      },
    });
    expect(d.symbol, 'AAPL');
    expect(d.name, 'Apple Inc.');
    expect(d.price, 195.12);
    expect(d.change, 2.34);
    expect(d.changePercent, 1.23); // % stripped
    expect(d.marketCap, '3000000000000');
    expect(d.peRatio, '31.20');
  });

  test('StockSearchResult parses numbered keys', () {
    final r = StockSearchResult.fromJson({
      '1. symbol': 'MSFT',
      '2. name': 'Microsoft Corp',
      '4. region': 'United States',
    });
    expect(r.symbol, 'MSFT');
    expect(r.name, 'Microsoft Corp');
    expect(r.region, 'United States');
  });

  test('ScreenerRow handles null numeric fields', () {
    final r = ScreenerRow.fromJson({
      'symbol': 'X',
      'company': 'X Co',
      'sector': 'Technology',
      'price': 10,
      'changePercent': -1.5,
      'marketCap': null,
      'peRatio': null,
      'dividendYield': 2.1,
      'beta': 1.2,
    });
    expect(r.price, 10);
    expect(r.changePercent, -1.5);
    expect(r.marketCap, isNull);
    expect(r.peRatio, isNull);
    expect(r.dividendYield, 2.1);
  });

  test('MarketOverview parses nested quotes + sectors', () {
    final m = MarketOverview.fromJson({
      'stocks': [
        {'symbol': 'AAPL', 'name': 'Apple', 'sector': 'Technology', 'price': 1.0}
      ],
      'gainers': [],
      'losers': [],
      'mostActive': [],
      'sectorSummary': [
        {'id': 'technology', 'name': 'Technology', 'color': 'blue', 'avgChange': 1.1, 'stockCount': 6}
      ],
    });
    expect(m.stocks, hasLength(1));
    expect(m.stocks.first.symbol, 'AAPL');
    expect(m.sectorSummary.first.name, 'Technology');
    expect(m.sectorSummary.first.stockCount, 6);
  });

  test('SnapTradeAccounts + PortfolioResponse parse and report connected/empty',
      () {
    final a = SnapTradeAccounts.fromJson({
      'configured': true,
      'accounts': [
        {'id': '1', 'name': 'Roth', 'institution': 'Schwab', 'number': '1234', 'totalValue': 1000.0, 'currency': 'USD'}
      ],
      'positions': [],
      'totalValue': 1000.0,
      'currency': 'USD',
    });
    expect(a.connected, isTrue);
    expect(a.accounts.first.institution, 'Schwab');

    final p = PortfolioResponse.fromJson({
      'holdings': [],
      'allocation': [],
      'totalValue': 0,
      'totalCostBasis': 0,
      'totalReturn': 0,
      'accountCount': 0,
      'currency': 'USD',
    });
    expect(p.isEmpty, isTrue);
  });
}
