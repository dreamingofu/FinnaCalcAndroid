//
// InvestingGoals.kt
//
// Port of the model, store and math from iOS Features/Investing/InvestingGoals.swift.
//
// Two kinds of goal: Growth chases a value (a dollar target or a total-return
// percent), and Balance keeps a slice of the portfolio inside a line — the
// diversification goal.
//
// Percent goals measure TOTAL RETURN on the money put in, not distance from a
// frozen starting value: measuring from a snapshot meant that adding money
// later read as "gain". Basis comes from the brokerage's own P/L, and a scope
// with no known basis reads honestly as no progress rather than a made-up
// percent.
//

package com.finnacalc.android.features.investing

import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.features.budgeting.GoalEmoji
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Model

@Serializable
enum class InvestingTargetKind {
    /** Reach a dollar value. */
    @SerialName("amount") Amount,

    /** Total gain on the money put in. */
    @SerialName("percent") Percent,
}

@Serializable
enum class InvestingGoalKind {
    @SerialName("growth") Growth,
    @SerialName("mix") Mix,
}

/** What a Balance goal watches: named holdings, one sector, or an asset class. */
@Serializable
enum class MixScope {
    @SerialName("holdings") Holdings,
    @SerialName("sector") Sector,
    @SerialName("assetClass") AssetClass,
}

/**
 * Unknown fields default rather than wiping every saved goal — the same
 * lenient-decode rule the budgeting store follows.
 */
@Serializable
data class InvestingGoal(
    val id: String,
    val name: String,
    /** null = follow the name suggestion, like budgeting. */
    val emoji: String? = null,
    /** Ring colour hex; null = the default green. */
    val ringColorHex: String? = null,
    /** Brokerage account this goal watches; null = every connected account. */
    val accountId: String? = null,
    /** Shown when the account disappears (disconnected broker). */
    val accountName: String? = null,
    /** Symbols in scope; empty = the whole portfolio. */
    val symbols: List<String> = emptyList(),
    val targetKind: InvestingTargetKind = InvestingTargetKind.Amount,
    /** Dollars for Amount, percent points for Percent. */
    val targetValue: Double,
    /** Scope's market value when the goal was created (percent baseline). */
    val baselineValue: Double = 0.0,
    /** ISO yyyy-MM-dd; "" = no date. The form no longer offers dates. */
    val targetDate: String = "",
    /** Percent marks that notify when progress crosses them. */
    val alerts: List<Int> = emptyList(),
    /** Marks that already notified, so each fires once per crossing. */
    val alertsFired: List<Int> = emptyList(),
    val kind: InvestingGoalKind = InvestingGoalKind.Growth,
    val mixScope: MixScope = MixScope.Holdings,
    val mixSector: String? = null,
    /** "Stocks" or "Funds" when mixScope is AssetClass. */
    val mixAssetClass: String? = null,
    /** true = stay under targetValue percent; false = stay above it. */
    val mixKeepUnder: Boolean = true,
    /**
     * Set when the user answered the "you reached it, remove this goal?"
     * prompt with Keep, so a finished goal asks once rather than every open.
     */
    val keptAfterReached: Boolean = false,
) {
    /**
     * The emoji to display: the user's override, else the name-derived
     * suggestion — the same rule budgeting goals follow, and the same one iOS
     * applies (`goal.emoji ?? GoalEmoji.suggest(for: goal.name)`). A hardcoded
     * glyph here would make an investing goal named "New car" look unlike the
     * budgeting goal of the same name.
     */
    val resolvedEmoji: String get() = emoji?.takeIf { it.isNotEmpty() } ?: GoalEmoji.suggest(name)

    /** What a Balance goal's slice is called on cards and rows. */
    val mixScopeLabel: String
        get() = when (mixScope) {
            MixScope.Holdings -> symbols.joinToString(" · ")
            MixScope.Sector -> mixSector ?: "Sector"
            MixScope.AssetClass -> mixAssetClass ?: "Asset class"
        }
}

// MARK: - Store

class InvestingGoalStore private constructor() {

    private val _goals = MutableStateFlow(load())
    val goals: StateFlow<List<InvestingGoal>> = _goals.asStateFlow()

    fun setAll(goals: List<InvestingGoal>) {
        _goals.value = goals
        JsonPrefs.persist(goals, STORAGE_KEY)
    }

    fun add(goal: InvestingGoal) = setAll(_goals.value + goal)

    fun update(goal: InvestingGoal) =
        setAll(_goals.value.map { if (it.id == goal.id) goal else it })

    fun delete(goal: InvestingGoal) = setAll(_goals.value.filterNot { it.id == goal.id })

    companion object {
        val shared = InvestingGoalStore()
        private const val STORAGE_KEY = "finnacalc.investing.goals"

        private fun load(): List<InvestingGoal> =
            JsonPrefs.load<List<InvestingGoal>>(STORAGE_KEY) ?: emptyList()

        /** Test seam: drops in-memory state after JsonPrefs.resetForTesting(). */
        fun resetForTesting() {
            shared._goals.value = emptyList()
        }
    }
}

// MARK: - Measuring

object InvestingGoalMath {

    data class Reading(
        val current: Double,
        /** The dollar value the goal is driving at. */
        val targetAmount: Double,
        /** 0…1, clamped. */
        val fraction: Double,
        /** For percent goals: the gain so far, in percent points. */
        val gainPct: Double?,
    )

    /** How big the slice is against the whole, and whether it respects the line. */
    data class MixReading(
        /** The slice's share of the portfolio, 0…100. */
        val weightPct: Double,
        val sliceValue: Double,
        val totalValue: Double,
        val compliant: Boolean,
    )

    /** The live market value of what the goal watches. */
    fun scopeValue(goal: InvestingGoal, positions: List<BrokeragePosition>): Double =
        positions
            .filter { goal.accountId == null || it.accountId == goal.accountId }
            .filter { goal.symbols.isEmpty() || goal.symbols.contains(it.symbol.uppercase()) }
            .sumOf { it.marketValue ?: 0.0 }

    fun measureMix(
        goal: InvestingGoal,
        positions: List<BrokeragePosition>,
        sectors: Map<String, String?> = emptyMap(),
    ): MixReading {
        val inAccount = positions.filter { goal.accountId == null || it.accountId == goal.accountId }
        var total = 0.0
        var slice = 0.0
        for (p in inAccount) {
            val mv = p.marketValue ?: continue
            if (mv <= 0) continue
            total += mv
            val symbol = p.symbol.uppercase()
            val inSlice = when (goal.mixScope) {
                MixScope.Holdings -> goal.symbols.contains(symbol)
                MixScope.Sector -> (sectors[symbol] ?: "") == (goal.mixSector ?: "")
                MixScope.AssetClass -> {
                    val isFund = PortfolioAnalytics.knownETFs.contains(symbol)
                    (goal.mixAssetClass == "Funds") == isFund
                }
            }
            if (inSlice) slice += mv
        }
        val pct = if (total > 0) slice / total * 100 else 0.0
        val compliant = if (goal.mixKeepUnder) pct <= goal.targetValue else pct >= goal.targetValue
        return MixReading(pct, slice, total, compliant)
    }

    fun measure(goal: InvestingGoal, positions: List<BrokeragePosition>): Reading {
        val current = scopeValue(goal, positions)
        return when (goal.targetKind) {
            InvestingTargetKind.Amount -> {
                val fraction =
                    if (goal.targetValue > 0) (current / goal.targetValue).coerceIn(0.0, 1.0) else 0.0
                Reading(current, goal.targetValue, fraction, null)
            }

            InvestingTargetKind.Percent -> {
                val scoped = positions
                    .filter { goal.accountId == null || it.accountId == goal.accountId }
                    .filter { goal.symbols.isEmpty() || goal.symbols.contains(it.symbol.uppercase()) }
                var value = 0.0
                var basis = 0.0
                for (p in scoped) {
                    val mv = p.marketValue ?: continue
                    val pnl = p.openPnl ?: continue
                    value += mv
                    basis += mv - pnl
                }
                if (basis <= 0) {
                    // No known basis: no progress, rather than a made-up percent.
                    return Reading(current, 0.0, 0.0, null)
                }
                val gainPct = (value - basis) / basis * 100
                val fraction =
                    if (goal.targetValue > 0) (gainPct / goal.targetValue).coerceIn(0.0, 1.0) else 0.0
                Reading(value, basis * (1 + goal.targetValue / 100), fraction, gainPct)
            }
        }
    }

    /**
     * How far along the goal's own alert scale a reading sits, as whole
     * percent. Growth marks measure progress toward the target; Balance marks
     * measure distance toward the LINE, so 100 means the slice has reached the
     * cap (or fallen to the floor) and past it means the line is crossed.
     * Returns null when there is nothing meaningful to measure.
     */
    fun alertProgressPercent(
        goal: InvestingGoal,
        positions: List<BrokeragePosition>,
        sectors: Map<String, String?> = emptyMap(),
    ): Int? {
        if (goal.kind == InvestingGoalKind.Mix) {
            val reading = measureMix(goal, positions, sectors)
            if (goal.targetValue <= 0 || reading.weightPct <= 0) return null
            val toward = if (goal.mixKeepUnder) {
                reading.weightPct / goal.targetValue * 100
            } else {
                goal.targetValue / reading.weightPct * 100
            }
            return floor(toward).toInt()
        }
        return floor(measure(goal, positions).fraction * 100).toInt()
    }
}
