package com.finnacalc.android.features.investing

import com.finnacalc.android.core.market.MarketStat
import com.finnacalc.android.core.snaptrade.BrokerageAccount
import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.core.snaptrade.SnapTradeConnection
import com.finnacalc.android.core.snaptrade.SnapTradeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioUiStateTest {

    private fun account(id: String) = BrokerageAccount(
        id = id, name = "Acct $id", institution = "Test Broker", number = "1234", currency = "USD",
    )

    private fun position(symbol: String, accountId: String, value: Double, pnl: Double? = null) =
        BrokeragePosition(
            accountId = accountId, symbol = symbol, description = "", units = 1.0,
            marketValue = value, openPnl = pnl,
        )

    // MARK: Account selection

    @Test
    fun `deselecting an account removes it from every figure`() {
        val state = PortfolioUiState(
            accounts = listOf(account("a"), account("b")),
            positions = listOf(position("AAPL", "a", 1000.0), position("MSFT", "b", 500.0)),
            deselectedAccounts = setOf("b"),
        )
        assertEquals(listOf("a"), state.selectedAccounts.map { it.id })
        assertEquals(1, state.visiblePositions.size)
        assertEquals(1000.0, state.totalValue, 0.001)
    }

    @Test
    fun `orders with no account stay visible`() {
        val state = PortfolioUiState(
            accounts = listOf(account("a")),
            orders = listOf(
                SnapTradeOrder(symbol = "AAPL", accountId = null),
                SnapTradeOrder(symbol = "MSFT", accountId = "b"),
            ),
            deselectedAccounts = setOf("b"),
        )
        assertEquals(listOf("AAPL"), state.visibleOrders.map { it.symbol })
    }

    // MARK: Pending orders

    @Test
    fun `pending orders match working statuses and exclude terminal ones`() {
        val state = PortfolioUiState(
            orders = listOf(
                SnapTradeOrder(brokerageOrderId = "1", status = "PENDING_NEW", symbol = "A"),
                SnapTradeOrder(brokerageOrderId = "2", status = "PARTIALLY_FILLED", symbol = "B"),
                SnapTradeOrder(brokerageOrderId = "3", status = "EXECUTED", symbol = "C"),
                SnapTradeOrder(brokerageOrderId = "4", status = "CANCELED", symbol = "D"),
                // REPLACED is terminal even though it contains the REPLACE token.
                SnapTradeOrder(brokerageOrderId = "5", status = "REPLACED", symbol = "E"),
                SnapTradeOrder(brokerageOrderId = "6", status = "PENDING_CANCEL", symbol = "F"),
            )
        )
        assertEquals(listOf("A", "B"), state.pendingOrders.map { it.symbol })
    }

    // MARK: Day change

    @Test
    fun `day change needs a quote for every holding`() {
        val state = PortfolioUiState(
            accounts = listOf(account("a")),
            positions = listOf(position("AAPL", "a", 1000.0), position("MSFT", "a", 1000.0)),
            holdingStats = mapOf("AAPL" to MarketStat("AAPL", null, 100.0, 2.0)),
        )
        // MSFT has no quote — a partial "today" would be fabricated.
        assertNull(state.dayChange)
    }

    @Test
    fun `day change sums each holding's move`() {
        val state = PortfolioUiState(
            accounts = listOf(account("a")),
            positions = listOf(position("AAPL", "a", 1100.0)),
            holdingStats = mapOf("AAPL" to MarketStat("AAPL", null, 110.0, 10.0)),
        )
        val (change, pct) = state.dayChange!!
        // 1100 now, 1000 yesterday.
        assertEquals(100.0, change, 0.001)
        assertEquals(10.0, pct, 0.001)
    }

    @Test
    fun `open pnl is the all-time fallback and absent when unreported`() {
        val withPnl = PortfolioUiState(
            accounts = listOf(account("a")),
            positions = listOf(position("AAPL", "a", 1000.0, pnl = 120.0), position("MSFT", "a", 500.0, pnl = -20.0)),
        )
        assertEquals(100.0, withPnl.openPnl!!, 0.001)

        val without = PortfolioUiState(
            accounts = listOf(account("a")),
            positions = listOf(position("AAPL", "a", 1000.0)),
        )
        assertNull(without.openPnl)
    }

    // MARK: Analytics positions

    @Test
    fun `analytics positions include order-derived holdings`() {
        val state = PortfolioUiState(
            accounts = listOf(account("a")),
            positions = listOf(position("AAPL", "a", 1000.0)),
            orders = listOf(
                SnapTradeOrder(
                    brokerageOrderId = "1", status = "EXECUTED", symbol = "TSLA",
                    action = "BUY", filledQuantity = 3.0, accountId = "a",
                )
            ),
        )
        val symbols = state.analyticsPositions.map { it.symbol }
        assertTrue(symbols.contains("AAPL"))
        assertTrue(symbols.contains("TSLA"))
    }

    // MARK: Connections

    @Test
    fun `disabled connections surface for reconnect`() {
        val state = PortfolioUiState(
            connections = listOf(
                SnapTradeConnection("c1", "Webull", disabled = false, type = "trade"),
                SnapTradeConnection("c2", "Schwab", disabled = true, type = "read"),
            )
        )
        assertEquals(listOf("Schwab"), state.disabledConnections.map { it.brokerage })
    }

    @Test
    fun `trading availability distinguishes view-only from unsupported`() {
        val viewOnly = SnapTradeConnection("c", "Webull", disabled = false, type = "read", allowsTrading = true)
        assertEquals(SnapTradeConnection.TradingAvailability.ConnectionIsViewOnly, viewOnly.tradingAvailability)
        assertTrue(viewOnly.tradingBlockedReason!!.contains("Enable trading asks"))

        val unsupported = SnapTradeConnection("c", "Vanguard", disabled = false, type = "read", allowsTrading = false)
        assertEquals(SnapTradeConnection.TradingAvailability.BrokerageUnsupported, unsupported.tradingAvailability)
        assertTrue(unsupported.tradingBlockedReason!!.contains("doesn't support placing orders"))

        val allowed = SnapTradeConnection("c", "Webull", disabled = false, type = "trade")
        assertEquals(SnapTradeConnection.TradingAvailability.Allowed, allowed.tradingAvailability)
        assertNull(allowed.tradingBlockedReason)

        // An older backend sends neither field — never block on a guess.
        val unknown = SnapTradeConnection("c", "Webull", disabled = false)
        assertEquals(SnapTradeConnection.TradingAvailability.Unknown, unknown.tradingAvailability)
        assertNull(unknown.tradingBlockedReason)
    }
}
