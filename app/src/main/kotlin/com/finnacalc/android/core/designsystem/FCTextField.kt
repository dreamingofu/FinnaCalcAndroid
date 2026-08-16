//
// FCTextField.kt
//
// Port of iOS FCTextField.swift (native port of the web components/ui/input.tsx):
//
//     flex h-10 w-full rounded-md border border-input bg-background px-3 py-2
//     text-base ... placeholder:text-muted-foreground
//     focus-visible:ring-2 ring-ring ring-offset-2
//     disabled:opacity-50
//
// text-base (16) matches the phone rendering. In-field placeholder text is off
// by default (hint text was removed app-wide); opt in with `showsPlaceholder`
// (e.g. the auth form). The placeholder is always the accessibility label.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A single-line text field styled to match the web `<Input>`, including the
 * focus ring. Pass `isSecure = true` for password entry; set [keyboardOptions]
 * (e.g. KeyboardType.Decimal / Email) to match iOS keyboardType call sites.
 */
@Composable
fun FCTextField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    showsPlaceholder: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val shape = RoundedCornerShape(Theme.Radius.md)
    Box(
        modifier = modifier
            .fillMaxWidth()  // w-full
            .alpha(if (enabled) 1f else 0.5f)  // disabled:opacity-50
            // focus-visible:ring-2 ring-ring ring-offset-2 — drawn just outside
            // the field border while focused.
            .then(
                if (focused) {
                    Modifier.border(2.dp, c.ring, RoundedCornerShape(Theme.Radius.md + 2.dp))
                } else Modifier
            )
            .padding(2.dp)
            .height(40.dp)  // h-10
            .background(c.background, shape)  // bg-background
            .border(1.dp, c.input, shape)     // border border-input
            .padding(horizontal = 12.dp)      // px-3
            .semantics { contentDescription = placeholder },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = Theme.sans(Theme.FontSize.base).copy(color = c.foreground),  // text-base (16)
            cursorBrush = SolidColor(c.primary),  // caret color
            keyboardOptions = keyboardOptions,
            visualTransformation = if (isSecure) {
                PasswordVisualTransformation()
            } else VisualTransformation.None,
        )
        if (showsPlaceholder && value.isEmpty()) {
            Text(
                placeholder,
                style = Theme.sans(Theme.FontSize.base),
                color = c.mutedForeground,  // placeholder:text-muted-foreground
            )
        }
    }
}
