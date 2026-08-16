//
// HomeScreen.kt
//
// Port of the iOS Home tab (HomeView / HomeDashboardView / HomeSignedOutView /
// PaperHome.swift). Deliberately the SAME page signed in or out — greeting
// header → FinnaBot prompt → Expenses → Investing → Goals → Lesson of the week
// → every calculator — so the app doesn't rearrange itself the moment someone
// signs in. Only the header and the Investing card's source differ.
//
// This is also where FinnaBot's real entry point lives: the ambient prompt bar,
// replacing the temporary top-bar action Phase 7 shipped.
//
// Every card is honest about what it doesn't know: the Expenses donut invites
// rather than drawing zero, and the Investing card holds "—" until a real
// quote lands.
//

package com.finnacalc.android.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.app.CrossTabNavigation
import com.finnacalc.android.core.auth.AuthUser
import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.core.designsystem.PaperSampleDonut
import com.finnacalc.android.core.designsystem.PaperSectionHeader
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.ambientGlow
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.paperCard
import com.finnacalc.android.core.market.MarketService
import com.finnacalc.android.core.market.MarketStat
import com.finnacalc.android.features.budgeting.BankLedgerStore
import com.finnacalc.android.features.budgeting.BudgetStore
import com.finnacalc.android.features.budgeting.GoalEmoji
import com.finnacalc.android.features.budgeting.GoalProgress
import com.finnacalc.android.features.budgeting.GoalRing
import com.finnacalc.android.features.budgeting.SavingsGoal
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.CalculatorKind
import com.finnacalc.android.features.education.EducationContent
import com.finnacalc.android.features.investing.InvestingGoal
import com.finnacalc.android.features.investing.InvestingGoalKind
import com.finnacalc.android.features.investing.InvestingGoalMath
import com.finnacalc.android.features.investing.InvestingGoalStore
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

// MARK: - Root

@Composable
fun HomeScreen(
    user: AuthUser?,
    budget: BudgetStore,
    onOpenChat: () -> Unit,
    onOpenCalculator: (CalculatorKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper.page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Header(user)
        // 18dp below the header — the section gap is 24.
        Box(Modifier.padding(top = (-6).dp)) { PromptCard(onOpenChat) }
        ExpensesBigCard(budget)
        InvestingBigCard(user)
        GoalsBigCard(budget)
        LessonOfWeekCard()
        CalculatorsSection(onOpenCalculator)
    }
}

@Composable
private fun Header(user: AuthUser?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (user != null) {
            Text(
                "${greeting()}, ${firstName(user.displayName)}",
                style = Theme.sans(30, FontWeight.Bold),
                color = Paper.ink,
            )
        } else {
            Text(
                buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Paper.ink)) { append("Finna") }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Paper.cobalt)) { append("Calc") }
                },
                style = Theme.sans(30, FontWeight.Bold),
            )
        }
        Text(
            "Your All In One Personal Finance Platform",
            style = Theme.sans(Theme.FontSize.sm),
            color = Paper.muted,
        )
    }
}

/**
 * The first token of the display name; an email falls back to the part before
 * the "@" so we still greet with something human.
 */
internal fun firstName(displayName: String): String {
    val base = if (displayName.contains("@") && !displayName.contains(" ")) {
        displayName.substringBefore("@")
    } else {
        displayName
    }
    return base.split(" ").firstOrNull()?.takeIf { it.isNotEmpty() } ?: base
}

internal fun greeting(hour: Int = java.time.LocalTime.now().hour): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

// MARK: - FinnaBot prompt

@Composable
private fun PromptCard(onOpenChat: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ambientGlow(22.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Paper.card)
            .fcPressable(onOpenChat)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The wordmark stands in for the iOS FinnaBotLogo asset.
        Text(
            buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = Paper.ink)) { append("F") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = Paper.cobalt)) { append("B") }
            },
            style = Theme.sans(17, FontWeight.Bold),
        )
        Text(
            "Ask FinnaBot a question…",
            style = Theme.sans(14),
            color = Paper.muted,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Theme.colors.brandBlue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// MARK: - Big card chrome

/**
 * The shared Home card footprint: 18/18/16/18 insets, a 153dp floor, and a
 * weighted border (the page is the same colour, so a hairline barely reads).
 */
@Composable
private fun BigCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 153.dp)
            .clip(shape)
            .background(Paper.card)
            .border(1.5.dp, Paper.border, shape)
            .then(if (onClick != null) Modifier.fcPressable(onClick) else Modifier)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun BigCardHeader(title: String) {
    Text(
        title,
        style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.sp),
        color = Paper.muted,
    )
}

// MARK: - Expenses

@Composable
private fun ExpensesBigCard(budget: BudgetStore) {
    budget.version.collectAsState().value
    BankLedgerStore.shared.version.collectAsState().value

    // Top 3 categories + an "Other" rollup, in the Budgeting donut's palette
    // so category colours agree across screens.
    val palette = listOf(Color(0xFF3B5BDB), Color(0xFFE8590C), Color(0xFF0CA678))
    val cats = budget.expenseByCategory.sortedByDescending { it.value }
    val slices = buildList {
        cats.take(3).forEachIndexed { i, c -> if (c.value > 0) add(Triple(c.name, c.value, palette[i])) }
        val rest = cats.drop(3).sumOf { it.value }
        if (rest > 0) add(Triple("Other", rest, Color(0xFFE64980)))
    }

    BigCard(onClick = { CrossTabNavigation.request("budgeting", page = "budget") }) {
        BigCardHeader("EXPENSES")
        if (slices.isEmpty()) {
            // A sample donut with no figures at all: it shows the shape the
            // card will take and can't be mistaken for the user's own numbers.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaperSampleDonut(92.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Add your first expense",
                        style = Theme.sans(14, FontWeight.SemiBold),
                        color = Paper.ink,
                    )
                    Text(
                        "See where your money goes each month",
                        style = Theme.sans(12),
                        color = Paper.muted,
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    ExpensesDonut(slices)
                    // No "/mo": the figure follows whatever My Budget has open,
                    // which can be a set of dates that isn't a month at all.
                    Text(
                        Paper.compactMoney(budget.monthlyExpenses),
                        style = Theme.figure(16, FontWeight.Bold),
                        color = Paper.ink,
                    )
                }
                // Every slice, including the rollup: a prefix would leave a
                // wedge unlabelled and the legend short of the centre total.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    slices.forEach { (name, value, color) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                            Text(
                                name,
                                style = Theme.sans(12),
                                color = Paper.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                Paper.compactMoney(value),
                                style = Theme.figure(12, FontWeight.SemiBold),
                                color = Paper.ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesDonut(slices: List<Triple<String, Double, Color>>) {
    val track = Paper.ringTrack
    Canvas(Modifier.size(92.dp)) {
        val lineWidth = 10.dp.toPx()
        val radius = (minOf(size.width, size.height) - lineWidth) / 2
        val topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius)
        val arcSize = Size(radius * 2, radius * 2)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(lineWidth))

        val total = slices.sumOf { it.second }
        if (total <= 0) return@Canvas
        val gap = if (slices.size > 1) 3f else 0f
        var start = -90f
        slices.forEach { (_, value, color) ->
            val sweep = (360.0 * (value / total)).toFloat()
            if (sweep > gap) {
                drawArc(
                    color, start + gap / 2, sweep - gap, false,
                    topLeft, arcSize, style = Stroke(lineWidth, cap = StrokeCap.Butt),
                )
            }
            start += sweep
        }
    }
}

// MARK: - Investing

/**
 * The user's own portfolio when a brokerage is connected, the S&P 500
 * otherwise. The index itself isn't quotable on our data plan, so the fallback
 * tracks SPY and is labelled "S&P 500 ETF" — the same honest naming the
 * Markets row uses. The figure stays "—" until a real quote lands.
 */
@Composable
private fun InvestingBigCard(user: AuthUser?) {
    var stat by remember { mutableStateOf<MarketStat?>(null) }

    LaunchedEffect(Unit) {
        stat = runCatching { MarketService.marketStats(listOf("SPY")).stats.firstOrNull() }.getOrNull()
    }

    BigCard(onClick = { CrossTabNavigation.request("investing", tabName = "portfolio") }) {
        BigCardHeader("INVESTING")
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("S&P 500 ETF", style = Theme.sans(14, FontWeight.SemiBold), color = Paper.ink)
                val current = stat
                if (current == null) {
                    Text("—", style = Theme.figure(28, FontWeight.Bold), color = Paper.muted)
                } else {
                    val up = current.changePct >= 0
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (up) Icons.Filled.CallMade else Icons.Filled.CallReceived,
                            contentDescription = null,
                            tint = if (up) Paper.positive else Paper.negative,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            (if (up) "+" else "−") + CalcFormat.fixed(abs(current.changePct), 2) + "%",
                            style = Theme.figure(28, FontWeight.Bold),
                            color = if (up) Paper.positive else Paper.negative,
                        )
                    }
                }
                Text("today", style = Theme.sans(12), color = Paper.muted)
            }
        }
    }
}

// MARK: - Goals

/**
 * The top two goals ACROSS budgeting and investing by completion, so a mixed
 * pair lands each person where that goal lives.
 */
@Composable
private fun GoalsBigCard(budget: BudgetStore) {
    budget.version.collectAsState().value
    val investingGoals by InvestingGoalStore.shared.goals.collectAsState()

    val ledger = BankLedgerStore.shared
    val scored = buildList<Pair<Any, Double>> {
        budget.currentGoals.filter { it.targetAmount > 0 }.forEach {
            add(it as Any to GoalProgress.fraction(it, ledger))
        }
        // Investing goals have no positions on this page, so they rank by
        // whatever their own math can answer without a brokerage call.
        investingGoals.forEach {
            add(it as Any to InvestingGoalMath.measure(it, emptyList()).fraction)
        }
    }.sortedByDescending { it.second }.take(2)

    BigCard(onClick = {
        val first = scored.firstOrNull()?.first
        if (first is InvestingGoal) CrossTabNavigation.request("investing", tabName = "portfolio")
        else CrossTabNavigation.request("budgeting", page = "goals")
    }) {
        BigCardHeader("GOALS")
        when {
            scored.isEmpty() -> Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalRingView(0.0, Paper.positive, 69.dp, "🎯")
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Set your first savings goal",
                        style = Theme.sans(14, FontWeight.SemiBold),
                        color = Paper.ink,
                    )
                    Text("Track progress right here", style = Theme.sans(12), color = Paper.muted)
                }
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                scored.forEach { (goal, fraction) ->
                    when (goal) {
                        is SavingsGoal -> {
                            val m = GoalProgress.measure(goal, ledger)
                            GoalRow(
                                emoji = GoalEmoji.resolve(goal),
                                title = goal.name,
                                detail = "${Paper.compactMoney(m.first)} / ${Paper.compactMoney(m.second)}",
                                fraction = fraction,
                                tint = GoalRing.color(goal.ringColorHex) ?: Paper.positive,
                            )
                        }

                        is InvestingGoal -> {
                            val detail = if (goal.kind == InvestingGoalKind.Mix) {
                                "${if (goal.mixKeepUnder) "cap" else "floor"} " +
                                    "${CalcFormat.fixed(goal.targetValue, 0)}%"
                            } else {
                                "of ${Paper.compactMoney(goal.targetValue)}"
                            }
                            GoalRow(
                                emoji = goal.emoji ?: "📈",
                                title = goal.name,
                                detail = detail,
                                fraction = fraction,
                                tint = GoalRing.color(goal.ringColorHex) ?: Paper.positive,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(emoji: String, title: String, detail: String, fraction: Double, tint: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoalRingView(fraction, tint, 40.dp, emoji)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.ifEmpty { "Goal" },
                style = Theme.sans(13, FontWeight.SemiBold),
                color = Paper.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = Theme.sans(11), color = Paper.muted)
        }
        Text(
            "${(fraction * 100).toInt()}%",
            style = Theme.figure(13, FontWeight.Bold),
            color = Paper.ink,
        )
    }
}

@Composable
private fun GoalRingView(fraction: Double, tint: Color, size: androidx.compose.ui.unit.Dp, emoji: String) {
    val track = Paper.ringTrack
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val lineWidth = this.size.minDimension * 0.11f
            val radius = (this.size.minDimension - lineWidth) / 2
            val topLeft = Offset(this.size.width / 2 - radius, this.size.height / 2 - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(lineWidth))
            val sweep = (360.0 * fraction.coerceIn(0.0, 1.0)).toFloat()
            if (sweep > 0) {
                drawArc(
                    tint, -90f, sweep, false, topLeft, arcSize,
                    style = Stroke(lineWidth, cap = StrokeCap.Round),
                )
            }
        }
        Text(emoji, style = Theme.sans((size.value * 0.4f).toInt()))
    }
}

// MARK: - Lesson of the week

/**
 * One featured Education lesson, rotated by calendar week so it changes on its
 * own without any backend. Deterministic — the same lesson all week for
 * everyone — and every entry points at a real topic.
 */
@Composable
private fun LessonOfWeekCard() {
    val week = LocalDate.now().get(WeekFields.of(Locale.US).weekOfWeekBasedYear())
    val lesson = weeklyLessons[abs(week) % weeklyLessons.size]

    BigCard(onClick = { CrossTabNavigation.request("education") }) {
        BigCardHeader("LESSON OF THE WEEK")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                lesson.second,
                style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
                color = Paper.cobalt,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Paper.cobaltSoft)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
            // Two lines max — a third pushes the card past the shared height.
            Text(
                lesson.third,
                style = Theme.sans(17, FontWeight.Bold),
                color = Paper.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal val weeklyLessons: List<Triple<String, String, String>> = listOf(
    Triple("retirement", "RETIREMENT", "The Effect of Time on Your Retirement Savings"),
    Triple("investing", "INVESTING", "How to Invest with Confidence"),
    Triple("budgeting", "BUDGETING", "Building a Budget That Actually Sticks"),
    Triple("credit", "CREDIT & DEBT", "Paying Down Debt Without Losing Momentum"),
    Triple("taxes", "TAX PLANNING", "Understanding the Taxes You Pay"),
)

// MARK: - Calculators

@Composable
private fun CalculatorsSection(onOpen: (CalculatorKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // No "All" link: this section already lists every calculator.
        PaperSectionHeader("CALCULATORS")
        Column(Modifier.paperCard(22.dp)) {
            CalculatorKind.entries.forEachIndexed { index, kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fcPressable { onOpen(kind) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            // One tint for every chip: a calculator isn't
                            // urgent or growing, it's just a calculator.
                            .background(Theme.colors.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            kind.icon,
                            contentDescription = null,
                            tint = Theme.colors.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        shortTitle(kind),
                        style = Theme.sans(13, FontWeight.SemiBold),
                        color = Paper.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Paper.chevron,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (index < CalculatorKind.entries.size - 1) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Paper.divider)
                    )
                }
            }
        }
    }
}

internal fun shortTitle(kind: CalculatorKind): String = kind.title
    .replace(" Calculator", "")
    .replace(" Estimator", "")
    .replace(" Projector", "")

/** Referenced so the Education catalog stays a compile-time dependency here. */
internal val lessonTopicIds: List<String> = weeklyLessons.map { it.first }
    .filter { id -> EducationContent.topics.any { it.first == id } }
