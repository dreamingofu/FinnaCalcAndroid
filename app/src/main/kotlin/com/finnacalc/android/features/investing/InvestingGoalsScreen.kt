//
// InvestingGoalsScreen.kt
//
// Port of the Goals surfaces from iOS Features/Investing/InvestingGoals.swift —
// the section on the Portfolio page, the goal card, and the add/edit form.
//
// Two kinds: Growth chases a value, Balance keeps a slice inside a line. A
// goal reads "—" rather than a number whenever the portfolio can't answer it
// (no positions, or a percent goal whose scope has no cost basis), because a
// zero would read as "no progress" when the truth is "not known".
//
// Deviation from iOS: the form is a bottom sheet rather than a pushed screen,
// matching the budgeting goal editor already shipped here.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.FCButtonVariant
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.notifications.Notifier
import com.finnacalc.android.core.snaptrade.BrokeragePosition
import com.finnacalc.android.features.budgeting.GoalRing
import com.finnacalc.android.features.calculators.CalcFormat
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestingGoalsSection(positions: List<BrokeragePosition>) {
    val goals by InvestingGoalStore.shared.goals.collectAsState()
    var editing by remember { mutableStateOf<InvestingGoal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<InvestingGoal?>(null) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Goals",
                style = Theme.sans(18, FontWeight.Bold),
                color = Theme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Theme.colors.brandTint)
                    .fcPressable { creating = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Theme.colors.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text("New goal", style = Theme.sans(12, FontWeight.Bold), color = Theme.colors.primary)
            }
        }

        if (goals.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Theme.colors.card)
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "No investing goals yet",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Text(
                    "Set a value to reach, a return to hit, or a cap on how much of the portfolio " +
                        "one thing can be. Progress reads from your live holdings.",
                    style = Theme.sans(12).copy(lineHeight = 17.sp),
                    color = Theme.colors.mutedForeground,
                )
            }
        } else {
            goals.forEach { goal ->
                InvestingGoalCard(
                    goal = goal,
                    positions = positions,
                    onEdit = { editing = goal },
                    onDelete = { confirmDelete = goal },
                )
            }
        }
    }

    val editingGoal = editing
    if (creating || editingGoal != null) {
        ModalBottomSheet(
            onDismissRequest = { creating = false; editing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Theme.colors.background,
        ) {
            InvestingGoalForm(
                existing = editingGoal,
                positions = positions,
                onCancel = { creating = false; editing = null },
                onSave = { goal ->
                    if (editingGoal == null) InvestingGoalStore.shared.add(goal)
                    else InvestingGoalStore.shared.update(goal)
                    // Asked at the consent moment: saving a goal with alerts.
                    if (goal.alerts.isNotEmpty() && Notifier.needsRuntimePermission(context)) {
                        InvestingGoalPermissionRequest.pending = true
                    }
                    creating = false
                    editing = null
                },
            )
        }
    }

    confirmDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this goal?") },
            text = {
                Text(
                    "“${goal.name}” and its alert history are removed. Your holdings aren't " +
                        "touched. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    InvestingGoalStore.shared.delete(goal)
                    confirmDelete = null
                }) { Text("Delete", color = Theme.colors.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
            containerColor = Theme.colors.card,
        )
    }
}

/** Set when a saved goal wants the POST_NOTIFICATIONS prompt; the shell asks. */
object InvestingGoalPermissionRequest {
    var pending: Boolean = false
}

// MARK: - Card

@Composable
private fun InvestingGoalCard(
    goal: InvestingGoal,
    positions: List<BrokeragePosition>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val ringColor = GoalRing.color(goal.ringColorHex) ?: Theme.colors.positive

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .fcPressable(onEdit)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.resolvedEmoji,
                style = Theme.sans(20),
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    goal.name.ifEmpty { "Investing goal" },
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Text(
                    goalSubtitle(goal),
                    style = Theme.sans(11),
                    color = Theme.colors.mutedForeground,
                )
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${goal.name}",
                tint = Theme.colors.borderStrong,
                modifier = Modifier
                    .size(18.dp)
                    .fcPressable(onDelete),
            )
        }

        if (goal.kind == InvestingGoalKind.Mix) {
            val reading = InvestingGoalMath.measureMix(goal, positions)
            if (reading.totalValue <= 0) {
                UnknownRow("Connect a brokerage and this reads your real mix.")
            } else {
                ProgressRow(
                    figure = CalcFormat.fixed(reading.weightPct, 1) + "%",
                    caption = if (goal.mixKeepUnder) {
                        "of the portfolio · cap ${CalcFormat.fixed(goal.targetValue, 0)}%"
                    } else {
                        "of the portfolio · floor ${CalcFormat.fixed(goal.targetValue, 0)}%"
                    },
                    fraction = (reading.weightPct / 100.0).coerceIn(0.0, 1.0),
                    tint = if (reading.compliant) Theme.colors.positive else Theme.colors.caution,
                )
            }
        } else {
            val reading = InvestingGoalMath.measure(goal, positions)
            val gain = reading.gainPct
            when {
                positions.isEmpty() ->
                    UnknownRow("Connect a brokerage and this reads your real holdings.")

                goal.targetKind == InvestingTargetKind.Percent && gain == null ->
                    // No cost basis reported: no honest percent to show.
                    UnknownRow("Your brokerage hasn't reported a cost basis for this scope yet.")

                goal.targetKind == InvestingTargetKind.Percent -> ProgressRow(
                    figure = (if (gain!! >= 0) "+" else "-") + CalcFormat.fixed(abs(gain), 1) + "%",
                    caption = "of a ${CalcFormat.fixed(goal.targetValue, 0)}% target return",
                    fraction = reading.fraction,
                    tint = ringColor,
                )

                else -> ProgressRow(
                    figure = "$" + CalcFormat.int(reading.current),
                    caption = "of $" + CalcFormat.int(goal.targetValue),
                    fraction = reading.fraction,
                    tint = ringColor,
                )
            }
        }
    }
}

private fun goalSubtitle(goal: InvestingGoal): String = when {
    goal.kind == InvestingGoalKind.Mix ->
        goal.mixScopeLabel.ifEmpty { "Your mix" } + (if (goal.mixKeepUnder) " · cap" else " · floor")

    goal.symbols.isEmpty() -> "Whole portfolio"
    else -> goal.symbols.joinToString(" · ")
}

@Composable
private fun ProgressRow(figure: String, caption: String, fraction: Double, tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(figure, style = Theme.figure(24, FontWeight.Bold), color = Theme.colors.foreground)
            Text(
                caption,
                style = Theme.sans(11),
                color = Theme.colors.mutedForeground,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.secondary)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                    .padding(vertical = 3.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

/** The house rule: unknown reads as a dash and a reason, never as zero. */
@Composable
private fun UnknownRow(reason: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("—", style = Theme.figure(24, FontWeight.Bold), color = Theme.colors.mutedForeground)
        Text(reason, style = Theme.sans(11), color = Theme.colors.mutedForeground)
    }
}

// MARK: - Form

@Composable
private fun InvestingGoalForm(
    existing: InvestingGoal?,
    positions: List<BrokeragePosition>,
    onCancel: () -> Unit,
    onSave: (InvestingGoal) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: InvestingGoalKind.Growth) }
    var targetKind by remember { mutableStateOf(existing?.targetKind ?: InvestingTargetKind.Amount) }
    var target by remember {
        mutableStateOf(existing?.targetValue?.takeIf { it > 0 }?.let { CalcFormat.fixed(it, 0) } ?: "")
    }
    var symbols by remember { mutableStateOf(existing?.symbols?.joinToString(", ") ?: "") }
    var keepUnder by remember { mutableStateOf(existing?.mixKeepUnder ?: true) }
    var alerts by remember { mutableStateOf(existing?.alerts?.toSet() ?: emptySet()) }

    val targetValue = target.replace(",", "").toDoubleOrNull() ?: 0.0
    val canSave = name.isNotBlank() && targetValue > 0

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (existing == null) "New investing goal" else "Edit goal",
            style = Theme.sans(17, FontWeight.Bold),
            color = Theme.colors.foreground,
        )

        FieldLabel("NAME")
        FCTextField("e.g. Reach $50k", name, { name = it }, Modifier.fillMaxWidth(), showsPlaceholder = true)

        FieldLabel("WHAT THIS GOAL WATCHES")
        SegmentRow(
            listOf("Growth" to InvestingGoalKind.Growth, "Balance" to InvestingGoalKind.Mix),
            kind,
        ) { kind = it }

        if (kind == InvestingGoalKind.Growth) {
            FieldLabel("TARGET")
            SegmentRow(
                listOf("Dollar value" to InvestingTargetKind.Amount, "Total return" to InvestingTargetKind.Percent),
                targetKind,
            ) { targetKind = it }
            FCTextField(
                if (targetKind == InvestingTargetKind.Amount) "50000" else "25",
                target,
                { target = it },
                Modifier.fillMaxWidth(),
                showsPlaceholder = true,
            )
        } else {
            FieldLabel("KEEP THE SLICE")
            SegmentRow(listOf("Under" to true, "Above" to false), keepUnder) { keepUnder = it }
            FieldLabel("PERCENT OF PORTFOLIO")
            FCTextField("20", target, { target = it }, Modifier.fillMaxWidth(), showsPlaceholder = true)
        }

        FieldLabel("SYMBOLS (BLANK = WHOLE PORTFOLIO)")
        FCTextField("AAPL, MSFT", symbols, { symbols = it }, Modifier.fillMaxWidth(), showsPlaceholder = true)

        FieldLabel("ALERT ME AT")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvestingGoalAlertCenter.thresholds.forEach { mark ->
                val on = alerts.contains(mark)
                Text(
                    "$mark%",
                    style = Theme.sans(12, FontWeight.SemiBold),
                    color = if (on) Color.White else Theme.colors.foreground,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) Theme.colors.primary else Theme.colors.secondary)
                        .fcPressable { alerts = if (on) alerts - mark else alerts + mark }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FCButton("Cancel", variant = FCButtonVariant.Outline, size = FCButtonSize.Lg, onClick = onCancel)
            FCButton(
                "Save goal",
                size = FCButtonSize.Lg,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            ) {
                val parsed = symbols.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val goal = (existing ?: InvestingGoal(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    targetValue = targetValue,
                    baselineValue = 0.0,
                )).copy(
                    name = name.trim(),
                    kind = kind,
                    targetKind = if (kind == InvestingGoalKind.Mix) InvestingTargetKind.Percent else targetKind,
                    targetValue = targetValue,
                    symbols = parsed,
                    mixKeepUnder = keepUnder,
                    mixScope = MixScope.Holdings,
                    alerts = alerts.sorted(),
                    // A changed target invalidates which marks already fired.
                    alertsFired = if (existing?.targetValue == targetValue) {
                        existing?.alertsFired ?: emptyList()
                    } else emptyList(),
                    baselineValue = existing?.baselineValue
                        ?: InvestingGoalMath.scopeValue(
                            InvestingGoal(id = "probe", name = "", targetValue = 1.0, symbols = parsed),
                            positions,
                        ),
                )
                onSave(goal)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.sp),
        color = Theme.colors.mutedForeground,
    )
}

@Composable
private fun <T> SegmentRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Theme.colors.secondary)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) Theme.colors.card else Color.Transparent)
                    .fcPressable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Theme.sans(12, if (on) FontWeight.Bold else FontWeight.Normal),
                    color = if (on) Theme.colors.foreground else Theme.colors.mutedForeground,
                )
            }
        }
    }
}
