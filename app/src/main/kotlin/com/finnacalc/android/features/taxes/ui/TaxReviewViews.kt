//
// TaxReviewViews.kt
//
// Port of iOS Features/Taxes/UI/TaxReviewViews.swift:
//   ReviewScreen   — the line-by-line 1040, deduction check, section jump list,
//                    audit/warning panel.
//   FilingScreen   — headline figure, share of the plain-text summary, and the
//                    printable summary card.
//   AuditRiskPanel — audit flags + not-fully-modeled warnings.
//
// Fidelity note carried over from iOS: FilingScreen does NOT submit anything.
// FinnaCalc can't transmit a return yet, so the E-file button is present but
// disabled and the screen says filing is coming soon. Nothing is sent.
//
// The iOS share sheet (UIActivityViewController) becomes an ACTION_SEND chooser
// over the same plain-text rendering.
//

package com.finnacalc.android.features.taxes.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.FCButtonVariant
import com.finnacalc.android.core.designsystem.FCCard
import com.finnacalc.android.core.designsystem.FCCardContent
import com.finnacalc.android.core.designsystem.FCCardDescription
import com.finnacalc.android.core.designsystem.FCCardHeader
import com.finnacalc.android.core.designsystem.FCCardTitle
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.taxes.TaxViewModel
import com.finnacalc.android.features.taxes.engine.AuditSeverity
import com.finnacalc.android.features.taxes.engine.DeductionUsed
import com.finnacalc.android.features.taxes.engine.Form1040Summary
import com.finnacalc.android.features.taxes.engine.TaxCalculationResult
import com.finnacalc.android.features.taxes.engine.build1040Summary
import com.finnacalc.android.features.taxes.engine.visibleSections
import kotlin.math.abs

// MARK: - ReviewScreen

@Composable
fun ReviewScreen(
    vm: TaxViewModel,
    onEdit: ((String) -> Unit)? = null,
    onFile: (() -> Unit)? = null,
) {
    val result by vm.result.collectAsState()
    val answers by vm.answers.collectAsState()
    val sections = visibleSections(answers)

    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header (big title + hero refund figure)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Review your return",
                    style = Theme.sans(28, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (result.owes) "Estimated amount you owe" else "Estimated federal refund",
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                    )
                    Text(
                        CalcFormat.currency(abs(result.refundOrOwed), 0),
                        style = Theme.figure(40, FontWeight.Bold),
                        color = if (result.owes) Theme.colors.negative else Theme.colors.positive,
                    )
                }
            }

            // Deduction check
            FCCard {
                FCCardHeader {
                    FCCardTitle("Deduction check")
                    FCCardDescription(deductionCopy(result))
                }
            }

            // Section edit list
            FCCard {
                FCCardHeader {
                    FCCardTitle("Your answers")
                    FCCardDescription("Jump back to any section to make changes.")
                }
                FCCardContent {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sections.forEach { section ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    section.title,
                                    style = Theme.sans(Theme.FontSize.sm),
                                    color = Theme.colors.foreground,
                                    modifier = Modifier.weight(1f),
                                )
                                FCButton(
                                    onClick = { onEdit?.invoke(section.id) },
                                    variant = FCButtonVariant.Ghost,
                                    size = FCButtonSize.Sm,
                                    enabled = onEdit != null,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text("Edit")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Full line-by-line return
            FCCard {
                FCCardHeader { FCCardTitle("Your 1040, line by line") }
                FCCardContent {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.trace.forEach { line ->
                            SummaryRowView(line.label, line.formRef, CalcFormat.currency(line.amount))
                        }
                    }
                }
            }

            AuditRiskPanel(result)
        }

        if (onFile != null) {
            FilingFooter {
                FCButton(
                    onClick = onFile,
                    size = FCButtonSize.Lg,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Continue to summary")
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun deductionCopy(result: TaxCalculationResult): String {
    val std = CalcFormat.currency(result.standardDeduction, 0)
    val item = CalcFormat.currency(result.itemizedDeduction, 0)
    val outcome = if (result.deductionUsed == DeductionUsed.Itemized) {
        "we itemized and saved you about ${CalcFormat.currency(result.itemizedSavings, 0)}."
    } else {
        "the standard deduction is better for you."
    }
    return "Standard $std vs. itemized $item: $outcome"
}

/** A summary line: label (+ dimmed form ref) on the left, amount right. */
@Composable
private fun SummaryRowView(label: String, formRef: String?, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Row(Modifier.weight(1f)) {
            Text(
                label,
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
            if (!formRef.isNullOrEmpty()) {
                Text(
                    "  $formRef",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            value,
            style = Theme.figure(Theme.FontSize.sm, FontWeight.Medium),
            color = Theme.colors.foreground,
        )
    }
}

// MARK: - AuditRiskPanel

/** Renders nothing when both lists are empty (matches the iOS early return). */
@Composable
fun AuditRiskPanel(result: TaxCalculationResult) {
    if (result.auditFlags.isEmpty() && result.warnings.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        result.auditFlags.forEach { flag ->
            AlertBox(
                destructive = flag.severity == AuditSeverity.High,
                icon = if (flag.severity == AuditSeverity.High) Icons.Outlined.Shield else Icons.Outlined.Info,
                title = if (flag.severity == AuditSeverity.Info) "Heads up" else "Check this",
                message = flag.message,
            )
        }
        result.warnings.forEach { warning ->
            AlertBox(
                destructive = false,
                icon = Icons.Outlined.Info,
                title = null,
                message = warning.message,
            )
        }
    }
}

// MARK: - FilingScreen

@Composable
fun FilingScreen(vm: TaxViewModel, onBack: (() -> Unit)? = null) {
    val result by vm.result.collectAsState()
    val summary = build1040Summary(result)
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Header (big title + hero figure)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your estimate", style = Theme.sans(28, FontWeight.Bold), color = Theme.colors.foreground)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (result.owes) "Estimated balance due" else "Estimated federal refund",
                        style = Theme.sans(Theme.FontSize.sm),
                        color = Theme.colors.mutedForeground,
                    )
                    Text(
                        CalcFormat.currency(abs(result.refundOrOwed), 0),
                        style = Theme.figure(40, FontWeight.Bold),
                        color = if (result.owes) Theme.colors.negative else Theme.colors.positive,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FCButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, renderSummaryText(summary))
                        }
                        context.startActivity(Intent.createChooser(send, "Share summary"))
                    },
                    variant = FCButtonVariant.Outline,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Save / share summary")
                    }
                }

                // The one note this screen needs. No acknowledgment checkbox:
                // there is nothing to continue to yet, and no submit outcome,
                // since nothing is submitted.
                AlertBox(
                    destructive = false,
                    icon = Icons.Outlined.Info,
                    title = "Filing is coming soon",
                    message = "FinnaCalc doesn't file returns yet. This is an estimate you can check " +
                        "against whatever you file with. Nothing here is sent anywhere.",
                )
            }

            // The printable/shareable summary, rendered inline.
            FCCard {
                FCCardHeader {
                    FCCardTitle("Your ${summary.taxYear} federal return")
                    FCCardDescription("Filing status: ${summary.filingStatusLabel}")
                }
                FCCardContent {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        summary.groups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    group.title,
                                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                                    color = Theme.colors.foreground,
                                )
                                group.lines.forEach { line ->
                                    SummaryRowView(line.label, line.formRef, CalcFormat.currency(line.amount))
                                }
                            }
                        }

                        HorizontalDivider(color = Theme.colors.border)

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                summary.headline.label,
                                style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold),
                                color = Theme.colors.foreground,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                CalcFormat.currency(summary.headline.amount, 0),
                                style = Theme.figure(Theme.FontSize.base, FontWeight.Bold),
                                color = if (summary.headline.owes) Theme.colors.destructive else Theme.colors.primary,
                            )
                        }

                        val state = summary.state
                        if (state != null) {
                            HorizontalDivider(color = Theme.colors.border)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    state.name,
                                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                                    color = Theme.colors.foreground,
                                )
                                if (state.hasIncomeTax) {
                                    SummaryRowView("State tax", null, CalcFormat.currency(state.tax))
                                    SummaryRowView(
                                        if (state.refundOrOwed >= 0) "State refund" else "State balance due",
                                        null,
                                        CalcFormat.currency(abs(state.refundOrOwed)),
                                    )
                                } else if (state.note != null) {
                                    Text(
                                        state.note,
                                        style = Theme.sans(Theme.FontSize.sm),
                                        color = Theme.colors.mutedForeground,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FilingFooter {
            if (onBack != null) {
                FCButton("Back", variant = FCButtonVariant.Outline, size = FCButtonSize.Lg, onClick = onBack)
            }
            // Deliberately inert, not hidden: filing is the obvious next step
            // and its absence should be visible, not implied. It stays disabled
            // until FinnaCalc can actually transmit, so a tap can never look
            // like it did something.
            FCButton(
                onClick = {},
                size = FCButtonSize.Lg,
                enabled = false,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("E-file (coming soon)")
                }
            }
        }
    }
}

/** Plain-text rendering for the share chooser. */
private fun renderSummaryText(summary: Form1040Summary): String {
    val lines = mutableListOf<String>()
    lines.add("FinnaCalc ${summary.taxYear} Federal Tax Estimate")
    lines.add("Filing status: ${summary.filingStatusLabel}")
    lines.add("")
    for (group in summary.groups) {
        lines.add(group.title.uppercase())
        for (line in group.lines) {
            val ref = line.formRef?.let { " ($it)" } ?: ""
            lines.add("  ${line.label}$ref: ${CalcFormat.currency(line.amount)}")
        }
        lines.add("")
    }
    lines.add("${summary.headline.label}: ${CalcFormat.currency(summary.headline.amount, 0)}")
    val state = summary.state
    if (state != null) {
        lines.add("")
        lines.add(state.name.uppercase())
        if (state.hasIncomeTax) {
            lines.add("  State tax: ${CalcFormat.currency(state.tax)}")
            val label = if (state.refundOrOwed >= 0) "State refund" else "State balance due"
            lines.add("  $label: ${CalcFormat.currency(abs(state.refundOrOwed))}")
        } else if (state.note != null) {
            lines.add("  ${state.note}")
        }
    }
    lines.add("")
    lines.add("This is an estimate, not a filed return.")
    return lines.joinToString("\n")
}

// MARK: - AlertBox

/** The native equivalent of the web `<Alert>` (default + destructive). */
@Composable
fun AlertBox(destructive: Boolean, icon: ImageVector, title: String?, message: String) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    val accent: Color = if (destructive) Theme.colors.destructive else Theme.colors.foreground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (destructive) Theme.colors.destructive.copy(alpha = 0.08f)
                else Theme.colors.muted.copy(alpha = 0.4f)
            )
            .border(
                1.dp,
                if (destructive) Theme.colors.destructive.copy(alpha = 0.5f) else Theme.colors.border,
                shape,
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold),
                    color = accent,
                )
            }
            Text(
                message,
                style = Theme.sans(Theme.FontSize.sm),
                color = if (destructive) Theme.colors.destructive else Theme.colors.mutedForeground,
            )
        }
    }
}
