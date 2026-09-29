// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Mention
import social.aloha.core.model.StatusTag

class RichTextRendererTest {
    private val colors = RichTextColors(link = Color.Blue, quote = Color.Gray, codeBackground = Color.LightGray)

    private fun render(
        html: String,
        mentions: List<Mention> = emptyList(),
        tags: List<StatusTag> = emptyList(),
        emojis: List<CustomEmoji> = emptyList(),
    ) = StatusHtmlParser.parse(html, mentions, tags, emojis).toAnnotatedString(colors)

    private fun links(text: androidx.compose.ui.text.AnnotatedString) = text.getLinkAnnotations(0, text.length).map {
        (it.item as LinkAnnotation.Url).url to
            text.substring(it.start, it.end)
    }

    @Test
    fun `paragraphs are one line each, and the text reads as the plain projection`() {
        val html = "<p>One <strong>bold</strong></p><p>Two</p>"
        val text = render(html)
        assertEquals(StatusHtmlParser.parse(html).plainText, text.text)
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("bold", text.substring(bold.start, bold.end))
    }

    @Test
    fun `list items carry their marker and code its typeface`() {
        val text = render("<ol><li>First</li><li>Second</li></ol><ul><li>Dot</li></ul><p>Use <code>gradle</code></p>")
        assertTrue(text.text.startsWith("1. First\n2. Second\n• Dot"), text.text)
        val code = text.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("gradle", text.substring(code.start, code.end))
    }

    @Test
    fun `mentions and hashtags round-trip through their link, web links stay as they are`() {
        val html = """<p><a href="https://other.test/@bob" class="u-url mention">@<span>bob</span></a> """ +
            """<a href="https://cloud.test/tags/nextcloud" class="hashtag">#NextCloud</a> """ +
            """<a href="https://example.test/page?a=1&amp;b=2">page</a></p>"""
        val text =
            render(
                html,
                listOf(Mention("42", "bob", "bob@other.test", "https://other.test/@bob")),
                listOf(StatusTag("NextCloud")),
            )
        val targets = text.getLinkAnnotations(0, text.length).map {
            RichLinkTarget.parse((it.item as LinkAnnotation.Url).url)
        }.distinct()
        assertEquals(
            listOf(
                RichLinkTarget.Mention("42", "bob@other.test"),
                RichLinkTarget.Hashtag("NextCloud"),
                RichLinkTarget.Web("https://example.test/page?a=1&b=2"),
            ),
            targets,
        )
        assertTrue(links(text).any { it.second == "page" })
    }

    @Test
    fun `a handle with characters that need escaping survives the round trip`() {
        val html = """<p><a href="https://x.test/@a%26b" class="mention">@a&amp;b</a></p>"""
        val target = RichLinkTarget.parse(
            (render(html).getLinkAnnotations(0, 12).first().item as LinkAnnotation.Url).url,
        )
        assertEquals(RichLinkTarget.Mention(null, "a&b@x.test"), target)
    }

    @Test
    fun `a custom emoji is an inline slot that reads as its shortcode`() {
        val text = render("<p>Hi :wave:</p>", emojis = listOf(CustomEmoji("wave", "https://cloud.test/wave.png")))
        assertEquals("Hi :wave:", text.text)
        val slot = text.getStringAnnotations(0, text.length).single()
        assertEquals(emojiSlot("wave"), slot.item)
        assertEquals(":wave:", text.substring(slot.start, slot.end))
    }

    @Test
    fun `a slot id is the same whatever builds it`() {
        val expected = androidx.compose.ui.text.buildAnnotatedString { appendInlineContent(emojiSlot("x"), ":x:") }
        assertEquals(expected.getStringAnnotations(0, 3).single().item, emojiSlot("x"))
    }
}
