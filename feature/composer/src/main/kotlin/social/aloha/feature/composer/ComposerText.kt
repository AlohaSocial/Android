// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** What the word at the cursor is being completed into. */
internal enum class CompletionKind(val prefix: Char) { Account('@'), Hashtag('#'), Emoji(':') }

/** The word being typed at the cursor, from its prefix up to the cursor, when it can be completed. */
internal data class Completing(val kind: CompletionKind, val query: String, val start: Int, val end: Int)

internal object ComposerText {
    private val mention = Regex("""(?<![\w/@])@\w+(?:@[\w.-]*\w)?""")
    private val hashtag = Regex("""(?<![\w/#&])#\w*[\p{L}_]\w*""")
    private val url = Regex("""https?://[^\s<]+""")
    private val whitespace = Regex("""\s+""")

    // the word being typed, up to the cursor: a prefix, then name characters (and a domain for @)
    private val typing = Regex("""(?:^|(?<=\s))([@#:])([\w.@-]*)$""")

    /** The ranges to colour: every mention, hashtag and link, the way the server will link them. */
    fun spans(text: String): List<IntRange> =
        (mention.findAll(text) + hashtag.findAll(text) + url.findAll(text)).map { it.range }.toList()

    /**
     * Where [text] goes past the limit: the end of its longest beginning that [fits]. A count that is not a
     * plain tally of characters, a link costing the same whatever its length, is still near enough here.
     */
    fun overFrom(text: String, fits: (String) -> Boolean): Int {
        var low = 0
        var high = text.length
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (fits(text.substring(0, middle))) low = middle else high = middle - 1
        }
        // never between the halves of a character outside the basic plane
        return if (low > 0 && text[low - 1].isHighSurrogate()) low - 1 else low
    }

    /** [text] without its mentions, hashtags and links: what reads as a language. */
    fun prose(text: String): String =
        spans(text).sortedByDescending { it.first }.fold(text) { left, range -> left.removeRange(range) }
            .replace(whitespace, " ").trim()

    /** The hashtags in [text], without their `#`, as the server will read them. */
    fun hashtags(text: String): List<String> = hashtag.findAll(text).map { it.value.drop(1) }.toList()

    /** The word at [value]'s cursor that a completion would replace, or null when there is none. */
    fun completing(value: TextFieldValue): Completing? {
        val cursor = value.selection.start
        val match = typing.find(value.text.substring(0, cursor)).takeIf { value.selection.collapsed } ?: return null
        val kind = CompletionKind.entries.first { it.prefix == match.groupValues[1][0] }
        val query = match.groupValues[2]
        // a colon on its own is punctuation, and a lone @ or # has nothing to complete yet
        val least = if (kind == CompletionKind.Emoji) MIN_EMOJI_QUERY else 1
        return Completing(kind, query, match.range.first, cursor).takeIf { query.length >= least }
    }

    /** [value] with [completing]'s word replaced by [replacement] and a space, the cursor after it. */
    fun complete(value: TextFieldValue, completing: Completing, replacement: String): TextFieldValue {
        val text = value.text
        val end = completing.end
        // an existing space after the word is kept rather than doubled
        val space = if (end < text.length && text[end] == ' ') "" else " "
        val updated = text.substring(0, completing.start) + replacement + space + text.substring(end)
        val cursor = completing.start + replacement.length + 1
        return TextFieldValue(updated, TextRange(cursor.coerceAtMost(updated.length)))
    }

    private const val MIN_EMOJI_QUERY = 2
}

/**
 * Colours mentions, hashtags and links as they are typed, and what is past the limit, from [overFrom], in
 * [over]; without changing a character.
 */
internal class HighlightTransformation(
    private val color: Color,
    private val over: SpanStyle,
    private val overFrom: Int?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val style = SpanStyle(color = color)
        val styled = AnnotatedString.Builder(text).apply {
            ComposerText.spans(text.text).forEach { addStyle(style, it.first, it.last + 1) }
            overFrom?.takeIf { it < text.length }?.let { addStyle(over, it, text.length) }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean =
        other is HighlightTransformation && other.color == color && other.over == over && other.overFrom == overFrom

    override fun hashCode(): Int = (color.hashCode() * HASH + over.hashCode()) * HASH + (overFrom ?: -1)

    private companion object {
        const val HASH = 31
    }
}
