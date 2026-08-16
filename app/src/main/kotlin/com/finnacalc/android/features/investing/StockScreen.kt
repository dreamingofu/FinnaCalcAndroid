//
// StockScreen.kt
//
// Port of iOS Features/Investing/StocksPageView.swift + StockDetailSections —
// the Cash App-style stock detail: hero price with scrub readout, the line /
// candle chart with range pills, Follow, key stats, about, financials, and
// news. Every optional field hides its own line when the payload carries
// null — Alpaca prices what trades and publishes no fundamentals, so a
// missing figure is a missing line, never a zero.
//
// A retired ticker resolved server-side (PARA → PSKY) surfaces its alias note
// rather than silently charting a different company.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.CandlePoint
import com.finnacalc.android.core.market.FinancialsResponse
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.market.NewsArticle
import com.finnacalc.android.core.market.StockResponse
import com.finnacalc.android.features.calculators.CalcFormat

@Composable
fun StockScreen(symbol: String) {
    var stock by remember(symbol) { mutableStateOf<StockResponse?>(null) }
    var points by remember(symbol) { mutableStateOf<List<CandlePoint>>(emptyList()) }
    var news by remember(symbol) { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var financials by remember(symbol) { mutableStateOf<FinancialsResponse?>(null) }
    var range by remember(symbol) { mutableStateOf(ChartRange.OneDay) }
    var style by remember { mutableStateOf(ChartStyle.Line) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember(symbol) { mutableStateOf(true) }
    var error by remember(symbol) { mutableStateOf<String?>(null) }
    var following by remember(symbol) { mutableStateOf(WatchlistStore.contains(symbol)) }

    LaunchedEffect(symbol) {
        loading = true
        error = null
        val result = runCatching { MarketService.stock(symbol) }
        stock = result.getOrNull()
        if (result.isFailure) error = "Couldn't load $symbol."
        loading = false
        news = runCatching { MarketService.news(symbol).articles }.getOrDefault(emptyList())
        financials = runCatching { MarketService.financials(symbol) }.getOrNull()
    }

    LaunchedEffect(symbol, range) {
        points = runCatching { MarketService.candles(symbol, range.raw).points }.getOrDefault(emptyList())
    }

    val quote = stock?.quote
    val price = quote?.let { MarketFormat.parse(it.price) }
    val change = quote?.let { MarketFormat.parse(it.change) } ?: 0.0
    val changePct = quote?.let { MarketFormat.parse(it.changePercent) } ?: 0.0
    // 1D colours against the previous close, derived from the quote (the
    // route only ever sent null for it); every other range against its first
    // point.
    val baseline = if (range == ChartRange.OneDay && price != null) price - change else points.firstOrNull()?.c

    // While scrubbing the hero reads the point under the finger, so the
    // figure and the chart never disagree.
    val scrubbed = selectedIndex?.let { points.getOrNull(it) }
    val heroPrice = scrubbed?.c ?: price
    val heroChange = scrubbed?.let { p -> baseline?.let { p.c - it } } ?: change
    val heroChangePct = scrubbed?.let { p ->
        baseline?.takeIf { it != 0.0 }?.let { (p.c - it) / it * 100 }
    } ?: changePct

    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (loading && stock == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Theme.colors.primary)
            }
            return@Column
        }
        error?.let {
            Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.destructive)
            return@Column
        }
        val data = stock ?: return@Column

        // Hero: logo, name, price, change.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CompanyLogo(data.quote.symbol, logoUrl = data.overview.logo, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    data.overview.name.ifEmpty { data.quote.symbol },
                    style = Theme.sans(18, FontWeight.Bold),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(data.quote.symbol, style = Theme.sans(Theme.FontSize.xs, FontWeight.Medium), color = Theme.colors.mutedForeground)
            }
            Icon(
                if (following) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (following) "Unfollow" else "Follow",
                tint = if (following) Theme.colors.primary else Theme.colors.mutedForeground,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .fcPressable { following = WatchlistStore.toggle(data.quote.symbol) }
                    .padding(7.dp),
            )
        }

        // A retired ticker resolved to its successor says so.
        data.alias?.let { alias ->
            Text(
                alias.note,
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.caution,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .background(Theme.colors.cautionTint)
                    .padding(10.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                heroPrice?.let { MarketFormat.price(it) } ?: "—",
                style = Theme.figure(36, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val tint = if (heroChange >= 0) Theme.colors.positive else Theme.colors.negative
                Text(
                    (if (heroChange >= 0) "+" else "−") + "$" + CalcFormat.fixed(kotlin.math.abs(heroChange), 2),
                    style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = tint,
                )
                ChangePill(heroChangePct)
                Text(
                    if (scrubbed != null) "at this point" else "today",
                    style = Theme.sans(11),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        // Chart + range pills + style toggle.
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            if (points.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No price history for this range.", style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
                }
            } else {
                StockLineChart(
                    points = points,
                    modifier = Modifier.fillMaxSize(),
                    baseline = baseline,
                    range = range,
                    style = style,
                    showScales = true,
                    selectedIndex = selectedIndex,
                    onSelectedIndexChange = { selectedIndex = it },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ChartRangePicker(range) { range = it }
            }
            Icon(
                if (style == ChartStyle.Line) Icons.Default.ShowChart else Icons.Default.CandlestickChart,
                contentDescription = "Toggle chart style",
                tint = Theme.colors.primary,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Theme.colors.brandTint)
                    .fcPressable { style = if (style == ChartStyle.Line) ChartStyle.Candles else ChartStyle.Line }
                    .padding(7.dp),
            )
        }

        KeyStatsSection(data)
        AboutSection(data)
        financials?.let { FinancialsSection(it) }
        if (news.isNotEmpty()) NewsSection(news, title = "News")
    }
}

// MARK: - Sections

@Composable
private fun KeyStatsSection(data: StockResponse) {
    // Every row is omitted when its value is null — Alpaca publishes no
    // fundamentals, and an invented figure is worse than a missing one.
    val rows = buildList {
        MarketFormat.parse(data.overview.marketCapitalization)?.let { add("Market cap" to MarketFormat.abbreviatedMoney(it)) }
        MarketFormat.parse(data.overview.peRatio)?.let { add("P/E ratio" to CalcFormat.fixed(it, 2)) }
        data.stats?.high52?.let { add("52-week high" to MarketFormat.price(it)) }
        data.stats?.low52?.let { add("52-week low" to MarketFormat.price(it)) }
        data.stats?.beta?.let { add("Beta" to CalcFormat.fixed(it, 2)) }
        data.stats?.epsTTM?.let { add("EPS (TTM)" to CalcFormat.fixed(it, 2)) }
        data.stats?.dividendYield?.let { add("Dividend yield" to CalcFormat.fixed(it, 2) + "%") }
        data.company?.exchange?.let { add("Exchange" to it) }
        data.company?.sector?.let { add("Sector" to it) }
        data.company?.industry?.let { add("Industry" to it) }
    }
    if (rows.isEmpty()) return
    SectionCard("Key stats") {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground, modifier = Modifier.weight(1f))
                Text(value, style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold), color = Theme.colors.foreground)
            }
        }
    }
}

@Composable
private fun AboutSection(data: StockResponse) {
    val description = data.overview.description
    if (description.isBlank()) return
    var expanded by remember(data.quote.symbol) { mutableStateOf(false) }
    SectionCard("About") {
        Text(
            description,
            style = Theme.sans(Theme.FontSize.sm).copy(lineHeight = 20.sp),
            color = Theme.colors.textBody,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (expanded) "Show less" else "Show more",
            style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold),
            color = Theme.colors.primary,
            modifier = Modifier.padding(top = 8.dp).fcPressable { expanded = !expanded },
        )
    }
}

@Composable
private fun FinancialsSection(financials: FinancialsResponse) {
    val periods = financials.annual.takeIf { it.isNotEmpty() } ?: financials.quarterly
    if (periods.isEmpty()) return
    val maxRevenue = periods.maxOf { kotlin.math.abs(it.revenue) }.takeIf { it > 0 } ?: return
    SectionCard("Revenue and profit") {
        periods.takeLast(6).forEach { period ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(period.label, style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold), color = Theme.colors.foreground, modifier = Modifier.weight(1f))
                    Text(
                        MarketFormat.abbreviatedMoney(period.revenue),
                        style = Theme.figure(Theme.FontSize.xs, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        MarketFormat.abbreviatedMoney(period.netProfit),
                        style = Theme.figure(Theme.FontSize.xs, FontWeight.SemiBold),
                        color = if (period.netProfit >= 0) Theme.colors.positive else Theme.colors.negative,
                    )
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Theme.colors.secondary)) {
                    Box(
                        Modifier
                            .fillMaxWidth((kotlin.math.abs(period.revenue) / maxRevenue).toFloat())
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Theme.colors.primary),
                    )
                }
            }
        }
        Text(
            "Revenue and net profit per period, from SEC filings. Bars are scaled to the largest revenue shown.",
            style = Theme.sans(Theme.FontSize.xs),
            color = Theme.colors.mutedForeground,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
        Spacer(Modifier.height(4.dp))
        content()
    }
}
