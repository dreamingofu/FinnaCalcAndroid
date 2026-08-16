//
// HistoryDate.kt
//
// Port of iOS HistoryTabView.swift's HistoryDate — snapshot dates are
// persisted as ISO-8601 strings (mirroring the web's `Date.toISOString()`),
// then formatted for display. `parse` tolerates full ISO-8601 timestamps and
// bare `yyyy-MM-dd` values.
//

package com.finnacalc.android.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object HistoryDate {
    val monthStart: LocalDate get() = LocalDate.now().withDayOfMonth(1)

    private val medium = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val long = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    private val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    /** ISO-8601 string for storage (matches `toISOString()` shape, UTC midnight). */
    fun iso(date: LocalDate): String =
        date.atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

    fun parse(raw: String): LocalDate? {
        // Full ISO timestamp first, then a bare date.
        runCatching { return Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDate() }
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    /** `format(date, 'PPP')` — e.g. "June 1, 2026". */
    fun long(raw: String): String = parse(raw)?.format(long) ?: raw

    /** `format(date, 'MMM d, yyyy')` — e.g. "Jun 1, 2026". */
    fun medium(date: LocalDate): String = date.format(medium)

    /** `format(date, 'MMMM yyyy')` — e.g. "June 2026". */
    fun monthYear(date: LocalDate): String = date.format(monthYearFmt)

    /** The list-row date range: "June 1, 2026 - June 30, 2026". */
    fun range(start: String, end: String): String = long(start) + " - " + long(end)
}
