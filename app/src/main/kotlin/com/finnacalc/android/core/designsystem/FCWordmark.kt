//
// FCWordmark.kt
//
// Port of iOS FCWordmark.swift — the "Finna" + "Calc" wordmark from
// components/header.tsx, where "Calc" is tinted with the brand blue.
//

package com.finnacalc.android.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

@Composable
fun FCWordmark(
    modifier: Modifier = Modifier,
    size: Int = Theme.FontSize.xl,  // text-xl in the header
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Theme.colors.foreground)) { append("Finna") }
            withStyle(SpanStyle(color = Theme.colors.primary)) { append("Calc") }
        },
        style = Theme.sans(size, FontWeight.Bold),
        modifier = modifier,
    )
}
