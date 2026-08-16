package com.finnacalc.android.features.budgeting

import com.finnacalc.android.core.util.JsonPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advisor snapshot is what leaves the device and what the report cache is
 * keyed on, so both its content and its byte-stability matter.
 */
class AdvisorSnapshotTest {

    private fun storeWith(items: List<BudgetItem>, goals: List<SavingsGoal> = emptyList()): BudgetStore {
        JsonPrefs.resetForTesting()
        val store = BudgetStore()
        store.items = items
        store.goals = goals
        store.history = emptyList()
        return store
    }

    private fun item(
        category: String,
        amount: Double,
        type: ItemType = ItemType.Expense,
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

    private val basicBudget = listOf(
        item("Salary", 5_000.0, ItemType.Income),
        item("Housing", 1_800.0),
        item("Food", 600.0),
    )

    // MARK: - Figures

    @Test
    fun `headline figures round the way the web does`() {
        val s = buildAdvisorSnapshot(storeWith(basicBudget))
        assertEquals(5_000, s.monthlyIncome)
        assertEquals(2_400, s.monthlyExpenses)
        assertEquals(2_600, s.monthlyNet)
        // Net over income, not the Savings-category rate: 2600/5000 = 52%.
        assertEquals(52.0, s.savingsRatePct, 0.001)
    }

    @Test
    fun `expense slices carry their share of income, largest first`() {
        val s = buildAdvisorSnapshot(storeWith(basicBudget))
        assertEquals(listOf("Housing", "Food"), s.expenseByCategory.map { it.category })
        assertEquals(36.0, s.expenseByCategory[0].pctOfIncome!!, 0.001)
        assertEquals(12.0, s.expenseByCategory[1].pctOfIncome!!, 0.001)
    }

    @Test
    fun `share of income is omitted rather than divided by zero`() {
        val s = buildAdvisorSnapshot(storeWith(listOf(item("Housing", 900.0))))
        assertNull(s.expenseByCategory.single().pctOfIncome)
        assertEquals(0.0, s.savingsRatePct, 0.001)
    }

    @Test
    fun `line items keep their description and are capped at forty`() {
        val many = (1..45).map { item("Food", it.toDouble(), subcategory = "Line $it") }
        val s = buildAdvisorSnapshot(storeWith(many))
        assertEquals(40, s.lineItems.size)
        // Largest first: line 45 leads.
        assertEquals("Line 45", s.lineItems.first().name)
        assertTrue(s.dataNotes.any { it.contains("40 largest lines of 45") })
    }

    @Test
    fun `a line without a description falls back to its category`() {
        val s = buildAdvisorSnapshot(storeWith(listOf(item("Housing", 1_200.0))))
        assertEquals("Housing", s.lineItems.single().name)
    }

    // MARK: - dataNotes (what the model must not assume)

    @Test
    fun `an unlabeled emergency fund is reported as unknown, not absent`() {
        val s = buildAdvisorSnapshot(storeWith(basicBudget))
        assertEquals(0.0, s.emergencyFundMonthsCovered, 0.001)
        assertTrue(
            "the zero must be explained as unknown",
            s.dataNotes.any { it.contains("Whether an emergency fund exists is unknown") },
        )
    }

    @Test
    fun `a hand-typed budget says so, and says what it cannot see`() {
        val s = buildAdvisorSnapshot(storeWith(basicBudget))
        assertTrue(s.dataNotes.any { it.contains("hand-typed budget") && it.contains("no linked bank") })
    }

    @Test
    fun `no comparison window means the model is told not to claim a trend`() {
        val s = buildAdvisorSnapshot(storeWith(basicBudget))
        assertTrue(s.categoryChanges.isEmpty())
        assertTrue(s.dataNotes.any { it.contains("do not claim spending rose or fell") })
    }

    @Test
    fun `an emergency contribution without a visible balance is flagged`() {
        val withContribution = basicBudget + item("Savings", 300.0, subcategory = "Emergency fund")
        val s = buildAdvisorSnapshot(storeWith(withContribution))
        assertTrue(s.dataNotes.any { it.contains("do not claim the user has no emergency fund") })
    }

    @Test
    fun `the standing disclaimer is declared so replies do not repeat it`() {
        val s = buildAdvisorSnapshot(storeWith(basicBudget))
        assertTrue(s.dataNotes.any { it.contains("Do not add a disclaimer") })
        assertTrue(s.dataNotes.any { it.contains("normalised to per-month amounts") })
    }

    // MARK: - Cache signature

    @Test
    fun `the same budget produces the same signature`() {
        val a = AdvisorCacheStore.signature(buildAdvisorSnapshot(storeWith(basicBudget)))
        val b = AdvisorCacheStore.signature(buildAdvisorSnapshot(storeWith(basicBudget.reversed())))
        // Order-shuffled input must not re-run the model: the snapshot sorts.
        assertEquals(a, b)
        assertFalse(a.isEmpty())
    }

    @Test
    fun `changing the budget changes the signature`() {
        val a = AdvisorCacheStore.signature(buildAdvisorSnapshot(storeWith(basicBudget)))
        val b = AdvisorCacheStore.signature(
            buildAdvisorSnapshot(storeWith(basicBudget + item("Transportation", 200.0)))
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `a cached report only comes back for its own signature`() {
        JsonPrefs.resetForTesting()
        val messages = listOf(
            AdvisorCacheStore.StoredMessage("m1", "user", "analyse it", true),
            AdvisorCacheStore.StoredMessage("m2", "assistant", "Rent is 36% of income.", false),
        )
        AdvisorCacheStore.save("sig-a", messages, "quick")

        val hit = AdvisorCacheStore.load("sig-a")
        assertEquals(2, hit!!.first.size)
        assertEquals("quick", hit.second)
        assertNull(AdvisorCacheStore.load("sig-b"))
    }
}
