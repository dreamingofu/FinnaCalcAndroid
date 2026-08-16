//
// GoalsScreen.kt
//
// Port of iOS Features/Budgeting/GoalsTabView.swift — one card per goal
// (progress ring + saved/target, a Remaining / Needed-per-month /
// Planned-per-month stat row, a pace line, and a warning when the planned
// contribution falls short), plus add / add-funds / edit sheets and a
// delete confirm.
//
// Per-goal math mirrors the web component:
//   progress       = current / target * 100
//   remaining      = target - current
//   daysLeft       = differenceInDays(targetDate, today)
//   monthsLeft     = daysLeft > 0 ? ceil(daysLeft / 30.44) : 0
//   neededPerMonth = monthsLeft > 0 ? remaining / monthsLeft : remaining
//
// Deviation from iOS: goal alert notifications (GoalAlertCenter) land with
// the notification infrastructure in Phase 8; the alert thresholds are still
// stored on the goal by the form.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Paper
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.CalcSegmentedControl
import com.finnacalc.android.features.calculators.CalcSelectField
import com.finnacalc.android.features.calculators.calcValue
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

// MARK: - Metrics

internal data class GoalMetrics(
    val progress: Double,
    val remaining: Double,
    val daysLeft: Long,
    val monthsLeft: Int,
    /** Required monthly saving to hit the date; null without a target date. */
    val neededPerMonth: Double?,
    val hasTargetDate: Boolean,
) {
    companion object {
        fun of(goal: SavingsGoal, current: Double): GoalMetrics {
            val target = goal.targetAmount
            val progress = if (target != 0.0) current / target * 100
            else if (current > 0) Double.POSITIVE_INFINITY else 0.0
            val remaining = target - current
            val parsed = runCatching { LocalDate.parse(goal.targetDate.take(10)) }.getOrNull()
            val daysLeft = parsed?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) } ?: 0L
            val monthsLeft = if (daysLeft > 0) ceil(daysLeft / 30.44).toInt() else 0
            return GoalMetrics(
                progress = progress,
                remaining = remaining,
                daysLeft = daysLeft,
                monthsLeft = monthsLeft,
                neededPerMonth = if (parsed == null) null
                else if (monthsLeft > 0) remaining / monthsLeft else remaining,
                hasTargetDate = parsed != null,
            )
        }
    }
}

// MARK: - Screen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalsScreen(store: BudgetStore) {
    store.version.collectAsState().value
    val ledger = BankLedgerStore.shared
    ledger.version.collectAsState().value

    var formGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var addingNew by remember { mutableStateOf(false) }
    var fundsGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var pendingDelete by remember { mutableStateOf<SavingsGoal?>(null) }

    val goals = store.currentGoals

    Box(Modifier.fillMaxSize().background(Theme.colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Goals", style = Theme.sans(17, FontWeight.SemiBold), color = Theme.colors.foreground)

            if (goals.isEmpty()) {
                GoalsEmptyState { addingNew = true }
            } else {
                goals.forEachIndexed { index, goal ->
                    GoalCard(
                        goal = goal,
                        ledger = ledger,
                        // The chosen colour; the default green alternates
                        // strength like the goal rings on Home.
                        ringColor = GoalRing.color(goal.ringColorHex)
                            ?: if (index % 2 == 0) Theme.colors.positive else Theme.colors.positive.copy(alpha = 0.6f),
                        onFunds = { fundsGoal = goal },
                        onEdit = { formGoal = goal },
                        onDelete = { pendingDelete = goal },
                    )
                }
            }
            Spacer(Modifier.height(60.dp))
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable { addingNew = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add goal", tint = Color.White)
        }
    }

    if (addingNew) {
        GoalFormSheet(
            store = store,
            existing = null,
            onDismiss = { addingNew = false },
            onSave = {
                store.addGoal(it)
                addingNew = false
            },
        )
    }

    formGoal?.let { goal ->
        GoalFormSheet(
            store = store,
            existing = goal,
            onDismiss = { formGoal = null },
            onSave = {
                store.updateGoal(it)
                formGoal = null
            },
        )
    }

    fundsGoal?.let { goal ->
        AddFundsSheet(
            goal = goal,
            onDismiss = { fundsGoal = null },
            onAdd = { amount ->
                store.addFunds(goal, amount)
                fundsGoal = null
            },
        )
    }

    pendingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this goal?") },
            text = {
                Text(
                    "${goal.name} is removed, along with the progress recorded on it. Your budget lines, " +
                        "saved history and other goals are kept. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteGoal(goal)
                    pendingDelete = null
                }) { Text("Delete goal", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun GoalsEmptyState(onAdd: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎯", style = Theme.sans(30))
        Text("No goals yet", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
        Text(
            if (Entitlements.paidBudgeting) {
                "Track what you're saving toward, cap what you spend, or set an income target."
            } else {
                "Track what you're saving toward and watch the progress ring fill."
            },
            style = Theme.sans(13),
            color = Theme.colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
        Text(
            "+ Add goal",
            style = Theme.sans(13, FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onAdd)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

// MARK: - Goal card

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalCard(
    goal: SavingsGoal,
    ledger: BankLedgerStore,
    ringColor: Color,
    onFunds: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val (current, target) = GoalProgress.measure(goal, ledger)
    val fraction = if (target > 0) current / target else 0.0
    val metrics = GoalMetrics.of(goal, current)
    // A spending goal past its limit is the one state that must not look like
    // success: the ring goes negative-red instead of full-green.
    val overLimit = goal.kind == GoalKind.Spending && fraction >= 1
    val drawColor = if (overLimit) Theme.colors.negative else ringColor

    // How far short the plan falls per month. Never for a spending cap:
    // planning to spend less than the limit allows is the good case.
    val shortfall: Double? = run {
        if (goal.kind == GoalKind.Spending) return@run null
        val needed = metrics.neededPerMonth ?: return@run null
        if (metrics.progress >= 100 || goal.monthlyContribution <= 0 || needed <= 0) return@run null
        val gap = needed - goal.monthlyContribution
        if (gap > 0) gap else null
    }

    val shape = RoundedCornerShape(20.dp)
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, shape)
                .combinedClickable(onClick = onEdit, onLongClick = { menu = true })
                .padding(16.dp),
        ) {
            // Top row: ring + name/target.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GoalRingView(fraction = fraction, color = drawColor, emoji = GoalEmoji.resolve(goal))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        goal.name.ifEmpty { "Untitled goal" },
                        style = Theme.sans(16, FontWeight.Bold),
                        color = Theme.colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${Paper.compactMoney(current)} of ${Paper.compactMoney(target)} ${goal.kind.progressVerb}",
                        style = Theme.figure(13, FontWeight.SemiBold),
                        color = if (overLimit) Theme.colors.negative else Theme.colors.mutedForeground,
                    )
                    GoalProgress.ruleLabel(goal, ledger)?.let { rule ->
                        Text(rule, style = Theme.sans(11), color = Theme.colors.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Pace badge.
                val badge = when {
                    overLimit -> "over limit" to Theme.colors.negative
                    fraction >= 1 -> "reached" to Theme.colors.positive
                    shortfall != null -> "behind" to Theme.colors.caution
                    else -> "on track" to Theme.colors.positive
                }
                Text(
                    badge.first,
                    style = Theme.sans(10, FontWeight.Bold),
                    color = badge.second,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badge.second.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 13.dp).height(1.dp).background(Theme.colors.border))

            // Stat row: remaining · needed/mo · planned/mo.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalStat(
                    if (goal.kind == GoalKind.Spending) "LEFT TO SPEND" else "REMAINING",
                    Paper.compactMoney(kotlin.math.max(metrics.remaining, 0.0)),
                    Modifier.weight(1f),
                )
                GoalStat(
                    "NEEDED / MO",
                    metrics.neededPerMonth?.let { Paper.compactMoney(kotlin.math.max(it, 0.0)) } ?: "—",
                    Modifier.weight(1f),
                )
                GoalStat(
                    "PLANNED / MO",
                    if (goal.monthlyContribution > 0) Paper.compactMoney(goal.monthlyContribution) else "—",
                    Modifier.weight(1f),
                )
            }

            // Pace line — the unit follows the horizon, always "on average".
            paceLine(goal, metrics)?.let { pace ->
                Text(pace, style = Theme.sans(12), color = Theme.colors.mutedForeground, modifier = Modifier.padding(top = 10.dp))
            }

            if (shortfall != null) {
                val projection = if (goal.monthlyContribution > 0) {
                    val months = ceil(metrics.remaining / goal.monthlyContribution).toInt()
                    " At ${Paper.compactMoney(goal.monthlyContribution)}/mo it lands in about $months months instead."
                } else ""
                Text(
                    "${Paper.compactMoney(shortfall)}/mo short of the ${Paper.compactMoney(metrics.neededPerMonth ?: 0.0)}/mo " +
                        "this date needs.$projection",
                    style = Theme.sans(12),
                    color = Theme.colors.caution,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Theme.Radius.md))
                        .background(Theme.colors.cautionTint)
                        .padding(10.dp),
                )
            } else {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (goal.kind) {
                            GoalKind.Saving -> "+ Add funds"
                            GoalKind.Spending -> "+ Add spending"
                            GoalKind.Income -> "+ Add income"
                        },
                        style = Theme.sans(12, FontWeight.Bold),
                        color = Theme.colors.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Theme.colors.brandTint)
                            .fcPressable(onFunds)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                    Text(
                        "Edit",
                        style = Theme.sans(12, FontWeight.Bold),
                        color = Theme.colors.mutedForeground,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Theme.colors.secondary)
                            .fcPressable(onEdit)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        when (goal.kind) {
                            GoalKind.Saving -> "Add Funds"
                            GoalKind.Spending -> "Add Spending"
                            GoalKind.Income -> "Add Income"
                        }
                    )
                },
                onClick = {
                    menu = false
                    onFunds()
                },
            )
            DropdownMenuItem(text = { Text("Edit Goal") }, onClick = {
                menu = false
                onEdit()
            })
            DropdownMenuItem(
                text = { Text("Delete Goal", color = Theme.colors.destructive) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * "Save about $83.33 a week on average to hit Oct 14." The unit follows the
 * horizon: days when it is close, weeks inside a quarter, months beyond.
 */
private fun paceLine(goal: SavingsGoal, metrics: GoalMetrics): String? {
    if (!metrics.hasTargetDate || metrics.remaining <= 0) return null
    val days = metrics.daysLeft
    if (days <= 0) return null
    val verb = when (goal.kind) {
        GoalKind.Saving -> "Save"
        GoalKind.Spending -> "Stay under"
        GoalKind.Income -> "Earn"
    }
    val target = runCatching { LocalDate.parse(goal.targetDate.take(10)) }.getOrNull() ?: return null
    val date = com.finnacalc.android.core.util.HistoryDate.medium(target)
    return when {
        days <= 31 -> "$verb about ${Paper.compactMoney(metrics.remaining / days)} a day on average to hit $date."
        days <= 92 -> "$verb about ${Paper.compactMoney(metrics.remaining / (days / 7.0))} a week on average to hit $date."
        else -> "$verb about ${Paper.compactMoney(metrics.remaining / (days / 30.44))} a month on average to hit $date."
    }
}

@Composable
private fun GoalStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = Theme.sans(9, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
            color = Theme.colors.mutedForeground,
        )
        Text(value, style = Theme.figure(14, FontWeight.Bold), color = Theme.colors.foreground, maxLines = 1)
    }
}

/** Progress ring with the goal's emoji at its centre. */
@Composable
private fun GoalRingView(fraction: Double, color: Color, emoji: String) {
    val track = Theme.colors.secondary
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val radius = (minOf(size.width, size.height) - stroke) / 2
            val topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = (360.0 * min(kotlin.math.max(fraction, 0.0), 1.0)).toFloat()
            if (sweep > 0) {
                drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text(emoji, style = Theme.sans(22))
    }
}

// MARK: - Add funds sheet

@Composable
private fun AddFundsSheet(goal: SavingsGoal, onDismiss: () -> Unit, onAdd: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    val title = when (goal.kind) {
        GoalKind.Saving -> "Add funds"
        GoalKind.Spending -> "Add spending"
        GoalKind.Income -> "Add income"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title — ${goal.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FCTextField(
                    "Amount", amount, { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    showsPlaceholder = true,
                )
                Text(
                    "Counts toward this goal's progress. It doesn't create a budget line.",
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.mutedForeground,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(amount.calcValue) }, enabled = amount.calcValue > 0) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// MARK: - Goal form

@Composable
private fun GoalFormSheet(
    store: BudgetStore,
    existing: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (SavingsGoal) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.targetAmount?.let { CalcFormat.int(it) } ?: "") }
    var current by remember { mutableStateOf(existing?.currentAmount?.let { CalcFormat.int(it) } ?: "") }
    var contribution by remember { mutableStateOf(existing?.monthlyContribution?.let { CalcFormat.int(it) } ?: "") }
    var targetDate by remember { mutableStateOf(existing?.targetDate ?: LocalDate.now().plusYears(1).toString()) }
    var kind by remember { mutableStateOf(existing?.kind ?: GoalKind.Saving) }
    var emoji by remember { mutableStateOf(existing?.emoji) }
    var ringHex by remember { mutableStateOf(existing?.ringColorHex) }
    var alerts by remember { mutableStateOf(existing?.alerts ?: emptyList()) }

    // Spending and income goals are read from a linked bank, so they only
    // exist for connected users (Entitlements.paidBudgeting).
    val kinds = if (Entitlements.paidBudgeting) GoalKind.entries else listOf(GoalKind.Saving)
    val isValid = name.trim().isNotEmpty() && target.calcValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit goal" else "New goal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (kinds.size > 1) {
                    CalcSegmentedControl(kind, { kind = it }, kinds.map { it to it.title })
                }
                FCTextField("Goal name", name, { name = it }, showsPlaceholder = true)
                FCTextField(
                    if (kind.isLimit) "Limit amount" else "Target amount", target, { target = it },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    showsPlaceholder = true,
                )
                FCTextField(
                    "Saved so far", current, { current = it },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    showsPlaceholder = true,
                )
                FCTextField(
                    "Planned per month", contribution, { contribution = it },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    showsPlaceholder = true,
                )
                FCTextField("Target date (yyyy-mm-dd)", targetDate, { targetDate = it }, showsPlaceholder = true)
                CalcSelectField(
                    "Icon", emoji ?: "", { emoji = it.ifEmpty { null } },
                    listOf("" to "Automatic (${GoalEmoji.suggest(name)})") + GoalEmoji.palette.map { it to it },
                )
                CalcSelectField(
                    "Ring colour", ringHex ?: "", { ringHex = it.ifEmpty { null } },
                    listOf("" to "Default green") + GoalRing.palette.map { it to "#$it" },
                )
                // Alert thresholds — stored now; the notifications that read
                // them land in Phase 8.
                Text("Alert me at", style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium), color = Theme.colors.foreground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalProgress.alertThresholds.forEach { threshold ->
                        val on = threshold in alerts
                        Text(
                            "$threshold%",
                            style = Theme.sans(12, if (on) FontWeight.Bold else FontWeight.Medium),
                            color = if (on) Color.White else Theme.colors.mutedForeground,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) Theme.colors.primary else Theme.colors.secondary)
                                .clickable {
                                    alerts = if (on) alerts - threshold else (alerts + threshold).sorted()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val goal = (existing ?: SavingsGoal(
                        id = UUID.randomUUID().toString(),
                        name = "", targetAmount = 0.0, currentAmount = 0.0,
                        targetDate = "", monthlyContribution = 0.0,
                        budgetType = store.budgetType,
                    )).copy(
                        name = name.trim(),
                        targetAmount = target.calcValue,
                        currentAmount = current.calcValue,
                        targetDate = targetDate.trim(),
                        monthlyContribution = contribution.calcValue,
                        kind = kind,
                        emoji = emoji,
                        ringColorHex = ringHex,
                        alerts = alerts,
                    )
                    onSave(goal)
                },
            ) { Text(if (existing != null) "Save" else "Add goal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
