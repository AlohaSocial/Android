// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArticleTitleTest {
    @Test
    fun `the heading the server prepends is removed, and nothing after it`() {
        assertEquals("<p>Body</p>", withoutArticleTitle("<h2>On tides</h2><p>Body</p>", "On tides"))
    }

    @Test
    fun `a paragraph or other heading that is the title counts as its copy`() {
        assertEquals("<p>Body</p>", withoutArticleTitle("<p>On tides</p><p>Body</p>", "On tides"))
        val heading = " <H1 class=\"t\"><b>On</b>\n tides</H1><p>Body</p>"
        assertEquals("<p>Body</p>", withoutArticleTitle(heading, "On tides"))
    }

    @Test
    fun `content that does not open with the title is left whole`() {
        val content = "<p>Intro</p><h2>On tides</h2>"
        assertEquals(content, withoutArticleTitle(content, "On tides"))
        assertEquals("<h2>On tides, part 2</h2>", withoutArticleTitle("<h2>On tides, part 2</h2>", "On tides"))
        assertEquals("On tides<p>x</p>", withoutArticleTitle("On tides<p>x</p>", "On tides"))
        assertEquals("<p>x</p>", withoutArticleTitle("<p>x</p>", ""))
    }

    @Test
    fun `entities in the copy match the plain-text title`() {
        val title = "Fish & <Chips> \"today\""
        val content = "<h2>Fish &amp; &lt;Chips&gt; &quot;today&quot;</h2><p>Body</p>"
        assertEquals("<p>Body</p>", withoutArticleTitle(content, title))
    }

    @Test
    fun `a title longer than the content does not match`() {
        assertEquals("<p>On</p>", withoutArticleTitle("<p>On</p>", "On tides and the moon"))
    }

    @Test
    fun `a title cut to its limit matches the full heading it was cut from`() {
        val full = "a".repeat(600)
        assertEquals("<p>Body</p>", withoutArticleTitle("<h2>$full</h2><p>Body</p>", full.take(500)))
        val short = "a".repeat(400)
        assertEquals("<h2>$full</h2>", withoutArticleTitle("<h2>$full</h2>", short))
    }
}
