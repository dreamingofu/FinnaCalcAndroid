//
// EntitlementStore.kt
//
// Port of iOS Features/Plans/EntitlementStore.swift onto Google Play Billing.
// Which paid tier the user holds, straight from Play: entitlements come from
// queryPurchasesAsync, which Play delivers signed and verified — nothing here
// trusts a plain flag.
//
// Entitlements follow the GOOGLE ACCOUNT, not the FinnaCalc account: no
// sign-in is required to buy or to hold a plan, and signing out of FinnaCalc
// doesn't touch it. Nothing in the app is feature-gated on this yet (see
// Entitlements in GoalSupport.kt); the store exists so the Plans screen and
// the Account row can show the truth.
//
// PRODUCT SHAPE — deviation from iOS, deliberate. StoreKit models this as six
// flat products (com.finnacalc.<tier>.<interval>). Play's subscription model
// is one subscription per tier with a base plan per interval, which is what
// makes switching monthly↔annual prorate natively, so that is what this uses:
//
//     subscription id   finnacalc_pro | finnacalc_plus | finnacalc_trader
//     base plan id      monthly | annual
//
// These must match Play Console exactly. Until the products exist there,
// queryProductDetails returns nothing and the screen falls back to the
// catalog's USD prices with purchasing reported as unavailable — it never
// shows a price Play didn't quote as if Play had quoted it.
//

package com.finnacalc.android.features.plans

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** What a purchase attempt came back as, for the UI's messaging. */
enum class PurchaseOutcome {
    Success,
    Cancelled,
    /** Deferred approval (a pending purchase) — not an error, not yet an entitlement. */
    Pending,
    /** Play has no product for this plan, so nothing could be launched. */
    Unavailable,
}

class EntitlementStore(context: Context) {

    /** null = free. Highest active tier wins (pro beats plus/trader). */
    private val _activeTier = MutableStateFlow<PlanTier?>(null)
    val activeTier: StateFlow<PlanTier?> = _activeTier.asStateFlow()

    private val _activeInterval = MutableStateFlow<PlanBillingInterval?>(null)
    val activeInterval: StateFlow<PlanBillingInterval?> = _activeInterval.asStateFlow()

    /** Play product details, keyed by subscription id; empty until loaded. */
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /**
     * Set when Play itself can't serve this build — no Play Store, or the
     * subscriptions haven't been published. The screen says so rather than
     * offering a button that can only fail.
     */
    private val _storeUnavailable = MutableStateFlow<String?>(null)
    val storeUnavailable: StateFlow<String?> = _storeUnavailable.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Purchases arriving outside a launched flow (a renewal, a pending
     * purchase clearing, a purchase made on another device) land here for the
     * app's whole lifetime; missing them loses purchases made mid-session.
     */
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                scope.launch { ingest(purchases) }
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private var connected = false

    // MARK: Product identity

    companion object {
        /** One subscription per tier; the interval is a base plan inside it. */
        fun subscriptionId(tier: PlanTier): String = "finnacalc_${tier.raw}"

        fun basePlanId(interval: PlanBillingInterval): String = interval.raw

        fun tierFor(subscriptionId: String): PlanTier? =
            PlanTier.entries.firstOrNull { subscriptionId(it) == subscriptionId }

        /** Pro outranks the single-side plans when more than one is active. */
        private val TIER_RANK = mapOf(PlanTier.Plus to 1, PlanTier.Trader to 1, PlanTier.Pro to 2)
    }

    // MARK: Connection

    private suspend fun connect(): Boolean {
        if (connected && client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                private var resumed = false
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (resumed) return
                    resumed = true
                    connected = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!connected) {
                        _storeUnavailable.value = playMessage(result)
                    }
                    cont.resume(connected)
                }

                override fun onBillingServiceDisconnected() {
                    connected = false
                    if (!resumed) {
                        resumed = true
                        cont.resume(false)
                    }
                }
            })
        }
    }

    // MARK: Products

    suspend fun loadProducts() {
        _loading.value = true
        try {
            if (!connect()) return
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    PlanTier.entries.map { tier ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(subscriptionId(tier))
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    }
                )
                .build()
            val result = client.queryProductDetails(params)
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _storeUnavailable.value = playMessage(result.billingResult)
                return
            }
            val found = result.productDetailsList.orEmpty()
            _products.value = found.associateBy { it.productId }
            _storeUnavailable.value = if (found.isEmpty()) {
                "Subscriptions aren't available from this build yet."
            } else null
        } finally {
            _loading.value = false
        }
    }

    /**
     * Play's own localized price for a plan's interval, or null when Play
     * hasn't quoted one — the caller falls back to the catalog rather than
     * showing a Play-shaped price Play never returned.
     */
    fun displayPrice(tier: PlanTier, interval: PlanBillingInterval): String? =
        offerFor(tier, interval)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    private fun offerFor(
        tier: PlanTier,
        interval: PlanBillingInterval,
    ): ProductDetails.SubscriptionOfferDetails? =
        _products.value[subscriptionId(tier)]
            ?.subscriptionOfferDetails
            ?.firstOrNull { it.basePlanId == basePlanId(interval) }

    // MARK: Entitlements

    suspend fun refresh() {
        if (!connect()) return
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        ingest(result.purchasesList)
    }

    /** Play's restore is a re-read of the account's purchases. */
    suspend fun restore() = refresh()

    private suspend fun ingest(purchases: List<Purchase>) {
        // Only a fully purchased subscription is an entitlement; a pending one
        // has not been paid for and must not unlock anything.
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }

        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            )
        }

        val tiers = active.flatMap { it.products }.mapNotNull { tierFor(it) }
        val best = tiers.maxByOrNull { TIER_RANK[it] ?: 0 }
        _activeTier.value = best
        // Play reports the subscription, not which base plan is running; the
        // interval is only known when its product details are loaded and a
        // single base plan matches. Left null rather than guessed.
        _activeInterval.value = best?.let { tier ->
            val plans = _products.value[subscriptionId(tier)]?.subscriptionOfferDetails
                ?.map { it.basePlanId }.orEmpty().distinct()
            if (plans.size == 1) PlanBillingInterval.entries.firstOrNull { it.raw == plans.first() } else null
        }
    }

    // MARK: Purchase

    suspend fun purchase(
        activity: Activity,
        tier: PlanTier,
        interval: PlanBillingInterval,
    ): PurchaseOutcome {
        if (!connect()) return PurchaseOutcome.Unavailable
        val details = _products.value[subscriptionId(tier)] ?: return PurchaseOutcome.Unavailable
        val offer = offerFor(tier, interval) ?: return PurchaseOutcome.Unavailable

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()

        val launch = client.launchBillingFlow(activity, params)
        return when (launch.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                // The real outcome arrives on the purchases listener; re-read
                // so a completed purchase is reflected without a manual pull.
                refresh()
                if (_activeTier.value != null) PurchaseOutcome.Success else PurchaseOutcome.Pending
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refresh()
                PurchaseOutcome.Success
            }

            else -> throw BillingException(playMessage(launch))
        }
    }

    /** Play's subscription centre, deep-linked to this product when known. */
    fun manageSubscriptionUrl(packageName: String, tier: PlanTier?): String =
        if (tier != null) {
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${subscriptionId(tier)}&package=$packageName"
        } else {
            "https://play.google.com/store/account/subscriptions"
        }

    private fun playMessage(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play billing isn't available on this device."

        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            -> "Google Play is unreachable right now."

        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "This plan isn't available on your account."

        else -> result.debugMessage.ifBlank { "Google Play returned an error (${result.responseCode})." }
    }
}

class BillingException(message: String) : Exception(message)
