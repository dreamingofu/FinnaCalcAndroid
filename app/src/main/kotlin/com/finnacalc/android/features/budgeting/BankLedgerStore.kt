//
// BankLedgerStore.kt
//
// Port of the store half of iOS Features/Budgeting/BankLedger.swift. What a
// linked bank actually leaves behind: connections, accounts, and every
// transaction they returned, kept so My Budget can be drawn for any stretch of
// time and any account without asking Plaid again. A period or account change
// is a filter over what is already on the phone.
//
// Two things live here that no bank sent: lines the user typed (cash, a friend
// paying you back) and corrected categories (our Plaid mapping is coarse).
//
// iOS @Published becomes a single `version` StateFlow tick + plain state:
// every mutation bumps it, and Compose reads through snapshot-friendly
// accessors. (Deviation: iOS Combine fine-grained publishers aren't idiomatic
// here; the store is small enough that whole-store invalidation is fine.)
//

package com.finnacalc.android.features.budgeting

import com.finnacalc.android.core.plaid.PlaidImportResult
import com.finnacalc.android.core.util.JsonPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BankLedgerStore private constructor() {

    private val _version = MutableStateFlow(0)
    /** Bumps on every mutation — collect to re-read derived state. */
    val version: StateFlow<Int> = _version.asStateFlow()

    var connections: List<BankConnection> = JsonPrefs.load(Keys.CONNECTIONS) ?: emptyList()
        private set
    var entries: List<LedgerEntry> = JsonPrefs.load(Keys.ENTRIES) ?: emptyList()
        private set

    /** One view state per budget, keyed by BudgetType.raw. Personal and
     * Business are separate budgets that happen to share a set of linked
     * banks. */
    private var viewStates: MutableMap<String, BankViewState> =
        JsonPrefs.load<Map<String, BankViewState>>(Keys.VIEW_STATE)?.toMutableMap() ?: mutableMapOf()

    /** Which budget the view state applies to; BudgetStore keeps it in step. */
    var budgetType: BudgetType = BudgetType.Personal
        set(value) {
            field = value
            bump()
        }

    /** Categories the user corrected, keyed by entry id. */
    var categoryOverrides: Map<String, String> = JsonPrefs.load(Keys.OVERRIDES) ?: emptyMap()
        private set

    var viewState: BankViewState
        get() = viewStates[budgetType.raw] ?: BankViewState()
        set(value) {
            // Adoption is one-way: reading the bank once makes it part of
            // this budget until the connection itself goes.
            val stamped = if (value.usingBank) value.copy(everUsedBank = true) else value
            viewStates[budgetType.raw] = stamped
            JsonPrefs.persist(viewStates.toMap(), Keys.VIEW_STATE)
            bump()
        }

    /** Whether a budget has ever read the bank. */
    fun hasAdopted(type: BudgetType): Boolean =
        isConnected && viewStates[type.raw]?.everUsedBank == true

    private object Keys {
        const val CONNECTIONS = "finnacalc-bank-connections"
        const val ENTRIES = "finnacalc-bank-entries"
        const val VIEW_STATE = "finnacalc-bank-view"
        const val OVERRIDES = "finnacalc-bank-categories"
    }

    private fun bump() {
        _version.value += 1
    }

    // MARK: Derived

    /** A real Plaid connection, as opposed to the manual pseudo-connection. */
    val hasBank: Boolean get() = connections.any { it.id != MANUAL_CONNECTION_ID }

    val isConnected: Boolean get() = hasBank

    /** Reading a bank right now. Guards on the connection too. */
    val isReadingBank: Boolean get() = viewState.usingBank && isConnected

    /** The label the account button carries. */
    val sourceLabel: String
        get() {
            if (!isReadingBank) return "Manual"
            val picked = selectedAccountIDs
            if (picked.size == 1) {
                val only = picked.first()
                return selectableAccounts.firstOrNull { it.id == only }?.displayName ?: "1 account"
            }
            if (picked.size == selectableAccounts.size) return "All accounts"
            return "${picked.size} accounts"
        }

    /** Every account across every connection, grouped by institution. */
    val accounts: List<BankAccount>
        get() = connections
            .filter { it.id != MANUAL_CONNECTION_ID }
            .sortedBy { it.institution.lowercase() }
            .flatMap { it.accounts.sortedBy { a -> a.name.lowercase() } }

    val selectableAccounts: List<BankAccount> get() = accounts

    val hasManualEntries: Boolean get() = entries.any { it.typedByUser }

    /** Lines the user typed against one account, newest first. */
    fun typedEntries(accountID: String): List<LedgerEntry> =
        entries.filter { it.typedByUser && it.accountID == accountID }.sortedByDescending { it.date }

    fun isEntryIncluded(id: String): Boolean = !viewState.excludedEntryIDs.contains(id)

    /** Ticks one typed line on or off under its account. */
    fun toggleEntry(id: String) {
        val excluded = viewState.excludedEntryIDs.toMutableSet()
        if (!excluded.add(id)) excluded.remove(id)
        viewState = viewState.copy(excludedEntryIDs = excluded)
    }

    /** Every typed line under an account, in or out at once. */
    fun setTypedEntries(accountID: String, included: Boolean) {
        val ids = typedEntries(accountID).map { it.id }
        val excluded = viewState.excludedEntryIDs.toMutableSet()
        if (included) excluded.removeAll(ids.toSet()) else excluded.addAll(ids)
        viewState = viewState.copy(excludedEntryIDs = excluded)
    }

    /**
     * The ticked accounts, resolved. An empty set means every account, and
     * ids for accounts that have since gone are dropped.
     */
    val selectedAccountIDs: Set<String>
        get() {
            val all = selectableAccounts.map { it.id }.toSet()
            val picked = viewState.accountIDs.intersect(all)
            return picked.ifEmpty { all }
        }

    fun account(id: String): BankAccount? = selectableAccounts.firstOrNull { it.id == id }

    /** The connection an account belongs to, for the picker's unlink action. */
    fun connectionHolding(accountID: String): BankConnection? =
        connections.firstOrNull { c -> c.accounts.any { it.id == accountID } }

    /** Banks that can be unlinked (not the manual pseudo-connection). */
    val removableConnections: List<BankConnection>
        get() = connections
            .filter { it.id != MANUAL_CONNECTION_ID }
            .sortedBy { it.institution.lowercase() }

    /**
     * Ticks or unticks one account. Picking any account is itself the move to
     * the bank; unticking the last one is the move back to the manual budget.
     */
    fun toggleAccount(id: String) {
        val picked = (if (viewState.usingBank) selectedAccountIDs else emptySet()).toMutableSet()
        if (!picked.add(id)) picked.remove(id)
        viewState = if (picked.isEmpty()) {
            viewState.copy(usingBank = false, accountIDs = emptySet())
        } else {
            // A full house is stored as "none picked", so adding the last
            // account and pressing All accounts land in the same state.
            viewState.copy(
                usingBank = true,
                accountIDs = if (picked.size == selectableAccounts.size) emptySet() else picked,
            )
        }
    }

    fun showManualBudget() {
        viewState = viewState.copy(usingBank = false)
    }

    fun selectAllAccounts() {
        viewState = viewState.copy(usingBank = true, accountIDs = emptySet())
    }

    val isShowingAllAccounts: Boolean
        get() = isReadingBank && selectedAccountIDs.size == selectableAccounts.size

    fun isSelected(id: String): Boolean = isReadingBank && selectedAccountIDs.contains(id)

    /** Months with at least one transaction (in the ticked accounts), newest first. */
    val monthsWithActivity: List<String>
        get() {
            val picked = selectedAccountIDs
            val seen = mutableSetOf<String>()
            for (entry in entries) {
                if (entry.date.length < 7) continue
                val id = entry.accountID
                if (id != null && !picked.contains(id)) continue
                seen.add(entry.date.take(7))
            }
            return seen.sortedDescending()
        }

    /** First and last ISO dates on record, for the custom range picker's bounds. */
    val dateBounds: Pair<String, String>?
        get() {
            val dates = entries.map { it.date }.sorted()
            val first = dates.firstOrNull() ?: return null
            return first to dates.last()
        }

    // MARK: Reading

    /** The entries a period and a set of ticked accounts select, newest first. */
    fun entries(period: BudgetPeriod, accountIDs: Set<String>): List<LedgerEntry> {
        val excluded = viewState.excludedEntryIDs
        return entries
            .filter { entry ->
                if (!period.contains(entry.date)) return@filter false
                if (entry.typedByUser && excluded.contains(entry.id)) return@filter false
                val id = entry.accountID ?: return@filter true
                accountIDs.contains(id)
            }
            .sortedByDescending { it.date }
    }

    /**
     * The same entries as budget lines, so every list, donut and total in the
     * editor works on bank data unchanged. Frequency is Monthly so
     * monthlyAmount == amount: these already happened once, not a rate.
     */
    fun items(period: BudgetPeriod, accountIDs: Set<String>, budgetType: BudgetType): List<BudgetItem> =
        entries(period, accountIDs).map { entry ->
            val type = if (entry.amount > 0) ItemType.Expense else ItemType.Income
            BudgetItem(
                id = entry.id,
                category = categoryOverrides[entry.id]
                    ?: BudgetStore.plaidCategory(entry.category, type, budgetType),
                subcategory = entry.name,
                amount = kotlin.math.abs(entry.amount),
                frequency = entry.chargeSchedule?.frequency ?: Frequency.Monthly,
                type = type,
                isFixed = false,
                budgetType = budgetType,
                importDate = entry.date,
                month = BudgetStore.UNDATED_MONTH_KEY,
                chargeSchedule = entry.chargeSchedule,
            )
        }

    /** The lines currently on screen. */
    fun currentItems(budgetType: BudgetType): List<BudgetItem> =
        items(viewState.period, selectedAccountIDs, budgetType)

    // MARK: Writing

    /**
     * Files a finished Plaid import. Re-linking the same institution replaces
     * its transactions rather than stacking a second copy of the same 90 days.
     */
    fun record(result: PlaidImportResult) {
        val institution = result.institution ?: "Bank"
        val connectionID = connectionIdFor(institution)

        var accounts = result.accounts.map {
            BankAccount(id = it.id, name = it.name, mask = it.mask, kind = it.kind, institution = institution)
        }
        if (accounts.isEmpty()) {
            accounts = listOf(BankAccount(id = connectionID, name = institution, institution = institution))
        }

        val fallbackAccount = if (accounts.size == 1) accounts[0].id else null
        val known = accounts.map { it.id }.toSet()
        val fresh = result.transactions.mapIndexed { index, txn ->
            LedgerEntry(
                // The index keeps two identical charges on the same day distinct.
                id = "$connectionID|$index|${txn.date}|${txn.name}|${txn.amount}",
                connectionID = connectionID,
                // An account_id we have no account for falls back to the
                // connection's own account.
                accountID = txn.accountId?.takeIf { known.contains(it) } ?: fallbackAccount,
                date = txn.date,
                name = txn.name,
                amount = txn.amount,
                category = txn.category,
            )
        }

        // A re-link replaces this connection's transactions; the user's own
        // subscription tagging survives, matched by name.
        val tagged = entries
            .filter { it.connectionID == connectionID && it.chargeSchedule != null }
            .associate { it.name to it.chargeSchedule!! }
        val restored = fresh.map { entry ->
            tagged[entry.name]?.let { entry.copy(chargeSchedule = it) } ?: entry
        }

        connections = connections.filter { it.id != connectionID } +
            BankConnection(connectionID, institution, System.currentTimeMillis(), accounts)
        // Only the bank's own rows are replaced; typed lines survive a refresh.
        entries = entries.filter { !(it.connectionID == connectionID && !it.typedByUser) } + restored
        // Land the budget that did the linking on it, across every account.
        viewState = BankViewState(usingBank = true, accountIDs = emptySet(), period = BudgetPeriod.Everything, everUsedBank = true)
        save()
    }

    /**
     * A line typed while reading a bank: a cash payment, anything the bank
     * never saw. `date` chosen by the caller so the line lands inside the
     * period it was added from.
     */
    fun addManual(
        name: String, amount: Double, type: ItemType, category: String,
        date: String, accountID: String? = null, chargeSchedule: ChargeSchedule? = null,
    ) {
        val target = accountID ?: defaultTypedAccountID ?: return
        val connection = connectionHolding(target) ?: return
        val entry = LedgerEntry(
            // Timestamped rather than content-hashed: two $20 cash lunches on
            // the same day are two lines, not one.
            id = "manual|${java.util.UUID.randomUUID()}",
            connectionID = connection.id,
            accountID = target,
            date = date,
            name = name,
            // Stored in Plaid's convention: positive is money out.
            amount = if (type == ItemType.Expense) kotlin.math.abs(amount) else -kotlin.math.abs(amount),
            category = category,
            chargeSchedule = chargeSchedule,
            typedByUser = true,
        )
        entries = entries + entry
        categoryOverrides = categoryOverrides + (entry.id to category)
        save()
    }

    /**
     * Edits any line, the bank's own included — our category mapping is
     * coarse enough that some rows arrive plainly wrong. Edits to a bank's
     * own rows do not survive a re-link; hand-typed lines are never touched
     * by one.
     */
    fun updateEntry(
        id: String, name: String, amount: Double, type: ItemType,
        category: String, chargeSchedule: ChargeSchedule? = null,
    ) {
        val index = entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = entries[index]
        entries = entries.toMutableList().also {
            it[index] = old.copy(
                name = name,
                amount = if (type == ItemType.Expense) kotlin.math.abs(amount) else -kotlin.math.abs(amount),
                category = category,
                chargeSchedule = chargeSchedule,
            )
        }
        categoryOverrides = categoryOverrides + (id to category)
        save()
    }

    /**
     * Removes any line. A bank's own row comes back on the next link of that
     * institution: we are hiding it from this budget, not telling the bank it
     * never happened.
     */
    fun deleteEntry(id: String) {
        entries = entries.filter { it.id != id }
        categoryOverrides = categoryOverrides - id
        viewState = viewState.copy(excludedEntryIDs = viewState.excludedEntryIDs - id)
        save()
    }

    /** Where a typed line goes when the caller doesn't say. */
    val defaultTypedAccountID: String?
        get() {
            val picked = selectedAccountIDs
            if (picked.size == 1) return picked.first()
            return accounts.firstOrNull()?.id
        }

    /** Unlinks one institution and forgets what it sent. */
    fun disconnect(connectionID: String) {
        connections = connections.filter { it.id != connectionID }
        entries = entries.filter { it.connectionID != connectionID }
        settle()
        save()
    }

    fun disconnectAll() {
        connections = emptyList()
        entries = emptyList()
        settle()
        save()
    }

    /**
     * Keeps the view pointing at something that still exists — every budget's
     * view state, not just the open one.
     */
    private fun settle() {
        val live = selectableAccounts.map { it.id }.toSet()
        for (key in viewStates.keys.toList()) {
            viewStates[key] = if (!isConnected) {
                BankViewState()
            } else {
                val state = viewStates[key] ?: BankViewState()
                state.copy(accountIDs = state.accountIDs.intersect(live))
            }
        }
        JsonPrefs.persist(viewStates.toMap(), Keys.VIEW_STATE)
    }

    // MARK: Persistence

    private fun save() {
        JsonPrefs.persist(connections, Keys.CONNECTIONS)
        JsonPrefs.persist(entries, Keys.ENTRIES)
        JsonPrefs.persist(categoryOverrides, Keys.OVERRIDES)
        bump()
    }

    companion object {
        /**
         * The pseudo-connection legacy id for hand-typed lines (kept for the
         * connection-id namespace; typed lines now live under real accounts).
         */
        const val MANUAL_CONNECTION_ID = "manual"

        /** Stable across re-links of the same bank. */
        private fun connectionIdFor(institution: String): String =
            "bank:" + institution.lowercase().trim()

        val shared: BankLedgerStore by lazy { BankLedgerStore() }
    }
}
