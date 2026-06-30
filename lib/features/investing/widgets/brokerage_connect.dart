import 'package:flutter/material.dart';
import 'package:flutter_custom_tabs/flutter_custom_tabs.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../models/snaptrade_models.dart';
import '../services/snaptrade_service.dart';

/// Connect-a-broker card mirroring the web's `<BrokerageConnect>` (SnapTrade).
/// Loads accounts on init; renders loading / not-configured / connected /
/// not-connected states. Connecting opens SnapTrade's portal in a Custom Tab,
/// then reloads accounts (the session cookie is retained by the http client).
class BrokerageConnect extends StatefulWidget {
  const BrokerageConnect({super.key});

  @override
  State<BrokerageConnect> createState() => _BrokerageConnectState();
}

class _BrokerageConnectState extends State<BrokerageConnect> {
  SnapTradeAccounts? _data;
  bool _loading = true;
  bool _connecting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  String _money(double? n, String currency) {
    if (n == null) return '—';
    return NumberFormat.simpleCurrency(
            locale: 'en_US', name: currency, decimalDigits: 2)
        .format(n);
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final res = await context.read<SnapTradeService>().getAccounts();
      if (!mounted) return;
      setState(() {
        _data = res;
        _error = res.error;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _loading = false;
      });
    }
  }

  Future<void> _connect() async {
    setState(() {
      _error = null;
      _connecting = true;
    });
    try {
      final url = await context.read<SnapTradeService>().connect();
      await launchUrl(Uri.parse(url));
      // The session cookie is kept by the http client; reload accounts after the
      // user returns from the portal tab.
      if (!mounted) return;
      setState(() => _connecting = false);
      await _load();
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _connecting = false;
      });
    }
  }

  Future<void> _disconnect() async {
    try {
      await context.read<SnapTradeService>().disconnect();
    } on ApiException catch (_) {
      /* ignore — refresh reflects the real state */
    }
    if (!mounted) return;
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final data = _data;
    final hasAccounts = data?.accounts.isNotEmpty ?? false;

    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [_header(context, hasAccounts)]),
          FCCardContent(child: _content(context)),
        ],
      ),
    );
  }

  Widget _header(BuildContext context, bool hasAccounts) {
    final c = context.colors;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            color: FCPalette.blue600,
            borderRadius: FCRadii.smAll,
          ),
          child: const Icon(Icons.account_balance, size: 16, color: Colors.white),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const FCCardTitle('Your Brokerage'),
              const SizedBox(height: 4),
              Text(
                'Connect any broker to view & trade · Powered by SnapTrade',
                style: TextStyle(
                    fontSize: FCFontSizes.xs, color: c.mutedForeground),
              ),
            ],
          ),
        ),
        if (hasAccounts) ...[
          const SizedBox(width: 8),
          FCButton(
            label: 'Refresh',
            variant: FCButtonVariant.outline,
            size: FCButtonSize.sm,
            icon: const Icon(Icons.refresh),
            loading: _loading,
            onPressed: _loading ? null : _load,
          ),
          const SizedBox(width: 8),
          FCButton(
            label: 'Disconnect',
            variant: FCButtonVariant.outline,
            size: FCButtonSize.sm,
            onPressed: _disconnect,
          ),
        ],
      ],
    );
  }

  Widget _content(BuildContext context) {
    if (_loading) return _skeleton(context);
    final data = _data;
    final configured = data?.configured ?? true;
    if (!configured) return _notConfigured(context);
    if ((data?.accounts.isNotEmpty ?? false)) return _connected(context, data!);
    return _notConnected(context);
  }

  Widget _skeleton(BuildContext context) {
    final c = context.colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 0; i < 3; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          Container(
            height: 48,
            decoration: BoxDecoration(
              color: c.muted,
              borderRadius: FCRadii.lgAll,
            ),
          ),
        ],
      ],
    );
  }

  Widget _notConfigured(BuildContext context) {
    final c = context.colors;
    return FCCalloutBanner(
      background: Color.alphaBlend(c.muted.withValues(alpha: 0.5), c.card),
      border: c.border,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.info_outline, size: 16, color: c.mutedForeground),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Brokerage connections aren’t configured yet. Add '
              'SNAPTRADE_CLIENT_ID and SNAPTRADE_CONSUMER_KEY to enable '
              'connecting a broker.',
              style: TextStyle(
                  fontSize: FCFontSizes.sm, color: c.mutedForeground),
            ),
          ),
        ],
      ),
    );
  }

  Widget _connected(BuildContext context, SnapTradeAccounts data) {
    final currency = data.currency;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (_error != null) ...[
          _errorBanner(context, _error!),
          const SizedBox(height: 16),
        ],
        _accountsGrid(context, data.accounts),
        if (data.positions.isNotEmpty) ...[
          const SizedBox(height: 20),
          _positionsTable(context, data.positions, currency),
        ],
        const SizedBox(height: 16),
        Row(
          children: [
            FCButton(
              label: _connecting ? 'Opening…' : 'Connect another broker',
              variant: FCButtonVariant.outline,
              size: FCButtonSize.sm,
              icon: const Icon(Icons.add_circle_outline),
              loading: _connecting,
              onPressed: _connecting ? null : _connect,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Trading from FinnaCalc is coming next.',
                textAlign: TextAlign.right,
                style: TextStyle(
                    fontSize: FCFontSizes.xs,
                    color: context.colors.mutedForeground),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _accountsGrid(BuildContext context, List<BrokerageAccount> accounts) {
    return LayoutBuilder(builder: (context, constraints) {
      const spacing = 12.0;
      final cols = constraints.maxWidth >= 560
          ? 3
          : constraints.maxWidth >= 360
              ? 2
              : 1;
      final tileWidth = (constraints.maxWidth - spacing * (cols - 1)) / cols;
      return Wrap(
        spacing: spacing,
        runSpacing: spacing,
        children: [
          for (final a in accounts)
            SizedBox(
              width: tileWidth > 0 ? tileWidth : constraints.maxWidth,
              child: _accountCard(context, a),
            ),
        ],
      );
    });
  }

  Widget _accountCard(BuildContext context, BrokerageAccount a) {
    final c = context.colors;
    final last4 = a.number.length > 4
        ? a.number.substring(a.number.length - 4)
        : a.number;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.lgAll,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            a.institution,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style:
                TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
          ),
          const SizedBox(height: 2),
          Text.rich(
            TextSpan(
              text: a.name,
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.semibold,
                  color: c.foreground),
              children: [
                if (a.number.isNotEmpty)
                  TextSpan(
                    text: ' ····$last4',
                    style: TextStyle(
                        fontWeight: FCFontWeights.normal,
                        color: c.mutedForeground),
                  ),
              ],
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 6),
          Text(
            _money(a.totalValue, a.currency),
            style: TextStyle(
                fontSize: FCFontSizes.lg,
                fontWeight: FCFontWeights.bold,
                color: c.foreground),
          ),
        ],
      ),
    );
  }

  Widget _positionsTable(
      BuildContext context, List<BrokeragePosition> positions, String currency) {
    final c = context.colors;
    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.lgAll,
      ),
      clipBehavior: Clip.antiAlias,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minWidth: MediaQuery.sizeOf(context).width - 96,
          ),
          child: DataTable(
            headingRowHeight: 36,
            dataRowMinHeight: 44,
            dataRowMaxHeight: 56,
            horizontalMargin: 16,
            columnSpacing: 24,
            headingRowColor: WidgetStatePropertyAll(
              Color.alphaBlend(c.muted.withValues(alpha: 0.3), c.card),
            ),
            headingTextStyle: TextStyle(
                fontSize: FCFontSizes.xs,
                fontWeight: FCFontWeights.semibold,
                color: c.mutedForeground),
            dataTextStyle:
                TextStyle(fontSize: FCFontSizes.sm, color: c.foreground),
            columns: const [
              DataColumn(label: Text('Symbol')),
              DataColumn(label: Text('Units'), numeric: true),
              DataColumn(label: Text('Price'), numeric: true),
              DataColumn(label: Text('Market value'), numeric: true),
              DataColumn(label: Text('Open P/L'), numeric: true),
            ],
            rows: [
              for (final p in positions) _positionRow(context, p, currency),
            ],
          ),
        ),
      ),
    );
  }

  DataRow _positionRow(
      BuildContext context, BrokeragePosition p, String currency) {
    final c = context.colors;
    final pnl = p.openPnl;
    final pnlColor = pnl == null
        ? c.foreground
        : (pnl >= 0 ? FCPalette.green600 : FCPalette.red600);
    final pnlText = pnl == null
        ? '—'
        : '${pnl >= 0 ? '+' : ''}${_money(pnl, currency)}';
    return DataRow(cells: [
      DataCell(
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(p.symbol,
                style: TextStyle(
                    fontSize: FCFontSizes.sm,
                    fontWeight: FCFontWeights.bold,
                    color: c.foreground)),
            if (p.description.isNotEmpty)
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 200),
                child: Text(
                  p.description,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: c.mutedForeground),
                ),
              ),
          ],
        ),
      ),
      DataCell(Text(Fmt.group(p.units))),
      DataCell(Text(_money(p.price, currency))),
      DataCell(Text(
        _money(p.marketValue, currency),
        style: const TextStyle(fontWeight: FCFontWeights.medium),
      )),
      DataCell(Text(
        pnlText,
        style: TextStyle(
            fontWeight: FCFontWeights.semibold, color: pnlColor),
      )),
    ]);
  }

  Widget _notConnected(BuildContext context) {
    final c = context.colors;
    return Column(
      children: [
        const SizedBox(height: 8),
        Container(
          width: 56,
          height: 56,
          decoration: const BoxDecoration(
            color: FCPalette.blue50,
            shape: BoxShape.circle,
          ),
          child: const Icon(Icons.link, size: 28, color: FCPalette.blue600),
        ),
        const SizedBox(height: 16),
        Text('Connect the broker you already use',
            style: TextStyle(
                fontSize: FCFontSizes.base,
                fontWeight: FCFontWeights.semibold,
                color: c.foreground)),
        const SizedBox(height: 6),
        Text(
          'Link Robinhood, Webull, Schwab, Fidelity and more to see your real '
          'positions in FinnaCalc — and trade from here as we roll it out.',
          textAlign: TextAlign.center,
          style: TextStyle(
              fontSize: FCFontSizes.sm, color: c.mutedForeground, height: 1.5),
        ),
        const SizedBox(height: 20),
        if (_error != null) ...[
          _errorBanner(context, _error!),
          const SizedBox(height: 16),
        ],
        FCButton(
          label: _connecting ? 'Opening…' : 'Connect a brokerage',
          icon: _connecting ? null : const Icon(Icons.add_circle_outline),
          loading: _connecting,
          onPressed: _connecting ? null : _connect,
        ),
        const SizedBox(height: 12),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.verified_user_outlined,
                size: 14, color: c.mutedForeground),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                'Secure connection via SnapTrade · we never see your brokerage '
                'password',
                style: TextStyle(
                    fontSize: FCFontSizes.xs, color: c.mutedForeground),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
      ],
    );
  }

  Widget _errorBanner(BuildContext context, String message) {
    return FCCalloutBanner(
      background: FCPalette.red500.withValues(alpha: 0.1),
      border: FCPalette.red500.withValues(alpha: 0.3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.error_outline, size: 16, color: FCPalette.red600),
          const SizedBox(width: 8),
          Expanded(
            child: Text(message,
                style: const TextStyle(
                    fontSize: FCFontSizes.xs, color: FCPalette.red600)),
          ),
        ],
      ),
    );
  }
}
