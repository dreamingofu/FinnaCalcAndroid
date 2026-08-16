//
// MarketModels.kt
//
// Port of iOS Core/Market/MarketModels.swift — Codable mirrors of the
// market-data routes, all of which run on Alpaca (the only market-data
// provider). Alpaca prices what trades; it publishes no fundamentals, so the
// profile/valuation/ownership fields come back null today. They stay declared
// and optional, and every section that reads one hides itself when it's null,
// so nothing on screen is ever a guess.
//

package com.finnacalc.android.core.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

// MARK: - /api/stock?symbol=

@Serializable
data class StockQuoteFields(
    @SerialName("01. symbol") val symbol: String,
    @SerialName("05. price") val price: String,
    @SerialName("09. change") val change: String,
    @SerialName("10. change percent") val changePercent: String,
)

@Serializable
data class StockOverviewFields(
    @SerialName("Name") val name: String,
    @SerialName("MarketCapitalization") val marketCapitalization: String,
    @SerialName("Description") val description: String,
    @SerialName("Logo") val logo: String,
    @SerialName("PERatio") val peRatio: String,
)

/**
 * Extended stats. All optional: only the 52-week window is derived from
 * Alpaca bars today. Every reader hides its line on null.
 */
@Serializable
data class StockStatsFields(
    val high52: Double? = null,
    val low52: Double? = null,
    val beta: Double? = null,
    val epsTTM: Double? = null,
    val dividendYield: Double? = null,
    val netMargin: Double? = null,
    val revenueGrowth: Double? = null,
    val grossMargin: Double? = null,
    /** Millions of shares (Finnhub convention). */
    val sharesOutstanding: Double? = null,
)

/** Company facts. Only `exchange` is populated from Alpaca's asset record. */
@Serializable
data class StockCompanyFields(
    val exchange: String? = null,
    val industry: String? = null,
    val sector: String? = null,
    val ceo: String? = null,
    val employees: String? = null,
    val ipo: String? = null,
    val website: String? = null,
    val country: String? = null,
)

/** A retired ticker redirected to the symbol that replaced it. */
@Serializable
data class SymbolAlias(
    /** The retired symbol the user asked for. */
    val from: String,
    /** The live symbol actually being shown. */
    val to: String,
    /** "renamed" or "acquired". */
    val kind: String,
    /** Ready-to-display explanation, e.g. "Paramount Global now trades as PSKY." */
    val note: String,
)

@Serializable
data class StockResponse(
    val quote: StockQuoteFields,
    val overview: StockOverviewFields,
    val stats: StockStatsFields? = null,
    val company: StockCompanyFields? = null,
    val alias: SymbolAlias? = null,
)

// MARK: - /api/stock-search?keywords=

@Serializable
data class StockSearchResult(
    @SerialName("1. symbol") val symbol: String,
    @SerialName("2. name") val name: String,
    @SerialName("4. region") val region: String,
)

// MARK: - /api/screener

/**
 * One screened symbol. Every field is measured — a snapshot's price and
 * move, today's bar, and volume against the recent session average.
 */
@Serializable
data class ScreenerRow(
    val symbol: String,
    val company: String,
    val exchange: String,
    val price: Double,
    val change: Double? = null,
    val changePct: Double? = null,
    val volume: Double? = null,
    val avgVolume: Double? = null,
    /** Today's volume over the recent session average; 1.0 is a normal day. */
    val relVolume: Double? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val prevClose: Double? = null,
) {
    /**
     * Where today's price sits between the session's low and high, 0…1.
     * null unless the range is real, so the bar is never drawn from a guess.
     */
    val dayRangePosition: Double?
        get() {
            val low = dayLow ?: return null
            val high = dayHigh ?: return null
            if (high <= low) return null
            return ((price - low) / (high - low)).coerceIn(0.0, 1.0)
        }
}

/** Which list the screener draws from. Alpaca's own screener endpoints. */
@Serializable
enum class ScreenerPreset(val raw: String) {
    @SerialName("actives") Actives("actives"),
    @SerialName("gainers") Gainers("gainers"),
    @SerialName("losers") Losers("losers");

    val title: String
        get() = when (this) {
            Actives -> "Most active"
            Gainers -> "Gainers"
            Losers -> "Losers"
        }

    /** What the list actually is, said plainly under the results. */
    val blurb: String
        get() = when (this) {
            Actives -> "The day's most-traded US stocks, by share volume."
            Gainers -> "The day's biggest percentage gainers."
            Losers -> "The day's biggest percentage losers."
        }
}

@Serializable
data class ScreenerResponse(
    val rows: List<ScreenerRow>,
    val preset: ScreenerPreset? = null,
    val universeSize: Int? = null,
    val asOf: String? = null,
    /** Filters the route was asked for but can't run — decoded so a stray one is seen. */
    val unsupported: List<String>? = null,
    val error: String? = null,
)

// MARK: - /api/top-movers

@Serializable
data class Mover(
    val symbol: String,
    val name: String,
    val price: Double,
    val change: Double,
    val changesPercentage: Double,
)

@Serializable
data class TopMoversResponse(
    val topGainers: List<Mover>,
    val topLosers: List<Mover>,
)

// MARK: - /api/market-overview

@Serializable
data class MarketQuote(
    val symbol: String,
    val name: String,
    val sector: String,
    val sectorColor: String,
    val price: Double,
    val change: Double,
    val changesPercentage: Double,
    val high: Double,
    val low: Double,
    val open: Double,
    val previousClose: Double,
    val logo: String,
)

@Serializable
data class SectorSummary(
    val id: String,
    val name: String,
    val color: String,
    val avgChange: Double,
    val stockCount: Int,
)

@Serializable
data class MarketOverviewResponse(
    val stocks: List<MarketQuote>,
    val gainers: List<MarketQuote>,
    val losers: List<MarketQuote>,
    val mostActive: List<MarketQuote>,
    val sectorSummary: List<SectorSummary>,
    val timestamp: Double,
)

// MARK: - /api/market-stats?symbols=

/**
 * One instrument's price + day change for the Home "Markets today" row.
 * Symbols the providers couldn't resolve are omitted from the response, so
 * the row shows "—" for them rather than a fabricated figure.
 */
@Serializable
data class MarketStat(
    val symbol: String,
    val name: String? = null,
    val price: Double,
    val changePct: Double,
)

@Serializable
data class MarketStatsResponse(val stats: List<MarketStat>)

// MARK: - /api/candles?symbol=&range=

/** One price sample: `t` = epoch seconds, `c` = closing price. */
@Serializable
data class CandlePoint(
    val t: Double,
    val c: Double,
    val o: Double? = null,
    val h: Double? = null,
    val l: Double? = null,
) {
    /**
     * The bar's real epoch instant. The candles API encodes the exchange's
     * wall clock as if it were UTC: a 9:30 AM New York open arrives as
     * 13:30Z read-as-9:30. Shifting by New York's offset at that instant
     * recovers the true epoch; format the result in the device zone.
     */
    val localInstant: Instant
        get() {
            val exchange = ZoneId.of("America/New_York")
            val asUtc = Instant.ofEpochSecond(t.toLong())
            val offset = exchange.rules.getOffset(asUtc).totalSeconds
            return Instant.ofEpochSecond(t.toLong() - offset)
        }
}

@Serializable
data class CandlesResponse(
    /** The symbol actually charted — differs when a retired ticker was resolved. */
    val symbol: String,
    val range: String,
    val points: List<CandlePoint>,
)

// MARK: - /api/insider-trades

/** One Form 4 transaction, already classified by the backend. */
@Serializable
data class InsiderTrade(
    val date: String,
    val filedAt: String? = null,
    val symbol: String? = null,
    val issuerName: String? = null,
    val role: String? = null,
    /** SEC transaction code: P purchase, S sale, A award, M exercise, F withheld, G gift. */
    val code: String,
    /** Plain-language version of `code` ("Bought", "Withheld for taxes"). */
    val label: String,
    /** True only for open-market buys and sells. */
    val discretionary: Boolean,
    val acquired: Boolean,
    val shares: Double,
    /** null on grants and exercises, which carry no purchase price. */
    val price: Double? = null,
    val value: Double? = null,
    val sharesAfter: Double? = null,
    val url: String? = null,
)

@Serializable
data class InsiderTradesResponse(
    val cik: String,
    val name: String? = null,
    val trades: List<InsiderTrade>,
)

// MARK: - /api/news?symbol=

@Serializable
data class NewsArticle(
    val id: String,
    val headline: String,
    val source: String,
    val url: String,
    val image: String,
    val datetime: Double? = null,    // epoch seconds
    val summary: String,
)

@Serializable
data class NewsResponse(
    val symbol: String,
    val articles: List<NewsArticle>,
)

@Serializable
data class MarketNewsResponse(val articles: List<NewsArticle>)

// MARK: - /api/financials?symbol=

@Serializable
data class FinancialPeriod(
    val year: Int? = null,
    val quarter: Int? = null,
    val revenue: Double,
    val netProfit: Double,
) {
    /** "2024" for annual, "Q1 '24" for quarterly. */
    val label: String
        get() {
            val y = year ?: return "—"
            val q = quarter
            if (q != null && q > 0) return "Q$q '${y.toString().takeLast(2)}"
            return y.toString()
        }
}

@Serializable
data class FinancialsResponse(
    val symbol: String,
    val annual: List<FinancialPeriod>,
    val quarterly: List<FinancialPeriod>,
)

// MARK: - /api/statements?symbol=

@Serializable
data class StatementsResponse(
    val symbol: String,
    val companyName: String? = null,
    val fiscalYearEndMonth: Int? = null,
    val years: List<Int>,
    val statements: List<Statement>,
) {
    @Serializable
    data class Statement(val name: String, val rows: List<Row>)

    @Serializable
    data class Row(val label: String, val values: List<Double?>)
}

// MARK: - /api/sparklines?symbols=

@Serializable
data class SparklinesResponse(
    /** Closing prices per symbol, oldest → newest. */
    val sparklines: Map<String, List<Double>>,
)
