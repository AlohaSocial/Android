// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** What an account's widgets show without asking the network: its unread count and its newest mentions. */
@Serializable
public data class WidgetFeed(val unread: Int = 0, val mentions: List<MentionSnippet> = emptyList())

/** A mention as a widget lists it. */
@Serializable
public data class MentionSnippet(
    val statusId: String,
    val name: String,
    val avatar: String? = null,
    val text: String,
    @Serializable(with = InstantSerializer::class) val at: Instant = Instant.EPOCH,
)
