//
// BankStatementParser.kt
//
// Port of iOS BankStatementParser.swift + the CSVParser from
// BudgetTabView.swift — turns a CSV bank statement into budget lines with the
// same column handling, the same categorizer, the same conventions.
//

package com.finnacalc.android.features.budgeting

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// MARK: - Minimal quote-aware CSV parsing

object CSVParser {
    /**
     * Split CSV text into rows of fields, honoring double-quoted fields
     * (embedded commas and "" escapes).
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.clear()
        }

        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    val next = text.getOrNull(i + 1)
                    when (next) {
                        '"' -> {
                            field.append('"')
                            i += 1
                        }
                        ',' -> {
                            inQuotes = false
                            endField()
                            i += 1
                        }
                        '\n' -> {
                            inQuotes = false
                            endRow()
                            i += 1
                        }
                        '\r' -> {
                            inQuotes = false
                            endRow()
                            if (text.getOrNull(i + 2) == '\n') i += 1
                            i += 1
                        }
                        null -> inQuotes = false
                        else -> inQuotes = false  // stray char after quote — ignore
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\n' -> endRow()
                    '\r' -> { /* consumed; \n follows in CRLF */ }
                    else -> field.append(ch)
                }
            }
            i += 1
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    /** Parse a currency-ish number: strips $, commas, parentheses-negatives. */
    fun number(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.drop(1).dropLast(1)
        }
        s = s.replace("$", "").replace(",", "").trim()
        val value = s.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }
}

// MARK: - Statement parsing

object BankStatementParser {

    /** A user-facing reason a statement couldn't be read. */
    class ParseException(message: String) : Exception(message)

    /**
     * Budget lines from statement text, or a thrown [ParseException] whose
     * message names what was missing. (The caller reads the file via SAF and
     * hands the content over — the Android analogue of the security-scoped
     * URL read on iOS.)
     */
    fun parse(content: String, budgetType: BudgetType): List<BudgetItem> {
        val rows = CSVParser.parse(content)
        if (rows.size <= 1) throw ParseException("That CSV has no data rows.")

        val header = rows[0].map { it.lowercase().trim() }
        fun col(candidates: List<String>): Int? =
            header.indexOfFirst { field -> candidates.any { field.contains(it) } }.takeIf { it >= 0 }

        val dateIdx = col(listOf("date"))
        val descIdx = col(listOf("description", "memo", "payee", "merchant", "name", "details"))
        val amountIdx = col(listOf("amount"))
        val debitIdx = col(listOf("debit", "withdrawal"))
        val creditIdx = col(listOf("credit", "deposit"))

        if (amountIdx == null && debitIdx == null && creditIdx == null) {
            throw ParseException(
                "Couldn't find an amount column. Expected a header with \"Amount\" (or Debit/Credit)."
            )
        }

        val imported = mutableListOf<BudgetItem>()
        for (row in rows.drop(1)) {
            fun field(idx: Int?): String {
                if (idx == null || idx >= row.size) return ""
                return row[idx].trim()
            }

            var amount: Double
            var type: ItemType
            if (amountIdx != null) {
                val value = CSVParser.number(field(amountIdx)) ?: continue
                if (value == 0.0) continue
                // Statement convention: negative = money out.
                type = if (value < 0) ItemType.Expense else ItemType.Income
                amount = kotlin.math.abs(value)
            } else {
                val debit = CSVParser.number(field(debitIdx)) ?: 0.0
                val credit = CSVParser.number(field(creditIdx)) ?: 0.0
                when {
                    debit > 0 -> { type = ItemType.Expense; amount = debit }
                    credit > 0 -> { type = ItemType.Income; amount = credit }
                    else -> continue
                }
            }

            // Read the payee/memo for a real category instead of dropping
            // every row into "Other".
            val description = field(descIdx)
            val category = TransactionCategorizer.category(description, type, budgetType)

            imported.add(
                BudgetItem(
                    id = UUID.randomUUID().toString(),
                    category = category,
                    subcategory = description,
                    amount = amount,
                    frequency = Frequency.Monthly,
                    type = type,
                    isFixed = false,
                    budgetType = budgetType,
                    importDate = field(dateIdx).ifEmpty { null },
                    month = BudgetStore.UNDATED_MONTH_KEY,
                )
            )
        }

        if (imported.isEmpty()) throw ParseException("No usable rows found in that CSV.")
        return imported
    }

    /**
     * The statement's own date range, parsed leniently: statements write
     * dates a dozen ways and a snapshot needs real ones. null when nothing
     * parses.
     */
    fun dateRange(items: List<BudgetItem>): Pair<LocalDate, LocalDate>? {
        val dates = items.mapNotNull { it.importDate?.let(::parseDate) }
        val first = dates.minOrNull() ?: return null
        return first to dates.max()
    }

    private val dateFormats = listOf(
        "yyyy-MM-dd", "MM/dd/yyyy", "M/d/yyyy", "MM/dd/yy", "M/d/yy",
        "yyyy/MM/dd", "dd-MM-yyyy", "MMM d, yyyy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    fun parseDate(raw: String): LocalDate? {
        val trimmed = raw.take(20).trim()
        for (formatter in dateFormats) {
            try {
                return LocalDate.parse(trimmed, formatter)
            } catch (_: Exception) {
                // try the next format
            }
        }
        return null
    }
}
