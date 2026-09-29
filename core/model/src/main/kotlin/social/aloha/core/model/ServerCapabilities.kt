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
 * @property detectedAt [Instant.EPOCH] until detection ran, which makes the capabilities stale.
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
    val hlsLadder: Boolean = false,
    val watchPositions: Boolean = false,
    val stories: Boolean = false,
    val collections: Boolean = false,
    val emojiReactions: Boolean = false,
    val quotePosts: Boolean = false,
    val mediaFromNextcloudFiles: Boolean = false,
    val preferencesWrite: Boolean = false,
    val limits: ServerLimits = ServerLimits.MastodonDefaults,
    val theme: NextcloudTheme? = null,
    @Serializable(with = InstantSerializer::class) val detectedAt: Instant = Instant.EPOCH,
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

    /** Which sync tier applies. They compose: streaming serves the foreground, polling the rest. */
    val syncTier: SyncTier
        get() = when {
            !webPushVapidKey.isNullOrEmpty() -> SyncTier.WebPush
            streamingUrl != null -> SyncTier.Streaming
            else -> SyncTier.Polling
        }

    public fun isStale(now: Instant, maximumAge: Duration = Duration.ofDays(1)): Boolean =
        Duration.between(detectedAt, now) > maximumAge

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

        /** The most conservative statement about a server: it speaks the Mastodon API at [apiBase]. */
        public fun minimal(apiBase: String): ServerCapabilities = ServerCapabilities(apiBase = apiBase)
    }
}

@Serializable
public enum class SyncTier { WebPush, Streaming, Polling }
