package com.finnacalc.android.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.finnacalc.android.core.designsystem.FinnaTheme
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.core.auth.AuthUser
import com.finnacalc.android.features.budgeting.BudgetAdvisorScreen
import com.finnacalc.android.features.budgeting.BudgetItem
import com.finnacalc.android.features.budgeting.BudgetType
import com.finnacalc.android.features.budgeting.Frequency
import com.finnacalc.android.features.budgeting.ItemType
import com.finnacalc.android.features.budgeting.SavingsGoal
import com.finnacalc.android.features.budgeting.BudgetStore
import com.finnacalc.android.features.budgeting.BudgetTabScreen
import com.finnacalc.android.features.budgeting.BudgetingFeature
import com.finnacalc.android.features.budgeting.GoalsScreen
import com.finnacalc.android.features.budgeting.HistoryScreen
import com.finnacalc.android.features.budgeting.SubscriptionsScreen
import com.finnacalc.android.features.calculators.CalculatorDestination
import com.finnacalc.android.features.calculators.CalculatorKind
import com.finnacalc.android.features.calculators.CalculatorsHubView
import com.finnacalc.android.features.education.EducationScreen
import com.finnacalc.android.features.home.HomeScreen
import com.finnacalc.android.features.investing.BondsScreen
import com.finnacalc.android.features.investing.EtfListScreen
import com.finnacalc.android.features.investing.InvestingGoalsSection
import com.finnacalc.android.features.investing.SafeInvestmentsScreen
import com.finnacalc.android.features.investing.ScreenerScreen
import com.finnacalc.android.features.investing.SectorCatalog
import com.finnacalc.android.features.investing.SectorScreen
import com.finnacalc.android.features.investing.StockScreen
import com.finnacalc.android.features.investing.WatchlistCard
import com.finnacalc.android.features.investing.TradeTrackerScreen
import com.finnacalc.android.features.pages.AboutScreen
import com.finnacalc.android.features.pages.PrivacyScreen
import com.finnacalc.android.features.pages.TermsScreen
import com.finnacalc.android.features.taxes.TaxViewModel
import com.finnacalc.android.features.taxes.ui.TaxCalculatorsView
import com.finnacalc.android.features.taxes.ui.TaxFilingExperience
import com.finnacalc.android.features.taxes.ui.TaxesScreen
import com.finnacalc.android.features.taxes.ui.FilingScreen
import com.finnacalc.android.features.taxes.ui.ReviewScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Composition smoke tests.
 *
 * A whole class of bug — the negative padding that crashed Home on launch —
 * throws only when a screen is actually composed, so no amount of testing the
 * logic underneath can catch it. These render each screen once and fail if
 * composition or its first layout pass throws.
 *
 * They deliberately assert almost nothing about what is drawn. The render IS
 * the assertion; pinning layout details here would turn every design tweak
 * into a test failure, which is how a suite like this stops being run.
 *
 * Robolectric runs them on the JVM, so they belong to the normal `test` task
 * and the green gate covers them without a device.
 *
 * Screens whose first frame needs the network are still worth rendering: they
 * compose their loading state, which is the state most users see first and the
 * one no other test exercises.
 */
// A plain Application, not FinnaApp: the real one builds a Supabase client in
// onCreate, which can't initialise on the JVM. Every screen here takes its
// stores as parameters, so none of them needs the app class — and a screen
// that did would be a screen this suite can't cover, which is worth knowing.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ScreenRenderTest {

    // An Activity host, so the screens get the ViewModelStoreOwner and
    // LifecycleOwner that viewModel() and lifecycle effects expect.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        JsonPrefs.resetForTesting()
    }

    /** Renders [content]; a throw during composition or layout fails the test. */
    private fun renders(content: @Composable () -> Unit) {
        compose.setContent { FinnaTheme(darkTheme = false) { content() } }
        compose.waitForIdle()
    }

    /**
     * The same content in dark mode. Both palettes are first-class in this app,
     * and a token that only resolves in one scheme would otherwise ship.
     */
    private fun rendersDark(content: @Composable () -> Unit) {
        compose.setContent { FinnaTheme(darkTheme = true) { content() } }
        compose.waitForIdle()
    }

    private fun store() = BudgetStore()

    /**
     * A budget with real figures in it. The empty state and the populated one
     * are different code paths — the donut, its legend and the goal rows only
     * exist in the second — so both are worth rendering.
     */
    private fun populatedStore(): BudgetStore = BudgetStore().apply {
        items = listOf(
            item("Salary", 5_000.0, ItemType.Income),
            item("Housing", 1_800.0),
            item("Food", 620.0),
            item("Transportation", 240.0),
            item("Entertainment", 95.0),
        )
        goals = listOf(
            SavingsGoal(
                id = "g1", name = "Emergency fund", targetAmount = 10_000.0,
                currentAmount = 6_200.0, targetDate = "2026-12-31",
                monthlyContribution = 400.0,
            ),
            SavingsGoal(
                id = "g2", name = "New car", targetAmount = 12_000.0,
                currentAmount = 4_100.0, targetDate = "2027-06-30",
                monthlyContribution = 250.0,
            ),
        )
        history = emptyList()
    }

    private fun item(category: String, amount: Double, type: ItemType = ItemType.Expense) = BudgetItem(
        id = "i-$category",
        category = category,
        subcategory = "",
        amount = amount,
        frequency = Frequency.Monthly,
        type = type,
        isFixed = false,
        budgetType = BudgetType.Personal,
        month = BudgetStore.UNDATED_MONTH_KEY,
    )

    // MARK: - Home

    @Test
    fun `home renders`() {
        // The regression this suite exists for: Home used a negative padding
        // and died during its first composition.
        renders { HomeScreen(user = null, budget = store(), onOpenChat = {}, onOpenCalculator = {}) }
    }

    @Test
    fun `home renders with a populated budget`() {
        // Draws the real donut, its legend, and the goal rows — none of which
        // the empty state composes at all.
        renders {
            HomeScreen(
                user = AuthUser(id = "u1", email = "alex@example.com", name = "Alex Rivera"),
                budget = populatedStore(),
                onOpenChat = {},
                onOpenCalculator = {},
            )
        }
    }

    @Test
    fun `my budget renders with a populated budget`() {
        renders { BudgetTabScreen(populatedStore(), push = {}) }
    }

    @Test
    fun `goals render with real goals`() {
        renders { GoalsScreen(populatedStore()) }
    }

    @Test
    fun `budget analysis renders with findings`() {
        // Findings only exist once there are figures to read.
        renders { BudgetAdvisorScreen(populatedStore(), onOpenBudget = {}, onOpenGoals = {}) }
    }

    @Test
    fun `home renders in dark mode`() {
        rendersDark { HomeScreen(user = null, budget = store(), onOpenChat = {}, onOpenCalculator = {}) }
    }

    // MARK: - Calculators

    @Test
    fun `the calculator hub renders`() {
        renders { CalculatorsHubView() }
    }

    @Test
    fun `every calculator renders`() {
        // The host takes content once, so the kind is swapped through state
        // and each one is composed in turn — a broken field or result panel in
        // any single calculator still throws here.
        val kind = mutableStateOf(CalculatorKind.entries.first())
        compose.setContent { FinnaTheme(darkTheme = false) { CalculatorDestination(kind.value) } }
        CalculatorKind.entries.forEach {
            kind.value = it
            compose.waitForIdle()
        }
    }

    // MARK: - Taxes

    @Test
    fun `the taxes launcher renders`() {
        renders { TaxesScreen(vm = TaxViewModel()) }
    }

    @Test
    fun `the tax calculators render`() {
        renders { TaxCalculatorsView() }
    }

    // MARK: - Education

    @Test
    fun `education renders`() {
        renders { EducationScreen() }
    }

    // MARK: - Pages

    @Test
    fun `about renders`() {
        renders { AboutScreen() }
    }

    @Test
    fun `privacy renders`() {
        renders { PrivacyScreen() }
    }

    @Test
    fun `terms renders`() {
        renders { TermsScreen() }
    }

    // MARK: - Investing reference pages

    @Test
    fun `the etf list renders`() {
        renders { EtfListScreen {} }
    }

    @Test
    fun `safe investments renders`() {
        renders { SafeInvestmentsScreen() }
    }

    @Test
    fun `bonds renders`() {
        renders { BondsScreen() }
    }

    @Test
    fun `the trade tracker renders`() {
        renders { TradeTrackerScreen {} }
    }

    @Test
    fun `investing goals render with no positions`() {
        // The empty state, which is what a user without a brokerage sees.
        renders { InvestingGoalsSection(emptyList()) }
    }

    // MARK: - Budgeting

    @Test
    fun `the budgeting hub renders`() {
        renders { BudgetingFeature(store()) }
    }

    @Test
    fun `my budget renders`() {
        renders { BudgetTabScreen(store(), push = {}) }
    }

    @Test
    fun `goals render`() {
        renders { GoalsScreen(store()) }
    }

    @Test
    fun `history renders`() {
        renders { HistoryScreen(store()) {} }
    }

    @Test
    fun `subscriptions render`() {
        renders { SubscriptionsScreen(store()) }
    }

    @Test
    fun `budget analysis renders`() {
        renders { BudgetAdvisorScreen(store(), onOpenBudget = {}, onOpenGoals = {}) }
    }

    // MARK: - The tax filing flow

    @Test
    fun `the filing experience renders`() {
        renders { TaxFilingExperience(TaxViewModel()) {} }
    }

    @Test
    fun `the tax review renders`() {
        renders { ReviewScreen(TaxViewModel()) }
    }

    @Test
    fun `the tax summary renders`() {
        renders { FilingScreen(TaxViewModel()) }
    }

    // MARK: - Investing surfaces that fetch

    @Test
    fun `the screener renders its loading state`() {
        renders { ScreenerScreen {} }
    }

    @Test
    fun `the watchlist card renders`() {
        renders { WatchlistCard {} }
    }

    @Test
    fun `a stock detail renders its loading state`() {
        renders { StockScreen("AAPL") }
    }

    @Test
    fun `a sector page renders`() {
        SectorCatalog.all.firstOrNull()?.let { sector ->
            renders { SectorScreen(sector) {} }
        }
    }
}
