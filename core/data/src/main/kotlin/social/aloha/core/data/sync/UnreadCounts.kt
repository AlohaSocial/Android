// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Each account's unread notification count, as its server last said or as reading the notifications
 * just left it. The poll writes it, the badge, the notifications screen and the widgets read it.
 */
@Singleton
public class UnreadCounts @Inject constructor(private val widgets: WidgetUpdates) {
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    public val all: StateFlow<Map<String, Int>> = counts

    public fun of(accountId: String): Int? = counts.value[accountId]

    public suspend fun set(accountId: String, count: Int) {
        counts.update { it + (accountId to count) }
        widgets.setUnread(accountId, count)
    }

    /** Drops the counts of accounts that are no longer signed in. */
    public fun retain(accountIds: Set<String>) {
        counts.update { it.filterKeys(accountIds::contains) }
    }
}
