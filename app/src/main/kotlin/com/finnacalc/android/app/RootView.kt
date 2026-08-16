//
// RootView.kt
//
// Port of iOS App/RootView.swift — the app's navigation shell: the five
// sections (Home, Budgeting, Investing, Taxes, Education) as bottom tabs, a
// trailing account button in the top bar, and the splash while the session
// restores. Tab content is capped at Theme.readableWidth and centered so
// tablets read like the phone-width design source.
//
// The chat sheet and feedback prompt share one sheet slot in later phases,
// mirroring the iOS ActiveSheet driver; for Phase 2 the slots are the account
// and auth sheets.
//

package com.finnacalc.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finnacalc.android.FinnaApp
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.designsystem.AppearanceSetting
import com.finnacalc.android.core.designsystem.FCWordmark
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.feedback.SessionFeedback
import com.finnacalc.android.features.auth.AccountScreen
import com.finnacalc.android.features.auth.AuthScreen
import com.finnacalc.android.features.budgeting.BudgetingFeature
import com.finnacalc.android.features.calculators.CalculatorDestination
import com.finnacalc.android.features.calculators.CalculatorKind
import com.finnacalc.android.features.chat.ChatViewModel
import com.finnacalc.android.features.chat.FinnaBotSheet
import com.finnacalc.android.features.education.EducationScreen
import com.finnacalc.android.features.feedback.FeedbackSheet
import com.finnacalc.android.features.feedback.FeedbackSource
import com.finnacalc.android.features.home.HomeScreen
import com.finnacalc.android.features.investing.InvestingFeature
import com.finnacalc.android.features.shared.ComingSoonView
import com.finnacalc.android.features.taxes.ui.TaxesScreen

// MARK: - Tabs

enum class FinnaTab(val id: String, val title: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Budgeting("budgeting", "Budgeting", Icons.Default.AccountBalanceWallet),
    Investing("investing", "Investing", Icons.AutoMirrored.Filled.ShowChart),
    Taxes("taxes", "Taxes", Icons.Outlined.Description),
    Education("education", "Education", Icons.AutoMirrored.Outlined.MenuBook),
}

@Composable
fun RootView(
    auth: AuthManager,
    appearance: AppearanceSetting,
    onAppearanceChange: (AppearanceSetting) -> Unit,
) {
    val loading by auth.loading.collectAsState()
    if (loading) {
        SplashView()
    } else {
        MainTabs(auth, appearance, onAppearanceChange)
    }
}

// MARK: - Shell

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    auth: AuthManager,
    appearance: AppearanceSetting,
    onAppearanceChange: (AppearanceSetting) -> Unit,
) {
    var selection by rememberSaveable { mutableStateOf(FinnaTab.Home) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showAuth by rememberSaveable { mutableStateOf(false) }
    var showChat by rememberSaveable { mutableStateOf(false) }
    val user by auth.user.collectAsState()

    // FinnaBot lives at the shell so the conversation survives panel open/close.
    val chat: ChatViewModel = viewModel()

    // Cross-tab links ("Manage →", "Portfolio →") land here.
    LaunchedEffect(Unit) {
        CrossTabNavigation.switchTab.collect { id ->
            FinnaTab.entries.firstOrNull { it.id == id }?.let { selection = it }
        }
    }

    // A page elsewhere in the app wants FinnaBot to look at something. It goes
    // into the shell's one conversation, typed and not sent, so the thread
    // stays single and the user sees what is about to be sent.
    LaunchedEffect(Unit) {
        CrossTabNavigation.askChat.collect { question ->
            chat.setInput(question)
            showChat = true
        }
    }

    val app = LocalContext.current.applicationContext as FinnaApp
    AppLifecycleEffects(app, app.budget)

    // The feedback prompt shares the chat's slot: it must never cover a
    // conversation mid-flight, so it only raises when nothing else is up.
    val wantsFeedback by SessionFeedback.showPrompt.collectAsState()
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(wantsFeedback) {
        if (wantsFeedback && !showChat && !showAccount && !showAuth) showFeedback = true
    }

    Scaffold(
        containerColor = Theme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selection.title,
                        style = Theme.sans(17, FontWeight.SemiBold),
                        color = Theme.colors.foreground,
                    )
                },
                actions = {
                    // Account is the settings hub for everyone — signed-out
                    // users get a Sign in entry point inside it. Always the
                    // icon, never the user's name, so the bar stays visually
                    // identical signed in or out.
                    IconButton(onClick = { showAccount = true }) {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = "Account",
                            tint = Theme.colors.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Theme.colors.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Theme.colors.card) {
                FinnaTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selection == tab,
                        onClick = { selection = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title, style = Theme.sans(11, FontWeight.Medium)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Theme.colors.primary,
                            selectedTextColor = Theme.colors.primary,
                            unselectedIconColor = Theme.colors.mutedForeground,
                            unselectedTextColor = Theme.colors.mutedForeground,
                            indicatorColor = Theme.colors.brandTint,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // Tablets: phone-width layouts are the design source, so cap the
        // content column and center it. The gutters are the same page
        // background, so they read as one surface.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Theme.colors.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = Theme.readableWidth)) {
                when (selection) {
                    FinnaTab.Home -> {
                        // The dashboard absorbed the calculator catalog, so a
                        // tapped calculator pushes over Home rather than
                        // living in its own tab.
                        var calculator by rememberSaveable { mutableStateOf<CalculatorKind?>(null) }
                        val open = calculator
                        if (open != null) {
                            BackHandler { calculator = null }
                            CalculatorDestination(open)
                        } else {
                            HomeScreen(
                                user = user,
                                budget = (LocalContext.current.applicationContext as FinnaApp).budget,
                                onOpenChat = { showChat = true },
                                onOpenCalculator = { calculator = it },
                            )
                        }
                    }
                    FinnaTab.Budgeting -> BudgetingFeature(
                        (LocalContext.current.applicationContext as FinnaApp).budget
                    )
                    FinnaTab.Investing -> InvestingFeature(auth)
                    FinnaTab.Taxes -> TaxesScreen()
                    FinnaTab.Education -> EducationScreen()
                }
            }
        }
    }

    // MARK: Sheets

    if (showChat) {
        FinnaBotSheet(chat) { showChat = false }
    }

    if (showFeedback) {
        FeedbackSheet(FeedbackSource.Prompt, auth, onDismiss = {
            showFeedback = false
            SessionFeedback.dismiss()
        })
    }

    if (showAccount) {
        ModalBottomSheet(
            onDismissRequest = { showAccount = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Theme.colors.surfaceSunken,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0) },
        ) {
            AccountScreen(
                auth = auth,
                user = user,
                appearance = appearance,
                entitlements = (LocalContext.current.applicationContext as FinnaApp).entitlements,
                onAppearanceChange = onAppearanceChange,
                onShowAuth = { showAuth = true },
                onDismiss = { showAccount = false },
            )
        }
    }

    if (showAuth) {
        ModalBottomSheet(
            onDismissRequest = { showAuth = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Theme.colors.background,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0) },
        ) {
            AuthScreen(auth = auth, onDismiss = { showAuth = false })
        }
    }
}

// MARK: - Splash

@Composable
private fun SplashView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            16.dp, Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The FinnaCalc logo asset lands in Phase 8 — the wordmark carries the
        // splash until then (deviation from iOS, which shows logo + wordmark).
        FCWordmark(size = 34)
        CircularProgressIndicator(
            color = Theme.colors.primary,
            trackColor = Color.Transparent,
            modifier = Modifier.size(28.dp),
        )
    }
}
