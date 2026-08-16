//
// HistoryScreen.kt
//
// Port of iOS Features/Budgeting/HistoryTabView.swift — saved budget
// snapshots and bank imports over time: newest three regardless of year,
// then a year the user steps through, split by quarter. Each row opens its
// lines; a snapshot can be re-imported into the editor or deleted.
//

package com.finnacalc.android.features.budgeting

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.util.HistoryDate
import com.finnacalc.android.features.calculators.CalcFormat
import java.time.Year

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(store: BudgetStore, onImport: (BudgetHistoryEntry) -> Unit) {
    store.version.collectAsState().value

    var year by remember { mutableStateOf(Year.now().value) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<BudgetHistoryEntry?>(null) }

    val entries = store.currentHistory
    val recent = entries.take(3)
    val quarters = listOf(
        "Jan to Mar" to 1..3, "Apr to Jun" to 4..6,
        "Jul to Sep" to 7..9, "Oct to Dec" to 10..12,
    )
    fun inQuarter(months: IntRange): List<BudgetHistoryEntry> = entries.filter { entry ->
        val start = HistoryDate.parse(entry.startDate) ?: return@filter false
        start.year == year && start.monthValue in months
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("History", style = Theme.sans(17, FontWeight.SemiBold), color = Theme.colors.foreground)
        Text(
            "Budgets you saved and transactions you imported, kept as snapshots. Opening one shows the lines it holds; importing pulls them back into My Budget.",
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
        )

        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No saved snapshots", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
                Text(
                    "Save a budget to History from My Budget, or import a bank statement.",
                    style = Theme.sans(13),
                    color = Theme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            if (recent.isNotEmpty()) {
                HistoryGroup("RECENTLY ADDED", recent, expanded, { key ->
                    expanded = if (key in expanded) expanded - key else expanded + key
                }, onImport) { pendingDelete = it }
            }
            // Year stepper.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Theme.colors.secondary)
                        .clickable { year -= 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year", tint = Theme.colors.primary, modifier = Modifier.size(16.dp))
                }
                Text("$year", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Theme.colors.secondary)
                        .clickable { year += 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year", tint = Theme.colors.primary, modifier = Modifier.size(16.dp))
                }
            }
            var any = false
            quarters.forEach { (title, months) ->
                val list = inQuarter(months)
                if (list.isNotEmpty()) {
                    any = true
                    HistoryGroup(title.uppercase(), list, expanded, { key ->
                        expanded = if (key in expanded) expanded - key else expanded + key
                    }, onImport) { pendingDelete = it }
                }
            }
            if (recent.isEmpty() && !any) {
                Text(
                    "Nothing saved in $year. Use the arrows to look at another year.",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this snapshot?") },
            text = {
                Text(
                    "“${entry.name}” and the ${entry.budgetItems.size} line${if (entry.budgetItems.size == 1) "" else "s"} " +
                        "it holds are removed from History. Your live budget, goals and connected banks are " +
                        "untouched. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteSnapshot(entry)
                    pendingDelete = null
                }) { Text("Delete snapshot", color = Theme.colors.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep it") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryGroup(
    title: String,
    entries: List<BudgetHistoryEntry>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onImport: (BudgetHistoryEntry) -> Unit,
    onDelete: (BudgetHistoryEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.1.sp),
            color = Theme.colors.mutedForeground,
        )
        entries.forEach { entry ->
            var menu by remember(entry.id) { mutableStateOf(false) }
            val isOpen = entry.id in expanded
            val shape = RoundedCornerShape(16.dp)
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Theme.colors.card)
                        .border(1.dp, Theme.colors.border, shape),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onToggle(entry.id) }, onLongClick = { menu = true })
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.name, style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(HistoryDate.range(entry.startDate, entry.endDate), style = Theme.sans(12), color = Theme.colors.mutedForeground)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${entry.budgetItems.size} line${if (entry.budgetItems.size == 1) "" else "s"}",
                                    style = Theme.figure(11, FontWeight.SemiBold), color = Theme.colors.mutedForeground,
                                )
                                Text("+$" + CalcFormat.int(entry.monthlyIncome), style = Theme.figure(11, FontWeight.SemiBold), color = Theme.colors.positive)
                                Text("−$" + CalcFormat.int(entry.monthlyExpenses), style = Theme.figure(11, FontWeight.SemiBold), color = Theme.colors.negative)
                            }
                        }
                        Icon(
                            if (isOpen) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null, tint = Theme.colors.borderStrong, modifier = Modifier.size(18.dp),
                        )
                    }

                    if (isOpen) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                        // The lines it holds, largest first.
                        entry.budgetItems.sortedByDescending { it.monthlyAmount }.take(20).forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.subcategory.ifEmpty { item.category },
                                        style = Theme.sans(13, FontWeight.SemiBold), color = Theme.colors.foreground,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(item.category, style = Theme.sans(11), color = Theme.colors.mutedForeground)
                                }
                                Text(
                                    (if (item.type == ItemType.Income) "+" else "−") + "$" + CalcFormat.int(item.monthlyAmount),
                                    style = Theme.figure(12, FontWeight.SemiBold),
                                    color = if (item.type == ItemType.Income) Theme.colors.positive else Theme.colors.negative,
                                )
                            }
                        }
                        if (entry.budgetItems.size > 20) {
                            Text(
                                "+ ${entry.budgetItems.size - 20} more — import to see them all",
                                style = Theme.sans(11), color = Theme.colors.mutedForeground,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.colors.border))
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Import into My Budget",
                                style = Theme.sans(12, FontWeight.Bold), color = Theme.colors.primary,
                                modifier = Modifier
                                    .clip(CircleShape).background(Theme.colors.brandTint)
                                    .fcPressable { onImport(entry) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                            Text(
                                "Delete",
                                style = Theme.sans(12, FontWeight.Bold), color = Theme.colors.destructive,
                                modifier = Modifier
                                    .clip(CircleShape).background(Theme.colors.destructive.copy(alpha = 0.1f))
                                    .fcPressable { onDelete(entry) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Import into My Budget") }, onClick = {
                        menu = false
                        onImport(entry)
                    })
                    DropdownMenuItem(
                        text = { Text("Delete", color = Theme.colors.destructive) },
                        onClick = {
                            menu = false
                            onDelete(entry)
                        },
                    )
                }
            }
        }
    }
}
