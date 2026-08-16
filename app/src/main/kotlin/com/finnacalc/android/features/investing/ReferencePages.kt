//
// ReferencePages.kt
//
// Ports of three reference screens from the iOS Investing tab:
//   ETFListView         → EtfListScreen        (curated ETFs with live prices)
//   SafeInvestmentsView → SafeInvestmentsScreen (static reference list)
//   BondsPageView       → BondsScreen           (the web's placeholder page)
//
// The ETF rows drill into the same stock detail the rest of the tab uses, so
// this is a curated on-ramp rather than a parallel implementation. Prices come
// from the live quote and read "—" until they arrive; nothing here shows a
// figure it hasn't been given.
//

package com.finnacalc.android.features.investing

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCBadge
import com.finnacalc.android.core.designsystem.FCBadgeVariant
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.staggeredAppear
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.features.calculators.CalcFormat

// MARK: - ETFs & index funds

data class EtfEntry(val symbol: String, val name: String, val blurb: String)

val curatedEtfs = listOf(
    EtfEntry("VOO", "Vanguard S&P 500", "The 500 largest US companies"),
    EtfEntry("SPY", "SPDR S&P 500", "The classic S&P 500 tracker"),
    EtfEntry("VTI", "Vanguard Total Market", "The entire US stock market"),
    EtfEntry("QQQ", "Invesco QQQ", "Nasdaq-100, big tech heavy"),
    EtfEntry("DIA", "SPDR Dow Jones", "The Dow 30 industrials"),
    EtfEntry("IWM", "iShares Russell 2000", "US small caps"),
    EtfEntry("VXUS", "Vanguard Intl Stock", "The world outside the US"),
    EtfEntry("SCHD", "Schwab US Dividend", "High-quality dividend payers"),
    EtfEntry("VIG", "Vanguard Div Appreciation", "Dividend growers"),
    EtfEntry("AGG", "iShares Core US Bond", "Investment-grade US bonds"),
    EtfEntry("BND", "Vanguard Total Bond", "The broad US bond market"),
    EtfEntry("VNQ", "Vanguard Real Estate", "US REITs"),
    EtfEntry("GLD", "SPDR Gold Shares", "Physical gold"),
)

@Composable
fun EtfListScreen(onOpenSymbol: (String) -> Unit) {
    // A LazyColumn: every row pulls its own live quote, so composing all of
    // them up front means thirteen requests to fill one screen.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item("hero") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B7285)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Layers,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text("ETFs & Index Funds", style = Theme.sans(26, FontWeight.Bold), color = Theme.colors.foreground)
            Text(
                "One purchase, hundreds of companies. The simplest way to own the whole market.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }
        }

        item("divider") { HorizontalDivider(color = Theme.colors.border) }

        items(curatedEtfs, key = { it.symbol }) { etf ->
            EtfRow(etf) { onOpenSymbol(etf.symbol) }
        }
    }
}

@Composable
private fun EtfRow(etf: EtfEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var price by remember(etf.symbol) { mutableStateOf<Double?>(null) }
    var changePct by remember(etf.symbol) { mutableStateOf(0.0) }
    var loaded by remember(etf.symbol) { mutableStateOf(false) }

    LaunchedEffect(etf.symbol) {
        val result = runCatching { MarketService.stock(etf.symbol) }.getOrNull()
        if (result != null) {
            price = result.quote.price.toDoubleOrNull()
            changePct = result.quote.changePercent.replace("%", "").toDoubleOrNull() ?: 0.0
        }
        loaded = true
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .fcPressable(onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompanyLogo(etf.symbol, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(etf.name, style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold), color = Theme.colors.foreground)
            Text("${etf.symbol} · ${etf.blurb}", style = Theme.sans(11), color = Theme.colors.mutedForeground)
        }
        val currentPrice = price
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (currentPrice != null) {
                val isUp = changePct >= 0
                Text(
                    "$" + CalcFormat.fixed(currentPrice, 2),
                    style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                )
                Text(
                    (if (isUp) "+" else "-") + CalcFormat.fixed(kotlin.math.abs(changePct), 2) + "%",
                    style = Theme.figure(11, FontWeight.Medium),
                    color = if (isUp) Theme.colors.positive else Theme.colors.negative,
                )
            } else {
                // A dash while the quote is in flight, never a placeholder figure.
                Text("—", style = Theme.figure(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
            }
        }
    }
}

// MARK: - Safe investments

private data class SafeInvestment(
    val name: String,
    val avgReturn: String,
    val risk: String,
    val description: String,
    val minInvestment: String,
    val link: String,
)

private val safeInvestments = listOf(
    SafeInvestment(
        "S&P 500 Index Fund (IVV)", "10.5%", "Low-Medium",
        "Tracks the 500 largest US companies.", "$1",
        "https://www.ishares.com/us/products/239726/ishares-core-sp-500-etf",
    ),
    SafeInvestment(
        "Total Stock Market (VTI)", "10.2%", "Low-Medium",
        "Owns the entire US stock market.", "$1",
        "https://investor.vanguard.com/investment-products/etfs/profile/vti",
    ),
    SafeInvestment(
        "High-Yield Savings", "4.5%+", "None",
        "FDIC insured savings account.", "$0",
        "https://www.nerdwallet.com/best/banking/high-yield-online-savings-accounts",
    ),
)

@Composable
fun SafeInvestmentsScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Safe Investment Options",
                style = Theme.sans(Theme.FontSize.xl2, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Text(
                "Top safest investments with consistent returns",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }

        // The list is educational; the returns are historical and not promises.
        FCCard {
            FCCardContent {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Theme.colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Educational, not financial advice",
                            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                            color = Theme.colors.foreground,
                        )
                        Text(
                            "Average returns are historical and not guaranteed. All investing " +
                                "carries risk, including the possible loss of principal. Do your " +
                                "own research before investing.",
                            style = Theme.sans(Theme.FontSize.xs),
                            color = Theme.colors.mutedForeground,
                        )
                    }
                }
            }
        }

        FCCard {
            Column(Modifier.fillMaxWidth()) {
                safeInvestments.forEachIndexed { index, investment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Theme.colors.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = Theme.colors.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                investment.name,
                                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                                color = Theme.colors.foreground,
                            )
                            Text(
                                investment.description,
                                style = Theme.sans(Theme.FontSize.xs),
                                color = Theme.colors.mutedForeground,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RiskBadge(investment.risk)
                                Text(
                                    "Min: ${investment.minInvestment}",
                                    style = Theme.sans(11),
                                    color = Theme.colors.mutedForeground,
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    investment.avgReturn,
                                    style = Theme.figure(Theme.FontSize.lg, FontWeight.Bold),
                                    color = Theme.colors.positive,
                                )
                                Text("avg return", style = Theme.sans(10), color = Theme.colors.mutedForeground)
                            }
                            Text(
                                "Invest Now",
                                style = Theme.sans(11, FontWeight.SemiBold),
                                color = Theme.colors.primaryForeground,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Theme.colors.primary)
                                    .fcPressable {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(investment.link))
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (index < safeInvestments.size - 1) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Theme.colors.border)
                        )
                    }
                }
            }
        }
    }
}

/**
 * The web's `getRiskColor` hue mapping: None / Very Low → green, Low → blue,
 * Low-Medium → amber, Medium → orange, anything else → the muted default.
 */
@Composable
private fun RiskBadge(risk: String) {
    val color: Color? = when (risk) {
        "None", "Very Low" -> Theme.colors.positive
        "Low" -> Theme.colors.primary
        "Low-Medium" -> Theme.colors.caution
        "Medium" -> Theme.colors.accentOrange
        else -> null
    }
    if (color != null) {
        Text(
            risk,
            style = Theme.sans(11, FontWeight.SemiBold),
            color = color,
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    } else {
        FCBadge(risk, variant = FCBadgeVariant.Secondary)
    }
}

// MARK: - Bonds

/**
 * The web component is a small informational placeholder, and so is this: a
 * heading and one honest line. Inventing bond content the reference doesn't
 * have would be worse than saying it's coming.
 */
@Composable
fun BondsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Bonds", style = Theme.sans(Theme.FontSize.xl2, FontWeight.Bold), color = Theme.colors.foreground)
        FCCard {
            FCCardContent {
                Text(
                    "Content about bond investing will go here.",
                    style = Theme.sans(Theme.FontSize.base),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
    }
}
