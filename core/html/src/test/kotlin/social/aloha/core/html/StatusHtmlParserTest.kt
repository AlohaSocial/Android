// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import social.aloha.core.model.Account
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Mention
import social.aloha.core.model.Status
import social.aloha.core.model.StatusTag

private fun parse(
    html: String,
    mentions: List<Mention> = emptyList(),
    tags: List<StatusTag> = emptyList(),
    emojis: List<CustomEmoji> = emptyList(),
) = StatusHtmlParser.parse(html, mentions, tags, emojis)

class StatusHtmlParserTest {
    @Nested
    inner class Structure {
        @Test
        fun `a plain paragraph becomes text`() {
            val result = parse("<p>Hello, world.</p>")
            assertEquals("Hello, world.", result.plainText)
            assertEquals(1, result.blocks.size)
        }

        @Test
        fun `paragraphs are separated in the plain projection`() {
            val result = parse("<p>One</p><p>Two</p>")
            assertEquals("One\nTwo", result.plainText)
            assertEquals(2, result.blocks.size)
        }

        @Test
        fun `a line break is a newline, not a lost character`() {
            assertEquals("One\nTwo", parse("<p>One<br>Two</p>").plainText)
            assertEquals("One\nTwo", parse("<p>One<br />Two</p>").plainText)
        }

        @Test
        fun `nested formatting composes rather than replacing`() {
            val italic = parse("<p><strong>Bold <em>and italic</em></strong></p>").runs.first {
                "and italic" in it.text
            }
            assertTrue(RichText.Style.Bold in italic.style)
            assertTrue(RichText.Style.Italic in italic.style)
        }

        @Test
        fun `unsupported tags are stripped to their text content`() {
            val result = parse("<p>Before <script>evil()</script><marquee>After</marquee></p>")
            assertTrue("Before" in result.plainText)
            assertTrue("After" in result.plainText)
            assertFalse("<script>" in result.plainText)
        }

        @Test
        fun `comments and doctypes vanish`() {
            assertEquals("Kept", parse("<!DOCTYPE html><p>Kept<!-- dropped --></p>").plainText)
        }

        @Test
        fun `malformed markup does not hang or throw`() {
            val inputs = listOf(
                "<p>Unclosed", "<<<>>>", "<p class=", "<a href='unterminated>text</a>",
                "5 < 6 and 7 > 3", "<p>a</p></div></div>", "<", "<!--", "<!", "&", "&#;", "&#x110000;",
            )
            assertTimeoutPreemptively(Duration.ofSeconds(1)) { inputs.forEach { parse(it) } }
        }

        @Test
        fun `entities decode, including numeric and hex forms`() {
            assertEquals("Tom & Jerry", parse("<p>Tom &amp; Jerry</p>").plainText)
            assertEquals("<tag>", parse("<p>&lt;tag&gt;</p>").plainText)
            assertEquals("café", parse("<p>caf&#233;</p>").plainText)
            assertEquals("café", parse("<p>caf&#xe9;</p>").plainText)
            assertEquals("…", parse("<p>&hellip;</p>").plainText)
            assertEquals("R&D", parse("<p>R&D</p>").plainText)
            assertEquals("&notanentity;", parse("<p>&notanentity;</p>").plainText)
            // beyond the basic plane, and a number that is no character at all
            assertEquals("😀", parse("<p>&#128512;</p>").plainText)
            assertEquals("&#xD800;", parse("<p>&#xD800;</p>").plainText)
        }

        @Test
        fun `lists produce blocks with their ordinal`() {
            val unordered = parse("<ul><li>One</li><li>Two</li></ul>")
            assertTrue("One" in unordered.plainText && "Two" in unordered.plainText)
            val indices = parse("<ol><li>First</li><li>Second</li></ol>").blocks.mapNotNull {
                (it.kind as? RichText.Block.Kind.ListItem)?.takeIf { item -> item.ordered }?.index
            }
            assertEquals(listOf(1, 2), indices)
        }

        @Test
        fun `blockquotes and code carry their style`() {
            assertTrue(parse("<blockquote><p>Quoted</p></blockquote>").runs.any { RichText.Style.Quote in it.style })
            val code = parse("<p>Use <code>gradle build</code></p>")
            assertTrue(code.runs.any { it.text == "gradle build" && RichText.Style.Code in it.style })
        }
    }

    @Nested
    inner class Links {
        private val mentions = listOf(Mention("42", "bob", "bob@other.test", "https://other.test/@bob"))
        private val tags = listOf(StatusTag("NextCloud", "https://cloud.test/tags/nextcloud"))

        @Test
        fun `a mention resolves to the account it names, by href`() {
            val html = """<p><a href="https://other.test/@bob" class="u-url mention">@<span>bob</span></a></p>"""
            val link = parse(html, mentions).runs.firstNotNullOf { it.link } as RichText.Link.Mention
            assertEquals("42", link.accountId)
            assertEquals("bob@other.test", link.acct)
        }

        @Test
        fun `the domain inside a span is kept, so two Alices stay distinguishable`() {
            val html = """<p><a href="https://other.test/@bob" class="u-url mention">@<span>bob</span>""" +
                """<span class="invisible">@other.test</span></a></p>"""
            assertTrue("other.test" in parse(html, mentions).plainText)
        }

        @Test
        fun `a mention the status did not declare still routes by handle`() {
            val html = """<p><a href="https://elsewhere.test/@carol" class="mention">@carol</a></p>"""
            val link = parse(html).runs.firstNotNullOf { it.link } as RichText.Link.Mention
            assertNull(link.accountId)
            assertEquals("carol@elsewhere.test", link.acct)
        }

        @Test
        fun `a hashtag takes the status's own spelling, not the URL's`() {
            val html = """<p><a href="https://cloud.test/tags/nextcloud" class="hashtag">""" +
                """#<span>NextCloud</span></a></p>"""
            val link = parse(html, tags = tags).runs.firstNotNullOf { it.link } as RichText.Link.Hashtag
            assertEquals("NextCloud", link.name)
        }

        @Test
        fun `Mastodon's hashtag, marked mention and hashtag at once, is a hashtag`() {
            val html = """<p><a href="https://mastodon.xyz/tags/Teams" class="mention hashtag" rel="tag">""" +
                """#<span>Teams</span></a></p>"""
            val link = parse(html).runs.firstNotNullOf { it.link }
            assertEquals(RichText.Link.Hashtag::class, link::class)
            assertEquals("Teams", (link as RichText.Link.Hashtag).name)
        }

        @Test
        fun `an ordinary link is a web link`() {
            val result = parse("""<p>See <a href="https://example.test/page">this</a></p>""")
            assertEquals(listOf("https://example.test/page"), result.webLinks)
            assertTrue(result.mentions.isEmpty())
        }

        @Test
        fun `a link that is not http or https is only text`() {
            for (href in listOf(
                "javascript:alert(1)",
                "intent://scan#Intent;scheme=zxing;end",
                "file:///etc/passwd",
                "/relative",
            )) {
                val result = parse("""<p><a href="$href">tap</a></p>""")
                assertTrue(result.runs.all { it.link == null }, href)
                assertEquals("tap", result.plainText)
            }
        }
    }

    @Nested
    inner class Emoji {
        private val emojis = listOf(
            CustomEmoji("nextcloud", "https://cloud.test/e/nextcloud.png"),
            CustomEmoji("wave", "https://cloud.test/e/wave.png"),
        )

        @Test
        fun `a known shortcode becomes an emoji run`() {
            val codes = parse("<p>Hello :wave: from :nextcloud:</p>", emojis = emojis).runs.mapNotNull {
                it.emoji?.shortcode
            }
            assertEquals(listOf("wave", "nextcloud"), codes)
        }

        @Test
        fun `an unknown shortcode stays literal text`() {
            val result = parse("<p>:unknown_thing:</p>", emojis = emojis)
            assertTrue(result.runs.all { it.emoji == null })
            assertEquals(":unknown_thing:", result.plainText)
        }

        @Test
        fun `prose with colons is not mistaken for emoji`() {
            val result = parse("<p>Note: this is fine. Ratio 3:1 here.</p>", emojis = emojis)
            assertTrue(result.runs.all { it.emoji == null })
            assertTrue("3:1" in result.plainText)
        }

        @Test
        fun `a hidden emoji typed by hand still renders`() {
            val hidden = listOf(CustomEmoji("secret", "https://x.test/s.png", visibleInPicker = false))
            assertEquals(
                listOf("secret"),
                parse("<p>:secret:</p>", emojis = hidden).runs.mapNotNull {
                    it.emoji?.shortcode
                },
            )
        }
    }

    @Nested
    inner class Performance {
        @Test
        fun `a 50 KB pathological input parses within a bound`() {
            val html = "<p>" + "<span><strong><em>x</em></strong></span>".repeat(1200) + "</p>"
            assertTrue(html.length > 45_000)
            val result = assertTimeoutPreemptively<RichText>(Duration.ofSeconds(1)) { parse(html) }
            assertFalse(result.plainText.isEmpty())
        }

        @Test
        fun `a long run of entities does not degrade badly`() {
            val html = "<p>" + "&amp;&lt;&gt;&#233;".repeat(3000) + "</p>"
            assertTimeoutPreemptively(Duration.ofSeconds(1)) { parse(html) }
        }

        @Test
        fun `ampersands and colons without their closing character stay linear`() {
            val emojis = listOf(CustomEmoji("wave", "https://cloud.test/e/wave.png"))
            val html = "<p>" + "&".repeat(50_000) + ":".repeat(50_000) + "</p>"
            assertTimeoutPreemptively(Duration.ofSeconds(1)) { parse(html, emojis = emojis) }
        }
    }

    @Nested
    inner class Cache {
        private val account = Account("1", "a", "a")

        @Test
        fun `the same status parses once and is served from the cache after`() {
            val cache = RichTextCache()
            val status = Status("1", account, content = "<p>Cached</p>")
            val first = cache.richText(status)
            assertSame(first, cache.richText(status.copy(favouritesCount = 3)))
            assertEquals("Cached", first.plainText)
        }

        @Test
        fun `a boost is parsed from what it boosts`() {
            val boost = Status("2", account, reblog = Status("1", account, content = "<p>Original</p>"))
            assertEquals("Original", RichTextCache().richText(boost).plainText)
        }

        @Test
        fun `the least recently used entry goes first`() {
            val cache = RichTextCache(capacity = 2)
            val a = Status("a", account, content = "<p>A</p>")
            val first = cache.richText(a)
            cache.richText(Status("b", account, content = "<p>B</p>"))
            cache.richText(a)
            cache.richText(Status("c", account, content = "<p>C</p>"))
            assertSame(first, cache.richText(a))
        }

        @Test
        fun `a content warning is text, so a less-than sign in it is kept`() {
            val status =
                Status("1", account, spoilerText = "<3 spoilers & more :wave:", emojis = listOf(CustomEmoji("wave")))
            val spoiler = RichTextCache().spoiler(status)
            assertEquals("<3 spoilers & more :wave:", spoiler.plainText)
            assertEquals("wave", spoiler.runs.last().emoji?.shortcode)
        }
    }
}
