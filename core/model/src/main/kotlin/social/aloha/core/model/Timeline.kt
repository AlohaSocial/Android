// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/** Where a timeline's rows come from. */
@Serializable
public sealed interface TimelineSource {
    /** The persisted part of the cache key; never changes for a source. */
    public val storageKey: String

    /**
     * The `{timeline}` segment of `/api/v1/timelines/{timeline}/`, or null for the sources with a
     * route of their own (a list timeline is a name and an id, so it cannot be a name here).
     */
    public val pathSegment: String? get() = null

    /** Whether reading it needs a signed-in viewer. */
    public val requiresViewer: Boolean get() = true

    @Serializable
    public data object Home : TimelineSource {
        override val storageKey: String get() = "home"
        override val pathSegment: String get() = "home"
    }

    /** The public timeline narrowed to this instance with `local=true`. */
    @Serializable
    public data object Local : TimelineSource {
        override val storageKey: String get() = "public:local"
        override val pathSegment: String get() = "public"
        override val requiresViewer: Boolean get() = false
    }

    @Serializable
    public data object Federated : TimelineSource {
        override val storageKey: String get() = "public:federated"
        override val pathSegment: String get() = "public"
        override val requiresViewer: Boolean get() = false
    }

    @Serializable
    public data object Direct : TimelineSource {
        override val storageKey: String get() = "direct"
        override val pathSegment: String get() = "direct"
    }

    @Serializable
    public data object Favourites : TimelineSource {
        override val storageKey: String get() = "favourites"
        override val pathSegment: String get() = "favourites"
    }

    @Serializable
    public data object Bookmarks : TimelineSource {
        override val storageKey: String get() = "bookmarks"
    }

    @Serializable
    public data object Trending : TimelineSource {
        override val storageKey: String get() = "trending"
        override val requiresViewer: Boolean get() = false
    }

    @Serializable
    public data class List(val id: String) : TimelineSource {
        override val storageKey: String get() = "list:$id"
    }

    @Serializable
    public data class Hashtag(val name: String) : TimelineSource {
        override val storageKey: String get() = "tag:${name.lowercase()}"
        override val requiresViewer: Boolean get() = false
    }

    @Serializable
    public data class Account(val id: String, val includeReplies: Boolean, val onlyMedia: Boolean) : TimelineSource {
        override val storageKey: String
            get() = "account:$id:${if (includeReplies) 1 else 0}:${if (onlyMedia) 1 else 0}"
    }
}

/**
 * A mode plus a source: the cache key, which is why Photos-of-home and Home never share rows.
 */
@Serializable
public data class TimelineKey(val mode: FeedMode, val source: TimelineSource) {
    val storageKey: String get() = "${mode.key}:${source.storageKey}"

    public companion object {
        public fun home(source: TimelineSource = TimelineSource.Home): TimelineKey = TimelineKey(FeedMode.Home, source)
    }
}

/**
 * The narrowings Nextcloud Social accepts beyond Mastodon's parameters. `only_video` is the narrower
 * of the first two and wins when both are set, since every video is media.
 */
@Serializable
public data class TimelineFilters(
    val onlyMedia: Boolean = false,
    val onlyVideo: Boolean = false,
    val onlyNews: Boolean = false,
) {
    val isEmpty: Boolean get() = !onlyMedia && !onlyVideo && !onlyNews

    public companion object {
        public val None: TimelineFilters = TimelineFilters()

        /**
         * What to ask the server for, given the mode and what the server supports. An absent capability
         * yields no parameter; the caller then filters on the device and over-fetches.
         */
        public fun forMode(mode: FeedMode, capabilities: ServerCapabilities): TimelineFilters = when (mode) {
            FeedMode.Home -> None

            FeedMode.Photos, FeedMode.Audio -> TimelineFilters(onlyMedia = capabilities.onlyMediaFilter)

            FeedMode.Video, FeedMode.Shorts ->
                if (capabilities.onlyVideoFilter) {
                    TimelineFilters(onlyVideo = true)
                } else {
                    TimelineFilters(onlyMedia = capabilities.onlyMediaFilter)
                }

            FeedMode.News -> TimelineFilters(onlyNews = capabilities.onlyNewsFilter)
        }
    }
}

/**
 * How hard the client works to fill a page of a mode, capped at [MAXIMUM_UPSTREAM_PAGES] upstream pages
 * per visible page. The device filters every mode but home, since even a server that narrows leaves
 * some of it (`only_media` includes video, `only_video` every video that is not a short, and Mastodon
 * ignores `only_media` on home); where the server narrowed, the first page mostly fills and fetching
 * stops there.
 */
public object OverFetch {
    public const val MAXIMUM_UPSTREAM_PAGES: Int = 5

    @Suppress("MagicNumber") // the table is the specification
    public fun multiplier(mode: FeedMode): Int = when (mode) {
        FeedMode.Home -> 1
        FeedMode.Photos -> 3
        FeedMode.Video -> 5
        FeedMode.Shorts -> 8
        FeedMode.News -> 1
        FeedMode.Audio -> 5
    }
}
