//
// AdvisorReportSection.kt
//
// The AI REPORT card from iOS BudgetAdvisorView: the streamed report, the
// "Deep analysis" escalation, the retry on failure, and the follow-up bar.
//
// Deviation from iOS: the follow-up bar is inline at the bottom of the card
// rather than pinned to the screen's safe area, so it stays attached to the
// report it belongs to inside the tab's own scroll.
//

package com.finnacalc.android.features.budgeting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.features.chat.renderChatMarkdown

@Composable
fun AdvisorReportSection(
    store: BudgetStore,
    vm: BudgetAdvisorViewModel = viewModel(key = "budget-advisor-report"),
) {
    // Recomputed whenever the budget changes; its signature drives the cache.
    val snapshot = remember(store.version.collectAsState().value) { buildAdvisorSnapshot(store) }

    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val depth by vm.depth.collectAsState()
    val input by vm.input.collectAsState()
    val visible = messages.filterNot { it.isGeneratedPrompt }

    // Nothing to report on an empty budget — and nothing to spend a model call
    // on either.
    val hasData = snapshot.monthlyIncome > 0 || snapshot.monthlyExpenses > 0
    LaunchedEffect(snapshot) { if (hasData) vm.startIfNeeded(snapshot) }
    if (!hasData) return

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AI REPORT",
                style = Theme.sans(11, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
                color = Theme.colors.mutedForeground,
            )
            if (depth == AdvisorDepth.Deep && !isLoading && visible.isNotEmpty()) {
                Text(
                    "· Deep",
                    style = Theme.sans(11, FontWeight.SemiBold),
                    color = Theme.colors.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            // Refresh ignores the cache on purpose.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (isLoading) Modifier
                        else Modifier.fcPressable { vm.runAnalysis(AdvisorDepth.Quick, snapshot) }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Run the report again",
                    tint = if (isLoading) Theme.colors.mutedForeground else Theme.colors.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            visible.forEach { MessageView(it) }

            // The deep analysis lives with the report it deepens.
            if (!isLoading && visible.isNotEmpty() && depth == AdvisorDepth.Quick) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Theme.colors.primary)
                        .fcPressable { vm.runAnalysis(AdvisorDepth.Deep, snapshot) }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    Text("Deep analysis", style = Theme.sans(11, FontWeight.Bold), color = Color.White)
                }
            }

            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Theme.colors.primary,
                    )
                    Text(
                        if (depth == AdvisorDepth.Deep) "Running a deep analysis…" else "Analyzing your budget…",
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }

            error?.let { message ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Theme.colors.destructive,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        message,
                        style = Theme.sans(Theme.FontSize.xs),
                        color = Theme.colors.destructive,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Retry",
                        style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold),
                        color = Theme.colors.destructive,
                        modifier = Modifier.fcPressable { vm.retry(snapshot) },
                    )
                }
            }

            FollowUpBar(
                input = input,
                isLoading = isLoading,
                onInputChange = vm::setInput,
                onSend = { vm.send(snapshot) },
                onStop = vm::stop,
            )
        }
    }
}

@Composable
private fun MessageView(message: AdvisorMessage) {
    if (message.role == AdvisorRole.Assistant) {
        Text(
            renderChatMarkdown(message.content),
            style = Theme.sans(Theme.FontSize.sm).copy(lineHeight = 21.sp),
            color = Theme.colors.textBody,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.width(40.dp))
            Text(
                message.content,
                style = Theme.sans(Theme.FontSize.sm),
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp))
                    .background(Theme.colors.brandBlue)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.FollowUpBar(
    input: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val sendable = !isLoading && input.trim().isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Theme.colors.background)
            .border(1.dp, Theme.colors.border, CircleShape)
            .padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(start = 9.dp)) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !isLoading,
                textStyle = Theme.sans(13).copy(color = Theme.colors.foreground),
                cursorBrush = SolidColor(Theme.colors.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            "Ask about this report…",
                            style = Theme.sans(13),
                            color = Theme.colors.mutedForeground,
                        )
                    }
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isLoading || sendable) Theme.colors.primary else Theme.colors.borderStrong)
                .then(
                    when {
                        isLoading -> Modifier.fcPressable(onStop)
                        sendable -> Modifier.fcPressable(onSend)
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isLoading) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
                contentDescription = if (isLoading) "Stop" else "Send",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
