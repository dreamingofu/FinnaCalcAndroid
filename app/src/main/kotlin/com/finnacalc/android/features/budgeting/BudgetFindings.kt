//
// BudgetFindings.kt
//
// Port of iOS Features/Budgeting/BudgetFindings.swift — the local,
// deterministic findings behind Budget Analysis, computed from whatever
// budget My Budget has open. Nothing here calls a model.
//
// Guideline figures (15% savings, 3 months of expenses, the 50/30/20 split,
// 36% of income on debt payments) are widely published guidelines, named as
// such in the copy. The findings compare against them and never tell the user
// what to do with the difference.
//
// The emergency-fund finding only counts goals the user LABELED as one. We
// cannot know which savings are the cushion; when nothing is labeled the
// finding says so and explains how to label, rather than guessing.
//

package com.finnacalc.android.features.budgeting

import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.features.calculators.CalcFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

sealed class FindingStatus(val label: String) {
    /** pill label, e.g. "healthy" */
    class Good(label: String) : FindingStatus(label)
    class Warn(label: String) : FindingStatus(label)
    class Bad(label: String) : FindingStatus(label)

    val rank: Int
        get() = when (this) {
            is Good -> 0
            is Warn -> 1
            is Bad -> 2
        }
}

enum class FindingAction(val label: String) {
    EditBudget("Open My Budget"),
    OpenGoals("Open Goals"),
}

data class Finding(
    val id: String,
    val title: String,
    val detail: String,
    val status: FindingStatus,
    /** Deterministic copy with real figures. No AI. */
    val fix: String,
    val action: FindingAction?,
    /**
     * Score weight. Informational findings carry 0 and leave the score to the
     * four load-bearing ones (surplus 30 · savings rate 25 · emergency 25 ·
     * goals 20).
     */
    val weight: Double,
)

object BudgetFindings {

    // MARK: Shared sums

    fun measuredGoalTotal(store: BudgetStore): Double {
        val ledger = BankLedgerStore.shared
        return store.currentGoals
            .filter { it.kind == GoalKind.Saving }
            .sumOf { GoalProgress.measure(it, ledger).first }
    }

    /**
     * Months of expenses covered by goals the user labeled as the cushion.
     * null when nothing is labeled, which is different from zero.
     */
    fun emergencyMonths(store: BudgetStore): Double? {
        val expenses = store.monthlyExpenses
        if (expenses <= 0) return null
        val ledger = BankLedgerStore.shared
        val labeled = store.currentGoals.filter { isEmergencyName(it.name) }
        if (labeled.isEmpty()) return null
        val total = labeled.sumOf { GoalProgress.measure(it, ledger).first }
        return total / expenses
    }

    /**
     * What the open budget CONTRIBUTES to an emergency fund each month: lines
     * whose own name says so. A budget line is a flow, not a balance, so this
     * can never say how much is saved.
     */
    fun emergencyContribution(store: BudgetStore): Double =
        store.currentItems
            .filter { isEmergencyName(it.subcategory) || isEmergencyName(it.category) }
            .sumOf { it.monthlyAmount }

    /**
     * Commonly published share-of-income guidelines per personal category.
     * Business categories deliberately have none: no household rule fits a
     * business.
     */
    val categoryGuidelines: Map<String, Double> = mapOf(
        "Housing" to 30.0,
        "Food" to 15.0,
        "Transportation" to 15.0,
        "Utilities" to 10.0,
        "Entertainment" to 10.0,
        "Insurance" to 12.0,
        "Healthcare" to 10.0,
    )

    fun isEmergencyName(name: String): Boolean {
        val lowered = name.lowercase()
        return lowered.contains("emergency") || lowered.contains("rainy") ||
            lowered.contains("safety net") || lowered.contains("cushion")
    }

    // MARK: The list

    fun compute(store: BudgetStore): List<Finding> {
        val out = mutableListOf<Finding>()
        val income = store.monthlyIncome
        val expenses = store.monthlyExpenses
        val net = store.monthlyNet
        if (income <= 0 && expenses <= 0) return emptyList()
        val savingsRate = if (income > 0) net / income * 100 else 0.0
        val categories = store.expenseByCategory.sortedByDescending { it.value }

        // Monthly surplus (weight 30)
        run {
            val status = when {
                net > 0 -> FindingStatus.Good("healthy")
                net == 0.0 -> FindingStatus.Warn("break-even")
                else -> FindingStatus.Bad("overspent")
            }
            val topCat = categories.firstOrNull()
            val fix = when {
                net > 0 ->
                    "You keep ${Paper.compactMoney(net)}/mo unassigned. Zero-based budgeting, which many " +
                        "budgeting guides recommend, gives every dollar a job: a goal, a category cap, or " +
                        "an extra debt payment."
                net == 0.0 ->
                    "Income and spending land exactly even, so nothing is left to put toward a goal."
                else ->
                    "You spend ${Paper.compactMoney(abs(net))}/mo more than you earn. Start with your top " +
                        "category, ${topCat?.name ?: "your largest expense"} at " +
                        "${Paper.compactMoney(topCat?.value ?: 0.0)}/mo."
            }
            out.add(
                Finding(
                    "surplus", "Monthly surplus",
                    "${if (net >= 0) "+" else "−"}$${CalcFormat.int(abs(net))} per month",
                    status, fix, FindingAction.EditBudget, 30.0,
                )
            )
        }

        // Biggest expense, measured against the published share-of-income
        // guideline for its category when one exists.
        val top = categories.firstOrNull()
        if (top != null && expenses > 0) {
            val share = top.value / expenses * 100
            val concentrated = share >= 50 && categories.size > 1
            val shareOfIncome = if (income > 0) top.value / income * 100 else null

            var fix: String
            var status: FindingStatus
            val guideline = categoryGuidelines[top.name]
            if (guideline != null && shareOfIncome != null && income > 0) {
                val guidelineAmount = income * guideline / 100
                if (shareOfIncome > guideline) {
                    fix = "${top.name} takes ${CalcFormat.fixed(shareOfIncome, 0)}% of your income. Budgeting " +
                        "guides commonly keep it near ${CalcFormat.fixed(guideline, 0)}%, which is about " +
                        "${Paper.compactMoney(guidelineAmount)}/mo at your income; yours runs " +
                        "${Paper.compactMoney(top.value - guidelineAmount)}/mo above that mark."
                    status = FindingStatus.Warn("${CalcFormat.fixed(shareOfIncome, 0)}% of income")
                } else {
                    fix = "${top.name} takes ${CalcFormat.fixed(shareOfIncome, 0)}% of your income, inside the " +
                        "~${CalcFormat.fixed(guideline, 0)}% budgeting guides commonly suggest."
                    status = FindingStatus.Good("within guideline")
                }
            } else {
                fix = "Your largest line, at ${CalcFormat.fixed(share, 0)}% of spending" +
                    (shareOfIncome?.let { " and ${CalcFormat.fixed(it, 0)}% of income" } ?: "") + "."
                status = if (concentrated) FindingStatus.Warn("concentrated")
                else FindingStatus.Good("${CalcFormat.fixed(share, 0)}% of spending")
            }
            if (concentrated) {
                fix += " Half or more of all spending sits in this one category, so a change here moves the whole budget."
            }
            out.add(
                Finding(
                    "biggest", "Biggest expense: ${top.name}",
                    "${Paper.compactMoney(top.value)}/mo · ${CalcFormat.fixed(share, 0)}% of spending",
                    status, fix, FindingAction.EditBudget, 0.0,
                )
            )
        }

        // Biggest month-over-month move (informational; needs two months)
        categoryMoves(store).firstOrNull()?.let { delta ->
            val direction = if (delta.change > 0) "up" else "down"
            val pct = abs(delta.change)
            out.add(
                Finding(
                    "delta",
                    "${delta.category} $direction ${CalcFormat.fixed(pct, 0)}% ${delta.currentLabel}",
                    "${Paper.compactMoney(delta.current)} ${delta.currentLabel} · " +
                        "${Paper.compactMoney(delta.previous)} ${delta.previousLabel}",
                    if (delta.change > 0) FindingStatus.Warn("$direction ${CalcFormat.fixed(pct, 0)}%")
                    else FindingStatus.Good("$direction ${CalcFormat.fixed(pct, 0)}%"),
                    // States the two figures, the window each covers, and stops.
                    "${delta.category} is at ${Paper.compactMoney(delta.current)} ${delta.currentLabel}, against " +
                        "${Paper.compactMoney(delta.previous)} ${delta.previousLabel}. That is the biggest change " +
                        "of any category, and both figures cover the same length of time.",
                    FindingAction.EditBudget, 0.0,
                )
            )
        }

        // Needs / wants / savings vs the 50/30/20 guide (personal only)
        if (store.budgetType == BudgetType.Personal && income > 0 && expenses > 0) {
            val needsSet = setOf(
                "Housing", "Utilities", "Food", "Transportation",
                "Healthcare", "Insurance", "Debt Payments",
            )
            val savingsSet = setOf("Savings", "Retirement")
            var needs = 0.0
            var wants = 0.0
            var saved = 0.0
            for (c in categories) {
                when (c.name) {
                    in needsSet -> needs += c.value
                    in savingsSet -> saved += c.value
                    else -> wants += c.value
                }
            }
            // What's left over is saving too, even if it never got a category.
            saved += max(net, 0.0)
            val n = needs / income * 100
            val w = wants / income * 100
            val s = saved / income * 100
            val status = when {
                n <= 55 && s >= 15 -> FindingStatus.Good("balanced")
                n <= 65 -> FindingStatus.Warn("needs-heavy")
                else -> FindingStatus.Bad("needs-heavy")
            }
            out.add(
                Finding(
                    "split", "Needs, wants, and saving",
                    "${CalcFormat.fixed(n, 0)} / ${CalcFormat.fixed(w, 0)} / ${CalcFormat.fixed(s, 0)} of income, in %",
                    status,
                    "The 50/30/20 guide many budgeting sites use puts needs at 50% of income, wants at 30%, and " +
                        "saving at 20%. Yours splits ${CalcFormat.fixed(n, 0)} / ${CalcFormat.fixed(w, 0)} / " +
                        "${CalcFormat.fixed(s, 0)}, counting what's left over as saving. An estimate: only you " +
                        "know which lines are truly needs.",
                    FindingAction.EditBudget, 0.0,
                )
            )
        }

        // Debt payments vs income (informational; only when the line exists)
        val debtNames = setOf("Debt Payments", "Loan Payments")
        val debt = categories.filter { it.name in debtNames }.sumOf { it.value }
        if (debt > 0 && income > 0) {
            val share = debt / income * 100
            val status = when {
                share < 20 -> FindingStatus.Good("manageable")
                share <= 36 -> FindingStatus.Warn("${CalcFormat.fixed(share, 0)}% of income")
                else -> FindingStatus.Bad("heavy")
            }
            out.add(
                Finding(
                    "debt", "Debt payments",
                    "${Paper.compactMoney(debt)}/mo · ${CalcFormat.fixed(share, 0)}% of income",
                    status,
                    "A common guideline keeps all debt payments under 36% of income; yours take " +
                        "${CalcFormat.fixed(share, 0)}%. High-interest balances, usually credit cards, are the " +
                        "ones most guides suggest looking at first.",
                    FindingAction.EditBudget, 0.0,
                )
            )
        }

        // Savings rate (weight 25)
        if (income > 0) {
            val status = when {
                savingsRate >= 15 -> FindingStatus.Good("excellent")
                savingsRate >= 5 -> FindingStatus.Warn("thin")
                else -> FindingStatus.Bad("too low")
            }
            val neededForTarget = income * 0.15 - net
            val fix = if (savingsRate >= 15) {
                "Saving ${CalcFormat.fixed(savingsRate, 1)}% of income, at or above the 15% guideline. Keep it up."
            } else {
                "The common guideline is 15 to 20%. Freeing up ${Paper.compactMoney(max(neededForTarget, 0.0))}/mo " +
                    "of spending would reach 15%."
            }
            out.add(
                Finding(
                    "savingsRate", "Savings rate",
                    "${CalcFormat.fixed(savingsRate, 1)}% of income",
                    status, fix, FindingAction.EditBudget, 25.0,
                )
            )
        }

        // Emergency fund (weight 25) — labeled goals only, honest when nothing is.
        if (expenses > 0) {
            val low = expenses * 3
            val high = expenses * 6
            val months = emergencyMonths(store)
            val contribution = emergencyContribution(store)
            when {
                months != null -> {
                    val status = when {
                        months >= 3 -> FindingStatus.Good("covered")
                        months >= 1 -> FindingStatus.Warn("building")
                        else -> FindingStatus.Bad("exposed")
                    }
                    val fix = when {
                        months >= 3 ->
                            "${CalcFormat.fixed(months, 1)} months of expenses in your emergency goals, inside the " +
                                "3-to-6-month range experts commonly suggest."
                        net > 0 ->
                            "Experts commonly suggest 3 to 6 months of expenses, ${Paper.compactMoney(low)} to " +
                                "${Paper.compactMoney(high)} for you. Routing ${Paper.compactMoney(net)}/mo of " +
                                "surplus toward it is one way it grows."
                        else ->
                            "Experts commonly suggest 3 to 6 months of expenses, ${Paper.compactMoney(low)} to " +
                                "${Paper.compactMoney(high)} for you. A surplus has to come first, because a " +
                                "cushion can't be funded while overspending."
                    }
                    out.add(
                        Finding(
                            "emergency", "Emergency fund",
                            "${CalcFormat.fixed(months, 1)} of 3.0 months covered",
                            status, fix, FindingAction.OpenGoals, 25.0,
                        )
                    )
                }
                contribution > 0 -> {
                    // The budget itself names one: a line titled Emergency fund.
                    out.add(
                        Finding(
                            "emergency", "Emergency fund",
                            "${Paper.compactMoney(contribution)}/mo set aside · saved total unknown",
                            FindingStatus.Warn("building"),
                            "Your budget puts ${Paper.compactMoney(contribution)}/mo toward it. Experts commonly " +
                                "suggest 3 to 6 months of expenses saved, ${Paper.compactMoney(low)} to " +
                                "${Paper.compactMoney(high)} for you; a budget line can't tell us how much is " +
                                "already there. Name a goal Emergency Fund with the saved amount, or point one at " +
                                "the account holding it, and this tracks the total itself.",
                            FindingAction.OpenGoals, 25.0,
                        )
                    )
                }
                else -> {
                    // We genuinely don't know whether one exists, and the copy
                    // says so instead of scoring the user as if we did.
                    val surplusLine = if (net > 0) {
                        " If it doesn't exist yet, the ${Paper.compactMoney(net)}/mo you currently keep is where " +
                            "many guides suggest it starts."
                    } else ""
                    out.add(
                        Finding(
                            "emergency", "Emergency fund",
                            "Unknown to us: ${Paper.compactMoney(low)} to ${Paper.compactMoney(high)} is the common guideline",
                            FindingStatus.Warn("unknown"),
                            "Your expenses are ${Paper.compactMoney(expenses)}/mo, and experts commonly suggest " +
                                "keeping 3 to 6 months of them saved: ${Paper.compactMoney(low)} to " +
                                "${Paper.compactMoney(high)} for you. We can't see whether you have that. If you " +
                                "do, name a goal Emergency Fund, or point one at the account holding it, and this " +
                                "tracks itself.$surplusLine",
                            FindingAction.OpenGoals, 25.0,
                        )
                    )
                }
            }
        }

        // Goal pace (weight 20, split across off-pace goals)
        val ledger = BankLedgerStore.shared
        val activeGoals = store.currentGoals.filter { goal ->
            goal.targetAmount > 0 && GoalProgress.measure(goal, ledger).first < goal.targetAmount
        }
        if (activeGoals.isNotEmpty()) {
            val offPace = activeGoals.mapNotNull { goalPace(it, ledger) }
            if (offPace.isEmpty()) {
                out.add(
                    Finding(
                        "goals", "Goal pace",
                        "${activeGoals.size} goal${if (activeGoals.size == 1) "" else "s"} on track",
                        FindingStatus.Good("on pace"),
                        "Every active goal's planned figure covers what its date needs.",
                        FindingAction.OpenGoals, 20.0,
                    )
                )
            } else {
                val each = 20.0 / offPace.size
                offPace.forEach { out.add(it.copy(weight = each)) }
            }
        }

        return out
    }

    // MARK: Score

    /**
     * Weighted roll-up over the load-bearing findings: good = full weight,
     * warn = half, bad = none. Informational findings carry no weight.
     */
    fun score(findings: List<Finding>): Int {
        val weighted = findings.filter { it.weight > 0 }
        val totalWeight = weighted.sumOf { it.weight }
        if (totalWeight <= 0) return 0
        val earned = weighted.sumOf { f ->
            when (f.status) {
                is FindingStatus.Good -> f.weight
                is FindingStatus.Warn -> f.weight * 0.5
                is FindingStatus.Bad -> 0.0
            }
        }
        return (earned / totalWeight * 100).roundToInt()
    }

    /** "3 look good, 2 need attention" — the hub card's line. */
    fun summaryLine(findings: List<Finding>): String? {
        if (findings.isEmpty()) return null
        val good = findings.count { it.status.rank == 0 }
        val attention = findings.size - good
        if (attention == 0) return "All $good findings look good"
        if (good == 0) return "$attention finding${if (attention == 1) "" else "s"} need${if (attention == 1) "s" else ""} attention"
        return "$good look${if (good == 1) "s" else ""} good, $attention need${if (attention == 1) "s" else ""} attention"
    }

    // MARK: Helpers

    data class CategoryMove(
        val category: String,
        val current: Double,
        val previous: Double,
        /** Percent change, signed. */
        val change: Double,
        val previousLabel: String,
        /**
         * The newer month being measured. Named explicitly because it is not
         * always "now": bank comparisons use a month-to-date window.
         */
        val currentLabel: String,
    )

    private val isoFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val monthNameFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.US)

    /**
     * Every qualifying category swing between the two most recent comparable
     * months, largest first. Reads whatever My Budget has OPEN, matching
     * store.currentItems and every other finding on the page.
     */
    fun categoryMoves(store: BudgetStore, today: LocalDate = LocalDate.now()): List<CategoryMove> {
        val thisMonth: List<BudgetItem>
        val lastMonth: List<BudgetItem>
        val previousLabel: String
        val currentLabel: String

        val ledger = BankLedgerStore.shared
        if (ledger.isReadingBank) {
            // The month IN PROGRESS against the same stretch of last month,
            // day for day: comparing nine days against a full previous month
            // would invent a swing out of the calendar alone.
            val thisStart = today.withDayOfMonth(1)
            val prevStart = thisStart.minusMonths(1)
            val prevLength = YearMonth.from(prevStart).lengthOfMonth()
            val prevDay = minOf(today.dayOfMonth, prevLength)
            val prevEnd = prevStart.plusDays((prevDay - 1).toLong())
            val all = ledger.selectableAccounts.map { it.id }.toSet()
            thisMonth = ledger.items(
                BudgetPeriod.Range(thisStart.format(isoFmt), today.format(isoFmt)), all, store.budgetType,
            )
            lastMonth = ledger.items(
                BudgetPeriod.Range(prevStart.format(isoFmt), prevEnd.format(isoFmt)), all, store.budgetType,
            )
            previousLabel = if (prevDay == prevLength) {
                "over all of ${prevStart.format(monthNameFmt)}"
            } else {
                "by this point in ${prevStart.format(monthNameFmt)}"
            }
            currentLabel = "so far this month"
        } else {
            val slot = store.rememberedSlot
            if (!BudgetStore.isDated(slot)) return emptyList()
            val prevKey = BudgetStore.monthKey(slot, -1)
            thisMonth = store.itemsInMonth(slot)
            lastMonth = store.itemsInMonth(prevKey)
            previousLabel = "in ${BudgetStore.monthDisplayName(prevKey)}"
            currentLabel = "in ${BudgetStore.monthDisplayName(slot)}"
        }
        if (thisMonth.isEmpty() || lastMonth.isEmpty()) return emptyList()

        fun byCategory(items: List<BudgetItem>): Map<String, Double> =
            items.filter { it.type == ItemType.Expense }
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.monthlyAmount } }

        val now = byCategory(thisMonth)
        val prev = byCategory(lastMonth)

        val moves = mutableListOf<CategoryMove>()
        for ((category, previous) in prev) {
            // Both floors matter: a $4 line tripling is noise, and a brand-new
            // category has no baseline to compare against.
            if (previous < 15) continue
            val current = now[category] ?: continue
            val change = (current - previous) / previous * 100
            if (abs(change) < 10 || abs(current - previous) < 15) continue
            moves.add(CategoryMove(category, current, previous, change, previousLabel, currentLabel))
        }
        return moves.sortedByDescending { abs(it.change) }
    }

    private fun goalPace(goal: SavingsGoal, ledger: BankLedgerStore): Finding? {
        val (current, target) = GoalProgress.measure(goal, ledger)
        val remaining = target - current
        if (remaining <= 0) return null
        val targetDate = runCatching { LocalDate.parse(goal.targetDate.take(10)) }.getOrNull() ?: return null
        val days = ChronoUnit.DAYS.between(LocalDate.now(), targetDate)
        val monthsLeft = if (days > 0) ceil(days / 30.44).toInt() else 0
        val needed = if (monthsLeft > 0) remaining / monthsLeft else remaining
        if (needed <= 0 || goal.monthlyContribution >= needed) return null
        val shortfall = needed - goal.monthlyContribution

        val fix = if (goal.monthlyContribution > 0) {
            val monthsAtPlanned = ceil(remaining / goal.monthlyContribution).toInt()
            "At ${Paper.compactMoney(goal.monthlyContribution)}/mo, ${goal.name} funds in ~$monthsAtPlanned months; " +
                "it needs ${Paper.compactMoney(needed)}/mo to land by the target date."
        } else {
            "No monthly plan set. ${Paper.compactMoney(needed)}/mo funds ${goal.name} by its target date."
        }
        val severe = shortfall >= needed * 0.25
        return Finding(
            "goal-${goal.id}",
            "${goal.name} pace",
            "${Paper.compactMoney(goal.monthlyContribution)}/mo of ${Paper.compactMoney(needed)}/mo needed",
            if (severe) FindingStatus.Bad("off pace") else FindingStatus.Warn("${Paper.compactMoney(shortfall)}/mo short"),
            fix, FindingAction.OpenGoals, 0.0,
        )
    }
}
