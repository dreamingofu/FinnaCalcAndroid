//
// ScreenerScreen.kt
//
// Port of iOS Features/Investing/DashboardScreenerView.swift — the Screener
// tab, built on what Alpaca can actually answer: price, the day's move,
// volume against the recent session average, and the session's range. The
// fundamentals other screeners filter on (market cap, P/E, sector, ratings,
// dividend yield) need a vendor this app doesn't have, so they are absent and
// SAID to be absent, never faked or stubbed.
//
// Three deliberate calls carried over:
//  · A preset IS the universe, and the footer names it. "Most active" is not
//    "every US stock ranked by volume" and shouldn't imply it.
//  · Sorting is a chip row, not table headers — a ten-column table on a phone
//    means horizontal scrolling to compare the two numbers anyone compares.
//  · The heatmap sizes tiles by volume and colours them by move, so it
//    answers "where did today's trading go".
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.LogoDev
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.market.ScreenerPreset
import com.finnacalc.android.core.market.ScreenerRow
import com.finnacalc.android.features.calculators.CalcFormat
import kotlin.math.abs
import kotlin.math.sqrt

private enum class SortKey(val title: String) {
    ChangePct("Change %"), Volume("Volume"), RelVolume("Rel vol"), Price("Price"), Symbol("Symbol")
}

private enum class ViewMode(val title: String) { Table("List"), Heatmap("Heatmap") }

@Composable
fun ScreenerScreen(onOpenSymbol: (String) -> Unit) {
    var rows by remember { mutableStateOf<List<ScreenerRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var universeSize by remember { mutableStateOf<Int?>(null) }
    var preset by remember { mutableStateOf(ScreenerPreset.Actives) }
    var mode by remember { mutableStateOf(ViewMode.Table) }
    var sortKey by remember { mutableStateOf(SortKey.Volume) }
    var sortAscending by remember { mutableStateOf(false) }

    LaunchedEffect(preset) {
        loading = true
        error = null
        val response = runCatching { MarketService.screener(mapOf("preset" to preset.raw)) }.getOrNull()
        if (response == null) {
            error = "Couldn't load the screener."
            rows = emptyList()
        } else {
            rows = response.rows
            universeSize = response.universeSize
            error = response.error
        }
        loading = false
    }

    val sorted = remember(rows, sortKey, sortAscending) {
        val comparator = when (sortKey) {
            SortKey.ChangePct -> compareBy<ScreenerRow> { it.changePct ?: 0.0 }
            SortKey.Volume -> compareBy { it.volume ?: 0.0 }
            SortKey.RelVolume -> compareBy { it.relVolume ?: 0.0 }
            SortKey.Price -> compareBy { it.price }
            SortKey.Symbol -> compareBy { it.symbol }
        }
        if (sortAscending) rows.sortedWith(comparator) else rows.sortedWith(comparator.reversed())
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Preset row — the preset IS the universe.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenerPreset.entries.forEach { p ->
                Chip(p.title, selected = p == preset) { preset = p }
            }
        }

        // Mode switch.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ViewMode.entries.forEach { m ->
                Chip(m.title, selected = m == mode) { mode = m }
            }
        }

        when {
            loading && rows.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Theme.colors.primary) }

            error != null && rows.isEmpty() -> Text(
                error!!,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.destructive,
            )

            rows.isEmpty() -> Text(
                "Nothing matched. Try another list.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )

            mode == ViewMode.Table -> {
                // Sorting is a chip row, not table headers.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortKey.entries.forEach { key ->
                        Chip(
                            key.title,
                            selected = key == sortKey,
                            // The direction is an icon, as it is on iOS; a text
                            // arrow rendered thin and off the label's baseline.
                            trailing = if (key != sortKey) null else {
                                if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
                            },
                        ) {
                            if (key == sortKey) sortAscending = !sortAscending else {
                                sortKey = key
                                sortAscending = false
                            }
                        }
                    }
                }
                ResultsCard(sorted, onOpenSymbol)
            }

            else -> Heatmap(sorted, onOpenSymbol)
        }

        // What the list actually is, said plainly under the results — plus the
        // Logo.dev credit its free tier requires.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                preset.blurb + (universeSize?.let { " $it symbols before filtering." } ?: ""),
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
            )
            Text(
                "Screens on measured price and volume only — market cap, P/E, sector and dividend " +
                    "yield need a fundamentals source this app doesn't have. ${LogoDev.ATTRIBUTION_TEXT}.",
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    val tint = if (selected) Color.White else Theme.colors.mutedForeground
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Theme.colors.primary else Theme.colors.secondary)
            .fcPressable(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Theme.sans(Theme.FontSize.xs, if (selected) FontWeight.Bold else FontWeight.Medium),
            color = tint,
            maxLines = 1,
        )
        if (trailing != null) {
            Icon(
                trailing,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun ResultsCard(rows: List<ScreenerRow>, onOpenSymbol: (String) -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape),
    ) {
        rows.take(50).forEachIndexed { index, row ->
            ScreenerRowView(row) { onOpenSymbol(row.symbol) }
            if (index < minOf(rows.size, 50) - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
            }
        }
    }
}

@Composable
private fun ScreenerRowView(row: ScreenerRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .fcPressable(onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompanyLogo(row.symbol, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.symbol, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
            Text(
                row.company,
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Where today's price sits in the session's range — drawn only
            // when the range is real.
            row.dayRangePosition?.let { position ->
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(64.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.secondary),
                ) {
                    Box(
                        Modifier
                            .padding(start = (60 * position).dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Theme.colors.primary),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                MarketFormat.price(row.price),
                style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.foreground,
            )
            row.changePct?.let { ChangePill(it) }
            row.relVolume?.let {
                Text(
                    CalcFormat.fixed(it, 1) + "× vol",
                    style = Theme.sans(10),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
    }
}

/**
 * Tiles sized by volume and coloured by move — "where did today's trading
 * go". A squarified treemap would be nicer; a volume-ordered flow keeps the
 * same reading (bigger tile = more traded) without the layout machinery.
 */
@Composable
private fun Heatmap(rows: List<ScreenerRow>, onOpenSymbol: (String) -> Unit) {
    val top = rows.filter { (it.volume ?: 0.0) > 0 }.take(24)
    if (top.isEmpty()) return
    val maxVolume = top.maxOf { it.volume ?: 0.0 }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        top.chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chunk.forEach { row ->
                    val change = row.changePct ?: 0.0
                    val tint = if (change >= 0) Theme.colors.positive else Theme.colors.negative
                    // Alpha tracks the size of the move, area tracks volume.
                    val intensity = (abs(change) / 5.0).coerceIn(0.15, 0.85)
                    val heightScale = sqrt((row.volume ?: 0.0) / maxVolume).coerceIn(0.45, 1.0)
                    // Floored at what the three lines actually need. Scaled
                    // alone the smallest tile came out ~40dp, which clipped the
                    // percentage mid-glyph and hid the volume entirely — the
                    // tile reported a move it wasn't tall enough to show.
                    Column(
                        Modifier
                            .weight(1f)
                            .height((88 * heightScale).dp.coerceAtLeast(64.dp))
                            .clip(RoundedCornerShape(Theme.Radius.md))
                            .background(tint.copy(alpha = intensity.toFloat()))
                            .fcPressable { onOpenSymbol(row.symbol) }
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(row.symbol, style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold), color = Color.White, maxLines = 1)
                        Text(
                            (if (change >= 0) "+" else "−") + MarketFormat.percent(change),
                            style = Theme.figure(10, FontWeight.SemiBold),
                            color = Color.White,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            MarketFormat.abbreviated(row.volume ?: 0.0),
                            style = Theme.sans(9),
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                // Keep the last row's tiles the same width as a full row's.
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(
            "Tile size is share volume; colour is today's move.",
            style = Theme.sans(Theme.FontSize.xs).copy(letterSpacing = 0.sp),
            color = Theme.colors.mutedForeground,
            textAlign = TextAlign.Start,
        )
    }
}
