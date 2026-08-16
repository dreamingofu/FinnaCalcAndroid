//
// TaxesScreen.kt
//
// Port of iOS Features/Taxes/TaxesView.swift — the Taxes tab: a launcher for
// the full-screen estimate experience plus the Calculators & Tools tab.
//
// iOS presents TaxFilingExperience as a fullScreenCover; here the tab swaps to
// it outright, which is the same effect (it takes over the app, no bottom bar)
// and keeps the system back gesture meaningful.
//

package com.finnacalc.android.features.taxes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.features.taxes.TaxViewModel

private enum class TaxesTab(val label: String) {
    File("Tax Estimate"),
    Calculators("Calculators & Tools"),
}

@Composable
fun TaxesScreen(vm: TaxViewModel = viewModel()) {
    var tab by remember { mutableStateOf(TaxesTab.File) }
    var showFiling by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val answers by vm.answers.collectAsState()
    val hasProgress = answers.isNotEmpty()

    if (showFiling) {
        BackHandler { showFiling = false }
        TaxFilingExperience(vm) { showFiling = false }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Estimate your federal taxes or explore tools to plan ahead.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
            SegmentedTabs(tab) { tab = it }
        }

        when (tab) {
            TaxesTab.File -> Launcher(
                hasProgress = hasProgress,
                onStart = { showFiling = true },
                onReset = { showResetConfirm = true },
            )

            TaxesTab.Calculators -> TaxCalculatorsView()
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Start over? This clears every answer.") },
            text = {
                Text(
                    "Every answer you entered for your 2025 estimate is erased, and the section " +
                        "checklist resets. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reset()
                    TaxFilingProgress.resetAll()
                    showResetConfirm = false
                }) {
                    Text("Erase and start over", color = Theme.colors.destructive)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
            containerColor = Theme.colors.card,
        )
    }
}

@Composable
private fun SegmentedTabs(selected: TaxesTab, onSelect: (TaxesTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.md))
            .background(Theme.colors.secondary)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TaxesTab.entries.forEach { entry ->
            val isOn = entry == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Theme.Radius.sm))
                    .background(if (isOn) Theme.colors.card else Color.Transparent)
                    .fcPressable { onSelect(entry) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.label,
                    style = Theme.sans(
                        Theme.FontSize.sm,
                        if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isOn) Theme.colors.foreground else Theme.colors.mutedForeground,
                )
            }
        }
    }
}

// MARK: - Launcher (hero)

@Composable
private fun Launcher(hasProgress: Boolean, onStart: () -> Unit, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            if (hasProgress) "Pick up right where you left off. Everything you entered is saved."
            else "Answer a few questions and watch your refund estimate build as you go.",
            style = Theme.sans(Theme.FontSize.base),
            color = Theme.colors.mutedForeground,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Theme.Radius.xl))
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, RoundedCornerShape(Theme.Radius.xl))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // One mark, centred: two stacked pages with a dollar badge on the
            // corner. Two separate coloured tiles read as two unrelated
            // buttons; this reads as one object.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TaxMark() }
            LauncherPoint(
                Icons.Filled.CheckCircle, Theme.colors.positive,
                "The real 1040 math, updated for tax year 2025",
            )
            LauncherPoint(
                Icons.Filled.Autorenew, Theme.colors.primary,
                "Your refund estimate updates as you answer",
            )
            LauncherPoint(
                Icons.Filled.Lock, Theme.colors.accentPurple,
                "Saves automatically. Sensitive info never leaves your device unencrypted",
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Theme.colors.primary)
                .fcPressable(onStart)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (hasProgress) "Continue My Estimate" else "Start My Estimate",
                style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                color = Theme.colors.primaryForeground,
            )
        }

        Text(
            "Filing from FinnaCalc is coming soon. For now this is an estimate you can check against " +
                "your filing service.",
            style = Theme.sans(Theme.FontSize.xs),
            color = Theme.colors.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (hasProgress) {
            Text(
                "Start over",
                style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                color = Theme.colors.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .fcPressable(onReset),
            )
        }
    }
}

/**
 * Two stacked pages, the back one peeking out behind, with a dollar badge on
 * the lower-left corner. All one hue so it reads as a single mark rather than
 * a row of buttons.
 */
@Composable
private fun TaxMark() {
    Box(
        modifier = Modifier.size(width = 58.dp, height = 54.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = Theme.colors.primary.copy(alpha = 0.28f),
            modifier = Modifier
                .size(34.dp)
                .offset(x = 9.dp, y = (-9).dp),
        )
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = Theme.colors.primary,
            modifier = Modifier.size(40.dp),
        )
        Box(
            modifier = Modifier
                .size(21.dp)
                .offset(x = (-3).dp, y = 4.dp)
                .clip(CircleShape)
                .background(Theme.colors.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MonetizationOn,
                contentDescription = null,
                tint = Theme.colors.positive,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Three points, three tints. All-green would read as three ticks of approval;
 * green for what is verified, blue for what updates live, violet for the
 * privacy one. Nothing amber or red, which would read as a warning.
 */
@Composable
private fun LauncherPoint(icon: ImageVector, tint: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.textBody)
    }
}
