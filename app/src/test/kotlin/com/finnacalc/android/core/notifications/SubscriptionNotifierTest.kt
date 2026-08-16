package com.finnacalc.android.core.notifications

import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.widget.GoalSnapshot
import com.finnacalc.android.widget.GoalsSnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The reminder rule, which decides whether a bill nudge fires at all — the
 * half that has to be right before any of it reaches a notification tray.
 */
class SubscriptionNotifierTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 10, 8, 0)

    private fun reminder(
        id: String = "sub:netflix",
        name: String = "NETFLIX.COM",
        chargeIn: Long = 10,
        leadDays: Int = 3,
        remind: Boolean = true,
    ) = PendingReminder(
        id = id,
        name = name,
        nextCharge = now.plusDays(chargeIn),
        leadDays = leadDays,
        remind = remind,
    )

    @Test
    fun `a reminder fires its lead days before the charge, in the morning`() {
        val planned = SubscriptionNotifier.plan(
            listOf(reminder(chargeIn = 10, leadDays = 3)),
            now, enabled = true, ignored = emptySet(),
        )
        assertEquals(1, planned.size)
        val fire = planned.first().second
        assertEquals(now.plusDays(7).toLocalDate(), fire.toLocalDate())
        assertEquals(9, fire.hour)
    }

    @Test
    fun `a lead window that has already passed is dropped`() {
        // Charging tomorrow with a 3-day lead: the moment to warn is gone, and
        // a reminder for yesterday is noise rather than a warning.
        val planned = SubscriptionNotifier.plan(
            listOf(reminder(chargeIn = 1, leadDays = 3)),
            now, enabled = true, ignored = emptySet(),
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `the per-item switch is respected`() {
        val planned = SubscriptionNotifier.plan(
            listOf(reminder(remind = false)),
            now, enabled = true, ignored = emptySet(),
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `an ignored subscription is never scheduled`() {
        val planned = SubscriptionNotifier.plan(
            listOf(reminder(id = "sub:gym")),
            now, enabled = true, ignored = setOf("sub:gym"),
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `the master switch silences everything`() {
        val planned = SubscriptionNotifier.plan(
            listOf(reminder(), reminder(id = "sub:spotify")),
            now, enabled = false, ignored = emptySet(),
        )
        assertTrue(planned.isEmpty())
    }

    @Test
    fun `the ignore list round-trips`() {
        JsonPrefs.resetForTesting()
        assertFalse(SubscriptionNotifier.isIgnored("sub:gym"))
        SubscriptionNotifier.setIgnored("sub:gym", true)
        assertTrue(SubscriptionNotifier.isIgnored("sub:gym"))
        SubscriptionNotifier.setIgnored("sub:gym", false)
        assertFalse(SubscriptionNotifier.isIgnored("sub:gym"))
    }

    // MARK: - Copy

    @Test
    fun `the lead phrase reads naturally at one day and beyond`() {
        assertEquals("tomorrow", SubscriptionNotifier.leadPhrase(1))
        assertEquals("in 3 days", SubscriptionNotifier.leadPhrase(3))
    }

    @Test
    fun `the merchant name is the title, trimmed`() {
        // A bank spells a merchant however it likes, trailing spaces included.
        val (title, body) = SubscriptionNotifier.content(reminder(name = "  NETFLIX.COM  "))
        assertEquals("NETFLIX.COM", title)
        assertEquals("You'll be charged for this in 3 days.", body)
    }

    @Test
    fun `the body never states an amount`() {
        // What a subscription costs varies; the charge date is what's known.
        val (_, body) = SubscriptionNotifier.content(reminder())
        assertFalse(body.contains("$"))
    }
}

/** The widget reads a published snapshot, so publishing has to be right. */
class GoalsSnapshotStoreTest {

    @Test
    fun `snapshots publish closest-to-done first and cap at four`() {
        JsonPrefs.resetForTesting()
        GoalsSnapshotStore.publish(
            listOf(
                GoalSnapshot("a", "Car", 1_000.0, 10_000.0),
                GoalSnapshot("b", "Emergency", 9_000.0, 10_000.0),
                GoalSnapshot("c", "Trip", 5_000.0, 10_000.0),
                GoalSnapshot("d", "Laptop", 2_000.0, 10_000.0),
                GoalSnapshot("e", "Rainy day", 8_000.0, 10_000.0),
            )
        )
        val loaded = GoalsSnapshotStore.load()
        assertEquals(4, loaded.size)
        assertEquals(listOf("b", "e", "c", "d"), loaded.map { it.id })
    }

    @Test
    fun `a fraction clamps and survives a zero target`() {
        assertEquals(1.0, GoalSnapshot("a", "Done", 200.0, 100.0).fraction, 0.001)
        assertEquals(0.0, GoalSnapshot("b", "Unset", 200.0, 0.0).fraction, 0.001)
        assertEquals(0.5, GoalSnapshot("c", "Half", 50.0, 100.0).fraction, 0.001)
    }

    @Test
    fun `an empty publish reads back empty rather than stale`() {
        JsonPrefs.resetForTesting()
        GoalsSnapshotStore.publish(listOf(GoalSnapshot("a", "Car", 1.0, 2.0)))
        GoalsSnapshotStore.publish(emptyList())
        assertTrue(GoalsSnapshotStore.load().isEmpty())
    }
}
