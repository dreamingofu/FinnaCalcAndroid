//
// CalcSupport.kt
//
// Port of iOS Features/Calculators/CalcSupport.swift — shared toolkit for the
// standalone calculators. Every calculator screen is built from these pieces
// so they read consistently and match the web calculators' look: labeled
// inputs, a full-width Calculate button, and a grid of bold, color-coded
// results (green = headline, red = cost).
//

package com.finnacalc.android.features.calculators

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcShadow
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

// MARK: - Parsing & formatting (mirrors the web helpers)

/**
 * Mirrors the web's `num()` helper — strips everything except digits, a
 * decimal point, and a minus sign (so comma-grouped currency text like
 * "3,200" parses correctly), then parses; empty/invalid input becomes 0.
 */
val String.calcValue: Double
    get() {
        val cleaned = filter { it.isDigit() || it == '.' || it == '-' }
        return cleaned.toDoubleOrNull() ?: 0.0
    }

object CalcFormat {
    private fun formatter(pattern: String): DecimalFormat =
        DecimalFormat(pattern).apply { roundingMode = RoundingMode.HALF_UP }

    /** Grouped with a fixed number of fraction digits — the web `fmt()` helper. */
    fun decimal(value: Double, fraction: Int = 2): String {
        val v = if (value.isFinite()) value else 0.0
        val pattern = if (fraction > 0) "#,##0." + "0".repeat(fraction) else "#,##0"
        return formatter(pattern).format(v)
    }

    /** Grouped integer — the web `value.toLocaleString()` on whole numbers. */
    fun int(value: Double): String = decimal(value, 0)

    /** Fixed fraction digits, no grouping — the web `value.toFixed(n)`. */
    fun fixed(value: Double, n: Int): String {
        val v = if (value.isFinite()) value else 0.0
        return BigDecimal(v).setScale(n, RoundingMode.HALF_UP).toPlainString()
    }

    fun currency(value: Double, fraction: Int = 2): String = "$" + decimal(value, fraction)
}

/**
 * Mirrors `calc-data.js`'s `currency()`/`pctStr()` helpers exactly — grouped
 * currency and fixed-point percent strings, each guarded against non-finite
 * results (shows an em dash instead of NaN/Infinity, matching the JS spec).
 */
object CalcFmt {
    fun currency(value: Double, fractionDigits: Int = 0): String {
        if (!value.isFinite()) return "—"
        return "$" + CalcFormat.decimal(value, fractionDigits)
    }

    fun percent(value: Double, fractionDigits: Int = 1): String {
        if (!value.isFinite()) return "—"
        return CalcFormat.fixed(value, fractionDigits) + "%"
    }

    fun int(value: Double): String {
        if (!value.isFinite()) return "—"
        return CalcFormat.int(value)
    }
}

// MARK: - Result accents (Direction-1D redesign palette)

/**
 * Result accent color, ported from `calc-data.js`'s `accent` field: green =
 * primary result, blue = total/supporting, red = interest/cost, purple =
 * principal, orange = secondary metric.
 */
enum class CalcAccent {
    Green, Blue, Red, Purple, Orange;

    val color: Color
        @Composable get() = when (this) {
            Green -> Theme.colors.positive
            Blue -> Theme.colors.primary
            Red -> Theme.colors.negative
            Purple -> Theme.colors.accentPurple
            Orange -> Theme.colors.accentOrange
        }
}

/** One labeled, color-coded figure in the results panel. */
data class CalcResultMetric(
    val label: String,
    val value: String,
    val accent: CalcAccent,
)

// MARK: - Header

/** Icon chip + title + description — the calculator screen's hero row. */
@Composable
fun CalcHeader(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(Theme.Radius.sm))
                .background(Theme.colors.brandTint)
                .padding(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(24.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = Theme.sans(Theme.FontSize.xl, FontWeight.SemiBold),
                color = Theme.colors.foreground,
            )
            Text(
                description,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }
    }
}

// MARK: - Section card

/** One field-group card (2–3 per calculator). */
@Composable
fun CalcSectionCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fcShadow(Theme.Elevation.Sm, shape)
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
            color = Theme.colors.foreground,
        )
        content()
    }
}

/**
 * Two-column layout for grouped inputs (web `grid grid-cols-2 gap-4`). Cells
 * are passed as nullable slots so callers can drop mode-dependent fields; a
 * lone trailing cell sits half-width, left — matching CSS grid auto-flow.
 */
@Composable
fun CalcGrid(vararg cells: (@Composable () -> Unit)?) {
    val visible = cells.filterNotNull()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        visible.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { cell -> Box(Modifier.weight(1f)) { cell() } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Field label + hint disclosure

/**
 * Wraps any field control with its label row and an optional tappable ⓘ that
 * reveals a methodology hint inline (any number can be open at once). The
 * label reserves two lines so wrapping labels don't push their field lower
 * than a single-line label beside them in the grid.
 */
@Composable
fun CalcFieldContainer(
    label: String,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    var hintShown by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                label,
                style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
                color = Theme.colors.foreground,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hint != null) {
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(if (hintShown) Theme.colors.primary else Theme.colors.muted)
                        .clickable { hintShown = !hintShown },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "i",
                        style = Theme.sans(10, FontWeight.Bold),
                        color = if (hintShown) Color.White else Theme.colors.mutedForeground,
                    )
                }
            }
        }
        control()
        AnimatedVisibility(
            visible = hint != null && hintShown,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Theme.colors.border)
                )
                Text(
                    hint ?: "",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
        }
    }
}

// MARK: - Field controls

/** Shared 44dp bordered box chrome — background, border, focus ring, radius. */
@Composable
private fun Modifier.calcInputChrome(focused: Boolean = false): Modifier {
    val shape = RoundedCornerShape(Theme.Radius.md)
    return this
        .fillMaxWidth()
        .height(44.dp)
        .background(Theme.colors.background, shape)
        .border(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) Theme.colors.primary else Theme.colors.input,
            shape = shape,
        )
}

/**
 * Mirrors `calc-ui.jsx`'s `formatCurrencyTyping`: live thousands separators,
 * one decimal point, max 2 fraction digits, leading zeros stripped.
 */
fun formatCurrencyTyping(raw: String): String {
    var digitsAndDot = raw.filter { it.isDigit() || it == '.' }
    if (digitsAndDot.isEmpty()) return ""
    val firstDot = digitsAndDot.indexOf('.')
    if (firstDot >= 0) {
        val intPart = digitsAndDot.substring(0, firstDot)
        val afterDot = digitsAndDot.substring(firstDot + 1).filter { it != '.' }
        digitsAndDot = "$intPart.$afterDot"
    }
    val parts = digitsAndDot.split(".", limit = 2)
    var intRaw = parts[0]
    while (intRaw.length > 1 && intRaw.first() == '0') intRaw = intRaw.drop(1)
    if (intRaw.isEmpty()) intRaw = if (digitsAndDot.startsWith(".")) "0" else ""
    val grouped = intRaw.reversed().chunked(3).joinToString(",").reversed()
    if (parts.size == 1) return grouped
    return grouped + "." + parts[1].take(2)
}

/** `$` prefix + live thousands-separator formatting as the user types. */
@Composable
private fun CalcCurrencyInput(text: String, onTextChange: (String) -> Unit, placeholder: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier.calcInputChrome(focused),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
            color = Theme.colors.mutedForeground,
            modifier = Modifier.padding(start = 14.dp),
        )
        Box(Modifier.weight(1f).padding(start = 8.dp, end = 14.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { onTextChange(formatCurrencyTyping(it)) },
                singleLine = true,
                interactionSource = interaction,
                textStyle = Theme.figure(Theme.FontSize.base).copy(color = Theme.colors.foreground),
                cursorBrush = SolidColor(Theme.colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(
                    placeholder,
                    style = Theme.figure(Theme.FontSize.base),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
    }
}

/** Value input first, `%` suffix trailing — digits + one decimal point only. */
@Composable
private fun CalcPercentInput(text: String, onTextChange: (String) -> Unit, placeholder: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier.calcInputChrome(focused),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { newValue ->
                    var seenDot = false
                    onTextChange(newValue.filter { ch ->
                        when {
                            ch.isDigit() -> true
                            ch == '.' && !seenDot -> { seenDot = true; true }
                            else -> false
                        }
                    })
                },
                singleLine = true,
                interactionSource = interaction,
                textStyle = Theme.figure(Theme.FontSize.base).copy(color = Theme.colors.foreground),
                cursorBrush = SolidColor(Theme.colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(
                    placeholder,
                    style = Theme.figure(Theme.FontSize.base),
                    color = Theme.colors.mutedForeground,
                )
            }
        }
        Text(
            "%",
            style = Theme.sans(Theme.FontSize.sm, FontWeight.Medium),
            color = Theme.colors.mutedForeground,
            modifier = Modifier.padding(end = 14.dp),
        )
    }
}

/**
 * Derives a one-word placeholder from a field label: the last word that
 * contains a letter (so trailing symbols like "%" or "($)" are skipped).
 */
object CalcPlaceholder {
    fun lastWord(label: String): String {
        val words = label.split(' ', '/')
        val word = words.lastOrNull { w -> w.any { it.isLetter() } } ?: return label
        return word.trim('(', ')', '%', '$', '#')
    }
}

/** Labeled currency field: `$` prefix + comma-formatted live input. */
@Composable
fun CalcCurrencyField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    hint: String? = null,
    placeholder: String? = null,
) {
    CalcFieldContainer(label, hint) {
        CalcCurrencyInput(text, onTextChange, placeholder ?: CalcPlaceholder.lastWord(label))
    }
}

/** Labeled percent field: value + `%` suffix. */
@Composable
fun CalcPercentField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    hint: String? = null,
    placeholder: String? = null,
) {
    CalcFieldContainer(label, hint) {
        CalcPercentInput(text, onTextChange, placeholder ?: CalcPlaceholder.lastWord(label))
    }
}

/** Labeled whole-number stepper (months/term/years/hours): `−` / value+unit / `+`. */
@Composable
fun CalcStepperField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    min: Int = 0,
    step: Int = 1,
    unit: String = "",
    hint: String? = null,
) {
    val value = text.toIntOrNull() ?: 0
    CalcFieldContainer(label, hint) {
        Row(
            modifier = Modifier.calcInputChrome().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Icons.Default.Remove) { onTextChange(maxOf(min, value - step).toString()) }
            Spacer(Modifier.weight(1f))
            // Typing beats stepping for a big jump: 360 months is 300 taps
            // away from the default. The arrows stay for a nudge either way.
            BasicTextField(
                value = text,
                onValueChange = { newValue ->
                    onTextChange(newValue.filter { it.isDigit() })
                },
                singleLine = true,
                textStyle = Theme.figure(Theme.FontSize.base, FontWeight.SemiBold).copy(
                    color = Theme.colors.foreground,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(Theme.colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(64.dp),
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = Theme.sans(Theme.FontSize.xs, FontWeight.Medium),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            StepButton(Icons.Default.Add) { onTextChange((value + step).toString()) }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(Theme.Radius.sm))
            .background(Theme.colors.muted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Theme.colors.foreground, modifier = Modifier.size(14.dp))
    }
}

/** Labeled dropdown (web `<Select>`). `options` are (value, label) pairs. */
@Composable
fun <T> CalcSelectField(
    label: String,
    selection: T,
    onSelectionChange: (T) -> Unit,
    options: List<Pair<T, String>>,
    hint: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    CalcFieldContainer(label, hint) {
        Box {
            Row(
                modifier = Modifier
                    .calcInputChrome()
                    .clip(RoundedCornerShape(Theme.Radius.md))
                    .clickable { open = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.firstOrNull { it.first == selection }?.second ?: "",
                    style = Theme.sans(Theme.FontSize.base),
                    color = Theme.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Theme.colors.mutedForeground,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel, style = Theme.sans(Theme.FontSize.sm)) },
                        onClick = {
                            onSelectionChange(value)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/** Bare segmented control (equal-width pill buttons in a track). */
@Composable
fun <T> CalcSegmentedControl(
    selection: T,
    onSelectionChange: (T) -> Unit,
    options: List<Pair<T, String>>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.md))
            .background(Theme.colors.muted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isOn = value == selection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(Theme.Radius.sm))
                    .background(if (isOn) Theme.colors.card else Color.Transparent)
                    .clickable { onSelectionChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Theme.sans(Theme.FontSize.sm, if (isOn) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isOn) Theme.colors.foreground else Theme.colors.mutedForeground,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Labeled segmented field (2–3 options) — always its own full-width row. */
@Composable
fun <T> CalcSegmentedField(
    label: String,
    selection: T,
    onSelectionChange: (T) -> Unit,
    options: List<Pair<T, String>>,
) {
    CalcFieldContainer(label) { CalcSegmentedControl(selection, onSelectionChange, options) }
}

/** The standalone mode-tabs card (Loan calculator only). */
@Composable
fun <T> CalcModeTabsCard(
    selection: T,
    onSelectionChange: (T) -> Unit,
    options: List<Pair<T, String>>,
) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Box(
        Modifier
            .fillMaxWidth()
            .fcShadow(Theme.Elevation.Sm, shape)
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        CalcSegmentedControl(selection, onSelectionChange, options)
    }
}

// MARK: - Results panel

/**
 * "Your {verb} Calculation" heading + a two-column grid of color-coded
 * metrics — revealed once Calculate is first tapped, then stays live.
 */
@Composable
fun CalcResultsPanel(verb: String, results: List<CalcResultMetric>) {
    val stripe = Theme.colors.primary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.Radius.md))
            .background(Theme.colors.primarySoftBG)
            .drawBehind {
                drawRect(stripe, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
            },
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Your $verb Calculation",
                style = Theme.sans(Theme.FontSize.lg, FontWeight.SemiBold),
                color = Theme.colors.primary,
            )
            CalcGrid(
                *results.map<CalcResultMetric, (@Composable () -> Unit)?> { metric ->
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                metric.label,
                                style = Theme.sans(Theme.FontSize.sm),
                                color = Theme.colors.mutedForeground,
                            )
                            Text(
                                metric.value,
                                style = Theme.figure(Theme.FontSize.xl2, FontWeight.Bold),
                                color = metric.accent.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }.toTypedArray()
            )
        }
    }
}

/**
 * Shown instead of the results panel when the current inputs can't be
 * computed — never render NaN/blank metrics.
 */
@Composable
fun CalcResultsError(message: String = "Enter valid values to see results.") {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    val stripe = Theme.colors.negative
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .drawBehind {
                drawRect(stripe, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
            },
    ) {
        Text(
            message,
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.negative,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// MARK: - Page scaffold

/**
 * Standard calculator screen (Direction 1D — stacked section cards): header,
 * mode tabs (if any, via `sections`), N section cards, the results
 * panel/error, and a sticky Calculate footer. The hub's back navigation
 * replaces the web's manual back button.
 */
@Composable
fun CalculatorScreen(
    icon: ImageVector,
    title: String,
    description: String,
    verb: String,
    revealed: Boolean,
    results: List<CalcResultMetric>?,
    invalidMessage: String = "Enter valid values to see results.",
    onCalculate: () -> Unit,
    sections: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Theme.colors.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CalcHeader(icon, title, description)
            sections()
            if (revealed) {
                if (results != null) {
                    CalcResultsPanel(verb, results)
                } else {
                    CalcResultsError(invalidMessage)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Sticky Calculate footer.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Theme.colors.background),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Theme.colors.border)
            )
            FCButton(
                onClick = onCalculate,
                size = FCButtonSize.Lg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 14.dp),
            ) {
                Text("Calculate $verb")
            }
        }
    }
}
