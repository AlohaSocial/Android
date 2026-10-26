// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * What one account's server can actually do. Computed at sign-in, refreshed on launch when stale,
 * persisted. Never inferred from a hostname, and a capability that cannot be determined is absent:
 * the app is fully usable with every flag false.
 *
 * @property apiBase the resolved prefix Mastodon routes hang off, ending in `/`; not always the root.
 * @property softwareName lowercased, from NodeInfo: `nextcloud-social`, `mastodon`, …
 * @property theme the colour this Nextcloud wears; null on Mastodon and with Theming disabled.
 * @property localFeed whether the server lets a signed-in reader read its local live feed; Mastodon
 *   4.5 lets an administrator disable it, and the federated one ([federatedFeed]), as mastodon.social does.
 * @property detectedAt [Instant.EPOCH] until detection ran, which makes the capabilities stale.
 * @property format the [CURRENT_FORMAT] of the detection that wrote them; capabilities written before a
 *   field was detected read as stale, so the next launch fills it rather than a day later.
 */
@Serializable
public data class ServerCapabilities(
    val apiBase: String,
    val softwareName: String = "",
    val softwareVersion: String = "",
    val mastodonApiVersion: Int? = null,
    val streamingUrl: String? = null,
    val webPushVapidKey: String? = null,
    val groupedNotifications: Boolean = false,
    val notificationPolicy: Boolean = false,
    val filtersV2: Boolean = false,
    val editHistory: Boolean = false,
    val translation: Boolean = false,
    val translationLanguages: Map<String, List<String>> = emptyMap(),
    val onlyMediaFilter: Boolean = false,
    val onlyVideoFilter: Boolean = false,
    val onlyNewsFilter: Boolean = false,
    val onlyShortFilter: Boolean = false,
    val hlsLadder: Boolean = false,
    val watchPositions: Boolean = false,
    val stories: Boolean = false,
    val collections: Boolean = false,
    val emojiReactions: Boolean = false,
    val quotePosts: Boolean = false,
    val mediaFromNextcloudFiles: Boolean = false,
    val preferencesWrite: Boolean = false,
    val limits: ServerLimits = ServerLimits.MastodonDefaults,
    val localFeed: Boolean = true,
    val federatedFeed: Boolean = true,
    val theme: NextcloudTheme? = null,
    @Serializable(with = InstantSerializer::class) val detectedAt: Instant = Instant.EPOCH,
    val format: Int = 0,
) {
    /**
     * Where the Nextcloud itself lives, as opposed to where its Mastodon API is served. Without the root
     * rewrite rules the API base is `…/index.php/apps/social/` while the OCS routes stay above it;
     * stripping the app prefix is the only way back up, since nothing announces the root.
     */
    val nextcloudRoot: String
        get() {
            val index = apiBase.indexOf(APP_PREFIX)
            return if (index < 0) apiBase else apiBase.substring(0, index)
        }

    val isNextcloudSocial: Boolean get() = "nextcloud" in softwareName && "social" in softwareName

    /**
     * Whether a post this server serves carries its reactions, so the single post needs no second request
     * for them: Nextcloud Social from 0.26.221 on. Before, it sent an empty list on every post and served the
     * real ones only from `/statuses/{id}/reactions`; an empty list there cannot tell the two apart, so the
     * version decides.
     */
    val reactionsOnStatus: Boolean get() = isNextcloudSocial && atLeast(softwareVersion, REACTIONS_ON_STATUS)

    /**
     * Whether the account's own pages (memories, statistics, video channels, the exports and the authorized
     * apps) take the sign-in token, so they need no Nextcloud connection: Nextcloud Social from 0.26.220 on.
     * Before, a token answers 401 there, which says nothing a probe could tell from a revoked token.
     */
    val accountPagesWithToken: Boolean get() = isNextcloudSocial && atLeast(softwareVersion, ACCOUNT_PAGES_WITH_TOKEN)

    /**
     * Whether the server counts a view a player reports (`POST /statuses/{id}/view`): Nextcloud Social
     * from 0.26.278 on. Nothing announces the route, so the version decides.
     */
    val videoViews: Boolean get() = isNextcloudSocial && atLeast(softwareVersion, VIDEO_VIEWS)

    /**
     * Whether `accounts/{id}/statuses` applies `exclude_replies` and `tagged`: every server but Nextcloud
     * Social before 0.26.237, which ignored both and answered every post.
     */
    val accountPostsNarrowed: Boolean get() = !isNextcloudSocial || atLeast(softwareVersion, ACCOUNT_POSTS_NARROWED)

    /**
     * Whether an account's `followers_count` is real for every reader: every server but Nextcloud Social
     * before 0.26.241, which sent 0 to a reader who hides the numbers (`/api/v1/social/counts`).
     */
    val realFollowerCounts: Boolean get() = !isNextcloudSocial || atLeast(softwareVersion, REAL_FOLLOWER_COUNTS)

    /**
     * Whether the unread count is asked of v2, which counts groups: where the server groups notifications, but
     * not on Nextcloud Social before 0.26.276, whose v2 count grouped the newest 50 and ignored the marker. v1
     * counts past the marker everywhere.
     */
    val groupedUnreadCount: Boolean
        get() = groupedNotifications && (!isNextcloudSocial || atLeast(softwareVersion, GROUPED_UNREAD_COUNT))

    /**
     * The `types[]` a notifications request names to have every kind this app shows. Nextcloud Social leaves
     * its own kinds out of a page asked for with a token unless they are named, so there it names them all;
     * elsewhere none, which means every kind.
     */
    val allNotificationTypes: List<String>
        get() = if (isNextcloudSocial) {
            NotificationKind.entries.filterNot { it.isUnknown }.map { it.wire }
        } else {
            emptyList()
        }

    /** How this server measures a post against its character limit. */
    val lengthRule: LengthRule get() = if (isNextcloudSocial) LengthRule.CodePoints else LengthRule.Mastodon

    /** Which sync tier applies. They compose: streaming serves the foreground, polling the rest. */
    val syncTier: SyncTier
        get() = when {
            !webPushVapidKey.isNullOrEmpty() -> SyncTier.WebPush
            streamingUrl != null -> SyncTier.Streaming
            else -> SyncTier.Polling
        }

    public fun isStale(now: Instant, maximumAge: Duration = Duration.ofDays(1)): Boolean =
        format < CURRENT_FORMAT || Duration.between(detectedAt, now) > maximumAge

    /**
     * These capabilities promoted by what [statuses] show, since nothing announces them: an `hls_url`
     * on an attachment, a non-empty `reactions` list, a quoted post. Once true they stay true.
     */
    public fun latched(statuses: List<Status>): ServerCapabilities {
        val shown = statuses.map { it.displayed }
        return copy(
            hlsLadder = hlsLadder || shown.any { status -> status.mediaAttachments.any { it.hlsUrl != null } },
            emojiReactions = emojiReactions || shown.any { !it.reactions.isNullOrEmpty() },
            quotePosts = quotePosts || shown.any { it.quoteId != null },
        )
    }

    /** Whether a mode has anything to source from; News has no device-side equivalent, so it is hidden. */
    public fun supports(mode: FeedMode): Boolean = mode != FeedMode.News || onlyNewsFilter

    public companion object {
        private const val APP_PREFIX = "index.php/apps/social/"
        private val REACTIONS_ON_STATUS = listOf(0, 26, 221)
        private val ACCOUNT_PAGES_WITH_TOKEN = listOf(0, 26, 220)
        private val VIDEO_VIEWS = listOf(0, 26, 278)
        private val ACCOUNT_POSTS_NARROWED = listOf(0, 26, 237)
        private val REAL_FOLLOWER_COUNTS = listOf(0, 26, 241)
        private val GROUPED_UNREAD_COUNT = listOf(0, 26, 276)

        /** Raised whenever detection learns a new field: 1 added the live feeds, 2 the Shorts narrowing. */
        public const val CURRENT_FORMAT: Int = 2

        /** The most conservative statement about a server: it speaks the Mastodon API at [apiBase]. */
        public fun minimal(apiBase: String): ServerCapabilities = ServerCapabilities(apiBase = apiBase)
    }
}

/**
 * Whether the dotted [version] (`0.26.251`, anything after its numbers ignored) is [least] or later; one that
 * reads as no number at all is not.
 */
internal fun atLeast(version: String, least: List<Int>): Boolean {
    val parts = version.trim().split('.').map { part -> part.takeWhile(Char::isDigit) }
    if (parts.first().isEmpty()) return false
    val numbers = parts.map { it.toIntOrNull() ?: 0 }
    val differing = least.indices.firstOrNull { numbers.getOrElse(it) { 0 } != least[it] }
    return differing == null || numbers.getOrElse(differing) { 0 } > least[differing]
}

@Serializable
public enum class SyncTier { WebPush, Streaming, Polling }
