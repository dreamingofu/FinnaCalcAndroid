//
// BudgetAdvisorScreen.kt
//
// Port of iOS Features/Budgeting/BudgetAdvisorView.swift's local half — the
// deterministic findings from BudgetFindings, a weighted score, and a
// per-finding card with its status pill, figures, and the plain-language
// "what this means" line. Every figure comes from the budget My Budget has
// open; nothing here is invented and nothing is rounded into a claim the
// numbers don't support.
//
// Deviation from iOS: the AI report (the streamed advisor call and its
// snapshot cache) rides on the chat infrastructure and lands in Phase 7. The
// screen shows the local findings now and says where the written analysis
// will appear, rather than implying an analysis that isn't there.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.ambientGlow
import com.finnacalc.android.core.designsystem.fcPressable

@Composable
fun BudgetAdvisorScreen(
    store: BudgetStore,
    onOpenBudget: () -> Unit,
    onOpenGoals: () -> Unit,
) {
    store.version.collectAsState().value
    BankLedgerStore.shared.version.collectAsState().value

    val findings = BudgetFindings.compute(store)
    val score = BudgetFindings.score(findings)
    val summary = BudgetFindings.summaryLine(findings)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Budget Analysis", style = Theme.sans(17, FontWeight.SemiBold), color = Theme.colors.foreground)

        if (findings.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Nothing to analyse yet", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
                Text(
                    "Add income and expenses in My Budget, or connect a bank, and this reads what's actually there.",
                    style = Theme.sans(13),
                    color = Theme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Open My Budget",
                    style = Theme.sans(13, FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .background(Theme.colors.primary)
                        .fcPressable(onOpenBudget)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
            return@Column
        }

        ScoreCard(score, summary, store.currentSlotLabel)

        findings.forEach { finding ->
            FindingCard(finding) {
                when (finding.action) {
                    FindingAction.EditBudget -> onOpenBudget()
                    FindingAction.OpenGoals -> onOpenGoals()
                    null -> {}
                }
            }
        }

        // The written analysis rides on the chat infrastructure (Phase 7).
        Column(
            Modifier
                .fillMaxWidth()
                .ambientGlow(18.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Written analysis", style = Theme.sans(14, FontWeight.Bold), color = Theme.colors.foreground)
            Text(
                "FinnaBot's written read of this budget arrives with the chat feature. The findings above are computed on this device from your own figures and don't need it.",
                style = Theme.sans(12),
                color = Theme.colors.mutedForeground,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ScoreCard(score: Int, summary: String?, slotLabel: String) {
    val tone = when {
        score >= 75 -> Theme.colors.positive
        score >= 45 -> Theme.colors.caution
        else -> Theme.colors.negative
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$score", style = Theme.figure(24, FontWeight.Bold), color = tone)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Budget score", style = Theme.sans(15, FontWeight.Bold), color = Theme.colors.foreground)
            summary?.let {
                Text(it, style = Theme.sans(12), color = Theme.colors.mutedForeground)
            }
            Text(
                "Weighted over surplus, savings rate, emergency cover and goal pace, for the budget you have open ($slotLabel).",
                style = Theme.sans(11),
                color = Theme.colors.mutedForeground,
            )
        }
    }
}

@Composable
private fun FindingCard(finding: Finding, onAction: () -> Unit) {
    val tone = when (finding.status) {
        is FindingStatus.Good -> Theme.colors.positive
        is FindingStatus.Warn -> Theme.colors.caution
        is FindingStatus.Bad -> Theme.colors.negative
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                finding.title,
                style = Theme.sans(14, FontWeight.Bold),
                color = Theme.colors.foreground,
                modifier = Modifier.weight(1f),
            )
            Text(
                finding.status.label,
                style = Theme.sans(10, FontWeight.Bold),
                color = tone,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(finding.detail, style = Theme.figure(13, FontWeight.SemiBold), color = tone)
        Text(finding.fix, style = Theme.sans(12).copy(lineHeight = 17.sp), color = Theme.colors.mutedForeground)
        finding.action?.let { action ->
            Text(
                action.label,
                style = Theme.sans(12, FontWeight.Bold),
                color = Theme.colors.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Theme.colors.brandTint)
                    .fcPressable(onAction)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
