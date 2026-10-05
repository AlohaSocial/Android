// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import java.time.Instant
import social.aloha.core.html.RichTextCache
import social.aloha.core.html.StatusHtmlParser
import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.CustomEmoji
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.Poll
import social.aloha.core.model.Reaction
import social.aloha.core.model.Status
import social.aloha.core.model.StatusPlace
import social.aloha.core.model.VideoDetails
import social.aloha.core.model.Visibility

/**
 * Everything a status row draws, built off the main thread by [StatusRowMapper] so a row never parses
 * or renders text while scrolling. [statusId] is the post shown, a boost's target; [rowId] is the entry
 * in the timeline, the boost itself.
 */
@Immutable
public data class StatusRowUi(
    val rowId: String,
    val statusId: String,
    val url: String?,
    val context: ContextLine?,
    val author: AuthorUi,
    val createdAt: Instant,
    val edited: Boolean,
    val visibility: Visibility,
    val spoiler: AnnotatedString?,
    val body: AnnotatedString,
    val plainText: String,
    val emojis: List<CustomEmoji>,
    val media: List<MediaAttachment>,
    val sensitive: Boolean,
    val poll: Poll?,
    val card: Card?,
    val quote: QuoteUi?,
    val quoteWithdrawn: Boolean,
    val place: StatusPlace?,
    val archived: Boolean,
    val reactions: List<Reaction>,
    val counts: Counts,
    val state: State,
    val filterWarning: List<String>?,
    val isOwn: Boolean,
    val language: String?,
    /** What the server knows about the post's video beyond the file: its title, length, views. */
    val video: VideoDetails? = null,
    /** What a quote of this post comes to: one, a request, nothing, or a link where quotes are unknown. */
    val quoteAccess: QuoteAccess = QuoteAccess.Link,
    /** The words a filter matched, painted once the reader shows the filtered post anyway. */
    val filterMatches: List<String> = emptyList(),
) {
    @Immutable
    public data class AuthorUi(
        val id: String,
        val name: AnnotatedString,
        val plainName: String,
        val handle: String,
        val avatarUrl: String?,
        val bot: Boolean,
    )

    @Immutable
    public data class QuoteUi(
        val statusId: String,
        val author: AuthorUi,
        val createdAt: Instant,
        val spoiler: String?,
        val excerpt: AnnotatedString,
        val firstMedia: MediaAttachment?,
    )

    public data class Counts(val replies: Int, val boosts: Int, val favourites: Int, val dislikes: Int)

    public data class State(
        val boosted: Boolean,
        val favourited: Boolean,
        val bookmarked: Boolean,
        val pinned: Boolean,
        val muted: Boolean,
    )

    /** The one line above a post that says why it is here; never two. */
    @Immutable
    public sealed interface ContextLine {
        /** A boost: who boosted, when, and the reply line of the boosted post where it is a reply. */
        public data class BoostedBy(
            val name: String,
            val avatarUrl: String? = null,
            val at: Instant? = null,
            val reply: ContextLine? = null,
        ) : ContextLine

        public data object Pinned : ContextLine

        /** A reply to oneself: a thread continued. Mastodon omits the self-mention, so it is not "replying to". */
        public data object ContinuedThread : ContextLine

        public data class ReplyingTo(val handle: String) : ContextLine

        /** A reply to someone the post does not name. */
        public data object Replying : ContextLine
    }
}

/**
 * Builds [StatusRowUi] from a status. Pure apart from the parse cache, so a timeline maps a page on a
 * background dispatcher and hands the list finished rows.
 *
 * @param viewerAccountId the reading account's id on its server, to tell its own posts.
 */
public class StatusRowMapper(private val cache: RichTextCache, private val colors: RichTextColors) {
    public fun map(
        status: Status,
        viewerAccountId: String?,
        filterWarning: List<String>? = null,
        showContext: Boolean = true,
        filterMatches: List<String> = emptyList(),
    ): StatusRowUi {
        val shown = status.displayed
        val quoted = shown.quote?.quotedStatus
        return StatusRowUi(
            rowId = status.id,
            statusId = shown.id,
            url = shown.url ?: shown.uri.takeIf { it.isNotEmpty() },
            context = if (showContext) contextOf(status) else null,
            author = author(shown.account),
            createdAt = shown.createdAt,
            edited = shown.isEdited,
            visibility = shown.visibility,
            spoiler = cache.spoiler(status).takeUnless { it.isEmpty }?.toAnnotatedString(colors),
            body = cache.richText(status).toAnnotatedString(colors),
            plainText = cache.plainText(status),
            emojis = shown.emojis + shown.account.emojis,
            media = shown.mediaAttachments,
            sensitive = shown.sensitive,
            poll = shown.poll,
            // a card is the preview of a link; next to media it would repeat what the post already shows
            card = shown.card?.takeIf { it.url != null && shown.mediaAttachments.isEmpty() },
            quote = quoted?.let(::quote),
            quoteWithdrawn = shown.quote?.state in WITHDRAWN,
            place = shown.place,
            archived = shown.archived == true,
            reactions = shown.reactions.orEmpty(),
            counts = countsOf(shown),
            state = StatusRowUi.State(shown.reblogged, shown.favourited, shown.bookmarked, shown.pinned, shown.muted),
            filterWarning = filterWarning,
            isOwn = viewerAccountId != null && shown.account.id == viewerAccountId,
            language = shown.language,
            video = shown.video,
            filterMatches = filterMatches,
            quoteAccess = quoteAccess(shown, viewerAccountId != null && shown.account.id == viewerAccountId),
        )
    }

    private fun contextOf(status: Status): StatusRowUi.ContextLine? {
        val shown = status.displayed
        return when {
            status.booster != null -> StatusRowUi.ContextLine.BoostedBy(
                status.account.bestDisplayName,
                status.account.avatar,
                status.createdAt,
                replyOf(shown),
            )

            shown.pinned -> StatusRowUi.ContextLine.Pinned

            else -> replyOf(shown)
        }
    }

    private fun replyOf(shown: Status): StatusRowUi.ContextLine? {
        val replyTo = shown.inReplyToAccountId ?: return null
        if (replyTo == shown.account.id) return StatusRowUi.ContextLine.ContinuedThread
        val mention = shown.mentions.firstOrNull { it.id == replyTo }
        return mention?.let { StatusRowUi.ContextLine.ReplyingTo("@${it.acct}") } ?: StatusRowUi.ContextLine.Replying
    }

    /** An account as a row draws it: its name with custom emoji, handle and avatar. */
    public fun author(account: Account): StatusRowUi.AuthorUi = StatusRowUi.AuthorUi(
        id = account.id,
        name = StatusHtmlParser.parseText(account.bestDisplayName, account.emojis).toAnnotatedString(colors),
        plainName = account.bestDisplayName,
        handle = account.qualifiedHandle,
        avatarUrl = account.avatar,
        bot = account.bot,
    )

    private fun quote(status: Status) = StatusRowUi.QuoteUi(
        statusId = status.id,
        author = author(status.account),
        createdAt = status.createdAt,
        spoiler = status.spoilerText.takeIf { status.hasContentWarning },
        excerpt = cache.richText(status).toAnnotatedString(colors),
        firstMedia = status.mediaAttachments.firstOrNull(),
    )

    private companion object {
        val WITHDRAWN = setOf("revoked", "rejected")
    }
}

/** What quoting a post comes to for the reader. */
public enum class QuoteAccess {
    /** The quote is made at once. */
    Quote,

    /** The author approves each quote, so it goes as a request. */
    Request,

    /** The author allows no quote, or none from the reader. */
    Denied,

    /** The server says nothing of quotes: a link to the post stands in. */
    Link,
}

/**
 * What quoting [shown] comes to: the server's answer for the reader where it gives one, else the post's
 * policy; a direct post is never quoted, and the reader may always quote their own.
 */
internal fun quoteAccess(shown: Status, own: Boolean): QuoteAccess = when {
    shown.visibility == Visibility.Direct -> QuoteAccess.Denied
    own -> QuoteAccess.Quote
    shown.quoteApproval == "automatic" -> QuoteAccess.Quote
    shown.quoteApproval == "manual" -> QuoteAccess.Request
    shown.quoteApproval == "denied" || shown.quoteApprovalPolicy == "nobody" -> QuoteAccess.Denied
    shown.quoteApproval != null || shown.quoteApprovalPolicy != null -> QuoteAccess.Quote
    else -> QuoteAccess.Link
}

private fun countsOf(shown: Status) =
    StatusRowUi.Counts(shown.repliesCount, shown.reblogsCount, shown.favouritesCount, shown.dislikesCount)
