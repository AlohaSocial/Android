// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * The viewer's relationship to one account.
 *
 * @property showingReblogs honoured by Nextcloud Social as part of the timeline query, so it is real.
 *   True when the server does not say.
 * @property muteExpiresAt when a timed mute ends, where the server says.
 */
@Serializable
public data class Relationship(
    val id: String,
    val following: Boolean = false,
    val followedBy: Boolean = false,
    val blocking: Boolean = false,
    val blockedBy: Boolean = false,
    val muting: Boolean = false,
    val mutingNotifications: Boolean = false,
    val requested: Boolean = false,
    val requestedBy: Boolean = false,
    val domainBlocking: Boolean = false,
    val endorsed: Boolean = false,
    val notifying: Boolean = false,
    val showingReblogs: Boolean = true,
    val note: String? = null,
    val languages: List<String>? = null,
    @Serializable(with = InstantSerializer::class) val muteExpiresAt: Instant? = null,
)
