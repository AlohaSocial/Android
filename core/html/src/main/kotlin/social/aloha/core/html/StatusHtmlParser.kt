// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.Mention
import social.aloha.core.model.StatusTag

/**
 * Turns a status's restricted HTML into [RichText]. Hand-written, because `Html.fromHtml` builds
 * spans the renderer would have to take apart again and cannot tell a mention from a link. Stateless
 * between calls, so it runs on whatever dispatcher the caller is on.
 */
public object StatusHtmlParser {
    public fun parse(
        html: String,
        mentions: List<Mention> = emptyList(),
        tags: List<StatusTag> = emptyList(),
        emojis: List<CustomEmoji> = emptyList(),
    ): RichText {
        val builder = Builder(mentions, tags, emojis)
        generateSequence(HtmlTokenizer(html)::next).forEach(builder::accept)
        return builder.finish()
    }

    /** Plain text only, for what never needs styling: a search index, a notification body. */
    public fun plainText(html: String): String = parse(html).plainText

    /**
     * Text that is not markup but may carry custom emoji: a content warning, a display name. A `<` in it
     * is a character to show, so it is escaped before the emoji are resolved.
     */
    public fun parseText(text: String, emojis: List<CustomEmoji> = emptyList()): RichText =
        if (text.isEmpty()) RichText.Empty else parse("<p>${escape(text)}</p>", emojis = emojis)

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private class Builder(mentions: List<Mention>, tags: List<StatusTag>, emojis: List<CustomEmoji>) {
    private val links = LinkResolver(mentions, tags)
    private val emojiByShortcode = emojis.associateBy { it.shortcode }
    private val runs = mutableListOf<RichText.Run>()
    private val blocks = mutableListOf<RichText.Block>()
    private val plain = StringBuilder()
    private val styleStack = ArrayDeque<RichText.Style>()
    private val linkStack = ArrayDeque<RichText.Link?>()
    private var blockStart = 0
    private var blockKind: RichText.Block.Kind = RichText.Block.Kind.Paragraph
    private val listCounters = ArrayDeque<Int>()
    private val orderedStack = ArrayDeque<Boolean>()

    private val style: RichText.Style get() = styleStack.fold(RichText.Style.None) { all, next -> all + next }
    private val link: RichText.Link? get() = linkStack.lastOrNull()

    fun accept(token: HtmlTokenizer.Token) {
        when (token) {
            is HtmlTokenizer.Token.Text -> append(token.text)

            is HtmlTokenizer.Token.OpenTag -> {
                open(token.name, token.attributes)
                if (token.selfClosing) close(token.name)
            }

            is HtmlTokenizer.Token.CloseTag -> close(token.name)
        }
    }

    // Emoji resolve within one text run, so a shortcode split across tags is not an emoji, which is
    // also how the server's own renderer sees it.
    private fun append(text: String) {
        if (text.isEmpty()) return
        EmojiSplitter.split(text, emojiByShortcode).mapNotNull(::runOf).forEach(::addRun)
    }

    private fun runOf(piece: EmojiSplitter.Piece): RichText.Run? = when (piece) {
        is EmojiSplitter.Piece.Text -> piece.text.takeIf { it.isNotEmpty() }?.let { RichText.Run(it, style, link) }
        is EmojiSplitter.Piece.Emoji -> RichText.Run(":${piece.emoji.shortcode}:", style, link, piece.emoji)
    }

    private fun addRun(run: RichText.Run) {
        runs += run
        plain.append(run.text)
    }

    private fun open(tag: String, attributes: Map<String, String>) {
        when (tag) {
            "br" -> addRun(RichText.Run("\n", style))

            "p", "div" -> closeBlock(RichText.Block.Kind.Paragraph)

            "blockquote" -> openBlock(RichText.Style.Quote, RichText.Block.Kind.Blockquote)

            "pre" -> openBlock(RichText.Style.Code, RichText.Block.Kind.CodeBlock)

            "strong", "b" -> styleStack.addLast(RichText.Style.Bold)

            "em", "i" -> styleStack.addLast(RichText.Style.Italic)

            "del", "s" -> styleStack.addLast(RichText.Style.Strikethrough)

            "code" -> styleStack.addLast(RichText.Style.Code)

            "ul", "ol" -> openList(ordered = tag == "ol")

            "li" -> openListItem()

            "a" -> linkStack.addLast(links.resolve(attributes))

            // Mastodon wraps the `@` and the domain of a mention in `invisible` / `ellipsis` spans. Both
            // are presentation and their text is kept: dropping the domain would make two Alices one.
            "span" -> linkStack.addLast(link)
        }
    }

    private fun close(tag: String) {
        when (tag) {
            "p", "div" -> closeBlock(RichText.Block.Kind.Paragraph)

            "blockquote", "pre" -> {
                styleStack.removeLastOrNull()
                closeBlock(blockKind)
                blockKind = RichText.Block.Kind.Paragraph
            }

            "strong", "b", "em", "i", "del", "s", "code" -> styleStack.removeLastOrNull()

            "ul", "ol" -> {
                orderedStack.removeLastOrNull()
                listCounters.removeLastOrNull()
                closeBlock(blockKind)
                blockKind = RichText.Block.Kind.Paragraph
            }

            "li" -> {
                closeBlock(blockKind)
                blockKind = RichText.Block.Kind.Paragraph
            }

            "a", "span" -> linkStack.removeLastOrNull()
        }
    }

    private fun openBlock(style: RichText.Style, kind: RichText.Block.Kind) {
        closeBlock(RichText.Block.Kind.Paragraph)
        styleStack.addLast(style)
        blockKind = kind
    }

    private fun openList(ordered: Boolean) {
        closeBlock(RichText.Block.Kind.Paragraph)
        orderedStack.addLast(ordered)
        listCounters.addLast(0)
    }

    private fun openListItem() {
        closeBlock(blockKind)
        if (listCounters.isNotEmpty()) listCounters.addLast(listCounters.removeLast() + 1)
        blockKind = RichText.Block.Kind.ListItem(orderedStack.lastOrNull() ?: false, listCounters.lastOrNull() ?: 1)
    }

    private fun closeBlock(next: RichText.Block.Kind) {
        if (runs.size > blockStart) {
            blocks += RichText.Block(blockKind, blockStart until runs.size)
            blockStart = runs.size
            if (!plain.endsWith("\n")) plain.append('\n')
        }
        blockKind = next
    }

    fun finish(): RichText {
        if (runs.size > blockStart) blocks += RichText.Block(blockKind, blockStart until runs.size)
        return RichText(runs.toList(), plain.toString().trim(), blocks.toList())
    }
}

/** Splits text around `:shortcode:` occurrences that name a known emoji. */
internal object EmojiSplitter {
    sealed interface Piece {
        data class Text(val text: String) : Piece

        data class Emoji(val emoji: CustomEmoji) : Piece
    }

    /** A shortcode is short; looking further would make a line of prose with two colons a linear search each. */
    private const val MAX_SHORTCODE = 64

    fun split(text: String, emojis: Map<String, CustomEmoji>): List<Piece> {
        if (emojis.isEmpty() || ':' !in text) return listOf(Piece.Text(text))
        val pieces = mutableListOf<Piece>()
        var textStart = 0
        var colon = text.indexOf(':')
        while (colon >= 0) {
            val found = emojiAt(text, colon, emojis)
            if (found != null) {
                pieces.addText(text, textStart, colon)
                pieces += Piece.Emoji(found.first)
                textStart = found.second
            }
            colon = text.indexOf(':', found?.second ?: (colon + 1))
        }
        pieces.addText(text, textStart, text.length)
        return pieces.ifEmpty { listOf(Piece.Text("")) }
    }

    private fun MutableList<Piece>.addText(text: String, start: Int, end: Int) {
        if (end > start) add(Piece.Text(text.substring(start, end)))
    }

    /** The emoji whose `:shortcode:` opens at [colon], and the index just past it; null when none does. */
    private fun emojiAt(text: String, colon: Int, emojis: Map<String, CustomEmoji>): Pair<CustomEmoji, Int>? {
        val closing = (colon + 1 until minOf(text.length, colon + 1 + MAX_SHORTCODE)).firstOrNull { text[it] == ':' }
        return closing?.let { end -> emojis[text.substring(colon + 1, end)]?.let { it to end + 1 } }
    }
}
