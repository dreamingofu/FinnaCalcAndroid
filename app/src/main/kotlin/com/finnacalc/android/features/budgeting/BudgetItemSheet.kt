//
// BudgetItemSheet.kt
//
// Port of iOS BudgetTabView.ItemDraft + BudgetItemSheet — the add/edit form:
// type, category, frequency, description, amount, fixed toggle, and the
// subscription schedule (cadence + anchor + reminder) with a live preview of
// the rule and its next real charge date.
//
// The draft holds the schedule as flat fields rather than a built
// ChargeSchedule so flipping cadence back and forth doesn't throw away the
// answers already given; resolvedSchedule() assembles them on save.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.util.HistoryDate
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.calculators.CalcSegmentedControl
import com.finnacalc.android.features.calculators.CalcSelectField
import com.finnacalc.android.features.calculators.calcValue
import java.time.LocalDate
import java.time.LocalDateTime

// MARK: - Draft

/** Draft for the add/edit sheet. `itemID == null` means "add new". */
data class ItemDraft(
    val itemID: String? = null,
    val type: ItemType = ItemType.Income,
    val category: String = "",
    val subcategory: String = "",
    val amount: String = "",
    val frequency: Frequency = Frequency.Monthly,
    val isFixed: Boolean = false,
    val isSubscription: Boolean = false,
    val cadence: ChargeCadence = ChargeCadence.Monthly,
    /** Monthly/quarterly/annually: pin by day-of-month or by nth weekday. */
    val byWeekday: Boolean = false,
    /** Weekly/biweekly: pin by weekday, or by a strict day stride. */
    val byStride: Boolean = false,
    /** 0 means "the last day". */
    val dayOfMonth: Int = 1,
    /** 1..4, or 5 for "last". */
    val week: Int = 1,
    /** Calendar convention: Sunday = 1. */
    val weekday: Int = 2,
    val monthOfYear: Int = 1,
    /** ISO yyyy-MM-dd. */
    val startDate: String = LocalDate.now().toString(),
    val remind: Boolean = false,
) {
    /**
     * null when this isn't a subscription — the one place the flat fields
     * become the stored rule, so every save path agrees.
     */
    fun resolvedSchedule(): ChargeSchedule? {
        if (type != ItemType.Expense || !isSubscription) return null
        val anchor: ChargeAnchor = when (cadence) {
            ChargeCadence.Weekly ->
                if (byStride) ChargeAnchor.EveryNDays(startDate) else ChargeAnchor.Weekday(weekday)
            ChargeCadence.Biweekly ->
                // Clamp to the three pairs that make sense: 1st&3rd, 2nd&4th,
                // 3rd&last.
                if (byStride) ChargeAnchor.EveryNDays(startDate)
                else ChargeAnchor.NthWeekday(week.coerceIn(1, 3), weekday)
            ChargeCadence.Monthly, ChargeCadence.Quarterly, ChargeCadence.Annually ->
                if (byWeekday) ChargeAnchor.NthWeekday(week, weekday) else ChargeAnchor.Day(dayOfMonth)
        }
        return ChargeSchedule(
            cadence = cadence,
            anchor = anchor,
            month = if (cadence.needsMonth) monthOfYear else null,
            remind = remind,
        )
    }

    companion object {
        /**
         * Seeds the add/edit sheet from an existing line. The cadence AND
         * anchor are seeded from what the item already says, so flipping
         * Subscription on doesn't rewrite a Yearly expense's frequency (and
         * every total) to monthly.
         */
        fun from(item: BudgetItem): ItemDraft {
            var draft = ItemDraft(
                itemID = item.id,
                type = item.type,
                category = item.category,
                subcategory = item.subcategory,
                amount = CalcFormat.fixed(item.amount, 2).trimEnd('0').trimEnd('.'),
                frequency = item.frequency,
                isFixed = item.isFixed,
                cadence = ChargeCadence.from(item.frequency) ?: ChargeCadence.Monthly,
                byStride = item.frequency == Frequency.Biweekly,
            )
            val schedule = item.chargeSchedule ?: return draft
            draft = draft.copy(
                isSubscription = true,
                cadence = schedule.cadence,
                remind = schedule.remind,
                monthOfYear = schedule.month ?: 1,
            )
            return when (val anchor = schedule.anchor) {
                is ChargeAnchor.Day -> draft.copy(dayOfMonth = anchor.day, byWeekday = false, byStride = false)
                is ChargeAnchor.NthWeekday -> draft.copy(byWeekday = true, week = anchor.week, weekday = anchor.weekday, byStride = false)
                is ChargeAnchor.Weekday -> draft.copy(weekday = anchor.weekday, byStride = false)
                is ChargeAnchor.EveryNDays -> draft.copy(byStride = true, startDate = anchor.start)
            }
        }
    }
}

/** Applies an edited draft, preserving identity, month and budget type. */
fun BudgetItem.applyDraft(draft: ItemDraft): BudgetItem {
    val schedule = draft.resolvedSchedule()
    return copy(
        category = draft.category,
        subcategory = draft.subcategory,
        amount = draft.amount.calcValue,
        type = draft.type,
        isFixed = draft.isFixed,
        chargeSchedule = schedule,
        // The cadence IS the frequency for a subscription.
        frequency = schedule?.frequency ?: draft.frequency,
    )
}

// MARK: - Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetItemSheet(
    store: BudgetStore,
    initial: ItemDraft,
    onDismiss: () -> Unit,
    onSave: (ItemDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }

    val categories = if (draft.type == ItemType.Income) {
        BudgetCategories.income(store.budgetType)
    } else {
        BudgetCategories.expense(store.budgetType)
    }
    val isValid = draft.category.isNotEmpty() && draft.amount.calcValue > 0

    // "Every 2 weeks" and "Twice a month" are different rates but read as the
    // same thing in a plain frequency list, so only the clearer one is
    // offered (Every 2 weeks stays listed for an item that already carries it).
    val selectableFrequencies = Frequency.entries.filter {
        it != Frequency.Biweekly || draft.frequency == Frequency.Biweekly
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Cancel · title · Save.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.primary,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (draft.itemID != null) "Edit Item" else "Add Item",
                    style = Theme.sans(16, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (draft.itemID != null) "Save" else "Add",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = if (isValid) Theme.colors.primary else Theme.colors.mutedForeground,
                    modifier = Modifier.clickable(enabled = isValid) { onSave(draft) },
                )
            }

            CalcSegmentedControl(
                selection = draft.type,
                onSelectionChange = {
                    // Category lists differ per type.
                    draft = draft.copy(type = it, category = "")
                },
                options = ItemType.entries.map { it to it.title },
            )

            CalcSelectField(
                "Category", draft.category, { draft = draft.copy(category = it) },
                listOf("" to "Select…") + categories.map { it to it },
            )

            // A subscription's cadence IS its frequency, and saving writes it
            // through — so the row goes read-only while one is set.
            val schedule = draft.resolvedSchedule()
            if (schedule != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Frequency", style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium), color = Theme.colors.foreground)
                    Spacer(Modifier.weight(1f))
                    Text(schedule.frequency.title, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.mutedForeground)
                }
            } else {
                CalcSelectField(
                    "Frequency", draft.frequency, { draft = draft.copy(frequency = it) },
                    selectableFrequencies.map { it to it.title },
                )
            }

            FCTextField(
                "Description", draft.subcategory, { draft = draft.copy(subcategory = it) },
                showsPlaceholder = true,
            )
            FCTextField(
                "Amount", draft.amount, { draft = draft.copy(amount = it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                showsPlaceholder = true,
            )

            ToggleRow("Fixed amount", draft.isFixed) { draft = draft.copy(isFixed = it) }
            Text(
                "Fixed amounts don't vary month to month.",
                style = Theme.sans(Theme.FontSize.xs),
                color = Theme.colors.mutedForeground,
            )

            // Any expense can be a subscription — except Daily, the one
            // frequency with no subscription cadence.
            if (draft.type == ItemType.Expense && draft.frequency != Frequency.Daily) {
                ToggleRow("Subscription", draft.isSubscription) { draft = draft.copy(isSubscription = it) }
                if (draft.isSubscription) {
                    CalcSelectField(
                        "Charges", draft.cadence, { draft = draft.copy(cadence = it) },
                        ChargeCadence.entries.map { it to it.title },
                    )
                    ScheduleRows(draft) { draft = it }
                    ToggleRow("Remind me before it charges", draft.remind) { draft = draft.copy(remind = it) }
                }
                SubscriptionFooter(draft)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium), color = Theme.colors.foreground)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Theme.colors.primary),
        )
    }
}

/** The schedule question that fits the chosen cadence. */
@Composable
private fun ScheduleRows(draft: ItemDraft, onChange: (ItemDraft) -> Unit) {
    when (draft.cadence) {
        ChargeCadence.Weekly, ChargeCadence.Biweekly -> {
            CalcSegmentedControl(
                selection = draft.byStride,
                onSelectionChange = { onChange(draft.copy(byStride = it)) },
                options = listOf(
                    false to "Day of week",
                    true to if (draft.cadence == ChargeCadence.Weekly) "Every 7 days" else "Every 14 days",
                ),
            )
            if (draft.byStride) {
                // First charge date, ISO — a simple date field (the calendar
                // dialog arrives with the shared date-picker in 4c polish).
                FCTextField(
                    "First charge (yyyy-mm-dd)", draft.startDate,
                    { onChange(draft.copy(startDate = it)) },
                    showsPlaceholder = true,
                )
            } else {
                // Twice a month, so which two weeks matters: plenty of people
                // are paid on the 2nd and 4th, not the 1st and 3rd.
                if (draft.cadence == ChargeCadence.Biweekly) {
                    CalcSelectField(
                        "Weeks", draft.week.coerceIn(1, 3), { onChange(draft.copy(week = it)) },
                        (1..3).map { it to ChargeDateEngine.weekPairLabel(it) },
                    )
                }
                WeekdayPicker(draft, onChange)
            }
        }
        ChargeCadence.Monthly, ChargeCadence.Quarterly, ChargeCadence.Annually -> {
            if (draft.cadence.needsMonth) {
                CalcSelectField(
                    if (draft.cadence == ChargeCadence.Quarterly) "First charge month" else "Month",
                    draft.monthOfYear, { onChange(draft.copy(monthOfYear = it)) },
                    (1..12).map { it to (ChargeDateEngine.monthName(it) ?: "") },
                )
            }
            CalcSegmentedControl(
                selection = draft.byWeekday,
                onSelectionChange = { onChange(draft.copy(byWeekday = it)) },
                options = listOf(false to "Day of month", true to "Day of week"),
            )
            if (draft.byWeekday) {
                CalcSelectField(
                    "Week", draft.week, { onChange(draft.copy(week = it)) },
                    (1..5).map { it to ChargeDateEngine.ordinalWeek(it).replaceFirstChar { c -> c.uppercase() } },
                )
                WeekdayPicker(draft, onChange)
            } else {
                CalcSelectField(
                    "Charges on day", draft.dayOfMonth, { onChange(draft.copy(dayOfMonth = it)) },
                    (1..31).map { it to ChargeDateEngine.ordinal(it) } + listOf(0 to "Last day"),
                )
            }
        }
    }
}

@Composable
private fun WeekdayPicker(draft: ItemDraft, onChange: (ItemDraft) -> Unit) {
    CalcSelectField(
        "Charges on", draft.weekday, { onChange(draft.copy(weekday = it)) },
        (1..7).map { it to ChargeDateEngine.weekdayName(it) },
    )
}

/**
 * Spells the rule back out with the real next date — the cheapest way for
 * the user to catch a wrong pick before saving.
 */
@Composable
private fun SubscriptionFooter(draft: ItemDraft) {
    val schedule = draft.resolvedSchedule()
    val text = if (schedule != null) {
        val lead = schedule.cadence.leadDays
        val next = ChargeDateEngine.next(schedule, LocalDateTime.now())
        val whenText = next?.let { " · next ${HistoryDate.medium(it.toLocalDate())}" } ?: ""
        val tail = if (schedule.remind) {
            "We'll notify you $lead ${if (lead == 1) "day" else "days"} before."
        } else {
            "Tracked without a notification."
        }
        "${schedule.summary}$whenText. $tail You can see and edit every subscription in Budgeting → Subscriptions."
    } else {
        "Mark a recurring charge as a subscription to track it in Budgeting → Subscriptions and get a heads-up before it hits."
    }
    Text(text, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
}
