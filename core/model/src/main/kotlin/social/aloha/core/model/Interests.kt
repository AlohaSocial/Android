// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * The reader's hashtag interests on Nextcloud Social: what the server has learned, what it is
 * still weighing, and the switches that govern the learning.
 *
 * @property thin true when the server has too little to go on yet.
 */
@Serializable
public data class InterestsState(
    val settings: InterestSettings = InterestSettings(),
    val interests: List<InterestTag> = emptyList(),
    val candidates: List<InterestTag> = emptyList(),
    val thin: Boolean = false,
)

@Serializable
public data class InterestSettings(
    val learning: Boolean = true,
    val paused: Boolean = false,
    val languages: List<String> = emptyList(),
)

/** One hashtag the server ranks, with how strongly. */
@Serializable
public data class InterestTag(val tag: String, val score: Double = 0.0, val pinned: Boolean = false) {
    val id: String get() = tag.lowercase()
}

/**
 * A feed outside the fediverse (RSS, Atom, a YouTube channel) that the server reads for the viewer.
 * Entries link out; nothing is copied.
 *
 * @property error the server's last word on reading the feed; null when it read fine.
 */
@Serializable
public data class SubscriptionFeed(
    val id: String,
    val url: String? = null,
    val title: String = "",
    val siteUrl: String? = null,
    val entryCount: Int = 0,
    val error: String? = null,
    @Serializable(with = InstantSerializer::class) val lastReadAt: Instant? = null,
) {
    /** The title, or the site's host, or the feed's address. */
    val displayTitle: String
        get() = title.ifEmpty { hostOf(siteUrl) ?: hostOf(url) ?: url ?: id }
}

/** One published entry from a subscribed feed. */
@Serializable
public data class SubscriptionEntry(
    val id: String,
    val title: String = "",
    val url: String? = null,
    val summary: String? = null,
    @Serializable(with = InstantSerializer::class) val publishedAt: Instant? = null,
    val feedTitle: String? = null,
)

/** The weekly recap: how much the reader posted this week against last. Counts are zero while it is off. */
@Serializable
public data class WeeklyRecap(val enabled: Boolean, val thisWeek: Int = 0, val lastWeek: Int = 0)

/** One of the reader's own posts a moderator has not looked at yet. */
@Serializable
public data class HeldPost(
    val id: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val spoilerText: String = "",
    val text: String = "",
    val mediaCount: Int = 0,
    val reason: String? = null,
)

/** `GET /api/v1/review`: the held posts and the reasons the server may give. */
@Serializable
public data class HeldPostsPage(val held: List<HeldPost> = emptyList(), val reasons: List<String> = emptyList())
