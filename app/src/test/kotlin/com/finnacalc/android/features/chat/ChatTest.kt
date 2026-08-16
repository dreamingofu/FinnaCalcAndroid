package com.finnacalc.android.features.chat

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure pieces of the chat layer: the advice-disclaimer heuristic and
 * the markdown renderer that stands in for SwiftUI's AttributedString(markdown:).
 */
class ChatTest {

    // MARK: - seeksAdvice

    @Test
    fun `recommendation questions are flagged`() {
        assertTrue(ChatViewModel.seeksAdvice("Should I pay off my car loan first?"))
        assertTrue(ChatViewModel.seeksAdvice("What's the best stock to own right now"))
        assertTrue(ChatViewModel.seeksAdvice("Is a Roth worth it for me"))
        assertTrue(ChatViewModel.seeksAdvice("how much should I save each month"))
    }

    @Test
    fun `explanations are not flagged`() {
        assertFalse(ChatViewModel.seeksAdvice("What is compound interest?"))
        assertFalse(ChatViewModel.seeksAdvice("How does the standard deduction work"))
        assertFalse(ChatViewModel.seeksAdvice("Explain an expense ratio"))
    }

    @Test
    fun `the cue match is case-insensitive`() {
        assertTrue(ChatViewModel.seeksAdvice("SHOULD I REFINANCE"))
        assertTrue(ChatViewModel.seeksAdvice("Recommend a budget split"))
    }

    // MARK: - Markdown

    @Test
    fun `bold spans are styled and their markers removed`() {
        val out = renderChatMarkdown("Save **$200** a month")
        assertEquals("Save $200 a month", out.text)
        val bold = out.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals("$200", out.text.substring(bold.start, bold.end))
    }

    @Test
    fun `italics and code carry their own styles`() {
        val italic = renderChatMarkdown("that is *roughly* right")
        assertEquals("that is roughly right", italic.text)
        assertEquals(FontStyle.Italic, italic.spanStyles.single().item.fontStyle)

        val code = renderChatMarkdown("use `monthlyNet` here")
        assertEquals("use monthlyNet here", code.text)
        assertTrue(code.spanStyles.single().item.fontFamily != null)
    }

    @Test
    fun `headings become bold lines`() {
        val out = renderChatMarkdown("## Where your money goes\nrent is the biggest line")
        assertEquals("Where your money goes\nrent is the biggest line", out.text)
        assertEquals(FontWeight.Bold, out.spanStyles.single().item.fontWeight)
    }

    @Test
    fun `list markers normalise to bullets`() {
        val out = renderChatMarkdown("- rent\n- food\n  - snacks")
        assertEquals("• rent\n• food\n  • snacks", out.text)
    }

    @Test
    fun `an unpaired marker stays literal`() {
        // A lone asterisk is text, not the start of emphasis that never ends.
        val out = renderChatMarkdown("3 * 4 = 12")
        assertEquals("3 * 4 = 12", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `emphasis does not span a blank line`() {
        val out = renderChatMarkdown("first *paragraph\n\nsecond* paragraph")
        assertEquals("first *paragraph\n\nsecond* paragraph", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `code spans are not re-parsed as markdown`() {
        val out = renderChatMarkdown("`a * b` matters")
        assertEquals("a * b matters", out.text)
        assertEquals(1, out.spanStyles.size)
    }

    @Test
    fun `nested emphasis resolves both layers`() {
        val out = renderChatMarkdown("**bold with *italics* inside**")
        assertEquals("bold with italics inside", out.text)
        assertEquals(2, out.spanStyles.size)
        assertTrue(out.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(out.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `plain text passes through untouched`() {
        val raw = "Rent is 32% of your income, which is inside the usual range."
        assertEquals(raw, renderChatMarkdown(raw).text)
    }

    @Test
    fun `an empty span is left literal`() {
        // "****" would otherwise render as an invisible zero-length span.
        assertEquals("****", renderChatMarkdown("****").text)
    }
}
