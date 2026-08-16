package com.finnacalc.android.features.investing

import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.core.snaptrade.SnapTradeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PortfolioAnalyticsTest {

    private fun position(
        symbol: String, units: Double, price: Double? = null,
        marketValue: Double? = null, openPnl: Double? = null,
    ) = BrokeragePosition(
        accountId = "a1", symbol = symbol, description = "",
        units = units, price = price, marketValue = marketValue, openPnl = openPnl,
    )

    // MARK: Holdings

    @Test
    fun `holdings collapse duplicate symbols and weight by value`() {
        val holdings = PortfolioAnalytics.holdings(
            listOf(
                position("AAPL", 10.0, marketValue = 2000.0, openPnl = 100.0),
                position("aapl", 5.0, marketValue = 1000.0, openPnl = 50.0),
                position("MSFT", 2.0, marketValue = 1000.0),
            )
        )
        assertEquals(2, holdings.size)
        assertEquals("AAPL", holdings[0].symbol)
        assertEquals(3000.0, holdings[0].value, 0.001)
        assertEquals(0.75, holdings[0].weight, 0.001)
        assertEquals(150.0, holdings[0].openPnl!!, 0.001)
        // MSFT reported no pnl — absent, not zero.
        assertNull(holdings[1].openPnl)
    }

    @Test
    fun `holdings fall back to the live quote when the brokerage sends no value`() {
        val holdings = PortfolioAnalytics.holdings(
            listOf(position("NVDA", 4.0)),
            priceFor = { if (it == "NVDA") 100.0 else null },
        )
        assertEquals(400.0, holdings.single().value, 0.001)
    }

    @Test
    fun `valueless positions are dropped`() {
        assertTrue(PortfolioAnalytics.holdings(listOf(position("XXXX", 3.0))).isEmpty())
    }

    // MARK: Provisional positions from orders

    @Test
    fun `filled buys become provisional positions until holdings cover them`() {
        val orders = listOf(
            SnapTradeOrder(symbol = "TSLA", action = "BUY", status = "EXECUTED", filledQuantity = 2.0, accountId = "a1"),
            SnapTradeOrder(symbol = "TSLA", action = "SELL", status = "EXECUTED", filledQuantity = 0.5),
            SnapTradeOrder(symbol = "AAPL", action = "BUY", status = "EXECUTED", filledQuantity = 1.0),
            SnapTradeOrder(symbol = "PEND", action = "BUY", status = "PENDING"),
        )
        val covered = listOf(position("AAPL", 1.0, marketValue = 200.0))
        val provisional = PortfolioAnalytics.provisionalPositions(orders, covered)
        // AAPL is already held; PEND never filled; TSLA nets 1.5.
        assertEquals(1, provisional.size)
        assertEquals("TSLA", provisional[0].symbol)
        assertEquals(1.5, provisional[0].units, 0.001)
        assertEquals("a1", provisional[0].accountId)
    }

    @Test
    fun `provisional costs poison symbols with unpriced fills`() {
        val orders = listOf(
            SnapTradeOrder(symbol = "GOOD", action = "BUY", status = "EXECUTED", filledQuantity = 2.0, executionPrice = 10.0),
            SnapTradeOrder(symbol = "BAD", action = "BUY", status = "EXECUTED", filledQuantity = 1.0),
        )
        val costs = PortfolioAnalytics.provisionalCosts(orders)
        assertEquals(20.0, costs["GOOD"]!!, 0.001)
        assertNull(costs["BAD"])
    }

    // MARK: Diversification

    @Test
    fun `effective count reads concentration`() {
        // Four equal slices → 4 effective holdings.
        assertEquals(4.0, PortfolioAnalytics.effectiveCount(listOf(0.25, 0.25, 0.25, 0.25))!!, 0.001)
        // 70/10/10/10 → about 1.9.
        assertEquals(1.923, PortfolioAnalytics.effectiveCount(listOf(0.7, 0.1, 0.1, 0.1))!!, 0.01)
    }

    @Test
    fun `diversification scores holdings alone when sectors are unknown`() {
        val holdings = (1..15).map {
            PortfolioAnalytics.Holding("S$it", 100.0, 1.0 / 15, null)
        }
        val d = PortfolioAnalytics.diversification(holdings, null)!!
        assertEquals(100, d.score)
        assertNull(d.effectiveSectors)
    }

    @Test
    fun `single holding scores zero`() {
        val d = PortfolioAnalytics.diversification(
            listOf(PortfolioAnalytics.Holding("AAPL", 100.0, 1.0, null)), null,
        )!!
        assertEquals(0, d.score)
        assertEquals("Concentrated", PortfolioAnalytics.diversificationLabel(d.score))
    }

    // MARK: Types

    @Test
    fun `type breakdown separates stocks from known ETFs`() {
        val holdings = listOf(
            PortfolioAnalytics.Holding("SPY", 600.0, 0.6, null),
            PortfolioAnalytics.Holding("AAPL", 400.0, 0.4, null),
        )
        val types = PortfolioAnalytics.types(holdings)!!
        assertEquals(1.0, types.coverage, 0.0)
        assertEquals("ETFs", types.slices[0].name)
        assertEquals(0.6, types.slices[0].weight, 0.001)
    }

    // MARK: Risk

    @Test
    fun `weighted beta renormalises over covered holdings`() {
        val holdings = listOf(
            PortfolioAnalytics.Holding("A", 500.0, 0.5, null),
            PortfolioAnalytics.Holding("B", 500.0, 0.5, null),
        )
        val fundamentals = mapOf(
            "A" to SymbolFundamentals("A", beta = 2.0),
            // B reports no beta.
        )
        val risk = PortfolioAnalytics.risk(holdings, fundamentals)
        // Renormalised: beta 2.0 over the covered half, coverage 0.5.
        assertEquals(2.0, risk.beta!!, 0.001)
        assertEquals(0.5, risk.betaCoverage, 0.001)
    }

    // MARK: Dividends

    @Test
    fun `dividends project forward from yields`() {
        val holdings = listOf(
            PortfolioAnalytics.Holding("SCHD", 10_000.0, 0.5, null),
            PortfolioAnalytics.Holding("GROW", 10_000.0, 0.5, null),
        )
        val fundamentals = mapOf("SCHD" to SymbolFundamentals("SCHD", dividendYield = 3.5))
        val picture = PortfolioAnalytics.dividends(holdings, fundamentals)!!
        assertEquals(350.0, picture.annual, 0.001)
        assertEquals(1.75, picture.portfolioYield, 0.001)  // over the WHOLE book
        assertEquals(0.5, picture.payerWeight, 0.001)
    }

    @Test
    fun `no payers means no picture rather than zeros`() {
        val holdings = listOf(PortfolioAnalytics.Holding("GROW", 100.0, 1.0, null))
        assertNull(PortfolioAnalytics.dividends(holdings, emptyMap()))
    }

    // MARK: Tax

    @Test
    fun `tax splits winners from losers and nets the estimate`() {
        val holdings = listOf(
            PortfolioAnalytics.Holding("WIN", 1000.0, 0.5, 400.0),
            PortfolioAnalytics.Holding("LOSE", 1000.0, 0.5, -150.0),
        )
        val picture = PortfolioAnalytics.tax(holdings)!!
        assertEquals(400.0, picture.gains, 0.001)
        assertEquals(150.0, picture.losses, 0.001)
        assertEquals(250.0, picture.net, 0.001)
        assertEquals(1.0, picture.coverage, 0.001)
        // 15% long-term rate on the net.
        assertEquals(37.5, PortfolioAnalytics.estimatedTax(picture, 15.0), 0.001)
        // A net loss owes nothing.
        val allLoss = PortfolioAnalytics.tax(
            listOf(PortfolioAnalytics.Holding("L", 100.0, 1.0, -50.0)),
        )!!
        assertEquals(0.0, PortfolioAnalytics.estimatedTax(allLoss, 15.0), 0.001)
    }

    // MARK: Volatility

    @Test
    fun `volatility needs enough points and real spread`() {
        assertNull(PortfolioAnalytics.annualisedVolatility(listOf(1.0, 2.0), listOf(10.0, 11.0)))

        // A year of daily closes with a gentle oscillation → a finite figure.
        val days = 252
        val times = (0 until days).map { it * 86_400.0 * 365.25 / 252 }
        val closes = (0 until days).map { 100.0 + 5 * sin(2 * PI * it / 20) }
        val vol = PortfolioAnalytics.annualisedVolatility(times, closes)
        assertNotNull(vol)
        assertTrue(vol!! > 0)

        // A flat series has zero variance → null, not zero.
        assertNull(PortfolioAnalytics.annualisedVolatility(times, List(days) { 100.0 }))
    }
}
