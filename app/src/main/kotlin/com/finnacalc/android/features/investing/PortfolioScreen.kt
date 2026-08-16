//
// PortfolioScreen.kt
//
// Port of iOS Features/Investing/PortfolioLedgerView.swift — the Portfolio
// view: total-value hero with the derived value curve, holdings, recent
// orders, account management, and the SnapTrade connect flow.
//
// Deviations from iOS, with reasons:
//  · The hero's inverted "spotlight" fill is kept as a card on the page's own
//    surface; the accent swap it needed on iOS doesn't apply.
//  · The SnapTrade portal opens in a browser tab and returns through the
//    app's deep link, where iOS used ASWebAuthenticationSession.
//  · No portfolio-history endpoint exists on this data plan, so the curve is
//    Σ(units × closes) clamped to the earliest filled order, and the change
//    line renders only when every holding contributed — see
//    PortfolioViewModel.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.networking.ApiException
import com.finnacalc.android.core.snaptrade.BrokerageAccess
import com.finnacalc.android.core.snaptrade.SnapTradeOrder
import com.finnacalc.android.core.snaptrade.SnapTradeService
import com.finnacalc.android.core.util.HistoryDate
import com.finnacalc.android.features.calculators.CalcFormat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun PortfolioScreen(
    auth: AuthManager,
    onOpenSymbol: (String) -> Unit,
    onTrade: (symbol: String, sell: Boolean) -> Unit,
    viewModel: PortfolioViewModel = viewModel(),
) {
    val user by auth.user.collectAsState()
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var showBrokerPicker by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var showAccounts by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var pendingCancel by remember { mutableStateOf<SnapTradeOrder?>(null) }

    // Brokerage links are tied to the FinnaCalc account server-side, so a
    // sign-in changes what this page can show.
    LaunchedEffect(user?.id) {
        if (user != null) viewModel.loadIfNeeded()
    }

    fun connect(slug: String?, access: BrokerageAccess) {
        connecting = true
        connectError = null
        scope.launch {
            try {
                // The portal opens in a browser tab; it returns through the
                // app's finnacalc:// deep link, after which the page reloads.
                uriHandler.openUri(SnapTradeService.connect(access, slug))
            } catch (e: ApiException) {
                connectError = e.message
            }
            connecting = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            user == null -> SignedOutCard()
            !state.configured -> Text(
                "Brokerage connections aren't configured on this server yet.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
            state.loading && state.accounts.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Theme.colors.primary) }

            !state.hasAccounts -> ConnectInvite(connecting) { showBrokerPicker = true }

            else -> {
                // A connection SnapTrade lost its token for: holdings go
                // stale until the user re-authorises that same connection.
                state.disabledConnections.forEach { connection ->
                    ReconnectBanner(connection.brokerage) {
                        scope.launch {
                            runCatching {
                                uriHandler.openUri(
                                    SnapTradeService.reconnect(
                                        connection.id,
                                        if (connection.type?.lowercase() == "trade") BrokerageAccess.Trade
                                        else BrokerageAccess.View,
                                    )
                                )
                            }
                        }
                    }
                }

                PortfolioHero(state, viewModel)
                HoldingsCard(state, onOpenSymbol, onTrade)
                OrdersCard(state) { pendingCancel = it }
                ManageRow(
                    syncing = state.syncing,
                    syncUnavailable = state.syncUnavailable,
                    onSync = { viewModel.syncHoldings() },
                    onAccounts = { showAccounts = true },
                    onAddBrokerage = { showBrokerPicker = true },
                    onDisconnect = { confirmDisconnect = true },
                )
            }
        }
        connectError?.let {
            Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.destructive)
        }
    }

    if (showBrokerPicker) {
        BrokerPickerSheet(
            onDismiss = { showBrokerPicker = false },
            onConnect = { slug, access ->
                showBrokerPicker = false
                connect(slug, access)
            },
        )
    }

    if (showAccounts) {
        AccountSelectionSheet(state, viewModel) { showAccounts = false }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect your brokerage?") },
            text = {
                Text(
                    "FinnaCalc loses access to your live holdings, orders, and the analytics built on " +
                        "them. Nothing at your brokerage itself is touched: your money and positions stay " +
                        "exactly where they are. Reconnecting later means linking the brokerage again from " +
                        "scratch."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) { Text("Disconnect", color = Theme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }

    pendingCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text("Cancel this order?") },
            text = {
                Text(
                    "${order.action ?: ""} ${order.symbol ?: ""} is still working at your brokerage. " +
                        "Cancelling asks the brokerage to pull it; anything already filled stays filled."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelOrder(order)
                    pendingCancel = null
                }) { Text("Cancel order", color = Theme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text("Keep it") } },
        )
    }
}

// MARK: - Empty / signed-out states

@Composable
private fun SignedOutCard() {
    CardSurface {
        Text("Sign in to connect a brokerage", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
        Text(
            "Brokerage links belong to your FinnaCalc account, so holdings follow you across devices.",
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
        )
    }
}

@Composable
private fun ConnectInvite(connecting: Boolean, onConnect: () -> Unit) {
    CardSurface {
        Text("Connect your brokerage", style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
        Text(
            "Link read-only and your holdings, orders and analysis show up here. Nothing can be traded " +
                "unless you choose to allow it.",
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
        )
        Text(
            if (connecting) "Opening…" else "Connect brokerage",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onConnect)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun ReconnectBanner(brokerage: String, onReconnect: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.lg))
            .background(Theme.colors.cautionTint)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("$brokerage needs reconnecting", style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
        Text(
            "Your brokerage ended the link, so holdings here will go stale until it's re-authorised.",
            style = Theme.sans(Theme.FontSize.xs),
            color = Theme.colors.mutedForeground,
        )
        Text(
            "Reconnect",
            style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold),
            color = Theme.colors.primary,
            modifier = Modifier
                .clip(CircleShape)
                .background(Theme.colors.card)
                .fcPressable(onReconnect)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

// MARK: - Hero

@Composable
private fun PortfolioHero(state: PortfolioUiState, viewModel: PortfolioViewModel) {
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val scrubbed = scrubIndex?.let { state.heroPoints.getOrNull(it) }
    val value = scrubbed?.c ?: state.totalValue
    val day = state.dayChange
    val open = state.openPnl

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Theme.colors.foreground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "TOTAL VALUE",
            style = Theme.sans(10, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
            color = Theme.colors.background.copy(alpha = 0.6f),
        )
        Text(
            "$" + CalcFormat.decimal(value, 2),
            style = Theme.figure(34, FontWeight.Bold),
            color = Theme.colors.background,
            maxLines = 1,
        )
        // Today's move when every holding has a quote; otherwise the
        // brokerage's own all-time open P/L, labelled as such.
        when {
            day != null -> ChangeLine(day.first, day.second, "today")
            open != null -> ChangeLine(open, null, "all time")
            else -> Text(
                "Waiting on live quotes",
                style = Theme.sans(11),
                color = Theme.colors.background.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(Modifier.fillMaxWidth().height(120.dp)) {
            when {
                state.heroLoading && state.heroPoints.isEmpty() ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Theme.colors.background, strokeWidth = 2.dp)
                    }
                state.heroPoints.size > 1 -> StockLineChart(
                    points = state.heroPoints,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    baseline = state.heroPoints.firstOrNull()?.c,
                    range = state.heroRange,
                    selectedIndex = scrubIndex,
                    onSelectedIndexChange = { scrubIndex = it },
                )
                else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No price history covers this window yet.",
                        style = Theme.sans(11),
                        color = Theme.colors.background.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // The curve's own change line only renders when every holding
        // contributed a series — a partial curve's delta would be invented.
        if (state.heroPoints.size > 1 && state.heroSeriesComplete) {
            val first = state.heroPoints.first().c
            val last = state.heroPoints.last().c
            val delta = last - first
            val pct = if (first > 0) delta / first * 100 else 0.0
            val since = state.heroSince
                ?.let { HistoryDate.medium(it.atZone(ZoneId.systemDefault()).toLocalDate()) }
            Text(
                (if (delta >= 0) "+" else "−") + "$" + CalcFormat.decimal(kotlin.math.abs(delta), 2) +
                    " (" + CalcFormat.fixed(kotlin.math.abs(pct), 2) + "%) " +
                    (since?.let { "since $it" } ?: state.heroRange.raw),
                style = Theme.figure(Theme.FontSize.xs, FontWeight.SemiBold),
                color = Theme.colors.background.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.height(6.dp))
        ChartRangePicker(state.heroRange) { viewModel.setRange(it) }
    }
}

@Composable
private fun ChangeLine(change: Double, pct: Double?, label: String) {
    val tint = if (change >= 0) Theme.colors.positive else Theme.colors.negative
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (change >= 0) "+" else "−") + "$" + CalcFormat.decimal(kotlin.math.abs(change), 2) +
                (pct?.let { " (" + CalcFormat.fixed(kotlin.math.abs(it), 2) + "%)" } ?: ""),
            style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
            color = tint,
        )
        Text(label, style = Theme.sans(11), color = Theme.colors.background.copy(alpha = 0.6f))
    }
}

// MARK: - Holdings

@Composable
private fun HoldingsCard(
    state: PortfolioUiState,
    onOpenSymbol: (String) -> Unit,
    onTrade: (String, Boolean) -> Unit,
) {
    val holdings = state.holdings
    if (holdings.isEmpty()) return
    CardSurface {
        Text("My assets", style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
        holdings.forEach { holding ->
            val stat = state.holdingStats[holding.symbol]
            Row(
                Modifier
                    .fillMaxWidth()
                    .fcPressable { onOpenSymbol(holding.symbol) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompanyLogo(holding.symbol, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(holding.symbol, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                    Text(
                        CalcFormat.fixed(holding.weight * 100, 1) + "% of portfolio",
                        style = Theme.sans(11),
                        color = Theme.colors.mutedForeground,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "$" + CalcFormat.decimal(holding.value, 2),
                        style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                    )
                    stat?.let { ChangePill(it.changePct) }
                }
                Text(
                    "Sell",
                    style = Theme.sans(11, FontWeight.Bold),
                    color = Theme.colors.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Theme.colors.brandTint)
                        .fcPressable { onTrade(holding.symbol, true) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// MARK: - Orders

@Composable
private fun OrdersCard(state: PortfolioUiState, onCancel: (SnapTradeOrder) -> Unit) {
    val orders = state.visibleOrders
    if (orders.isEmpty()) return
    val pending = state.pendingOrders.map { it.id }.toSet()
    CardSurface {
        Text("Recent orders", style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
        orders.take(10).forEach { order ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        listOfNotNull(order.action, order.symbol).joinToString(" "),
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            order.status,
                            order.totalQuantity?.let { CalcFormat.fixed(it, 2).trimEnd('0').trimEnd('.') + " sh" },
                            order.executionPrice?.let { "at " + MarketFormat.price(it) },
                            order.timePlaced?.let { stamp ->
                                runCatching {
                                    HistoryDate.medium(
                                        Instant.parse(stamp).atZone(ZoneId.systemDefault()).toLocalDate()
                                    )
                                }.getOrNull()
                            },
                        ).joinToString(" · "),
                        style = Theme.sans(11),
                        color = Theme.colors.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (order.id in pending && order.accountId != null && order.brokerageOrderId != null) {
                    Text(
                        "Cancel",
                        style = Theme.sans(11, FontWeight.Bold),
                        color = Theme.colors.destructive,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Theme.colors.destructive.copy(alpha = 0.1f))
                            .fcPressable { onCancel(order) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Manage

@Composable
private fun ManageRow(
    syncing: Boolean,
    syncUnavailable: Boolean,
    onSync: () -> Unit,
    onAccounts: () -> Unit,
    onAddBrokerage: () -> Unit,
    onDisconnect: () -> Unit,
) {
    CardSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(16.dp))
            Text(
                if (syncing) "Syncing holdings…" else "Sync holdings now",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.primary,
                modifier = Modifier.fcPressable(onSync),
            )
        }
        if (syncUnavailable) {
            // 0 of N refreshed: SnapTrade declined every manual sync, so
            // nothing fresher is coming and the copy must not promise it.
            Text(
                "Your brokerage declined an on-demand sync, so holdings update on their usual daily schedule.",
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
            )
        }
        Text(
            "Choose accounts",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier.padding(top = 4.dp).fcPressable(onAccounts),
        )
        Text(
            "Add another brokerage",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier.padding(top = 4.dp).fcPressable(onAddBrokerage),
        )
        Text(
            "Disconnect",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.destructive,
            modifier = Modifier.padding(top = 4.dp).fcPressable(onDisconnect),
        )
    }
}

@Composable
private fun AccountSelectionSheet(
    state: PortfolioUiState,
    viewModel: PortfolioViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accounts in this portfolio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.accounts.forEach { account ->
                    val selected = account.id !in state.deselectedAccounts
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAccountSelected(account.id, !selected) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.name.ifEmpty { account.institution },
                                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                                color = Theme.colors.foreground,
                            )
                            Text(
                                listOfNotNull(
                                    account.institution,
                                    account.number.takeIf { it.isNotEmpty() }?.let { "••" + it.takeLast(4) },
                                    account.totalValue?.let { "$" + CalcFormat.int(it) },
                                ).joinToString(" · "),
                                style = Theme.sans(11),
                                color = Theme.colors.mutedForeground,
                            )
                        }
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text(
                    "Unticking an account leaves its holdings out of every figure on this page. Nothing at " +
                        "the brokerage changes.",
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.mutedForeground,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun CardSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
