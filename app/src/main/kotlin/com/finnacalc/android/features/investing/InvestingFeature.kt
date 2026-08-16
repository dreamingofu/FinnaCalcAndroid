//
// InvestingFeature.kt
//
// Port of iOS Features/Investing/InvestingView.swift — the Investing tab
// root: header, universal search with live typeahead, and the three view tabs
// (Discover / Portfolio / Screener), plus the local back-stack for the pages
// they push (stock detail, sector page).
//
// Portfolio lands in Phase 5c; until then its slot carries the watchlist and
// says where the brokerage ledger is going, rather than pretending to be it.
//

package com.finnacalc.android.features.investing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finnacalc.android.app.CrossTabNavigation
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.market.StockSearchResult
import com.finnacalc.android.features.calculators.CalcSegmentedControl
import kotlinx.coroutines.delay

sealed class InvestingDest {
    data class Stock(val symbol: String) : InvestingDest()
    data class Sector(val sectorId: String) : InvestingDest()
    data object Etfs : InvestingDest()
    data object Bonds : InvestingDest()
    data object Safe : InvestingDest()
    data object Tracker : InvestingDest()
}

private enum class InvestingTab(val title: String) {
    Discover("Discover"), Portfolio("Portfolio"), Screener("Screener")
}

@Composable
fun InvestingFeature(auth: AuthManager) {
    val stack = remember { mutableStateListOf<InvestingDest>() }
    // The order ticket is a sheet over whatever is showing, so it lives here
    // rather than in the stack.
    var ticket by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    if (stack.isNotEmpty()) {
        BackHandler { stack.removeAt(stack.lastIndex) }
    }

    when (val top = stack.lastOrNull()) {
        null -> InvestingHome(
            auth = auth,
            reloadKey = reloadKey,
            push = { stack.add(it) },
            onTrade = { symbol, sell -> ticket = symbol to sell },
        )
        is InvestingDest.Stock -> StockScreen(top.symbol)
        is InvestingDest.Sector -> {
            val sector = SectorCatalog.all.firstOrNull { it.id == top.sectorId }
            if (sector != null) {
                SectorScreen(sector) { stack.add(InvestingDest.Stock(it)) }
            }
        }
        is InvestingDest.Etfs -> EtfListScreen { stack.add(InvestingDest.Stock(it)) }
        is InvestingDest.Bonds -> BondsScreen()
        is InvestingDest.Safe -> SafeInvestmentsScreen()
        is InvestingDest.Tracker -> TradeTrackerScreen { stack.add(InvestingDest.Stock(it)) }
    }

    ticket?.let { (symbol, sell) ->
        OrderTicketSheet(
            symbol = symbol,
            startWithSell = sell,
            onDismiss = { ticket = null },
            // A placed order changes the portfolio, so the page reloads.
            onPlaced = { reloadKey += 1 },
        )
    }
}

@Composable
private fun InvestingHome(
    auth: AuthManager,
    reloadKey: Int,
    push: (InvestingDest) -> Unit,
    onTrade: (String, Boolean) -> Unit,
) {
    var activeTab by remember { mutableStateOf(InvestingTab.Discover) }

    // A Home card asked for a specific tab on the way in. One-shot.
    LaunchedEffect(Unit) {
        when (CrossTabNavigation.pendingInvestingTab) {
            "portfolio" -> activeTab = InvestingTab.Portfolio
            "screener" -> activeTab = InvestingTab.Screener
            "discover" -> activeTab = InvestingTab.Discover
        }
        CrossTabNavigation.pendingInvestingTab = null
    }
    var searchTerm by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Live typeahead, debounced, ≥2 chars.
    LaunchedEffect(searchTerm) {
        val term = searchTerm.trim()
        if (term.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        searchResults = runCatching { MarketService.search(term) }.getOrDefault(emptyList())
        isSearching = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Investing", style = Theme.sans(30, FontWeight.Bold), color = Theme.colors.foreground)
            Text(
                "Live markets, your portfolio, and stock research in one place.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }

        // Universal search.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .background(Theme.colors.muted.copy(alpha = 0.5f))
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(Theme.Radius.md))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Theme.colors.mutedForeground, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    FCTextField(
                        "Search stocks", searchTerm, { searchTerm = it.uppercase() },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        showsPlaceholder = true,
                    )
                }
            }
            when {
                isSearching -> Text("Searching…", style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
                searchResults.isNotEmpty() -> SearchResults(searchResults) { symbol ->
                    searchTerm = ""
                    searchResults = emptyList()
                    push(InvestingDest.Stock(symbol))
                }
                searchTerm.trim().length >= 2 -> Text(
                    "No results found.",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        CalcSegmentedControl(activeTab, { activeTab = it }, InvestingTab.entries.map { it to it.title })

        when (activeTab) {
            InvestingTab.Discover -> DiscoverScreen(
                onOpenSymbol = { push(InvestingDest.Stock(it)) },
                onOpenSector = { push(InvestingDest.Sector(it.id)) },
                onOpenPage = { push(it) },
            )
            InvestingTab.Portfolio -> {
                val portfolioViewModel: PortfolioViewModel = viewModel()
                val portfolioState by portfolioViewModel.state.collectAsState()
                // A placed order invalidates holdings and orders.
                LaunchedEffect(reloadKey) { if (reloadKey > 0) portfolioViewModel.refresh() }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PortfolioScreen(
                        auth = auth,
                        onOpenSymbol = { push(InvestingDest.Stock(it)) },
                        onTrade = onTrade,
                        viewModel = portfolioViewModel,
                    )
                    InvestingGoalsSection(portfolioState.positions)
                    if (portfolioState.holdings.isNotEmpty()) {
                        PortfolioAnalyticsSection(portfolioState)
                    }
                    WatchlistCard { push(InvestingDest.Stock(it)) }
                }
            }
            InvestingTab.Screener -> ScreenerScreen { push(InvestingDest.Stock(it)) }
        }
    }
}

@Composable
private fun SearchResults(results: List<StockSearchResult>, onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape),
    ) {
        results.take(8).forEachIndexed { index, result ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .fcPressable { onPick(result.symbol) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompanyLogo(result.symbol, size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(result.symbol, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                    Text(
                        result.name,
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Theme.colors.mutedForeground,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (index < minOf(results.size, 8) - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
            }
        }
    }
}
