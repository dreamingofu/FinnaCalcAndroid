//
// WatchlistStore.kt
//
// Port of iOS Features/Investing/WatchlistStore.swift — thin helper over the
// `finnacalc.watchlist` stored array, so the stock-detail "Follow" button can
// add/remove a symbol and stay in sync with the watchlist card.
//

package com.finnacalc.android.features.investing

import com.finnacalc.android.core.util.JsonPrefs

object WatchlistStore {
    private const val STORAGE_KEY = "finnacalc.watchlist"

    fun symbols(): List<String> = savedSymbols() ?: emptyList()

    /**
     * null when the user has never saved a watchlist (callers may substitute
     * defaults); an empty list means they deliberately cleared it.
     */
    fun savedSymbols(): List<String>? = JsonPrefs.load(STORAGE_KEY)

    fun save(symbols: List<String>) = JsonPrefs.persist(symbols, STORAGE_KEY)

    fun contains(symbol: String): Boolean =
        symbols().any { it.equals(symbol, ignoreCase = true) }

    /** Toggle membership; returns the new followed state. */
    fun toggle(symbol: String): Boolean {
        val list = symbols().toMutableList()
        val index = list.indexOfFirst { it.equals(symbol, ignoreCase = true) }
        return if (index >= 0) {
            list.removeAt(index)
            save(list)
            false
        } else {
            list.add(symbol.uppercase())
            save(list)
            true
        }
    }
}
