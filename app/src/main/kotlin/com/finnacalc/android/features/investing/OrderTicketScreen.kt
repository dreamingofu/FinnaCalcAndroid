//
// OrderTicketScreen.kt
//
// Port of iOS Features/Investing/OrderTicketView.swift — SnapTrade's two-step
// order flow drives the UX: the amount screen gathers terms, "Review"
// validates them with the brokerage (/trade/impact — nothing executes), the
// review screen shows the brokerage's own numbers, and only "Confirm"
// executes (/trade/place with the locked tradeId).
//
// Dollar (notional) orders are Market + Day only and brokerage-dependent —
// the backend enforces those terms; if a broker doesn't support them the
// impact call surfaces the error. Limit orders use a share-quantity form.
//
// The unconfirmed-place state is terminal on purpose: when a failure can't
// prove the order did NOT reach the brokerage, there is no retry path,
// because retrying a maybe-executed order is how duplicates happen.
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.networking.ApiException
import com.finnacalc.android.core.snaptrade.BrokerageAccount
import com.finnacalc.android.core.snaptrade.SnapTradeConnection
import com.finnacalc.android.core.snaptrade.SnapTradeOrder
import com.finnacalc.android.core.snaptrade.SnapTradeOrderImpact
import com.finnacalc.android.core.snaptrade.SnapTradeQuote
import com.finnacalc.android.core.snaptrade.SnapTradeService
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.CalcSegmentedControl
import com.finnacalc.android.features.calculators.calcValue
import kotlinx.coroutines.launch

private enum class TradeAction(val label: String, val raw: String) {
    Buy("Buy", "BUY"), Sell("Sell", "SELL")
}

private enum class EntryMode(val label: String) {
    Dollars("Dollars"), Shares("Shares"), Limit("Limit")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTicketSheet(
    symbol: String,
    companyName: String? = null,
    startWithSell: Boolean = false,
    onDismiss: () -> Unit,
    onPlaced: () -> Unit,
) {
    var accounts by remember { mutableStateOf<List<BrokerageAccount>>(emptyList()) }
    var connections by remember { mutableStateOf<List<SnapTradeConnection>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var loadingAccounts by remember { mutableStateOf(true) }
    var quote by remember { mutableStateOf<SnapTradeQuote?>(null) }

    var action by remember { mutableStateOf(if (startWithSell) TradeAction.Sell else TradeAction.Buy) }
    var mode by remember { mutableStateOf(EntryMode.Dollars) }
    var amountText by remember { mutableStateOf("") }
    var limitPriceText by remember { mutableStateOf("") }

    // Flow state: entry → review (impact) → placed | unconfirmed.
    var impact by remember { mutableStateOf<SnapTradeOrderImpact?>(null) }
    var placed by remember { mutableStateOf<SnapTradeOrder?>(null) }
    var placeUnconfirmed by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val data = runCatching { SnapTradeService.accounts() }.getOrNull()
        accounts = data?.accounts ?: emptyList()
        selectedAccountId = accounts.firstOrNull()?.id
        connections = runCatching { SnapTradeService.connections().connections }.getOrDefault(emptyList())
        loadingAccounts = false
    }

    LaunchedEffect(selectedAccountId) {
        val id = selectedAccountId ?: return@LaunchedEffect
        quote = runCatching { SnapTradeService.quote(id, symbol) }.getOrNull()
    }

    val account = accounts.firstOrNull { it.id == selectedAccountId }
    val connection = connections.firstOrNull { it.id == account?.connectionId }
    val blockedReason = connection?.tradingBlockedReason

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${action.label} ${symbol.uppercase()}",
                style = Theme.sans(20, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            companyName?.let {
                Text(it, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
            }

            when {
                loadingAccounts -> CircularProgressIndicator(color = Theme.colors.primary)

                accounts.isEmpty() -> Text(
                    "Connect a brokerage in Portfolio before placing an order.",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                )

                placed != null -> PlacedState(placed!!, onDismiss)

                placeUnconfirmed != null -> UnconfirmedState(placeUnconfirmed!!, onDismiss)

                impact != null -> ReviewState(
                    impact = impact!!,
                    action = action,
                    symbol = symbol,
                    working = working,
                    error = error,
                    onBack = {
                        impact = null
                        error = null
                    },
                    onConfirm = {
                        working = true
                        error = null
                        scope.launch {
                            val tradeId = impact!!.tradeId
                            try {
                                placed = SnapTradeService.placeOrder(tradeId)
                                onPlaced()
                            } catch (e: ApiException) {
                                // A failure that can't prove the order didn't
                                // reach the brokerage is terminal: no retry.
                                placeUnconfirmed = e.message
                            }
                            working = false
                        }
                    },
                )

                else -> {
                    CalcSegmentedControl(action, { action = it }, TradeAction.entries.map { it to it.label })

                    // Which account the order hits — an order goes to exactly one.
                    if (accounts.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Account", style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold), color = Theme.colors.mutedForeground)
                            accounts.forEach { candidate ->
                                val selected = candidate.id == selectedAccountId
                                Text(
                                    listOfNotNull(
                                        candidate.name.ifEmpty { candidate.institution },
                                        candidate.cash?.let { "$" + CalcFormat.int(it) + " cash" },
                                    ).joinToString(" · "),
                                    style = Theme.sans(Theme.FontSize.sm, if (selected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (selected) Theme.colors.foreground else Theme.colors.mutedForeground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Theme.Radius.md))
                                        .background(if (selected) Theme.colors.brandTint else Color.Transparent)
                                        .clickable { selectedAccountId = candidate.id }
                                        .padding(10.dp),
                                )
                            }
                        }
                    }

                    blockedReason?.let {
                        Text(
                            it,
                            style = Theme.sans(Theme.FontSize.xs),
                            color = Theme.colors.caution,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Theme.Radius.md))
                                .background(Theme.colors.cautionTint)
                                .padding(10.dp),
                        )
                    }

                    CalcSegmentedControl(mode, { mode = it }, EntryMode.entries.map { it to it.label })

                    FCTextField(
                        when (mode) {
                            EntryMode.Dollars -> "Amount in dollars"
                            EntryMode.Shares -> "Number of shares"
                            EntryMode.Limit -> "Number of shares"
                        },
                        amountText,
                        { amountText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        showsPlaceholder = true,
                    )
                    if (mode == EntryMode.Limit) {
                        FCTextField(
                            "Limit price", limitPriceText, { limitPriceText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            showsPlaceholder = true,
                        )
                    }

                    quote?.let { q ->
                        val parts = listOfNotNull(
                            q.last?.let { "Last " + MarketFormat.price(it) },
                            q.bid?.let { "Bid " + MarketFormat.price(it) },
                            q.ask?.let { "Ask " + MarketFormat.price(it) },
                        )
                        if (parts.isNotEmpty()) {
                            Text(
                                parts.joinToString(" · "),
                                style = Theme.figure(Theme.FontSize.xs),
                                color = Theme.colors.mutedForeground,
                            )
                        }
                    }

                    if (mode == EntryMode.Dollars) {
                        Text(
                            "Dollar orders are Market and Day only, and only at brokerages that support them.",
                            style = Theme.sans(Theme.FontSize.xs),
                            color = Theme.colors.mutedForeground,
                        )
                    }

                    error?.let {
                        Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.destructive)
                    }

                    val amount = amountText.calcValue
                    val limitPrice = limitPriceText.calcValue
                    val canReview = amount > 0 &&
                        (mode != EntryMode.Limit || limitPrice > 0) &&
                        selectedAccountId != null && !working

                    Text(
                        if (working) "Checking with your brokerage…" else "Review order",
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(Theme.colors.primary.copy(alpha = if (canReview) 1f else 0.5f))
                            .then(
                                if (canReview) Modifier.fcPressable {
                                    working = true
                                    error = null
                                    scope.launch {
                                        val accountId = selectedAccountId!!
                                        try {
                                            impact = when (mode) {
                                                // Nothing is bought or sold by
                                                // the impact call.
                                                EntryMode.Dollars -> SnapTradeService.orderImpactNotional(
                                                    accountId, symbol, action.raw, amount,
                                                )
                                                EntryMode.Shares -> SnapTradeService.orderImpact(
                                                    accountId, symbol, action.raw, "Market", "Day", amount, null,
                                                )
                                                EntryMode.Limit -> SnapTradeService.orderImpact(
                                                    accountId, symbol, action.raw, "Limit", "Day", amount, limitPrice,
                                                )
                                            }
                                        } catch (e: ApiException) {
                                            error = e.message
                                        }
                                        working = false
                                    }
                                } else Modifier
                            )
                            .padding(vertical = 14.dp),
                    )
                    Text(
                        "Nothing is bought or sold until you confirm on the next screen.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ReviewState(
    impact: SnapTradeOrderImpact,
    action: TradeAction,
    symbol: String,
    working: Boolean,
    error: String?,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review", style = Theme.sans(16, FontWeight.Bold), color = Theme.colors.foreground)
        CardSurface {
            // The brokerage's own numbers, from the locked tradeId.
            listOfNotNull(
                "Order" to "${impact.action ?: action.raw} ${impact.symbol ?: symbol.uppercase()}",
                impact.units?.let { "Shares" to CalcFormat.fixed(it, 4).trimEnd('0').trimEnd('.') },
                impact.notionalValue?.let { "Amount" to ("$" + CalcFormat.decimal(it, 2)) },
                impact.price?.let { "Price" to MarketFormat.price(it) },
                impact.estimatedCommission?.let { "Commission" to ("$" + CalcFormat.decimal(it, 2)) },
                impact.forexFees?.let { "FX fees" to ("$" + CalcFormat.decimal(it, 2)) },
                impact.remainingCash?.let { "Cash after" to ("$" + CalcFormat.decimal(it, 2)) },
                impact.exchange?.let { "Routes to" to it },
            ).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(label, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground, modifier = Modifier.weight(1f))
                    Text(value, style = Theme.figure(Theme.FontSize.sm, FontWeight.SemiBold), color = Theme.colors.foreground)
                }
            }
        }
        error?.let { Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.destructive) }
        Text(
            if (working) "Placing…" else "Confirm order",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .then(if (working) Modifier else Modifier.fcPressable(onConfirm))
                .padding(vertical = 14.dp),
        )
        Text(
            "Back",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !working, onClick = onBack),
        )
    }
}

@Composable
private fun PlacedState(order: SnapTradeOrder, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Order placed", style = Theme.sans(18, FontWeight.Bold), color = Theme.colors.positive)
        Text(
            listOfNotNull(
                order.status?.let { "Status: $it" },
                order.symbol,
                order.totalQuantity?.let { CalcFormat.fixed(it, 4).trimEnd('0').trimEnd('.') + " shares" },
            ).joinToString(" · "),
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
        )
        Text(
            "Your brokerage decides when it fills. Holdings here update on their next sync.",
            style = Theme.sans(Theme.FontSize.xs),
            color = Theme.colors.mutedForeground,
        )
        Text(
            "Done",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onDismiss)
                .padding(vertical = 13.dp),
        )
    }
}

@Composable
private fun UnconfirmedState(message: String, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("We can't confirm this order", style = Theme.sans(18, FontWeight.Bold), color = Theme.colors.caution)
        Text(message, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
        Text(
            "It may or may not have reached your brokerage. Check your orders there before trying again — " +
                "placing it a second time is how duplicate orders happen.",
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.foreground,
        )
        Text(
            "Close",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onDismiss)
                .padding(vertical = 13.dp),
        )
    }
}
