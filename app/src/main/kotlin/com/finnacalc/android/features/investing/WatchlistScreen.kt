//
// WatchlistScreen.kt
//
// Port of iOS Features/Investing/DashboardWatchlistView.swift — a card of
// live quote rows (logo · name/ticker · sparkline · price · % change) instead
// of embedded webview charts. Sparklines come from ONE batched request for
// the whole list (/api/sparklines), dodging the per-symbol rate limit.
//
// The symbol list is persisted under the `finnacalc.watchlist` key the app
// shares with the stock-detail "Follow" button.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.FCButtonVariant
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.MarketService

private val defaultSymbols = listOf("AAPL", "TSLA", "NVDA", "MSFT", "AMZN", "META", "GOOGL", "AMD")

@Composable
fun WatchlistCard(onOpenSymbol: (String) -> Unit) {
    var symbols by remember {
        mutableStateOf(WatchlistStore.savedSymbols()?.takeIf { it.isNotEmpty() } ?: defaultSymbols)
    }
    var sparklines by remember { mutableStateOf<Map<String, List<Double>>>(emptyMap()) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    // One batched request covers the whole watchlist.
    LaunchedEffect(symbols) {
        sparklines = if (symbols.isEmpty()) {
            emptyMap()
        } else {
            runCatching { MarketService.sparklines(symbols).sparklines }.getOrDefault(emptyMap())
        }
    }

    fun persist(next: List<String>) {
        symbols = next
        WatchlistStore.save(next)
    }

    FCCard {
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("My Watchlist", style = Theme.sans(18, FontWeight.SemiBold), color = Theme.colors.cardForeground)
                Spacer(Modifier.weight(1f))
                if (!adding) {
                    FCButton("Add", variant = FCButtonVariant.Outline, size = FCButtonSize.Sm) { adding = true }
                }
            }
            if (adding) {
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        FCTextField(
                            "Ticker e.g. AMD", draft, { draft = it.uppercase() },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            showsPlaceholder = true,
                        )
                    }
                    FCButton("Add", size = FCButtonSize.Sm) {
                        val s = draft.trim().uppercase()
                        if (s.isNotEmpty() && s !in symbols) persist(symbols + s)
                        draft = ""
                        adding = false
                    }
                    FCButton("Cancel", variant = FCButtonVariant.Ghost, size = FCButtonSize.Sm) {
                        draft = ""
                        adding = false
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))

        FCCardContent {
            if (symbols.isEmpty()) {
                Text(
                    "Your watchlist is empty. Add a ticker to track it here.",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            } else {
                Column(Modifier.padding(top = 8.dp)) {
                    symbols.forEachIndexed { index, symbol ->
                        WatchlistRow(
                            symbol = symbol,
                            closes = sparklines[symbol] ?: emptyList(),
                            onOpen = { onOpenSymbol(symbol) },
                            onRemove = { persist(symbols.filter { it != symbol }) },
                        )
                        if (index < symbols.size - 1) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistRow(
    symbol: String,
    closes: List<Double>,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var name by remember(symbol) { mutableStateOf("") }
    var logo by remember(symbol) { mutableStateOf("") }
    var price by remember(symbol) { mutableStateOf<Double?>(null) }
    var changePct by remember(symbol) { mutableStateOf(0.0) }
    var loaded by remember(symbol) { mutableStateOf(false) }

    LaunchedEffect(symbol) {
        val r = runCatching { MarketService.stock(symbol) }.getOrNull()
        if (r != null) {
            name = r.overview.name
            logo = r.overview.logo
            price = MarketFormat.parse(r.quote.price)
            changePct = MarketFormat.parse(r.quote.changePercent) ?: 0.0
        }
        loaded = true
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fcPressable(onOpen),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompanyLogo(symbol, logoUrl = logo, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name.ifEmpty { symbol },
                    style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(symbol, style = Theme.sans(Theme.FontSize.xs, FontWeight.Medium), color = Theme.colors.mutedForeground)
            }
            if (closes.size >= 2) {
                SparkLine(closes, changePct >= 0, Modifier.width(46.dp).height(26.dp))
            }
            // Price over a coloured %-change pill, right-aligned.
            val p = price
            when {
                p != null -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        MarketFormat.price(p),
                        style = Theme.figure(Theme.FontSize.base, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                        maxLines = 1,
                    )
                    ChangePill(changePct)
                }
                loaded -> Text("—", style = Theme.figure(Theme.FontSize.base), color = Theme.colors.mutedForeground)
                else -> CircularProgressIndicator(Modifier.size(16.dp), color = Theme.colors.primary, strokeWidth = 2.dp)
            }
        }
        Icon(
            Icons.Default.Close,
            contentDescription = "Remove $symbol",
            tint = Theme.colors.mutedForeground,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(28.dp)
                .clickable(onClick = onRemove)
                .padding(7.dp),
        )
    }
}
