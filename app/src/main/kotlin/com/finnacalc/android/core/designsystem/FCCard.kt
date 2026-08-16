//
// FCCard.kt
//
// Port of iOS FCCard.swift (native port of the web components/ui/card.tsx).
// Card + Header/Title/Description/Content/Footer composition preserved so call
// sites read the same way:
//
//     FCCard {
//         FCCardHeader {
//             FCCardTitle("ROI")
//             FCCardDescription("Return on investment")
//         }
//         FCCardContent { ... }
//         FCCardFooter { FCButton("Calculate") { } }
//     }
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The primary content container — rounded 12, hairline border, soft shadow.
 * Set [interactive] when the whole card navigates somewhere (a touch stronger
 * elevation to read as tappable).
 */
@Composable
fun FCCard(
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fcShadow(if (interactive) Theme.Elevation.Md else Theme.Elevation.Sm, shape)
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape),
        horizontalAlignment = Alignment.Start,
    ) {
        CompositionLocalProvider(LocalContentColor provides Theme.colors.cardForeground) {
            content()
        }
    }
}

/** `flex flex-col space-y-1.5 p-6` */
@Composable
fun ColumnScope.FCCardHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),  // p-6
        verticalArrangement = Arrangement.spacedBy(6.dp),  // space-y-1.5
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

/**
 * `text-2xl font-semibold leading-none tracking-tight`
 *
 * `leading-none` approximated with a tight lineHeight — FinnaCalc card titles
 * are short single-line strings, for which this is pixel-identical (same
 * accepted approximation as the iOS port).
 */
@Composable
fun FCCardTitle(text: String) {
    Text(
        text,
        style = Theme.sans(Theme.FontSize.xl2, FontWeight.SemiBold).copy(
            letterSpacing = (-0.025).em(Theme.FontSize.xl2),  // tracking-tight
            lineHeight = (Theme.FontSize.xl2 + 2).sp,
        ),
        color = Theme.colors.cardForeground,
    )
}

/** em-based letter spacing at a given point size (`-0.025em @ 24pt` = -0.6sp). */
private fun Double.em(size: Int) = (this * size).sp

/** `text-sm text-muted-foreground` */
@Composable
fun FCCardDescription(text: String) {
    Text(
        text,
        style = Theme.sans(Theme.FontSize.sm),
        color = Theme.colors.mutedForeground,
    )
}

/** `p-6 pt-0` — sits flush under the header. */
@Composable
fun ColumnScope.FCCardContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.Start,
        content = content,
    )
}

/** `flex items-center p-6 pt-0` */
@Composable
fun ColumnScope.FCCardFooter(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
