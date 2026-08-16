//
// FinnaBotSheet.kt
//
// Port of the view half of iOS Features/Chat/FinnaBotView.swift — the FinnaBot
// panel. iOS presents it as a sheet from the shell; Android uses a full-height
// ModalBottomSheet, which is the platform's equivalent presentation.
//
// The bubbles keep the iMessage split the iOS view uses: the assistant's
// replies are blue on the leading edge, the user's are neutral grey on the
// trailing edge, so the two sides read as clearly different voices. The
// assistant's keeps markdown rendering.
//

package com.finnacalc.android.features.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinnaBotSheet(chat: ChatViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Header(onClose = onDismiss)
            HorizontalDivider(color = Theme.colors.border)
            MessageList(chat, Modifier.weight(1f))
            HorizontalDivider(color = Theme.colors.border)
            InputBar(chat)
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The wordmark stands in for the iOS FinnaBotLogo asset until the real
        // art lands with the app icon in Phase 8.
        Text(
            buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = Theme.colors.foreground)) { append("Finna") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = Theme.colors.primary)) { append("Bot") }
            },
            style = Theme.sans(16, FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Text(
            "Close",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
            color = Theme.colors.primary,
            modifier = Modifier
                .fcPressable(onClose)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MessageList(chat: ChatViewModel, modifier: Modifier = Modifier) {
    val messages by chat.messages.collectAsState()
    val isLoading by chat.isLoading.collectAsState()
    val error by chat.error.collectAsState()
    val listState = rememberLazyListState()

    // Follow the stream: every appended chunk and the typing row scroll to end.
    LaunchedEffect(messages, isLoading) {
        val count = messages.size + (if (isLoading) 1 else 0) + (if (error != null) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages.size, key = { messages[it].id }) { index ->
            Bubble(messages[index])
        }
        if (isLoading) {
            item("typing") {
                Row(Modifier.fillMaxWidth()) { TypingDots() }
            }
        }
        val err = error
        if (err != null) {
            item("error") {
                Text(
                    err,
                    style = Theme.sans(Theme.FontSize.xs),
                    color = Theme.colors.destructive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Theme.Radius.sm))
                        .background(Theme.colors.destructive.copy(alpha = 0.1f))
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    if (message.role == ChatRole.User) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.width(40.dp))
            Text(
                message.content,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.foreground,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Theme.colors.secondary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    renderChatMarkdown(message.content),
                    style = Theme.sans(Theme.FontSize.sm).copy(lineHeight = 21.sp),
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Theme.colors.brandBlue)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(24.dp))
            }
            // Only on answers to "what should I do with my money" questions, so
            // it stays meaningful instead of becoming wallpaper.
            if (message.needsAdviceDisclaimer && message.content.isNotEmpty()) {
                Text(
                    "FinnaBot is AI — this isn't financial advice.",
                    style = Theme.sans(10),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InputBar(chat: ChatViewModel) {
    val input by chat.input.collectAsState()
    val isLoading by chat.isLoading.collectAsState()
    val sendable = !isLoading && input.trim().isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.colors.background)
            .imePadding()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(Theme.colors.background)
                .border(1.dp, Theme.colors.input, CircleShape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = chat::setInput,
                enabled = !isLoading,
                textStyle = Theme.sans(Theme.FontSize.sm).copy(color = Theme.colors.foreground),
                cursorBrush = SolidColor(Theme.colors.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { chat.send() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            "Message FinnaBot",
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
                .size(36.dp)
                .alpha(if (sendable) 1f else 0.5f)
                .clip(CircleShape)
                .background(Theme.colors.brandBlue)
                .then(if (sendable) Modifier.fcPressable { chat.send() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.muted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(750, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(if (phase.toInt() == i) 1f else 0.35f)
                    .clip(CircleShape)
                    .background(Theme.colors.mutedForeground)
            )
        }
    }
}
