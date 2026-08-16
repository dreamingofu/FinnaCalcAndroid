//
// BrokerPickerSheet.kt
//
// Port of iOS Features/Investing/BrokerPickerSheet.swift — FinnaCalc's own
// "pick your brokerage" screen, shown before the SnapTrade window instead of
// dropping users straight into SnapTrade's list. Search on top, brokerages
// under it, and ONE confirm step holding the access choice (view only / view
// and trade) plus what a connection may or may not allow.
//
// Capability honesty: which brokerages connect view-only or without
// fractional orders is decided by each brokerage and can change, so the
// confirm step explains the CATEGORIES and says the connect window shows
// exactly what this one supports. The brokerages SnapTrade documents as
// read-only sit at the bottom and say so on their row.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.market.BrandLogo
import com.finnacalc.android.core.snaptrade.BrokerageAccess
import com.finnacalc.android.core.snaptrade.SnapTradeBrokerage
import com.finnacalc.android.core.snaptrade.SnapTradeService

data class BrokerChoice(
    val name: String,
    /** Website, for the logo fallback. Empty when the catalog gave none. */
    val domain: String,
    /** SnapTrade brokerage slug passed to the connect portal. */
    val slug: String,
    /** Documented as view-only through SnapTrade. */
    val viewOnly: Boolean = false,
    /** SnapTrade's own square logo, when the catalog carries one. */
    val logo: String? = null,
)

object BrokerCatalog {
    /**
     * Offline fallback only. The picker loads SnapTrade's real catalog and
     * uses this list solely when that call fails, so a dead network still
     * shows something recognisable. Slugs and trading flags here were WRONG
     * before being checked against the live catalog — which is exactly why
     * this is the fallback, not the source.
     */
    val popular: List<BrokerChoice> = listOf(
        BrokerChoice("Webull US", "webull.com", "WEBULL"),
        BrokerChoice("E*Trade", "etrade.com", "ETRADE"),
        BrokerChoice("Public", "public.com", "PUBLIC"),
        BrokerChoice("moomoo", "moomoo.com", "MOOMOO"),
        BrokerChoice("Coinbase", "coinbase.com", "COINBASE"),
        BrokerChoice("Wealthsimple", "wealthsimple.com", "WEALTHSIMPLETRADE"),
        BrokerChoice("tastytrade", "tastytrade.com", "TASTYTRADE"),
        BrokerChoice("Questrade", "questrade.com", "QUESTRADE"),
        BrokerChoice("Robinhood", "robinhood.com", "ROBINHOOD", viewOnly = true),
        BrokerChoice("Schwab", "schwab.com", "SCHWAB", viewOnly = true),
        BrokerChoice("Fidelity", "fidelity.com", "FIDELITY", viewOnly = true),
        BrokerChoice("Vanguard US", "vanguard.com", "VANGUARD", viewOnly = true),
        BrokerChoice("Interactive Brokers", "interactivebrokers.com", "INTERACTIVE-BROKERS-FLEX", viewOnly = true),
        BrokerChoice("eToro", "etoro.com", "ETORO", viewOnly = true),
        BrokerChoice("Chase", "chase.com", "CHASE", viewOnly = true),
    )

    fun from(brokerage: SnapTradeBrokerage): BrokerChoice = BrokerChoice(
        name = brokerage.name,
        domain = brokerage.domain ?: "",
        slug = brokerage.slug,
        viewOnly = brokerage.allowsTrading == false,
        logo = brokerage.logo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokerPickerSheet(
    onDismiss: () -> Unit,
    onConnect: (slug: String?, access: BrokerageAccess) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<BrokerChoice>>(emptyList()) }
    var chosen by remember { mutableStateOf<BrokerChoice?>(null) }

    LaunchedEffect(Unit) {
        val live = runCatching { SnapTradeService.brokerages() }.getOrNull()
        catalog = live?.brokerages
            ?.filter { it.enabled != false && it.maintenanceMode != true }
            ?.map(BrokerCatalog::from)
            ?.takeIf { it.isNotEmpty() }
            ?: BrokerCatalog.popular
    }

    val list = remember(catalog, query) {
        val source = catalog.ifEmpty { BrokerCatalog.popular }
        val filtered = if (query.isBlank()) source
        else source.filter { it.name.contains(query.trim(), ignoreCase = true) }
        // View-only brokerages sit at the bottom, as on iOS.
        filtered.sortedBy { it.viewOnly }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
    ) {
        val target = chosen
        if (target == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Choose your brokerage", style = Theme.sans(18, FontWeight.Bold), color = Theme.colors.foreground)
                FCTextField("Search brokerages", query, { query = it }, showsPlaceholder = true)
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    list.forEach { broker ->
                        BrokerRow(broker) { chosen = broker }
                    }
                    Text(
                        "Can't find yours? Continue without picking and the connect window lists every brokerage SnapTrade supports.",
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.mutedForeground,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Text(
                        "Continue without picking",
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                        color = Theme.colors.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(Theme.colors.brandTint)
                            .fcPressable { chosen = BrokerChoice("Any brokerage", "", "") }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        } else {
            ConfirmAccess(
                broker = target,
                onBack = { chosen = null },
                onConnect = { access ->
                    onConnect(target.slug.takeIf { it.isNotEmpty() }, access)
                },
            )
        }
    }
}

@Composable
private fun BrokerRow(broker: BrokerChoice, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val logo = broker.logo?.takeIf { it.isNotEmpty() }
        ?: broker.domain.takeIf { it.isNotEmpty() }?.let { BrandLogo.domain(it, 44f, density) }
    Row(
        Modifier
            .fillMaxWidth()
            .fcPressable(onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Theme.Radius.md))
                .background(Theme.colors.secondary),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                Text(
                    broker.name.take(1).uppercase(),
                    style = Theme.sans(15, FontWeight.Bold),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                broker.name,
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (broker.viewOnly) {
                Text(
                    "Holdings only — orders stay in their app",
                    style = Theme.sans(11),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
    }
}

/**
 * ONE confirm step: the access choice plus what a connection may or may not
 * allow. The categories are explained; the connect window itself shows what
 * this brokerage actually supports.
 */
@Composable
private fun ConfirmAccess(
    broker: BrokerChoice,
    onBack: () -> Unit,
    onConnect: (BrokerageAccess) -> Unit,
) {
    var access by remember { mutableStateOf(BrokerageAccess.View) }
    Column(
        Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Back",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.primary,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Text("Connect ${broker.name}", style = Theme.sans(18, FontWeight.Bold), color = Theme.colors.foreground)

        listOf(
            BrokerageAccess.View to ("View only" to "FinnaCalc reads your holdings and orders. It can never place a trade."),
            BrokerageAccess.Trade to ("View and trade" to "Adds placing orders from FinnaCalc. Every order still shows a review step with the brokerage's own figures before anything executes."),
        ).forEach { (value, copy) ->
            val selected = access == value
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.lg))
                    .background(if (selected) Theme.colors.brandTint else Theme.colors.card)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) Theme.colors.primary else Theme.colors.border,
                        RoundedCornerShape(Theme.Radius.lg),
                    )
                    .clickable { access = value }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(copy.first, style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold), color = Theme.colors.foreground)
                Text(copy.second, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
            }
        }

        if (broker.viewOnly) {
            Text(
                "${broker.name} connects for viewing only through SnapTrade. Your holdings show in FinnaCalc; " +
                    "orders are placed in ${broker.name} itself.",
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.caution,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .background(Theme.colors.cautionTint)
                    .padding(10.dp),
            )
        }

        Text(
            "Access is fixed for the life of the connection — changing it later means disconnecting and " +
                "linking again. The connect window shows exactly what this brokerage supports, including " +
                "whether it allows fractional orders.",
            style = Theme.sans(Theme.FontSize.xs),
            color = Theme.colors.mutedForeground,
        )

        Text(
            "Continue",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable { onConnect(access) }
                .padding(vertical = 13.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}
