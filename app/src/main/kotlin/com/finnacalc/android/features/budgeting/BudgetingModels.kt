//
// BudgetingModels.kt
//
// Port of iOS Features/Budgeting/BudgetingModels.swift — domain models for the
// Budget Planner, originally from the web app/budgeting/page.tsx.
//
// Deviation from iOS: the Swift files carry custom lenient decoders migrating
// blobs written by older iOS builds (legacy billingDay, missing month fields).
// This is a fresh Android install with no legacy blobs, so models decode with
// plain defaults; JsonPrefs already swallows whole-blob failures.
//

package com.finnacalc.android.features.budgeting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BudgetType(val raw: String) {
    @SerialName("personal") Personal("personal"),
    @SerialName("business") Business("business");

    val title: String get() = if (this == Personal) "Personal" else "Business"
}

@Serializable
enum class ItemType(val raw: String) {
    @SerialName("income") Income("income"),
    @SerialName("expense") Expense("expense");

    val title: String get() = if (this == Income) "Income" else "Expense"
}

@Serializable
enum class Frequency(val raw: String) {
    // biweekly, semimonthly and quarterly were added with subscription
    // cadences — without them an every-2-weeks or quarterly charge had no
    // honest frequency.
    @SerialName("daily") Daily("daily"),
    @SerialName("weekly") Weekly("weekly"),
    @SerialName("biweekly") Biweekly("biweekly"),
    @SerialName("semimonthly") Semimonthly("semimonthly"),
    @SerialName("monthly") Monthly("monthly"),
    @SerialName("quarterly") Quarterly("quarterly"),
    @SerialName("yearly") Yearly("yearly");

    val title: String
        get() = when (this) {
            Biweekly -> "Every 2 weeks"
            Semimonthly -> "Twice a month"
            else -> raw.replaceFirstChar { it.uppercase() }
        }

    /**
     * convertToMonthly multipliers from the web (daily 30, weekly 4.33,
     * monthly 1, yearly 1/12), with biweekly as half the weekly rate and
     * quarterly as a third of a month. biweekly (26/yr = 2.165/mo) and
     * semimonthly (exactly 2/mo) are genuinely different rates; costing the
     * latter as biweekly overstates it by 8%.
     */
    val monthlyMultiplier: Double
        get() = when (this) {
            Daily -> 30.0
            Weekly -> 4.33
            Biweekly -> 2.165
            Semimonthly -> 2.0
            Monthly -> 1.0
            Quarterly -> 1.0 / 3.0
            Yearly -> 1.0 / 12.0
        }
}

@Serializable
data class BudgetItem(
    val id: String,
    val category: String,
    val subcategory: String,
    val amount: Double,      // always positive
    val frequency: Frequency,
    val type: ItemType,
    val isFixed: Boolean,
    val budgetType: BudgetType,
    val importDate: String? = null,
    /**
     * Which month this line belongs to, as "yyyy-MM" — budgets are planned
     * per month. "" means "not yet stamped" and is migrated to the undated
     * slot on add.
     */
    val month: String = "",
    /**
     * When this charge recurs, if the user marked it a subscription. Non-null
     * is the ONLY marker for "this is a subscription" — one field, so the
     * flag and the timing can't contradict each other.
     */
    val chargeSchedule: ChargeSchedule? = null,
) {
    val isSubscription: Boolean get() = chargeSchedule != null

    /** convertToMonthly(amount, frequency) */
    val monthlyAmount: Double get() = amount * frequency.monthlyMultiplier
}

/**
 * What a goal is counting. Saving is the original: money put aside by hand.
 * Spending and income are read from a linked bank — a ceiling on what goes
 * out, a target for what comes in — so they only exist for connected users.
 */
@Serializable
enum class GoalKind(val raw: String) {
    @SerialName("saving") Saving("saving"),
    @SerialName("spending") Spending("spending"),
    @SerialName("income") Income("income");

    val title: String
        get() = when (this) {
            Saving -> "Saving"
            Spending -> "Spending"
            Income -> "Income"
        }

    /** What the amount field means: a target to reach, or a line not to cross. */
    val isLimit: Boolean get() = this == Spending

    /**
     * The past participle for a progress caption: "40% spent", "80% earned".
     * A spending goal that says "saved" reads like praise for overspending.
     */
    val progressVerb: String
        get() = when (this) {
            Saving -> "saved"
            Spending -> "spent"
            Income -> "earned"
        }
}

@Serializable
data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val targetDate: String,   // ISO yyyy-MM-dd
    val monthlyContribution: Double,
    /** Which budget (Personal/Business) this goal belongs to. */
    val budgetType: BudgetType = BudgetType.Personal,
    /** User-chosen icon override. null = follow the name-derived suggestion. */
    val emoji: String? = null,
    /** Progress ring colour as RRGGBB hex. null = the theme's positive green. */
    val ringColorHex: String? = null,
    val kind: GoalKind = GoalKind.Saving,
    /**
     * Which accounts count. Spending/income: empty means every CONNECTED
     * account. Saving: empty is the original behaviour (hand-counted);
     * naming accounts turns the goal into a balance watcher.
     */
    val accountIDs: List<String> = emptyList(),
    /** Spending/income: the stretch of time measured. null = everything on record. */
    val period: BudgetPeriod? = null,
    /** Spending and income: only this category counts. null = every one. */
    val category: String? = null,
    /**
     * Spending/income counted by hand rather than read from a bank. The
     * figure then lives in `currentAmount`, exactly as a saving goal's does.
     */
    val manualOnly: Boolean = false,
    /** Progress alerts the user asked for, as percents (50/75/90/100). */
    val alerts: List<Int> = emptyList(),
    /** Which of those have already fired, so crossing 50% notifies once. */
    val alertsFired: List<Int> = emptyList(),
    /** Set when the user answered the "remove this goal?" prompt with Keep. */
    val keptAfterReached: Boolean = false,
)

@Serializable
data class BudgetHistoryEntry(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val budgetItems: List<BudgetItem>,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val monthlyNet: Double,
    val budgetType: BudgetType,
)

/** A category total used for charts and the budget-advisor snapshot. */
data class CategorySlice(
    val name: String,
    val value: Double,
)

/** Category options per the web `categories` lists. */
object BudgetCategories {
    fun income(type: BudgetType): List<String> = when (type) {
        BudgetType.Personal -> listOf("Salary", "Freelance", "Investments", "Gift", "Other")
        BudgetType.Business -> listOf(
            "Sales Revenue", "Service Revenue", "Subscriptions", "Interest Earned",
            "Other Fees", "Total Revenue", "Other Revenue",
        )
    }

    fun expense(type: BudgetType): List<String> = when (type) {
        BudgetType.Personal -> listOf(
            "Housing", "Utilities", "Food", "Transportation", "Entertainment",
            "Healthcare", "Insurance", "Debt Payments", "Savings", "Retirement", "Other",
        )
        BudgetType.Business -> listOf(
            "Cost of Goods Sold (COGS)", "Salaries/Wages", "Marketing & Advertising",
            "Rent/Lease", "Utilities", "Software & Subscriptions", "Supplies",
            "Repairs & Maintenance", "Insurance", "Professional Fees", "Taxes",
            "Travel", "Depreciation", "Loan Payments", "Other Operating Costs",
        )
    }
}
