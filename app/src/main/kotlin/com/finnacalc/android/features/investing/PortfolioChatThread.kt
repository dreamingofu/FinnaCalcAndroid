//
// PortfolioChatThread.kt
//
// Port of `PortfolioChatThread` in iOS Features/Investing/PortfolioAnalyticsViews.swift —
// the conversation about the mix, on the page rather than in the FinnaBot
// panel. It has no card chrome of its own: it sits at the bottom of the
// analysis, the way Budget Analysis keeps its thread inline under the report.
//
// The mix rides along invisibly with the first message via the view model's
// contextPrefix, so the model sees the holdings without the user pasting them
// — and the footnote says exactly what it can see.
//

package com.finnacalc.android.features.investing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.chat.ChatMessage
import com.finnacalc.android.features.chat.ChatRole
import com.finnacalc.android.features.chat.ChatViewModel
import com.finnacalc.android.features.chat.renderChatMarkdown

/**
 * Its own conversation, separate from the shell's FinnaBot thread — the mix
 * context belongs to this page, not to every question the user ever asks.
 */
@Composable
fun PortfolioChatThread(
    holdings: List<PortfolioAnalytics.Holding>,
    chat: ChatViewModel = viewModel(key = "portfolio-analysis-chat"),
) {
    // The model sees the mix without the user having to paste it: the holdings
    // ride along invisibly with the first message.
    LaunchedEffect(holdings.map { it.symbol to it.weight }) {
        val split = holdings.take(10)
            .joinToString(", ") { "${it.symbol} ${CalcFormat.fixed(it.weight * 100, 1)}%" }
        chat.contextPrefix = if (split.isEmpty()) null else
            "Context, not typed by the user: their portfolio is split $split. Use it when relevant."
    }

    val messages by chat.messages.collectAsState()
    val isLoading by chat.isLoading.collectAsState()
    val error by chat.error.collectAsState()
    val input by chat.input.collectAsState()

    // The canned welcome belongs to the standalone bot, not here.
    val conversation = messages.filterNot { it.id == ChatViewModel.WELCOME_MESSAGE_ID }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        conversation.forEach { Bubble(it) }

        if (conversation.isEmpty() && !isLoading) {
            Text(
                "Your mix rides along with whatever you ask, so questions like \"what stands out here\" " +
                    "already have the numbers.",
                style = Theme.sans(Theme.FontSize.xs).copy(lineHeight = 17.sp),
                color = Theme.colors.mutedForeground,
            )
        }

        if (isLoading && conversation.lastOrNull()?.role != ChatRole.Assistant) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Theme.colors.primary,
                )
                Text(
                    "Reading your mix…",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        error?.let {
            Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.negative)
        }

        FollowUpBar(
            input = input,
            sendable = !isLoading && input.trim().isNotEmpty(),
            onInputChange = chat::setInput,
            onSend = chat::send,
        )

        Text(
            "FinnaBot is an AI and can be wrong. It sees only the holdings and weights above, and " +
                "nothing it says is financial advice.",
            style = Theme.sans(11).copy(lineHeight = 15.sp),
            color = Theme.colors.mutedForeground,
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    if (message.role == ChatRole.User) {
        Text(
            message.content,
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.foreground,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.colors.muted.copy(alpha = 0.6f))
                .padding(11.dp),
        )
    } else {
        Text(
            renderChatMarkdown(message.content),
            style = Theme.sans(Theme.FontSize.sm).copy(lineHeight = 21.sp),
            color = Theme.colors.textBody,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FollowUpBar(
    input: String,
    sendable: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Theme.colors.card)
                .border(1.dp, Theme.colors.border, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = Theme.sans(Theme.FontSize.sm).copy(color = Theme.colors.foreground),
                cursorBrush = SolidColor(Theme.colors.primary),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            "Ask FinnaBot about this portfolio",
                            style = Theme.sans(Theme.FontSize.sm),
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
                .background(if (sendable) Theme.colors.primary else Theme.colors.borderStrong)
                .then(if (sendable) Modifier.fcPressable(onSend) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
