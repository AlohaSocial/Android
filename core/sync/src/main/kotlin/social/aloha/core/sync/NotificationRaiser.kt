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
import social.aloha.core.model.Digest
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.NotificationsFrom
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Visibility

/**
 * Raises on the device what a poll found, since the server cannot push. Only what came after the read
 * marker is raised, so what was read on another device stays quiet, and each notification only once,
 * a group again only when it grew. When it cannot finish, the page or the marker failing, during quiet
 * hours or without the permission, nothing counts as raised and it asks to be told again, so what
 * arrived meanwhile is raised on a later poll if it is still unread.
 *
 * With the [Digest] on, a poll raises at most the personal mentions and leaves the rest unraised; the
 * digest, run at its times, then raises what is still unread as one summary per account. Every
 * notification not yet raised is found again from the server, so nothing is kept on the device for it.
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
        if (!device.allowed || quiet() || paused()) return false
        val fresh = fresh(account) ?: return false
        val digest = settings.digest.first()
        val now = when {
            digest == null -> fresh
            digest.personalNow -> personal(account, fresh)
            else -> emptyList()
        }
        if (now.isNotEmpty()) {
            device.show(account, now, manyAccounts = accounts.all().size > 1)
            raised.mark(account.id, now)
        }
        return true
    }

    /** Raises, as one summary per account, what the digest held back; nothing during quiet hours. */
    suspend fun digest() {
        if (!device.allowed || quiet() || paused()) return
        val all = accounts.all()
        all.filterNot { it.needsReauth }.forEach { account ->
            val fresh = fresh(account).orEmpty()
            if (fresh.isNotEmpty()) {
                device.showDigest(account, fresh, manyAccounts = all.size > 1)
                raised.mark(account.id, fresh)
            }
        }
    }

    /** What is unread and not yet raised; null when the server could not say. */
    private suspend fun fresh(account: SignedInAccount): List<NotificationItem>? {
        val page = (notifications.page(account, emptySet()) as? Answer.Got)?.value
        // a marker that cannot be read would have everything raised, what another device read too
        val marker = notifications.readMarker(account) as? Answer.Got
        if (page == null || marker == null) return null
        val unread = page.items.filter { NotificationItem.isNewer(it.newestId, marker.value) }.take(MAXIMUM)
        return from(account, raised.fresh(account.id, unread))
    }

    /**
     * Of [items], those from whom the account gets notifications, as its server is asked to push them; null
     * when who is followed could not be asked, so nothing is marked and the next poll asks again.
     */
    private suspend fun from(account: SignedInAccount, items: List<NotificationItem>): List<NotificationItem>? {
        val from = settings.from(account.id).first()
        if (from == NotificationsFrom.Anyone || items.isEmpty()) return items
        val kept = if (from == NotificationsFrom.NoOne) {
            emptySet()
        } else {
            val people = items.mapNotNull { it.newest?.id }.distinct()
            notifications.followed(account, people, followers = from == NotificationsFrom.Followers)
        }
        return kept?.let { following ->
            val (shown, dropped) = items.partition { it.newest?.id in following }
            // what is not to be raised is done with, not asked about again on every poll
            raised.mark(account.id, dropped)
            shown
        }
    }

    private suspend fun paused(): Boolean = settings.pausedUntil.first()?.isAfter(clock.instant()) == true

    /** The mentions that do not wait for the digest: private ones, and those from people followed. */
    private suspend fun personal(account: SignedInAccount, items: List<NotificationItem>): List<NotificationItem> {
        val mentions = items.filter { it.kind == NotificationKind.Mention }
        val (private, public) = mentions.partition { it.status?.visibility == Visibility.Direct }
        val authors = public.mapNotNull { it.newest?.id }.distinct()
        val followed = if (authors.isEmpty()) emptySet() else notifications.followed(account, authors).orEmpty()
        return private + public.filter { it.newest?.id in followed }
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
