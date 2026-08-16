//
// PortfolioAnalyticsSection.kt
//
// Port of iOS Features/Investing/PortfolioAnalyticsViews.swift — the analysis
// cards over the PortfolioAnalytics engine: diversification, sector/type
// breakdown, risk, forward dividends, and the unrealised tax picture.
//
// Coverage is reported wherever a figure is built from a subset: a weighted
// beta across 60% of the portfolio is a different claim from one across all
// of it, and each card says which. Anything the data plan can't supply
// (dividends actually received, realized gains, expense ratios) is absent
// rather than estimated.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.features.budgeting.BudgetCategoryStyle
import com.finnacalc.android.features.calculators.CalcFormat

@Composable
fun PortfolioAnalyticsSection(state: PortfolioUiState) {
    val holdings = state.holdings
    if (holdings.isEmpty()) return

    val store = PortfolioFundamentalsStore.shared
    val fundamentals by store.fundamentals.collectAsState()
    val scope = rememberCoroutineScope()

    // Largest holdings first — the store's per-stage caps apply in that order.
    LaunchedEffect(holdings.map { it.symbol }) {
        store.load(holdings.map { it.symbol }, scope)
    }

    val sectors = PortfolioAnalytics.sectors(holdings, fundamentals)
    val types = PortfolioAnalytics.types(holdings)
    val diversification = PortfolioAnalytics.diversification(holdings, sectors?.slices?.map { it.weight })
    val risk = PortfolioAnalytics.risk(holdings, fundamentals)
    val dividends = PortfolioAnalytics.dividends(holdings, fundamentals)
    val tax = PortfolioAnalytics.tax(holdings)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Analysis", style = Theme.sans(18, FontWeight.Bold), color = Theme.colors.foreground)

        diversification?.let { d ->
            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Diversification",
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                        color = Theme.colors.foreground,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        PortfolioAnalytics.diversificationLabel(d.score),
                        style = Theme.sans(11, FontWeight.Bold),
                        color = Theme.colors.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Theme.colors.brandTint)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text("${d.score}", style = Theme.figure(30, FontWeight.Bold), color = Theme.colors.foreground)
                Text(
                    "Your ${d.holdingCount} holdings spread like " +
                        "${CalcFormat.fixed(d.effectiveHoldings, 1)} equally-sized ones. " +
                        "${d.topSymbol} is the largest at ${CalcFormat.fixed(d.topWeight * 100, 0)}%, and the " +
                        "top three hold ${CalcFormat.fixed(d.topThreeWeight * 100, 0)}%." +
                        (d.effectiveSectors?.let {
                            " Across sectors it spreads like ${CalcFormat.fixed(it, 1)} equal ones."
                        } ?: " Sector data isn't available for these names, so the score reads holdings alone."),
                    style = Theme.sans(Theme.FontSize.xs).copy(lineHeight = 17.sp),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        // Sector breakdown when it's known; otherwise the type split, which
        // every holding can always answer.
        val breakdown = sectors ?: types
        breakdown?.let { b ->
            CardSurface {
                Text(
                    if (sectors != null) "Where your money sits" else "Stocks and funds",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(b.slices.map { it.weight }, Modifier.size(96.dp))
                    Column(Modifier.weight(1f)) {
                        b.slices.take(6).forEachIndexed { index, slice ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BudgetCategoryStyle.chartColor(index))
                                )
                                Text(
                                    slice.name,
                                    style = Theme.sans(11, FontWeight.Medium),
                                    color = Theme.colors.foreground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    CalcFormat.fixed(slice.weight * 100, 0) + "%",
                                    style = Theme.figure(11, FontWeight.SemiBold),
                                    color = Theme.colors.mutedForeground,
                                )
                            }
                        }
                    }
                }
                if (b.coverage < 1) {
                    // Coverage is always stated — a breakdown over part of the
                    // book is a different claim from one over all of it.
                    Text(
                        "Covers ${CalcFormat.fixed(b.coverage * 100, 0)}% of your holdings; the rest have no " +
                            "sector on file, so they're left out rather than guessed.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }

        if (risk.beta != null || risk.volatility != null) {
            CardSurface {
                Text("Risk", style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                risk.beta?.let { beta ->
                    Text(
                        "Beta " + CalcFormat.fixed(beta, 2) + " — " + PortfolioAnalytics.betaLabel(beta),
                        style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                    )
                    Text(
                        "Across ${CalcFormat.fixed(risk.betaCoverage * 100, 0)}% of your holdings — the ones " +
                            "that report a beta.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                    )
                }
                risk.volatility?.let { vol ->
                    Text(
                        "Volatility " + CalcFormat.fixed(vol, 1) + "% a year",
                        style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "Annualised from the past year of closes, across " +
                            "${CalcFormat.fixed(risk.volatilityCoverage * 100, 0)}% of your holdings.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }

        dividends?.let { d ->
            CardSurface {
                Text("Dividends", style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                Text(
                    "$" + CalcFormat.int(d.annual) + " a year",
                    style = Theme.figure(24, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Text(
                    "About $" + CalcFormat.int(d.monthly) + " a month, a " +
                        CalcFormat.fixed(d.portfolioYield, 2) + "% yield on the whole portfolio. " +
                        CalcFormat.fixed(d.payerWeight * 100, 0) + "% of your money is in something that pays.",
                    style = Theme.sans(Theme.FontSize.xs).copy(lineHeight = 17.sp),
                    color = Theme.colors.mutedForeground,
                )
                Text(
                    "A projection at today's yields, not a payment schedule — the data plan carries no record " +
                        "of dividends actually received.",
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        tax?.let { t ->
            CardSurface {
                Text("Unrealised gains", style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("GAINS", style = Theme.sans(9, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = Theme.colors.mutedForeground)
                        Text("$" + CalcFormat.int(t.gains), style = Theme.figure(18, FontWeight.Bold), color = Theme.colors.positive)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("LOSSES", style = Theme.sans(9, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = Theme.colors.mutedForeground)
                        Text("$" + CalcFormat.int(t.losses), style = Theme.figure(18, FontWeight.Bold), color = Theme.colors.negative)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("NET", style = Theme.sans(9, FontWeight.Bold).copy(letterSpacing = 0.8.sp), color = Theme.colors.mutedForeground)
                        Text(
                            "$" + CalcFormat.int(t.net),
                            style = Theme.figure(18, FontWeight.Bold),
                            color = if (t.net >= 0) Theme.colors.positive else Theme.colors.negative,
                        )
                    }
                }
                Text(
                    "If every winner were sold today, federal tax at the 15% long-term rate would be about $" +
                        CalcFormat.int(PortfolioAnalytics.estimatedTax(t, 15.0)) +
                        ". That assumes every lot has been held over a year, and it ignores state tax.",
                    style = Theme.sans(Theme.FontSize.xs).copy(lineHeight = 17.sp),
                    color = Theme.colors.mutedForeground,
                )
                if (t.coverage < 1) {
                    Text(
                        "Covers ${CalcFormat.fixed(t.coverage * 100, 0)}% of your holdings — the ones your " +
                            "brokerage reports a cost basis for.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
        }

        // The conversation about the mix, at the bottom of the analysis, where
        // iOS puts it under the performance card.
        CardSurface { PortfolioChatThread(holdings) }
    }
}

@Composable
private fun DonutChart(weights: List<Double>, modifier: Modifier = Modifier) {
    val track = Theme.colors.secondary
    val colors = weights.indices.map { BudgetCategoryStyle.chartColor(it) }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val lineWidth = 16.dp.toPx()
            val radius = (minOf(size.width, size.height) - lineWidth) / 2
            val topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(lineWidth))
            val gap = if (weights.size > 8) 1.5f else if (weights.size > 1) 3f else 0f
            var start = -90f
            weights.forEachIndexed { index, weight ->
                val sweep = (360.0 * weight).toFloat()
                if (sweep > gap) {
                    drawArc(
                        colors[index], start + gap / 2, sweep - gap, false,
                        topLeft, arcSize, style = Stroke(lineWidth, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
    }
}
