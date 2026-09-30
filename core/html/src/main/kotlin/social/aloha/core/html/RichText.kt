// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.html

import social.aloha.core.model.CustomEmoji

/**
 * A parsed status body. Carries both the styled runs and a plain-text projection: the latter is what
 * search, notifications and screen readers read, and building it twice would be two chances to
 * disagree.
 *
 * @property blocks let a renderer lay out paragraphs and lists without walking the runs again.
 */
public data class RichText(val runs: List<Run>, val plainText: String, val blocks: List<Block>) {
    val isEmpty: Boolean get() = plainText.isBlank()

    val mentions: List<String> get() = runs.mapNotNull { (it.link as? Link.Mention)?.acct }

    val hashtags: List<String> get() = runs.mapNotNull { (it.link as? Link.Hashtag)?.name }

    val webLinks: List<String> get() = runs.mapNotNull { (it.link as? Link.Web)?.url }

    /** @property emoji set where the run is a custom emoji standing in for `:shortcode:`. */
    public data class Run(
        val text: String,
        val style: Style = Style.None,
        val link: Link? = null,
        val emoji: CustomEmoji? = null,
    )

    /** A set of text styles, as bits so a run costs no allocation for it. */
    @JvmInline
    public value class Style(private val bits: Int) {
        public operator fun contains(other: Style): Boolean = bits and other.bits == other.bits

        public operator fun plus(other: Style): Style = Style(bits or other.bits)

        public companion object {
            public val None: Style = Style(0)
            public val Bold: Style = Style(1 shl 0)
            public val Italic: Style = Style(1 shl 1)
            public val Strikethrough: Style = Style(1 shl 2)
            public val Code: Style = Style(1 shl 3)
            public val Quote: Style = Style(1 shl 4)
        }
    }

    /** Where a link goes, already resolved against the status's `mentions` and `tags`, so a tap routes in-app. */
    public sealed interface Link {
        public data class Mention(val accountId: String?, val acct: String, val url: String?) : Link

        public data class Hashtag(val name: String, val url: String?) : Link

        /** Only `http` and `https`: a server-supplied `javascript:` or `intent:` href is never a link. */
        public data class Web(val url: String) : Link
    }

    /** @property range indices into [runs]. */
    public data class Block(val kind: Kind, val range: IntRange) {
        public sealed interface Kind {
            public data object Paragraph : Kind

            public data class ListItem(val ordered: Boolean, val index: Int) : Kind

            public data object Blockquote : Kind

            public data object CodeBlock : Kind
        }
    }

    public companion object {
        public val Empty: RichText = RichText(emptyList(), "", emptyList())
    }
}
