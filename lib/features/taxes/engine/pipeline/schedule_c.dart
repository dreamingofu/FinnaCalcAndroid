/// Schedule C — net profit or loss from each business, split by owner
/// (taxpayer vs spouse) so self-employment tax can be figured per person.
library;

import '../types/tax_return.dart';

class ScheduleCResult {
  final ({double taxpayer, double spouse}) netByOwner;
  final double totalNet;

  const ScheduleCResult({
    required this.netByOwner,
    required this.totalNet,
  });
}

ScheduleCResult computeScheduleC(TaxReturn2024 r) {
  if (!r.income.flags.hasSelfEmployment) {
    return const ScheduleCResult(
      netByOwner: (taxpayer: 0, spouse: 0),
      totalNet: 0,
    );
  }

  var taxpayer = 0.0;
  var spouse = 0.0;
  for (final c in r.income.scheduleC) {
    final expenses = c.expenses.values.fold<double>(0, (a, b) => a + b);
    final net = c.grossReceipts -
        c.costOfGoodsSold -
        expenses -
        c.homeOfficeDeduction -
        c.vehicleExpense;
    if (c.owner == 'spouse') {
      spouse += net;
    } else {
      taxpayer += net;
    }
  }

  return ScheduleCResult(
    netByOwner: (taxpayer: taxpayer, spouse: spouse),
    totalNet: taxpayer + spouse,
  );
}
