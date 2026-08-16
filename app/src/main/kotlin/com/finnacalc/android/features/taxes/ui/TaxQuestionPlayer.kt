//
// TaxQuestionPlayer.kt
//
// Port of iOS Features/Taxes/Filing/TaxQuestionPlayer.swift — the section
// player: ONE question per screen. Big friendly title, "Why we ask" expander,
// an input sized for the question type, and a pinned Back / Continue footer.
// Yes/No and select answers auto-advance, the interaction this flow is built
// around.
//
// The visible question list is recomputed from the live answers after every
// change (a gate answer can reveal follow-ups mid-section), with the index
// clamped so the player never walks off the end.
//

package com.finnacalc.android.features.taxes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.FCButtonVariant
import com.finnacalc.android.core.designsystem.FCTextField
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.features.taxes.TaxViewModel
import com.finnacalc.android.features.taxes.engine.AnswerValue
import com.finnacalc.android.features.taxes.engine.InputType
import com.finnacalc.android.features.taxes.engine.Question
import com.finnacalc.android.features.taxes.engine.QuestionOption
import com.finnacalc.android.features.taxes.engine.Section
import com.finnacalc.android.features.taxes.engine.questionsFor
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TaxQuestionPlayer(
    vm: TaxViewModel,
    section: Section,
    onExit: () -> Unit,
    onComplete: () -> Unit,
) {
    val answers by vm.answers.collectAsState()
    val questions = questionsFor(section.id, answers)

    // Fresh player state per section, as `.id(id)` gives the iOS view.
    var index by remember(section.id) { mutableIntStateOf(0) }
    val clamped = index.coerceIn(0, maxOf(questions.size - 1, 0))
    val question = questions.getOrNull(clamped)
    val isLast = clamped >= questions.size - 1
    val scope = rememberCoroutineScope()

    fun advance() {
        if (clamped + 1 < questions.size) index = clamped + 1 else onComplete()
    }

    fun back() {
        if (clamped > 0) index = clamped - 1 else onExit()
    }

    Column(Modifier.fillMaxSize()) {
        // Progress header (section name + thin bar)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    section.title,
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.Bold),
                    color = Theme.colors.foreground,
                    modifier = Modifier.weight(1f),
                )
                if (questions.isNotEmpty()) {
                    Text(
                        "${clamped + 1} of ${questions.size}",
                        style = Theme.figure(Theme.FontSize.xs, FontWeight.Normal),
                        color = Theme.colors.mutedForeground,
                    )
                }
            }
            LinearProgressIndicator(
                progress = {
                    val total = maxOf(questions.size, 1)
                    minOf(clamped + 1, total).toFloat() / total
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Theme.colors.primary,
                trackColor = Theme.colors.secondary,
                drawStopIndicator = {},
            )
        }

        if (question != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                QuestionScreen(
                    question = question,
                    value = answers[question.id],
                    onChange = { vm.set(question.id, it) },
                    onAutoAdvance = { scope.launch { delay(250); advance() } },
                )
            }
        } else {
            // Section has no visible questions (all gated off) — nothing to ask.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Theme.colors.positive,
                    modifier = Modifier.size(38.dp),
                )
                Text(
                    "Nothing needed here based on your answers.",
                    style = Theme.sans(Theme.FontSize.base),
                    color = Theme.colors.mutedForeground,
                )
            }
        }

        FilingFooter {
            FCButton("Back", variant = FCButtonVariant.Outline, size = FCButtonSize.Lg) { back() }
            FCButton(
                if (isLast) "Done" else "Continue",
                size = FCButtonSize.Lg,
                modifier = Modifier.weight(1f),
            ) { advance() }
        }
    }
}

// MARK: - One question screen

@Composable
private fun QuestionScreen(
    question: Question,
    value: AnswerValue?,
    onChange: (AnswerValue) -> Unit,
    onAutoAdvance: () -> Unit,
) {
    var showHelp by remember(question.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Big friendly question title.
        Text(
            question.text,
            style = Theme.sans(26, FontWeight.Bold),
            color = Theme.colors.foreground,
        )

        // "Why we ask" expander.
        val help = question.helpText
        if (!help.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fcPressable { showHelp = !showHelp },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = Theme.colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Why we ask",
                        style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                        color = Theme.colors.primary,
                    )
                    Icon(
                        if (showHelp) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Theme.colors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                AnimatedVisibility(showHelp) {
                    Text(
                        help,
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Theme.Radius.md))
                            .background(Theme.colors.muted.copy(alpha = 0.5f))
                            .padding(12.dp),
                    )
                }
            }
        }

        Box(Modifier.padding(top = 4.dp)) {
            when (question.inputType) {
                InputType.Boolean -> YesNoInput((value as? AnswerValue.Bool)?.value) { picked ->
                    onChange(AnswerValue.Bool(picked))
                    onAutoAdvance()
                }

                InputType.Select -> SelectInput(
                    options = question.options ?: emptyList(),
                    selected = (value as? AnswerValue.Str)?.value,
                ) { picked ->
                    onChange(AnswerValue.Str(picked))
                    onAutoAdvance()
                }

                InputType.Dollar -> AmountInput(
                    key = question.id,
                    initial = (value as? AnswerValue.Num)?.value,
                    allowNegative = question.allowNegative,
                    prefix = "$",
                    placeholder = question.placeholder ?: "0",
                    integerOnly = false,
                ) { onChange(AnswerValue.Num(it)) }

                InputType.Integer -> AmountInput(
                    key = question.id,
                    initial = (value as? AnswerValue.Num)?.value,
                    allowNegative = false,
                    prefix = null,
                    placeholder = question.placeholder ?: "0",
                    integerOnly = true,
                ) { onChange(AnswerValue.Num(it)) }

                InputType.Text -> TextAnswerInput(
                    key = question.id,
                    initial = (value as? AnswerValue.Str)?.value ?: "",
                    placeholder = question.placeholder ?: "",
                ) { onChange(AnswerValue.Str(it)) }
            }
        }
    }
}

// MARK: - Yes / No cards

@Composable
private fun YesNoInput(value: Boolean?, onPick: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        YesNoCard("Yes", value == true, Modifier.weight(1f)) { onPick(true) }
        YesNoCard("No", value == false, Modifier.weight(1f)) { onPick(false) }
    }
}

@Composable
private fun YesNoCard(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Theme.colors.primary.copy(alpha = 0.10f) else Theme.colors.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Theme.colors.primary else Theme.colors.border, shape)
            .fcPressable(onClick)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = Theme.sans(Theme.FontSize.lg, FontWeight.Bold),
            color = if (selected) Theme.colors.primary else Theme.colors.foreground,
        )
    }
}

// MARK: - Select option cards

@Composable
private fun SelectInput(options: List<QuestionOption>, selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val shape = RoundedCornerShape(Theme.Radius.lg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(if (isSelected) Theme.colors.primary.copy(alpha = 0.08f) else Theme.colors.card)
                    .border(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) Theme.colors.primary else Theme.colors.border,
                        shape,
                    )
                    .fcPressable { onPick(option.value) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    option.label,
                    style = Theme.sans(
                        Theme.FontSize.base,
                        if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = Theme.colors.foreground,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) Theme.colors.primary else Theme.colors.borderStrong,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// MARK: - Dollar / integer input (big amount field)

@Composable
private fun AmountInput(
    key: String,
    initial: Double?,
    allowNegative: Boolean,
    prefix: String?,
    placeholder: String,
    integerOnly: Boolean,
    onChange: (Double) -> Unit,
) {
    var text by remember(key) {
        mutableStateOf(
            when {
                initial == null || initial == 0.0 -> ""
                integerOnly || initial == floor(initial) -> initial.toLong().toString()
                else -> initial.toString()
            }
        )
    }
    val shape = RoundedCornerShape(Theme.Radius.lg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(
                prefix,
                style = Theme.figure(30, FontWeight.Bold),
                color = Theme.colors.mutedForeground,
            )
        }
        BigAmountField(
            value = text,
            placeholder = placeholder,
            allowNegative = allowNegative,
            modifier = Modifier.weight(1f),
        ) { newValue ->
            text = newValue
            val cleaned = newValue.replace(",", "")
            val v = cleaned.toDoubleOrNull()
            when {
                v != null -> onChange(if (integerOnly) floor(v) else v)
                cleaned.isEmpty() -> onChange(0.0)
            }
        }
    }
}

/** The oversized figure field the amount questions type into. */
@Composable
private fun BigAmountField(
    value: String,
    placeholder: String,
    allowNegative: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = Theme.figure(30, FontWeight.Bold).copy(color = Theme.colors.foreground),
        singleLine = true,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Theme.colors.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowNegative) KeyboardType.Number else KeyboardType.Decimal,
        ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = Theme.figure(30, FontWeight.Bold),
                        color = Theme.colors.mutedForeground.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
}

// MARK: - Free text input

@Composable
private fun TextAnswerInput(
    key: String,
    initial: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    var text by remember(key) { mutableStateOf(initial) }
    LaunchedEffect(key) { text = initial }
    FCTextField(
        placeholder = placeholder,
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        showsPlaceholder = true,
    )
}
