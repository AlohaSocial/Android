// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.notifications.NotificationsRepository
import social.aloha.core.data.notifications.RaisedNotifications
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.SignedInAccount

/**
 * Raises on the device what a poll found, since the server cannot push. Only what came after the read
 * marker is raised, so what was read on another device stays quiet, and each notification only once,
 * a group again only when it grew. When it cannot finish, the page or the marker failing, during quiet
 * hours or without the permission, nothing counts as raised and it asks to be told again, so what
 * arrived meanwhile is raised on a later poll if it is still unread.
 */
internal class NotificationRaiser @Inject constructor(
    private val accounts: AccountRepository,
    private val notifications: NotificationsRepository,
    private val raised: RaisedNotifications,
    private val settings: SyncSettings,
    private val device: LocalNotifications,
    private val clock: Clock,
) : PollListener {
    override suspend fun onUnreadChanged(account: SignedInAccount, count: Int): Boolean {
        if (count == 0) return true
        if (!device.allowed || quiet()) return false
        val page = (notifications.page(account, emptySet()) as? Answer.Got)?.value
        // a marker that cannot be read would have everything raised, what another device read too
        val marker = notifications.readMarker(account) as? Answer.Got
        if (page == null || marker == null) return false
        val unread = page.items.filter { NotificationItem.isNewer(it.newestId, marker.value) }.take(MAXIMUM)
        val fresh = raised.fresh(account.id, unread)
        if (fresh.isNotEmpty()) {
            device.show(account, fresh, manyAccounts = accounts.all().size > 1)
            raised.mark(account.id, fresh)
        }
        return true
    }

    private suspend fun quiet(): Boolean {
        val hours = settings.quietHours.first() ?: return false
        return hours.isQuiet(clock.instant().atZone(ZoneId.systemDefault()).hour)
    }

    private companion object {
        // a device away for a long time is told about the newest few, not flooded
        const val MAXIMUM = 8
    }
}
