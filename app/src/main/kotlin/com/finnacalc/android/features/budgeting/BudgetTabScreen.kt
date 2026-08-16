//
// BudgetTabScreen.kt
//
// Port of iOS Features/Budgeting/BudgetTabView.swift — the "My Budget"
// editor: month pill → Income/Expenses segmented tabs with live totals →
// collapsible category list (top-3 per category + "View all") → "Where it
// goes" donut summary → floating Add button. Bank actions (Plaid link, CSV
// statement import) and Save-to-History live as toolbar icons.
//
// Budgets are slot-keyed (BudgetItem.month): a "yyyy-MM" month or the undated
// working slot. The date pill's menu keeps "Add/Edit date" (restamps lines)
// apart from "View other budgets" (navigates). When a bank is being read, the
// pill halves become the period and the account picker instead.
//
// Deviations from iOS, with reasons:
//  · Swipe-to-delete rows become long-press menus (Edit/Delete) — the Android
//    idiom; a swipe container fighting vertical scroll costs more than it
//    gives.
//  · SubscriptionNotifier.reconcile call sites are omitted until the
//    notification infrastructure lands (Phase 8).
//

package com.finnacalc.android.features.budgeting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.networking.ApiException
import com.finnacalc.android.core.plaid.PlaidProduct
import com.finnacalc.android.core.plaid.PlaidService
import com.finnacalc.android.core.util.HistoryDate
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.calcValue
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// Category groups reused by the lists and the donut.
internal data class CategoryGroup(
    val category: String,
    val items: List<BudgetItem>,
    val total: Double,
)

internal fun groupByCategory(items: List<BudgetItem>, type: ItemType): List<CategoryGroup> {
    val order = mutableListOf<String>()
    val map = mutableMapOf<String, MutableList<BudgetItem>>()
    for (item in items) {
        if (item.type != type) continue
        if (map[item.category] == null) order.add(item.category)
        map.getOrPut(item.category) { mutableListOf() }.add(item)
    }
    return order.map { name ->
        val list = map[name] ?: emptyList()
        CategoryGroup(name, list, list.sumOf { it.monthlyAmount })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetTabScreen(store: BudgetStore, push: (BudgetingDest) -> Unit) {
    store.version.collectAsState().value
    val bank = BankLedgerStore.shared
    bank.version.collectAsState().value

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // MARK: State

    var month by rememberSaveable { mutableStateOf(store.rememberedSlot) }
    var tab by rememberSaveable { mutableStateOf(ItemType.Expense) }
    var expandedCategories by rememberSaveable { mutableStateOf(setOf<String>()) }
    var expandedSlices by rememberSaveable { mutableStateOf(setOf<String>()) }
    var didAutoExpand by rememberSaveable { mutableStateOf(false) }

    var draft by remember { mutableStateOf<ItemDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<BudgetItem?>(null) }
    var capDraftCategory by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showBudgetBrowser by remember { mutableStateOf(false) }
    var confirmNewBudget by remember { mutableStateOf(false) }
    var pendingMerge by remember { mutableStateOf<String?>(null) }
    var pendingDeleteBudget by remember { mutableStateOf<String?>(null) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var showHistoryImporter by remember { mutableStateOf(false) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<List<BudgetItem>, String>?>(null) }
    var pendingBankImport by remember { mutableStateOf<Pair<List<BudgetItem>, String>?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccess by remember { mutableStateOf<String?>(null) }

    val readingBank = bank.isReadingBank
    val hasDate = BudgetStore.isDated(month)

    // The lines the whole screen runs on.
    val monthItems = if (readingBank) bank.currentItems(store.budgetType) else store.itemsInMonth(month)
    val monthIncome = monthItems.filter { it.type == ItemType.Income }.sumOf { it.monthlyAmount }
    val monthExpenses = monthItems.filter { it.type == ItemType.Expense }.sumOf { it.monthlyAmount }
    val undatedLineCount = store.itemsInMonth(BudgetStore.UNDATED_MONTH_KEY).size

    fun openSlot(slot: String) {
        month = slot
        store.rememberSlot(slot)
    }

    // Over-cap categories start expanded (once), per the handoff.
    if (!didAutoExpand) {
        didAutoExpand = true
        val autoExpand = groupByCategory(monthItems, ItemType.Expense)
            .filter { g -> store.cap(g.category)?.let { g.total > it } == true }
            .map { it.category }
        if (autoExpand.isNotEmpty()) expandedCategories = expandedCategories + autoExpand
    }

    // MARK: Imports

    fun applyImport(items: List<BudgetItem>, combine: Boolean, source: String) {
        if (!combine) expandedCategories = emptySet()
        store.landImport(items, combine)
        openSlot(BudgetStore.UNDATED_MONTH_KEY)
        importError = null
        importSuccess = "Imported ${items.size} line${if (items.size == 1) "" else "s"} from $source."
    }

    fun beginManualImport(items: List<BudgetItem>, source: String) {
        if (undatedLineCount > 0) pendingImport = items to source
        else applyImport(items, combine = false, source = source)
    }

    fun beginImport(items: List<BudgetItem>, source: String) {
        if (items.isEmpty()) return
        // On a bank, ask about the budget actually on screen first.
        if (readingBank) pendingBankImport = items to source
        else beginManualImport(items, source)
    }

    /** Today, unless today falls outside the stretch on screen — then its last day. */
    fun dateForNewLine(): String {
        val today = LocalDate.now().toString()
        if (bank.viewState.period.contains(today)) return today
        return when (val period = bank.viewState.period) {
            is BudgetPeriod.Everything -> today
            is BudgetPeriod.Month -> {
                val ym = runCatching { YearMonth.parse(period.key) }.getOrNull() ?: return today
                "${period.key}-%02d".format(ym.lengthOfMonth())
            }
            is BudgetPeriod.Range -> period.end.take(10)
        }
    }

    fun addToBank(items: List<BudgetItem>) {
        for (item in items) {
            val name = item.subcategory.ifEmpty { item.category }
            bank.addManual(
                name = name, amount = item.amount, type = item.type, category = item.category,
                date = item.importDate?.take(10) ?: dateForNewLine(),
            )
        }
        importSuccess = "Added ${items.size} line${if (items.size == 1) "" else "s"}."
    }

    // MARK: Slot actions

    fun applyDate(target: String, replacing: Boolean = false) {
        if (replacing) store.clearMonth(target)
        store.moveItems(month, target)
        openSlot(target)
    }

    fun assignDate(target: String) {
        if (target == month) return
        if (monthItems.isNotEmpty() && store.itemsInMonth(target).isNotEmpty()) {
            pendingMerge = target
        } else {
            applyDate(target)
        }
    }

    fun startNewBudget() {
        store.clearMonth(BudgetStore.UNDATED_MONTH_KEY)
        expandedCategories = emptySet()
        openSlot(BudgetStore.UNDATED_MONTH_KEY)
    }

    fun removeItem(item: BudgetItem) {
        if (readingBank) bank.deleteEntry(item.id) else store.deleteItem(item)
    }

    fun saveDraft(d: ItemDraft) {
        val amount = d.amount.calcValue
        if (readingBank) {
            val name = d.subcategory.ifEmpty { d.category }
            val schedule = d.resolvedSchedule()
            if (d.itemID != null) {
                bank.updateEntry(d.itemID, name, amount, d.type, d.category, schedule)
            } else {
                bank.addManual(name, amount, d.type, d.category, dateForNewLine(), chargeSchedule = schedule)
            }
            return
        }
        if (d.itemID != null) {
            store.items.firstOrNull { it.id == d.itemID }?.let { existing ->
                store.updateItem(existing.applyDraft(d))
            }
        } else {
            val schedule = d.resolvedSchedule()
            store.addItem(
                BudgetItem(
                    id = store.newItemID(),
                    category = d.category,
                    subcategory = d.subcategory,
                    amount = amount,
                    frequency = schedule?.frequency ?: d.frequency,
                    type = d.type,
                    isFixed = d.isFixed,
                    budgetType = store.budgetType,
                    importDate = null,
                    month = month,
                    chargeSchedule = schedule,
                )
            )
        }
    }

    // MARK: Plaid + CSV

    val plaid = rememberPlaidLink(
        onSuccess = { publicToken ->
            scope.launch {
                try {
                    val result = PlaidService.importTransactions(publicToken)
                    if (result.transactions.isEmpty()) {
                        importError = "No transactions were found on this account."
                    } else {
                        // Kept in the ledger, not snapshotted — so this screen
                        // can be drawn for any period and any account.
                        bank.record(result)
                        val count = result.transactions.size
                        importSuccess = "Linked $count transaction${if (count == 1) "" else "s"}. " +
                            "Use the account and date buttons to narrow it down."
                    }
                } catch (e: ApiException) {
                    importError = e.message
                }
                importing = false
            }
        },
        onExit = { importing = false },
    )

    fun connectBank() {
        importing = true
        importError = null
        importSuccess = null
        scope.launch {
            try {
                val token = PlaidService.createLinkToken(PlaidProduct.Transactions)
                plaid.open(token)
            } catch (e: ApiException) {
                importError = e.message
                importing = false
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importError = null
        importSuccess = null
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: throw BankStatementParser.ParseException("Couldn't read that file as text.")
            beginImport(BankStatementParser.parse(content, store.budgetType), "the statement")
        } catch (e: BankStatementParser.ParseException) {
            importError = e.message
        } catch (_: Exception) {
            importError = "Couldn't read that file as text."
        }
    }

    // MARK: Layout

    Column(Modifier.fillMaxSize().background(Theme.colors.background)) {
        // Header bar: back handled by shell; title + bank/history toolbar menus.
        EditorTopBar(
            importing = importing,
            onConnectBank = { connectBank() },
            onImportCsv = { csvPicker.launch(arrayOf("text/*", "text/comma-separated-values", "text/csv")) },
            onSaveSnapshot = { showSaveSheet = true },
            saveEnabled = monthItems.isNotEmpty(),
            onImportHistory = { showHistoryImporter = true },
        )

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp, bottom = 84.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MonthPillRow(
                    store = store, bank = bank, month = month, hasDate = hasDate,
                    readingBank = readingBank, undatedLineCount = undatedLineCount,
                    onShowDatePicker = { showDatePicker = true },
                    onShowBrowser = { showBudgetBrowser = true },
                    onNewBudget = { if (undatedLineCount > 0) confirmNewBudget = true else startNewBudget() },
                    onDeleteBudget = { pendingDeleteBudget = month },
                    onShowAccountPicker = { showAccountPicker = true },
                    onPickPeriod = { bank.viewState = bank.viewState.copy(period = it) },
                    onShowPeriodPicker = { showPeriodPicker = true },
                )

                DirectionTabs(tab, monthIncome, monthExpenses) { tab = it }

                importError?.let {
                    Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.destructive)
                }
                importSuccess?.let {
                    Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.positive)
                }
                if (importing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Theme.colors.primary, strokeWidth = 2.dp)
                        Text("Importing…", style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
                    }
                }

                val direction = tab
                val groups = groupByCategory(monthItems, direction)
                if (groups.isEmpty()) {
                    EmptyInvite(direction, readingBank) { draft = ItemDraft(type = direction) }
                } else {
                    CategoryListCard(
                        store = store, groups = groups, direction = direction,
                        denominator = if (direction == ItemType.Expense) monthExpenses else monthIncome,
                        readingBank = readingBank,
                        expanded = expandedCategories,
                        onToggle = { key ->
                            expandedCategories = if (key in expandedCategories) {
                                expandedCategories - key
                            } else {
                                expandedCategories + key
                            }
                        },
                        onCollapseAll = { expandedCategories = emptySet() },
                        onAdd = { draft = ItemDraft(type = direction) },
                        onEdit = { draft = ItemDraft.from(it) },
                        onDelete = { pendingDelete = it },
                        onSetCap = { capDraftCategory = it },
                        onRemoveCap = { store.setCap(null, it) },
                        onViewAll = { category ->
                            push(BudgetingDest.CategoryItems(category, direction, readingBank))
                        },
                    )
                }

                // Summary donut follows the active tab.
                val denominator = if (direction == ItemType.Expense) monthExpenses else monthIncome
                if (denominator > 0) {
                    SummaryDonutCard(
                        direction = direction,
                        groups = groups.sortedByDescending { it.total },
                        denominator = denominator,
                        expandedSlices = expandedSlices,
                        onToggleSlice = { key ->
                            expandedSlices = if (key in expandedSlices) expandedSlices - key else expandedSlices + key
                        },
                        onViewAll = { category ->
                            push(BudgetingDest.CategoryItems(category, direction, readingBank))
                        },
                    )
                }
            }

            // Floating Add — always present. A bank shows what it saw, which
            // is never everything: cash still belongs in the budget.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 22.dp, vertical = 10.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.primary)
                    .fcPressable { draft = ItemDraft(type = tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add item", tint = Color.White)
            }
        }
    }

    // MARK: Sheets & dialogs

    draft?.let { d ->
        BudgetItemSheet(
            store = store,
            initial = d,
            onDismiss = { draft = null },
            onSave = { finished ->
                saveDraft(finished)
                draft = null
            },
        )
    }

    capDraftCategory?.let { category ->
        CategoryCapSheet(
            category = category,
            existing = store.cap(category),
            onDismiss = { capDraftCategory = null },
            onSave = { amount ->
                store.setCap(amount, category)
                capDraftCategory = null
            },
        )
    }

    if (showDatePicker) {
        MonthPickerSheet(
            mode = MonthPickerMode.Assign,
            title = if (hasDate) "Edit date" else "Add date",
            caption = if (hasDate) "Move this budget to another month."
            else "Save this budget to a month so you can come back to it.",
            openOn = if (hasDate) month else BudgetStore.currentMonthKey,
            selected = if (hasDate) month else null,
            monthNets = store.savedMonthNets,
            onDismiss = { showDatePicker = false },
            onPick = {
                showDatePicker = false
                assignDate(it)
            },
            onRemoveDate = if (hasDate) {
                {
                    showDatePicker = false
                    assignDate(BudgetStore.UNDATED_MONTH_KEY)
                }
            } else null,
        )
    }

    if (showBudgetBrowser) {
        MonthPickerSheet(
            mode = MonthPickerMode.Browse,
            title = "Other budgets",
            caption = "Open a budget you've saved to a month.",
            openOn = if (hasDate) month else (store.savedMonths.firstOrNull() ?: BudgetStore.currentMonthKey),
            selected = if (hasDate) month else null,
            monthNets = store.savedMonthNets,
            onDismiss = { showBudgetBrowser = false },
            onPick = {
                showBudgetBrowser = false
                openSlot(it)
            },
            onOpenUndated = if (hasDate) {
                {
                    showBudgetBrowser = false
                    openSlot(BudgetStore.UNDATED_MONTH_KEY)
                }
            } else null,
            undatedCount = undatedLineCount,
        )
    }

    if (showSaveSheet) {
        SaveSnapshotSheet(
            onDismiss = { showSaveSheet = false },
            onSave = { name, start, end ->
                // On a bank, save what is on screen.
                store.saveSnapshot(
                    name, start, end, month = month,
                    lines = if (readingBank) monthItems else null,
                )
                showSaveSheet = false
            },
        )
    }

    if (showHistoryImporter) {
        HistoryImportSheet(
            store = store,
            onDismiss = { showHistoryImporter = false },
            onPick = { entry ->
                showHistoryImporter = false
                beginImport(store.itemsFromSnapshot(entry), "“${entry.name}”")
            },
        )
    }

    if (showPeriodPicker) {
        PeriodRangeSheet(
            bounds = bank.dateBounds,
            current = bank.viewState.period,
            onDismiss = { showPeriodPicker = false },
            onPick = { start, end ->
                bank.viewState = bank.viewState.copy(period = BudgetPeriod.Range(start, end))
                showPeriodPicker = false
            },
        )
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            ledger = bank,
            onManual = { bank.showManualBudget() },
            onDismiss = { showAccountPicker = false },
        )
    }

    if (confirmNewBudget) {
        AlertDialog(
            onDismissRequest = { confirmNewBudget = false },
            title = { Text("Start a new budget?") },
            text = {
                Text(
                    "Your budget with no date isn't saved anywhere, so starting a new one clears it. " +
                        "Budgets you've saved to a month are kept. To hold on to this one first, you can " +
                        "assign it to a month, or save it to History under any date range you like: " +
                        "either, both, or neither."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    startNewBudget()
                    confirmNewBudget = false
                }) {
                    Text(
                        "Discard $undatedLineCount line${if (undatedLineCount == 1) "" else "s"} and start new",
                        color = Theme.colors.destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewBudget = false }) { Text("Keep my budget") }
            },
        )
    }

    pendingMerge?.let { target ->
        val moving = monthItems.size
        val existing = store.itemsInMonth(target).size
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("Combine these budgets?") },
            text = {
                Text(
                    "${BudgetStore.monthDisplayName(target)} already has a budget. Combine adds these " +
                        "$moving line${if (moving == 1) "" else "s"} to the $existing already there; " +
                        "replace deletes those $existing first."
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        applyDate(target)
                        pendingMerge = null
                    }) { Text("Combine them") }
                    TextButton(onClick = {
                        applyDate(target, replacing = true)
                        pendingMerge = null
                    }) { Text("Replace what's there", color = Theme.colors.destructive) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMerge = null }) { Text("Cancel") }
            },
        )
    }

    pendingDeleteBudget?.let { target ->
        val count = store.itemsInMonth(target).size
        AlertDialog(
            onDismissRequest = { pendingDeleteBudget = null },
            title = { Text("Delete this budget?") },
            text = {
                Text(
                    "Everything saved to ${BudgetStore.monthDisplayName(target)} is removed. Your other " +
                        "months, goals, saved history and category caps are kept. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearMonth(target)
                    openSlot(BudgetStore.UNDATED_MONTH_KEY)
                    pendingDeleteBudget = null
                }) { Text("Delete $count line${if (count == 1) "" else "s"}", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBudget = null }) { Text("Keep it") }
            },
        )
    }

    pendingImport?.let { (items, source) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("You already have a budget with no date") },
            text = {
                Text(
                    "Combine adds these ${items.size} imported line${if (items.size == 1) "" else "s"} to it. " +
                        "Starting new discards it, since it isn't saved anywhere, so date it or save it to " +
                        "History first if you want to keep it. Budgets saved to a month aren't affected " +
                        "either way."
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        applyImport(items, combine = true, source = source)
                        pendingImport = null
                    }) { Text("Combine them") }
                    TextButton(onClick = {
                        applyImport(items, combine = false, source = source)
                        pendingImport = null
                    }) {
                        Text(
                            "Discard $undatedLineCount line${if (undatedLineCount == 1) "" else "s"} and start new",
                            color = Theme.colors.destructive,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }

    pendingBankImport?.let { (items, source) ->
        AlertDialog(
            onDismissRequest = { pendingBankImport = null },
            title = { Text("You're on ${bank.sourceLabel}") },
            text = {
                Text(
                    "${items.size} line${if (items.size == 1) "" else "s"} from $source. Adding them here " +
                        "files them alongside your transactions, dated as they are. The manual budget is " +
                        "the one you write yourself, and it asks its own question next."
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        addToBank(items)
                        pendingBankImport = null
                    }) { Text("Add to ${bank.sourceLabel}") }
                    TextButton(onClick = {
                        bank.showManualBudget()
                        pendingBankImport = null
                        beginManualImport(items, source)
                    }) { Text("Put it on the manual budget") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBankImport = null }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this item?") },
            confirmButton = {
                TextButton(onClick = {
                    removeItem(item)
                    pendingDelete = null
                }) {
                    Text(
                        "Delete ${item.subcategory.ifEmpty { item.category }}",
                        color = Theme.colors.destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

// MARK: - Top bar

@Composable
private fun EditorTopBar(
    importing: Boolean,
    onConnectBank: () -> Unit,
    onImportCsv: () -> Unit,
    onSaveSnapshot: () -> Unit,
    saveEnabled: Boolean,
    onImportHistory: () -> Unit,
) {
    var bankMenu by remember { mutableStateOf(false) }
    var historyMenu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "My Budget",
            style = Theme.sans(17, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { bankMenu = true }, enabled = !importing) {
                Icon(Icons.Default.AccountBalance, contentDescription = "Bank actions", tint = Theme.colors.primary)
            }
            DropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Connect Bank") },
                    onClick = {
                        bankMenu = false
                        onConnectBank()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Import Bank Statement") },
                    onClick = {
                        bankMenu = false
                        onImportCsv()
                    },
                )
            }
        }
        Box {
            IconButton(onClick = { historyMenu = true }, enabled = !importing) {
                Icon(Icons.Default.SaveAlt, contentDescription = "History actions", tint = Theme.colors.primary)
            }
            DropdownMenu(expanded = historyMenu, onDismissRequest = { historyMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Save Budget to History") },
                    enabled = saveEnabled,
                    onClick = {
                        historyMenu = false
                        onSaveSnapshot()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Import from History") },
                    onClick = {
                        historyMenu = false
                        onImportHistory()
                    },
                )
            }
        }
    }
}

// MARK: - Month pill row

@Composable
private fun MonthPillRow(
    store: BudgetStore,
    bank: BankLedgerStore,
    month: String,
    hasDate: Boolean,
    readingBank: Boolean,
    undatedLineCount: Int,
    onShowDatePicker: () -> Unit,
    onShowBrowser: () -> Unit,
    onNewBudget: () -> Unit,
    onDeleteBudget: () -> Unit,
    onShowAccountPicker: () -> Unit,
    onPickPeriod: (BudgetPeriod) -> Unit,
    onShowPeriodPicker: () -> Unit,
) {
    var dateMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            PillLabel(
                icon = Icons.Default.CalendarMonth,
                text = if (readingBank) bank.viewState.period.label
                else if (hasDate) BudgetStore.monthDisplayName(month) else "Choose date",
                active = readingBank || hasDate,
                onClick = { dateMenu = true },
            )
            DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                if (readingBank) {
                    // Months lead; only months the bank sent something for.
                    bank.monthsWithActivity.forEach { key ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (bank.viewState.period == BudgetPeriod.Month(key)) "✓ " else "") +
                                        BudgetStore.monthDisplayName(key)
                                )
                            },
                            onClick = {
                                dateMenu = false
                                onPickPeriod(BudgetPeriod.Month(key))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (bank.viewState.period == BudgetPeriod.Everything) "✓ " else "") +
                                    "All transactions"
                            )
                        },
                        onClick = {
                            dateMenu = false
                            onPickPeriod(BudgetPeriod.Everything)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Custom dates…") },
                        onClick = {
                            dateMenu = false
                            onShowPeriodPicker()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(if (hasDate) "Edit Date" else "Add Date") },
                        onClick = {
                            dateMenu = false
                            onShowDatePicker()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("View Other Budgets") },
                        onClick = {
                            dateMenu = false
                            onShowBrowser()
                        },
                    )
                    if (bank.isConnected) {
                        DropdownMenuItem(
                            text = { Text("New budget") },
                            enabled = hasDate || undatedLineCount > 0,
                            onClick = {
                                dateMenu = false
                                onNewBudget()
                            },
                        )
                    }
                    if (hasDate) {
                        DropdownMenuItem(
                            text = { Text("Delete This Budget", color = Theme.colors.destructive) },
                            onClick = {
                                dateMenu = false
                                onDeleteBudget()
                            },
                        )
                    }
                }
            }
        }

        if (bank.isConnected) {
            Box(Modifier.weight(1f)) {
                PillLabel(
                    icon = Icons.Default.AccountBalance,
                    text = bank.sourceLabel,
                    active = readingBank,
                    filled = true,
                    onClick = onShowAccountPicker,
                )
            }
        } else {
            val enabled = hasDate || undatedLineCount > 0
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(Theme.colors.primary.copy(alpha = if (enabled) 1f else 0.5f))
                    .clickable(enabled = enabled, onClick = onNewBudget)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text("New budget", style = Theme.sans(13, FontWeight.Bold), color = Color.White, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PillLabel(
    icon: ImageVector,
    text: String,
    active: Boolean,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (filled) Theme.colors.primary else Theme.colors.card)
            .then(if (!filled) Modifier.border(1.dp, Theme.colors.border, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (filled) Color.White else if (active) Theme.colors.primary else Theme.colors.mutedForeground,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text,
            style = Theme.sans(13, FontWeight.Bold),
            color = if (filled) Color.White else if (active) Theme.colors.foreground else Theme.colors.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Default.KeyboardArrowDown, contentDescription = null,
            tint = if (filled) Color.White.copy(alpha = 0.8f) else Theme.colors.mutedForeground,
            modifier = Modifier.size(12.dp),
        )
    }
}

// MARK: - Direction tabs

@Composable
private fun DirectionTabs(tab: ItemType, income: Double, expenses: Double, onSelect: (ItemType) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Theme.colors.secondary)
            .padding(3.dp),
    ) {
        DirectionTab(
            selected = tab == ItemType.Income, label = "Income", up = true,
            tone = Theme.colors.positive, total = income,
            modifier = Modifier.weight(1f),
        ) { onSelect(ItemType.Income) }
        DirectionTab(
            selected = tab == ItemType.Expense, label = "Expenses", up = false,
            tone = Theme.colors.negative, total = expenses,
            modifier = Modifier.weight(1f),
        ) { onSelect(ItemType.Expense) }
    }
}

@Composable
private fun DirectionTab(
    selected: Boolean, label: String, up: Boolean, tone: Color, total: Double,
    modifier: Modifier, onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) Theme.colors.card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (up) "↑" else "↓", style = Theme.sans(11, FontWeight.Bold), color = tone)
        Text(
            label,
            style = Theme.sans(13, if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) Theme.colors.foreground else Theme.colors.mutedForeground,
        )
        Text(
            "$" + CalcFormat.int(total),
            style = Theme.figure(11, FontWeight.SemiBold),
            color = tone,
            maxLines = 1,
        )
    }
}

// MARK: - Category list

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryListCard(
    store: BudgetStore,
    groups: List<CategoryGroup>,
    direction: ItemType,
    denominator: Double,
    readingBank: Boolean,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onCollapseAll: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (BudgetItem) -> Unit,
    onDelete: (BudgetItem) -> Unit,
    onSetCap: (String) -> Unit,
    onRemoveCap: (String) -> Unit,
    onViewAll: (String) -> Unit,
) {
    val itemCount = groups.sumOf { it.items.size }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${groups.size} CATEGOR${if (groups.size == 1) "Y" else "IES"} · $itemCount ITEM${if (itemCount == 1) "" else "S"}",
                style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.1.sp),
                color = Theme.colors.mutedForeground,
            )
            Spacer(Modifier.weight(1f))
            if (expanded.isNotEmpty()) {
                Text(
                    "Collapse all",
                    style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold),
                    color = Theme.colors.primary,
                    modifier = Modifier.clickable(onClick = onCollapseAll),
                )
            }
        }
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, shape),
        ) {
            groups.forEachIndexed { index, group ->
                CategorySection(
                    store, group, direction, denominator, readingBank,
                    expanded, onToggle, onEdit, onDelete, onSetCap, onRemoveCap, onViewAll,
                )
                if (index < groups.size - 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                }
            }
            // Add footer row.
            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
            Text(
                "+ ${if (direction == ItemType.Income) "Add income" else "Add expense"}",
                style = Theme.sans(13, FontWeight.Bold),
                color = Theme.colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .fcPressable(onAdd)
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategorySection(
    store: BudgetStore,
    group: CategoryGroup,
    direction: ItemType,
    denominator: Double,
    readingBank: Boolean,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onEdit: (BudgetItem) -> Unit,
    onDelete: (BudgetItem) -> Unit,
    onSetCap: (String) -> Unit,
    onRemoveCap: (String) -> Unit,
    onViewAll: (String) -> Unit,
) {
    // Prefixed key so an income and an expense category sharing a name
    // ("Other") don't expand and collapse each other.
    val key = if (direction == ItemType.Income) "in:${group.category}" else group.category
    val isExpanded = key in expanded
    val cap = if (direction == ItemType.Expense) store.cap(group.category) else null
    val over = cap != null && group.total > cap
    // With a cap: bar tracks spend vs cap (red when over). Without one: the
    // category's share of the direction's total. One tone for every expense
    // category (green is income's on this screen).
    val fraction = when {
        cap != null && cap > 0 -> group.total / cap
        denominator > 0 -> group.total / denominator
        else -> 0.0
    }
    val barTone = when {
        direction == ItemType.Income -> Theme.colors.positive
        over -> Theme.colors.negative
        else -> Theme.colors.primary
    }
    var capMenu by remember { mutableStateOf(false) }

    Column {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (over) Theme.colors.negative.copy(alpha = 0.05f) else Color.Transparent)
                    .combinedClickable(
                        onClick = { onToggle(key) },
                        // Long-press a category header to set/edit its cap.
                        onLongClick = if (direction == ItemType.Expense) {
                            { capMenu = true }
                        } else null,
                    )
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (over) Theme.colors.negative else Theme.colors.mutedForeground,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "${group.category}  · ${group.items.size}",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (over) {
                    Text(
                        "over cap",
                        style = Theme.sans(10, FontWeight.Bold),
                        color = Theme.colors.negative,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Theme.colors.negative.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // Mini progress bar.
                Box(
                    Modifier
                        .width(52.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.secondary),
                ) {
                    Box(
                        Modifier
                            .width((52 * fraction.coerceIn(0.08, 1.0)).dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(barTone),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$" + CalcFormat.int(group.total),
                        style = Theme.figure(13, FontWeight.SemiBold),
                        color = if (over) Theme.colors.negative else Theme.colors.mutedForeground,
                        maxLines = 1,
                    )
                    if (cap != null) {
                        Text(
                            "of $" + CalcFormat.int(cap),
                            style = Theme.sans(10),
                            color = Theme.colors.mutedForeground,
                            maxLines = 1,
                        )
                    }
                }
            }
            DropdownMenu(expanded = capMenu, onDismissRequest = { capMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (cap == null) "Set Monthly Cap" else "Edit Monthly Cap") },
                    onClick = {
                        capMenu = false
                        onSetCap(group.category)
                    },
                )
                if (cap != null) {
                    DropdownMenuItem(
                        text = { Text("Remove Cap", color = Theme.colors.destructive) },
                        onClick = {
                            capMenu = false
                            onRemoveCap(group.category)
                        },
                    )
                }
            }
        }

        if (isExpanded) {
            // Top 3 by monthly amount + View all when longer.
            val sorted = group.items.sortedByDescending { it.monthlyAmount }
            sorted.take(3).forEach { item ->
                Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(1.dp).background(Theme.colors.border))
                BudgetItemRow(item, indent = true, readingBank = readingBank, onEdit = onEdit, onDelete = onDelete)
            }
            if (sorted.size > 3) {
                Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(1.dp).background(Theme.colors.border))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme.colors.secondary.copy(alpha = 0.4f))
                        .fcPressable { onViewAll(group.category) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("View all ${sorted.size}", style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold), color = Theme.colors.primary)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = Theme.colors.primary, modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * One budget item row: name over cadence detail, monthly figure, chevron.
 * Tap to edit (manual budget only — a bank's rows are charges that already
 * happened, so a tap opening a pre-filled editor invites correcting a bank's
 * own record by accident; long-press reaches Edit/Delete deliberately).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BudgetItemRow(
    item: BudgetItem,
    indent: Boolean,
    readingBank: Boolean,
    onEdit: (BudgetItem) -> Unit,
    onDelete: (BudgetItem) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (!readingBank) onEdit(item) else menu = true },
                    onLongClick = { menu = true },
                )
                .padding(start = if (indent) 36.dp else 15.dp, end = 15.dp)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.subcategory.ifEmpty { "No description" },
                    style = Theme.sans(13, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // On a bank the day it hit the account is the useful detail.
                val detail = if (readingBank) {
                    item.importDate?.let { raw ->
                        HistoryDate.parse(raw)?.let { HistoryDate.medium(it) } ?: raw
                    } ?: ""
                } else {
                    buildString {
                        append(CalcFormat.currency(item.amount, 2))
                        append(" ")
                        append(item.frequency.title.lowercase())
                        if (item.isFixed) append(" · fixed")
                        if (item.importDate != null) append(" · imported")
                    }
                }
                Text(detail, style = Theme.sans(11), color = Theme.colors.mutedForeground, maxLines = 1)
            }
            Text(
                (if (item.type == ItemType.Income) "+" else "−") + "$" + CalcFormat.int(item.monthlyAmount),
                style = Theme.figure(13, FontWeight.SemiBold),
                color = if (item.type == ItemType.Income) Theme.colors.positive else Theme.colors.negative,
                maxLines = 1,
            )
            if (!readingBank) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = Theme.colors.borderStrong, modifier = Modifier.size(14.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    menu = false
                    onEdit(item)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = Theme.colors.destructive) },
                onClick = {
                    menu = false
                    onDelete(item)
                },
            )
        }
    }
}

// MARK: - Empty states

@Composable
private fun EmptyInvite(direction: ItemType, readingBank: Boolean, onAdd: () -> Unit) {
    val title: String
    val subtitle: String
    val cta = if (direction == ItemType.Income) "Add income" else "Add expense"
    if (readingBank) {
        // An empty screen on a bank almost always means the dates or the
        // accounts are too narrow, so it says that first.
        title = if (direction == ItemType.Income) "No money came in over this stretch"
        else "Nothing was spent over this stretch"
        subtitle = "Try a wider set of dates or more accounts. You can also add anything your bank never saw, like cash."
    } else if (direction == ItemType.Income) {
        title = "Add your income"
        subtitle = "Start with a salary or any money coming in each month."
    } else {
        title = "Add your first expense"
        subtitle = "Rent, groceries, subscriptions: see where the month goes."
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
        Text(
            subtitle,
            style = Theme.sans(13),
            color = Theme.colors.mutedForeground,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "+ $cta",
            style = Theme.sans(13, FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onAdd)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

// MARK: - Summary donut

/**
 * Pure composition either way: the ring is the direction's own total split by
 * category, so a slice's share is its share of all expenses (or all income)
 * and the legend sums to 100%.
 */
@Composable
private fun SummaryDonutCard(
    direction: ItemType,
    groups: List<CategoryGroup>,
    denominator: Double,
    expandedSlices: Set<String>,
    onToggleSlice: (String) -> Unit,
    onViewAll: (String) -> Unit,
) {
    val slices = groups.mapIndexed { index, group ->
        Triple(group, BudgetCategoryStyle.chartColor(index), group.total)
    }
    val title = if (direction == ItemType.Expense) "WHERE IT GOES" else "WHERE IT COMES FROM"
    val caption = if (direction == ItemType.Expense) "EXPENSES" else "INCOME"
    val track = Theme.colors.secondary

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            title,
            style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.sp),
            color = Theme.colors.mutedForeground,
        )

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val lineWidth = 26.dp.toPx()
                    val radius = (minOf(size.width, size.height) - lineWidth) / 2
                    val topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius)
                    val arcSize = Size(radius * 2, radius * 2)
                    drawArc(
                        color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(lineWidth),
                    )
                    if (denominator > 0) {
                        // Tighter separators once the ring is busy.
                        val gap = if (slices.size > 8) 1.5f else if (slices.size > 1) 3f else 0f
                        var start = -90f
                        for ((_, color, value) in slices) {
                            val sweep = (360.0 * (value / denominator)).toFloat()
                            if (sweep > gap) {
                                drawArc(
                                    color = color,
                                    startAngle = start + gap / 2,
                                    sweepAngle = sweep - gap,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(lineWidth, cap = StrokeCap.Butt),
                                )
                            }
                            start += sweep
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$" + CalcFormat.int(denominator),
                        style = Theme.figure(26, FontWeight.Bold),
                        color = Theme.colors.foreground,
                        maxLines = 1,
                    )
                    Text(
                        caption,
                        style = Theme.sans(10, FontWeight.SemiBold).copy(letterSpacing = 1.sp),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }

        Column {
            slices.forEachIndexed { index, (group, color, value) ->
                LegendRow(direction, group, value, denominator, color, expandedSlices, onToggleSlice, onViewAll)
                if (index < slices.size - 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                }
            }
        }
    }
}

/** Share of the direction's total, to one decimal only when it needs one. */
private fun percentLabel(value: Double, denominator: Double): String {
    if (denominator <= 0) return "—"
    val pct = value / denominator * 100
    if (pct > 0 && pct < 0.1) return "<0.1%"
    val rounded = kotlin.math.round(pct * 10) / 10
    return if (rounded == kotlin.math.round(rounded)) "${rounded.toInt()}%" else "%.1f%%".format(rounded)
}

@Composable
private fun LegendRow(
    direction: ItemType,
    group: CategoryGroup,
    value: Double,
    denominator: Double,
    swatch: Color,
    expandedSlices: Set<String>,
    onToggleSlice: (String) -> Unit,
    onViewAll: (String) -> Unit,
) {
    val key = "${direction.raw}:${group.category}"
    val expanded = key in expandedSlices
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleSlice(key) }
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.5.dp)).background(swatch))
            Text(
                group.category,
                style = Theme.sans(13, FontWeight.SemiBold),
                color = Theme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Theme.colors.mutedForeground,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(percentLabel(value, denominator), style = Theme.figure(12), color = Theme.colors.mutedForeground)
            Text(
                "$" + CalcFormat.int(value),
                style = Theme.figure(13, FontWeight.SemiBold),
                color = Theme.colors.foreground,
                maxLines = 1,
            )
        }
        if (expanded) {
            // The same three-then-View-all the category sections use — each
            // line's share is of the SAME total, so the numbers stay honest.
            val sorted = group.items.sortedByDescending { it.monthlyAmount }
            sorted.take(3).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 19.dp)
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.subcategory.ifEmpty { item.category },
                        style = Theme.sans(12),
                        color = Theme.colors.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(percentLabel(item.monthlyAmount, denominator), style = Theme.figure(11), color = Theme.colors.mutedForeground)
                    Text(
                        "$" + CalcFormat.int(item.monthlyAmount),
                        style = Theme.figure(12, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                    )
                }
            }
            if (sorted.size > 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fcPressable { onViewAll(group.category) }
                        .padding(start = 19.dp, top = 4.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("View all ${sorted.size}", style = Theme.sans(12, FontWeight.Bold), color = Theme.colors.primary)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                        tint = Theme.colors.primary, modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}
