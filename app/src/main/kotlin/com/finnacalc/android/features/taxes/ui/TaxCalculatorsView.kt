//
// TaxCalculatorsView.kt
//
// Port of iOS Features/Taxes/UI/TaxCalculatorsView.swift — the "Tax
// Optimization Tools" screen: five self-contained federal-tax calculators
// (Tax Calculator, Refund Estimator, Deduction Finder, Quarterly Payments,
// Withholding) backed by the shared pure-Kotlin tax engine.
//
// Same inputs, same formulas (bracketTax / marginalRate /
// computeSelfEmploymentTax / STANDARD_DEDUCTION_2025), same hypothetical
// deduction catalog, same headline figures and rounding as iOS and the web.
//
// Layout follows the iOS adaptation of the web's sidebar + detail panel: the
// tool list scrolls at the top and the active calculator renders below it.
//

package com.finnacalc.android.features.taxes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCBadge
import com.finnacalc.android.core.designsystem.FCBadgeVariant
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.FCCardDescription
import com.finnacalc.android.core.designsystem.FCCardHeader
import com.finnacalc.android.core.designsystem.FCCardTitle
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.taxes.engine.FilingStatus
import com.finnacalc.android.features.taxes.engine.IncomeOwner
import com.finnacalc.android.features.taxes.engine.STANDARD_DEDUCTION_2025
import com.finnacalc.android.features.taxes.engine.bracketTax
import com.finnacalc.android.features.taxes.engine.computeSelfEmploymentTax
import com.finnacalc.android.features.taxes.engine.marginalRate
import kotlin.math.max

// MARK: - Tool catalog

private enum class Tool(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    TaxCalculator("Tax Calculator", "Estimate your federal tax liability", Icons.Filled.Functions),
    RefundEstimator("Refund Estimator", "See your potential federal refund", Icons.Filled.AttachMoney),
    DeductionFinder("Deduction Finder", "Discover potential federal write-offs", Icons.Filled.Search),
    QuarterlyCalculator(
        "Quarterly Payments",
        "Calculate estimated federal tax payments",
        Icons.Filled.CalendarMonth,
    ),
    WithholdingCalculator(
        "Withholding Calculator",
        "Adjust federal paycheck withholdings",
        Icons.Filled.PieChart,
    ),
}

@Composable
fun TaxCalculatorsView(modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(Tool.TaxCalculator) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Tax Optimization Tools",
                style = Theme.sans(Theme.FontSize.xl2, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Text(
                "Quick federal calculators for planning ahead",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }

        // Tool list (the web's left sidebar)
        FCCard {
            FCCardHeader { FCCardTitle("Tax Tools") }
            FCCardContent {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tool.entries.forEach { tool ->
                        val isActive = active == tool
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) Theme.colors.muted else Color.Transparent)
                                .fcPressable { active = tool }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isActive) {
                                Box(
                                    Modifier
                                        .width(2.dp)
                                        .height(36.dp)
                                        .background(Theme.colors.primary)
                                )
                            }
                            Icon(
                                tool.icon,
                                contentDescription = null,
                                tint = Theme.colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    tool.title,
                                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
                                    color = Theme.colors.foreground,
                                )
                                Text(
                                    tool.description,
                                    style = Theme.sans(Theme.FontSize.xs),
                                    color = Theme.colors.mutedForeground,
                                )
                            }
                        }
                    }
                }
            }
        }

        when (active) {
            Tool.TaxCalculator -> TaxCalculatorCard()
            Tool.RefundEstimator -> RefundEstimatorCard()
            Tool.DeductionFinder -> DeductionFinderCard()
            Tool.QuarterlyCalculator -> QuarterlyCalculatorCard()
            Tool.WithholdingCalculator -> WithholdingCalculatorCard()
        }
    }
}

// MARK: - Shared helpers

/** Sanitize a numeric input string. Mirrors the web `sanitizeNumber`. */
private fun sanitizeNumber(value: String): Double =
    value.filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0

/** Strip everything except digits, dot, and minus — the web onChange filter. */
private fun filterNumericInput(value: String): String =
    value.filter { it.isDigit() || it == '.' || it == '-' }

/** `$` + grouped whole dollars. */
private fun dollarsInt(value: Double): String = CalcFormat.currency(value, 0)

/** `$` + grouped figure keeping typed cents (the web's bare `toLocaleString`). */
private fun dollarsLoc(value: Double): String = "$" + CalcFormat.decimal(value, 2)

private fun standardDeductionFor(status: FilingStatus): Double =
    STANDARD_DEDUCTION_2025[status] ?: STANDARD_DEDUCTION_2025.getValue(FilingStatus.Single)

/** A labeled row inside a tinted panel. */
@Composable
private fun TaxRow(
    label: String,
    value: String,
    valueColor: Color = Theme.colors.foreground,
    bold: Boolean = true,
    small: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = Theme.sans(if (small) Theme.FontSize.sm else Theme.FontSize.base),
            color = Theme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = Theme.figure(
                if (small) Theme.FontSize.sm else Theme.FontSize.base,
                if (bold) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = valueColor,
        )
    }
}

/** A muted rounded panel wrapping rows. */
@Composable
private fun MutedPanel(padding: Int = 12, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.lg))
            .background(Theme.colors.muted)
            .padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A labeled numeric text input, filtered on every edit like the web. */
@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    helper: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
            color = Theme.colors.foreground,
        )
        FCTextField(
            placeholder = placeholder,
            value = value,
            onValueChange = { onValueChange(filterNumericInput(it)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            showsPlaceholder = true,
        )
        if (helper != null) {
            Text(helper, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
        }
    }
}

/** Placeholder empty-state shown before a calculation has been run. */
@Composable
private fun EmptyResult(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.lg))
            .background(Theme.colors.muted)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Theme.colors.mutedForeground, modifier = Modifier.size(40.dp))
        Text(
            message,
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
        )
    }
}

/**
 * The soft tints the web uses for result panels. Derived from the theme's
 * own positive / brand / negative / caution hues so both palettes stay in
 * the design system rather than hard-coding Tailwind values.
 */
private object TaxTint {
    val greenBG @Composable get() = Theme.colors.positive.copy(alpha = if (Theme.colors.isDark) 0.14f else 0.08f)
    val greenBorder @Composable get() = Theme.colors.positive.copy(alpha = 0.35f)
    val blueBG @Composable get() = Theme.colors.brandBlue.copy(alpha = if (Theme.colors.isDark) 0.16f else 0.08f)
    val blueStrong @Composable get() = Theme.colors.brandBlue
    val redBG @Composable get() = Theme.colors.negative.copy(alpha = if (Theme.colors.isDark) 0.14f else 0.08f)
    val yellowBG @Composable get() = Theme.colors.caution.copy(alpha = if (Theme.colors.isDark) 0.16f else 0.12f)
    val yellowBorder @Composable get() = Theme.colors.caution.copy(alpha = 0.4f)
}

/** The three statuses the quick tools offer, applied to real 2025 figures. */
@Composable
private fun FilingStatusPicker(selection: FilingStatus, onSelect: (FilingStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Filing Status",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
            color = Theme.colors.foreground,
        )
        FilingStatusMenu(selection, onSelect)
    }
}

@Composable
private fun FilingStatusMenu(selection: FilingStatus, onSelect: (FilingStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selection) {
        FilingStatus.Mfj -> "Married Filing Jointly"
        FilingStatus.Hoh -> "Head of Household"
        else -> "Single"
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(Theme.Radius.md))
                .background(Theme.colors.background)
                .border(1.dp, Theme.colors.input, RoundedCornerShape(Theme.Radius.md))
                .fcPressable { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = Theme.sans(Theme.FontSize.base),
                color = Theme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = Theme.colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                FilingStatus.Single to "Single",
                FilingStatus.Mfj to "Married Filing Jointly",
                FilingStatus.Hoh to "Head of Household",
            ).forEach { (status, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = Theme.sans(Theme.FontSize.base)) },
                    onClick = {
                        onSelect(status)
                        expanded = false
                    },
                )
            }
        }
    }
}

// MARK: - Tax Calculator

private data class TaxCalcResult(
    val grossIncome: Double,
    val standardDeduction: Double,
    val taxableIncome: Double,
    val estimatedTax: Double,
    val effectiveRate: Double,
    val marginalRate: Double,
)

@Composable
private fun TaxCalculatorCard() {
    var income by remember { mutableStateOf("") }
    var filingStatus by remember { mutableStateOf(FilingStatus.Single) }
    var results by remember { mutableStateOf<TaxCalcResult?>(null) }

    FCCard {
        FCCardHeader {
            FCCardTitle("Federal Tax Calculator")
            FCCardDescription("Estimate your federal income tax liability")
        }
        FCCardContent {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NumericField("Annual Income", income, { income = it }, placeholder = "$75,000")
                    FilingStatusPicker(filingStatus) { filingStatus = it }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Deduction Type",
                            style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
                            color = Theme.colors.foreground,
                        )
                        FCTextField(
                            placeholder = "",
                            value = "Standard Deduction (Estimated)",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                        )
                        Text(
                            "Itemized deductions can be explored in the Deduction Finder.",
                            style = Theme.sans(Theme.FontSize.xs),
                            color = Theme.colors.mutedForeground,
                        )
                    }

                    FCButton(
                        onClick = {
                            val incomeNum = sanitizeNumber(income)
                            val standardDeduction = standardDeductionFor(filingStatus)
                            val taxableIncome = max(0.0, incomeNum - standardDeduction)
                            val tax = bracketTax(taxableIncome, filingStatus)
                            results = TaxCalcResult(
                                grossIncome = incomeNum,
                                standardDeduction = standardDeduction,
                                taxableIncome = taxableIncome,
                                estimatedTax = tax,
                                effectiveRate = if (incomeNum > 0) (tax / incomeNum) * 100 else 0.0,
                                marginalRate = marginalRate(taxableIncome, filingStatus) * 100,
                            )
                        },
                        size = FCButtonSize.Lg,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Calculate Federal Tax") }
                }

                val r = results
                if (r != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Federal Tax Results",
                            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MutedPanel { TaxRow("Gross Income", dollarsLoc(r.grossIncome)) }
                            MutedPanel { TaxRow("Standard Deduction", dollarsInt(r.standardDeduction)) }
                            MutedPanel { TaxRow("Taxable Income", dollarsLoc(r.taxableIncome)) }
                            HorizontalDivider(color = Theme.colors.border)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Theme.Radius.lg))
                                    .background(TaxTint.redBG)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Estimated Federal Tax",
                                    style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                                    color = Theme.colors.foreground,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    dollarsInt(r.estimatedTax),
                                    style = Theme.figure(Theme.FontSize.xl2, FontWeight.Bold),
                                    color = Theme.colors.negative,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RateTile("Effective Rate", "${CalcFormat.fixed(r.effectiveRate, 1)}%", Modifier.weight(1f))
                                RateTile("Marginal Rate", "${CalcFormat.fixed(r.marginalRate, 0)}%", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateTile(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Theme.Radius.sm))
            .background(TaxTint.blueBG)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(caption, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
        Text(
            value,
            style = Theme.figure(Theme.FontSize.base, FontWeight.SemiBold),
            color = Theme.colors.foreground,
        )
    }
}

// MARK: - Refund Estimator

private data class RefundResult(
    val taxLiability: Double,
    val withheld: Double,
    val credits: Double,
    val refund: Double,
    val owes: Boolean,
)

@Composable
private fun RefundEstimatorCard() {
    var income by remember { mutableStateOf("") }
    var withheld by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf("") }
    var filingStatus by remember { mutableStateOf(FilingStatus.Single) }
    var results by remember { mutableStateOf<RefundResult?>(null) }

    FCCard {
        FCCardHeader {
            FCCardTitle("Federal Tax Refund Estimator")
            FCCardDescription("Estimate your potential federal tax refund")
        }
        FCCardContent {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NumericField("Total Annual Income", income, { income = it }, placeholder = "$65,000")
                    FilingStatusPicker(filingStatus) { filingStatus = it }
                    NumericField("Federal Tax Withheld", withheld, { withheld = it }, placeholder = "$8,500")
                    NumericField("Federal Tax Credits", credits, { credits = it }, placeholder = "$2,000")
                    FCButton(
                        onClick = {
                            val incomeNum = sanitizeNumber(income)
                            val withheldNum = sanitizeNumber(withheld)
                            val creditsNum = sanitizeNumber(credits)
                            val standardDeduction = standardDeductionFor(filingStatus)
                            val taxableIncome = max(0.0, incomeNum - standardDeduction)
                            val tax = bracketTax(taxableIncome, filingStatus)
                            val totalTaxLiability = max(0.0, tax - creditsNum)
                            val refund = withheldNum - totalTaxLiability
                            results = RefundResult(
                                taxLiability = totalTaxLiability,
                                withheld = withheldNum,
                                credits = creditsNum,
                                refund = refund,
                                owes = refund < 0,
                            )
                        },
                        size = FCButtonSize.Lg,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Calculate Federal Refund") }
                }

                val r = results
                if (r != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Federal Refund Results",
                            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Theme.Radius.lg))
                                .background(TaxTint.greenBG)
                                .border(2.dp, TaxTint.greenBorder, RoundedCornerShape(Theme.Radius.lg))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.AttachMoney,
                                contentDescription = null,
                                tint = Theme.colors.positive,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                (if (r.owes) "-" else "") + dollarsInt(kotlin.math.abs(r.refund)),
                                style = Theme.figure(30, FontWeight.Bold),
                                color = if (r.owes) Theme.colors.negative else Theme.colors.positive,
                            )
                            Text(
                                if (r.owes) "Estimated Federal Amount Owed" else "Estimated Federal Refund",
                                style = Theme.sans(Theme.FontSize.sm),
                                color = Theme.colors.positive,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MutedPanel(8) {
                                TaxRow("Fed. Tax Liability", dollarsInt(r.taxLiability), small = true)
                            }
                            MutedPanel(8) {
                                TaxRow("Fed. Tax Withheld", dollarsInt(r.withheld), small = true)
                            }
                            MutedPanel(8) {
                                TaxRow("Fed. Tax Credits", dollarsInt(r.credits), small = true)
                            }
                        }
                    }
                } else {
                    EmptyResult(
                        Icons.Filled.AttachMoney,
                        "Enter your info to estimate your federal refund.",
                    )
                }
            }
        }
    }
}

// MARK: - Deduction Finder

private data class DeductionItem(
    val name: String,
    val amount: String,
    val common: Boolean,
    val hypotheticalValue: Double,
    /**
     * Who actually qualifies, behind an info dot. Only on the ones where the
     * rule is not common knowledge; the obvious lines carry null.
     */
    val info: String? = null,
)

private data class DeductionCategory(val category: String, val items: List<DeductionItem>)

private val deductionItemsData: List<DeductionCategory> = listOf(
    DeductionCategory(
        "Home & Property",
        listOf(
            DeductionItem(
                "Mortgage Interest", "Up to $750k loan", true, 8000.0,
                "Interest on a loan secured by a home you own and live in (or a second home), only if " +
                    "you itemize. Interest counts on up to $750,000 of loan taken out after 2017, or " +
                    "$1 million on older loans.",
            ),
            DeductionItem(
                "Property Taxes", "Within the $40k SALT cap", true, 5000.0,
                "State and local property tax, counted inside the same SALT cap as income or sales " +
                    "tax. Only if you itemize, and only tax you actually paid during the year.",
            ),
            DeductionItem(
                "Home Office", "$5/sq ft (max $1.5k)", false, 1500.0,
                "Only for self-employed work, never for an employee working from home. The space has " +
                    "to be used regularly and only for business, and the simple method allows $5 per " +
                    "square foot up to 300 square feet.",
            ),
            DeductionItem(
                "Energy Credits", "Up to $3.2k", false, 1000.0,
                "A credit, not a deduction: it comes straight off the tax you owe instead of off your " +
                    "income. Covers qualifying improvements like heat pumps, insulation, and windows, " +
                    "with per-item caps.",
            ),
        )
    ),
    DeductionCategory(
        "Medical & Health",
        listOf(
            DeductionItem(
                "Medical Expenses", "> 7.5% of AGI", false, 3000.0,
                "Only the part above 7.5% of your income counts, and only if you itemize. At $60,000 " +
                    "of income, the first $4,500 of medical bills does nothing; a $6,000 year would " +
                    "deduct $1,500.",
            ),
            DeductionItem(
                "HSA Contributions", "Up to $4.3k/$8.55k", true, 4300.0,
                "Requires a high-deductible health plan. Deductible even if you take the standard " +
                    "deduction, but money your employer already withheld pre-tax does not count again.",
            ),
        )
    ),
    DeductionCategory(
        "Education",
        listOf(
            DeductionItem(
                "Student Loan Interest", "Up to $2.5k", true, 2500.0,
                "Up to $2,500 of interest on a qualified student loan, without itemizing. Phases out " +
                    "at higher incomes, and married filing separately cannot claim it at all.",
            ),
            DeductionItem(
                "Educator Expenses", "Up to $300", false, 300.0,
                "For K-12 teachers, aides, counsellors and principals who work at least 900 hours a " +
                    "year. Classroom supplies you paid for yourself, up to $300, without itemizing.",
            ),
        )
    ),
    DeductionCategory(
        "Charitable & Other",
        listOf(
            DeductionItem(
                "Charitable Donations", "Up to 60% AGI", true, 2000.0,
                "Only if you itemize, and only to qualifying organisations, not individuals or " +
                    "political groups. Cash gifts are capped at 60% of income, and anything $250 or " +
                    "more needs a receipt from the charity.",
            ),
            DeductionItem(
                "State & Local Taxes (SALT)", "Up to $40k (2025)", true, 10000.0,
                "State and local income (or sales) tax plus property tax, added together under one " +
                    "cap. For 2025 the cap is $40,000, phasing down at higher incomes and never below " +
                    "$10,000.",
            ),
            DeductionItem(
                "Business Expenses", "Self-employed", false, 5000.0,
                "Self-employment only. The cost has to be ordinary and necessary for the work, and " +
                    "anything used for both work and personal life counts only for the business share.",
            ),
        )
    ),
    DeductionCategory(
        "Retirement",
        listOf(
            DeductionItem(
                "Traditional IRA", "Up to $7k/$8k", true, 7000.0,
                "Deductible without itemizing, but if you or your spouse have a retirement plan at " +
                    "work, the deduction phases out above certain incomes. Roth contributions are " +
                    "never deductible.",
            ),
            DeductionItem(
                "Self-Employed Retirement", "Varies", false, 10000.0,
                "SEP-IRA, SIMPLE, or solo 401(k) contributions for self-employed income. The limit " +
                    "depends on your net business profit, so it varies year to year.",
            ),
        )
    ),
)

private data class DeductionSummary(
    val totalItemized: Double,
    val standardDeduction: Double,
    val shouldItemize: Boolean,
    val savings: Double,
    val selectedCount: Int,
)

@Composable
private fun DeductionFinderCard() {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var openInfo by remember { mutableStateOf<String?>(null) }

    fun key(category: String, name: String) = "$category-$name"

    val summary: DeductionSummary? = if (selected.isEmpty()) null else {
        var total = 0.0
        var count = 0
        deductionItemsData.forEach { category ->
            category.items.forEach { item ->
                if (selected.contains(key(category.category, item.name))) {
                    count += 1
                    total += item.hypotheticalValue
                }
            }
        }
        val standardDeduction = STANDARD_DEDUCTION_2025.getValue(FilingStatus.Single)
        val shouldItemize = total > standardDeduction
        DeductionSummary(
            totalItemized = total,
            standardDeduction = standardDeduction,
            shouldItemize = shouldItemize,
            savings = if (shouldItemize) total - standardDeduction else 0.0,
            selectedCount = count,
        )
    }

    FCCard {
        FCCardHeader {
            FCCardTitle("Federal Deduction Finder")
            FCCardDescription("Discover potential federal tax deductions")
        }
        FCCardContent {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                deductionItemsData.forEach { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Theme.colors.primary)
                            )
                            Text(
                                category.category,
                                style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                                color = Theme.colors.foreground,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            category.items.forEach { item ->
                                val k = key(category.category, item.name)
                                DeductionCell(
                                    item = item,
                                    isOn = selected.contains(k),
                                    infoOpen = openInfo == k,
                                    onToggle = {
                                        selected = if (selected.contains(k)) selected - k else selected + k
                                    },
                                    onToggleInfo = { openInfo = if (openInfo == k) null else k },
                                )
                            }
                        }
                    }
                }

                if (summary != null) DeductionSummaryCard(summary)
            }
        }
    }
}

@Composable
private fun DeductionCell(
    item: DeductionItem,
    isOn: Boolean,
    infoOpen: Boolean,
    onToggle: () -> Unit,
    onToggleInfo: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, if (isOn) Theme.colors.primary else Theme.colors.border, shape)
            .fcPressable(onToggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                item.name,
                style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
                color = Theme.colors.foreground,
            )
            // Who actually qualifies, one open at a time. Ticking a box you
            // don't qualify for is the easy mistake here.
            if (item.info != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(16.dp)
                        .fcPressable(onToggleInfo),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Who qualifies for ${item.name}",
                        tint = if (infoOpen) Theme.colors.primary
                        else Theme.colors.mutedForeground.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Box(Modifier.weight(1f))
            if (item.common) FCBadge("Common", variant = FCBadgeVariant.Secondary)
        }
        Text(item.amount, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
        if (item.info != null && infoOpen) {
            Text(
                item.info,
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .background(Theme.colors.muted.copy(alpha = 0.6f))
                    .padding(10.dp),
            )
        }
        HorizontalDivider(color = Theme.colors.border)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isOn) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (isOn) Theme.colors.primary else Theme.colors.mutedForeground,
                modifier = Modifier.size(18.dp),
            )
            Text("I believe I qualify", style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.foreground)
        }
    }
}

@Composable
private fun DeductionSummaryCard(s: DeductionSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.lg))
            .background(TaxTint.blueBG)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Potential Federal Deduction Summary",
            style = Theme.sans(Theme.FontSize.lg, FontWeight.SemiBold),
            color = Theme.colors.foreground,
        )
        TaxRow("Selected Potential Deductions", "${s.selectedCount} items")
        TaxRow("Estimated Itemized Total*", dollarsInt(s.totalItemized))
        TaxRow("Standard Deduction (Single)", dollarsInt(s.standardDeduction))
        HorizontalDivider(color = Theme.colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.Radius.lg))
                .background(Theme.colors.background)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recommendation",
                style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                color = Theme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (s.shouldItemize) "Likely Better to Itemize (Federal)"
                else "Likely Better to Take Standard (Federal)",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                color = Theme.colors.primary,
            )
        }

        if (s.shouldItemize) {
            Text(
                "Itemizing could potentially increase your federal deduction by ${dollarsInt(s.savings)}!*",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.positive,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.lg))
                    .background(TaxTint.greenBG)
                    .padding(12.dp),
            )
        }

        Text(
            "*Based on hypothetical values. Actual amounts vary. State deduction rules differ.",
            style = Theme.sans(Theme.FontSize.xs),
            color = Theme.colors.mutedForeground,
        )
    }
}

// MARK: - Quarterly Calculator

private data class QuarterlyResult(
    val netIncome: Double,
    val selfEmploymentTax: Double,
    val incomeTax: Double,
    val totalTax: Double,
    val quarterlyPayment: Double,
)

@Composable
private fun QuarterlyCalculatorCard() {
    var netIncome by remember { mutableStateOf("") }
    var filingStatus by remember { mutableStateOf(FilingStatus.Single) }
    var results by remember { mutableStateOf<QuarterlyResult?>(null) }

    FCCard {
        FCCardHeader {
            FCCardTitle("Quarterly Federal Tax Payment Calculator")
            FCCardDescription("Estimate federal tax payments for self-employed individuals")
        }
        FCCardContent {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NumericField(
                        "Expected Annual Net Income (After Expenses)",
                        netIncome, { netIncome = it },
                        placeholder = "$80,000",
                    )
                    FilingStatusPicker(filingStatus) { filingStatus = it }
                    FCButton(
                        onClick = {
                            val net = sanitizeNumber(netIncome)
                            val se = computeSelfEmploymentTax(
                                mapOf(IncomeOwner.Taxpayer to net, IncomeOwner.Spouse to 0.0),
                                mapOf(IncomeOwner.Taxpayer to 0.0, IncomeOwner.Spouse to 0.0),
                            )
                            val adjustedIncome = net - se.deduction
                            val standardDeduction = standardDeductionFor(filingStatus)
                            val taxableIncome = max(0.0, adjustedIncome - standardDeduction)
                            val incomeTax = bracketTax(taxableIncome, filingStatus)
                            val totalFederalTax = incomeTax + se.seTax
                            results = QuarterlyResult(
                                netIncome = net,
                                selfEmploymentTax = se.seTax,
                                incomeTax = incomeTax,
                                totalTax = totalFederalTax,
                                quarterlyPayment = totalFederalTax / 4,
                            )
                        },
                        size = FCButtonSize.Lg,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Calculate Federal Quarterly Payments") }
                }

                val r = results
                if (r != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Federal Payment Results",
                            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        MutedPanel(16) {
                            TaxRow("Net Income (Est.)", dollarsLoc(r.netIncome), small = true)
                            TaxRow("Fed. Income Tax (Est.)", dollarsInt(r.incomeTax), small = true)
                            TaxRow("SE Tax (Est.)", dollarsInt(r.selfEmploymentTax), small = true)
                            HorizontalDivider(color = Theme.colors.border)
                            TaxRow("Total Annual Federal Tax (Est.)", dollarsInt(r.totalTax))
                        }

                        Text(
                            "Estimated Federal Payments",
                            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuarterRow("Q1", "Due: April 15", r.quarterlyPayment)
                            QuarterRow("Q2", "Due: June 16", r.quarterlyPayment)
                            QuarterRow("Q3", "Due: September 15", r.quarterlyPayment)
                            QuarterRow("Q4", "Due: January 15, 2026", r.quarterlyPayment)
                        }

                        Text(
                            "State quarterly payments may also be required.",
                            style = Theme.sans(Theme.FontSize.xs),
                            color = Theme.colors.mutedForeground,
                        )
                    }
                } else {
                    EmptyResult(
                        Icons.Filled.CalendarMonth,
                        "Enter net income to estimate federal payments.",
                    )
                }
            }
        }
    }
}

@Composable
private fun QuarterRow(label: String, due: String, amount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Theme.colors.border, RoundedCornerShape(Theme.Radius.lg))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = Theme.sans(Theme.FontSize.base, FontWeight.Medium),
                color = Theme.colors.foreground,
            )
            Text(due, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
        }
        Text(
            dollarsInt(amount),
            style = Theme.figure(Theme.FontSize.base, FontWeight.SemiBold),
            color = Theme.colors.foreground,
        )
    }
}

// MARK: - Withholding Calculator

private data class WithholdingResult(
    val annualTax: Double,
    val perPaycheck: Double,
    val monthlyWithholding: Double,
    val payPeriods: Double,
)

@Composable
private fun WithholdingCalculatorCard() {
    var income by remember { mutableStateOf("") }
    var payPeriods by remember { mutableStateOf("26") }
    var step3Credits by remember { mutableStateOf("0") }
    var filingStatus by remember { mutableStateOf(FilingStatus.Single) }
    var results by remember { mutableStateOf<WithholdingResult?>(null) }

    FCCard {
        FCCardHeader {
            FCCardTitle("Federal Withholding Calculator")
            FCCardDescription("Estimate federal tax withholdings from your paycheck (simplified)")
        }
        FCCardContent {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NumericField("Annual Salary", income, { income = it }, placeholder = "$75,000")
                    FilingStatusPicker(filingStatus) { filingStatus = it }
                    NumericField(
                        "Pay Periods per Year", payPeriods, { payPeriods = it },
                        placeholder = "26 (bi-weekly)",
                        helper = "Weekly: 52, Bi-weekly: 26, Monthly: 12",
                    )
                    NumericField(
                        "W-4 Step 3 credits (dependents)", step3Credits, { step3Credits = it },
                        placeholder = "$0",
                        helper = "The dollar total from Step 3 of your W-4, like $2,000 per child you claim there.",
                    )
                    FCButton(
                        onClick = {
                            // The current W-4 (2020 and later) has no allowances: Step 3
                            // is a dollar amount of dependent and other credits subtracted
                            // from the annual tab.
                            val incomeNum = sanitizeNumber(income)
                            val periods = sanitizeNumber(payPeriods).takeIf { it != 0.0 } ?: 26.0
                            val creditsNum = sanitizeNumber(step3Credits)
                            val standardDeduction = standardDeductionFor(filingStatus)
                            val taxableIncome = max(0.0, incomeNum - standardDeduction)
                            val annualTax = bracketTax(taxableIncome, filingStatus)
                            val adjustedAnnualTax = max(0.0, annualTax - creditsNum)
                            results = WithholdingResult(
                                annualTax = adjustedAnnualTax,
                                perPaycheck = adjustedAnnualTax / periods,
                                monthlyWithholding = adjustedAnnualTax / 12,
                                payPeriods = periods,
                            )
                        },
                        size = FCButtonSize.Lg,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Calculate Federal Withholding") }
                }

                val r = results
                if (r != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Federal Withholding Results",
                            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Theme.Radius.lg))
                                .background(TaxTint.blueBG)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.PieChart,
                                contentDescription = null,
                                tint = TaxTint.blueStrong,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                dollarsInt(r.perPaycheck),
                                style = Theme.figure(30, FontWeight.Bold),
                                color = TaxTint.blueStrong,
                            )
                            Text(
                                "Federal Tax Per Paycheck (Est.)",
                                style = Theme.sans(Theme.FontSize.sm),
                                color = TaxTint.blueStrong,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MutedPanel { TaxRow("Est. Annual Federal Tax", dollarsInt(r.annualTax)) }
                            MutedPanel {
                                TaxRow("Est. Monthly Federal Withholding", dollarsInt(r.monthlyWithholding))
                            }
                            MutedPanel { TaxRow("Pay Periods", CalcFormat.fixed(r.payPeriods, 0)) }
                        }

                        Text(
                            "Heads up: this is a simplified estimate. It uses federal brackets and your " +
                                "standard deduction only, and leaves out state withholding, second jobs, " +
                                "and other income.",
                            style = Theme.sans(Theme.FontSize.sm),
                            color = Theme.colors.caution,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Theme.Radius.lg))
                                .background(TaxTint.yellowBG)
                                .border(1.dp, TaxTint.yellowBorder, RoundedCornerShape(Theme.Radius.lg))
                                .padding(16.dp),
                        )
                    }
                } else {
                    EmptyResult(Icons.Filled.PieChart, "Enter info to estimate federal withholding.")
                }

                // About W-4 panel (always shown, as in the web bottom info box).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Theme.Radius.lg))
                        .background(Theme.colors.muted)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Theme.colors.foreground,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "About W-4 Withholding",
                            style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                    }
                    Text(
                        "Your W-4 tells your employer how much federal tax to hold back from each " +
                            "paycheck. Withhold too little and you owe in April; too much and you have " +
                            "lent the money interest-free all year. Step 3 of the form is where " +
                            "dependent credits go, which is what lowers the amount held back.",
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }
    }
}
