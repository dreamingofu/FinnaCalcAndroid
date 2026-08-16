//
// PlansScreen.kt
//
// Port of iOS Features/Plans/PlansView.swift — the upgrade screen: FinnaCalc
// Pro (everything) / Budgeting Plus / Investing Plus, opened from the Account
// sheet's PLAN row.
//
// Layout follows the paywall patterns the paid finance apps converge on:
// benefit-led bullets with icons, a Monthly/Annual toggle with the saving
// called out, one visually elevated recommended plan, benefit CTAs ("Start
// Pro"), and a "cancel anytime" line. The Pro card uses the app's established
// inverted-spotlight treatment (fill = foreground, text = background).
//
// Billing is Google Play (see EntitlementStore): "Start …" runs Play's own
// purchase sheet, entitlements come back from Play, and no FinnaCalc sign-in
// is involved — plans follow the Google account. Prices shown are Play's own
// localized prices once products load; the PlanCatalog USD figures are the
// fallback while they do (the same numbers the base plans are configured
// with, never invented), and when Play has no products at all the screen says
// so instead of offering a button that can only fail.
//

package com.finnacalc.android.features.plans

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCBadge
import com.finnacalc.android.core.designsystem.FCBadgeVariant
import com.finnacalc.android.core.designsystem.FCIconChip
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.fcShadow
import kotlinx.coroutines.launch

/** The nearest Activity, which Play's billing flow has to be launched from. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PlansScreen(entitlements: EntitlementStore) {
    var interval by remember { mutableStateOf(PlanBillingInterval.Annual) }
    var workingTier by remember { mutableStateOf<PlanTier?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** The purchase went in but Play hasn't confirmed it yet. */
    var purchasePending by remember { mutableStateOf(false) }

    val currentTier by entitlements.activeTier.collectAsState()
    val unavailable by entitlements.storeUnavailable.collectAsState()
    val products by entitlements.products.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        entitlements.loadProducts()
        entitlements.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.surfaceSunken)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Get more from FinnaCalc",
                style = Theme.sans(22, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Text(
                "Pick the side of your money to level up, or take all of it.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }

        IntervalToggle(interval) { interval = it }

        PlanCatalog.all.forEach { plan ->
            val price = entitlements.displayPrice(plan.tier, interval)
                ?: PlanCatalog.priceString(plan.price(interval))
            val purchasable = products[EntitlementStore.subscriptionId(plan.tier)] != null

            PlanCard(
                plan = plan,
                interval = interval,
                price = price,
                isCurrent = currentTier == plan.tier,
                anyPlanOwned = currentTier != null,
                purchasable = purchasable,
                working = workingTier == plan.tier,
                dimmed = workingTier != null && workingTier != plan.tier,
                onStart = {
                    val activity = context.findActivity()
                    if (activity == null) {
                        error = "The purchase couldn't be started from here."
                        return@PlanCard
                    }
                    if (workingTier != null) return@PlanCard
                    error = null
                    purchasePending = false
                    workingTier = plan.tier
                    scope.launch {
                        try {
                            when (entitlements.purchase(activity, plan.tier, interval)) {
                                PurchaseOutcome.Success, PurchaseOutcome.Cancelled -> Unit
                                PurchaseOutcome.Pending -> purchasePending = true
                                PurchaseOutcome.Unavailable ->
                                    error = "This plan isn't available from Google Play yet."
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "The purchase couldn't be completed. Please try again."
                        } finally {
                            workingTier = null
                        }
                    }
                },
            )
        }

        // Play itself can't serve this build. Said plainly, because every
        // button below it would otherwise look broken instead of unavailable.
        unavailable?.let { message ->
            NoticeBox(
                icon = Icons.Filled.ErrorOutline,
                tint = Theme.colors.caution,
                background = Theme.colors.cautionTint,
                text = "$message Prices shown are the published US prices.",
            )
        }

        error?.let { message ->
            NoticeBox(
                icon = Icons.Filled.ErrorOutline,
                tint = Theme.colors.negative,
                background = Theme.colors.negative.copy(alpha = 0.08f),
                text = message,
            )
        }

        if (purchasePending) {
            NoticeBox(
                icon = Icons.Filled.HourglassEmpty,
                tint = Theme.colors.caution,
                background = Theme.colors.cautionTint,
                text = "Your purchase is waiting on Google Play. The plan activates once it clears.",
            )
        }

        if (currentTier != null) {
            ManageRow {
                val url = entitlements.manageSubscriptionUrl(context.packageName, currentTier)
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            }
        }

        Text(
            if (restoring) "Restoring…" else "Restore purchases",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (restoring) Modifier else Modifier.fcPressable {
                        restoring = true
                        scope.launch {
                            entitlements.restore()
                            restoring = false
                        }
                    }
                ),
        )

        Footer()
    }
}

// MARK: - Interval toggle

@Composable
private fun IntervalToggle(selected: PlanBillingInterval, onSelect: (PlanBillingInterval) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Theme.colors.secondary)
            .padding(3.dp),
    ) {
        PlanBillingInterval.entries.forEach { option ->
            val isOn = option == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isOn) Theme.colors.card else Color.Transparent)
                    .fcPressable { onSelect(option) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.label,
                    style = Theme.sans(13, if (isOn) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (isOn) Theme.colors.foreground else Theme.colors.mutedForeground,
                )
                if (option == PlanBillingInterval.Annual) {
                    Text(
                        "Save up to ${PlanCatalog.maxAnnualSavingsPercent}%",
                        style = Theme.sans(11, FontWeight.Bold),
                        color = Theme.colors.positive,
                    )
                }
            }
        }
    }
}

// MARK: - Plan cards

@Composable
private fun PlanCard(
    plan: Plan,
    interval: PlanBillingInterval,
    price: String,
    isCurrent: Boolean,
    anyPlanOwned: Boolean,
    purchasable: Boolean,
    working: Boolean,
    dimmed: Boolean,
    onStart: () -> Unit,
) {
    val spotlight = plan.recommended
    val shape = RoundedCornerShape(Theme.Radius.xxl)
    // Inverted spotlight: the fill is the foreground token, so the readable
    // values are its inverses — theme tokens would be backwards here.
    val titleColor = if (spotlight) Theme.colors.background else Theme.colors.foreground
    val mutedColor =
        if (spotlight) Theme.colors.background.copy(alpha = 0.65f) else Theme.colors.mutedForeground
    val bodyColor =
        if (spotlight) Theme.colors.background.copy(alpha = 0.92f) else Theme.colors.textBody
    val checkColor = if (spotlight) Theme.colors.brandBlue else Theme.colors.positive

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (spotlight) Modifier.fcShadow(Theme.Elevation.Lg, shape) else Modifier)
            .clip(shape)
            .background(if (spotlight) Theme.colors.foreground else Theme.colors.card)
            .then(
                if (spotlight) Modifier else Modifier.border(1.dp, Theme.colors.border, shape)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Title row
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (spotlight) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (spotlight) {
                Icon(
                    plan.icon,
                    contentDescription = null,
                    tint = Theme.colors.brandBlue,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    plan.name,
                    style = Theme.sans(17, FontWeight.Bold),
                    color = titleColor,
                    modifier = Modifier.weight(1f),
                )
                FCBadge("Best value")
            } else {
                FCIconChip(plan.icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(plan.name, style = Theme.sans(15, FontWeight.Bold), color = titleColor)
                    Text(plan.tagline, style = Theme.sans(12), color = mutedColor)
                }
            }
        }

        // Price line
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(price, style = Theme.figure(30, FontWeight.Bold), color = titleColor)
            Text(
                if (interval == PlanBillingInterval.Monthly) "/month" else "/year",
                style = Theme.sans(Theme.FontSize.sm),
                color = mutedColor,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (interval == PlanBillingInterval.Annual) {
                Text(
                    "Save ${plan.annualSavingsPercent}%",
                    style = Theme.sans(11, FontWeight.Bold),
                    color = Theme.colors.positive,
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 5.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.positive.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        // Benefits
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            plan.benefits.forEach { benefit ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = checkColor,
                        modifier = Modifier.size(14.dp).padding(top = 2.dp),
                    )
                    Text(benefit.text, style = Theme.sans(13), color = bodyColor)
                }
            }
        }

        // CTA, or the owned badge. Switching plans happens through Play's
        // subscription centre, where it prorates inside the subscription.
        when {
            isCurrent -> FCBadge("Current plan", variant = FCBadgeVariant.Positive, dot = true)

            !anyPlanOwned -> {
                val ctaTitle = when (plan.tier) {
                    PlanTier.Plus -> "Start Budgeting Plus"
                    PlanTier.Trader -> "Start Investing Plus"
                    PlanTier.Pro -> "Start Pro"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (dimmed || !purchasable) 0.5f else 1f)
                        .clip(CircleShape)
                        .background(if (spotlight) Theme.colors.primary else Theme.colors.secondary)
                        .then(
                            if (purchasable && !dimmed && !working) Modifier.fcPressable(onStart)
                            else Modifier
                        )
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = if (spotlight) Color.White else Theme.colors.foreground,
                        )
                    }
                    Text(
                        ctaTitle,
                        style = Theme.sans(14, FontWeight.Bold),
                        color = if (spotlight) Color.White else Theme.colors.foreground,
                    )
                }
            }
        }
    }
}

// MARK: - Supporting rows

@Composable
private fun ManageRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .fcPressable(onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Manage subscription",
            style = Theme.sans(14, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Theme.colors.borderStrong,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun NoticeBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.md))
            .background(background)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.foreground)
    }
}

@Composable
private fun Footer() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Subscriptions auto-renew until you cancel. Where you bought the plan is where you " +
                "manage it: in-app purchases through Google Play, or on finnacalc.com if you " +
                "subscribed there.",
            style = Theme.sans(11),
            color = Theme.colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
        // The allowances, in plain fine print before anyone agrees.
        Text(
            "Budgeting Plus and FinnaCalc Pro include 2 connected bank logins per account. Each " +
                "extra login is $2 a month. Ad-free covers the pages the plan includes; FinnaCalc " +
                "Pro covers the whole app.",
            style = Theme.sans(10),
            color = Theme.colors.mutedForeground.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
    }
}
