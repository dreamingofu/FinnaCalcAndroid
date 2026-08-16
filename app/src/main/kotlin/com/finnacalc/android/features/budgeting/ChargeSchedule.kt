//
// ChargeSchedule.kt
//
// Port of iOS Features/Budgeting/ChargeSchedule.swift — when a subscription
// charges. A schedule is a cadence (how often) plus an anchor (where in the
// cycle); the cadence also decides the reminder lead time and the item's
// Frequency, so the budget totals and the reminders can't disagree.
//
// Date math runs on java.time (proleptic Gregorian, user's zone) and moves by
// calendar components rather than by seconds, so DST can't drift the series.
//

package com.finnacalc.android.features.budgeting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// MARK: - Cadence

/** How often a subscription charges — the first question the editor asks. */
@Serializable
enum class ChargeCadence(val raw: String) {
    @SerialName("weekly") Weekly("weekly"),
    @SerialName("biweekly") Biweekly("biweekly"),
    @SerialName("monthly") Monthly("monthly"),
    @SerialName("quarterly") Quarterly("quarterly"),
    @SerialName("annually") Annually("annually");

    val title: String
        get() = when (this) {
            Weekly -> "Weekly"
            Biweekly -> "Every 2 weeks"
            Monthly -> "Monthly"
            Quarterly -> "Quarterly"
            Annually -> "Annually"
        }

    /**
     * The budget frequency this cadence implies. Marking an expense as a
     * subscription sets the item's frequency from here, so a $10 weekly
     * charge contributes $43.30 to the monthly totals instead of $10.
     */
    val frequency: Frequency
        get() = when (this) {
            Weekly -> Frequency.Weekly
            Biweekly -> Frequency.Biweekly
            Monthly -> Frequency.Monthly
            Quarterly -> Frequency.Quarterly
            Annually -> Frequency.Yearly
        }

    /**
     * Three days' warning on a weekly charge is nearly half the cycle — by
     * the time you could act, the next one is already due. Short cadences get
     * one.
     */
    val leadDays: Int
        get() = when (this) {
            Weekly, Biweekly -> 1
            Monthly, Quarterly, Annually -> 3
        }

    /** Suffix for a per-charge figure, so a quarterly $300 doesn't read "/mo". */
    val amountSuffix: String
        get() = when (this) {
            Weekly -> "/wk"
            Biweekly -> "/2 wks"
            Monthly -> "/mo"
            Quarterly -> "/qtr"
            Annually -> "/yr"
        }

    /** Whether the schedule needs a month picked. */
    val needsMonth: Boolean get() = this == Quarterly || this == Annually

    companion object {
        /**
         * The cadence matching a frequency, so opening the editor on an
         * existing expense starts from what it already says. Semimonthly IS
         * biweekly's "1st and 3rd" anchor. Daily has no subscription cadence;
         * the editor hides the section rather than silently picking one.
         */
        fun from(frequency: Frequency): ChargeCadence? = when (frequency) {
            Frequency.Weekly -> Weekly
            Frequency.Biweekly, Frequency.Semimonthly -> Biweekly
            Frequency.Monthly -> Monthly
            Frequency.Quarterly -> Quarterly
            Frequency.Yearly -> Annually
            Frequency.Daily -> null
        }
    }
}

// MARK: - Anchor

/** Where in the cadence the charge lands. */
@Serializable
sealed class ChargeAnchor {
    /**
     * A day of the month. `0` means "the last day"; 29–31 fall back to the
     * last day in months too short for them.
     */
    @Serializable
    @SerialName("day")
    data class Day(val day: Int) : ChargeAnchor()

    /**
     * "The third Saturday". `week` is 1..4, or 5 for the last one in the
     * month. `weekday` is Calendar's convention: Sunday = 1 .. Saturday = 7
     * (kept from iOS so schedules read identically).
     *
     * Under the biweekly cadence this means TWO charges a month: `week` and
     * the week two later.
     */
    @Serializable
    @SerialName("nthWeekday")
    data class NthWeekday(val week: Int, val weekday: Int) : ChargeAnchor()

    /**
     * A weekday on its own. Weekly reads it as "every Friday"; biweekly as
     * "the 1st and 3rd Friday".
     */
    @Serializable
    @SerialName("weekday")
    data class Weekday(val weekday: Int) : ChargeAnchor()

    /**
     * A strict stride (7 or 14 days, from the cadence) counted from a first
     * charge date (ISO yyyy-MM-dd).
     */
    @Serializable
    @SerialName("everyNDays")
    data class EveryNDays(val start: String) : ChargeAnchor()
}

// MARK: - Schedule

/**
 * A subscription's full charge rule. `BudgetItem.chargeSchedule == null` is
 * what marks an expense as *not* a subscription — one field, so the flag and
 * the timing can't contradict each other.
 */
@Serializable
data class ChargeSchedule(
    val cadence: ChargeCadence,
    val anchor: ChargeAnchor,
    /** 1..12, for the cadences that charge in a particular month. */
    val month: Int? = null,
    /**
     * Whether it also notifies before charging. Starts OFF: tracking a
     * subscription and being nagged about it are separate asks.
     */
    val remind: Boolean = false,
) {
    /**
     * What this schedule costs per month, which the anchor can refine: under
     * the biweekly cadence, "every 14 days" really is 26 charges a year, but
     * "the 1st and 3rd Friday" is exactly 2 a month.
     */
    val frequency: Frequency
        get() {
            if (cadence == ChargeCadence.Biweekly) {
                when (anchor) {
                    is ChargeAnchor.Weekday, is ChargeAnchor.NthWeekday -> return Frequency.Semimonthly
                    else -> {}
                }
            }
            return cadence.frequency
        }

    /** Plain-language summary of the rule, e.g. "Monthly on the 15th". */
    val summary: String
        get() {
            val monthName = month?.let { ChargeDateEngine.monthName(it) }
            return when {
                cadence == ChargeCadence.Weekly && anchor is ChargeAnchor.Weekday ->
                    "Every ${ChargeDateEngine.weekdayName(anchor.weekday)}"
                cadence == ChargeCadence.Weekly && anchor is ChargeAnchor.EveryNDays ->
                    "Every 7 days"
                cadence == ChargeCadence.Biweekly && anchor is ChargeAnchor.Weekday ->
                    "1st and 3rd ${ChargeDateEngine.weekdayName(anchor.weekday)} each month"
                cadence == ChargeCadence.Biweekly && anchor is ChargeAnchor.NthWeekday ->
                    "${ChargeDateEngine.weekPairLabel(anchor.week)} ${ChargeDateEngine.weekdayName(anchor.weekday)} each month"
                cadence == ChargeCadence.Biweekly && anchor is ChargeAnchor.EveryNDays ->
                    "Every 14 days"
                anchor is ChargeAnchor.Day -> {
                    val where = if (anchor.day == 0) "the last day" else "the ${ChargeDateEngine.ordinal(anchor.day)}"
                    when {
                        monthName != null && cadence == ChargeCadence.Quarterly ->
                            "Every 3 months from $monthName, on $where"
                        monthName != null -> "Every $monthName, on $where"
                        else -> "Monthly on $where"
                    }
                }
                anchor is ChargeAnchor.NthWeekday -> {
                    val where = "${ChargeDateEngine.ordinalWeek(anchor.week)} ${ChargeDateEngine.weekdayName(anchor.weekday)}"
                    when {
                        monthName != null && cadence == ChargeCadence.Quarterly ->
                            "Every 3 months from $monthName, on the $where"
                        monthName != null -> "Every $monthName, on the $where"
                        else -> "Monthly on the $where"
                    }
                }
                else -> cadence.title
            }
        }
}

// MARK: - Date engine

/** Resolves a schedule to real charge dates. */
object ChargeDateEngine {

    /** Charges are materialised at 9am, matching what the notifier schedules. */
    const val FIRE_HOUR = 9

    /** The next charge strictly after `after`, or null if the rule can't resolve. */
    fun next(schedule: ChargeSchedule, after: LocalDateTime): LocalDateTime? {
        val cadence = schedule.cadence
        val anchor = schedule.anchor
        return when {
            cadence == ChargeCadence.Weekly && anchor is ChargeAnchor.Weekday ->
                nextWeekday(anchor.weekday, after)
            cadence == ChargeCadence.Weekly && anchor is ChargeAnchor.EveryNDays ->
                parseIso(anchor.start)?.let { nextEveryNDays(it, 7, after) }
            cadence == ChargeCadence.Biweekly && anchor is ChargeAnchor.EveryNDays ->
                parseIso(anchor.start)?.let { nextEveryNDays(it, 14, after) }
            cadence == ChargeCadence.Biweekly && anchor is ChargeAnchor.Weekday ->
                nextPairedWeeks(1, anchor.weekday, after)
            cadence == ChargeCadence.Biweekly && anchor is ChargeAnchor.NthWeekday ->
                nextPairedWeeks(anchor.week, anchor.weekday, after)
            // Month-grid cadences. Monthly steps 1, quarterly 3, annually 12.
            cadence == ChargeCadence.Monthly -> nextOnMonthGrid(anchor, 1, null, after)
            cadence == ChargeCadence.Quarterly -> nextOnMonthGrid(anchor, 3, schedule.month, after)
            cadence == ChargeCadence.Annually -> nextOnMonthGrid(anchor, 12, schedule.month, after)
            else -> null
        }
    }

    private fun parseIso(date: String): LocalDate? =
        runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    private fun atFireHour(date: LocalDate): LocalDateTime = date.atTime(FIRE_HOUR, 0)

    /** Resolves an anchor inside one month (monthStart = first of month). */
    private fun anchored(anchor: ChargeAnchor, monthStart: LocalDate): LocalDateTime? {
        val total = monthStart.lengthOfMonth()
        return when (anchor) {
            is ChargeAnchor.Day -> {
                // 0 = last day; anything past the end of a short month clamps
                // to it, so "the 31st" charges 28/29 Feb rather than vanishing.
                val day = if (anchor.day == 0) total else anchor.day.coerceIn(1, total)
                atFireHour(monthStart.withDayOfMonth(day))
            }
            is ChargeAnchor.NthWeekday -> nthWeekday(anchor.week, anchor.weekday, monthStart, total)
            is ChargeAnchor.Weekday -> nthWeekday(1, anchor.weekday, monthStart, total)
            is ChargeAnchor.EveryNDays -> null
        }
    }

    /**
     * The `week`-th `weekday` of a month; week 5 (or a week that doesn't
     * exist, e.g. a 5th Friday) means the last one. `weekday` uses the iOS
     * Calendar convention (Sunday = 1 .. Saturday = 7).
     */
    private fun nthWeekday(week: Int, weekday: Int, monthStart: LocalDate, total: Int): LocalDateTime {
        // java.time: Monday=1..Sunday=7 → convert Calendar's Sunday=1..Saturday=7.
        val firstWeekday = (monthStart.dayOfWeek.value % 7) + 1
        val offsetToFirst = (weekday - firstWeekday + 7) % 7
        var lastMatchingDay = offsetToFirst + 1
        while (lastMatchingDay + 7 <= total) lastMatchingDay += 7
        val day = if (week >= 5) {
            lastMatchingDay
        } else {
            val wanted = offsetToFirst + 1 + (week.coerceAtLeast(1) - 1) * 7
            if (wanted <= total) wanted else lastMatchingDay
        }
        return atFireHour(monthStart.withDayOfMonth(day))
    }

    /**
     * Walks the month grid until the anchored date lands strictly after
     * `after`. `alignedTo` pins which months are in the grid: for quarterly,
     * month 4 means Jan/Apr/Jul/Oct.
     */
    private fun nextOnMonthGrid(anchor: ChargeAnchor, stride: Int, alignedTo: Int?, after: LocalDateTime): LocalDateTime? {
        var ms = after.toLocalDate().withDayOfMonth(1)

        if (alignedTo != null && stride > 1) {
            val current = ms.monthValue
            // Step forward to the next month that sits on the aligned grid.
            val delta = ((alignedTo - current) % stride + stride) % stride
            if (delta > 0) ms = ms.plusMonths(delta.toLong())
        }

        // Three hops covers: this period, the next, and one spare.
        repeat(3) {
            val candidate = anchored(anchor, ms)
            if (candidate != null && candidate.isAfter(after)) return candidate
            ms = ms.plusMonths(stride.toLong())
        }
        return null
    }

    /** "Every Friday". */
    private fun nextWeekday(weekday: Int, after: LocalDateTime): LocalDateTime {
        val today = after.toLocalDate()
        val todayWeekday = (today.dayOfWeek.value % 7) + 1
        val offset = (weekday - todayWeekday + 7) % 7
        val day = today.plusDays(offset.toLong())
        val candidate = atFireHour(day)
        if (candidate.isAfter(after)) return candidate
        // Right day, but 9am has already gone by.
        return atFireHour(day.plusDays(7))
    }

    /**
     * Two charges a month on the same weekday, `firstWeek` and the week two
     * later: week 1 gives "1st and 3rd", week 2 gives "2nd and 4th", week 3
     * gives "3rd and last". Deliberately not a 14-day stride.
     */
    private fun nextPairedWeeks(firstWeek: Int, weekday: Int, after: LocalDateTime): LocalDateTime? {
        var ms = after.toLocalDate().withDayOfMonth(1)
        val first = firstWeek.coerceAtLeast(1)
        val weeks = listOf(first, (first + 2).coerceAtMost(5))
        repeat(3) {
            val total = ms.lengthOfMonth()
            val candidate = weeks
                .map { nthWeekday(it, weekday, ms, total) }
                .filter { it.isAfter(after) }
                .minOrNull()
            if (candidate != null) return candidate
            ms = ms.plusMonths(1)
        }
        return null
    }

    /**
     * A strict N-day stride from a start date. Counts whole calendar days, so
     * DST and time-zone changes can't shift the series.
     */
    private fun nextEveryNDays(start: LocalDate, n: Int, after: LocalDateTime): LocalDateTime? {
        val first = atFireHour(start)
        if (first.isAfter(after)) return first

        val elapsed = ChronoUnit.DAYS.between(start, after.toLocalDate())
        var k = (elapsed / n).coerceAtLeast(0)
        repeat(3) {
            val candidate = atFireHour(start.plusDays(k * n))
            if (candidate.isAfter(after)) return candidate
            k += 1
        }
        return null
    }

    // MARK: Naming helpers

    fun weekdayName(weekday: Int): String {
        val names = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        return names[weekday.coerceIn(1, 7) - 1]
    }

    fun monthName(month: Int): String? {
        val names = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        return if (month in 1..12) names[month - 1] else null
    }

    /** "1st and 3rd" for week 1, "2nd and 4th" for week 2, and so on. */
    fun weekPairLabel(week: Int): String {
        val first = week.coerceAtLeast(1)
        val second = (first + 2).coerceAtMost(5)
        fun name(w: Int) = if (w >= 5) "last" else ordinal(w)
        return "${name(first)} and ${name(second)}"
    }

    fun ordinalWeek(week: Int): String = when (week) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        4 -> "fourth"
        else -> "last"
    }

    fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
