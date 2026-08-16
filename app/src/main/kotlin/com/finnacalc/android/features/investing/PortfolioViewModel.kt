//
// PortfolioViewModel.kt
//
// The state behind the Portfolio page, ported from the plumbing inside iOS
// PortfolioLedgerView.swift: SnapTrade accounts/positions/connections/orders,
// live quotes for the day figure, and the hero curve derived from the
// holdings' own close series.
//
// Two honesty rules from the iOS view carry over verbatim:
//  · The hero curve is Σ(units × closes) trimmed to the shortest series,
//    because no portfolio-history endpoint exists on this data plan. It is
//    clamped to the earliest filled order — otherwise a one-day-old portfolio
//    reports "up 37% past year", a gain the user never had — and the change
//    line only renders when EVERY holding contributed a series, since a
//    curve missing a holding can show a shape but its delta would be
//    fabricated.
//  · The day figure needs a live quote for every holding; missing one falls
//    back to the brokerage's own all-time open P/L, labelled as such, rather
//    than showing a partial "today".
//

package com.finnacalc.android.features.investing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.finnacalc.android.core.market.CandlePoint
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.market.MarketStat
import com.finnacalc.android.core.networking.ApiException
import com.finnacalc.android.core.snaptrade.BrokerageAccount
import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.core.snaptrade.SnapTradeAccountsResponse
import com.finnacalc.android.core.snaptrade.SnapTradeConnection
import com.finnacalc.android.core.snaptrade.SnapTradeOrder
import com.finnacalc.android.core.snaptrade.SnapTradeService
import com.finnacalc.android.core.util.JsonPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

data class PortfolioUiState(
    val loading: Boolean = true,
    val configured: Boolean = true,
    val error: String? = null,
    val accounts: List<BrokerageAccount> = emptyList(),
    val positions: List<BrokeragePosition> = emptyList(),
    val connections: List<SnapTradeConnection> = emptyList(),
    val orders: List<SnapTradeOrder> = emptyList(),
    val currency: String = "USD",
    /** Live quotes per held symbol, for the day figure. */
    val holdingStats: Map<String, MarketStat> = emptyMap(),
    val heroRange: ChartRange = ChartRange.OneDay,
    val heroPoints: List<CandlePoint> = emptyList(),
    val heroLoading: Boolean = false,
    /** True when EVERY held symbol contributed a series to the window. */
    val heroSeriesComplete: Boolean = false,
    /** Set when the curve was clamped to the portfolio's own start. */
    val heroSince: Instant? = null,
    /** SnapTrade declined the last manual holdings sync. */
    val syncUnavailable: Boolean = false,
    val syncing: Boolean = false,
    /** Accounts the user excluded from the portfolio (ids). */
    val deselectedAccounts: Set<String> = emptySet(),
) {
    val hasAccounts: Boolean get() = accounts.isNotEmpty()
    val disabledConnections: List<SnapTradeConnection> get() = connections.filter { it.disabled }

    /** Everything on the page reads the SELECTED accounts only. */
    val selectedAccounts: List<BrokerageAccount>
        get() = accounts.filter { it.id !in deselectedAccounts }

    val visiblePositions: List<BrokeragePosition>
        get() = positions.filter { it.accountId !in deselectedAccounts }

    val visibleOrders: List<SnapTradeOrder>
        get() = orders.filter { it.accountId == null || it.accountId !in deselectedAccounts }

    /**
     * Positions plus anything a filled order proves exists while the daily
     * holdings cache hasn't delivered it yet.
     */
    val analyticsPositions: List<BrokeragePosition>
        get() = visiblePositions + PortfolioAnalytics.provisionalPositions(visibleOrders, visiblePositions)

    val holdings: List<PortfolioAnalytics.Holding>
        get() = PortfolioAnalytics.holdings(analyticsPositions) { holdingStats[it]?.price }

    val totalValue: Double get() = holdings.sumOf { it.value }

    /**
     * Orders still working at the brokerage. Matched loosely so broker-specific
     * spellings land in the right section; canceled variants are excluded even
     * though they may contain a working token, and REPLACED is terminal.
     */
    val pendingOrders: List<SnapTradeOrder>
        get() = visibleOrders.filter { o ->
            val s = o.status?.uppercase() ?: ""
            if (s.contains("CANCEL") || s == "REPLACED") return@filter false
            listOf("PENDING", "ACCEPTED", "NEW", "OPEN", "PARTIAL", "QUEUED", "TRIGGERED", "REPLACE")
                .any { s.contains(it) }
        }

    /**
     * Today's move across the book, or null when any holding is missing a
     * quote — a partial "today" would be a fabricated figure.
     */
    val dayChange: Pair<Double, Double>?
        get() {
            if (holdings.isEmpty()) return null
            var change = 0.0
            for (h in holdings) {
                val stat = holdingStats[h.symbol] ?: return null
                // value / (1 + pct) is yesterday's value for this holding.
                val prior = h.value / (1 + stat.changePct / 100)
                change += h.value - prior
            }
            val base = totalValue - change
            if (base <= 0) return null
            return change to (change / base * 100)
        }

    /** The brokerage's own unrealised P/L — the all-time fallback. */
    val openPnl: Double?
        get() {
            val reported = holdings.mapNotNull { it.openPnl }
            return if (reported.isEmpty()) null else reported.sum()
        }
}

class PortfolioViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        PortfolioUiState(deselectedAccounts = loadDeselected())
    )
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val connections = runCatching { SnapTradeService.connections().connections }.getOrDefault(emptyList())
            val result = runCatching { SnapTradeService.accounts() }
            val data: SnapTradeAccountsResponse? = result.getOrNull()
            if (data == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    connections = connections,
                    error = (result.exceptionOrNull() as? ApiException)?.message,
                )
                return@launch
            }
            _state.value = _state.value.copy(
                loading = false,
                configured = data.configured,
                accounts = data.accounts,
                positions = data.positions,
                currency = data.currency ?: "USD",
                connections = connections,
                error = data.error,
            )
            loadOrders()
            loadQuotes()
            loadHeroSeries()
        }
    }

    private suspend fun loadOrders() {
        val accounts = _state.value.accounts
        val all = accounts.map { account ->
            viewModelScope.async {
                runCatching { SnapTradeService.orders(account.id).orders }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
        // Newest first — timePlaced is ISO-8601, so a string sort is chronological.
        _state.value = _state.value.copy(orders = all.sortedByDescending { it.timePlaced ?: "" })
    }

    private suspend fun loadQuotes() {
        val symbols = _state.value.analyticsPositions.map { it.symbol.uppercase() }.distinct()
        if (symbols.isEmpty()) return
        val stats = runCatching { MarketService.marketStats(symbols).stats }.getOrDefault(emptyList())
        _state.value = _state.value.copy(holdingStats = stats.associateBy { it.symbol.uppercase() })
    }

    fun setRange(range: ChartRange) {
        _state.value = _state.value.copy(heroRange = range)
        viewModelScope.launch { loadHeroSeries() }
    }

    fun setAccountSelected(id: String, selected: Boolean) {
        val next = _state.value.deselectedAccounts.toMutableSet()
        if (selected) next.remove(id) else next.add(id)
        _state.value = _state.value.copy(deselectedAccounts = next)
        JsonPrefs.persist(next.toList(), DESELECTED_KEY)
        viewModelScope.launch {
            loadQuotes()
            loadHeroSeries()
        }
    }

    /** Asks SnapTrade to sync holdings now, then reloads a moment later. */
    fun syncHoldings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true)
            val response = runCatching { SnapTradeService.refresh() }.getOrNull()
            _state.value = _state.value.copy(
                syncing = false,
                // 0 of N means every manual sync was declined (billed add-on /
                // rate limit) — stop promising fresher holdings are coming.
                syncUnavailable = response?.accepted == false,
            )
            if (response?.accepted == true) refresh()
        }
    }

    fun cancelOrder(order: SnapTradeOrder) {
        val accountId = order.accountId ?: return
        val brokerageOrderId = order.brokerageOrderId ?: return
        viewModelScope.launch {
            runCatching { SnapTradeService.cancelOrder(accountId, brokerageOrderId) }
            loadOrders()
        }
    }

    fun disconnect(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { SnapTradeService.disconnect() }
            _state.value = PortfolioUiState(loading = false, deselectedAccounts = _state.value.deselectedAccounts)
            onDone()
        }
    }

    /**
     * The portfolio valued over its holdings' own close series for the
     * selected window (Σ units × closes, trimmed to the shortest series).
     */
    private suspend fun loadHeroSeries() {
        val units = mutableMapOf<String, Double>()
        for (p in _state.value.analyticsPositions) {
            if (p.symbol.isEmpty() || p.units == 0.0) continue
            units[p.symbol.uppercase()] = (units[p.symbol.uppercase()] ?: 0.0) + p.units
        }
        if (units.isEmpty()) {
            _state.value = _state.value.copy(heroPoints = emptyList(), heroSeriesComplete = false, heroSince = null)
            return
        }
        val requested = _state.value.heroRange
        _state.value = _state.value.copy(heroLoading = true)
        val (points, complete) = summedSeries(requested.raw, units)
        // The user may have tapped another pill while these were in flight.
        if (requested != _state.value.heroRange) return

        // The curve only means "your portfolio" back to when it existed.
        var trimmed = points
        var since: Instant? = null
        val start = earliestFill()
        if (start != null && points.firstOrNull()?.t?.let { it < start.epochSecond.toDouble() } == true) {
            since = start
            trimmed = points.filter { it.t >= start.epochSecond.toDouble() }
        }

        _state.value = _state.value.copy(
            heroPoints = trimmed,
            heroSeriesComplete = complete,
            heroSince = since,
            heroLoading = false,
        )
    }

    private suspend fun summedSeries(
        range: String,
        unitsBySymbol: Map<String, Double>,
    ): Pair<List<CandlePoint>, Boolean> {
        val series = unitsBySymbol.keys.map { symbol ->
            viewModelScope.async {
                val points = runCatching { MarketService.candles(symbol, range).points }.getOrNull()
                if (points != null && points.size > 1) symbol to points else null
            }
        }.awaitAll().filterNotNull().toMap()

        val complete = series.size == unitsBySymbol.size
        val n = series.values.minOfOrNull { it.size } ?: 0
        if (n <= 1) return emptyList<CandlePoint>() to complete

        // Timestamps come from one contributing symbol's tail; the series all
        // cover the same window on the same market clock.
        val anchor = series.values.first().takeLast(n)
        val summed = DoubleArray(n)
        for ((symbol, points) in series) {
            val units = unitsBySymbol[symbol] ?: continue
            points.takeLast(n).forEachIndexed { i, point -> summed[i] += point.c * units }
        }
        return anchor.mapIndexed { i, point -> CandlePoint(t = point.t, c = summed[i]) } to complete
    }

    /** When the portfolio began, as proven by the earliest filled order. */
    private fun earliestFill(): Instant? {
        val stamps = _state.value.visibleOrders.mapNotNull { o ->
            val status = (o.status ?: "").uppercase()
            val filled = status in setOf("EXECUTED", "PARTIAL") || (o.filledQuantity ?: 0.0) > 0
            if (filled) o.timePlaced else null
        }
        // ISO-8601, so the min string is the earliest moment.
        val earliest = stamps.minOrNull() ?: return null
        return runCatching { Instant.parse(earliest) }.getOrNull()
    }

    companion object {
        private const val DESELECTED_KEY = "fc.portfolio.deselectedAccounts"

        private fun loadDeselected(): Set<String> =
            JsonPrefs.load<List<String>>(DESELECTED_KEY)?.toSet() ?: emptySet()
    }
}
