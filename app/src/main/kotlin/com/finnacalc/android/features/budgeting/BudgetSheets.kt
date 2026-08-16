//
// BudgetSheets.kt
//
// Ports of the editor's companion sheets from FinnaCalcIOS:
//   · MonthPickerSheet (BudgetTabView.swift) — assign a date / browse saved
//     budgets, chevrons step the year, a 3x4 grid picks the month.
//   · CategoryCapSheet (BudgetTabView.swift) — one-field monthly cap.
//   · SaveSnapshotSheet + HistoryImportSheet (HistoryTabView.swift).
//   · AccountPickerSheet + PeriodRangeSheet (BankLedger.swift).
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.util.HistoryDate
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.calcValue
import java.time.LocalDate
import java.time.Year

// MARK: - Month picker

enum class MonthPickerMode { Assign, Browse }

/**
 * Fast month/year jump: chevrons step the year, a 3x4 grid picks the month.
 * Assign mode: every month pickable; ones holding a budget carry a checkmark
 * (with a legend) so a merge isn't a surprise. Browse mode: only saved months
 * are pickable, each showing its net; the undated budget is reachable from
 * the footer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    mode: MonthPickerMode,
    title: String,
    caption: String,
    openOn: String,
    selected: String?,
    monthNets: Map<String, Double>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onRemoveDate: (() -> Unit)? = null,
    onOpenUndated: (() -> Unit)? = null,
    undatedCount: Int = 0,
) {
    var year by remember {
        mutableStateOf(openOn.take(4).toIntOrNull() ?: Year.now().value)
    }
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val nothingToBrowse = mode == MonthPickerMode.Browse && monthNets.isEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
                Text(caption, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground, textAlign = TextAlign.Center)
            }

            if (nothingToBrowse) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 28.dp),
                ) {
                    Text("No saved budgets yet", style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                    Text(
                        "Add a date to a budget and it'll show up here.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Year header.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    YearChevron(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { year -= 1 }
                    Spacer(Modifier.weight(1f))
                    Text("$year", style = Theme.sans(17, FontWeight.Bold), color = Theme.colors.foreground)
                    Spacer(Modifier.weight(1f))
                    YearChevron(Icons.AutoMirrored.Filled.KeyboardArrowRight) { year += 1 }
                }

                // 3x4 month grid.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { rowIndex ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0..2).forEach { colIndex ->
                                val index = rowIndex * 3 + colIndex
                                val key = "%04d-%02d".format(year, index + 1)
                                val net = monthNets[key]
                                val pickable = mode == MonthPickerMode.Assign || net != null
                                val isSelected = key == selected
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) Theme.colors.primary
                                            else Theme.colors.secondary.copy(alpha = if (pickable) 0.6f else 0.25f)
                                        )
                                        .clickable(enabled = pickable) { onPick(key) }
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        monthNames[index],
                                        style = Theme.sans(Theme.FontSize.sm, if (isSelected) FontWeight.Bold else FontWeight.SemiBold),
                                        color = if (isSelected) Color.White
                                        else if (pickable) Theme.colors.foreground else Theme.colors.mutedForeground,
                                    )
                                    Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                                        if (mode == MonthPickerMode.Browse) {
                                            Text(
                                                net?.let { (if (it >= 0) "+" else "−") + Paper.compactMoney(kotlin.math.abs(it)) } ?: "—",
                                                style = Theme.figure(10, FontWeight.SemiBold),
                                                color = when {
                                                    net == null -> Theme.colors.mutedForeground
                                                    isSelected -> Color.White
                                                    net >= 0 -> Theme.colors.positive
                                                    else -> Theme.colors.negative
                                                },
                                                maxLines = 1,
                                            )
                                        } else if (net != null) {
                                            // The only marker in the grid: this month
                                            // already holds a budget.
                                            Icon(
                                                Icons.Default.CheckCircle, contentDescription = null,
                                                tint = if (isSelected) Color.White else Theme.colors.positive,
                                                modifier = Modifier.size(9.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (mode == MonthPickerMode.Assign && monthNets.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Theme.colors.positive, modifier = Modifier.size(9.dp))
                        Text("Already has a budget", style = Theme.sans(11), color = Theme.colors.mutedForeground)
                    }
                }
            }

            if (onRemoveDate != null) {
                Text(
                    "Remove date",
                    style = Theme.sans(13, FontWeight.Bold),
                    color = Theme.colors.destructive,
                    modifier = Modifier.clickable(onClick = onRemoveDate),
                )
            }

            if (onOpenUndated != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clickable(onClick = onOpenUndated),
                ) {
                    Text("Open budget with no date", style = Theme.sans(13, FontWeight.Bold), color = Theme.colors.primary)
                    Text(
                        if (undatedCount > 0) {
                            "$undatedCount line${if (undatedCount == 1) "" else "s"} not saved to a month yet"
                        } else "Empty so far",
                        style = Theme.sans(11),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }
    }
}

@Composable
private fun YearChevron(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Theme.colors.secondary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Theme.colors.mutedForeground, modifier = Modifier.size(16.dp))
    }
}

// MARK: - Category cap sheet

/** One-field sheet: a category's monthly spending cap. */
@Composable
fun CategoryCapSheet(
    category: String,
    existing: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var amount by remember { mutableStateOf(existing?.let { CalcFormat.int(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$category Cap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FCTextField(
                    "Monthly cap", amount, { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    showsPlaceholder = true,
                )
                Text(
                    "The $category bar tracks spending against this cap and flags the category when it goes over.",
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.mutedForeground,
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onSave(amount.calcValue) }, enabled = amount.calcValue > 0) { Text("Save") }
                if (existing != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove cap", color = Theme.colors.destructive) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// MARK: - Save snapshot sheet

/**
 * Mirrors the "Save Budget Snapshot" dialog: optional custom name + a
 * start/end date range (defaulting to the current month). Start required,
 * start <= end, 1 week minimum and 1 year maximum span. On save it hands
 * ISO-8601 strings up to the store.
 */
@Composable
fun SaveSnapshotSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, startIso: String, endIso: String) -> Unit,
) {
    var customName by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf(HistoryDate.monthStart.toString()) }
    var endText by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Budget Snapshot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FCTextField("Name (optional)", customName, { customName = it }, showsPlaceholder = true)
                FCTextField("Start (yyyy-mm-dd)", startText, { startText = it }, showsPlaceholder = true)
                FCTextField("End (yyyy-mm-dd)", endText, { endText = it }, showsPlaceholder = true)
                Text(
                    "Snapshots cover at least a week and at most a year. Leave the name blank to auto-name from the date range.",
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.mutedForeground,
                )
                error?.let { Text(it, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                val end = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()
                when {
                    start == null || end == null -> error = "Enter both dates as yyyy-mm-dd."
                    end.isBefore(start.plusDays(6)) -> error = "Snapshots cover at least a week."
                    end.isAfter(start.plusDays(366)) -> error = "Snapshots cover at most a year."
                    else -> {
                        val base = "Budget: ${HistoryDate.medium(start)}"
                        val defaultName = if (start == end) base else "$base - ${HistoryDate.medium(end)}"
                        val name = customName.trim().ifEmpty { defaultName }
                        onSave(name, HistoryDate.iso(start), HistoryDate.iso(end))
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// MARK: - History import sheet

/**
 * Picks a saved snapshot to pull back into the editor. Scoped to the active
 * budget type. Same shape as the History tab: newest three first, then a
 * year stepped through, split by quarter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryImportSheet(
    store: BudgetStore,
    onDismiss: () -> Unit,
    onPick: (BudgetHistoryEntry) -> Unit,
) {
    val entries = store.currentHistory
    var year by remember { mutableStateOf(Year.now().value) }
    val quarters = listOf(
        "Jan to Mar" to 1..3, "Apr to Jun" to 4..6,
        "Jul to Sep" to 7..9, "Oct to Dec" to 10..12,
    )
    fun inQuarter(months: IntRange): List<BudgetHistoryEntry> = entries.filter { entry ->
        val start = HistoryDate.parse(entry.startDate) ?: return@filter false
        start.year == year && start.monthValue in months
    }
    val recent = entries.take(3)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Import from History",
                style = Theme.sans(16, FontWeight.Bold),
                color = Theme.colors.foreground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (entries.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No saved snapshots", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
                    Text(
                        "Save a budget to History first, or import a bank statement.",
                        style = Theme.sans(13),
                        color = Theme.colors.mutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                if (recent.isNotEmpty()) {
                    HistorySection("RECENTLY ADDED", recent, onPick)
                }
                // Year pill.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YearChevron(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { year -= 1 }
                    Text("$year", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
                    YearChevron(Icons.AutoMirrored.Filled.KeyboardArrowRight) { year += 1 }
                }
                var any = false
                quarters.forEach { (title, months) ->
                    val list = inQuarter(months)
                    if (list.isNotEmpty()) {
                        any = true
                        HistorySection(title.uppercase(), list, onPick)
                    }
                }
                if (recent.isEmpty() && !any) {
                    Text(
                        "Nothing saved in $year. Use the arrows to look at another year.",
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySection(title: String, list: List<BudgetHistoryEntry>, onPick: (BudgetHistoryEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.1.sp),
            color = Theme.colors.mutedForeground,
        )
        list.forEach { entry ->
            val count = entry.budgetItems.size
            val shape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Theme.colors.card)
                    .border(1.dp, Theme.colors.border, shape)
                    .fcPressable { onPick(entry) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(entry.name, style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground, maxLines = 2)
                Text(HistoryDate.range(entry.startDate, entry.endDate), style = Theme.sans(12), color = Theme.colors.mutedForeground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$count line${if (count == 1) "" else "s"}", style = Theme.figure(11, FontWeight.SemiBold), color = Theme.colors.mutedForeground)
                    Text("+$" + CalcFormat.int(entry.monthlyIncome), style = Theme.figure(11, FontWeight.SemiBold), color = Theme.colors.positive)
                    Text("−$" + CalcFormat.int(entry.monthlyExpenses), style = Theme.figure(11, FontWeight.SemiBold), color = Theme.colors.negative)
                }
            }
        }
    }
}

// MARK: - Account picker

/**
 * Which accounts the figures cover. A sheet because this is a multiple
 * choice. Every account type is here, not just the spending ones — a card, a
 * loan or a retirement account moves money as surely as a checking account.
 * Long-press an account (or All accounts) to unlink its bank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(
    ledger: BankLedgerStore,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    ledger.version.collectAsState().value
    var pendingRemoval by remember { mutableStateOf<BankConnection?>(null) }

    // Accounts under the institution they belong to.
    val groups = run {
        val order = mutableListOf<String>()
        val map = mutableMapOf<String, MutableList<BankAccount>>()
        for (account in ledger.selectableAccounts) {
            if (map[account.institution] == null) order.add(account.institution)
            map.getOrPut(account.institution) { mutableListOf() }.add(account)
        }
        order.map { it to (map[it] ?: emptyList()) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
                Spacer(Modifier.weight(1f))
                Text(
                    "Done",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = Theme.colors.primary,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            PickerRow(
                title = "All accounts",
                subtitle = "Everything linked, plus anything you added",
                icon = Icons.Default.Layers,
                ticked = ledger.isShowingAllAccounts,
            ) { ledger.selectAllAccounts() }

            groups.forEach { (institution, accounts) ->
                Text(
                    institution.uppercase(),
                    style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.1.sp),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                accounts.forEach { account ->
                    PickerRow(
                        title = account.displayName,
                        subtitle = kindLabel(account.kind),
                        icon = kindIcon(account.kind),
                        ticked = ledger.isSelected(account.id),
                        onLongPress = {
                            ledger.connectionHolding(account.id)
                                ?.takeIf { it.id != BankLedgerStore.MANUAL_CONNECTION_ID }
                                ?.let { pendingRemoval = it }
                        },
                    ) { ledger.toggleAccount(account.id) }

                    // Lines the user typed against this account, in by default.
                    if (ledger.isSelected(account.id)) {
                        val typed = ledger.typedEntries(account.id)
                        if (typed.isNotEmpty()) {
                            val allOut = typed.all { !ledger.isEntryIncluded(it.id) }
                            Text(
                                if (allOut) "Include what you added" else "Bank transactions only",
                                style = Theme.sans(13, FontWeight.SemiBold),
                                color = Theme.colors.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { ledger.setTypedEntries(account.id, allOut) }
                                    .padding(start = 42.dp, top = 6.dp, bottom = 6.dp),
                            )
                            typed.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { ledger.toggleEntry(entry.id) }
                                        .padding(start = 42.dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(entry.name, style = Theme.sans(13, FontWeight.SemiBold), color = Theme.colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "Added by you · " + BudgetPeriod.slashed(entry.date),
                                            style = Theme.sans(11), color = Theme.colors.mutedForeground, maxLines = 1,
                                        )
                                    }
                                    if (ledger.isEntryIncluded(entry.id)) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Below its own divider: this leaves the bank behind for the
            // budget you type.
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Theme.colors.border))
            PickerRow(
                title = "Manual budget",
                subtitle = "The budget you write yourself",
                icon = Icons.Default.Edit,
                ticked = false,
            ) {
                onManual()
                onDismiss()
            }
        }
    }

    pendingRemoval?.let { connection ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${connection.institution}?") },
            text = {
                val n = connection.accounts.size
                Text(
                    "${connection.institution} and its $n account${if (n == 1) "" else "s"} are removed " +
                        "from FinnaCalc, along with the transactions they sent. Your manual budget, your " +
                        "saved history and anything you typed in yourself are kept. To get it back, " +
                        "connect the bank again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ledger.disconnect(connection.id)
                    pendingRemoval = null
                    if (!ledger.isConnected) onDismiss()
                }) { Text("Remove", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Keep it") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    ticked: Boolean,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.width(22.dp).size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold), color = Theme.colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = Theme.sans(11), color = Theme.colors.mutedForeground, maxLines = 1)
            }
        }
        if (ticked) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(15.dp))
        }
    }
}

/** Plaid's account types, said plainly. */
private fun kindLabel(kind: String?): String = when ((kind ?: "").lowercase()) {
    "depository" -> "Bank account"
    "credit" -> "Credit card"
    "loan" -> "Loan"
    "investment", "brokerage" -> "Investments"
    "" -> ""
    else -> (kind ?: "").replaceFirstChar { it.uppercase() }
}

private fun kindIcon(kind: String?): ImageVector = when ((kind ?: "").lowercase()) {
    "credit" -> Icons.Default.CreditCard
    else -> Icons.Default.AccountBalance
}

// MARK: - Period range sheet

/**
 * Two dates, for when none of the months on offer is the stretch the user
 * wants. Bounded to what the bank actually sent.
 */
@Composable
fun PeriodRangeSheet(
    bounds: Pair<String, String>?,
    current: BudgetPeriod,
    onDismiss: () -> Unit,
    onPick: (start: String, end: String) -> Unit,
) {
    val initial = if (current is BudgetPeriod.Range) current.start to current.end
    else bounds ?: (LocalDate.now().toString() to LocalDate.now().toString())
    var startText by remember { mutableStateOf(initial.first) }
    var endText by remember { mutableStateOf(initial.second) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom dates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FCTextField("From (yyyy-mm-dd)", startText, { startText = it }, showsPlaceholder = true)
                FCTextField("To (yyyy-mm-dd)", endText, { endText = it }, showsPlaceholder = true)
                bounds?.let { (start, end) ->
                    Text(
                        "Your bank sent ${BudgetPeriod.slashed(start)} to ${BudgetPeriod.slashed(end)}.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                    )
                }
                error?.let { Text(it, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.destructive) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                val end = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()
                when {
                    start == null || end == null -> error = "Enter both dates as yyyy-mm-dd."
                    end.isBefore(start) -> error = "End can't be before start."
                    else -> onPick(start.toString(), end.toString())
                }
            }) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
