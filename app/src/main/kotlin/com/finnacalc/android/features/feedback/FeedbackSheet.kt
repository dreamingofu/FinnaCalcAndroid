//
// FeedbackSheet.kt
//
// Port of iOS Features/Feedback/FeedbackView.swift — the in-app feedback form,
// opened automatically after ~5 minutes of use (SessionFeedback) and manually
// from Account → Send feedback. Submits to /api/feedback, which emails the app
// owner.
//

package com.finnacalc.android.features.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.core.auth.AuthManager
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.feedback.FeedbackRequest
import com.finnacalc.android.core.feedback.FeedbackService
import com.finnacalc.android.core.feedback.SessionFeedback
import kotlinx.coroutines.launch

enum class FeedbackSource(val raw: String) { Prompt("prompt"), Manual("manual") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(
    source: FeedbackSource,
    auth: AuthManager,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit = {},
) {
    var rating by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val user by auth.user.collectAsState()
    val scope = rememberCoroutineScope()
    val canSubmit = message.isNotBlank() && !sending

    LaunchedEffect(user) {
        if (email.isEmpty()) email = user?.email.orEmpty()
    }
    LaunchedEffect(Unit) {
        // Showing the auto-prompt starts the 30-day quiet period, even if the
        // user dismisses without sending.
        if (source == FeedbackSource.Prompt) SessionFeedback.stampCooldown()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.colors.background,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sent) "Thanks!" else "Send feedback",
                    style = Theme.sans(17, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (sent) "Done" else "Close",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = Theme.colors.primary,
                    modifier = Modifier.fcPressable(onDismiss),
                )
            }

            if (sent) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Theme.colors.positive.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Theme.colors.positive,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Text(
                        "Feedback sent",
                        style = Theme.sans(Theme.FontSize.lg, FontWeight.Bold),
                        color = Theme.colors.foreground,
                    )
                    Text(
                        "Thank you — it helps make FinnaCalc better.",
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
                return@ModalBottomSheet
            }

            Text(
                if (source == FeedbackSource.Prompt) {
                    "Enjoying FinnaCalc? Tell us what's working and what isn't — it goes straight " +
                        "to the team."
                } else {
                    "Found a bug, or have an idea? We read every message."
                },
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )

            // Stars — tapping the current rating clears it.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { i ->
                    Icon(
                        if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "$i star${if (i == 1) "" else "s"}",
                        tint = if (i <= rating) Theme.colors.caution else Theme.colors.borderStrong,
                        modifier = Modifier
                            .size(30.dp)
                            .fcPressable { rating = if (rating == i) 0 else i },
                    )
                }
            }

            FieldLabel("YOUR FEEDBACK")
            FeedbackField(
                value = message,
                placeholder = "What should we know?",
                onValueChange = { message = it },
                minHeight = 132.dp,
            )

            FieldLabel("EMAIL")
            FeedbackField(
                value = email,
                placeholder = "you@example.com",
                onValueChange = { email = it },
                minHeight = 44.dp,
                keyboardType = KeyboardType.Email,
            )

            error?.let {
                Text(it, style = Theme.sans(Theme.FontSize.sm), color = Theme.colors.negative)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (canSubmit) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(Theme.colors.primary)
                    .then(
                        if (canSubmit) Modifier.fcPressable {
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    FeedbackService.submit(
                                        FeedbackRequest(
                                            message = message.trim(),
                                            rating = rating.takeIf { it > 0 },
                                            email = email.trim().ifEmpty { null },
                                            userId = user?.id,
                                            appVersion = FeedbackService.appVersion,
                                            source = source.raw,
                                        )
                                    )
                                    // Any successful submission (prompt or
                                    // manual) quiets the auto-prompt for 30 days.
                                    SessionFeedback.stampCooldown()
                                    onSubmitted()
                                    sent = true
                                } catch (e: Exception) {
                                    error = e.message
                                        ?: "Couldn't send that. Check your connection and try again."
                                }
                                sending = false
                            }
                        } else Modifier
                    )
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                }
                Text(
                    if (sending) "Sending…" else "Send feedback",
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = Color.White,
                )
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
private fun FeedbackField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    minHeight: androidx.compose.ui.unit.Dp,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val shape = RoundedCornerShape(Theme.Radius.md)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight)
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Theme.sans(Theme.FontSize.sm).copy(color = Theme.colors.foreground),
            cursorBrush = SolidColor(Theme.colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                    )
                }
                inner()
            },
        )
    }
}
