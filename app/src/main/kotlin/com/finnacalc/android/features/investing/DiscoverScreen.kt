//
// DiscoverScreen.kt
//
// Port of iOS Features/Investing/StocksDiscoverView.swift (+ the highlight
// carousel from MarketsDashboardView) — the Investing tab's Discover landing:
// a swipeable carousel of highlight cards (Market / Biggest Movers / Most
// Active / Biggest Losers), a market-news row, and a grid of category tiles.
//
// Every figure comes from /api/market-overview, /api/market-stats and
// /api/market-news. Nothing is shown while a value is unknown — a dash or an
// omitted card instead, matching the iOS rule.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.MarketOverviewResponse
import com.finnacalc.android.core.market.MarketQuote
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.market.MarketStat
import com.finnacalc.android.core.market.NewsArticle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiscoverScreen(
    onOpenSymbol: (String) -> Unit,
    onOpenSector: (SectorMeta) -> Unit,
) {
    var overview by remember { mutableStateOf<MarketOverviewResponse?>(null) }
    var indexStats by remember { mutableStateOf<List<MarketStat>>(emptyList()) }
    var news by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // SPY / QQQ / IWM come from the same cached market-stats route the
        // Home dashboard uses, so this adds no new data source.
        indexStats = runCatching { MarketService.marketStats(listOf("SPY", "QQQ", "IWM")).stats }
            .getOrDefault(emptyList())
        val result = runCatching { MarketService.marketOverview() }
        overview = result.getOrNull()
        if (result.isFailure) error = "Couldn't load the market right now."
        loading = false
        news = runCatching { MarketService.marketNews().articles }.getOrDefault(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        when {
            loading && overview == null -> Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Theme.colors.primary) }

            error != null && overview == null -> Text(
                error!!,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.destructive,
            )

            else -> overview?.let { data ->
                HighlightCarousel(data, indexStats, onOpenSymbol)
                if (news.isNotEmpty()) NewsSection(news)
                CategoriesSection(onOpenSector)
            }
        }
    }
}

// MARK: - Highlight carousel

@Composable
private fun HighlightCarousel(
    overview: MarketOverviewResponse,
    indexStats: List<MarketStat>,
    onOpenSymbol: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (indexStats.isNotEmpty()) {
            HighlightCard("Market", "Where the big index ETFs sit today") {
                indexStats.forEach { stat ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stat.symbol, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                            // Honest labels: SPY is the S&P 500 ETF, never
                            // "the index" itself.
                            Text(
                                etfLabel(stat.symbol, stat.name),
                                style = Theme.sans(10),
                                color = Theme.colors.mutedForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            MarketFormat.price(stat.price),
                            style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        Spacer(Modifier.width(8.dp))
                        ChangePill(stat.changePct)
                    }
                }
            }
        }
        HighlightCard("Biggest movers", "Today's largest percentage gains") {
            overview.gainers.take(4).forEach { QuoteLine(it, onOpenSymbol) }
        }
        HighlightCard("Most active", "The day's most-traded names") {
            overview.mostActive.take(4).forEach { QuoteLine(it, onOpenSymbol) }
        }
        HighlightCard("Biggest losers", "Today's largest percentage falls") {
            overview.losers.take(4).forEach { QuoteLine(it, onOpenSymbol) }
        }
    }
}

/**
 * ETFs are labelled as ETFs. SPY tracks the S&P 500 and QQQ the Nasdaq-100
 * (not the Composite) — the app never calls an ETF an index.
 */
private fun etfLabel(symbol: String, name: String?): String = when (symbol.uppercase()) {
    "SPY" -> "S&P 500 ETF"
    "QQQ" -> "Nasdaq 100 ETF"
    "IWM" -> "Russell 2000 ETF"
    else -> name ?: symbol
}

@Composable
private fun HighlightCard(title: String, blurb: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(280.dp)
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
        Text(blurb, style = Theme.sans(11), color = Theme.colors.mutedForeground)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun QuoteLine(quote: MarketQuote, onOpenSymbol: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .fcPressable { onOpenSymbol(quote.symbol) }
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompanyLogo(quote.symbol, logoUrl = quote.logo, size = 28.dp)
        Column(Modifier.weight(1f)) {
            Text(quote.symbol, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
            Text(
                quote.name,
                style = Theme.sans(10),
                color = Theme.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ChangePill(quote.changesPercentage)
    }
}

// MARK: - News

@Composable
fun NewsSection(articles: List<NewsArticle>, title: String = "News") {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = Theme.sans(Theme.FontSize.xl2, FontWeight.Bold), color = Theme.colors.foreground)
        articles.take(6).forEach { article ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .fcPressable { uriHandler.openUri(article.url) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (article.image.isNotEmpty()) {
                    AsyncImage(
                        model = article.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(Theme.Radius.md))
                            .background(Theme.colors.secondary),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        article.headline,
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold).copy(lineHeight = 19.sp),
                        color = Theme.colors.foreground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(article.source.takeIf { it.isNotEmpty() }, article.datetime?.let(::relativeTime))
                            .joinToString(" · "),
                        style = Theme.sans(11),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }
    }
}

private fun relativeTime(epochSeconds: Double): String {
    val then = Instant.ofEpochSecond(epochSeconds.toLong())
    val minutes = (Instant.now().epochSecond - then.epochSecond) / 60
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.US).withZone(ZoneId.systemDefault()).format(then)
    }
}

// MARK: - Categories

@Composable
private fun CategoriesSection(onOpenSector: (SectorMeta) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Categories", style = Theme.sans(Theme.FontSize.xl2, FontWeight.Bold), color = Theme.colors.foreground)
        SectorCatalog.all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { sector ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(112.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(sector.color)
                            .fcPressable { onOpenSector(sector) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(sector.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(sector.name, style = Theme.sans(15, FontWeight.Bold), color = Color.White)
                        Text(
                            sector.blurb,
                            style = Theme.sans(10),
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Sector page

/**
 * Port of CategoryPageView — one sector's names, filtered out of the
 * market-overview stocks list by SectorMeta.name.
 */
@Composable
fun SectorScreen(sector: SectorMeta, onOpenSymbol: (String) -> Unit) {
    var stocks by remember(sector.id) { mutableStateOf<List<MarketQuote>>(emptyList()) }
    var loading by remember(sector.id) { mutableStateOf(true) }

    LaunchedEffect(sector.id) {
        val overview = runCatching { MarketService.marketOverview() }.getOrNull()
        stocks = overview?.stocks?.filter { it.sector.equals(sector.name, ignoreCase = true) } ?: emptyList()
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Theme.colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sector.name, style = Theme.sans(28, FontWeight.Bold), color = Theme.colors.foreground)
            Text(sector.blurb, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
        }
        when {
            loading -> CircularProgressIndicator(color = Theme.colors.primary)
            stocks.isEmpty() -> Text(
                "No ${sector.name.lowercase()} names in today's market snapshot.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
            else -> {
                val shape = RoundedCornerShape(Theme.Radius.lg)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Theme.colors.card)
                        .border(1.dp, Theme.colors.border, shape),
                ) {
                    stocks.forEachIndexed { index, quote ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .fcPressable { onOpenSymbol(quote.symbol) }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompanyLogo(quote.symbol, logoUrl = quote.logo, size = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(quote.symbol, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                                Text(
                                    quote.name,
                                    style = Theme.sans(11),
                                    color = Theme.colors.mutedForeground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    MarketFormat.price(quote.price),
                                    style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                                    color = Theme.colors.foreground,
                                )
                                ChangePill(quote.changesPercentage)
                            }
                        }
                        if (index < stocks.size - 1) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                        }
                    }
                }
            }
        }
    }
}
