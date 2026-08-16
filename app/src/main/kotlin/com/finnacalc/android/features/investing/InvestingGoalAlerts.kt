//
// InvestingGoalAlerts.kt
//
// Port of InvestingGoalAlertCenter from iOS Features/Investing/InvestingGoals.swift.
//
// Percent marks fire once when progress crosses them, un-fire when the market
// pulls back under, and only the highest newly crossed mark notifies at once.
// Checked whenever fresh positions are in hand — there is no background feed
// to watch, so the moments the app values the portfolio are the moments
// progress can move.
//
// The message says what actually happened in the goal's own terms. "What this
// goal watches has crossed 50% of the target" told nobody anything.
//

package com.finnacalc.android.features.investing

import android.content.Context
import com.finnacalc.android.core.notifications.NotificationChannelId
import com.finnacalc.android.core.notifications.Notifier
import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.features.calculators.CalcFormat

object InvestingGoalAlertCenter {

    /** The thresholds offered as one-tap chips; any percent can be typed. */
    val thresholds = listOf(50, 75, 90, 100)

    data class Alert(val identifier: String, val title: String, val body: String)

    /**
     * The pure half: which goals cross which marks, what the resulting
     * notification says, and the updated `alertsFired` state. Split out from
     * posting so it can be tested without a Context.
     */
    fun evaluate(
        goals: List<InvestingGoal>,
        positions: List<BrokeragePosition>,
        sectors: Map<String, String?> = emptyMap(),
    ): Pair<List<InvestingGoal>, List<Alert>> {
        val updated = goals.toMutableList()
        val alerts = mutableListOf<Alert>()

        for (index in updated.indices) {
            val goal = updated[index]
            if (goal.alerts.isEmpty()) continue
            val pct = InvestingGoalMath.alertProgressPercent(goal, positions, sectors) ?: continue

            // A mark un-fires when progress falls back under it, so it can
            // honestly fire again on the next crossing.
            val stillFired = goal.alertsFired.filter { it <= pct }
            val newlyCrossed = goal.alerts.filter { pct >= it && !stillFired.contains(it) }.sorted()

            newlyCrossed.lastOrNull()?.let { top ->
                alerts.add(alertFor(goal, top))
            }

            val fired = (stillFired + newlyCrossed).distinct().sorted()
            if (fired != goal.alertsFired) {
                updated[index] = goal.copy(alertsFired = fired)
            }
        }
        return updated to alerts
    }

    /** Evaluates, posts anything newly crossed, and saves the fired state. */
    fun check(
        positions: List<BrokeragePosition>,
        context: Context? = null,
        sectors: Map<String, String?> = emptyMap(),
    ) {
        val store = InvestingGoalStore.shared
        val (updated, alerts) = evaluate(store.goals.value, positions, sectors)
        if (updated != store.goals.value) store.setAll(updated)
        alerts.forEach {
            Notifier.post(it.identifier, it.title, it.body, NotificationChannelId.Goals, context)
        }
    }

    private fun money(value: Double): String =
        "$" + if (value >= 1000) CalcFormat.int(value) else CalcFormat.fixed(value, 2)

    internal fun alertFor(goal: InvestingGoal, threshold: Int): Alert {
        val name = goal.name.ifEmpty { "Your investing goal" }
        val identifier = "finnacalc.investinggoal.${goal.id}.$threshold"

        if (goal.kind == InvestingGoalKind.Mix) {
            val slice = goal.mixScopeLabel.ifEmpty { "That slice" }
            val line = CalcFormat.fixed(goal.targetValue, 0)
            return if (threshold >= 100) {
                Alert(
                    identifier,
                    "$name: line crossed",
                    if (goal.mixKeepUnder) "$slice is now past your $line% cap."
                    else "$slice has slipped below your $line% floor.",
                )
            } else {
                Alert(
                    identifier,
                    "$name: $threshold% of the way",
                    if (goal.mixKeepUnder) {
                        "$slice is $threshold% of the way to your $line% cap."
                    } else {
                        "$slice has drifted $threshold% of the way down to your $line% floor."
                    },
                )
            }
        }

        val scope = if (goal.symbols.isEmpty()) "Your portfolio" else goal.symbols.joinToString(", ")
        val reached = threshold >= 100
        val title = if (reached) "$name: goal reached" else "$name: $threshold% there"

        return if (goal.targetKind == InvestingTargetKind.Percent) {
            val target = CalcFormat.fixed(goal.targetValue, 0)
            Alert(
                identifier, title,
                if (reached) "$scope is up the full $target% you were aiming for."
                else "$scope is $threshold% of the way to a $target% gain.",
            )
        } else {
            val target = money(goal.targetValue)
            Alert(
                identifier, title,
                if (reached) "$scope has reached $target."
                else "$scope is $threshold% of the way to $target.",
            )
        }
    }
}
