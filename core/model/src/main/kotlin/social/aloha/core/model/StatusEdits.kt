// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** A server translation of a status. */
@Serializable
public data class Translation(
    val content: String,
    val spoilerText: String? = null,
    val detectedSourceLanguage: String? = null,
    val provider: String? = null,
)

/** One revision of an edited status. [createdAt] is [Instant.EPOCH] when the server did not say. */
@Serializable
public data class StatusEdit(
    val account: Account,
    val content: String = "",
    val spoilerText: String = "",
    val sensitive: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.EPOCH,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
)

/** The original plain text of a status, which an edit composer loads instead of the rendered HTML. */
@Serializable
public data class StatusSource(val id: String, val text: String = "", val spoilerText: String = "")

/** A status queued for later. [scheduledAt] is [Instant.EPOCH] when the server did not say. */
@Serializable
public data class ScheduledStatus(
    val id: String,
    val params: ScheduledStatusParams,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant = Instant.EPOCH,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
)

@Serializable
public data class ScheduledStatusParams(
    val text: String? = null,
    val visibility: Visibility? = null,
    val spoilerText: String? = null,
    val sensitive: Boolean = false,
    val language: String? = null,
    val inReplyToId: String? = null,
)
