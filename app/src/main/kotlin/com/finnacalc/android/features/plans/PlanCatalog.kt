//
// PlanCatalog.kt
//
// Port of iOS Features/Plans/PlanCatalog.swift — the three paid tiers and
// everything the Plans screen says about them. Single source of truth for copy
// and savings math: every savings percentage on screen is derived from these
// numbers (house rule — no figure appears that isn't). Prices shown prefer
// Play's own localized formattedPrice once billing loads; the USD figures here
// are the fallback and must match the base plans configured in Play Console.
//
// Naming: "Plan*" / "Entitlement*" / "Billing*" — the Subscription* names are
// taken by the bill-reminder feature (SubscriptionDetector & co).
//

package com.finnacalc.android.features.plans

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** A paid tier. Raw values are the backend's wire format. */
enum class PlanTier(val raw: String) { Plus("plus"), Trader("trader"), Pro("pro") }

enum class PlanBillingInterval(val raw: String, val label: String) {
    Monthly("monthly", "Monthly"),
    Annual("annual", "Annual"),
}

data class PlanBenefit(val icon: ImageVector, val text: String)

data class Plan(
    val tier: PlanTier,
    val name: String,
    val tagline: String,
    val icon: ImageVector,
    /** Display prices in USD; must match the Play Console base plans. */
    val monthly: Double,
    val annual: Double,
    val benefits: List<PlanBenefit>,
    /** The visually elevated recommendation (Pro only). */
    val recommended: Boolean,
) {
    /** What a year costs on the annual price vs. paying monthly, e.g. 33. */
    val annualSavingsPercent: Int
        get() {
            val yearAtMonthly = monthly * 12
            if (yearAtMonthly <= 0) return 0
            return (((yearAtMonthly - annual) / yearAtMonthly) * 100).roundToInt()
        }

    fun price(interval: PlanBillingInterval): Double =
        if (interval == PlanBillingInterval.Monthly) monthly else annual
}

object PlanCatalog {
    /**
     * Pro first — the recommended plan leads the page. Tier raw values
     * (plus/trader/pro) are wire and product-ID format and must not change;
     * only the display names did.
     */
    val all: List<Plan> = listOf(
        Plan(
            tier = PlanTier.Pro,
            name = "FinnaCalc Pro",
            tagline = "Both plans, one price",
            icon = Icons.Filled.AutoAwesome,
            monthly = 16.99,
            annual = 159.99,
            benefits = listOf(
                PlanBenefit(Icons.Filled.PieChart, "Everything in Budgeting Plus"),
                PlanBenefit(Icons.AutoMirrored.Outlined.TrendingUp, "Everything in Investing Plus"),
                PlanBenefit(Icons.Filled.Block, "No ads"),
                PlanBenefit(Icons.Filled.AutoAwesome, "First in line for every new feature"),
            ),
            recommended = true,
        ),
        Plan(
            tier = PlanTier.Plus,
            name = "Budgeting Plus",
            tagline = "Your budget keeps itself up to date",
            icon = Icons.Filled.PieChart,
            monthly = 9.99,
            annual = 94.99,
            benefits = listOf(
                PlanBenefit(Icons.Filled.AccountBalance, "Bank connections that sync your budget on their own"),
                PlanBenefit(Icons.Filled.AutoAwesome, "Advanced budget analysis with follow-up chat"),
                PlanBenefit(Icons.Filled.TrackChanges, "Extra goals with alerts and widgets"),
                PlanBenefit(Icons.Filled.Block, "Ad-free budgeting"),
                PlanBenefit(Icons.AutoMirrored.Filled.MenuBook, "Early and exclusive lessons in Education"),
            ),
            recommended = false,
        ),
        Plan(
            tier = PlanTier.Trader,
            name = "Investing Plus",
            tagline = "See exactly what you own",
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
            monthly = 9.99,
            annual = 94.99,
            benefits = listOf(
                PlanBenefit(
                    Icons.Filled.InsertChart,
                    "Portfolio Analysis: your mix, sectors, dividends, and a tax view",
                ),
                PlanBenefit(Icons.Filled.Description, "Ten years of company financials you can actually read"),
                PlanBenefit(Icons.Filled.Group, "Trade Tracker alerts for investors and insiders"),
                PlanBenefit(Icons.Filled.Block, "Ad-free investing"),
                PlanBenefit(Icons.AutoMirrored.Filled.MenuBook, "Early and exclusive lessons in Education"),
            ),
            recommended = false,
        ),
    )

    fun plan(tier: PlanTier): Plan? = all.firstOrNull { it.tier == tier }

    /** Largest annual saving across tiers — the toggle's "Save up to X%". */
    val maxAnnualSavingsPercent: Int get() = all.maxOfOrNull { it.annualSavingsPercent } ?: 0

    /** "$14.99" — prices are catalog constants, USD only. */
    fun priceString(amount: Double): String =
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(amount)
}
