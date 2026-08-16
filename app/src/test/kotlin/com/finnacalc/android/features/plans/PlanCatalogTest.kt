package com.finnacalc.android.features.plans

import com.finnacalc.android.core.feedback.SessionFeedback
import com.finnacalc.android.core.util.JsonPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog is the single source of truth for the paywall's copy and its
 * savings math — the house rule is that no percentage appears on screen that
 * isn't derived from these numbers, so the derivation is worth pinning.
 */
class PlanCatalogTest {

    @Test
    fun `annual savings are derived from the two prices`() {
        val pro = PlanCatalog.plan(PlanTier.Pro)!!
        // 16.99 * 12 = 203.88; (203.88 - 159.99) / 203.88 = 21.5% → 22.
        assertEquals(22, pro.annualSavingsPercent)

        val plus = PlanCatalog.plan(PlanTier.Plus)!!
        // 9.99 * 12 = 119.88; (119.88 - 94.99) / 119.88 = 20.8% → 21.
        assertEquals(21, plus.annualSavingsPercent)
    }

    @Test
    fun `the toggle quotes the largest saving on offer`() {
        assertEquals(
            PlanCatalog.all.maxOf { it.annualSavingsPercent },
            PlanCatalog.maxAnnualSavingsPercent,
        )
    }

    @Test
    fun `price returns the interval actually asked for`() {
        val pro = PlanCatalog.plan(PlanTier.Pro)!!
        assertEquals(16.99, pro.price(PlanBillingInterval.Monthly), 0.001)
        assertEquals(159.99, pro.price(PlanBillingInterval.Annual), 0.001)
    }

    @Test
    fun `prices format as US dollars with cents`() {
        assertEquals("$16.99", PlanCatalog.priceString(16.99))
        assertEquals("$159.99", PlanCatalog.priceString(159.99))
        assertEquals("$9.00", PlanCatalog.priceString(9.0))
    }

    @Test
    fun `the recommended plan leads and is the only one`() {
        assertTrue(PlanCatalog.all.first().recommended)
        assertEquals(1, PlanCatalog.all.count { it.recommended })
        assertEquals(PlanTier.Pro, PlanCatalog.all.first().tier)
    }

    @Test
    fun `every tier is in the catalog exactly once`() {
        assertEquals(PlanTier.entries.size, PlanCatalog.all.size)
        assertEquals(PlanTier.entries.toSet(), PlanCatalog.all.map { it.tier }.toSet())
        PlanTier.entries.forEach { assertNotNull(PlanCatalog.plan(it)) }
    }

    // MARK: - Play product identity

    @Test
    fun `each tier maps to its own subscription id and back`() {
        PlanTier.entries.forEach { tier ->
            val id = EntitlementStore.subscriptionId(tier)
            assertEquals("finnacalc_${tier.raw}", id)
            assertEquals(tier, EntitlementStore.tierFor(id))
        }
    }

    @Test
    fun `an unknown subscription id maps to no tier`() {
        assertNull(EntitlementStore.tierFor("finnacalc_platinum"))
        assertNull(EntitlementStore.tierFor("com.finnacalc.pro.monthly"))
    }

    @Test
    fun `base plans are the interval raw values`() {
        assertEquals("monthly", EntitlementStore.basePlanId(PlanBillingInterval.Monthly))
        assertEquals("annual", EntitlementStore.basePlanId(PlanBillingInterval.Annual))
    }
}

/** The 30-day quiet period behind the automatic feedback prompt. */
class SessionFeedbackTest {

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `a first-time user is eligible`() {
        JsonPrefs.resetForTesting()
        assertTrue(SessionFeedback.isEligible(nowMillis = 1_000_000))
    }

    @Test
    fun `stamping the cooldown quiets the prompt for thirty days`() {
        JsonPrefs.resetForTesting()
        val now = 100 * day
        SessionFeedback.stampCooldown(now)

        assertFalse(SessionFeedback.isEligible(now))
        assertFalse(SessionFeedback.isEligible(now + 29 * day))
        assertTrue(SessionFeedback.isEligible(now + 30 * day))
    }

    @Test
    fun `stamping clears a pending prompt`() {
        JsonPrefs.resetForTesting()
        SessionFeedback.stampCooldown(50 * day)
        assertFalse(SessionFeedback.showPrompt.value)
    }
}
