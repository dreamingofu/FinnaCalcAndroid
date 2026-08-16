package com.finnacalc.android.features.calculators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcLogicTest {

    // MARK: calcValue parsing

    @Test
    fun `calcValue strips grouping and symbols`() {
        assertEquals(3200.0, "3,200".calcValue, 0.0)
        assertEquals(1234.56, "$1,234.56".calcValue, 0.0)
        assertEquals(-42.0, "-42".calcValue, 0.0)
        assertEquals(0.0, "".calcValue, 0.0)
        assertEquals(0.0, "abc".calcValue, 0.0)
    }

    @Test
    fun `formatCurrencyTyping groups and clamps decimals`() {
        assertEquals("1,234", formatCurrencyTyping("1234"))
        assertEquals("1,234.56", formatCurrencyTyping("1234.567"))
        assertEquals("0.5", formatCurrencyTyping(".5"))
        assertEquals("1,000", formatCurrencyTyping("01000"))
        assertEquals("", formatCurrencyTyping(""))
    }

    // MARK: Loan

    @Test
    fun `loan payment mode matches amortization formula`() {
        // $50,000 at 6% over 60 months → $966.64/mo
        val results = LoanCalc.results(
            amount = 50_000.0, rate = 6.0, term = 60.0,
            paymentsMade = 0.0, payment = 0.0, mode = LoanCalc.Mode.Payment,
        )
        assertNotNull(results)
        assertEquals("Monthly payment", results!![0].label)
        assertEquals("$966.64", results[0].value)
    }

    @Test
    fun `loan zero rate splits evenly`() {
        val results = LoanCalc.results(
            amount = 12_000.0, rate = 0.0, term = 12.0,
            paymentsMade = 0.0, payment = 0.0, mode = LoanCalc.Mode.Payment,
        )
        assertEquals("$1,000.00", results!![0].value)
    }

    @Test
    fun `solveRate recovers the rate that generated the payment`() {
        // Round-trip: the payment at 6%/60mo solves back to ~6% APR.
        val apr = LoanCalc.solveRate(50_000.0, 966.64, 60.0)
        assertNotNull(apr)
        assertEquals(6.0, apr!!, 0.01)
    }

    @Test
    fun `solveRate returns zero for exact repayment and null when payments never repay`() {
        assertEquals(0.0, LoanCalc.solveRate(12_000.0, 1_000.0, 12.0)!!, 0.0001)
        assertNull(LoanCalc.solveRate(50_000.0, 100.0, 60.0))
    }

    @Test
    fun `loan remaining mode clamps to zero`() {
        val results = LoanCalc.results(
            amount = 10_000.0, rate = 5.0, term = 24.0,
            paymentsMade = 24.0, payment = 0.0, mode = LoanCalc.Mode.Remaining,
        )
        assertEquals("$0.00", results!![1].value)
    }

    // MARK: Emergency fund

    @Test
    fun `emergency fund months to goal iterates with APY`() {
        val results = EmergencyFundCalc.results(
            monthlyExpenses = 1_000.0, currentSavings = 0.0,
            targetType = "months", months = 6.0, dollarAmount = 0.0,
            contribution = 1_000.0, apy = 0.0,
        )
        assertEquals("$6,000", results!![0].value)
        assertEquals("6 mo", results[2].value)
    }

    @Test
    fun `emergency fund without contribution never reaches goal`() {
        val results = EmergencyFundCalc.results(
            monthlyExpenses = 1_000.0, currentSavings = 100.0,
            targetType = "months", months = 6.0, dollarAmount = 0.0,
            contribution = 0.0, apy = 4.0,
        )
        assertEquals("Add a contribution", results!![2].value)
    }

    // MARK: Break-even

    @Test
    fun `break even rounds units up`() {
        val results = BreakEvenCalc.results(
            fixedCosts = 10_000.0, variableCost = 6.0, price = 10.0,
            businessType = "single", targetMargin = 0.0, seasonality = 0.0,
        )
        // 10000 / 4 = 2500 units
        assertEquals("2,500 units", results!![0].value)
        assertEquals("$25,000", results[1].value)
    }

    @Test
    fun `break even returns null when contribution margin non-positive`() {
        assertNull(
            BreakEvenCalc.results(
                fixedCosts = 10_000.0, variableCost = 12.0, price = 10.0,
                businessType = "single", targetMargin = 0.0, seasonality = 0.0,
            )
        )
    }

    // MARK: ROI

    @Test
    fun `roi annualizes over the period`() {
        val results = RoiCalc.results(
            initial = 10_000.0, final = 20_000.0, years = 3.0,
            inflation = 0.0, taxRate = 0.0,
        )
        assertEquals("100.0%", results!![0].value)
        // 2^(1/3) - 1 = 25.99%
        assertEquals("26.0%", results[1].value)
    }

    // MARK: Compound interest

    @Test
    fun `compound interest grows monthly`() {
        val results = CompoundInterestCalc.results(
            initialDeposit = 10_000.0, monthlyContribution = 0.0,
            annualRate = 12.0, years = 1.0,
        )
        // 10,000 * 1.01^12 = 11,268.25
        assertEquals("$11,268", results!![0].value)
    }

    // MARK: Profit margin

    @Test
    fun `profit margin computes three tiers`() {
        val results = ProfitMarginCalc.results(
            revenue = 100_000.0, cogs = 40_000.0, opex = 30_000.0, taxRate = 20.0,
        )
        assertEquals("24.0%", results!![0].value)  // net: 30k * 0.8 / 100k
        assertEquals("60.0%", results[1].value)    // gross
        assertEquals("30.0%", results[2].value)    // operating
        assertEquals("$24,000", results[3].value)
    }

    // MARK: Formatting guards

    @Test
    fun `non-finite figures render em dash`() {
        assertEquals("—", CalcFmt.currency(Double.NaN))
        assertEquals("—", CalcFmt.percent(Double.POSITIVE_INFINITY))
        assertTrue(CalcFmt.currency(1234.5, 2).startsWith("$1,234"))
    }
}
