//
// Motion.kt
//
// Shared native motion primitives, ported from iOS Motion.swift:
//   · Modifier.fcPressable — springs content down slightly while pressed (the
//     tactile "native app" feel on every tappable row/tile).
//   · Modifier.staggeredAppear(index) — a one-shot fade + rise for list/hub
//     rows, each row landing a beat after the previous one.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Spring scale + dim while pressed, with the click handled here so the press
 * state drives the animation. Use on row/tile buttons (iOS FCPressable).
 */
fun Modifier.fcPressable(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "fcPressableScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "fcPressableAlpha",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** One-shot fade + rise, staggered by row index (caps at 12 beats). */
fun Modifier.staggeredAppear(index: Int): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 12) * 50L)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "staggeredAlpha",
    )
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 14f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "staggeredRise",
    )
    graphicsLayer {
        this.alpha = alpha
        translationY = rise * 1.dp.toPx()
    }
}
