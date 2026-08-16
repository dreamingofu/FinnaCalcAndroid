//
// CompanyLogo.kt
//
// Port of iOS Features/Investing/CompanyLogoView.swift — a circular company
// mark keyed by ticker, resolved in order: a logo the API supplied, then
// Brandfetch's ticker CDN, then Logo.dev's, then a monogram tinted per symbol.
// Two providers because their catalogues differ; the second covers names the
// first has never heard of.
//
// Both are licensed for commercial use. Logo.dev's free tier requires a
// visible credit, which lives in the About page and under the screener list —
// see Logos.kt before adding, moving or removing that source.
//
// The monogram is a real state, not dead code: both sources are asked for a
// 404 on a miss precisely so an unknown ticker falls through to ours instead
// of a stranger's grey placeholder. It also covers offline.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.market.BrandLogo
import com.finnacalc.android.core.market.LogoDev
import com.finnacalc.android.features.budgeting.BudgetCategoryStyle

@Composable
fun CompanyLogo(
    symbol: String,
    modifier: Modifier = Modifier,
    /** A logo URL the API supplied, when it has one; it outranks the CDNs. */
    logoUrl: String = "",
    size: Dp = 48.dp,
) {
    val density = LocalDensity.current.density
    val sources = remember(symbol, logoUrl, size, density) {
        listOfNotNull(
            logoUrl.takeIf { it.isNotEmpty() },
            BrandLogo.ticker(symbol, size.value, density),
            LogoDev.ticker(symbol, size.value, density),
        )
    }
    // Walks the chain: each 404 advances to the next source, and running off
    // the end lands on our own monogram.
    var attempt by remember(symbol, logoUrl) { mutableIntStateOf(0) }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (attempt < sources.size) {
            SubcomposeAsyncImage(
                model = sources[attempt],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.White),
                error = { attempt += 1 },
                loading = { Monogram(symbol, size) },
            )
        } else {
            Monogram(symbol, size)
        }
    }
}

/**
 * Stable tint per symbol. Summed scalars, not hashCode: a seeded hash would
 * recolour every list on a cold start.
 */
internal fun logoTint(symbol: String): Color {
    val palette = BudgetCategoryStyle.chartPalette
    val ticker = symbol.uppercase()
    if (ticker.isEmpty()) return palette[0]
    var code = 0
    for (ch in ticker) code = (code * 31 + ch.code) and 0xFFFF
    return palette[code % palette.size]
}

@Composable
private fun Monogram(symbol: String, size: Dp) {
    val ticker = symbol.uppercase()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(logoTint(symbol)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ticker.take(if (ticker.length > 3) 1 else 2),
            style = Theme.sans((size.value * 0.36f).toInt().coerceAtLeast(8), FontWeight.Bold),
            color = Color.White,
        )
    }
}
