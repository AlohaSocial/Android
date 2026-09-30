// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.map
import social.aloha.core.data.sync.UnreadCounts
import social.aloha.core.model.Account
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.MarkerEndpoints
import social.aloha.core.network.endpoints.NotificationEndpoints
import social.aloha.core.network.endpoints.PageAnchor
import social.aloha.core.network.endpoints.Paging

/** One page of notifications, and where the next older one starts; null at the end. */
public data class NotificationPage(val items: List<NotificationItem>, val olderThan: String?)

/**
 * The notifications list: grouped where the server groups (v2), one row per event where it does not
 * (v1), with the account's notification filters applied, and the read marker, which only ever moves
 * forwards so a device that is behind cannot un-read what another already read.
 */
@Singleton
public class NotificationsRepository @Inject constructor(
    private val clients: ClientFactory,
    private val unread: UnreadCounts,
) {
    private val written = ConcurrentHashMap<String, String>()

    /**
     * The newest page, or the one older than [olderThan], of the [kinds] asked for (all when empty). A
     * notification a filter hides is left out, and the post of one a filter warns about shows only the
     * filter's title, in the list, on the device and in the widgets alike.
     */
    public suspend fun page(
        account: SignedInAccount,
        kinds: Set<NotificationKind>,
        olderThan: String? = null,
    ): Answer<NotificationPage> {
        val anchor = olderThan?.let(PageAnchor::OlderThan) ?: PageAnchor.Cold
        val types = kinds.map { it.wire }
        return if (account.capabilities.groupedNotifications) {
            clients.answer(account, NotificationEndpoints.grouped(PAGE, anchor, types)).map { page ->
                // a group's page_min_id is the oldest notification it covers, where the next page starts
                val cursor = page.notificationGroups.lastOrNull()?.let { it.pageMinId ?: it.mostRecentNotificationId }
                NotificationPage(NotificationItem.from(page), cursor.takeIf { page.notificationGroups.size >= PAGE })
            }
        } else {
            clients.answer(account, NotificationEndpoints.flat(PAGE, anchor, types)).map { page ->
                NotificationPage(NotificationItem.from(page), page.lastOrNull()?.id.takeIf { page.size >= PAGE })
            }
        }
    }

    /** Everyone in the group [groupKey], beyond the sample its row shows. */
    public suspend fun groupAccounts(account: SignedInAccount, groupKey: String): Answer<List<Account>> =
        clients.answer(account, NotificationEndpoints.groupAccounts(groupKey))

    /**
     * The newest notification the person read, on any of their devices: null when they have read none.
     * Missed when the server could not say and this process never heard, since then nothing is known.
     */
    public suspend fun readMarker(account: SignedInAccount): Answer<String?> =
        when (val answer = clients.answer(account, MarkerEndpoints.read())) {
            is Answer.Got -> {
                answer.value.notifications?.lastReadId?.let { remember(account.id, it) }
                Answer.Got(written[account.id])
            }

            is Answer.Missed -> written[account.id]?.let { Answer.Got(it) } ?: answer
        }

    /**
     * Marks everything up to [newestId] read, unless something newer already is; the badge clears at once.
     * The marker counts as moved only once the server took it, so one that did not go is sent again.
     */
    public suspend fun markRead(account: SignedInAccount, newestId: String) {
        unread.set(account.id, 0)
        if (!NotificationItem.isNewer(newestId, written[account.id])) return
        val answer = clients.answer(account, MarkerEndpoints.write(home = null, notifications = newestId))
        if (answer is Answer.Got) remember(account.id, newestId)
    }

    private fun remember(accountId: String, id: String) {
        written.merge(accountId, id) { old, new -> if (NotificationItem.isNewer(new, old)) new else old }
    }

    private companion object {
        const val PAGE = Paging.DEFAULT_LIMIT
    }
}
