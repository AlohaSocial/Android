// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Nextcloud Social's twelve-week posting rhythm for a local account. Remote accounts answer
 * `available: false`, and the profile draws nothing.
 *
 * @property since when the account was created.
 * @property weeks one post count per week, oldest first; twelve entries when available.
 * @property hashtagNames the account's hashtags, when the server includes them here.
 */
@Serializable
public data class ProfileHighlights(
    val available: Boolean,
    @Serializable(with = InstantSerializer::class) val since: Instant? = null,
    val weeks: List<Int> = emptyList(),
    val hashtagNames: List<String> = emptyList(),
) {
    val total: Int get() = weeks.sum()

    /** The one-line reading of the chart, so nobody has to read the chart. */
    public enum class Rhythm { Quiet, Busier, Slowing, Steady }

    /** The last four weeks against the average four weeks before them. */
    val rhythm: Rhythm
        get() {
            if (weeks.size < RECENT_WEEKS) return if (total == 0) Rhythm.Quiet else Rhythm.Steady
            val recent = weeks.takeLast(RECENT_WEEKS).sum()
            val earlier = weeks.dropLast(RECENT_WEEKS).sum()
            val earlierPerFour = earlier.toDouble() / maxOf(1, weeks.size - RECENT_WEEKS) * RECENT_WEEKS
            return when {
                recent == 0 -> Rhythm.Quiet
                recent > earlierPerFour * BUSIER -> Rhythm.Busier
                recent < earlierPerFour * SLOWER -> Rhythm.Slowing
                else -> Rhythm.Steady
            }
        }

    private companion object {
        const val RECENT_WEEKS = 4
        const val BUSIER = 1.5
        const val SLOWER = 0.5
    }
}

/** For one account asked about: the people the reader follows who also follow it. */
@Serializable
public data class FamiliarFollowers(val id: String, val accounts: List<Account> = emptyList())

/** One person tagged in a photo (Pixelfed's `tagged_people`), sent as an account or a bare handle. */
@Serializable
public data class TaggedPerson(val id: String, val acct: String, val displayName: String? = null)

/** Mastodon's list `replies_policy`: whose replies a list timeline shows. */
@Serializable
public enum class ListRepliesPolicy(override val wire: String) : WireValue {
    Followed("followed"),
    List("list"),
    None("none"),
}

/** One animated picture from the instance's own library, so no search leaves the server. */
@Serializable
public data class GifEntry(
    val slug: String,
    val title: String,
    val url: String? = null,
    val previewUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    val id: String get() = slug

    /** Width over height, square when the server did not say. */
    val aspectRatio: Double
        get() = if (width != null && height != null && height > 0) width.toDouble() / height else 1.0
}

/** A page of the GIF library, with the credit the server asks to be shown. */
@Serializable
public data class GifLibrary(
    val gifs: List<GifEntry> = emptyList(),
    val total: Int = 0,
    val attribution: String? = null,
)

/** A group account the viewer may post as. */
@Serializable
public data class TeamAccount(val handle: String, val name: String, val avatar: String? = null) {
    val id: String get() = handle
}
