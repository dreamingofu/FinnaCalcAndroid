import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_service.dart';
import '../core/design_system/design_system.dart';
import '../core/networking/api_client.dart';
import '../features/budgeting/budget_controller.dart';
import '../features/budgeting/plaid_service.dart';
import '../features/investing/services/market_data_service.dart';
import '../features/investing/services/portfolio_service.dart';
import '../features/investing/services/snaptrade_service.dart';
import 'navigation_shell.dart';

/// Root widget. Provides [AuthService] to the tree and applies the design
/// system theme. Defaults to light mode to match the live web app; the dark
/// palette is wired and ready for a Phase 8 toggle.
class FinnaCalcApp extends StatelessWidget {
  const FinnaCalcApp({
    super.key,
    required this.authService,
    required this.apiClient,
  });

  final AuthService authService;
  final ApiClient apiClient;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthService>.value(value: authService),
        Provider<ApiClient>.value(value: apiClient),
        ProxyProvider<ApiClient, PlaidService>(
          update: (_, api, _) => PlaidService(api),
        ),
        ChangeNotifierProvider<BudgetController>(
          create: (_) => BudgetController()..load(),
        ),
        ProxyProvider<ApiClient, MarketDataService>(
          update: (_, api, _) => MarketDataService(api),
        ),
        ProxyProvider<ApiClient, SnapTradeService>(
          update: (_, api, _) => SnapTradeService(api),
        ),
        ProxyProvider2<ApiClient, PlaidService, PortfolioService>(
          update: (_, api, plaid, _) => PortfolioService(api, plaid),
        ),
      ],
      child: MaterialApp(
        title: 'FinnaCalc',
        debugShowCheckedModeBanner: false,
        theme: FCTheme.light(),
        darkTheme: FCTheme.dark(),
        themeMode: ThemeMode.light,
        home: const NavigationShell(),
      ),
    );
  }
}
