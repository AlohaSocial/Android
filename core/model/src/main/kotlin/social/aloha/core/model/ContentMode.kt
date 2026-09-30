// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * A top-level content destination. Each mode owns its timeline key and cache, its source selection,
 * its layout and gestures, and its Explore surface; modes are not filters over one list.
 */
@Serializable
public enum class FeedMode {
    Home,
    Photos,
    Video,
    Shorts,
    News,
    Audio,
    ;

    /** Off by default; enabled in Settings. */
    public val isOptional: Boolean get() = this == News || this == Audio

    /** The stable key used in cache keys and settings. */
    public val key: String get() = name.lowercase()

    public companion object {
        public val defaultEnabled: List<FeedMode> = listOf(Home, Photos, Video, Shorts)
    }
}

/** What a status is, decided once on insert and persisted. */
@Serializable
public enum class ContentKind {
    Text,
    Photo,
    Video,
    Short,
    Audio,
    News,

    /**
     * The server described the media too little to decide; the decision waits until a player reports
     * real dimensions. Provisionally excluded from Shorts.
     */
    Undetermined,
    ;

    /**
     * Whether a status of this kind appears in [mode]. A short is also a video, and so is a clip not yet
     * decided: a server without ffmpeg describes none of its videos, and Video mode must not be empty.
     */
    public fun belongs(mode: FeedMode): Boolean = when (mode) {
        FeedMode.Home -> true
        FeedMode.Photos -> this == Photo
        FeedMode.Video -> this == Video || this == Short || this == Undetermined
        FeedMode.Shorts -> this == Short
        FeedMode.News -> this == News
        FeedMode.Audio -> this == Audio
    }
}
