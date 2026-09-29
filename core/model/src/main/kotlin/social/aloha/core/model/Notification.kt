// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** A v1 notification: one row per event. [createdAt] is [Instant.EPOCH] when the server did not say. */
@Serializable
public data class Notification(
    val id: String,
    val type: NotificationKind,
    val account: Account,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.EPOCH,
    val status: Status? = null,
)

/**
 * A page of grouped notifications (Mastodon 4.3). Forty favourites of one post are one group and
 * one copy of the post, where v1 sends forty rows and forty copies.
 */
@Serializable
public data class GroupedNotifications(
    val accounts: List<Account> = emptyList(),
    val statuses: List<Status> = emptyList(),
    val notificationGroups: List<NotificationGroup> = emptyList(),
) {
    public fun account(id: String): Account? = accounts.firstOrNull { it.id == id }

    public fun status(id: String): Status? = statuses.firstOrNull { it.id == id }
}

/**
 * One group of notifications.
 *
 * @property groupKey built from what the group is (`favourite-{status id}`, `follow-all`), never from
 *   the ids in it, so it names the same group after more arrive. Ungrouped kinds are `ungrouped-{id}`.
 */
@Serializable
public data class NotificationGroup(
    val groupKey: String,
    val notificationsCount: Int,
    val type: NotificationKind,
    val mostRecentNotificationId: String,
    val pageMinId: String? = null,
    val pageMaxId: String? = null,
    @Serializable(with = InstantSerializer::class) val latestPageNotificationAt: Instant? = null,
    val sampleAccountIds: List<String> = emptyList(),
    val statusId: String? = null,
)
