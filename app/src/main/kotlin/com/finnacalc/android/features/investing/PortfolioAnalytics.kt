//
// PortfolioAnalytics.kt
//
// Port of iOS Features/Investing/PortfolioAnalytics.swift — the math behind
// the Portfolio page's analysis cards. Pure functions over the brokerage
// positions we already hold plus per-symbol fundamentals, so every figure on
// screen traces back to real data.
//
// What is deliberately NOT here, because no endpoint we have supplies it:
// dividends actually received, realized gains, fund expense ratios, insider
// filings. Coverage is reported wherever a figure is built from a subset — a
// weighted beta across 60% of the portfolio is a different claim from one
// across all of it, and the card has to say which.
//

package com.finnacalc.android.features.investing

import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.core.snaptrade.SnapTradeOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

// MARK: - Per-symbol inputs

/**
 * The slow-moving facts about a symbol: what it is, how it moves, what it
 * pays. One /api/stock call each, cached for a day.
 */
@Serializable
data class SymbolFundamentals(
    val symbol: String,
    val name: String? = null,
    val sector: String? = null,
    /** Sensitivity to the market. 1.0 moves with it, 1.6 swings 60% harder. */
    val beta: Double? = null,
    /** Annual dividend as a percent of price, e.g. 0.52 == 0.52%. */
    val dividendYield: Double? = null,
    val peRatio: Double? = null,
    /** Annualised stddev of daily returns, %, from the past year of closes. */
    val volatility: Double? = null,
)

// MARK: - Engine

object PortfolioAnalytics {

    // MARK: Weights

    data class Holding(
        val symbol: String,
        val value: Double,
        /** Share of the portfolio's invested value, 0…1. */
        val weight: Double,
        val openPnl: Double?,
    )

    /**
     * Positions collapsed to one row per symbol (the same stock held in two
     * accounts is one bet, not two) and weighted by market value. `priceFor`
     * is a fallback, not a preference: a brokerage that reports a value is
     * authoritative about its own account — but a freshly placed order looks
     * like a null marketValue, and without the fallback a whole portfolio
     * could vanish from these cards.
     */
    fun holdings(
        positions: List<BrokeragePosition>,
        priceFor: (String) -> Double? = { null },
    ): List<Holding> {
        val value = mutableMapOf<String, Double>()
        // Absent rather than zero when nothing reported one, so "flat" and
        // "we don't know the cost basis" stay distinguishable downstream.
        val pnl = mutableMapOf<String, Double>()
        for (p in positions) {
            val key = p.symbol.uppercase()
            val quoted = p.marketValue
                ?: p.price?.let { it * p.units }
                ?: priceFor(key)?.let { it * p.units }
            if (quoted == null || quoted <= 0) continue
            value[key] = (value[key] ?: 0.0) + quoted
            p.openPnl?.let { pnl[key] = (pnl[key] ?: 0.0) + it }
        }
        val total = value.values.sum()
        if (total <= 0) return emptyList()
        return value
            .map { (symbol, v) -> Holding(symbol, v, v / total, pnl[symbol]) }
            .sortedByDescending { it.value }
    }

    /**
     * Positions a filled order proves exist while the daily holdings cache
     * hasn't delivered them yet. Units are net filled buys minus sells per
     * symbol; symbols the cache covers are skipped so nothing double-counts
     * once the real position lands. Prices stay null — the quote fallback in
     * [holdings] values them honestly.
     */
    fun provisionalPositions(
        orders: List<SnapTradeOrder>,
        coveredBy: List<BrokeragePosition>,
    ): List<BrokeragePosition> {
        val held = coveredBy.map { it.symbol.uppercase() }.toSet()
        val units = mutableMapOf<String, Double>()
        val account = mutableMapOf<String, String?>()
        for (o in orders) {
            val symbol = o.symbol?.uppercase()?.takeIf { it.isNotEmpty() } ?: continue
            if (symbol in held) continue
            val status = (o.status ?: "").uppercase()
            val filled = status in setOf("EXECUTED", "PARTIAL") || (o.filledQuantity ?: 0.0) > 0
            if (!filled) continue
            // For a partial fill, filledQuantity is the truth; totalQuantity
            // would claim shares that never traded.
            val qty = o.filledQuantity?.takeIf { it > 0 } ?: o.totalQuantity ?: 0.0
            if (qty <= 0) continue
            val action = (o.action ?: "").uppercase()
            when {
                action.startsWith("BUY") -> units[symbol] = (units[symbol] ?: 0.0) + qty
                action.startsWith("SELL") -> units[symbol] = (units[symbol] ?: 0.0) - qty
            }
            if (account[symbol] == null) account[symbol] = o.accountId
        }
        return units.mapNotNull { (symbol, qty) ->
            if (qty <= 0) return@mapNotNull null
            BrokeragePosition(
                accountId = account[symbol] ?: "",
                symbol = symbol,
                description = "",
                units = qty,
            )
        }
    }

    /**
     * Net filled cost per symbol from the orders: buys add, sells remove. A
     * filled order with no execution price poisons its symbol (the basis
     * would be a guess), so that symbol is simply absent from the result.
     */
    fun provisionalCosts(orders: List<SnapTradeOrder>): Map<String, Double> {
        val cost = mutableMapOf<String, Double>()
        val poisoned = mutableSetOf<String>()
        for (o in orders) {
            val symbol = o.symbol?.uppercase()?.takeIf { it.isNotEmpty() } ?: continue
            val status = (o.status ?: "").uppercase()
            val filled = status in setOf("EXECUTED", "PARTIAL") || (o.filledQuantity ?: 0.0) > 0
            if (!filled) continue
            val qty = o.filledQuantity?.takeIf { it > 0 } ?: o.totalQuantity ?: 0.0
            if (qty <= 0) continue
            val px = o.executionPrice
            if (px == null) {
                poisoned.add(symbol)
                continue
            }
            val action = (o.action ?: "").uppercase()
            when {
                action.startsWith("BUY") -> cost[symbol] = (cost[symbol] ?: 0.0) + qty * px
                action.startsWith("SELL") -> cost[symbol] = (cost[symbol] ?: 0.0) - qty * px
            }
        }
        poisoned.forEach { cost.remove(it) }
        return cost.filterValues { it > 0 }
    }

    // MARK: Diversification

    data class Diversification(
        /** 0…100, from how evenly the money sits across holdings and sectors. */
        val score: Int,
        /** 1/HHI: how many equally-sized holdings the spread is worth. */
        val effectiveHoldings: Double,
        val effectiveSectors: Double?,
        val topSymbol: String,
        val topWeight: Double,
        /** Share of value in the largest three positions. */
        val topThreeWeight: Double,
        val holdingCount: Int,
    )

    /** Herfindahl index: 1.0 for everything in one place, 1/n for n equal slices. */
    fun hhi(weights: List<Double>): Double = weights.sumOf { it * it }

    /** The reciprocal of the HHI — "how many positions is this really". */
    fun effectiveCount(weights: List<Double>): Double? {
        val index = hhi(weights)
        if (index <= 0) return null
        return 1 / index
    }

    fun diversification(
        holdings: List<Holding>,
        sectorWeights: List<Double>?,
    ): Diversification? {
        val first = holdings.firstOrNull() ?: return null
        val effHoldings = effectiveCount(holdings.map { it.weight }) ?: return null

        // 15 equally-weighted holdings and 6 sectors are treated as a full
        // mark. Past that the extra spread stops changing the answer to "is
        // one bad day going to hurt".
        val holdingPart = ((effHoldings - 1) / 14).coerceIn(0.0, 1.0)
        val effSectors = sectorWeights?.let { effectiveCount(it) }
        val score = if (effSectors != null) {
            val sectorPart = ((effSectors - 1) / 5).coerceIn(0.0, 1.0)
            (100 * (0.6 * holdingPart + 0.4 * sectorPart)).roundToInt()
        } else {
            // No sector coverage: score on holdings alone rather than dock
            // the user 40 points for data we couldn't fetch.
            (100 * holdingPart).roundToInt()
        }

        return Diversification(
            score = score,
            effectiveHoldings = effHoldings,
            effectiveSectors = effSectors,
            topSymbol = first.symbol,
            topWeight = first.weight,
            topThreeWeight = holdings.take(3).sumOf { it.weight },
            holdingCount = holdings.size,
        )
    }

    /** The one-word read on a score. */
    fun diversificationLabel(score: Int): String = when {
        score < 35 -> "Concentrated"
        score < 60 -> "Narrow"
        score < 80 -> "Reasonably spread"
        else -> "Well spread"
    }

    // MARK: Sectors

    data class SectorSlice(
        val name: String,
        val value: Double,
        /** Share of the *covered* value, 0…1, so the slices sum to 100%. */
        val weight: Double,
        val symbols: List<String>,
    )

    data class SectorBreakdown(
        val slices: List<SectorSlice>,
        /** Share of the portfolio whose sector we know, 0…1. */
        val coverage: Double,
        val uncoveredValue: Double,
    )

    /**
     * Widely held funds, for splitting the book by TYPE. A curated list
     * rather than a guess off missing fundamentals: a stock with no sector on
     * file must not get promoted to "ETF" by absence of data.
     */
    val knownETFs: Set<String> = setOf(
        "SPY", "VOO", "IVV", "VTI", "QQQ", "QQQM", "DIA", "IWM", "VT", "VXUS",
        "VEA", "VWO", "SCHD", "JEPI", "JEPQ", "VYM", "VIG", "DGRO", "SGOV",
        "BND", "AGG", "TLT", "IEF", "SHY", "LQD", "HYG", "MUB", "BIL",
        "GLD", "IAU", "SLV", "ARKK", "ARKW", "SMH", "SOXX", "XLK", "XLF",
        "XLE", "XLV", "XLY", "XLP", "XLI", "XLB", "XLU", "XLRE", "XLC",
        "VNQ", "SCHB", "SCHX", "SPLG", "RSP", "MOAT", "COWZ", "VGT", "VUG",
        "VTV", "AVUV", "QUAL", "MTUM", "USMV", "EFA", "EEM", "IBIT", "FBTC",
    )

    /**
     * The same donut, cut by what each holding IS (stock, ETF). Coverage is
     * always 1 here: every holding lands in a slice.
     */
    fun types(holdings: List<Holding>): SectorBreakdown? {
        if (holdings.isEmpty()) return null
        val total = holdings.sumOf { it.value }
        if (total <= 0) return null
        val buckets = mutableMapOf<String, Pair<Double, MutableList<String>>>()
        for (h in holdings) {
            val name = if (h.symbol.uppercase() in knownETFs) "ETFs" else "Stocks"
            val entry = buckets[name] ?: (0.0 to mutableListOf())
            buckets[name] = (entry.first + h.value) to entry.second.also { it.add(h.symbol) }
        }
        val slices = buckets
            .map { (name, entry) -> SectorSlice(name, entry.first, entry.first / total, entry.second) }
            .sortedByDescending { it.value }
        return SectorBreakdown(slices, coverage = 1.0, uncoveredValue = 0.0)
    }

    fun sectors(
        holdings: List<Holding>,
        fundamentals: Map<String, SymbolFundamentals>,
    ): SectorBreakdown? {
        if (holdings.isEmpty()) return null
        val total = holdings.sumOf { it.value }
        val byName = mutableMapOf<String, Pair<Double, MutableList<String>>>()
        var uncovered = 0.0
        for (h in holdings) {
            val sector = fundamentals[h.symbol]?.sector?.trim()
            if (sector.isNullOrEmpty()) {
                uncovered += h.value
                continue
            }
            val entry = byName[sector] ?: (0.0 to mutableListOf())
            byName[sector] = (entry.first + h.value) to entry.second.also { it.add(h.symbol) }
        }
        val covered = total - uncovered
        if (covered <= 0) return null
        val slices = byName
            .map { (name, entry) -> SectorSlice(name, entry.first, entry.first / covered, entry.second) }
            .sortedByDescending { it.value }
        return SectorBreakdown(slices, coverage = covered / total, uncoveredValue = uncovered)
    }

    // MARK: Risk

    data class Risk(
        /** Value-weighted beta across the holdings that report one. */
        val beta: Double?,
        val betaCoverage: Double,
        /** Value-weighted annualised volatility, in percent. */
        val volatility: Double?,
        val volatilityCoverage: Double,
    )

    /**
     * Weighted average of a per-symbol figure, renormalised over the holdings
     * that actually have it. Otherwise a portfolio where only half the names
     * report a beta would read as half as volatile as it is.
     */
    fun weightedAverage(
        holdings: List<Holding>,
        value: (String) -> Double?,
    ): Pair<Double, Double>? {
        var sum = 0.0
        var weight = 0.0
        for (h in holdings) {
            val v = value(h.symbol) ?: continue
            sum += v * h.weight
            weight += h.weight
        }
        if (weight <= 0) return null
        return (sum / weight) to weight
    }

    fun risk(
        holdings: List<Holding>,
        fundamentals: Map<String, SymbolFundamentals>,
    ): Risk {
        val beta = weightedAverage(holdings) { fundamentals[it]?.beta }
        val vol = weightedAverage(holdings) { fundamentals[it]?.volatility }
        return Risk(
            beta = beta?.first, betaCoverage = beta?.second ?: 0.0,
            volatility = vol?.first, volatilityCoverage = vol?.second ?: 0.0,
        )
    }

    /** Plain-language read on a portfolio beta. */
    fun betaLabel(beta: Double): String = when {
        beta < 0.8 -> "Steadier than the market"
        beta < 1.05 -> "Moves with the market"
        beta < 1.3 -> "Swings harder than the market"
        else -> "Much sharper than the market"
    }

    // MARK: Dividends

    data class DividendRow(
        val symbol: String,
        val annual: Double,
        val yield: Double,
    )

    data class DividendPicture(
        val annual: Double,
        val monthly: Double,
        /** Income as a percent of the whole portfolio, payers and non-payers. */
        val portfolioYield: Double,
        val rows: List<DividendRow>,
        /** Share of value in something that pays anything at all. */
        val payerWeight: Double,
    )

    /**
     * Forward income at today's yields: what the portfolio would pay over the
     * next year if the yields and the holdings both stay put. Not a promise
     * of a payment, and the card says as much.
     */
    fun dividends(
        holdings: List<Holding>,
        fundamentals: Map<String, SymbolFundamentals>,
    ): DividendPicture? {
        val total = holdings.sumOf { it.value }
        if (total <= 0) return null
        val rows = mutableListOf<DividendRow>()
        var payerValue = 0.0
        for (h in holdings) {
            val y = fundamentals[h.symbol]?.dividendYield?.takeIf { it > 0 } ?: continue
            rows.add(DividendRow(h.symbol, h.value * y / 100, y))
            payerValue += h.value
        }
        if (rows.isEmpty()) return null
        val annual = rows.sumOf { it.annual }
        return DividendPicture(
            annual = annual,
            monthly = annual / 12,
            portfolioYield = annual / total * 100,
            rows = rows.sortedByDescending { it.annual },
            payerWeight = payerValue / total,
        )
    }

    // MARK: Tax

    data class TaxLot(
        val symbol: String,
        val openPnl: Double,
        val value: Double,
    )

    data class TaxPicture(
        val gains: Double,
        val losses: Double,          // positive number
        val winners: List<TaxLot>,
        val losers: List<TaxLot>,
        /** Share of value in positions that report a cost basis at all. */
        val coverage: Double,
    ) {
        val net: Double get() = gains - losses
    }

    /**
     * Splits the open book into what is up and what is down. `openPnl` is
     * the brokerage's own unrealised figure, so the cost basis is theirs,
     * not one we guessed.
     */
    fun tax(holdings: List<Holding>): TaxPicture? {
        var gains = 0.0
        var losses = 0.0
        val winners = mutableListOf<TaxLot>()
        val losers = mutableListOf<TaxLot>()
        var covered = 0.0
        val total = holdings.sumOf { it.value }
        for (h in holdings) {
            val pnl = h.openPnl ?: continue
            covered += h.value
            val lot = TaxLot(h.symbol, pnl, h.value)
            if (pnl >= 0) {
                gains += pnl
                winners.add(lot)
            } else {
                losses += -pnl
                losers.add(lot)
            }
        }
        if (covered <= 0) return null
        return TaxPicture(
            gains = gains, losses = losses,
            winners = winners.sortedByDescending { it.openPnl },
            losers = losers.sortedBy { it.openPnl },
            coverage = if (total > 0) covered / total else 0.0,
        )
    }

    /**
     * Tax on the open gains if every winner were sold today, at the chosen
     * long-term rate, after offsetting losses. Federal only, assumes every
     * lot held over a year. Both facts are stated on the card.
     */
    fun estimatedTax(picture: TaxPicture, rate: Double): Double =
        max(0.0, picture.net) * rate / 100

    // MARK: Volatility

    /**
     * Annualised standard deviation of log returns, in percent. The sampling
     * rate comes from the timestamps rather than being assumed daily, so a
     * weekly series annualises correctly too. Returns null below 40 points:
     * a stddev off a handful of closes is noise wearing a number's clothes.
     */
    fun annualisedVolatility(times: List<Double>, closes: List<Double>): Double? {
        if (closes.size < 40 || times.size != closes.size) return null
        val returns = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            if (closes[i] <= 0 || closes[i - 1] <= 0) continue
            returns.add(ln(closes[i] / closes[i - 1]))
        }
        if (returns.size < 30) return null
        val mean = returns.sum() / returns.size
        val variance = returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1)
        if (variance <= 0) return null

        val span = (times.lastOrNull() ?: 0.0) - (times.firstOrNull() ?: 0.0)
        if (span <= 0) return null
        val years = span / (365.25 * 86_400)
        if (years <= 0.05) return null
        val periodsPerYear = returns.size / years
        return sqrt(variance) * sqrt(periodsPerYear) * 100
    }
}
