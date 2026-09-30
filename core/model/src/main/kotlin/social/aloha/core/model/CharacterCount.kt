// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.text.BreakIterator

/** How a server measures a post against its `max_characters`. */
public enum class LengthRule {
    /**
     * Mastodon's: grapheme clusters, the unit a person counts, so an emoji is one; a URL costs the
     * flat `characters_reserved_per_url` however long it is; a remote mention costs only its `@user`.
     */
    Mastodon,

    /**
     * Nextcloud Social's: Unicode code points, everything at its full length. It advertises a URL
     * cost too but does not apply it, so a client charging the flat rate lets through a post the
     * server refuses.
     */
    CodePoints,
}

/**
 * Character counting in one place, by the rule of the server the post goes to. The content warning
 * counts against the same budget as the body on every server.
 */
public object CharacterCount {
    /** What the server's own linkifier treats as a URL. */
    private val urlPattern = Regex("""https?://[^\s<]+""")

    /** A mention of someone on another server: `@user@domain`, not preceded by a word or a slash. */
    private val remoteMention = Regex("""(?<![\w/])(@\w+)@[\w.-]*\w""")

    public fun count(
        text: String,
        spoilerText: String,
        limits: ServerLimits,
        rule: LengthRule = LengthRule.Mastodon,
    ): Int = when (rule) {
        LengthRule.Mastodon -> weighted(text, limits) + graphemes(spoilerText)
        LengthRule.CodePoints -> codePoints(text) + codePoints(spoilerText)
    }

    public fun remaining(
        text: String,
        spoilerText: String,
        limits: ServerLimits,
        rule: LengthRule = LengthRule.Mastodon,
    ): Int = limits.maxStatusCharacters - count(text, spoilerText, limits, rule)

    /** The body's cost on Mastodon: each URL at the flat rate, each remote mention by its `@user`. */
    private fun weighted(text: String, limits: ServerLimits): Int {
        val urls = urlPattern.findAll(text).map { it.value }.toList()
        val withoutUrls = urlPattern.replace(text, "")
        val shortened = remoteMention.replace(withoutUrls) { it.groupValues[1] }
        return graphemes(shortened) + urls.size * limits.charactersReservedPerUrl
    }

    private fun codePoints(text: String): Int = text.codePointCount(0, text.length)

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
