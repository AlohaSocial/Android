// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
public data class Poll(
    val id: String,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
    val expired: Boolean = false,
    val multiple: Boolean = false,
    val votesCount: Int = 0,
    val votersCount: Int? = null,
    val voted: Boolean = false,
    val ownVotes: List<Int> = emptyList(),
    val options: List<PollOption> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
) {
    /** The denominator for a percentage: voters for a multiple-choice poll, votes otherwise. */
    val participantCount: Int get() = if (multiple && votersCount != null) votersCount else votesCount

    /**
     * Results show once the poll is closed or the reader has voted; before that they would tell
     * people how to vote.
     */
    val showsResults: Boolean get() = expired || voted

    /** The share of participants who chose the option at [index], from 0 to 1. */
    public fun shareOfOptionAt(index: Int): Double {
        val votes = options.getOrNull(index)?.votesCount
        return if (votes == null || participantCount <= 0) 0.0 else votes.toDouble() / participantCount
    }
}

@Serializable
public data class PollOption(val title: String, val votesCount: Int? = null)
