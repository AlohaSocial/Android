// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp
import java.net.URLEncoder
import social.aloha.core.html.RichText

/** The colours a rendered status body uses; read from the theme once per row, not per run. */
@Immutable
public data class RichTextColors(val link: Color, val quote: Color, val codeBackground: Color) {
    public companion object {
        @Composable
        public fun fromTheme(): RichTextColors = with(MaterialTheme.colorScheme) {
            RichTextColors(link = primary, quote = onSurfaceVariant, codeBackground = surfaceContainerHigh)
        }
    }
}

/**
 * Renders [RichText] as an [AnnotatedString]. Pure, so the timeline builds it off the main thread and a
 * row never parses or renders while scrolling. Every link is a [LinkAnnotation.Url]: web links carry
 * their own address, mentions and hashtags an `aloha:` one that [RichLinkTarget.parse] turns back into
 * a destination, so the row's `UriHandler` can route them in-app. Each custom emoji is an inline
 * content slot named by [emojiSlot], with its `:shortcode:` as the text a screen reader speaks.
 */
public fun RichText.toAnnotatedString(colors: RichTextColors): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = colors.link))
    val covered = BooleanArray(runs.size)
    blocks.forEachIndexed { number, block ->
        if (number > 0) append('\n')
        withParagraph(block.kind, colors) {
            listPrefix(block.kind)?.let(::append)
            block.range.forEach { index ->
                covered[index] = true
                appendRun(runs[index], colors, linkStyles)
            }
        }
    }
    // runs outside any block, which a parser that closes every block never leaves, are still shown
    runs.forEachIndexed { index, run -> if (!covered[index]) appendRun(run, colors, linkStyles) }
}

/** The inline content id a custom emoji is drawn into. */
public fun emojiSlot(shortcode: String): String = "emoji:$shortcode"

private fun AnnotatedString.Builder.withParagraph(
    kind: RichText.Block.Kind,
    colors: RichTextColors,
    content: AnnotatedString.Builder.() -> Unit,
) {
    val paragraph = when (kind) {
        is RichText.Block.Kind.ListItem -> ParagraphStyle(
            textIndent = TextIndent(firstLine = 0.sp, restLine = LIST_INDENT),
        )

        RichText.Block.Kind.Blockquote -> ParagraphStyle(textIndent = TextIndent(QUOTE_INDENT, QUOTE_INDENT))

        RichText.Block.Kind.CodeBlock, RichText.Block.Kind.Paragraph -> null
    }
    val start = length
    content()
    paragraph?.let { addStyle(it, start, length) }
    if (kind == RichText.Block.Kind.Blockquote) addStyle(SpanStyle(color = colors.quote), start, length)
}

private fun listPrefix(kind: RichText.Block.Kind): String? =
    (kind as? RichText.Block.Kind.ListItem)?.let { if (it.ordered) "${it.index}. " else "• " }

private fun AnnotatedString.Builder.appendRun(run: RichText.Run, colors: RichTextColors, linkStyles: TextLinkStyles) {
    val target = run.link?.let(RichLinkTarget::uriFor)
    target?.let { pushLink(LinkAnnotation.Url(it, linkStyles)) }
    val span = run.style.toSpanStyle(colors)
    span?.let { pushStyle(it) }
    val emoji = run.emoji
    if (emoji != null) appendInlineContent(emojiSlot(emoji.shortcode), run.text) else append(run.text)
    span?.let { pop() }
    target?.let { pop() }
}

private fun RichText.Style.toSpanStyle(colors: RichTextColors): SpanStyle? {
    if (this == RichText.Style.None) return null
    val code = RichText.Style.Code in this
    return SpanStyle(
        fontWeight = if (RichText.Style.Bold in this) FontWeight.Bold else null,
        fontStyle = if (RichText.Style.Italic in this) FontStyle.Italic else null,
        textDecoration = if (RichText.Style.Strikethrough in this) TextDecoration.LineThrough else null,
        fontFamily = if (code) FontFamily.Monospace else null,
        background = if (code) colors.codeBackground else Color.Unspecified,
    )
}

/** Where a tapped link in a status body leads. */
public sealed interface RichLinkTarget {
    public data class Mention(val accountId: String?, val acct: String) : RichLinkTarget

    public data class Hashtag(val name: String) : RichLinkTarget

    public data class Web(val url: String) : RichLinkTarget

    public companion object {
        private const val MENTION = "aloha:mention"
        private const val HASHTAG = "aloha:tag"

        internal fun uriFor(link: RichText.Link): String = when (link) {
            is RichText.Link.Mention -> "$MENTION?acct=${encode(link.acct)}" +
                link.accountId?.let { "&id=${encode(it)}" }.orEmpty()

            is RichText.Link.Hashtag -> "$HASHTAG?name=${encode(link.name)}"

            is RichText.Link.Web -> link.url
        }

        /** The destination of a link annotation this renderer wrote; anything else is a web address. */
        public fun parse(uri: String): RichLinkTarget {
            val query = uri.substringAfter('?', "").split('&').associate {
                it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8.name())
            }
            return when {
                uri.startsWith("$MENTION?") -> Mention(query["id"], query["acct"].orEmpty())
                uri.startsWith("$HASHTAG?") -> Hashtag(query["name"].orEmpty())
                else -> Web(uri)
            }
        }

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}

private val LIST_INDENT = 16.sp
private val QUOTE_INDENT = 12.sp
