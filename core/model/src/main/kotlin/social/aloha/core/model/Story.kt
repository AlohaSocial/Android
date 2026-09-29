// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * One picture or video that stops existing after a day (Pixelfed's stories, as Nextcloud Social
 * serves them). A story has no timeline row: it is not a post.
 *
 * @property account the poster where the server embeds them. Nextcloud Social's own story routes
 *   name the poster by [accountUri] only, so this is null there and the caller resolves the account.
 * @property accountUri the poster's ActivityPub id, which is what Nextcloud Social sends as `account_id`.
 * @property duration seconds on screen; the server clamps it to 3–30.
 * @property viewCount how many accounts watched, told to the poster only.
 */
@Serializable
public data class Story(
    val id: String,
    val account: Account? = null,
    val accountUri: String? = null,
    val url: String? = null,
    val previewUrl: String? = null,
    val type: AttachmentKind = AttachmentKind.Image,
    val caption: String? = null,
    val duration: Double = DEFAULT_DURATION,
    @Serializable(with = InstantSerializer::class) val publishedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
    val seen: Boolean = false,
    val viewCount: Int? = null,
) {
    /**
     * Whether the story may still be shown. Never longer than a day from [insertedAt] (or from
     * [publishedAt]) whatever the sender claimed, since the promise of the feature is that the thing
     * goes away. A story without either timestamp is not live.
     */
    public fun isLive(now: Instant, insertedAt: Instant? = null): Boolean {
        val ceiling = (insertedAt ?: publishedAt)?.plus(LIFETIME)
        val end = listOfNotNull(expiresAt, ceiling).minOrNull() ?: return false
        return now < end
    }

    public companion object {
        public const val DEFAULT_DURATION: Double = 5.0
        public val LIFETIME: Duration = Duration.ofDays(1)
    }
}

/**
 * The stories rail, whichever shape the server chose: a flat array, or Pixelfed's `{self, nodes}`
 * with one node per account.
 *
 * @property own the viewer's own, when the server separates them.
 * @property others everybody else's, in the order the server ranked the accounts.
 */
@Serializable
public data class StoryCarousel(val own: List<Story> = emptyList(), val others: List<Story> = emptyList())

/** One reaction or comment on a story, told only to the story's poster. */
@Serializable
public data class StoryReaction(
    val id: String,
    val account: Account,
    val reaction: String? = null,
    val comment: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)

/**
 * One entry of `/api/v1/videos/continue`: where the reader got to in a video. Never federated,
 * never shown to anybody else.
 *
 * @property status the post where the server sent it whole. Nextcloud Social sends posts without
 *   their `account`, which a status cannot be built from, so the caller fetches it by [statusId].
 */
@Serializable
public data class ContinueWatchingItem(
    val statusId: String,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    val status: Status? = null,
) {
    val id: String get() = statusId

    /** How far through, between 0 and 1. */
    val fraction: Double get() = if (duration > 0) (position / duration).coerceIn(0.0, 1.0) else 0.0
}

/** When a watch position is worth reporting, and when a video counts as finished. */
public object WatchPositionRules {
    /** Below this nothing is reported: a video somebody opened and closed is noise. */
    public const val MINIMUM_REPORTABLE_SECONDS: Double = 10.0

    /** Past this the server forgets the position rather than bookmarking the credits; the client mirrors it. */
    public const val COMPLETION_FRACTION: Double = 0.95

    /** Reports are coalesced: never more than one in this many seconds. */
    public const val MINIMUM_REPORT_GAP_SECONDS: Double = 5.0

    /** How often a playing video reports; the server allows 600 reports a minute. */
    public const val REPORT_INTERVAL_SECONDS: Double = 10.0

    public fun shouldReport(position: Double, duration: Double): Boolean =
        position >= MINIMUM_REPORTABLE_SECONDS && duration > 0

    public fun isComplete(position: Double, duration: Double): Boolean =
        duration > 0 && position / duration >= COMPLETION_FRACTION
}

/**
 * A Pixelfed album, read and written where the server supports it. Named so it never shadows
 * Kotlin's own `Collection`.
 *
 * @property visibility `public`, `private` or `draft`, as Pixelfed names them.
 */
@Serializable
public data class MediaCollection(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val url: String? = null,
    val postCount: Int = 0,
    val thumbnail: String? = null,
    val visibility: String? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
)
