//
// CategoryItemsScreen.kt
//
// Port of iOS BudgetTabView.swift's CategoryItemsView — every item in one
// category ("View all N"), same row language, edit/delete flows of its own.
// Rows are derived live from the stores (rather than passed as a static
// snapshot) so the list can't disagree with the section it was opened from.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.features.calculators.calcValue

@Composable
fun CategoryItemsScreen(store: BudgetStore, dest: BudgetingDest.CategoryItems) {
    store.version.collectAsState().value
    val bank = BankLedgerStore.shared
    bank.version.collectAsState().value

    var editing by remember { mutableStateOf<ItemDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<BudgetItem?>(null) }

    val source = if (dest.readingBank) {
        bank.currentItems(store.budgetType)
    } else {
        store.itemsInMonth(store.rememberedSlot)
    }
    val items = source
        .filter { it.type == dest.direction && it.category == dest.category }
        .sortedByDescending { it.monthlyAmount }

    fun applyEdit(finished: ItemDraft) {
        val id = finished.itemID ?: return
        if (dest.readingBank) {
            val name = finished.subcategory.ifEmpty { finished.category }
            bank.updateEntry(id, name, finished.amount.calcValue, finished.type, finished.category)
            return
        }
        store.items.firstOrNull { it.id == id }?.let { original ->
            store.updateItem(original.applyDraft(finished))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            dest.category,
            style = Theme.sans(17, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, shape),
        ) {
            items.forEachIndexed { index, item ->
                BudgetItemRow(
                    item, indent = false, readingBank = dest.readingBank,
                    onEdit = { editing = ItemDraft.from(it) },
                    onDelete = { pendingDelete = it },
                )
                if (index < items.size - 1) {
                    Box(Modifier.fillMaxWidth().padding(start = 15.dp).height(1.dp).background(Theme.colors.border))
                }
            }
        }
    }

    editing?.let { d ->
        BudgetItemSheet(
            store = store,
            initial = d,
            onDismiss = { editing = null },
            onSave = { finished ->
                applyEdit(finished)
                editing = null
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this item?") },
            confirmButton = {
                TextButton(onClick = {
                    if (dest.readingBank) bank.deleteEntry(item.id) else store.deleteItem(item)
                    pendingDelete = null
                }) {
                    Text(
                        "Delete ${item.subcategory.ifEmpty { item.category }}",
                        color = Theme.colors.destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}
