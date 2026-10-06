// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityShieldTest {
    // what a rewrite may meet: handles local and remote, tags in any script, links with queries and fragments
    private val corpus = listOf(
        "Hey @alice@example.social, see #SwiftUI and https://example.test/a?b=1",
        "Talk to @bob about #plans.",
        "@carol.d@mastodon.example: #café #東京 #2026 https://example.test/x#frag, then @dave_e",
        "Line one\nhttp://plain.example/ and #a-b-c\n@e@f.example",
        "Just some ordinary words.",
        "Two links: https://one.example and https://two.example/path?x=1&y=2.",
        "Mail me at someone@example.test about #mail",
        "Grüße an @a@münchen.social, see HTTPS://EXAMPLE.test/X and #foo·bar",
    )

    @Test
    fun `every mention, hashtag and link survives a round trip, and none stays visible to the model`() {
        corpus.forEach { original ->
            val shield = EntityShield()
            val masked = shield.mask(original)
            assertFalse(
                original,
                masked.contains("http", ignoreCase = true) || Regex("""[@#]\p{L}""").containsMatchIn(masked),
            )
            assertTrue(original, shield.isIntact(masked))
            assertEquals(original, shield.restore(masked))
        }
    }

    @Test
    fun `a rewrite that drops, doubles or invents a token is caught`() {
        val shield = EntityShield()
        val masked = shield.mask("Talk to @bob about #plans")
        assertFalse(shield.isIntact(masked.replace("⟦0⟧", "someone")))
        assertFalse(shield.isIntact("$masked ⟦0⟧"))
        assertFalse(shield.isIntact("$masked ⟦7⟧"))
    }

    @Test
    fun `a draft with brackets like the tokens is not masked at all`() {
        assertFalse(EntityShield().accepts("Mind the ⟦0⟧ in @bob"))
    }

    @Test
    fun `the entities of a text are the same however they are ordered`() {
        assertEquals(EntityShield.entities("#a @b"), EntityShield.entities("@b, then #a"))
    }

    @Test
    fun `text with nothing to protect passes through unchanged`() {
        val shield = EntityShield()
        assertEquals("Just some ordinary words.", shield.mask("Just some ordinary words."))
        assertTrue(shield.isIntact("Other words entirely."))
    }
}
