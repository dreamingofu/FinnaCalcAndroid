//
// ChatMarkdown.kt
//
// The Android half of iOS FinnaBotView.rendered(_:) — renders the assistant's
// markdown the way Claude-style replies read: inline **bold** / *italics* /
// `code` styled, line breaks preserved, list markers normalised to bullets,
// heading markers folded into bold lines.
//
// SwiftUI gets this free from AttributedString(markdown:); Compose has no
// markdown parser, so the inline spans are built here. Anything the parser
// doesn't recognise stays as literal text, which is the same fallback the iOS
// version takes when parsing fails.
//

package com.finnacalc.android.features.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val HEADING = Regex("""(?m)^#{1,6}\s*(.+)$""")
private val BULLET = Regex("""(?m)^(\s*)[-*]\s+""")

/**
 * One inline marker: its delimiter and the style it applies. Ordered longest
 * delimiter first so `**bold**` is matched before `*italic*`.
 */
private val INLINE = listOf(
    "**" to SpanStyle(fontWeight = FontWeight.Bold),
    "__" to SpanStyle(fontWeight = FontWeight.Bold),
    "*" to SpanStyle(fontStyle = FontStyle.Italic),
    "_" to SpanStyle(fontStyle = FontStyle.Italic),
    "`" to SpanStyle(fontFamily = FontFamily.Monospace),
)

/** Renders assistant markdown into styled text. */
fun renderChatMarkdown(raw: String): AnnotatedString {
    // "## Heading" → "**Heading**", "- item" → "• item", as on iOS.
    var text = HEADING.replace(raw) { "**${it.groupValues[1]}**" }
    text = BULLET.replace(text) { "${it.groupValues[1]}• " }
    return buildAnnotatedString { appendInline(text) }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val marker = INLINE.firstOrNull { (delim, _) ->
            text.startsWith(delim, i) && closingIndex(text, i + delim.length, delim) != null
        }
        if (marker == null) {
            append(text[i])
            i += 1
            continue
        }
        val (delim, style) = marker
        val contentStart = i + delim.length
        val close = closingIndex(text, contentStart, delim)!!
        withStyle(style) {
            // Nested emphasis (e.g. **bold with `code`**) resolves recursively;
            // code spans stay literal so backticked markdown isn't re-parsed.
            if (delim == "`") append(text.substring(contentStart, close))
            else appendInline(text.substring(contentStart, close))
        }
        i = close + delim.length
    }
}

/**
 * The index of [delim]'s closing run at or after [from], or null when the
 * marker is never closed — an unpaired `*` is literal text, not emphasis.
 */
private fun closingIndex(text: String, from: Int, delim: String): Int? {
    if (from >= text.length) return null
    // An immediately-repeated delimiter is an empty span, not emphasis.
    if (text.startsWith(delim, from)) return null
    var i = from
    while (i < text.length) {
        // Emphasis doesn't span paragraphs; a blank line ends the search.
        if (text.startsWith("\n\n", i)) return null
        if (text.startsWith(delim, i)) return i
        i += 1
    }
    return null
}
