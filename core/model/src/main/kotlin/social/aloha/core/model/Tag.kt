// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import kotlinx.serialization.Serializable

/**
 * A hashtag.
 *
 * @property following present on the followed-tag and single-tag routes, absent from the public
 *   trending route, which has no viewer to answer for.
 */
@Serializable
public data class Tag(
    val name: String,
    val url: String? = null,
    val history: List<TagHistory> = emptyList(),
    val following: Boolean? = null,
) {
    val id: String get() = name.lowercase()

    /**
     * Nextcloud Social sends a single bucket and always `accounts: "0"`, since it counts uses and not
     * distinct accounts. A sparkline from one point misleads, so the UI shows uses only when this is true.
     */
    val hasOnlyOneBucket: Boolean get() = history.size <= 1

    val totalUses: Int get() = history.sumOf { it.usesCount }

    public companion object {
        private const val MAX_LENGTH = 127

        /**
         * The server's own normalisation: no leading `#`, trimmed, lowercased, cut to the 127 characters
         * its column holds. `#NextCloud` and `nextcloud` are one tag to follow, look up and unfollow.
         * Null when nothing remains.
         */
        public fun normalise(raw: String): String? {
            val value = raw.trim().trimStart('#').trim().lowercase()
            return value.takeIf { it.isNotEmpty() }?.take(MAX_LENGTH)
        }
    }
}

@Serializable
public data class TagHistory(val day: String, val uses: String, val accounts: String) {
    val usesCount: Int get() = uses.toIntOrNull() ?: 0

    val accountsCount: Int get() = accounts.toIntOrNull() ?: 0
}

/** A hashtag an account features on its profile. */
@Serializable
public data class FeaturedTag(
    val id: String,
    val name: String,
    val url: String? = null,
    val statusesCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastStatusAt: java.time.Instant? = null,
)
