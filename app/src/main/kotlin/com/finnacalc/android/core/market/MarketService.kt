//
// MarketService.kt
//
// Port of iOS Core/Market/MarketService.swift — market-data API calls
// (Alpaca-backed routes on the FinnaCalc server).
//

package com.finnacalc.android.core.market

import com.finnacalc.android.core.networking.ApiClient

object MarketService {
    suspend fun stock(symbol: String): StockResponse =
        ApiClient.shared.getJson("/api/stock", mapOf("symbol" to symbol))

    suspend fun search(keywords: String): List<StockSearchResult> =
        ApiClient.shared.getJson("/api/stock-search", mapOf("keywords" to keywords))

    /**
     * Screener over Alpaca's most-active universe. `query` carries only what
     * the route screens on — price and volume bounds, and a limit.
     */
    suspend fun screener(query: Map<String, String> = emptyMap()): ScreenerResponse =
        ApiClient.shared.getJson("/api/screener", query)

    suspend fun topMovers(): TopMoversResponse =
        ApiClient.shared.getJson("/api/top-movers")

    /**
     * Price + day-change for a few symbols in ONE call (the Home tab's
     * "Markets today" row) — deliberately not N /api/stock calls against a
     * shared, rate-limited API.
     */
    suspend fun marketStats(symbols: List<String>): MarketStatsResponse =
        ApiClient.shared.getJson("/api/market-stats", mapOf("symbols" to symbols.joinToString(",")))

    suspend fun marketOverview(): MarketOverviewResponse =
        ApiClient.shared.getJson("/api/market-overview")

    /** Price history for the native line chart. `range` is 1D/1W/1M/1Y/ALL. */
    suspend fun candles(symbol: String, range: String, interval: String? = null): CandlesResponse {
        val query = mutableMapOf("symbol" to symbol, "range" to range)
        if (interval != null) query["interval"] = interval
        return ApiClient.shared.getJson("/api/candles", query)
    }

    // MARK: Detail sections (empty payloads hide the UI)
    //
    // No earnings calendar or analyst ratings: those needed a fundamentals
    // vendor, and Alpaca — the only market-data provider — serves prices.

    suspend fun news(symbol: String): NewsResponse =
        ApiClient.shared.getJson("/api/news", mapOf("symbol" to symbol))

    /** General market news for the discover landing. */
    suspend fun marketNews(): MarketNewsResponse =
        ApiClient.shared.getJson("/api/market-news")

    suspend fun financials(symbol: String): FinancialsResponse =
        ApiClient.shared.getJson("/api/financials", mapOf("symbol" to symbol))

    /**
     * Ten years of statements from SEC filings. Empty for ETFs and anything
     * that doesn't file with the SEC; the section hides itself on empty.
     */
    suspend fun statements(symbol: String): StatementsResponse =
        ApiClient.shared.getJson("/api/statements", mapOf("symbol" to symbol))

    /** A person's Form 4 transactions, newest first (free SEC data). */
    suspend fun insiderTrades(cik: String): InsiderTradesResponse =
        ApiClient.shared.getJson("/api/insider-trades", mapOf("cik" to cik))

    /** Batched sparkline closes for a set of symbols (one request for the watchlist). */
    suspend fun sparklines(symbols: List<String>): SparklinesResponse =
        ApiClient.shared.getJson("/api/sparklines", mapOf("symbols" to symbols.joinToString(",")))
}
