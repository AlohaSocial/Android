// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import social.aloha.core.data.Trouble
import social.aloha.core.model.Account
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineSource

/**
 * One account's notifications as loaded: [items] newest first, [marker] the newest it had read when they
 * came, [olderThan] where its next page starts, null at its end.
 */
internal data class NotificationPart(
    val items: List<NotificationItem> = emptyList(),
    val marker: String? = null,
    val olderThan: String? = null,
    val trouble: Trouble? = null,
)

/** A notification of the merged list, with the account it came to. */
internal data class OwnedNotification(val owner: String, val item: NotificationItem) {
    /** Unique across accounts: two servers may number their notifications alike. */
    val key: String get() = "$owner/${item.key}"
}

/** The notifications of every account shown, newest first, and the accounts that bring the next page. */
internal data class MergedNotifications(val items: List<OwnedNotification>, val behind: List<String>) {
    /** The first notification [matching], with the account of [accounts] it came to. */
    fun owned(
        accounts: List<SignedInAccount>,
        matching: (OwnedNotification) -> Boolean,
    ): Pair<SignedInAccount, OwnedNotification>? {
        val owned = items.firstOrNull(matching) ?: return null
        return accounts.firstOrNull { it.id == owned.owner }?.let { it to owned }
    }
}

/**
 * [parts] by account, in the order of [accounts], as one list. Accounts load to different depths: below the
 * newest of the oldest notifications of the accounts that have more, one of them may still have some it
 * has not loaded, so the list stops there, and that account loads next. With one account, it is that
 * account's list as it is.
 */
internal fun mergeNotifications(accounts: List<String>, parts: Map<String, NotificationPart>): MergedNotifications {
    val reach = accounts.mapNotNull { id ->
        parts[id]?.takeIf { it.olderThan != null }?.items?.lastOrNull()?.let { id to it.latestAt }
    }
    val frontier = reach.maxOfOrNull { it.second }
    val items = accounts.flatMap { id ->
        parts[id]?.items.orEmpty().filter { frontier == null || !it.latestAt.isBefore(frontier) }
            .map { OwnedNotification(id, it) }
    }.sortedWith(compareByDescending<OwnedNotification> { it.item.latestAt }.thenBy { accounts.indexOf(it.owner) })
    return MergedNotifications(items, reach.filter { it.second == frontier }.map { it.first })
}

/**
 * [this] person as an account on [host] other than the one in use knows them: their id under that server's
 * name and their handle in full, so their profile opens by handle on the server in use.
 */
internal fun Account.elsewhere(host: String): Account {
    val full = acct.removePrefix("@").let { if ('@' in it) it else "$it@$host" }
    return copy(id = TimelineSource.Remote.ID_PREFIX + full, acct = full)
}
