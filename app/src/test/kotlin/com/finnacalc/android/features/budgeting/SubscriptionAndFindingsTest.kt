package com.finnacalc.android.features.budgeting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SubscriptionAndFindingsTest {

    private fun entry(date: String, name: String, amount: Double, category: String = "Entertainment") =
        LedgerEntry(
            id = "e-$date-$name-$amount",
            connectionID = "bank:test",
            accountID = "acct-1",
            date = date,
            name = name,
            amount = amount,
            category = category,
        )

    // MARK: Detection

    @Test
    fun `detects a monthly charge from repeated ledger entries`() {
        val found = SubscriptionDetector.detectFromLedger(
            listOf(
                entry("2026-05-04", "NETFLIX.COM", 15.99),
                entry("2026-06-04", "NETFLIX.COM", 15.99),
                entry("2026-07-04", "NETFLIX.COM", 15.99),
            )
        )
        assertEquals(1, found.size)
        assertEquals(ChargeCadence.Monthly, found[0].cadence)
        assertEquals(15.99, found[0].averageAmount, 0.001)
        assertEquals(3, found[0].occurrences)
        assertEquals(setOf("acct-1"), found[0].accountIDs)
    }

    @Test
    fun `detects weekly and annual cadences`() {
        val weekly = SubscriptionDetector.detectFromLedger(
            listOf(
                entry("2026-07-01", "GYM", 12.0, "Healthcare"),
                entry("2026-07-08", "GYM", 12.0, "Healthcare"),
            )
        )
        assertEquals(ChargeCadence.Weekly, weekly.single().cadence)

        val annual = SubscriptionDetector.detectFromLedger(
            listOf(
                entry("2025-07-01", "DOMAIN RENEWAL", 22.0, "Software & Subscriptions"),
                entry("2026-07-01", "DOMAIN RENEWAL", 22.0, "Software & Subscriptions"),
            )
        )
        assertEquals(ChargeCadence.Annually, annual.single().cadence)
    }

    @Test
    fun `rent and utilities are never subscriptions`() {
        val found = SubscriptionDetector.detectFromLedger(
            listOf(
                entry("2026-05-01", "GREYSTAR RENT", 1800.0, "Housing"),
                entry("2026-06-01", "GREYSTAR RENT", 1800.0, "Housing"),
            )
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `inconsistent amounts are not flagged`() {
        val found = SubscriptionDetector.detectFromLedger(
            listOf(
                entry("2026-05-04", "CORNER STORE", 10.0, "Food"),
                entry("2026-06-04", "CORNER STORE", 80.0, "Food"),
            )
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `income rows are ignored`() {
        // Plaid convention: negative is money in.
        val found = SubscriptionDetector.detectFromLedger(
            listOf(
                entry("2026-05-04", "PAYROLL", -2000.0, "Salary"),
                entry("2026-06-04", "PAYROLL", -2000.0, "Salary"),
            )
        )
        assertTrue(found.isEmpty())
    }

    // MARK: Reminder helpers

    @Test
    fun `normalize collapses web suffixes and punctuation`() {
        assertEquals("netflix", SubscriptionReminders.normalize("NETFLIX.com"))
        assertEquals("spotify usa", SubscriptionReminders.normalize("  Spotify*USA  "))
    }

    @Test
    fun `nextCharge re-anchors monthly on the day of month`() {
        val next = SubscriptionReminders.nextCharge(
            LocalDate.of(2026, 1, 31), ChargeCadence.Monthly, LocalDateTime.of(2026, 2, 10, 0, 0),
        )!!
        // February clamps to its last day rather than sticking at 28 forever.
        assertEquals(2, next.monthValue)
        assertEquals(28, next.dayOfMonth)
    }

    @Test
    fun `nextCharge steps weekly forward past now`() {
        val next = SubscriptionReminders.nextCharge(
            LocalDate.of(2026, 8, 1), ChargeCadence.Weekly, LocalDateTime.of(2026, 8, 16, 12, 0),
        )!!
        assertEquals(22, next.dayOfMonth)
    }

    @Test
    fun `reminder charges within a window walks the cycle`() {
        val reminder = SubscriptionReminder(
            id = "sub:test", name = "Test",
            amount = 10.0, nextCharge = LocalDateTime.of(2026, 8, 20, 9, 0),
            cadence = ChargeCadence.Monthly, chargeAmount = 10.0,
        )
        // The month it charges in.
        assertTrue(reminder.charges(BudgetPeriod.Month("2026-08")))
        // A later month it also charges in (walked forward).
        assertTrue(reminder.charges(BudgetPeriod.Month("2026-11")))
        // An earlier month it charged in (walked backward).
        assertTrue(reminder.charges(BudgetPeriod.Month("2026-05")))
        assertTrue(reminder.charges(BudgetPeriod.Everything))
    }

    // MARK: Findings

    private fun storeWith(items: List<BudgetItem>): BudgetStore {
        // Each store starts from a clean slate: JsonPrefs falls back to an
        // in-memory map off-device, which would otherwise carry over.
        com.finnacalc.android.core.util.JsonPrefs.resetForTesting()
        val store = BudgetStore()
        store.items = items
        store.goals = emptyList()
        store.history = emptyList()
        return store
    }

    private fun item(
        category: String, amount: Double, type: ItemType = ItemType.Expense,
        subcategory: String = "",
    ) = BudgetItem(
        id = "i-$category-$amount-$type-$subcategory",
        category = category,
        subcategory = subcategory,
        amount = amount,
        frequency = Frequency.Monthly,
        type = type,
        isFixed = false,
        budgetType = BudgetType.Personal,
        month = BudgetStore.UNDATED_MONTH_KEY,
    )

    @Test
    fun `findings are empty without a budget`() {
        assertTrue(BudgetFindings.compute(storeWith(emptyList())).isEmpty())
    }

    @Test
    fun `surplus finding reports the net and its status`() {
        val store = storeWith(
            listOf(
                item("Salary", 5000.0, ItemType.Income),
                item("Housing", 1500.0),
            )
        )
        val surplus = BudgetFindings.compute(store).first { it.id == "surplus" }
        assertEquals(0, surplus.status.rank)  // good
        assertTrue(surplus.detail.startsWith("+$3,500"))
    }

    @Test
    fun `overspending is flagged bad`() {
        val store = storeWith(
            listOf(
                item("Salary", 1000.0, ItemType.Income),
                item("Housing", 1500.0),
            )
        )
        val surplus = BudgetFindings.compute(store).first { it.id == "surplus" }
        assertEquals(2, surplus.status.rank)  // bad
        assertTrue(surplus.detail.startsWith("−$500"))
    }

    @Test
    fun `emergency fund is honest when nothing is labeled`() {
        val store = storeWith(
            listOf(
                item("Salary", 4000.0, ItemType.Income),
                item("Housing", 1000.0),
            )
        )
        val emergency = BudgetFindings.compute(store).first { it.id == "emergency" }
        assertEquals("unknown", emergency.status.label)
        assertTrue(emergency.fix.contains("We can't see whether you have that"))
    }

    @Test
    fun `emergency fund counts labeled goals only`() {
        val store = storeWith(
            listOf(
                item("Salary", 4000.0, ItemType.Income),
                item("Housing", 1000.0),
            )
        )
        store.goals = listOf(
            SavingsGoal(
                id = "g1", name = "Emergency Fund", targetAmount = 6000.0, currentAmount = 4000.0,
                targetDate = "2027-01-01", monthlyContribution = 200.0,
            ),
            SavingsGoal(
                id = "g2", name = "New car", targetAmount = 20000.0, currentAmount = 9000.0,
                targetDate = "2028-01-01", monthlyContribution = 300.0,
            ),
        )
        val emergency = BudgetFindings.compute(store).first { it.id == "emergency" }
        // 4000 saved / 1000 expenses = 4.0 months — the car goal doesn't count.
        assertTrue(emergency.detail.startsWith("4.0 of 3.0"))
        assertEquals(0, emergency.status.rank)  // good
    }

    @Test
    fun `emergency name matching covers the labeled variants`() {
        assertTrue(BudgetFindings.isEmergencyName("Rainy day fund"))
        assertTrue(BudgetFindings.isEmergencyName("safety net"))
        assertTrue(BudgetFindings.isEmergencyName("Cushion"))
        assertTrue(!BudgetFindings.isEmergencyName("Vacation"))
    }

    @Test
    fun `savings rate uses the fifteen percent guideline`() {
        val store = storeWith(
            listOf(
                item("Salary", 4000.0, ItemType.Income),
                item("Housing", 3000.0),
            )
        )
        val rate = BudgetFindings.compute(store).first { it.id == "savingsRate" }
        // 1000/4000 = 25% → excellent.
        assertEquals("excellent", rate.status.label)
    }

    @Test
    fun `score is weighted and bounded`() {
        val findings = listOf(
            Finding("a", "", "", FindingStatus.Good("good"), "", null, 30.0),
            Finding("b", "", "", FindingStatus.Warn("warn"), "", null, 25.0),
            Finding("c", "", "", FindingStatus.Bad("bad"), "", null, 25.0),
            Finding("d", "", "", FindingStatus.Good("info"), "", null, 0.0),
        )
        // (30 + 12.5 + 0) / 80 = 53%
        assertEquals(53, BudgetFindings.score(findings))
        assertEquals(0, BudgetFindings.score(emptyList()))
    }

    @Test
    fun `summary line counts good against attention`() {
        val good = Finding("a", "", "", FindingStatus.Good("g"), "", null, 0.0)
        val warn = Finding("b", "", "", FindingStatus.Warn("w"), "", null, 0.0)
        assertEquals("All 2 findings look good", BudgetFindings.summaryLine(listOf(good, good)))
        assertEquals("1 finding needs attention", BudgetFindings.summaryLine(listOf(warn)))
        assertEquals("1 looks good, 1 needs attention", BudgetFindings.summaryLine(listOf(good, warn)))
        assertNull(BudgetFindings.summaryLine(emptyList()))
    }

    // MARK: Goal metrics

    @Test
    fun `goal metrics compute needed per month from the date`() {
        val target = LocalDate.now().plusDays(90)
        val goal = SavingsGoal(
            id = "g", name = "Trip", targetAmount = 3000.0, currentAmount = 0.0,
            targetDate = target.toString(), monthlyContribution = 500.0,
        )
        val metrics = GoalMetrics.of(goal, 0.0)
        assertTrue(metrics.hasTargetDate)
        assertEquals(3, metrics.monthsLeft)
        assertEquals(1000.0, metrics.neededPerMonth!!, 0.001)
        assertEquals(0.0, metrics.progress, 0.001)
    }

    @Test
    fun `goal without a date has no needed rate`() {
        val goal = SavingsGoal(
            id = "g", name = "Someday", targetAmount = 1000.0, currentAmount = 250.0,
            targetDate = "", monthlyContribution = 0.0,
        )
        val metrics = GoalMetrics.of(goal, 250.0)
        assertTrue(!metrics.hasTargetDate)
        assertNull(metrics.neededPerMonth)
        assertEquals(25.0, metrics.progress, 0.001)
        assertEquals(750.0, metrics.remaining, 0.001)
    }
}
