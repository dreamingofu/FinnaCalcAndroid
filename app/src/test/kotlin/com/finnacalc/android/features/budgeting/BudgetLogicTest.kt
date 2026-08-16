package com.finnacalc.android.features.budgeting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BudgetLogicTest {

    // MARK: Frequency

    @Test
    fun `frequency multipliers match the web convertToMonthly`() {
        assertEquals(30.0, Frequency.Daily.monthlyMultiplier, 0.0)
        assertEquals(4.33, Frequency.Weekly.monthlyMultiplier, 0.0)
        assertEquals(2.165, Frequency.Biweekly.monthlyMultiplier, 0.0)
        assertEquals(2.0, Frequency.Semimonthly.monthlyMultiplier, 0.0)
        assertEquals(1.0 / 12.0, Frequency.Yearly.monthlyMultiplier, 1e-12)
    }

    @Test
    fun `charge schedule anchor refines biweekly frequency`() {
        // "1st and 3rd Friday" is exactly 2/mo (semimonthly), not 26/yr.
        val paired = ChargeSchedule(ChargeCadence.Biweekly, ChargeAnchor.Weekday(6))
        assertEquals(Frequency.Semimonthly, paired.frequency)
        val stride = ChargeSchedule(ChargeCadence.Biweekly, ChargeAnchor.EveryNDays("2026-01-02"))
        assertEquals(Frequency.Biweekly, stride.frequency)
    }

    // MARK: ChargeDateEngine

    @Test
    fun `monthly day anchor clamps short months`() {
        // "the 31st" in February resolves to the last day of February.
        val schedule = ChargeSchedule(ChargeCadence.Monthly, ChargeAnchor.Day(31))
        val after = LocalDateTime.of(2026, 2, 1, 0, 0)
        val next = ChargeDateEngine.next(schedule, after)!!
        assertEquals(2, next.monthValue)
        assertEquals(28, next.dayOfMonth)
        assertEquals(9, next.hour)
    }

    @Test
    fun `day zero means last day of month`() {
        val schedule = ChargeSchedule(ChargeCadence.Monthly, ChargeAnchor.Day(0))
        val next = ChargeDateEngine.next(schedule, LocalDateTime.of(2026, 4, 10, 0, 0))!!
        assertEquals(30, next.dayOfMonth)
    }

    @Test
    fun `weekly weekday anchor finds next occurrence`() {
        // 2026-08-16 is a Sunday. Next Friday (weekday 6) = 2026-08-21.
        val schedule = ChargeSchedule(ChargeCadence.Weekly, ChargeAnchor.Weekday(6))
        val next = ChargeDateEngine.next(schedule, LocalDateTime.of(2026, 8, 16, 12, 0))!!
        assertEquals(21, next.dayOfMonth)
        assertEquals(8, next.monthValue)
    }

    @Test
    fun `weekly same day after fire hour rolls a week`() {
        // Friday 2026-08-21 at noon: 9am already gone → next Friday.
        val schedule = ChargeSchedule(ChargeCadence.Weekly, ChargeAnchor.Weekday(6))
        val next = ChargeDateEngine.next(schedule, LocalDateTime.of(2026, 8, 21, 12, 0))!!
        assertEquals(28, next.dayOfMonth)
    }

    @Test
    fun `quarterly aligns to the anchor month grid`() {
        // Month 4 (April) grid → Jan/Apr/Jul/Oct. After mid-August → October.
        val schedule = ChargeSchedule(ChargeCadence.Quarterly, ChargeAnchor.Day(15), month = 4)
        val next = ChargeDateEngine.next(schedule, LocalDateTime.of(2026, 8, 16, 0, 0))!!
        assertEquals(10, next.monthValue)
        assertEquals(15, next.dayOfMonth)
    }

    @Test
    fun `every fourteen days strides from start`() {
        val schedule = ChargeSchedule(ChargeCadence.Biweekly, ChargeAnchor.EveryNDays("2026-08-01"))
        val next = ChargeDateEngine.next(schedule, LocalDateTime.of(2026, 8, 16, 0, 0))!!
        // 2026-08-01 + 14 = 2026-08-15 (9am, passed) → +28 = 2026-08-29.
        assertEquals(29, next.dayOfMonth)
    }

    @Test
    fun `paired weeks give first and third weekday`() {
        // Week 1 Friday of Sep 2026 = Sep 4; after Sep 5 → third Friday Sep 18.
        val schedule = ChargeSchedule(ChargeCadence.Biweekly, ChargeAnchor.NthWeekday(1, 6))
        val next = ChargeDateEngine.next(schedule, LocalDateTime.of(2026, 9, 5, 0, 0))!!
        assertEquals(18, next.dayOfMonth)
    }

    // MARK: Categorizer

    @Test
    fun `categorizer maps common merchants`() {
        assertEquals(
            "Food",
            TransactionCategorizer.category("SQ *BLUE BOTTLE COFFEE", ItemType.Expense, BudgetType.Personal),
        )
        assertEquals(
            "Salary",
            TransactionCategorizer.category("ACH PAYROLL DEP", ItemType.Income, BudgetType.Personal),
        )
        // "student loan" beats generic housing/debt ordering.
        assertEquals(
            "Debt Payments",
            TransactionCategorizer.category("NAVIENT STUDENT LOAN PMT", ItemType.Expense, BudgetType.Personal),
        )
        assertEquals(
            "Other",
            TransactionCategorizer.category("XYZZY UNKNOWN", ItemType.Expense, BudgetType.Personal),
        )
        assertEquals(
            "Other Operating Costs",
            TransactionCategorizer.category("XYZZY UNKNOWN", ItemType.Expense, BudgetType.Business),
        )
    }

    // MARK: CSV

    @Test
    fun `csv parser honors quotes and escapes`() {
        val rows = CSVParser.parse("a,\"b,c\",\"say \"\"hi\"\"\"\nd,e,f\n")
        assertEquals(listOf("a", "b,c", "say \"hi\""), rows[0])
        assertEquals(listOf("d", "e", "f"), rows[1])
    }

    @Test
    fun `csv number strips currency chrome`() {
        assertEquals(-1234.56, CSVParser.number("($1,234.56)")!!, 0.0)
        assertEquals(42.0, CSVParser.number("$42")!!, 0.0)
        assertNull(CSVParser.number(""))
    }

    @Test
    fun `statement parser uses debit credit columns`() {
        val csv = """
            Date,Description,Debit,Credit
            2026-08-01,STARBUCKS,5.75,
            2026-08-02,PAYROLL,,2000
        """.trimIndent()
        val items = BankStatementParser.parse(csv, BudgetType.Personal)
        assertEquals(2, items.size)
        assertEquals(ItemType.Expense, items[0].type)
        assertEquals("Food", items[0].category)
        assertEquals(ItemType.Income, items[1].type)
        assertEquals("Salary", items[1].category)
    }

    @Test
    fun `statement parser rejects headerless csv`() {
        try {
            BankStatementParser.parse("Date,Description,Foo\n2026-01-01,x,1", BudgetType.Personal)
            throw AssertionError("expected ParseException")
        } catch (e: BankStatementParser.ParseException) {
            assertTrue(e.message!!.contains("amount column"))
        }
    }

    // MARK: BudgetPeriod

    @Test
    fun `budget period contains works on iso strings`() {
        assertTrue(BudgetPeriod.Month("2026-08").contains("2026-08-16"))
        assertTrue(BudgetPeriod.Range("2026-08-01", "2026-08-31").contains("2026-08-16"))
        assertTrue(!BudgetPeriod.Month("2026-08").contains("2026-09-01"))
        assertEquals("05/26/26 - 07/26/26", BudgetPeriod.Range("2026-05-26", "2026-07-26").label)
    }

    // MARK: Plaid category mapping

    @Test
    fun `plaid category maps primaries`() {
        assertEquals("Food", BudgetStore.plaidCategory("FOOD_AND_DRINK", ItemType.Expense, BudgetType.Personal))
        assertEquals("Salary", BudgetStore.plaidCategory("INCOME_WAGES", ItemType.Income, BudgetType.Personal))
        assertEquals("Other Revenue", BudgetStore.plaidCategory("ANYTHING", ItemType.Income, BudgetType.Business))
        assertEquals("Other", BudgetStore.plaidCategory("UNKNOWN", ItemType.Expense, BudgetType.Personal))
    }

    // MARK: Goal emoji

    @Test
    fun `goal emoji suggests by whole word with plural fold`() {
        assertEquals("🚗", GoalEmoji.suggest("New Car Fund"))
        assertEquals("🚗", GoalEmoji.suggest("Cars"))
        assertEquals("🛟", GoalEmoji.suggest("Emergency fund"))
        assertTrue(GoalEmoji.suggest("Carnival tickets") != "🚗")  // ticket → 🎟️
        assertEquals(GoalEmoji.FALLBACK, GoalEmoji.suggest("zzz"))
    }

    // MARK: Schedule summaries

    @Test
    fun `schedule summaries read plainly`() {
        assertEquals(
            "Monthly on the 15th",
            ChargeSchedule(ChargeCadence.Monthly, ChargeAnchor.Day(15)).summary,
        )
        assertEquals(
            "Every Friday",
            ChargeSchedule(ChargeCadence.Weekly, ChargeAnchor.Weekday(6)).summary,
        )
        assertEquals(
            "1st and 3rd Friday each month",
            ChargeSchedule(ChargeCadence.Biweekly, ChargeAnchor.Weekday(6)).summary,
        )
        assertEquals(
            "Every 3 months from April, on the 15th",
            ChargeSchedule(ChargeCadence.Quarterly, ChargeAnchor.Day(15), month = 4).summary,
        )
    }
}
