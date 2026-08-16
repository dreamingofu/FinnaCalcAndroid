//
// BudgetingFeature.kt
//
// The Budgeting tab's root: a local back-stack over the hub and its pushed
// pages (iOS uses NavigationStack pushes; here a sealed destination list +
// BackHandler — parameters live in memory, matching the iOS behavior of a
// push, and are rebuilt from the stores on process death).
//
// Hub port of iOS Features/Budgeting/BudgetingView.swift — feature cards in
// the Paper & Cobalt system: header + Personal/Business toggle → 2x2 summary
// figures → My Budget, Budget Analysis, Goals, Subscriptions, History. Every
// card is listed from the first launch; a card's subtitle describes its
// feature until there's real data to report, then reports it.
//

package com.finnacalc.android.features.budgeting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.app.CrossTabNavigation
import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.core.designsystem.PaperBigCard
import com.finnacalc.android.core.designsystem.PaperSampleDonut
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.paperCard
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.shared.ComingSoonView

// MARK: - Destinations

sealed class BudgetingDest {
    data object MyBudget : BudgetingDest()
    data class CategoryItems(
        val category: String,
        val direction: ItemType,
        val readingBank: Boolean,
    ) : BudgetingDest()

    data object Goals : BudgetingDest()
    data object History : BudgetingDest()
    data object Subscriptions : BudgetingDest()
    data object Advisor : BudgetingDest()
}

@Composable
fun BudgetingFeature(store: BudgetStore) {
    val stack = remember { mutableStateListOf<BudgetingDest>() }
    // A History import hands the snapshot to the editor, which asks the
    // combine question on top of the budget it would actually land on.
    var pendingImport by remember { mutableStateOf<BudgetHistoryEntry?>(null) }

    if (stack.isNotEmpty()) {
        BackHandler { stack.removeAt(stack.lastIndex) }
    }

    // A Home card asked for a specific page on the way in. One-shot: read and
    // cleared, so returning to the tab later lands on the hub as usual.
    LaunchedEffect(Unit) {
        when (CrossTabNavigation.pendingBudgetingPage) {
            "budget" -> stack.add(BudgetingDest.MyBudget)
            "goals" -> stack.add(BudgetingDest.Goals)
            "history" -> stack.add(BudgetingDest.History)
            "subscriptions" -> stack.add(BudgetingDest.Subscriptions)
            "advisor" -> stack.add(BudgetingDest.Advisor)
        }
        CrossTabNavigation.pendingBudgetingPage = null
    }

    fun replaceWith(dest: BudgetingDest) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(dest)
    }

    when (val top = stack.lastOrNull()) {
        null -> BudgetingHub(store) { stack.add(it) }
        BudgetingDest.MyBudget -> BudgetTabScreen(
            store,
            push = { stack.add(it) },
            importOnAppear = pendingImport,
            onImportConsumed = { pendingImport = null },
        )
        is BudgetingDest.CategoryItems -> CategoryItemsScreen(store, top)
        BudgetingDest.Goals -> GoalsScreen(store)
        BudgetingDest.History -> HistoryScreen(store) { entry ->
            pendingImport = entry
            replaceWith(BudgetingDest.MyBudget)
        }
        BudgetingDest.Subscriptions -> SubscriptionsScreen(store)
        BudgetingDest.Advisor -> BudgetAdvisorScreen(
            store,
            onOpenBudget = { replaceWith(BudgetingDest.MyBudget) },
            onOpenGoals = { replaceWith(BudgetingDest.Goals) },
        )
    }
}

// MARK: - Hub

@Composable
private fun BudgetingHub(store: BudgetStore, push: (BudgetingDest) -> Unit) {
    // Recompose on any store/ledger mutation.
    store.version.collectAsState().value
    BankLedgerStore.shared.version.collectAsState().value

    var menuOpen by remember { mutableStateOf(false) }
    var confirmClearItems by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val hasBudget = store.monthlyIncome > 0 || store.monthlyExpenses > 0
    val otherType = if (store.budgetType == BudgetType.Personal) BudgetType.Business else BudgetType.Personal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header: title + Personal/Business toggle + overflow menu.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Budgeting",
                style = Theme.sans(Theme.FontSize.xl2, FontWeight.Bold).copy(letterSpacing = (-0.4).sp),
                color = Paper.ink,
            )
            Spacer(Modifier.weight(1f))
            BudgetTypeToggle(store)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Theme.colors.primary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Clear budget items", color = Theme.colors.destructive) },
                        onClick = {
                            menuOpen = false
                            confirmClearItems = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear All (items, goals, and history)", color = Theme.colors.destructive) },
                        onClick = {
                            menuOpen = false
                            confirmClearAll = true
                        },
                    )
                }
            }
        }

        // 2x2 summary — the four figures sit as dashes until there's
        // something to report, so the tab's shape never shifts.
        BudgetSummaryGrid(store, hasBudget)

        FeatureCards(store, hasBudget, push)
    }

    // Both clears stay inside the budget type on screen and neither can be
    // undone. Each names its own reach before it runs, including the months
    // it reaches past the one open, and says what survives as well as what
    // goes.
    if (confirmClearItems) {
        AlertDialog(
            onDismissRequest = { confirmClearItems = false },
            title = { Text("Clear ${store.budgetType.title} budget items?") },
            text = {
                Text(
                    "Every income and expense line in your ${store.budgetType.title} budget is removed, " +
                        "for every month, not only the one you have open. Your ${otherType.title} budget, " +
                        "goals, saved history and category caps are kept, and connected banks stay " +
                        "connected. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearBudgetItems(store.budgetType)
                    confirmClearItems = false
                }) { Text("Delete Items", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearItems = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear everything in ${store.budgetType.title}?") },
            text = {
                Text(
                    "Every ${store.budgetType.title} budget item, goal, snapshot in History and " +
                        "category cap is removed, for every month. Your ${otherType.title} budget is " +
                        "untouched, connected banks stay connected, and their transactions are " +
                        "unaffected. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAll(store.budgetType)
                    confirmClearAll = false
                }) { Text("Delete Everything", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BudgetTypeToggle(store: BudgetStore) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Paper.chipFill)
            .padding(3.dp),
    ) {
        BudgetType.entries.forEach { type ->
            val selected = store.budgetType == type
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Paper.card else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { store.budgetType = type }
                    .padding(horizontal = 13.dp, vertical = 5.dp),
            ) {
                Text(
                    type.title,
                    style = Theme.sans(Theme.FontSize.xs, if (selected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (selected) Paper.ink else Paper.muted,
                )
            }
        }
    }
}

@Composable
private fun BudgetSummaryGrid(store: BudgetStore, hasBudget: Boolean) {
    val net = store.monthlyNet
    val hasIncome = store.monthlyIncome > 0
    val hasExpenses = store.monthlyExpenses > 0
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                StatCell(
                    "INCOME",
                    if (hasIncome) Paper.compactMoney(store.monthlyIncome) else "—",
                    if (hasIncome) Paper.positive else Paper.muted,
                )
            }
            Box(Modifier.weight(1f)) {
                StatCell(
                    "EXPENSES",
                    if (hasExpenses) Paper.compactMoney(store.monthlyExpenses) else "—",
                    if (hasExpenses) Paper.negative else Paper.muted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                // Business reports margin instead: a company's health is
                // what's left of revenue, not what it routes to Savings.
                if (store.budgetType == BudgetType.Business) {
                    val margin = store.netProfitMargin
                    StatCell(
                        "NET PROFIT",
                        margin?.let { CalcFormat.fixed(it, 0) + "%" } ?: "—",
                        if (margin == null) Paper.muted else Paper.cobalt,
                    )
                } else {
                    val rate = store.savingsRate
                    StatCell(
                        "SAVINGS RATE",
                        rate?.let { CalcFormat.fixed(it, 0) + "%" } ?: "—",
                        if (rate == null) Paper.muted else Paper.cobalt,
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                // Only a dash when BOTH sides are empty — expenses with no
                // income is a real (negative) net worth showing.
                StatCell(
                    "NET INCOME",
                    if (hasBudget) (if (net >= 0) "+" else "−") + Paper.compactMoney(kotlin.math.abs(net)) else "—",
                    if (hasBudget) (if (net >= 0) Paper.positive else Paper.negative) else Paper.muted,
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.sp),
            color = Paper.muted,
        )
        Text(
            value,
            style = Theme.figure(Theme.FontSize.xl2, FontWeight.Bold),
            color = tint,
            maxLines = 1,
        )
    }
}

// MARK: - Feature cards

@Composable
private fun FeatureCards(store: BudgetStore, hasBudget: Boolean, push: (BudgetingDest) -> Unit) {
    val goals = store.currentGoals
    val goalsSubtitle = if (goals.isEmpty()) {
        // A linked bank unlocks the derived kinds, and the card is where
        // users learn that.
        if (Entitlements.paidBudgeting) "Saving, spending, and income goals"
        else "Savings goals with progress tracking"
    } else {
        "${goals.size} active goal${if (goals.size == 1) "" else "s"}"
    }
    // Snapshots insert at the FRONT (newest first) — first is the latest,
    // scoped to this budget.
    val latest = store.currentHistory.firstOrNull()
    val historySubtitle = latest?.let { "${it.name} · saved ${HistoryDateShort(it.endDate)}" }
        ?: "Snapshots and bank imports over time"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FeatureCard(
            mark = { PaperSampleDonut(52.dp) },
            title = "My Budget",
            subtitle = if (hasBudget) {
                "${Paper.compactMoney(store.monthlyIncome)} in · ${Paper.compactMoney(store.monthlyExpenses)} out"
            } else {
                "Add income and expenses, see where money goes"
            },
        ) { push(BudgetingDest.MyBudget) }

        FeatureCard(
            mark = { EmojiMark("🤖") },  // FinnaBot logo asset lands in Phase 7/8.
            title = "Budget Analysis",
            subtitle = "Personalized advice from your budget snapshot",
        ) { push(BudgetingDest.Advisor) }

        FeatureCard(
            mark = { EmojiMark("🎯") },
            title = "Goals",
            subtitle = goalsSubtitle,
        ) { push(BudgetingDest.Goals) }

        FeatureCard(
            mark = { EmojiMark("📅") },
            title = "Subscriptions",
            subtitle = "Spot recurring charges in your transactions",
        ) { push(BudgetingDest.Subscriptions) }

        FeatureCard(
            mark = {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = Paper.cobalt,
                    modifier = Modifier.size(34.dp),
                )
            },
            title = "History",
            subtitle = historySubtitle,
        ) { push(BudgetingDest.History) }
    }
}

/** "2026-06-30T…" → "Jun 30, 2026"-ish short date for the History card. */
@Composable
private fun HistoryDateShort(raw: String): String =
    com.finnacalc.android.core.util.HistoryDate.parse(raw)
        ?.let { com.finnacalc.android.core.util.HistoryDate.medium(it) } ?: raw

@Composable
private fun EmojiMark(glyph: String) {
    Text(glyph, style = Theme.sans(30))
}

@Composable
private fun FeatureCard(
    mark: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .paperCard(PaperBigCard.radius)
            .fcPressable(onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { mark() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = Theme.sans(19, FontWeight.Medium).copy(letterSpacing = (-0.3).sp),
                color = Paper.ink,
            )
            // Single line keeps every card the same height.
            Text(
                subtitle,
                style = Theme.sans(13),
                color = Paper.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Paper.chevron,
            modifier = Modifier.size(20.dp),
        )
    }
}
