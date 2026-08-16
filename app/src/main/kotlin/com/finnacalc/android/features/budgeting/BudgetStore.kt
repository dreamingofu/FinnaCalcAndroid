//
// BudgetStore.kt
//
// Port of iOS Features/Budgeting/BudgetStore.swift — the store for the Budget
// Planner: persistence (SharedPreferences, the analogue of the web's
// localStorage), the budget math, CRUD, and the Plaid transaction import.
//
// iOS @Published becomes a `version` StateFlow tick (same pattern as
// BankLedgerStore): every mutation persists and bumps it, and screens re-read
// the derived values. The ledger's own version is folded in by the UI layer
// collecting both.
//

package com.finnacalc.android.features.budgeting

import com.finnacalc.android.core.plaid.BankTransaction
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.widget.GoalSnapshot
import com.finnacalc.android.widget.GoalsSnapshotStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BudgetStore {

    private val _version = MutableStateFlow(0)
    /** Bumps on every mutation — collect to re-read derived state. */
    val version: StateFlow<Int> = _version.asStateFlow()

    var items: List<BudgetItem> = JsonPrefs.load(Keys.ITEMS) ?: emptyList()
        set(value) {
            field = value
            JsonPrefs.persist(value, Keys.ITEMS)
            bump()
        }

    var goals: List<SavingsGoal> = JsonPrefs.load(Keys.GOALS) ?: emptyList()
        set(value) {
            field = value
            JsonPrefs.persist(value, Keys.GOALS)
            bump()
            publishWidgetSnapshot()
        }

    var history: List<BudgetHistoryEntry> = JsonPrefs.load(Keys.HISTORY) ?: emptyList()
        set(value) {
            field = value
            JsonPrefs.persist(value, Keys.HISTORY)
            bump()
        }

    var budgetType: BudgetType = BudgetType.Personal
        set(value) {
            field = value
            // The ledger keeps a view state per budget, so it has to know
            // which one is open.
            BankLedgerStore.shared.budgetType = value
            bump()
        }

    /**
     * Per-category monthly spending caps, keyed budgetType.raw → category →
     * cap. Powers the editor's cap-progress bars and "over cap" states.
     */
    var categoryCaps: Map<String, Map<String, Double>> = JsonPrefs.load(Keys.CAPS) ?: emptyMap()
        set(value) {
            field = value
            JsonPrefs.persist(value, Keys.CAPS)
            bump()
        }

    /**
     * Last slot the editor was left on, per budget type — so closing My
     * Budget and coming back reopens the budget you were working on.
     */
    var lastOpenSlot: Map<String, String> = JsonPrefs.load(Keys.LAST_SLOT) ?: emptyMap()
        set(value) {
            field = value
            JsonPrefs.persist(value, Keys.LAST_SLOT)
            bump()
        }

    private object Keys {
        const val ITEMS = "finnacalc-budget-items"
        const val GOALS = "finnacalc-savings-goals"
        const val HISTORY = "finnacalc-budget-history"
        const val CAPS = "finnacalc-category-caps"
        const val LAST_SLOT = "finnacalc-budget-last-slot"
    }

    init {
        // Migrate items with no slot into the undated one. A date is only
        // ever set by the user picking one — auto-stamping these with today's
        // month would invent a budget for a month they never chose.
        if (items.any { it.month.isEmpty() }) {
            items = items.map { if (it.month.isEmpty()) it.copy(month = UNDATED_MONTH_KEY) else it }
        }
        BankLedgerStore.shared.budgetType = budgetType
    }

    private fun bump() {
        _version.value += 1
    }

    // MARK: Category caps (scoped to the active budgetType)

    fun cap(category: String): Double? = categoryCaps[budgetType.raw]?.get(category)

    /** Sets (or, with null / non-positive, removes) a category's monthly cap. */
    fun setCap(amount: Double?, category: String) {
        val forType = (categoryCaps[budgetType.raw] ?: emptyMap()).toMutableMap()
        if (amount != null && amount > 0) forType[category] = amount else forType.remove(category)
        categoryCaps = categoryCaps + (budgetType.raw to forType)
    }

    // MARK: Derived budget math (scoped to the active budgetType)

    /**
     * What every screen outside the editor shows (Home cards, the Budgeting
     * dashboard, pace, analysis): the SAME budget the editor reopens on —
     * whatever My Budget has open, wherever it came from.
     */
    val currentItems: List<BudgetItem>
        get() {
            val bank = BankLedgerStore.shared
            if (bank.isReadingBank) return bank.currentItems(budgetType)
            return itemsInMonth(rememberedSlot)
        }

    /**
     * Whether "days left in the month" describes the reported budget. True
     * for this calendar month's budget and for the undated one.
     */
    val currentSlotIsThisMonth: Boolean
        get() {
            val bank = BankLedgerStore.shared
            if (bank.isReadingBank) {
                val period = bank.viewState.period
                return period is BudgetPeriod.Month && period.key == currentMonthKey
            }
            return rememberedSlot == currentMonthKey || !isDated(rememberedSlot)
        }

    /** Short label for the budget being reported. */
    val currentSlotLabel: String
        get() {
            val bank = BankLedgerStore.shared
            if (bank.isReadingBank) return bank.viewState.period.label.lowercase()
            return if (isDated(rememberedSlot)) monthDisplayName(rememberedSlot) else "no date"
        }

    /** One slot's items for the active budget type. */
    fun itemsInMonth(month: String): List<BudgetItem> =
        items.filter { it.budgetType == budgetType && it.month == month }

    /** Months the active budget has actually been saved to, newest key first. */
    val savedMonths: List<String>
        get() = items
            .filter { it.budgetType == budgetType && isDated(it.month) }
            .map { it.month }
            .distinct()
            .sortedDescending()

    /**
     * The slot the editor should reopen on: whichever it was last left on,
     * dated or not; a month whose budget has since been deleted falls back
     * to undated.
     */
    val rememberedSlot: String
        get() {
            val slot = lastOpenSlot[budgetType.raw] ?: UNDATED_MONTH_KEY
            if (!isDated(slot)) return UNDATED_MONTH_KEY
            return if (itemsInMonth(slot).isEmpty()) UNDATED_MONTH_KEY else slot
        }

    fun rememberSlot(slot: String) {
        if (lastOpenSlot[budgetType.raw] == slot) return
        lastOpenSlot = lastOpenSlot + (budgetType.raw to slot)
    }

    /** Net (income − expenses) per saved month for the active budget. */
    val savedMonthNets: Map<String, Double>
        get() {
            val nets = mutableMapOf<String, Double>()
            for (item in items) {
                if (item.budgetType != budgetType || !isDated(item.month)) continue
                val delta = if (item.type == ItemType.Income) item.monthlyAmount else -item.monthlyAmount
                nets[item.month] = (nets[item.month] ?: 0.0) + delta
            }
            return nets
        }

    /** Goals for the active budget (Personal/Business) only. */
    val currentGoals: List<SavingsGoal> get() = goals.filter { it.budgetType == budgetType }

    /** Snapshots saved from the active budget only, newest first. */
    val currentHistory: List<BudgetHistoryEntry> get() = history.filter { it.budgetType == budgetType }

    val monthlyIncome: Double
        get() = currentItems.filter { it.type == ItemType.Income }.sumOf { it.monthlyAmount }

    val monthlyExpenses: Double
        get() = currentItems.filter { it.type == ItemType.Expense }.sumOf { it.monthlyAmount }

    val monthlyNet: Double get() = monthlyIncome - monthlyExpenses

    /**
     * Only "Savings"/"Retirement" expense categories count toward savings
     * rate; null when there's no income or no such contributions.
     */
    val savingsRate: Double?
        get() {
            val saved = currentItems
                .filter { it.type == ItemType.Expense && (it.category == "Savings" || it.category == "Retirement") }
                .sumOf { it.monthlyAmount }
            if (monthlyIncome <= 0 || saved <= 0) return null
            return saved / monthlyIncome * 100
        }

    /**
     * Net profit margin, for the Business budget: what share of revenue is
     * left after costs. null when there's no revenue to take a share of.
     */
    val netProfitMargin: Double?
        get() {
            if (monthlyIncome <= 0) return null
            return monthlyNet / monthlyIncome * 100
        }

    val expenseByCategory: List<CategorySlice> get() = grouped(ItemType.Expense)
    val incomeByCategory: List<CategorySlice> get() = grouped(ItemType.Income)

    private fun grouped(type: ItemType): List<CategorySlice> {
        val totals = mutableMapOf<String, Double>()
        for (item in currentItems) {
            if (item.type != type) continue
            totals[item.category] = (totals[item.category] ?: 0.0) + item.monthlyAmount
        }
        return totals.map { CategorySlice(it.key, it.value) }.sortedByDescending { it.value }
    }

    // MARK: CRUD

    /** An item with no slot joins the undated budget. */
    fun addItem(item: BudgetItem) {
        addItems(listOf(item))
    }

    /** Appends a whole import in one mutation (one persist, not one per row). */
    fun addItems(newItems: List<BudgetItem>) {
        if (newItems.isEmpty()) return
        items = items + newItems.map {
            if (it.month.isEmpty()) it.copy(month = UNDATED_MONTH_KEY) else it
        }
    }

    fun updateItem(item: BudgetItem) {
        items = items.map { if (it.id == item.id) item else it }
    }

    fun deleteItem(item: BudgetItem) {
        items = items.filter { it.id != item.id }
    }

    fun newItemID(): String = UUID.randomUUID().toString()

    /**
     * Restamps every line of the active budget from one slot to another. If
     * the destination already holds a budget the two end up combined, so
     * callers confirm that with the user first.
     */
    fun moveItems(source: String, destination: String) {
        if (source == destination) return
        if (items.none { it.budgetType == budgetType && it.month == source }) return
        items = items.map {
            if (it.budgetType == budgetType && it.month == source) it.copy(month = destination) else it
        }
    }

    /** Stamps the active budgetType so a goal always belongs to its tab. */
    fun addGoal(goal: SavingsGoal) {
        goals = goals + goal.copy(budgetType = budgetType)
    }

    /** Replaces a goal's editable fields, preserving identity and budget. */
    fun updateGoal(goal: SavingsGoal) {
        goals = goals.map { if (it.id == goal.id) goal.copy(budgetType = it.budgetType) else it }
    }

    fun deleteGoal(goal: SavingsGoal) {
        goals = goals.filter { it.id != goal.id }
    }

    fun addFunds(goal: SavingsGoal, amount: Double) {
        goals = goals.map {
            if (it.id == goal.id) it.copy(currentAmount = it.currentAmount + amount) else it
        }
    }

    // MARK: History snapshots

    /**
     * Freezes one month's plan into History. `month` defaults to the current
     * calendar month; `lines` overrides which items are captured (a snapshot
     * taken while reading a bank holds that account's transactions).
     */
    fun saveSnapshot(
        name: String, startDate: String, endDate: String,
        month: String? = null, lines: List<BudgetItem>? = null,
    ) {
        val captured = lines ?: itemsInMonth(month ?: currentMonthKey)
        val income = captured.filter { it.type == ItemType.Income }.sumOf { it.monthlyAmount }
        val expenses = captured.filter { it.type == ItemType.Expense }.sumOf { it.monthlyAmount }
        history = listOf(
            BudgetHistoryEntry(
                id = UUID.randomUUID().toString(),
                name = name,
                startDate = startDate,
                endDate = endDate,
                budgetItems = captured,
                monthlyIncome = income,
                monthlyExpenses = expenses,
                monthlyNet = income - expenses,
                budgetType = budgetType,
            )
        ) + history
    }

    fun deleteSnapshot(entry: BudgetHistoryEntry) {
        history = history.filter { it.id != entry.id }
    }

    // MARK: Clear

    /**
     * Wipes every income and expense line of ONE budget (Personal or
     * Business), across every month. Goals, history and category caps are
     * left alone — they aren't budget lines.
     */
    fun clearBudgetItems(type: BudgetType) {
        items = items.filter { it.budgetType != type }
    }

    /** Empties ONE slot of the active budget. */
    fun clearMonth(month: String) {
        items = items.filter { !(it.budgetType == budgetType && it.month == month) }
    }

    // MARK: Snapshot import

    /**
     * A saved snapshot's lines, re-keyed for the live budget: fresh ids, the
     * undated slot, the active budget type. Invalid or catch-all categories
     * get a second chance off the merchant name.
     */
    fun itemsFromSnapshot(entry: BudgetHistoryEntry): List<BudgetItem> =
        entry.budgetItems.map { saved ->
            var item = saved.copy(
                id = newItemID(),
                month = UNDATED_MONTH_KEY,
                budgetType = budgetType,
            )
            val valid = TransactionCategorizer.isValid(item.category, item.type, budgetType)
            val isCatchAll = item.category == TransactionCategorizer.fallback(item.type, budgetType)
            if ((!valid || isCatchAll) && item.subcategory.isNotEmpty()) {
                item = item.copy(
                    category = TransactionCategorizer.category(item.subcategory, item.type, budgetType)
                )
            } else if (!valid) {
                item = item.copy(category = TransactionCategorizer.fallback(item.type, budgetType))
            }
            item
        }

    /**
     * Lands imported lines in the undated slot, on top of what's there or in
     * place of it. Shared by the statement import, the History import, and
     * editing a snapshot.
     */
    fun landImport(newItems: List<BudgetItem>, combine: Boolean) {
        if (!combine) clearMonth(UNDATED_MONTH_KEY)
        addItems(newItems)
    }

    /**
     * Everything belonging to ONE budget: its lines, goals, snapshots and
     * category caps. Clearing from one must not reach the other.
     */
    fun clearAll(type: BudgetType) {
        items = items.filter { it.budgetType != type }
        goals = goals.filter { it.budgetType != type }
        history = history.filter { it.budgetType != type }
        categoryCaps = categoryCaps - type.raw
    }

    // MARK: Plaid import

    fun mapPlaidCategory(primary: String, type: ItemType): String =
        plaidCategory(primary, type, budgetType)

    /**
     * Import Plaid transactions as a HISTORY SNAPSHOT only — like the web's
     * handlePlaidImport, which never mutates the live budget.
     */
    fun importPlaidTransactions(
        transactions: List<BankTransaction>,
        snapshotName: String = "Bank Import (Plaid)",
    ) {
        if (transactions.isEmpty()) return
        val imported = transactions.map { txn ->
            val type = if (txn.amount > 0) ItemType.Expense else ItemType.Income
            BudgetItem(
                id = UUID.randomUUID().toString(),
                category = mapPlaidCategory(txn.category, type),
                subcategory = txn.name,
                amount = kotlin.math.abs(txn.amount),
                frequency = Frequency.Monthly,
                type = type,
                isFixed = false,
                budgetType = budgetType,
                importDate = txn.date,
                month = currentMonthKey,
            )
        }
        val totalIncome = imported.filter { it.type == ItemType.Income }.sumOf { it.amount }
        val totalExpenses = imported.filter { it.type == ItemType.Expense }.sumOf { it.amount }
        val dates = transactions.map { it.date }.sorted()
        history = listOf(
            BudgetHistoryEntry(
                id = UUID.randomUUID().toString(),
                name = snapshotName,
                startDate = dates.firstOrNull() ?: "",
                endDate = dates.lastOrNull() ?: "",
                budgetItems = imported,
                monthlyIncome = totalIncome,
                monthlyExpenses = totalExpenses,
                monthlyNet = totalIncome - totalExpenses,
                budgetType = budgetType,
            )
        ) + history
    }

    companion object {
        // MARK: Month keys ("yyyy-MM")

        private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
        private val monthDisplayFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

        val currentMonthKey: String get() = YearMonth.now().format(monthKeyFormatter)

        /**
         * The slot a budget lives in before it's been given a date — the
         * editor's default working budget.
         */
        const val UNDATED_MONTH_KEY = "undated"

        /** True for a real "yyyy-MM" slot. */
        fun isDated(key: String): Boolean = key != UNDATED_MONTH_KEY && key.isNotEmpty()

        /** "2026-07" → "July 2026" (echoes the key if it doesn't parse). */
        fun monthDisplayName(key: String): String = try {
            YearMonth.parse(key, monthKeyFormatter).format(monthDisplayFormatter)
        } catch (_: Exception) {
            key
        }

        /** The month key `offset` months away from `key`. */
        fun monthKey(key: String, offsetBy: Int): String = try {
            YearMonth.parse(key, monthKeyFormatter).plusMonths(offsetBy.toLong()).format(monthKeyFormatter)
        } catch (_: Exception) {
            key
        }

        /**
         * Maps a Plaid personal_finance_category primary to a budget
         * category (ported 1:1 from mapPlaidCategory in app/budgeting/page.tsx).
         */
        fun plaidCategory(primary: String, type: ItemType, budgetType: BudgetType): String {
            if (type == ItemType.Income) {
                if (budgetType == BudgetType.Business) return "Other Revenue"
                val isIncome = Regex("INCOME|PAYROLL|DEPOSIT").containsMatchIn(primary)
                return if (isIncome) "Salary" else "Other"
            }
            if (budgetType == BudgetType.Business) return "Other Operating Costs"
            return when (primary) {
                "FOOD_AND_DRINK" -> "Food"
                "RENT_AND_UTILITIES" -> "Housing"
                "TRANSPORTATION" -> "Transportation"
                "TRAVEL" -> "Transportation"
                "ENTERTAINMENT" -> "Entertainment"
                "MEDICAL" -> "Healthcare"
                "LOAN_PAYMENTS" -> "Debt Payments"
                "INSURANCE" -> "Insurance"
                else -> "Other"
            }
        }

        /** ISO "yyyy-MM-dd" for today, for typed ledger lines. */
        val todayIso: String get() = LocalDate.now().toString()
    }

    /**
     * The Goals widget runs in the launcher's process, so it can't read these
     * stores — it reads a published snapshot instead. Republished on every
     * goal change so the widget can't show a figure the app has moved past.
     */
    private fun publishWidgetSnapshot() {
        GoalsSnapshotStore.publish(
            currentGoals.filter { it.targetAmount > 0 }.map { goal ->
                val measured = GoalProgress.measure(goal, BankLedgerStore.shared)
                GoalSnapshot(
                    id = goal.id,
                    name = goal.name,
                    current = measured.first,
                    target = measured.second,
                )
            }
        )
    }

}
