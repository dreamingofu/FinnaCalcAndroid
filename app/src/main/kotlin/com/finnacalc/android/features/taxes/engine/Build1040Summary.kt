/**
 * Build1040Summary.kt
 *
 * Port of iOS Features/Taxes/Engine/Build1040Summary.swift.
 *
 * Pure structuring of a TaxCalculationResult into print/share-ready groups. No
 * Compose, no I/O — the printable view and any future PDF generator both read
 * this. Same group/line ordering, labels, form references, and conditional
 * inclusion rules as the Swift and TypeScript originals.
 *
 * Credit entries are emitted in sorted-key order, as in the Swift port, so the
 * summary is deterministic regardless of map iteration order.
 */

package com.finnacalc.android.features.taxes.engine

import kotlin.math.abs

data class SummaryLine(val label: String, val amount: Double, val formRef: String? = null)

data class SummaryGroup(val title: String, val lines: List<SummaryLine>)

data class Form1040Headline(val label: String, val amount: Double, val owes: Boolean)

data class Form1040SummaryState(
    val name: String,
    val hasIncomeTax: Boolean,
    val tax: Double,
    val refundOrOwed: Double,
    val note: String? = null,
)

data class Form1040Summary(
    val taxYear: Int = 2025,
    val filingStatusLabel: String,
    val headline: Form1040Headline,
    val groups: List<SummaryGroup>,
    val state: Form1040SummaryState? = null,
)

private val filingLabels: Map<FilingStatus, String> = mapOf(
    FilingStatus.Single to "Single",
    FilingStatus.Mfj to "Married filing jointly",
    FilingStatus.Mfs to "Married filing separately",
    FilingStatus.Hoh to "Head of household",
    FilingStatus.Qss to "Qualifying surviving spouse",
)

/** Title-case a camelCase credit key: "childTaxCredit" → "Child tax credit". */
private fun labelizeCredit(key: String): String {
    val spaced = buildString {
        for (ch in key) {
            if (ch.isUpperCase()) append(' ')
            append(ch)
        }
    }.lowercase()
    if (spaced.isEmpty()) return spaced
    return spaced.first().uppercase() + spaced.drop(1)
}

fun build1040Summary(r: TaxCalculationResult): Form1040Summary {
    val groups = mutableListOf<SummaryGroup>()

    groups.add(
        SummaryGroup(
            "Income",
            listOf(
                SummaryLine("Total income", r.totalIncome, "1040 line 9"),
                SummaryLine("Adjustments to income", r.totalAdjustments, "Schedule 1"),
                SummaryLine("Adjusted gross income (AGI)", r.agi, "1040 line 11"),
            )
        )
    )

    val deductionLines = mutableListOf(
        SummaryLine(
            if (r.deductionUsed == DeductionUsed.Itemized) "Itemized deductions" else "Standard deduction",
            r.deductionAmount,
            "1040 line 12",
        )
    )
    if (r.qbiDeduction > 0) {
        deductionLines.add(SummaryLine("QBI deduction", r.qbiDeduction, "1040 line 13"))
    }
    deductionLines.add(SummaryLine("Taxable income", r.taxableIncome, "1040 line 15"))
    groups.add(SummaryGroup("Deductions", deductionLines))

    val taxLines = mutableListOf(SummaryLine("Income tax", r.regularTax, "1040 line 16"))
    if (r.amt > 0) {
        taxLines.add(SummaryLine("Alternative minimum tax", r.amt, "Schedule 2"))
    }
    for (key in r.nonrefundableCredits.keys.sorted()) {
        val amount = r.nonrefundableCredits.getValue(key)
        taxLines.add(SummaryLine("− ${labelizeCredit(key)}", -amount))
    }
    if (r.seTax > 0) {
        taxLines.add(SummaryLine("Self-employment tax", r.seTax, "Schedule 2"))
    }
    if (r.additionalMedicareTax > 0) {
        taxLines.add(SummaryLine("Additional Medicare tax", r.additionalMedicareTax))
    }
    if (r.niit > 0) {
        taxLines.add(SummaryLine("Net investment income tax", r.niit))
    }
    taxLines.add(SummaryLine("Total tax", r.totalTax, "1040 line 24"))
    groups.add(SummaryGroup("Tax & credits", taxLines))

    val payLines = mutableListOf<SummaryLine>()
    for (key in r.refundableCredits.keys.sorted()) {
        payLines.add(SummaryLine(labelizeCredit(key), r.refundableCredits.getValue(key)))
    }
    payLines.add(SummaryLine("Total payments & refundable credits", r.totalPayments, "1040 line 33"))
    groups.add(SummaryGroup("Payments", payLines))

    val s = r.state
    val stateBlock = if (s != null && s.supported) {
        Form1040SummaryState(
            name = s.name,
            hasIncomeTax = s.hasIncomeTax,
            tax = s.tax,
            refundOrOwed = s.refundOrOwed,
            note = s.note,
        )
    } else null

    return Form1040Summary(
        taxYear = 2025,
        filingStatusLabel = filingLabels[r.filingStatus] ?: "",
        headline = Form1040Headline(
            label = if (r.owes) "Estimated balance due" else "Estimated federal refund",
            amount = abs(r.refundOrOwed),
            owes = r.owes,
        ),
        groups = groups,
        state = stateBlock,
    )
}
