// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CharacterCountTest {
    private val limits = ServerLimits.MastodonDefaults.copy(maxStatusCharacters = 500, charactersReservedPerUrl = 23)

    private fun count(text: String, spoiler: String = "") = CharacterCount.count(text, spoiler, limits)

    @Test
    fun `plain text counts as itself`() {
        assertEquals(5, count("Hello"))
    }

    @Test
    fun `a url costs the flat rate however long it is`() {
        val short = "See https://a.test"
        val long = "See https://example.test/a/very/long/path?with=query&more=parameters#fragment"
        assertEquals(count(short), count(long))
        assertEquals(27, count(short))
    }

    @Test
    fun `emoji and non-BMP characters count as one each`() {
        assertEquals(1, count("👋"))
        assertEquals(1, count("👨‍👩‍👧‍👦"))
        assertEquals(25, count("👋 https://example.test/x"))
    }

    @Test
    fun `the content warning shares the body's budget`() {
        assertEquals(7, count("Hello", spoiler = "CW"))
        assertEquals(493, CharacterCount.remaining("Hello", "CW", limits))
    }

    @Test
    fun `several urls each cost the flat rate`() {
        assertEquals(47, count("https://a.test https://b.test"))
    }

    @Test
    fun `over-length is a negative remainder`() {
        assertEquals(-100, CharacterCount.remaining("a".repeat(600), "", limits))
    }

    @Test
    fun `counting holds at the nextcloud ceiling`() {
        val nextcloud = limits.copy(maxStatusCharacters = 5000)
        assertEquals(0, CharacterCount.remaining("é".repeat(5000), "", nextcloud))
    }
}
