// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import social.aloha.core.model.Account
import social.aloha.core.model.NotificationItem
import social.aloha.core.model.NotificationKind

class MergedNotificationsTest {
    private val bob = Account("2", "bob", "bob", displayName = "Bob")

    private fun at(id: String, minute: Int) = NotificationItem(
        key = id,
        kind = NotificationKind.Favourite,
        accounts = listOf(bob),
        count = 1,
        status = null,
        latestAt = Instant.parse("2026-10-11T12:00:00Z").plusSeconds(minute * 60L),
        newestId = id,
        groupKey = null,
    )

    @Test
    fun `every account's notifications come newest first, as far as each account with more has loaded`() {
        val merged = mergeNotifications(
            listOf("a", "b"),
            mapOf(
                // a has more below minute 5; b has reached its end
                "a" to NotificationPart(listOf(at("a9", 9), at("a5", 5)), olderThan = "a5"),
                "b" to NotificationPart(listOf(at("b7", 7), at("b3", 3))),
            ),
        )
        assertEquals(listOf("a/a9", "b/b7", "a/a5"), merged.items.map { it.key })
        assertEquals(listOf("a"), merged.behind)
    }

    @Test
    fun `one account's list is shown as it is, and two at the same time keep the accounts' order`() {
        val one = mergeNotifications(listOf("a"), mapOf("a" to NotificationPart(listOf(at("1", 2), at("2", 2)))))
        assertEquals(listOf("a/1", "a/2"), one.items.map { it.key })
        val two = mergeNotifications(
            listOf("a", "b"),
            mapOf("a" to NotificationPart(listOf(at("1", 2))), "b" to NotificationPart(listOf(at("1", 2)))),
        )
        assertEquals(listOf("a/1", "b/1"), two.items.map { it.key })
    }

    @Test
    fun `a person another account's server knows is named in full, under its name`() {
        assertEquals("remote:bob@two.example", bob.elsewhere("two.example").id)
        assertEquals("dan@third.social", bob.copy(acct = "dan@third.social").elsewhere("two.example").acct)
    }
}
