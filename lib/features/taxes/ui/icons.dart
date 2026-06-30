import 'package:flutter/material.dart';

/// Maps the engine's lucide-react icon names (used by [Section] and
/// [LifeSituation]) to the nearest Material icon. Mirrors `ui/icons.ts`.
IconData taxIconFor(String? name) {
  switch (name) {
    case 'User':
      return Icons.person_outline;
    case 'Users':
      return Icons.people_outline;
    case 'Briefcase':
      return Icons.work_outline;
    case 'Store':
      return Icons.storefront_outlined;
    case 'TrendingUp':
      return Icons.trending_up;
    case 'PiggyBank':
      return Icons.savings_outlined;
    case 'Coins':
      return Icons.monetization_on_outlined;
    case 'Sliders':
      return Icons.tune;
    case 'Receipt':
      return Icons.receipt_long_outlined;
    case 'Gift':
      return Icons.card_giftcard_outlined;
    case 'Wallet':
      return Icons.account_balance_wallet_outlined;
    case 'Home':
      return Icons.home_outlined;
    case 'GraduationCap':
      return Icons.school_outlined;
    case 'Baby':
      return Icons.child_care_outlined;
    case 'Landmark':
      return Icons.account_balance_outlined;
    case 'Zap':
      return Icons.bolt_outlined;
    default:
      return Icons.check;
  }
}
