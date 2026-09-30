// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import javax.inject.Inject
import social.aloha.core.database.RaisedNotificationDao
import social.aloha.core.database.RaisedNotificationEntity
import social.aloha.core.model.NotificationItem

/**
 * Which notifications the device already raised, so a poll never says the same thing twice. A group
 * that grew since counts as fresh again and is raised in place.
 */
public class RaisedNotifications @Inject constructor(private val dao: RaisedNotificationDao) {
    public suspend fun fresh(accountId: String, items: List<NotificationItem>): List<NotificationItem> {
        if (items.isEmpty()) return emptyList()
        val raised = dao.get(accountId, items.map { it.key }).associate { it.key to it.newestId }
        return items.filter { NotificationItem.isNewer(it.newestId, raised[it.key]) }
    }

    public suspend fun mark(accountId: String, items: List<NotificationItem>) {
        dao.upsert(items.map { RaisedNotificationEntity(accountId, it.key, it.newestId) })
    }

    public suspend fun forget(accountId: String) {
        dao.forget(accountId)
    }
}
