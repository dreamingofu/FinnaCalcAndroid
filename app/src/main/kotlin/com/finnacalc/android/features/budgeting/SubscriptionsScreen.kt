//
// SubscriptionsScreen.kt
//
// Port of iOS Features/Budgeting/SubscriptionsView.swift — charges that
// repeat on the same schedule for about the same amount: the ones the user
// marked as subscriptions, plus any spotted in imported transactions.
//
// The Current/All scope is a viewing filter only; nobody's reminder setting
// moves when it flips. On a bank budget, Current means the ACCOUNTS the
// budget is reading (a subscription belongs to the card it bills, so it stays
// listed before it has charged this month), narrowed by the open dates when
// dates are chosen.
//
// Deviation from iOS: the reminder master switch and per-row bell schedule
// real notifications there. Here the `remind` flag is read and written on the
// schedule, and the actual scheduling lands with the notification
// infrastructure in Phase 8 — the copy says so rather than implying a
// notification that would not arrive.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.util.HistoryDate
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.CalcSegmentedControl

private enum class SubScope(val title: String) {
    // One word each: the pair sits inside the summary card's label row.
    Current("Current"),
    All("All"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionsScreen(store: BudgetStore) {
    store.version.collectAsState().value
    val bank = BankLedgerStore.shared
    bank.version.collectAsState().value

    // Current first: the screen opens on the budget the user is looking at.
    var scope by remember { mutableStateOf(SubScope.Current) }
    var pendingDismiss by remember { mutableStateOf<SubscriptionReminder?>(null) }
    var pendingRemove by remember { mutableStateOf<SubscriptionReminder?>(null) }
    var refresh by remember { mutableStateOf(0) }

    val reminders = remember(store.version.value, bank.version.value, refresh) {
        SubscriptionReminders.build(store, store.budgetType)
    }

    val visible = remember(reminders, scope, bank.version.value) {
        if (scope == SubScope.All) reminders else currentScope(reminders, store, bank)
    }
    val estimatedMonthlyTotal = visible.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Subscriptions", style = Theme.sans(17, FontWeight.SemiBold), color = Theme.colors.foreground)
        Text(
            "Charges that repeat on the same schedule for about the same amount: the ones you marked as subscriptions, plus any spotted in imported transactions.",
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
        )

        if (reminders.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("📅", style = Theme.sans(28))
                Text("Nothing recurring yet", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
                Text(
                    "Mark an expense as a subscription in My Budget, or connect a bank and we'll spot the charges that repeat.",
                    style = Theme.sans(13),
                    color = Theme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Summary card: scope switch + estimated monthly total.
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Theme.colors.card)
                    .border(1.dp, Theme.colors.border, shape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ESTIMATED MONTHLY",
                        style = Theme.sans(10, FontWeight.Bold).copy(letterSpacing = 1.sp),
                        color = Theme.colors.mutedForeground,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(width = 148.dp, height = 34.dp)) {
                        CalcSegmentedControl(scope, { scope = it }, SubScope.entries.map { it to it.title })
                    }
                }
                Text(
                    "$" + CalcFormat.int(estimatedMonthlyTotal),
                    style = Theme.figure(30, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Text(
                    "${visible.size} subscription${if (visible.size == 1) "" else "s"}" +
                        if (scope == SubScope.Current) currentScopeCaption(store, bank) else " across this budget",
                    style = Theme.sans(12),
                    color = Theme.colors.mutedForeground,
                )
            }

            if (visible.isEmpty()) {
                // Everything lives outside the budget that's open.
                Text(
                    "No subscriptions in the budget you have open. Switch to All to see every one.",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visible.forEach { reminder ->
                        SubscriptionRow(
                            reminder,
                            onRemove = {
                                if (reminder.detected) pendingDismiss = reminder else pendingRemove = reminder
                            },
                        )
                    }
                }
                Text(
                    "Reminders before a charge arrive once notifications ship. Until then this screen tracks what repeats and what it costs.",
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
    }

    pendingDismiss?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingDismiss = null },
            title = { Text("Remove from subscriptions?") },
            text = {
                Text(
                    "We listed ${reminder.name} because its charges repeat. It stops being listed; the " +
                        "transactions themselves stay. Marking one of them as a subscription in My Budget " +
                        "lists it again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    SubscriptionReminders.dismiss(reminder.id)
                    refresh += 1
                    pendingDismiss = null
                }) { Text("Remove", color = Theme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDismiss = null }) { Text("Keep it") } },
        )
    }

    pendingRemove?.let { reminder ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove from subscriptions?") },
            text = {
                Text(
                    "${reminder.name} stops being tracked as a subscription. The budget line itself stays, " +
                        "with its amount and category untouched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = reminder.itemID
                    if (id != null) {
                        val item = store.items.firstOrNull { it.id == id }
                        if (item != null) {
                            store.updateItem(item.copy(chargeSchedule = null))
                        } else {
                            // A bank charge: drop the schedule, keep the transaction.
                            val entry = BankLedgerStore.shared.entries.firstOrNull { it.id == id }
                            if (entry != null) {
                                BankLedgerStore.shared.updateEntry(
                                    id = entry.id,
                                    name = entry.name,
                                    amount = kotlin.math.abs(entry.amount),
                                    type = if (entry.amount > 0) ItemType.Expense else ItemType.Income,
                                    category = entry.category,
                                    chargeSchedule = null,
                                )
                            }
                        }
                    }
                    refresh += 1
                    pendingRemove = null
                }) { Text("Remove", color = Theme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Keep it") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionRow(reminder: SubscriptionReminder, onRemove: () -> Unit) {
    var menu by remember(reminder.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, shape)
                .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "🔁",
                style = Theme.sans(18),
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .background(Theme.colors.brandTint)
                    .padding(9.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    reminder.name,
                    style = Theme.sans(14, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${reminder.cadence.title} · next ${HistoryDate.medium(reminder.nextCharge.toLocalDate())}" +
                        if (reminder.detected) " · spotted" else "",
                    style = Theme.sans(11),
                    color = Theme.colors.mutedForeground,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$" + CalcFormat.decimal(reminder.chargeAmount, 2) + reminder.cadence.amountSuffix,
                    style = Theme.figure(13, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                )
                // The monthly-normalised figure, when it differs from the
                // per-charge one — a $10 weekly charge is $43.30 a month.
                if (reminder.cadence != ChargeCadence.Monthly) {
                    Text(
                        "$" + CalcFormat.int(reminder.amount) + "/mo",
                        style = Theme.sans(10),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Remove from subscriptions", color = Theme.colors.destructive) },
                onClick = {
                    menu = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * The reminders the Current scope shows. On a bank budget that's the accounts
 * being read (plus, when dates are chosen, only the ones charging inside
 * them); on a manual budget it's the lines the open budget contains.
 */
private fun currentScope(
    reminders: List<SubscriptionReminder>,
    store: BudgetStore,
    bank: BankLedgerStore,
): List<SubscriptionReminder> {
    if (bank.isReadingBank) {
        val picked = bank.selectedAccountIDs
        val period = bank.viewState.period
        // A hand-tagged line carries no account; it belongs to the budget
        // itself, so it stays visible.
        val onTheseAccounts = reminders.filter { r ->
            r.accountIDs.isEmpty() || r.accountIDs.any { it in picked }
        }
        if (period is BudgetPeriod.Everything) return onTheseAccounts
        // Dates chosen: the ones that CHARGE inside them. Past charges come
        // from the ledger; charges still to come are projected from the cycle.
        val chargedInWindow = bank.entries(period, picked)
            .filter { it.amount > 0 }
            .map { SubscriptionReminders.normalize(it.name) }
            .toSet()
        return onTheseAccounts.filter { r ->
            SubscriptionReminders.normalize(r.name) in chargedInWindow || r.charges(period)
        }
    }
    // A hand-typed budget has no accounts to scope by, so it matches the
    // lines the open budget actually contains.
    val names = store.currentItems
        .map { SubscriptionReminders.normalize(it.subcategory.ifEmpty { it.category }) }
        .toSet()
    return reminders.filter { SubscriptionReminders.normalize(it.name) in names }
}

/** What Current is scoped to right now, so the total never implies a wider set. */
private fun currentScopeCaption(store: BudgetStore, bank: BankLedgerStore): String {
    if (!bank.isReadingBank) return " in the budget you have open"
    val count = bank.selectedAccountIDs.size
    val accounts = if (count == 1) " on the account you have open" else " on the $count accounts you have open"
    val period = bank.viewState.period
    if (period is BudgetPeriod.Everything) return accounts
    return accounts + " over ${period.label.lowercase()}"
}
