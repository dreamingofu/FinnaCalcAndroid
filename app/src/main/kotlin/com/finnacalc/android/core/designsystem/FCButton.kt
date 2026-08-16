//
// FCButton.kt
//
// Port of iOS FCButton.swift (itself a native port of the web
// components/ui/button.tsx). Web `hover:` states map to the pressed state
// (the /90, /80 opacity steps); `disabled:opacity-50` maps to `enabled`.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/** Mirrors buttonVariants.variant in button.tsx. */
enum class FCButtonVariant {
    Default,      // bg-primary text-primary-foreground hover:bg-primary/90
    Destructive,  // bg-destructive text-destructive-foreground hover:bg-destructive/90
    Outline,      // border border-input bg-background hover:bg-accent
    Secondary,    // bg-secondary text-secondary-foreground hover:bg-secondary/80
    Ghost,        // hover:bg-accent hover:text-accent-foreground
    Link,         // text-primary underline on press
}

/** Mirrors buttonVariants.size in button.tsx. */
enum class FCButtonSize {
    Default,  // h-10 px-4
    Sm,       // h-9  px-3
    Lg,       // h-11 px-8
    Icon,     // h-10 w-10
}

/**
 * A button styled to match the web `<Button>`. Slot content for arbitrary
 * label (icon + text); use the String overload for plain text.
 */
@Composable
fun FCButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FCButtonVariant = FCButtonVariant.Default,
    size: FCButtonSize = FCButtonSize.Default,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val height = when (size) {
        FCButtonSize.Default, FCButtonSize.Icon -> 40.dp  // h-10
        FCButtonSize.Sm -> 36.dp                          // h-9
        FCButtonSize.Lg -> 44.dp                          // h-11
    }
    val hPad = when (size) {
        FCButtonSize.Default -> 16.dp  // px-4
        FCButtonSize.Sm -> 12.dp       // px-3
        FCButtonSize.Lg -> 32.dp       // px-8
        FCButtonSize.Icon -> 0.dp      // square
    }

    // Background; `pressed` applies the web hover: opacity step.
    val background = when (variant) {
        FCButtonVariant.Default -> c.primary.copy(alpha = if (pressed) 0.9f else 1f)
        FCButtonVariant.Destructive -> c.destructive.copy(alpha = if (pressed) 0.9f else 1f)
        FCButtonVariant.Secondary -> c.secondary.copy(alpha = if (pressed) 0.8f else 1f)
        FCButtonVariant.Outline -> if (pressed) c.accent else c.background
        FCButtonVariant.Ghost -> if (pressed) c.accent else Color.Transparent
        FCButtonVariant.Link -> Color.Transparent
    }
    val foreground = when (variant) {
        FCButtonVariant.Default -> c.primaryForeground
        FCButtonVariant.Destructive -> c.destructiveForeground
        FCButtonVariant.Secondary -> c.secondaryForeground
        FCButtonVariant.Outline, FCButtonVariant.Ghost ->
            if (pressed) c.accentForeground else c.foreground
        FCButtonVariant.Link -> c.primary
    }
    val animatedBackground by animateColorAsState(
        background, tween(150), label = "fcButtonBackground" // transition-colors
    )

    val shape = RoundedCornerShape(Theme.Radius.md)
    Row(
        modifier = modifier
            .height(height)
            .then(if (size == FCButtonSize.Icon) Modifier.width(40.dp) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)  // disabled:opacity-50
            .clip(shape)
            .background(animatedBackground)
            .then(
                if (variant == FCButtonVariant.Outline) {
                    Modifier.border(1.dp, c.input, shape)  // border border-input
                } else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,  // press feedback is the color step, as on iOS
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = hPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),  // gap-2
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            ProvideTextStyle(
                Theme.sans(Theme.FontSize.sm, FontWeight.Medium).copy(
                    color = foreground,
                    textDecoration = if (variant == FCButtonVariant.Link && pressed) {
                        TextDecoration.Underline
                    } else TextDecoration.None,
                )
            ) {
                content()
            }
        }
    }
}

/** Convenience for a plain text button: `FCButton("Save") { ... }`. */
@Composable
fun FCButton(
    title: String,
    modifier: Modifier = Modifier,
    variant: FCButtonVariant = FCButtonVariant.Default,
    size: FCButtonSize = FCButtonSize.Default,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FCButton(onClick, modifier, variant, size, enabled) { Text(title) }
}
