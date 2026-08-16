//
// FCStat.kt
//
// Port of iOS FCStat.swift — Stat / ResultRow, the calculator output
// components. FCStat is the headline figure; FCResultRow is one labeled line
// in the breakdown. Values render in the figure style with green/red tone for
// gains/costs — the design's signature ("figures are the heroes").
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Result tone — gains/payments (positive) vs costs/interest (negative). */
enum class FCResultTone {
    Neutral, Positive, Negative;

    val color: Color
        @Composable get() = when (this) {
            Neutral -> Theme.colors.foreground
            Positive -> Theme.colors.positive
            Negative -> Theme.colors.negative
        }
}

/** The headline calculator figure (monthly payment, true APR, …). */
@Composable
fun FCStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: FCResultTone = FCResultTone.Neutral,
    size: FCStatSize = FCStatSize.Medium,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            label.uppercase(),
            style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold).copy(
                letterSpacing = 0.6.sp,  // overline: uppercase, wide-tracked
            ),
            color = Theme.colors.mutedForeground,
        )
        Text(
            value,
            style = Theme.figure(size.point, FontWeight.Bold),
            color = tone.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class FCStatSize(val point: Int) {
    Small(Theme.FontSize.xl2),   // 24 — compact KPI tiles
    Medium(Theme.FontSize.xl3),  // 30
    Large(Theme.FontSize.xl4),   // 36 — headline
}

/** One labeled line in a result breakdown — label left, figure right. */
@Composable
fun FCResultRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: FCResultTone = FCResultTone.Neutral,
    emphasized: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Theme.sans(Theme.FontSize.sm),
            color = Theme.colors.mutedForeground,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.widthIn(min = 12.dp).weight(1f))
        Text(
            value,
            style = Theme.figure(Theme.FontSize.sm, if (emphasized) FontWeight.Bold else FontWeight.Medium),
            color = if (tone == FCResultTone.Neutral && emphasized) {
                Theme.colors.foreground
            } else tone.color,
        )
    }
}
