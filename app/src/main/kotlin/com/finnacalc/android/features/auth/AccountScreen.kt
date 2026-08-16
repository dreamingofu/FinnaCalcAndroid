//
// AccountScreen.kt
//
// Port of iOS Features/Auth/AccountView.swift — the account sheet: header →
// sign-in hero card → Appearance → About → footer actions. Uses the app's
// Theme tokens (scheme-dynamic), since the Appearance switcher lives here.
//
// Deviations from iOS, with reasons:
//  · Plans (StoreKit), Developer Preview, and Feedback sections arrive with
//    their features in later phases — Google Play Billing replaces StoreKit.
//  · About/Privacy/Terms pages land in Phase 8; the rows are present but open
//    a "coming soon" placeholder until then.
//

package com.finnacalc.android.features.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.auth.AuthUser
import com.finnacalc.android.core.designsystem.AppearanceSetting
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.networking.ApiException
import com.finnacalc.android.features.feedback.FeedbackSheet
import com.finnacalc.android.features.feedback.FeedbackSource
import com.finnacalc.android.features.pages.AboutScreen
import com.finnacalc.android.features.pages.PrivacyScreen
import com.finnacalc.android.features.pages.TermsScreen
import com.finnacalc.android.features.plans.EntitlementStore
import com.finnacalc.android.features.plans.PlanCatalog
import com.finnacalc.android.features.plans.PlanTier
import com.finnacalc.android.features.plans.PlansScreen
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    auth: AuthManager,
    user: AuthUser?,
    appearance: AppearanceSetting,
    entitlements: EntitlementStore,
    onAppearanceChange: (AppearanceSetting) -> Unit,
    onShowAuth: () -> Unit,
    onDismiss: () -> Unit,
) {
    var working by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteError by rememberSaveable { mutableStateOf<String?>(null) }
    var subpage by rememberSaveable { mutableStateOf<AccountSubpage?>(null) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activeTier by entitlements.activeTier.collectAsState()

    // A tapped row takes the sheet over, with a back bar — the Android
    // analogue of the iOS NavigationLink push inside the account sheet.
    subpage?.let { page ->
        SubpageHost(page.title, onBack = { subpage = null }) {
            when (page) {
                AccountSubpage.Plans -> PlansScreen(entitlements)
                AccountSubpage.About -> AboutScreen()
                AccountSubpage.Privacy -> PrivacyScreen()
                AccountSubpage.Terms -> TermsScreen()
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.surfaceSunken),
    ) {
        // Header bar: centered title + Done (iOS toolbar).
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(
                "Account",
                style = Theme.sans(16, FontWeight.Bold),
                color = Theme.colors.foreground,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                "Done",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                color = Theme.colors.primary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { onDismiss() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (user != null) {
                SignedInCard(user, working) {
                    working = true
                    scope.launch {
                        auth.signOut()
                        onDismiss()
                    }
                }
            } else {
                SignInHero(onShowAuth)
            }

            AppearanceSection(appearance, onAppearanceChange)
            PlanSection(activeTier) { subpage = AccountSubpage.Plans }
            SupportSection { showFeedback = true }
            AboutSection { subpage = it }

            if (user != null) {
                // In-app account deletion — required store policy for
                // account-creating apps on both platforms.
                Text(
                    "Delete account",
                    style = Theme.sans(13, FontWeight.SemiBold),
                    color = Theme.colors.destructive,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !working) { confirmDelete = true }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This permanently deletes your FinnaCalc account and cannot be undone. " +
                        "Data saved on this device stays until you delete the app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    working = true
                    scope.launch {
                        try {
                            auth.deleteAccount()
                            onDismiss()
                        } catch (e: ApiException) {
                            deleteError = e.message ?: "Something went wrong. Please try again."
                        }
                        working = false
                    }
                }) { Text("Delete", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("Couldn't delete account") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { deleteError = null }) { Text("OK") }
            },
        )
    }

    if (showFeedback) {
        FeedbackSheet(FeedbackSource.Manual, auth, onDismiss = { showFeedback = false })
    }
}

/** The pages the account sheet pushes to. */
enum class AccountSubpage(val title: String) {
    Plans("Plans"),
    About("About FinnaCalc"),
    Privacy("Privacy Policy"),
    Terms("Terms of Service"),
}

// MARK: - Hero (signed out)

@Composable
private fun SignInHero(onShowAuth: () -> Unit) {
    Column(
        modifier = cardSurface(RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Theme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = Theme.colors.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            "Save your progress",
            style = Theme.sans(16, FontWeight.Bold),
            color = Theme.colors.foreground,
        )
        Text(
            "Budgets, goals & brokerage sync across devices with a free account.",
            style = Theme.sans(13).copy(lineHeight = 18.sp),
            color = Theme.colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onShowAuth)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Sign in or create account",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

// MARK: - Hero (signed in)

@Composable
private fun SignedInCard(user: AuthUser, working: Boolean, onSignOut: () -> Unit) {
    Row(
        modifier = cardSurface(RoundedCornerShape(20.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Theme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user.displayName.take(1).uppercase(),
                style = Theme.sans(17, FontWeight.Bold),
                color = Color.White,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                user.displayName,
                style = Theme.sans(15, FontWeight.Bold),
                color = Theme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                user.email,
                style = Theme.sans(13),
                color = Theme.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Sign out",
            style = Theme.sans(13, FontWeight.SemiBold),
            color = Theme.colors.destructive,
            modifier = Modifier.clickable(enabled = !working, onClick = onSignOut),
        )
    }
}

// MARK: - Appearance

@Composable
private fun AppearanceSection(
    appearance: AppearanceSetting,
    onAppearanceChange: (AppearanceSetting) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel("APPEARANCE")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.secondary)
                .padding(3.dp),
        ) {
            AppearanceSetting.entries.forEach { option ->
                val selected = appearance == option
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) Theme.colors.card else Color.Transparent)
                        .clickable { onAppearanceChange(option) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        tint = if (selected) Theme.colors.primary else Theme.colors.mutedForeground,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        option.title,
                        style = Theme.sans(13, if (selected) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (selected) Theme.colors.foreground else Theme.colors.mutedForeground,
                    )
                }
            }
        }
    }
}

// MARK: - About

@Composable
private fun AboutSection(onOpen: (AccountSubpage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel("ABOUT")
        Column(cardSurface(RoundedCornerShape(16.dp))) {
            listOf(
                AccountSubpage.About to "About FinnaCalc",
                AccountSubpage.Privacy to "Privacy policy",
                AccountSubpage.Terms to "Terms of service",
            ).forEachIndexed { index, (page, title) ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Theme.colors.border)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fcPressable { onOpen(page) }
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
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
        }
    }
}

// MARK: - Shared bits

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
        color = Theme.colors.mutedForeground,
    )
}

@Composable
private fun cardSurface(shape: RoundedCornerShape): Modifier =
    Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Theme.colors.card)
        .border(1.dp, Theme.colors.border, shape)

// MARK: - Plan + support rows (Phase 8)

/**
 * What the user actually holds, never a guess: with no active tier this says
 * Free and offers the upgrade, rather than implying a plan that isn't there.
 */
@Composable
private fun PlanSection(activeTier: PlanTier?, onOpen: () -> Unit) {
    val plan = activeTier?.let { PlanCatalog.plan(it) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel("PLAN")
        Row(
            modifier = cardSurface(RoundedCornerShape(16.dp))
                .fcPressable(onOpen)
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    plan?.name ?: "Free",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                )
                Text(
                    plan?.tagline ?: "See what the paid plans add",
                    style = Theme.sans(12),
                    color = Theme.colors.mutedForeground,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Theme.colors.borderStrong,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SupportSection(onFeedback: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionLabel("SUPPORT")
        Row(
            modifier = cardSurface(RoundedCornerShape(16.dp))
                .fcPressable(onFeedback)
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Send feedback",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
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
}

/** A pushed page inside the account sheet: back bar over full-bleed content. */
@Composable
private fun SubpageHost(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.surfaceSunken)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(
                "Back",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fcPressable(onBack),
            )
            Text(
                title,
                style = Theme.sans(16, FontWeight.Bold),
                color = Theme.colors.foreground,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
