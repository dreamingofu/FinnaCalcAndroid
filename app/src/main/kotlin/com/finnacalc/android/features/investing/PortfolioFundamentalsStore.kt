//
// PortfolioFundamentalsStore.kt
//
// Port of iOS Features/Investing/PortfolioFundamentalsStore.swift — loads and
// caches the per-symbol facts the Portfolio analysis cards run on: the name
// and whatever /api/stock still carries, plus a year of closes for volatility.
//
// The caching is not an optimisation, it's the reason this is affordable:
// everything is cached for a day per symbol; failures are stamped too (a
// symbol the market API has nothing for is retried tomorrow, not on every
// appearance); both stages have their own caps, largest holdings first.
// Nothing here fabricates a value — a field that didn't load stays null and
// the card leaves the line out.
//

package com.finnacalc.android.features.investing

import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.util.JsonPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class PortfolioFundamentalsStore private constructor() {

    private val _fundamentals = MutableStateFlow<Map<String, SymbolFundamentals>>(emptyMap())
    val fundamentals: StateFlow<Map<String, SymbolFundamentals>> = _fundamentals.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Per-stage caps, applied to holdings sorted by value. */
    private val basicsLimit = 24
    private val volatilityLimit = 12

    private val ttlMillis = 24L * 60 * 60 * 1000
    private val storageKey = "finnacalc.portfolio.fundamentals"

    /** Concurrent requests in flight — the API is a shared server, not ours to flood. */
    private val concurrency = 4

    /** What we last swept, so re-rendering the page doesn't restart it. */
    private var lastRequest: List<String> = emptyList()
    private var record: MutableMap<String, Cached> =
        (JsonPrefs.load<Map<String, Cached>>(storageKey) ?: emptyMap()).toMutableMap()

    private enum class Stage { Basics, Volatility }

    @Serializable
    data class Cached(
        val value: SymbolFundamentals,
        /** When each stage last ran (epoch millis), successfully or not. */
        val basicsAt: Long? = null,
        val volatilityAt: Long? = null,
    )

    init {
        _fundamentals.value = record.mapValues { it.value.value }
    }

    // MARK: Entry point

    /**
     * Fills in whatever is missing or stale for these symbols, in value
     * order. Safe to call on every appearance: a fully-cached portfolio makes
     * no requests at all.
     */
    fun load(symbols: List<String>, scope: CoroutineScope) {
        val wanted = symbols.map { it.uppercase() }
        if (wanted.isEmpty()) return
        if (wanted == lastRequest && _loading.value) return
        lastRequest = wanted

        val now = System.currentTimeMillis()
        val basics = wanted.take(basicsLimit).filter { isStale(record[it]?.basicsAt, now) }
        val vol = wanted.take(volatilityLimit).filter { isStale(record[it]?.volatilityAt, now) }
        if (basics.isEmpty() && vol.isEmpty()) return

        _loading.value = true
        scope.launch {
            // Sector and beta first: they feed the cards at the top of the
            // section, and the page should stop looking empty as early as it can.
            run(basics, Stage.Basics)
            run(vol, Stage.Volatility)
            JsonPrefs.persist(record.toMap(), storageKey)
            _loading.value = false
        }
    }

    private fun isStale(stamp: Long?, now: Long): Boolean {
        if (stamp == null) return true
        return now - stamp > ttlMillis
    }

    // MARK: Stage runner

    private data class Patch(
        val symbol: String,
        val name: String? = null,
        val sector: String? = null,
        val beta: Double? = null,
        val dividendYield: Double? = null,
        val peRatio: Double? = null,
        val volatility: Double? = null,
    )

    /**
     * Runs one stage over its symbols, `concurrency` at a time, publishing
     * after each batch so the cards fill in as answers arrive.
     */
    private suspend fun run(symbols: List<String>, stage: Stage) {
        if (symbols.isEmpty()) return
        withContext(Dispatchers.IO) {
            symbols.chunked(concurrency).forEach { slice ->
                val patches = slice.map { symbol -> async { fetch(symbol, stage) } }.awaitAll()
                patches.forEach { apply(it, stage) }
                _fundamentals.value = record.mapValues { it.value.value }
            }
        }
    }

    /** Writes a stage's answer in, and stamps that stage whether or not it found anything. */
    @Synchronized
    private fun apply(patch: Patch, stage: Stage) {
        val slot = record[patch.symbol] ?: Cached(SymbolFundamentals(patch.symbol))
        val now = System.currentTimeMillis()
        record[patch.symbol] = when (stage) {
            Stage.Basics -> slot.copy(
                basicsAt = now,
                value = slot.value.copy(
                    name = patch.name ?: slot.value.name,
                    sector = patch.sector ?: slot.value.sector,
                    beta = patch.beta ?: slot.value.beta,
                    dividendYield = patch.dividendYield ?: slot.value.dividendYield,
                    peRatio = patch.peRatio ?: slot.value.peRatio,
                ),
            )
            Stage.Volatility -> slot.copy(
                volatilityAt = now,
                value = slot.value.copy(volatility = patch.volatility ?: slot.value.volatility),
            )
        }
    }

    // MARK: Fetching

    private suspend fun fetch(symbol: String, stage: Stage): Patch = when (stage) {
        Stage.Basics -> {
            runCatching { MarketService.stock(symbol) }.getOrNull()?.let { response ->
                Patch(
                    symbol = symbol,
                    name = response.overview.name,
                    sector = response.company?.sector,
                    beta = response.stats?.beta,
                    dividendYield = response.stats?.dividendYield,
                    peRatio = response.overview.peRatio.toDoubleOrNull(),
                )
            } ?: Patch(symbol)
        }
        Stage.Volatility -> {
            runCatching { MarketService.candles(symbol, "1Y") }.getOrNull()?.let { response ->
                Patch(
                    symbol = symbol,
                    volatility = PortfolioAnalytics.annualisedVolatility(
                        times = response.points.map { it.t },
                        closes = response.points.map { it.c },
                    ),
                )
            } ?: Patch(symbol)
        }
    }

    companion object {
        val shared: PortfolioFundamentalsStore by lazy { PortfolioFundamentalsStore() }
    }
}
