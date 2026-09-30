// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant

/**
 * One row of the notifications list, whether the server grouped it (v2) or sent it as one event (v1).
 *
 * @property key the group key, or the notification's own id; stable while the group grows.
 * @property accounts who did it, newest first; for a group, the sample the server sent.
 * @property count how many did it; more than [accounts] holds when the group is large.
 * @property newestId the newest notification in the row, which the read marker is compared with.
 * @property groupKey the key to ask for everyone in the group; null for a row of one.
 */
public data class NotificationItem(
    val key: String,
    val kind: NotificationKind,
    val accounts: List<Account>,
    val count: Int,
    val status: Status?,
    val latestAt: Instant,
    val newestId: String,
    val groupKey: String?,
) {
    /** The newest to do it, whom the row names. */
    val newest: Account? get() = accounts.firstOrNull()

    /** How many did it besides [newest]. */
    val others: Int get() = (count - 1).coerceAtLeast(0)

    public companion object {
        /** Grouped notifications, each group with its sample accounts and post from the page. */
        public fun from(page: GroupedNotifications): List<NotificationItem> = page.notificationGroups
            .filterNot { it.type.isUnknown }
            .map { group ->
                NotificationItem(
                    key = group.groupKey,
                    kind = group.type,
                    accounts = group.sampleAccountIds.mapNotNull(page::account),
                    count = group.notificationsCount,
                    status = group.statusId?.let(page::status),
                    latestAt = group.latestPageNotificationAt ?: Instant.EPOCH,
                    newestId = group.mostRecentNotificationId,
                    groupKey = group.groupKey.takeIf { group.notificationsCount > 1 },
                )
            }

        /** Notifications one row each, as a server without grouping sends them. */
        public fun from(notifications: List<Notification>): List<NotificationItem> = notifications
            .filterNot { it.type.isUnknown }
            .map {
                NotificationItem(it.id, it.type, listOf(it.account), 1, it.status, it.createdAt, it.id, groupKey = null)
            }

        /**
         * Whether [id] is newer than [than]. Ids are numbers in decimal, and longer than the range of a
         * `Long` on some servers, so they compare by length first.
         */
        public fun isNewer(id: String, than: String?): Boolean =
            than == null || id.length > than.length || (id.length == than.length && id > than)
    }
}
