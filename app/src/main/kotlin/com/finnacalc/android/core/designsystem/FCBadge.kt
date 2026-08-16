//
// FCBadge.kt
//
// Port of iOS FCBadge.swift (native port of the web components/ui/badge.tsx):
//
//     inline-flex items-center rounded-full border px-2.5 py-0.5
//     text-xs font-semibold
//
// Static label, no press state (the web badge is a non-interactive div).
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Badge variants (design Badge.prompt.md). */
enum class FCBadgeVariant {
    Default,      // brand emphasis (filled)
    Secondary,    // neutral category tag (muted fill)
    Destructive,  // destructive (red fill)
    Outline,      // quiet label (border, no fill)
    Positive,     // gains (green tint)
    Negative,     // costs (red tint)
    Caution,      // pending (amber tint)
}

@Composable
fun FCBadge(
    modifier: Modifier = Modifier,
    variant: FCBadgeVariant = FCBadgeVariant.Default,
    dot: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = Theme.colors
    val fill = when (variant) {
        FCBadgeVariant.Default -> c.primary
        FCBadgeVariant.Secondary -> c.secondary
        FCBadgeVariant.Destructive -> c.destructive
        FCBadgeVariant.Outline -> Color.Transparent
        FCBadgeVariant.Positive -> c.positive.copy(alpha = 0.12f)
        FCBadgeVariant.Negative -> c.negative.copy(alpha = 0.12f)
        FCBadgeVariant.Caution -> c.cautionTint
    }
    val foreground = when (variant) {
        FCBadgeVariant.Default -> c.primaryForeground
        FCBadgeVariant.Secondary -> c.secondaryForeground
        FCBadgeVariant.Destructive -> c.destructiveForeground
        FCBadgeVariant.Outline -> c.foreground
        FCBadgeVariant.Positive -> c.positive
        FCBadgeVariant.Negative -> c.negative
        FCBadgeVariant.Caution -> c.caution
    }
    // border-transparent on filled variants
    val borderColor = if (variant == FCBadgeVariant.Outline) c.border else Color.Transparent

    Row(
        modifier = modifier
            .clip(CircleShape)  // rounded-full
            .background(fill)
            .border(1.dp, borderColor, CircleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),  // px-2.5 py-0.5
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(foreground)
            )
        }
        CompositionLocalProvider(LocalContentColor provides foreground) {
            ProvideTextStyle(
                Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold).copy(color = foreground)
            ) {
                content()
            }
        }
    }
}

/** String convenience: `FCBadge("+1.8%", variant = Positive, dot = true)`. */
@Composable
fun FCBadge(
    title: String,
    modifier: Modifier = Modifier,
    variant: FCBadgeVariant = FCBadgeVariant.Default,
    dot: Boolean = false,
) {
    FCBadge(modifier, variant, dot) { Text(title) }
}
