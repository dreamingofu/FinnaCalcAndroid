//
// Paper.kt
//
// Port of the "Paper & Cobalt" support pieces from iOS Features/Home/
// PaperHome.swift — token aliases onto Theme, compact money formatting, the
// paper card chrome, section headers/links, the illustrative sample donut,
// the ambient AI glow, and the big-card metrics. Shared by the Budgeting hub
// and (Phase 8) the Home dashboard.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finnacalc.android.features.calculators.CalcFormat
import kotlin.math.cos
import kotlin.math.sin

/**
 * Token aliases (iOS `enum Paper`) — every value resolves through Theme, so
 * the hub follows light/dark like everything else.
 */
object Paper {
    val page: Color @Composable get() = Theme.colors.background
    val card: Color @Composable get() = Theme.colors.card
    val border: Color @Composable get() = Theme.colors.border
    val divider: Color @Composable get() = Theme.colors.border
    val ink: Color @Composable get() = Theme.colors.foreground
    val muted: Color @Composable get() = Theme.colors.mutedForeground
    val chipText: Color @Composable get() = Theme.colors.mutedForeground
    val chevron: Color @Composable get() = Theme.colors.borderStrong
    val cobalt: Color @Composable get() = Theme.colors.primary
    val cobaltSoft: Color @Composable get() = Theme.colors.brandTint
    val chipFill: Color @Composable get() = Theme.colors.secondary
    val ringTrack: Color @Composable get() = Theme.colors.secondary
    val positive: Color @Composable get() = Theme.colors.positive
    val negative: Color @Composable get() = Theme.colors.negative

    /**
     * "$3.2k / $5k"-style compact currency. Values under $10 keep their
     * cents: rounding $2.90 to "$3" made a tiny real portfolio read as a
     * different number on Home than on the Portfolio page.
     */
    fun compactMoney(value: Double): String {
        if (value >= 1000) {
            val k = value / 1000
            val str = if (k == kotlin.math.floor(k)) "%.0f".format(k) else "%.1f".format(k)
            return "$${str}k"
        }
        if (value < 10 && value != kotlin.math.floor(value)) {
            return "$" + CalcFormat.fixed(value, 2)
        }
        return "$" + CalcFormat.int(value)
    }
}

/**
 * Geometry shared by the big cards (iOS PaperBigCard): radius, border weight.
 */
object PaperBigCard {
    val radius: Dp = 22.dp
    val borderWidth: Dp = 1.5.dp
}

/** Card + border + soft shadow (the design's card chrome). */
fun Modifier.paperCard(radius: Dp): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    this
        .fcShadow(Theme.Elevation.Sm, shape)
        .clip(shape)
        .background(Theme.colors.card)
        .border(1.dp, Theme.colors.border, shape)
}

/** Uppercase tracked section header with an optional trailing slot. */
@Composable
fun PaperSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
            color = Theme.colors.mutedForeground,
        )
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** "Label →" section link. */
@Composable
fun PaperSectionLink(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fcPressable(onClick),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold),
            color = Theme.colors.primary,
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Theme.colors.primary,
            modifier = Modifier.size(11.dp),
        )
    }
}

/**
 * A four-colour donut with no figures in it — purely illustrative. Used for
 * empty states and the Budgeting hub's My Budget mark, so both preview the
 * real donut without ever implying real data.
 */
@Composable
fun PaperSampleDonut(size: Dp = 92.dp) {
    val wedges = listOf(
        0.38f to Color(0xFF3B5BDB), 0.27f to Color(0xFFE8590C),
        0.21f to Color(0xFF0CA678), 0.14f to Color(0xFFE64980),
    )
    Canvas(Modifier.size(size)) {
        val lineWidth = maxOf(5.dp.toPx(), size.toPx() * 0.11f)
        val radius = (minOf(this.size.width, this.size.height) - lineWidth) / 2
        val topLeft = Offset(this.size.width / 2 - radius, this.size.height / 2 - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val gap = 3f
        var start = -90f
        for ((fraction, color) in wedges) {
            val sweep = 360f * fraction
            drawArc(
                color = color,
                startAngle = start + gap / 2,
                sweepAngle = sweep - gap,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = lineWidth, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

/**
 * The Gemini-style ambient AI glow — a slowly drifting brand gradient border.
 * (Deviation from iOS: the blurred halo behind the card is dropped — Compose
 * blur of an animating layer is costly on low-end devices; the drifting
 * gradient border carries the treatment.)
 */
fun Modifier.ambientGlow(cornerRadius: Dp = 18.dp): Modifier = composed {
    val stops = listOf(
        Color(0xFF2563EB), Color(0xFF7C3AED),
        Color(0xFF06B6D4), Color(0xFF2563EB),
    )
    val transition = rememberInfiniteTransition(label = "ambientGlow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "glowAngle",
    )
    val start = Offset(0.5f + 0.5f * cos(t), 0.5f + 0.5f * sin(t))
    val end = Offset(0.5f - 0.5f * cos(t), 0.5f - 0.5f * sin(t))
    border(
        width = 1.5.dp,
        brush = Brush.linearGradient(
            colors = stops,
            start = Offset(start.x * 1000f, start.y * 1000f),
            end = Offset(end.x * 1000f, end.y * 1000f),
        ),
        shape = RoundedCornerShape(cornerRadius),
    )
}
