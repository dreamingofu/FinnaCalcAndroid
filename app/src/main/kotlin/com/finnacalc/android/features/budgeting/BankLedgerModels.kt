//
// BankLedgerModels.kt
//
// Port of the model half of iOS Features/Budgeting/BankLedger.swift: the
// accounts under each connection, every transaction they returned, and the
// per-budget view state. (The store lives in BankLedgerStore.kt; the picker
// and range sheets live with the budgeting UI.)
//

package com.finnacalc.android.features.budgeting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One account inside a linked institution: a checking, a savings, a card. */
@Serializable
data class BankAccount(
    val id: String,
    val name: String,
    /** Last four digits, when the bank gives them. */
    val mask: String? = null,
    /** Plaid's account type ("depository", "credit", "investment"…). */
    val kind: String? = null,
    val institution: String,
) {
    /** "Checking ••1234", or just the name when there's no mask. */
    val displayName: String
        get() = if (mask.isNullOrEmpty()) name else "$name ••$mask"

    /**
     * A credit card's charges are money out just like a debit, but a payment
     * TO the card is not income. Kept so that rule has somewhere to live.
     */
    val isCredit: Boolean get() = (kind ?: "").lowercase().contains("credit")
}

/** One linked institution and everything under it. */
@Serializable
data class BankConnection(
    val id: String,
    val institution: String,
    /** Epoch millis (iOS stores a Date). */
    val linkedAt: Long,
    val accounts: List<BankAccount>,
)

/**
 * One stored transaction. Deliberately not `BankTransaction`: that mirrors
 * the API response, this is what we keep, and it carries which connection it
 * came from so disconnecting a bank takes its transactions with it.
 */
@Serializable
data class LedgerEntry(
    val id: String,
    val connectionID: String,
    val accountID: String? = null,
    /** "yyyy-MM-dd", as Plaid sends it. */
    val date: String,
    val name: String,
    /** Plaid convention, kept as sent: positive is money out. */
    val amount: Double,
    val category: String,
    /**
     * Set when the user marks this charge a subscription in the editor. The
     * same field a manual budget line carries; null means it is not one.
     */
    val chargeSchedule: ChargeSchedule? = null,
    /**
     * Typed by the user rather than sent by the bank. It still belongs to a
     * real account: cash spent out of a checking account is that account's
     * business.
     */
    val typedByUser: Boolean = false,
)

/** The stretch of time My Budget is drawn for when it's reading a bank. */
@Serializable
sealed class BudgetPeriod {
    /** Everything the bank sent, which is about 90 days. */
    @Serializable
    @SerialName("everything")
    data object Everything : BudgetPeriod()

    /** A calendar month, "yyyy-MM". */
    @Serializable
    @SerialName("month")
    data class Month(val key: String) : BudgetPeriod()

    /** Any two dates the user picked (ISO yyyy-MM-dd). */
    @Serializable
    @SerialName("range")
    data class Range(val start: String, val end: String) : BudgetPeriod()

    /**
     * Short enough to sit in half a pill: a range is numeric,
     * "05/26/26 - 07/26/26".
     */
    val label: String
        get() = when (this) {
            is Everything -> "All transactions"
            is Month -> BudgetStore.monthDisplayName(key)
            is Range -> "${slashed(start)} - ${slashed(end)}"
        }

    /**
     * Whether a "yyyy-MM-dd" falls inside. String comparison is safe here:
     * ISO dates sort chronologically as text.
     */
    fun contains(isoDate: String): Boolean = when (this) {
        is Everything -> true
        is Month -> isoDate.startsWith(key)
        is Range -> isoDate in start..end
    }

    companion object {
        /** "2026-05-26" → "05/26/26", echoing the input if it doesn't parse. */
        fun slashed(isoDate: String): String {
            val parts = isoDate.take(10).split("-")
            if (parts.size != 3 || parts[0].length != 4) return isoDate
            return "${parts[1]}/${parts[2]}/${parts[0].takeLast(2)}"
        }
    }
}

/**
 * What My Budget is currently reading. Remembered, like the manual budget's
 * open slot: leaving the tab and coming back should land where you left off.
 */
@Serializable
data class BankViewState(
    /** False means the manual budget, whether or not a bank is linked. */
    val usingBank: Boolean = false,
    /**
     * Which accounts are ticked. Empty means all of them, so a fresh link and
     * a deliberate "select everything" are the same state.
     */
    val accountIDs: Set<String> = emptySet(),
    /**
     * Typed lines the user has unticked. Records the exceptions rather than
     * the inclusions: a line added later is in by default.
     */
    val excludedEntryIDs: Set<String> = emptySet(),
    val period: BudgetPeriod = BudgetPeriod.Everything,
    /**
     * Whether this budget has EVER read the bank, as opposed to reading it
     * right now. Subscriptions' "All" keys off this.
     */
    val everUsedBank: Boolean = false,
)
