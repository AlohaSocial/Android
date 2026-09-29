// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.text.BreakIterator

/**
 * Mastodon's character counting in one place. A URL costs a fixed number of characters however long
 * it is, and the content warning counts against the same budget as the body. Everything is counted in
 * grapheme clusters, the unit a person counts, never in UTF-16 units, so an emoji counts as one.
 */
public object CharacterCount {
    /** What the server's own linkifier treats as a URL. */
    private val urlPattern = Regex("""https?://[^\s<]+""")

    public fun count(text: String, spoilerText: String, limits: ServerLimits): Int =
        weighted(text, limits) + graphemes(spoilerText)

    public fun remaining(text: String, spoilerText: String, limits: ServerLimits): Int =
        limits.maxStatusCharacters - count(text, spoilerText, limits)

    /** The body's cost, with each URL charged the flat rate. */
    private fun weighted(text: String, limits: ServerLimits): Int {
        val urls = urlPattern.findAll(text).map { it.value }.toList()
        return graphemes(text) - urls.sumOf(::graphemes) + urls.size * limits.charactersReservedPerUrl
    }

    /** The number of user-perceived characters in [text]. */
    public fun graphemes(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    /** The first [maximum] user-perceived characters of [text], so an emoji is never cut in half. */
    public fun prefix(text: String, maximum: Int): String {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var end = 0
        repeat(maximum) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) return text
            end = next
        }
        return text.substring(0, end)
    }
}
