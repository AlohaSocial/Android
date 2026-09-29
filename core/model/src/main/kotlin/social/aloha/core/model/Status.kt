// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * A post.
 *
 * @property content restricted HTML, parsed off the main thread and cached by content hash.
 * @property createdAt [Instant.EPOCH] when the server did not say.
 * @property visibility [Visibility.Unknown] when the server sent nothing or a value this app does not
 *   know, which is treated as the most restrictive, never as public.
 * @property poll present-or-null on Nextcloud Social; other servers omit it.
 * @property reactions emoji reactions where the server supports them. Nextcloud Social always sends
 *   an empty list here and serves the real reactions only from `/statuses/{id}/reactions`.
 * @property quoteApprovalPolicy who may quote this post (`public`, `followers`, `nobody`), where the
 *   server says.
 * @property archived off the profile, not deleted; null when the server does not say.
 * @property video the PeerTube-shaped facts Nextcloud Social carries on a video post.
 */
@Serializable
public data class Status(
    val id: String,
    val account: Account,
    val uri: String = "",
    val url: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.EPOCH,
    @Serializable(with = InstantSerializer::class) val editedAt: Instant? = null,
    val content: String = "",
    val spoilerText: String = "",
    val visibility: Visibility = Visibility.Public,
    val sensitive: Boolean = false,
    val language: String? = null,
    val repliesCount: Int = 0,
    val reblogsCount: Int = 0,
    val favouritesCount: Int = 0,
    val favourited: Boolean = false,
    val reblogged: Boolean = false,
    val bookmarked: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val inReplyToId: String? = null,
    val inReplyToAccountId: String? = null,
    val reblog: Status? = null,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    val mentions: List<Mention> = emptyList(),
    val tags: List<StatusTag> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
    val poll: Poll? = null,
    val card: Card? = null,
    val application: ApplicationSummary? = null,
    val text: String? = null,
    val filtered: List<FilterResult>? = null,
    val reactions: List<Reaction>? = null,
    val quoteId: String? = null,
    val quote: QuotedStatus? = null,
    val quoteApprovalPolicy: String? = null,
    val dislikesCount: Int = 0,
    val disliked: Boolean = false,
    val archived: Boolean? = null,
    val place: StatusPlace? = null,
    val video: VideoDetails? = null,
) {
    /** The status whose content is drawn: for a boost that is the boosted post. */
    val displayed: Status get() = reblog ?: this

    val isBoost: Boolean get() = reblog != null

    /** The account that boosted, when this row is a boost. */
    val booster: Account? get() = if (isBoost) account else null

    val hasContentWarning: Boolean get() = spoilerText.isNotBlank()

    val isEdited: Boolean get() = editedAt != null

    /** The quoted post, when the server embedded it. */
    val quotedStatus: Status? get() = quote?.quotedStatus

    val hasMedia: Boolean get() = mediaAttachments.isNotEmpty()

    /** True when at least one attachment has no alt text; the composer warns before posting. */
    val hasUndescribedMedia: Boolean get() = mediaAttachments.any { !it.hasAltText }

    /** Whether the viewer wrote the drawn post. */
    public fun isOwn(viewerAccountId: String): Boolean = displayed.account.id == viewerAccountId

    /** A reply is never less restrictive than what it answers. */
    public fun replyVisibility(default: Visibility): Visibility =
        Visibility.mostRestrictive(default, displayed.visibility)

    /** Everyone a reply addresses, the author first, without the replier, without duplicates. */
    public fun replyMentions(excludingViewerAcct: String?): List<String> {
        val target = displayed
        return (listOf(target.account.acct) + target.mentions.map { it.acct })
            .filter { it.isNotEmpty() && it != excludingViewerAcct }
            .distinct()
    }
}

@Serializable
public data class Mention(val id: String, val username: String, val acct: String, val url: String? = null)

@Serializable
public data class StatusTag(val name: String, val url: String? = null)

@Serializable
public data class Reaction(
    val name: String,
    val count: Int,
    val me: Boolean = false,
    val url: String? = null,
    val staticUrl: String? = null,
)

@Serializable
public data class FilterResult(
    val filter: Filter,
    val keywordMatches: List<String>? = null,
    val statusMatches: List<String>? = null,
)

@Serializable
public data class ApplicationSummary(val name: String, val website: String? = null)

/**
 * A quoted post as the server embeds it.
 *
 * @property state `accepted`, `pending`, `revoked`, …, or null where the server sends the status bare.
 */
@Serializable
public data class QuotedStatus(val state: String? = null, val quotedStatus: Status? = null)

/** A place a post was tagged with. */
@Serializable
public data class StatusPlace(
    val id: String,
    val name: String = "",
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** "Name, Country", or just the name. */
    val label: String get() = if (country.isNullOrEmpty()) name else "$name, $country"
}
