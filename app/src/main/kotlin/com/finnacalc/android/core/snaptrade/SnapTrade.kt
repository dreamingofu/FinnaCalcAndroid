//
// SnapTrade.kt
//
// Port of iOS Core/SnapTrade/{SnapTradeModels,SnapTradeService}.swift —
// mirrors /api/snaptrade/*. The SnapTrade session lives in an httpOnly cookie
// set on the connect response; ApiClient's cookie jar carries it to the
// accounts call automatically (the OkHttp analogue of URLSession's shared
// cookie storage).
//

package com.finnacalc.android.core.snaptrade

import com.finnacalc.android.core.networking.ApiClient
import com.finnacalc.android.core.networking.ApiException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// MARK: - Models

@Serializable
data class BrokerageAccount(
    val id: String,
    val name: String,
    val institution: String,
    val number: String,
    val totalValue: Double? = null,
    /** Available cash (buying power) — shown on the order ticket. */
    val cash: Double? = null,
    val currency: String,
    /** The connection this account belongs to (maps to SnapTradeConnection.id). */
    val connectionId: String? = null,
)

@Serializable
data class BrokeragePosition(
    val accountId: String,
    val symbol: String,
    val description: String,
    val units: Double,
    val price: Double? = null,
    val marketValue: Double? = null,
    val openPnl: Double? = null,
)

@Serializable
data class SnapTradeAccountsResponse(
    val configured: Boolean,
    val connected: Boolean? = null,
    val accounts: List<BrokerageAccount>,
    val positions: List<BrokeragePosition>,
    val totalValue: Double? = null,
    val currency: String? = null,
    val error: String? = null,
)

@Serializable
data class SnapTradeConnectResponse(val redirectURI: String)

/** One row of SnapTrade's brokerage catalog. */
@Serializable
data class SnapTradeBrokerage(
    val slug: String,
    val name: String,
    val url: String? = null,
    val logo: String? = null,
    /**
     * Whether SnapTrade can place orders at this brokerage at all. null when
     * the catalog doesn't say; false means view-only, no matter what the
     * user picks on the way in.
     */
    val allowsTrading: Boolean? = null,
    val enabled: Boolean? = null,
    val maintenanceMode: Boolean? = null,
) {
    /** Domain for the favicon, from the brokerage's own URL. */
    val domain: String?
        get() {
            val u = url ?: return null
            val host = u.substringAfter("://").substringBefore("/")
            if (host.isEmpty()) return null
            return if (host.startsWith("www.")) host.drop(4) else host
        }
}

@Serializable
data class SnapTradeBrokeragesResponse(
    val configured: Boolean,
    val brokerages: List<SnapTradeBrokerage>,
    val error: String? = null,
)

// MARK: - Connections (health / reconnect)

/**
 * One brokerage connection. `disabled` means SnapTrade lost its access token
 * and the user must reconnect; `type` is "read" or "trade".
 */
@Serializable
data class SnapTradeConnection(
    val id: String,
    val brokerage: String,
    val disabled: Boolean,
    val type: String? = null,
    val allowsTrading: Boolean? = null,
    val allowsFractionalUnits: Boolean? = null,
) {
    /**
     * Whether orders can actually be placed through this connection, and if
     * not, whether reconnecting would help.
     */
    enum class TradingAvailability {
        Allowed,
        /** Fixed by RE-AUTHORISING this same connection, not by relinking. */
        ConnectionIsViewOnly,
        /** The brokerage doesn't offer trading through SnapTrade at all. */
        BrokerageUnsupported,
        /** An older backend that sends neither field. Never block on a guess. */
        Unknown,
    }

    val tradingAvailability: TradingAvailability
        get() {
            if (allowsTrading == false) return TradingAvailability.BrokerageUnsupported
            val t = type?.lowercase() ?: return TradingAvailability.Unknown
            return if (t == "trade") TradingAvailability.Allowed else TradingAvailability.ConnectionIsViewOnly
        }

    /** Sentence for the UI, or null when orders can go through. */
    val tradingBlockedReason: String?
        get() = when (tradingAvailability) {
            TradingAvailability.Allowed, TradingAvailability.Unknown -> null
            TradingAvailability.ConnectionIsViewOnly ->
                // "Enable trading" ASKS for order access; the brokerage can
                // still decline, so the copy promises a request, not a result.
                "$brokerage is linked for viewing, so orders are placed in $brokerage itself. " +
                    "Enable trading asks $brokerage for order access; if it declines, this stays view-only."
            TradingAvailability.BrokerageUnsupported ->
                "$brokerage doesn't support placing orders through FinnaCalc. Your holdings show here " +
                    "and orders go in $brokerage itself."
        }
}

@Serializable
data class SnapTradeConnectionsResponse(
    val configured: Boolean,
    val connections: List<SnapTradeConnection>,
)

/**
 * Result of POST /refresh — how many connections SnapTrade agreed to sync.
 * 0 of N means the manual sync was declined (billed add-on / rate limit).
 */
@Serializable
data class SnapTradeRefreshResponse(
    val refreshed: Int? = null,
    val total: Int? = null,
) {
    val accepted: Boolean get() = (refreshed ?: 0) > 0
}

// MARK: - Trading

/** Live brokerage quote for the order ticket. */
@Serializable
data class SnapTradeQuote(
    val symbol: String? = null,
    val bid: Double? = null,
    val ask: Double? = null,
    val last: Double? = null,
)

/**
 * The validated-but-not-executed order returned by the impact (Review) step.
 * `tradeId` is what /trade/place executes — the terms are locked server-side,
 * so what the user reviewed is exactly what runs.
 */
@Serializable
data class SnapTradeOrderImpact(
    val tradeId: String,
    val symbol: String? = null,
    val action: String? = null,
    val units: Double? = null,
    val price: Double? = null,
    /** Dollar amount for notional orders; null for share orders. */
    val notionalValue: Double? = null,
    val estimatedCommission: Double? = null,
    val forexFees: Double? = null,
    val remainingCash: Double? = null,
    val currency: String? = null,
    /** Which listing the order routes to (cross-listed symbols). */
    val exchange: String? = null,
    val symbolCurrency: String? = null,
)

/** A placed (or historical) order record. */
@Serializable
data class SnapTradeOrder(
    val brokerageOrderId: String? = null,
    val status: String? = null,
    val symbol: String? = null,
    val action: String? = null,
    val totalQuantity: Double? = null,
    val filledQuantity: Double? = null,
    val executionPrice: Double? = null,
    val limitPrice: Double? = null,
    val orderType: String? = null,
    val timeInForce: String? = null,
    val timePlaced: String? = null,
    /** The account this order belongs to — needed to cancel it. */
    val accountId: String? = null,
) {
    val id: String get() = brokerageOrderId ?: "${symbol ?: ""}-${action ?: ""}-${timePlaced ?: ""}"
}

@Serializable
data class SnapTradeOrdersResponse(val orders: List<SnapTradeOrder>)

// MARK: - Service

/**
 * What the user allows a brokerage connection to do. Chosen before the portal
 * opens; the backend treats anything it doesn't recognise as view, so trading
 * is never granted by accident.
 */
enum class BrokerageAccess(val raw: String) {
    View("read"),
    Trade("trade"),
}

object SnapTradeService {
    /**
     * POST /api/snaptrade/connect → portal URL. `platform: "android"` tells
     * the backend to point the portal's redirect at the app's callback scheme
     * instead of the website. `access` is fixed for the life of the
     * connection. `broker` is a SnapTrade slug so the portal opens straight
     * on that brokerage's login; null keeps the portal's own browser.
     */
    suspend fun connect(access: BrokerageAccess, broker: String? = null): String {
        val body = buildJsonObject {
            put("platform", "android")
            put("access", access.raw)
            if (broker != null) put("broker", broker)
        }.toString()
        val response: SnapTradeConnectResponse =
            ApiClient.shared.postJson("/api/snaptrade/connect", body)
        if (response.redirectURI.isEmpty()) throw ApiException.Message("No connection link returned.")
        return response.redirectURI
    }

    /** GET /api/snaptrade/accounts → connected accounts + positions. */
    suspend fun accounts(): SnapTradeAccountsResponse =
        ApiClient.shared.getJson("/api/snaptrade/accounts")

    /** POST /api/snaptrade/disconnect → clears the session cookie. */
    suspend fun disconnect() {
        ApiClient.shared.postData("/api/snaptrade/disconnect", "{}")
    }

    /** GET /api/snaptrade/brokerages → SnapTrade's brokerage catalog (reference data). */
    suspend fun brokerages(): SnapTradeBrokeragesResponse =
        ApiClient.shared.getJson("/api/snaptrade/brokerages")

    /** GET /api/snaptrade/connections → connections with health/type. */
    suspend fun connections(): SnapTradeConnectionsResponse =
        ApiClient.shared.getJson("/api/snaptrade/connections")

    /**
     * POST /api/snaptrade/refresh → asks SnapTrade to sync holdings now.
     * `refreshed == 0` means every manual sync was declined, so nothing new
     * is coming and callers shouldn't wait around for it.
     */
    suspend fun refresh(): SnapTradeRefreshResponse =
        ApiClient.shared.postJson("/api/snaptrade/refresh", "{}")

    /** GET /api/snaptrade/orders?accountId= → recent orders. */
    suspend fun orders(accountId: String): SnapTradeOrdersResponse =
        ApiClient.shared.getJson("/api/snaptrade/orders", mapOf("accountId" to accountId))

    /** POST /api/snaptrade/orders/cancel → cancels an open order. */
    suspend fun cancelOrder(accountId: String, brokerageOrderId: String): SnapTradeOrder {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("brokerageOrderId", brokerageOrderId)
        }.toString()
        return ApiClient.shared.postJson("/api/snaptrade/orders/cancel", body)
    }

    /**
     * POST /api/snaptrade/connect with a `reconnect` connection id → portal
     * URL that re-auths that specific disabled connection. `access` must
     * carry the connection's existing permission level: the backend defaults
     * an absent value to read-only.
     */
    suspend fun reconnect(connectionId: String, access: BrokerageAccess): String {
        val body = buildJsonObject {
            put("platform", "android")
            put("reconnect", connectionId)
            put("access", access.raw)
        }.toString()
        val response: SnapTradeConnectResponse =
            ApiClient.shared.postJson("/api/snaptrade/connect", body)
        if (response.redirectURI.isEmpty()) throw ApiException.Message("No connection link returned.")
        return response.redirectURI
    }

    // MARK: Trading — two-step order flow (impact = Review, place = Confirm)

    /** POST /api/snaptrade/quote → live brokerage quote for the order ticket. */
    suspend fun quote(accountId: String, symbol: String): SnapTradeQuote {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("symbol", symbol)
        }.toString()
        return ApiClient.shared.postJson("/api/snaptrade/quote", body)
    }

    /**
     * POST /api/snaptrade/trade/impact → validates a SHARE-quantity order and
     * returns estimated cost plus the tradeId that placeOrder executes.
     * Nothing is bought or sold by this call.
     */
    suspend fun orderImpact(
        accountId: String,
        symbol: String,
        action: String,
        orderType: String,
        timeInForce: String,
        units: Double,
        price: Double?,
    ): SnapTradeOrderImpact {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("symbol", symbol)
            put("action", action)
            put("orderType", orderType)
            put("timeInForce", timeInForce)
            put("units", units)
            if (price != null) put("price", price)
        }.toString()
        return ApiClient.shared.postJson("/api/snaptrade/trade/impact", body)
    }

    /**
     * POST /api/snaptrade/trade/impact for a DOLLAR-amount (notional) order.
     * The backend forces Market + Day (SnapTrade's notional constraint).
     */
    suspend fun orderImpactNotional(
        accountId: String,
        symbol: String,
        action: String,
        notionalValue: Double,
    ): SnapTradeOrderImpact {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("symbol", symbol)
            put("action", action)
            put("notionalValue", notionalValue)
        }.toString()
        return ApiClient.shared.postJson("/api/snaptrade/trade/impact", body)
    }

    /**
     * POST /api/snaptrade/trade/place → executes a reviewed order. The terms
     * are locked to the tradeId server-side, so this can't place anything
     * other than what the user just confirmed.
     */
    suspend fun placeOrder(tradeId: String): SnapTradeOrder {
        val body = buildJsonObject { put("tradeId", tradeId) }.toString()
        return ApiClient.shared.postJson("/api/snaptrade/trade/place", body)
    }
}
