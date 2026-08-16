//
// Logos.kt
//
// Ports of iOS Core/Market/{BrandLogo,LogoDev}.swift — company logos from
// Brandfetch's Logo Link CDN (primary) and Logo.dev (second source).
//
// Licensing: Brandfetch's Logo API is free, permits commercial use, and asks
// for no attribution; its client ID is a PUBLIC embed key that rides in the
// URL by design. Logo.dev's free tier REQUIRES a visible attribution link —
// that credit lives in the About page and under the screener list; if this
// source is ever dropped, drop those lines with it.
//
// Both use `fallback=404`: a miss 404s so the app's own tinted monogram stays
// the last word, instead of a vendor placeholder pretending to be a logo.
//

package com.finnacalc.android.core.market

import kotlin.math.ceil

object BrandLogo {
    /** Public embed key from the Brandfetch developer dashboard. */
    private const val CLIENT_ID = "1idsFuoxxIb4DvxlMNa"
    private const val HOST = "https://cdn.brandfetch.io"

    /** Pixel sizes we ask for, so the CDN caches a handful of variants. */
    private val buckets = listOf(64, 128, 256, 512)

    fun pixels(points: Float, density: Float): Int {
        val needed = ceil(points * density).toInt()
        return buckets.firstOrNull { it >= needed } ?: buckets.last()
    }

    private fun url(kind: String, id: String, points: Float, density: Float): String? {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return null
        val side = pixels(points, density)
        return "$HOST/$kind/$trimmed/w/$side/h/$side/fallback/404?c=$CLIENT_ID"
    }

    /** Logo for a stock or ETF ticker (AAPL, SPY, BRK-B — hyphens are fine). */
    fun ticker(symbol: String, points: Float, density: Float): String? =
        url("ticker", symbol.uppercase(), points, density)

    /** Logo for an organisation's website. */
    fun domain(domain: String, points: Float, density: Float): String? =
        url("domain", cleanHost(domain), points, density)

    internal fun cleanHost(domain: String): String {
        var host = domain.lowercase()
        // Strip a scheme if one was passed.
        host = host.substringAfter("://")
        host = host.substringBefore("/")
        if (host.startsWith("www.")) host = host.drop(4)
        return host
    }
}

object LogoDev {
    /** Publishable key (pk_) — safe in client URLs, grants only logo reads. */
    private const val TOKEN = "pk_ZTbfU6kERKSqyUQorOEXog"
    private const val HOST = "https://img.logo.dev"

    private fun url(path: String, points: Float, density: Float): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null
        val size = BrandLogo.pixels(points, density)
        return "$HOST/$trimmed?token=$TOKEN&size=$size&format=png&fallback=404"
    }

    fun ticker(symbol: String, points: Float, density: Float): String? =
        url("ticker/${symbol.uppercase()}", points, density)

    fun domain(domain: String, points: Float, density: Float): String? =
        url(BrandLogo.cleanHost(domain), points, density)

    /** The credit the free tier requires, in one place. */
    const val ATTRIBUTION_TEXT = "Logos by Logo.dev"
    const val ATTRIBUTION_URL = "https://logo.dev"
}
