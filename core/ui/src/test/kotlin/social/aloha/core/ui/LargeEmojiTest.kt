// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.CustomEmoji

class LargeEmojiTest {
    private fun large(html: String, emojis: List<CustomEmoji> = emptyList()) =
        StatusHtmlParser.parse(html, emojis = emojis).isFewEmoji()

    @Test
    fun `one, two and three emoji are drawn large, four are not`() {
        assertTrue(large("<p>\uD83D\uDE00</p>"))
        assertTrue(large("<p>\uD83D\uDE00\uD83C\uDF89</p>"))
        assertTrue(large("<p>\uD83D\uDE00 \uD83C\uDF89 \u2764\uFE0F</p>"))
        assertFalse(large("<p>\uD83D\uDE00\uD83C\uDF89\u2764\uFE0F\u2B50</p>"))
    }

    @Test
    fun `text beside an emoji, or no text at all, is drawn as text`() {
        assertFalse(large("<p>yes \uD83D\uDE00</p>"))
        assertFalse(large("<p>\u2605</p>"))
        assertFalse(large("<p>1</p>"))
        assertFalse(large("<p> </p>"))
        assertFalse(large(""))
    }

    @Test
    fun `flags, keycaps, skin tones and sequences each count as one`() {
        assertTrue(large("<p>\uD83C\uDDE9\uD83C\uDDEA\uD83C\uDDEB\uD83C\uDDF7</p>"))
        assertTrue(large("<p>1\uFE0F\u20E3#\uFE0F\u20E3</p>"))
        assertTrue(large("<p>\uD83D\uDC4D\uD83C\uDFFD\u270C\uD83C\uDFFF</p>"))
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        assertTrue(large("<p>$family$family$family</p>"))
        val scotland = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F"
        assertTrue(large("<p>$scotland</p>"))
        assertFalse(large("<p>$scotland$scotland$family\uD83D\uDC4D\uD83C\uDFFD</p>"))
    }

    @Test
    fun `the post's own custom emoji count as emoji, an unknown shortcode is text`() {
        val blobcat = listOf(CustomEmoji("blobcat", "https://x.example/blobcat.png"))
        assertTrue(large("<p>:blobcat:</p>", blobcat))
        assertTrue(large("<p>:blobcat: \uD83D\uDE00 :blobcat:</p>", blobcat))
        assertFalse(large("<p>:blobcat::blobcat::blobcat::blobcat:</p>", blobcat))
        assertFalse(large("<p>:blobdog:</p>", blobcat))
    }

    @Test
    fun `whitespace and line breaks between emoji are ignored`() {
        assertTrue(large("<p>\uD83D\uDE00<br>\uD83D\uDE00</p><p>\n \uD83D\uDE00 </p>"))
    }
}
