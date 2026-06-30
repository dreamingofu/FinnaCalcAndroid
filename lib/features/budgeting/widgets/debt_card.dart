import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/design_system/design_system.dart';
import '../../../core/networking/api_client.dart';
import '../../../core/util/formatters.dart';
import '../../../shared/widgets/result_widgets.dart';
import '../models/plaid_models.dart';
import '../plaid_service.dart';

enum _DebtStatus { idle, loading, ready, error }

/// A credit-utilization band: a label + colour, derived from FICO guidance.
class _UtilBand {
  const _UtilBand(this.label, this.color);
  final String label;
  final Color color;

  static _UtilBand of(double? u) {
    if (u == null) return const _UtilBand('—', FCPalette.gray500);
    if (u < 10) return const _UtilBand('Excellent', FCPalette.green600);
    if (u < 30) return const _UtilBand('Good', FCPalette.green600);
    if (u < 50) return const _UtilBand('Fair', FCPalette.yellow600);
    return const _UtilBand('High', FCPalette.red600);
  }
}

/// Connect-to-see debt & credit utilization card, mirroring the web's
/// `<DebtCard>`. Drives [PlaidService.importLiabilities] and renders summary
/// tiles, an overall utilization bar, and per-card / per-loan breakdowns.
class DebtCard extends StatefulWidget {
  const DebtCard({super.key});

  @override
  State<DebtCard> createState() => _DebtCardState();
}

class _DebtCardState extends State<DebtCard> {
  _DebtStatus _status = _DebtStatus.idle;
  LiabilitiesResponse? _data;
  String? _error;

  Future<void> _connect() async {
    setState(() {
      _status = _DebtStatus.loading;
      _error = null;
    });
    try {
      final resp = await context.read<PlaidService>().importLiabilities();
      if (!mounted) return;
      if (resp == null) {
        // User cancelled — stay idle.
        setState(() => _status = _DebtStatus.idle);
        return;
      }
      setState(() {
        _data = resp;
        _status = _DebtStatus.ready;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.message;
        _status = _DebtStatus.error;
      });
    }
  }

  void _reset() {
    setState(() {
      _data = null;
      _error = null;
      _status = _DebtStatus.idle;
    });
  }

  @override
  Widget build(BuildContext context) {
    return FCCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          FCCardHeader(children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: FCPalette.blue600,
                    borderRadius: FCRadii.smAll,
                  ),
                  child: const Icon(Icons.credit_card,
                      size: 16, color: Colors.white),
                ),
                const SizedBox(width: 10),
                const Expanded(
                  child: FCCardTitle('Debt & Credit Utilization'),
                ),
                if (_status == _DebtStatus.ready)
                  FCButton(
                    label: 'New',
                    variant: FCButtonVariant.outline,
                    size: FCButtonSize.sm,
                    icon: const Icon(Icons.refresh),
                    onPressed: _reset,
                  ),
              ],
            ),
            const FCCardDescription(
                'Powered by Plaid · soft, no credit inquiry'),
          ]),
          FCCardContent(child: _content(context)),
        ],
      ),
    );
  }

  Widget _content(BuildContext context) {
    switch (_status) {
      case _DebtStatus.ready:
        return _ready(context, _data!);
      case _DebtStatus.loading:
        return _loading(context);
      case _DebtStatus.idle:
      case _DebtStatus.error:
        return _idle(context);
    }
  }

  Widget _loading(BuildContext context) {
    final c = context.colors;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 40),
      child: Column(
        children: [
          const SizedBox(
            width: 28,
            height: 28,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              valueColor: AlwaysStoppedAnimation<Color>(FCPalette.blue600),
            ),
          ),
          const SizedBox(height: 12),
          Text('Importing your accounts…',
              style: TextStyle(
                  fontSize: FCFontSizes.sm,
                  fontWeight: FCFontWeights.medium,
                  color: c.foreground)),
          const SizedBox(height: 4),
          Text('Crunching balances and limits',
              style: TextStyle(
                  fontSize: FCFontSizes.xs, color: c.mutedForeground)),
        ],
      ),
    );
  }

  Widget _idle(BuildContext context) {
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
          child: const Icon(Icons.credit_card, size: 28, color: FCPalette.blue600),
        ),
        const SizedBox(height: 16),
        Text('See your debt & utilization',
            style: TextStyle(
                fontSize: FCFontSizes.base,
                fontWeight: FCFontWeights.semibold,
                color: c.foreground)),
        const SizedBox(height: 6),
        Text(
          'Securely link your cards and loans to see balances, APRs, minimum '
          'payments, and your credit utilization — the second biggest factor '
          'in your credit score.',
          textAlign: TextAlign.center,
          style: TextStyle(
              fontSize: FCFontSizes.sm,
              color: c.mutedForeground,
              height: 1.5),
        ),
        const SizedBox(height: 20),
        if (_error != null) ...[
          FCCalloutBanner(
            background: FCPalette.red50,
            border: FCPalette.red500.withValues(alpha: 0.3),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.error_outline,
                    size: 16, color: FCPalette.red600),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(_error!,
                      style: const TextStyle(
                          fontSize: FCFontSizes.xs, color: FCPalette.red600)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
        ],
        FCButton(
          label: 'Connect accounts',
          icon: const Icon(Icons.add_circle_outline),
          onPressed: _connect,
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
                'Bank-level encryption · soft connection, no credit inquiry',
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

  Widget _ready(BuildContext context, LiabilitiesResponse data) {
    final c = context.colors;
    final overall = _UtilBand.of(data.overallUtilization);
    final util = data.overallUtilization;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        // Summary tiles
        FCResultGrid(columns: 3, spacing: 12, children: [
          _tile(context, 'Total debt', Fmt.money0(data.totalDebt)),
          _tile(context, 'Min. payments / mo',
              Fmt.money0(data.totalMinimumPayments)),
          _tile(
            context,
            'Overall utilization',
            util != null ? '${util.toStringAsFixed(1)}%' : '—',
            valueColor: overall.color,
            sub: overall.label,
          ),
        ]),
        if (util != null) ...[
          const SizedBox(height: 20),
          FCProgressBar(
              value: util.clamp(0, 100).toDouble(),
              color: overall.color,
              height: 10),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.info_outline, size: 14, color: c.mutedForeground),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  'Keeping utilization under 30% helps your credit score.',
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: c.mutedForeground),
                ),
              ),
            ],
          ),
        ],
        if (data.creditLines.isNotEmpty) ...[
          const SizedBox(height: 24),
          _sectionLabel(context, 'Credit cards'),
          const SizedBox(height: 12),
          for (final card in data.creditLines) ...[
            _creditCard(context, card),
            const SizedBox(height: 12),
          ],
        ],
        if (data.otherDebts.isNotEmpty) ...[
          const SizedBox(height: 12),
          _sectionLabel(context, 'Loans'),
          const SizedBox(height: 12),
          for (final loan in data.otherDebts) ...[
            _loanRow(context, loan),
            const SizedBox(height: 12),
          ],
        ],
        const SizedBox(height: 4),
        Text(
          'Balances are pulled from your linked accounts via Plaid. This is a '
          'soft connection and does not affect your credit score.',
          style:
              TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
        ),
      ],
    );
  }

  Widget _sectionLabel(BuildContext context, String text) => Text(
        text,
        style: TextStyle(
            fontSize: FCFontSizes.sm,
            fontWeight: FCFontWeights.semibold,
            color: context.colors.mutedForeground),
      );

  Widget _tile(BuildContext context, String label, String value,
      {Color? valueColor, String? sub}) {
    final c = context.colors;
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
          Text(label,
              style: TextStyle(
                  fontSize: FCFontSizes.xs, color: c.mutedForeground)),
          const SizedBox(height: 4),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Flexible(
                child: Text(value,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: FCFontSizes.xl,
                        fontWeight: FCFontWeights.bold,
                        color: valueColor ?? c.foreground)),
              ),
              if (sub != null) ...[
                const SizedBox(width: 6),
                Text(sub,
                    style: TextStyle(
                        fontSize: FCFontSizes.xs,
                        fontWeight: FCFontWeights.medium,
                        color: valueColor ?? c.mutedForeground)),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _creditCard(BuildContext context, CreditLine card) {
    final c = context.colors;
    final band = _UtilBand.of(card.utilization);
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
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text.rich(
                      TextSpan(
                        text: card.name,
                        style: TextStyle(
                            fontSize: FCFontSizes.base,
                            fontWeight: FCFontWeights.semibold,
                            color: c.foreground),
                        children: [
                          if (card.mask != null)
                            TextSpan(
                              text: ' ····${card.mask}',
                              style: TextStyle(
                                  fontWeight: FCFontWeights.normal,
                                  color: c.mutedForeground),
                            ),
                        ],
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      card.limit != null
                          ? '${Fmt.money2(card.balance)} of ${Fmt.money2(card.limit!)} limit'
                          : Fmt.money2(card.balance),
                      style: TextStyle(
                          fontSize: FCFontSizes.xs, color: c.mutedForeground),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    card.utilization != null
                        ? '${card.utilization!.toStringAsFixed(0)}%'
                        : '—',
                    style: TextStyle(
                        fontSize: FCFontSizes.base,
                        fontWeight: FCFontWeights.bold,
                        color: band.color),
                  ),
                  Text('utilization',
                      style: TextStyle(
                          fontSize: 11, color: c.mutedForeground)),
                ],
              ),
            ],
          ),
          if (card.utilization != null) ...[
            const SizedBox(height: 10),
            FCProgressBar(
                value: card.utilization!.clamp(0, 100).toDouble(),
                color: band.color,
                height: 6),
          ],
          const SizedBox(height: 10),
          Wrap(
            spacing: 16,
            runSpacing: 4,
            children: [
              if (card.apr != null)
                _meta(context, 'APR', '${card.apr!.toStringAsFixed(2)}%'),
              if (card.minimumPayment != null)
                _meta(context, 'Min payment',
                    Fmt.money2(card.minimumPayment!)),
              if (card.nextDueDate != null)
                _meta(context, 'Due', card.nextDueDate!),
              if (card.isOverdue)
                const FCBadge('Overdue', variant: FCBadgeVariant.destructive),
            ],
          ),
        ],
      ),
    );
  }

  Widget _loanRow(BuildContext context, OtherDebt loan) {
    final c = context.colors;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        border: Border.all(color: c.border),
        borderRadius: FCRadii.lgAll,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: c.muted,
              borderRadius: FCRadii.smAll,
            ),
            child: Icon(
              loan.type == 'student' ? Icons.school_outlined : Icons.home_outlined,
              size: 16,
              color: c.foreground,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(loan.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: FCFontSizes.base,
                        fontWeight: FCFontWeights.semibold,
                        color: c.foreground)),
                const SizedBox(height: 2),
                Text(
                  '${loan.type} loan${loan.apr != null ? ' · ${loan.apr!.toStringAsFixed(2)}% APR' : ''}',
                  style: TextStyle(
                      fontSize: FCFontSizes.xs, color: c.mutedForeground),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(Fmt.money2(loan.balance),
                  style: TextStyle(
                      fontSize: FCFontSizes.base,
                      fontWeight: FCFontWeights.bold,
                      color: c.foreground)),
              if (loan.minimumPayment != null)
                Text('${Fmt.money2(loan.minimumPayment!)}/mo',
                    style: TextStyle(
                        fontSize: FCFontSizes.xs, color: c.mutedForeground)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _meta(BuildContext context, String label, String value) {
    final c = context.colors;
    return Text.rich(
      TextSpan(
        text: '$label: ',
        style: TextStyle(fontSize: FCFontSizes.xs, color: c.mutedForeground),
        children: [
          TextSpan(
            text: value,
            style: TextStyle(
                fontSize: FCFontSizes.xs,
                fontWeight: FCFontWeights.medium,
                color: c.foreground),
          ),
        ],
      ),
    );
  }
}
