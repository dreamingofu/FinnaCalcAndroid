//
// SubscriptionDetector.kt
//
// Port of iOS SubscriptionsView.swift's detector + the reminder-list builder
// from Core/Notifications/SubscriptionNotifier.swift. Detects recurring
// charges by pattern-matching over transactions already on the device (bank
// ledger entries and imported history snapshots) — no new data source, no
// backend call.
//
// This is detection, not cancellation. Scheduling the actual notifications
// lands with the notification infrastructure in Phase 8; the reminder list,
// its next-charge math and the per-subscription `remind` flag are here now
// because the Subscriptions screen is built on them.
//

package com.finnacalc.android.features.budgeting

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// MARK: - Detection

data class DetectedSubscription(
    /** normalized merchant key */
    val id: String,
    /** display name (as imported) */
    val merchant: String,
    val averageAmount: Double,
    val occurrences: Int,
    val lastDate: LocalDate,
    /** The cycle the gaps actually fit — a weekly charge must not say "Monthly". */
    val cadence: ChargeCadence,
    /** Accounts this merchant actually charged. */
    val accountIDs: Set<String> = emptySet(),
)

object SubscriptionDetector {

    /** One charge, in the shape both sources reduce to. */
    private data class Charge(
        val date: String,
        val name: String,
        val amount: Double,
        val category: String,
        val accountID: String?,
    )

    /**
     * The gaps that count as a repeating charge, in days, with the slack a
     * real biller needs: weekly, fortnightly, monthly, quarterly, yearly.
     */
    private val cadenceWindows = listOf(
        Triple(6.0, 8.0, ChargeCadence.Weekly),
        Triple(12.0, 16.0, ChargeCadence.Biweekly),
        Triple(20.0, 40.0, ChargeCadence.Monthly),
        Triple(80.0, 100.0, ChargeCadence.Quarterly),
        Triple(350.0, 380.0, ChargeCadence.Annually),
    )

    /**
     * Categories that repeat every month and are nobody's idea of a
     * subscription. Rent and the power bill would otherwise top the list and
     * bury the things a user might actually cancel.
     */
    private val notSubscriptions = setOf(
        "Housing", "Utilities", "Debt Payments", "Insurance", "Savings", "Retirement",
        "Rent/Lease", "Loan Payments", "Salaries/Wages", "Taxes",
    )

    /** Plaid's own primaries for the same "repeats but isn't a subscription" set. */
    private val plaidNotSubscriptions = setOf(
        "RENT_AND_UTILITIES", "LOAN_PAYMENTS", "TRANSFER_OUT", "TRANSFER_IN",
        "BANK_FEES", "INCOME",
    )

    /** Detection over history snapshots (imported transactions). */
    fun detectFromHistory(history: List<BudgetHistoryEntry>): List<DetectedSubscription> =
        detect(
            history
                .flatMap { it.budgetItems }
                .filter { it.type == ItemType.Expense && it.importDate != null }
                .map { Charge(it.importDate!!, it.subcategory, it.amount, it.category, null) }
        )

    /** The same heuristic over a linked bank's ledger. */
    fun detectFromLedger(entries: List<LedgerEntry>): List<DetectedSubscription> =
        detect(
            entries
                // Plaid's convention: positive is money out. Only spending
                // repeats in a way worth flagging.
                .filter { it.amount > 0 }
                .map { Charge(it.date, it.name, it.amount, it.category, it.accountID) }
        )

    /**
     * Flags a merchant as recurring when at least two charges land a plausible
     * billing cycle apart with amounts within 25% of each other. Deliberately
     * conservative — a missed subscription is far less annoying than a false
     * positive.
     */
    private fun detect(charges: List<Charge>): List<DetectedSubscription> {
        if (charges.isEmpty()) return emptyList()
        val grouped = charges.groupBy { normalize(it.name) }

        val results = mutableListOf<DetectedSubscription>()
        for ((key, group) in grouped) {
            if (key.isEmpty()) continue
            // A category is enough to rule one out; Plaid's own primaries come
            // through here too, hence both spellings.
            val category = group.firstOrNull()?.category ?: ""
            if (category in notSubscriptions) continue
            if (category.uppercase() in plaidNotSubscriptions) continue

            val dated = group
                .mapNotNull { charge ->
                    val date = runCatching { LocalDate.parse(charge.date.take(10)) }.getOrNull()
                        ?: return@mapNotNull null
                    date to charge.amount
                }
                .sortedBy { it.first }
            if (dated.size < 2) continue

            val accountIDs = group.mapNotNull { it.accountID }.toSet()
            val amounts = dated.map { it.second }
            val avgAmount = amounts.sum() / amounts.size
            if (avgAmount <= 0) continue
            val amountsConsistent = amounts.all { abs(it - avgAmount) / avgAmount < 0.25 }
            if (!amountsConsistent) continue

            // The cycle the most recent gap fits, since a biller that changed
            // cadence should be reported on what it does now.
            var matched: ChargeCadence? = null
            for (i in dated.indices.reversed()) {
                if (i == 0) break
                val days = ChronoUnit.DAYS.between(dated[i - 1].first, dated[i].first).toDouble()
                val window = cadenceWindows.firstOrNull { days >= it.first && days <= it.second }
                if (window != null) {
                    matched = window.third
                    break
                }
            }
            val cadence = matched ?: continue

            results.add(
                DetectedSubscription(
                    id = key,
                    merchant = group.firstOrNull()?.name ?: key,
                    averageAmount = avgAmount,
                    occurrences = dated.size,
                    lastDate = dated.last().first,
                    cadence = cadence,
                    accountIDs = accountIDs,
                )
            )
        }
        return results.sortedByDescending { it.averageAmount }
    }

    private fun normalize(name: String): String = name.trim().lowercase()
}

// MARK: - Reminder list

data class SubscriptionReminder(
    /** "sub:<merchantKey>" */
    val id: String,
    val name: String,
    /** Monthly-normalised figure. */
    val amount: Double,
    val nextCharge: LocalDateTime,
    /**
     * False for a subscription the user tracks but doesn't want notified
     * about. It still lists; it just isn't scheduled.
     */
    val remind: Boolean = true,
    val cadence: ChargeCadence = ChargeCadence.Monthly,
    /**
     * What lands on the statement each time — NOT the monthly-normalised
     * `amount`. A $10 weekly charge is $43.30 a month.
     */
    val chargeAmount: Double,
    /** Accounts this subscription bills; empty for a hand-tagged budget line. */
    val accountIDs: Set<String> = emptySet(),
    /** The backing budget line, when there is one (a tagged line or charge). */
    val itemID: String? = null,
    /** True when this came from detection rather than an explicit tag. */
    val detected: Boolean = false,
) {
    /**
     * Whether a charge of this subscription lands inside [period]. The cycle
     * is walked in both directions so a yearly bill belongs in a two-week
     * window only when that fortnight is the one it charges in. Bounded so a
     * weekly charge against a distant window can't spin.
     */
    fun charges(period: BudgetPeriod): Boolean {
        if (period is BudgetPeriod.Everything) return true
        var date = nextCharge.toLocalDate()
        // Forward.
        repeat(400) {
            if (period.contains(date.toString())) return true
            if (date.toString() > periodEnd(period)) return@repeat
            date = step(date, forward = true)
        }
        // Backward.
        date = nextCharge.toLocalDate()
        repeat(400) {
            if (period.contains(date.toString())) return true
            if (date.toString() < periodStart(period)) return@repeat
            date = step(date, forward = false)
        }
        return false
    }

    private fun step(date: LocalDate, forward: Boolean): LocalDate {
        val sign = if (forward) 1L else -1L
        return when (cadence) {
            ChargeCadence.Weekly -> date.plusDays(7 * sign)
            ChargeCadence.Biweekly -> date.plusDays(14 * sign)
            ChargeCadence.Monthly -> date.plusMonths(sign)
            ChargeCadence.Quarterly -> date.plusMonths(3 * sign)
            ChargeCadence.Annually -> date.plusYears(sign)
        }
    }

    private fun periodStart(period: BudgetPeriod): String = when (period) {
        is BudgetPeriod.Everything -> ""
        is BudgetPeriod.Month -> period.key + "-01"
        is BudgetPeriod.Range -> period.start
    }

    private fun periodEnd(period: BudgetPeriod): String = when (period) {
        is BudgetPeriod.Everything -> "9999"
        is BudgetPeriod.Month -> period.key + "-31"
        is BudgetPeriod.Range -> period.end
    }
}

/**
 * Builds the merged list of detected + hand-tagged recurring charges, keyed
 * by normalized merchant so a spelling difference between an imported name
 * and a manual description merges into one row. Ported from
 * SubscriptionNotifier.reminders; the notification scheduling half lands in
 * Phase 8.
 */
object SubscriptionReminders {

    /** Ids the user dismissed ("this isn't a subscription"). */
    private const val DISMISSED_KEY = "finnacalc-subscriptions-dismissed"

    fun dismissedIDs(): Set<String> =
        com.finnacalc.android.core.util.JsonPrefs.load<Set<String>>(DISMISSED_KEY) ?: emptySet()

    fun dismiss(id: String) {
        com.finnacalc.android.core.util.JsonPrefs.persist(dismissedIDs() + id, DISMISSED_KEY)
    }

    fun restore(id: String) {
        com.finnacalc.android.core.util.JsonPrefs.persist(dismissedIDs() - id, DISMISSED_KEY)
    }

    fun build(
        store: BudgetStore,
        budgetType: BudgetType? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<SubscriptionReminder> {
        val byKey = linkedMapOf<String, SubscriptionReminder>()
        val want = budgetType ?: store.budgetType
        val bank = BankLedgerStore.shared
        // Adopted, not currently open: All must include a bank this budget has
        // used even while a manual month is on screen.
        val usesBank = bank.hasAdopted(want)
        val scopedHistory = store.history.filter { it.budgetType == want }
        val bankEntries = if (usesBank) bank.entries else emptyList()
        val detected = SubscriptionDetector.detectFromHistory(scopedHistory) +
            SubscriptionDetector.detectFromLedger(bankEntries)
        val dismissed = dismissedIDs()

        for (sub in detected) {
            val key = normalize(sub.merchant)
            if (key.isEmpty()) continue
            // The user said this one is not a subscription. Only detection
            // honours that: a charge tagged by hand below still lists.
            if ("sub:$key" in dismissed) continue
            val next = nextCharge(sub.lastDate, sub.cadence, now) ?: continue
            byKey[key] = SubscriptionReminder(
                id = "sub:$key",
                name = sub.merchant,
                amount = sub.averageAmount * sub.cadence.frequency.monthlyMultiplier,
                nextCharge = next,
                cadence = sub.cadence,
                chargeAmount = sub.averageAmount,
                accountIDs = sub.accountIDs,
                detected = true,
            )
        }

        // Bank rows the user marked as subscriptions. Ahead of the manual pass
        // so a manual line of the same name still wins.
        val tagged = if (usesBank) {
            bank.items(
                BudgetPeriod.Everything,
                bank.selectableAccounts.map { it.id }.toSet(),
                want,
            )
        } else emptyList()
        for (item in tagged) {
            if (item.type != ItemType.Expense) continue
            val schedule = item.chargeSchedule ?: continue
            val name = item.subcategory.ifEmpty { item.category }
            val key = normalize(name)
            if (key.isEmpty()) continue
            val next = ChargeDateEngine.next(schedule, now) ?: continue
            byKey[key] = SubscriptionReminder(
                id = "sub:$key",
                name = name,
                amount = item.monthlyAmount,
                nextCharge = next,
                remind = schedule.remind,
                cadence = schedule.cadence,
                chargeAmount = item.amount,
                itemID = item.id,
            )
        }

        // Manual expenses tagged with a schedule. store.items spans every
        // planned month, so the name key collapses copied-forward duplicates.
        for (item in store.items) {
            if (item.type != ItemType.Expense) continue
            if (budgetType != null && item.budgetType != budgetType) continue
            val schedule = item.chargeSchedule ?: continue
            val name = item.subcategory.ifEmpty { item.category }
            val key = normalize(name)
            if (key.isEmpty()) continue
            val next = ChargeDateEngine.next(schedule, now) ?: continue
            byKey[key] = SubscriptionReminder(
                id = "sub:$key",
                name = name,
                amount = item.monthlyAmount,
                nextCharge = next,
                remind = schedule.remind,
                cadence = schedule.cadence,
                chargeAmount = item.amount,
                itemID = item.id,
            )
        }

        return byKey.values.sortedBy { it.nextCharge }
    }

    /**
     * Trim, lowercase, drop a trailing web suffix (Netflix.com → netflix) and
     * any non-alphanumeric noise, then collapse spaces.
     */
    fun normalize(s: String): String {
        var t = s.lowercase().trim()
        for (suffix in listOf(".com", ".net", ".org", ".io")) {
            if (t.endsWith(suffix)) t = t.dropLast(suffix.length)
        }
        return t.map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /**
     * The next occurrence of a detected charge, on its own cycle. Monthly
     * re-anchors on the day-of-month so a 29–31 seed isn't clamped down to 28
     * by a short month and kept there.
     */
    fun nextCharge(last: LocalDate, cadence: ChargeCadence, now: LocalDateTime): LocalDateTime? {
        if (cadence == ChargeCadence.Monthly) {
            val schedule = ChargeSchedule(ChargeCadence.Monthly, ChargeAnchor.Day(last.dayOfMonth))
            return ChargeDateEngine.next(schedule, now)
        }
        var next = last.atTime(ChargeDateEngine.FIRE_HOUR, 0)
        var guard = 0
        while (!next.isAfter(now) && guard < 500) {
            guard += 1
            next = when (cadence) {
                ChargeCadence.Weekly -> next.plusDays(7)
                ChargeCadence.Biweekly -> next.plusDays(14)
                ChargeCadence.Quarterly -> next.plusMonths(3)
                ChargeCadence.Annually -> next.plusYears(1)
                ChargeCadence.Monthly -> next.plusMonths(1)
            }
        }
        return if (next.isAfter(now)) next else null
    }
}
