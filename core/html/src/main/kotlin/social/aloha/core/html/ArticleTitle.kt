// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

/** A heading or paragraph that opens the content, as Aloha Social checks for the title it prepends. */
private val FIRST_BLOCK = Regex(
    """^\s*<(h[1-6]|p)\b[^>]*>(.*?)</\1\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val WHITESPACE = Regex("""\s+""")

/** The length Aloha Social cuts a long-form title to, in characters. */
private const val TITLE_LIMIT = 500

/**
 * [content] without the copy of a long-form post's [title] that Aloha Social puts at its top as `<h2>`, or
 * that the post already opened with as a heading or paragraph. Content that does not open with the title,
 * once tags, entities and runs of whitespace are resolved, is returned as it is. A title cut to the limit
 * matches a block that starts with it.
 */
public fun withoutArticleTitle(content: String, title: String): String {
    val block = FIRST_BLOCK.find(content) ?: return content
    val text = StatusHtmlParser.plainText(block.groupValues[2]).replace(WHITESPACE, " ").trim()
    val wanted = title.replace(WHITESPACE, " ").trim()
    val cut = wanted.codePointCount(0, wanted.length) >= TITLE_LIMIT && text.startsWith(wanted)
    return if (wanted.isNotEmpty() && (text == wanted || cut)) content.substring(block.range.last + 1) else content
}
