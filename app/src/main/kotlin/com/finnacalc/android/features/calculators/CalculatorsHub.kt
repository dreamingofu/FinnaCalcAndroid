//
// CalculatorsHub.kt
//
// Temporary Phase 3 hub for the standalone calculators — a categorized list of
// FCListRows over the catalog, hosted on the Home tab until the real Home
// dashboard (Phase 8) absorbs the catalog. Local back-stack (hub ↔ one
// calculator) with the system back gesture wired via BackHandler.
//

package com.finnacalc.android.features.calculators

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCListRow
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.staggeredAppear

@Composable
fun CalculatorsHubView() {
    var selected by rememberSaveable { mutableStateOf<CalculatorKind?>(null) }

    val current = selected
    if (current != null) {
        BackHandler { selected = null }
        CalculatorDestination(current)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Calculators",
                style = Theme.sans(Theme.FontSize.xl, FontWeight.SemiBold),
                color = Theme.colors.foreground,
                modifier = Modifier.padding(top = 6.dp),
            )
            val byCategory = CalculatorKind.entries.groupBy { it.category }
            var rowIndex = 0
            byCategory.forEach { (category, kinds) ->
                Text(
                    category.uppercase(),
                    style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
                    color = Theme.colors.mutedForeground,
                )
                FCCard {
                    kinds.forEach { kind ->
                        val index = rowIndex++
                        FCListRow(
                            icon = kind.icon,
                            title = kind.title,
                            subtitle = kind.summary,
                            modifier = Modifier
                                .staggeredAppear(index)
                                .fcPressable { selected = kind },
                        )
                    }
                }
            }
        }
    }
}
