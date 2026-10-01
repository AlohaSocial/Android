// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
public data class StatusContext(val ancestors: List<Status> = emptyList(), val descendants: List<Status> = emptyList())

@Serializable
public data class Conversation(
    val id: String,
    val accounts: List<Account> = emptyList(),
    val unread: Boolean = false,
    val lastStatus: Status? = null,
)

@Serializable
public data class Marker(
    val lastReadId: String,
    val version: Int = 0,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
)

/** The markers of one account; the server answers `{}` for an account with none yet. */
@Serializable
public data class MarkerSet(val home: Marker? = null, val notifications: Marker? = null)

@Serializable
public data class UnreadCount(val count: Int)

/** A list of accounts the viewer curates. */
@Serializable
public data class AccountList(
    val id: String,
    val title: String,
    val repliesPolicy: String? = null,
    val exclusive: Boolean = false,
    /** The Nextcloud group the list follows (Nextcloud Social's own): its members are the group's, not its owner's. */
    val group: String? = null,
) {
    val followsGroup: Boolean get() = !group.isNullOrEmpty()
}

@Serializable
public data class SearchResults(
    val accounts: List<Account> = emptyList(),
    val statuses: List<Status> = emptyList(),
    val hashtags: List<Tag> = emptyList(),
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && statuses.isEmpty() && hashtags.isEmpty()
}

@Serializable
public data class Suggestion(val account: Account, val source: String? = null, val sources: List<String>? = null) {
    val id: String get() = account.id
}

@Serializable
public data class Announcement(
    val id: String,
    val content: String = "",
    val published: Boolean = false,
    val read: Boolean = false,
    @Serializable(with = InstantSerializer::class) val publishedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    val allDay: Boolean = false,
    @Serializable(with = InstantSerializer::class) val endsAt: Instant? = null,
    val reactions: List<AnnouncementReaction> = emptyList(),
)

/** A reaction readers left on an announcement; [url] is set for a custom emoji. */
@Serializable
public data class AnnouncementReaction(
    val name: String,
    val count: Int = 0,
    val me: Boolean = false,
    val url: String? = null,
) {
    val id: String get() = name
}
