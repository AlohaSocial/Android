// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

/**
 * A single pass over the characters, producing tags and text. Deliberately small: Mastodon's status
 * content is a restricted subset, and anything this does not understand is stripped to its text
 * rather than being an error. It never throws and never loops: every token consumes at least one
 * character.
 */
internal class HtmlTokenizer(private val source: String) {
    sealed interface Token {
        data class Text(val text: String) : Token

        data class OpenTag(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean) : Token

        data class CloseTag(val name: String) : Token
    }

    private var index = 0

    private val atEnd: Boolean get() = index >= source.length

    private fun peek(): Char? = source.getOrNull(index)

    private fun takeWhile(predicate: (Char) -> Boolean): String {
        val start = index
        while (!atEnd && predicate(source[index])) index++
        return source.substring(start, index)
    }

    private fun skipPast(marker: String) {
        val end = source.indexOf(marker, index)
        index = if (end < 0) source.length else end + marker.length
    }

    fun next(): Token? = when {
        atEnd -> null

        peek() != '<' -> Token.Text(HtmlEntities.decode(takeWhile { it != '<' }))

        // comments and doctypes are skipped whole
        source.startsWith("<!--", index) -> Token.Text("").also { skipPast("-->") }

        source.startsWith("<!", index) -> Token.Text("").also { skipPast(">") }

        // a stray `<` that is not a tag is text, which is what a server that failed to escape one leaves
        else -> readTag() ?: Token.Text("<").also { index++ }
    }

    /** At a `<`: a tag, or null with the position unchanged when no tag name follows. */
    private fun readTag(): Token? {
        val start = index++
        val closing = peek() == '/'
        if (closing) index++
        val name = takeWhile(Char::isLetterOrDigit).lowercase()
        if (name.isEmpty()) index = start
        return name.takeIf { it.isNotEmpty() }?.let {
            val (attributes, selfClosing) = readAttributes()
            if (closing) Token.CloseTag(it) else Token.OpenTag(it, attributes, selfClosing)
        }
    }

    /** Up to and past the closing `>`: the attributes, and whether a `/` made the tag self-closing. */
    private fun readAttributes(): Pair<Map<String, String>, Boolean> {
        val attributes = mutableMapOf<String, String>()
        var selfClosing = false
        while (!atEnd && peek() != '>') {
            if (peek() == '/') selfClosing = true
            if (peek() == '/' || peek()!!.isWhitespace()) index++ else readAttributeInto(attributes)
        }
        if (!atEnd) index++
        return attributes to selfClosing
    }

    /** `key`, `key=value` or `key="value"`; a character that starts none of them is skipped. */
    private fun readAttributeInto(attributes: MutableMap<String, String>) {
        val key = takeWhile { !it.isWhitespace() && it != '=' && it != '>' && it != '/' }.lowercase()
        if (key.isEmpty()) {
            index++
            return
        }
        takeWhile(Char::isWhitespace)
        val hasValue = peek() == '='
        if (hasValue) {
            index++
            takeWhile(Char::isWhitespace)
        }
        attributes[key] = if (hasValue) HtmlEntities.decode(readValue()) else ""
    }

    private fun readValue(): String {
        val quote = peek()?.takeIf { it == '"' || it == '\'' } ?: return takeWhile { !it.isWhitespace() && it != '>' }
        index++
        return takeWhile { it != quote }.also { if (!atEnd) index++ }
    }
}

internal object HtmlEntities {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "middot" to "·", "bull" to "•", "copy" to "©",
        "reg" to "®", "trade" to "™", "deg" to "°", "euro" to "€", "pound" to "£", "laquo" to "«",
        "raquo" to "»", "times" to "×", "divide" to "÷", "shy" to "", "zwj" to "‍",
    )

    /** An entity is short; an `&` with no `;` within this many characters is a literal ampersand. */
    private const val MAX_ENTITY = 12
    private const val HEX = 16

    fun decode(text: String): String {
        if ('&' !in text) return text
        val result = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val semicolon = if (text[i] == '&') semicolonNear(text, i) else null
            if (semicolon == null) {
                result.append(text[i++])
            } else {
                val body = text.substring(i + 1, semicolon)
                result.append(resolve(body) ?: "&$body;")
                i = semicolon + 1
            }
        }
        return result.toString()
    }

    /** The `;` closing an entity that starts at [ampersand], looked for only within the entity window. */
    private fun semicolonNear(text: String, ampersand: Int): Int? =
        (ampersand + 1 until minOf(text.length, ampersand + MAX_ENTITY)).firstOrNull { text[it] == ';' }

    private fun resolve(body: String): String? = named[body.lowercase()] ?: body.removePrefix("#").takeIf { it != body }
        ?.let { digits -> if (digits.startsWith("x", true)) digits.drop(1).toIntOrNull(HEX) else digits.toIntOrNull() }
        ?.takeIf(::isCharacter)
        ?.let { String(Character.toChars(it)) }

    // a surrogate or out-of-range number is no character; the entity stays as written
    private fun isCharacter(codePoint: Int) =
        codePoint in 0..Character.MAX_CODE_POINT && codePoint !in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code
}
