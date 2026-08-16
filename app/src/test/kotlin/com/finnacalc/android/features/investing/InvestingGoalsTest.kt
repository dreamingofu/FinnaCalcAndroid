package com.finnacalc.android.features.investing

import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.features.budgeting.GoalEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestingGoalsTest {

    private fun pos(symbol: String, value: Double, pnl: Double? = null, account: String = "a1") =
        BrokeragePosition(
            accountId = account,
            symbol = symbol,
            description = symbol,
            units = 1.0,
            marketValue = value,
            openPnl = pnl,
        )

    private fun goal(
        target: Double = 10_000.0,
        kind: InvestingTargetKind = InvestingTargetKind.Amount,
        symbols: List<String> = emptyList(),
        alerts: List<Int> = emptyList(),
        fired: List<Int> = emptyList(),
        account: String? = null,
    ) = InvestingGoal(
        id = "g1",
        name = "Test goal",
        symbols = symbols,
        targetKind = kind,
        targetValue = target,
        alerts = alerts,
        alertsFired = fired,
        accountId = account,
    )

    // MARK: - Scope

    @Test
    fun `an empty symbol list means the whole portfolio`() {
        val positions = listOf(pos("AAPL", 600.0), pos("MSFT", 400.0))
        assertEquals(1_000.0, InvestingGoalMath.scopeValue(goal(), positions), 0.001)
    }

    @Test
    fun `symbols narrow the scope, case-insensitively`() {
        val positions = listOf(pos("AAPL", 600.0), pos("MSFT", 400.0))
        assertEquals(
            600.0,
            InvestingGoalMath.scopeValue(goal(symbols = listOf("AAPL")), positions),
            0.001,
        )
    }

    @Test
    fun `an account filter excludes other accounts`() {
        val positions = listOf(pos("AAPL", 600.0, account = "a1"), pos("MSFT", 400.0, account = "a2"))
        assertEquals(
            600.0,
            InvestingGoalMath.scopeValue(goal(account = "a1"), positions),
            0.001,
        )
    }

    // MARK: - Amount goals

    @Test
    fun `an amount goal measures value against target and clamps at one`() {
        val half = InvestingGoalMath.measure(goal(target = 1_000.0), listOf(pos("AAPL", 500.0)))
        assertEquals(0.5, half.fraction, 0.001)

        val over = InvestingGoalMath.measure(goal(target = 1_000.0), listOf(pos("AAPL", 4_000.0)))
        assertEquals(1.0, over.fraction, 0.001)
    }

    @Test
    fun `a zero target is no progress rather than a divide by zero`() {
        val reading = InvestingGoalMath.measure(goal(target = 0.0), listOf(pos("AAPL", 500.0)))
        assertEquals(0.0, reading.fraction, 0.001)
    }

    // MARK: - Percent goals

    @Test
    fun `a percent goal measures total return on the money put in`() {
        // $1,200 now, $200 of it gain → $1,000 basis → +20%.
        val reading = InvestingGoalMath.measure(
            goal(target = 40.0, kind = InvestingTargetKind.Percent),
            listOf(pos("AAPL", 1_200.0, pnl = 200.0)),
        )
        assertEquals(20.0, reading.gainPct!!, 0.001)
        assertEquals(0.5, reading.fraction, 0.001)
    }

    @Test
    fun `a percent goal with no cost basis reads as unknown, not zero gain`() {
        // The brokerage reported no P/L, so there is no honest percent.
        val reading = InvestingGoalMath.measure(
            goal(target = 20.0, kind = InvestingTargetKind.Percent),
            listOf(pos("AAPL", 1_200.0, pnl = null)),
        )
        assertNull(reading.gainPct)
        assertEquals(0.0, reading.fraction, 0.001)
    }

    @Test
    fun `a loss reads as no progress rather than a negative fraction`() {
        val reading = InvestingGoalMath.measure(
            goal(target = 20.0, kind = InvestingTargetKind.Percent),
            listOf(pos("AAPL", 800.0, pnl = -200.0)),
        )
        assertEquals(-20.0, reading.gainPct!!, 0.001)
        assertEquals(0.0, reading.fraction, 0.001)
    }

    // MARK: - Balance (mix) goals

    private fun mixGoal(target: Double, keepUnder: Boolean, symbols: List<String>) =
        InvestingGoal(
            id = "m1",
            name = "Mix",
            symbols = symbols,
            kind = InvestingGoalKind.Mix,
            mixScope = MixScope.Holdings,
            targetValue = target,
            mixKeepUnder = keepUnder,
        )

    @Test
    fun `a mix goal weighs its slice against the whole`() {
        val positions = listOf(pos("AAPL", 300.0), pos("MSFT", 700.0))
        val reading = InvestingGoalMath.measureMix(mixGoal(20.0, true, listOf("AAPL")), positions)
        assertEquals(30.0, reading.weightPct, 0.001)
        assertEquals(300.0, reading.sliceValue, 0.001)
        assertEquals(1_000.0, reading.totalValue, 0.001)
        // 30% is over a 20% cap.
        assertFalse(reading.compliant)
    }

    @Test
    fun `a floor goal is compliant when the slice is big enough`() {
        val positions = listOf(pos("AAPL", 300.0), pos("MSFT", 700.0))
        val reading = InvestingGoalMath.measureMix(mixGoal(20.0, false, listOf("AAPL")), positions)
        assertTrue(reading.compliant)
    }

    @Test
    fun `an empty portfolio weighs nothing rather than dividing by zero`() {
        val reading = InvestingGoalMath.measureMix(mixGoal(20.0, true, listOf("AAPL")), emptyList())
        assertEquals(0.0, reading.weightPct, 0.001)
        assertEquals(0.0, reading.totalValue, 0.001)
    }

    // MARK: - Alerts

    @Test
    fun `crossing a mark produces one alert and records it`() {
        val goals = listOf(goal(target = 1_000.0, alerts = listOf(50, 75, 100)))
        val (updated, alerts) = InvestingGoalAlertCenter.evaluate(goals, listOf(pos("AAPL", 600.0)))
        assertEquals(1, alerts.size)
        assertEquals(listOf(50), updated.first().alertsFired)
    }

    @Test
    fun `only the highest newly crossed mark notifies at once`() {
        // 80% crosses both 50 and 75 in one go; only 75 should speak.
        val goals = listOf(goal(target = 1_000.0, alerts = listOf(50, 75, 100)))
        val (updated, alerts) = InvestingGoalAlertCenter.evaluate(goals, listOf(pos("AAPL", 800.0)))
        assertEquals(1, alerts.size)
        assertTrue(alerts.first().title.contains("75%"))
        assertEquals(listOf(50, 75), updated.first().alertsFired)
    }

    @Test
    fun `a mark already fired does not fire again`() {
        val goals = listOf(goal(target = 1_000.0, alerts = listOf(50), fired = listOf(50)))
        val (_, alerts) = InvestingGoalAlertCenter.evaluate(goals, listOf(pos("AAPL", 600.0)))
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `a mark un-fires when the market pulls back under it`() {
        // Fired at 50, now down to 30% — the mark clears so it can fire again.
        val goals = listOf(goal(target = 1_000.0, alerts = listOf(50), fired = listOf(50)))
        val (updated, alerts) = InvestingGoalAlertCenter.evaluate(goals, listOf(pos("AAPL", 300.0)))
        assertTrue(alerts.isEmpty())
        assertTrue(updated.first().alertsFired.isEmpty())
    }

    @Test
    fun `a goal with no alerts is left alone`() {
        val goals = listOf(goal(target = 1_000.0))
        val (updated, alerts) = InvestingGoalAlertCenter.evaluate(goals, listOf(pos("AAPL", 900.0)))
        assertTrue(alerts.isEmpty())
        assertEquals(goals, updated)
    }

    @Test
    fun `alert copy names the goal and what actually happened`() {
        val reached = InvestingGoalAlertCenter.alertFor(
            goal(target = 5_000.0).copy(name = "House fund"), 100,
        )
        assertEquals("House fund: goal reached", reached.title)
        assertTrue(reached.body.contains("$5,000"))

        val partway = InvestingGoalAlertCenter.alertFor(
            goal(target = 25.0, kind = InvestingTargetKind.Percent, symbols = listOf("AAPL")), 50,
        )
        assertTrue(partway.body.contains("AAPL"))
        assertTrue(partway.body.contains("25%"))
    }

    @Test
    fun `a balance goal's alert speaks in caps and floors`() {
        val capped = InvestingGoalAlertCenter.alertFor(
            mixGoal(20.0, true, listOf("AAPL")).copy(name = "Keep Apple small"), 100,
        )
        assertEquals("Keep Apple small: line crossed", capped.title)
        assertTrue(capped.body.contains("cap"))

        val floored = InvestingGoalAlertCenter.alertFor(
            mixGoal(20.0, false, listOf("AAPL")).copy(name = "Keep bonds up"), 100,
        )
        assertTrue(floored.body.contains("floor"))
    }

    @Test
    fun `alert identifiers are unique per goal and mark`() {
        val a = InvestingGoalAlertCenter.alertFor(goal(), 50).identifier
        val b = InvestingGoalAlertCenter.alertFor(goal(), 75).identifier
        assertTrue(a != b)
    }

    // MARK: - Emoji

    @Test
    fun `a goal's emoji comes from its name, as it does in budgeting`() {
        // The same suggester both sides of the app use, so an investing goal
        // named "New car" wears the same glyph as the budgeting goal of that
        // name. It used to be a hardcoded target.
        assertEquals("🚗", goal().copy(name = "New car").resolvedEmoji)
        assertEquals("🏠", goal().copy(name = "House downpayment").resolvedEmoji)
        assertEquals("🌴", goal().copy(name = "Retirement").resolvedEmoji)
    }

    @Test
    fun `a chosen emoji outranks the suggestion`() {
        assertEquals("🎸", goal().copy(name = "New car", emoji = "🎸").resolvedEmoji)
    }

    @Test
    fun `an unmatched name falls back rather than showing nothing`() {
        assertEquals(GoalEmoji.FALLBACK, goal().copy(name = "Zzzz").resolvedEmoji)
        // An empty override is not a choice; the suggester still runs.
        assertEquals("🚗", goal().copy(name = "Car fund", emoji = "").resolvedEmoji)
    }

    // MARK: - Store

    @Test
    fun `goals round-trip through storage`() {
        JsonPrefs.resetForTesting()
        InvestingGoalStore.resetForTesting()
        val store = InvestingGoalStore.shared
        store.add(goal(target = 2_500.0).copy(id = "keep-me"))
        assertEquals(1, store.goals.value.size)

        store.update(store.goals.value.first().copy(name = "Renamed"))
        assertEquals("Renamed", store.goals.value.first().name)

        store.delete(store.goals.value.first())
        assertTrue(store.goals.value.isEmpty())
    }
}

/** The Trade Tracker catalog is identity-only and must stay that way. */
class TradeTrackerTest {

    @Test
    fun `every person has an identity and a category`() {
        assertTrue(TrackerCatalog.all.isNotEmpty())
        TrackerCatalog.all.forEach {
            assertTrue(it.id.isNotBlank())
            assertTrue(it.name.isNotBlank())
            assertTrue(it.blurb.isNotBlank())
        }
    }

    @Test
    fun `ids are unique so follow state can't collide`() {
        assertEquals(TrackerCatalog.all.size, TrackerCatalog.all.map { it.id }.toSet().size)
    }

    @Test
    fun `each category has people`() {
        TrackerCategory.entries.forEach {
            assertTrue("${it.title} is empty", TrackerCatalog.inCategory(it).isNotEmpty())
        }
    }

    @Test
    fun `lookup finds a person and misses cleanly`() {
        assertNotNull(TrackerCatalog.person("buffett"))
        assertNull(TrackerCatalog.person("nobody"))
    }

    @Test
    fun `monograms are the initials`() {
        assertEquals("WB", TrackerCatalog.person("buffett")!!.monogram)
    }

    @Test
    fun `following toggles and persists`() {
        JsonPrefs.resetForTesting()
        TrackerFollowStore.resetForTesting()
        assertFalse(TrackerFollowStore.contains("buffett"))
        assertTrue(TrackerFollowStore.toggle("buffett"))
        assertTrue(TrackerFollowStore.contains("buffett"))
        assertFalse(TrackerFollowStore.toggle("buffett"))
        assertFalse(TrackerFollowStore.contains("buffett"))
    }

    @Test
    fun `a hand-set bell clears the master switch's undo snapshot`() {
        JsonPrefs.resetForTesting()
        TrackerAlertStore.resetForTesting()
        TrackerAlertStore.undo = mapOf("buffett" to false)
        TrackerAlertStore.toggle("buffett")
        assertNull(TrackerAlertStore.undo)
    }

    @Test
    fun `following a person never touches the alert list`() {
        JsonPrefs.resetForTesting()
        TrackerFollowStore.resetForTesting()
        TrackerAlertStore.resetForTesting()
        TrackerFollowStore.toggle("wood")
        assertFalse(TrackerAlertStore.contains("wood"))
    }
}

/** The curated ETF list is an on-ramp, so its symbols must be real tickers. */
class ReferencePagesTest {

    @Test
    fun `curated etfs are unique, uppercase symbols with copy`() {
        assertEquals(curatedEtfs.size, curatedEtfs.map { it.symbol }.toSet().size)
        curatedEtfs.forEach {
            assertEquals(it.symbol.uppercase(), it.symbol)
            assertTrue(it.name.isNotBlank())
            assertTrue(it.blurb.isNotBlank())
        }
    }
}
