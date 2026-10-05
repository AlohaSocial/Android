// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.intelligence

/**
 * Keeps mentions, hashtags and links out of a rewrite's reach: each is swapped for a token before the
 * model sees the text and put back after. A rewrite that loses a mention sends the post to the wrong
 * person, and one that adds a mention sends it to someone new, so a rewrite whose entities differ from
 * the draft's in any way is rejected.
 */
internal class EntityShield {
    private val kept = LinkedHashMap<String, String>()

    /** Whether [text] can be masked at all: brackets like the tokens' in it could not be told from them. */
    fun accepts(text: String): Boolean = OPEN !in text && CLOSE !in text

    fun mask(text: String): String = PATTERNS.fold(text) { masked, pattern ->
        pattern.replace(masked) { found ->
            val token = "$OPEN${kept.size}$CLOSE"
            kept[token] = found.value
            token
        }
    }

    /** Whether every token came back, once each, and no token was made up. */
    fun isIntact(text: String): Boolean =
        kept.keys.all { text.split(it).size == 2 } && TOKEN.findAll(text).count() == kept.size

    fun restore(text: String): String = kept.entries.fold(text) { restored, (token, original) ->
        restored.replace(token, original)
    }

    companion object {
        private const val OPEN = '⟦'
        private const val CLOSE = '⟧'

        private val PATTERNS = listOf(
            Regex("""https?://\S+""", RegexOption.IGNORE_CASE),
            Regex("""@[\p{L}\p{N}_.-]+(@[\p{L}\p{N}.-]+)?"""),
            Regex("""#[\p{L}\p{N}_·‌-]+"""),
        )
        private val TOKEN = Regex("""$OPEN\d+$CLOSE""")

        /** The mentions, hashtags and links in [text], as the shield finds them, sorted. */
        fun entities(text: String): List<String> =
            PATTERNS.fold(text to emptyList<String>()) { (rest, found), pattern ->
                pattern.replace(rest, " ") to found + pattern.findAll(rest).map { it.value }
            }.second.sorted()
    }
}
