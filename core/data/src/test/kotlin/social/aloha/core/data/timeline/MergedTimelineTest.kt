// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.Account
import social.aloha.core.model.Status

class MergedTimelineTest {
    private val author = Account("9", "bob", "bob@other.social", displayName = "Bob")
    private val booster = Account("8", "carol", "carol@third.social", displayName = "Carol")

    /** Post [id] on one account's server, the post at [uri] wherever it is, from [minute] minutes past noon. */
    private fun post(id: String, uri: String, minute: Int) = Status(
        id = id,
        account = author,
        uri = uri,
        createdAt = Instant.parse("2026-10-11T12:00:00Z").plusSeconds(minute * 60L),
    )

    private fun boost(id: String, of: Status, minute: Int) = Status(
        id = id,
        account = booster,
        uri = "https://third.social/boosts/$id",
        createdAt = Instant.parse("2026-10-11T12:00:00Z").plusSeconds(minute * 60L),
        reblog = of,
    )

    private fun rows(vararg statuses: Status) = statuses.map { TimelineRow.Post(it) }

    @Test
    fun `a post seen by two accounts shows once, as the first of them, and says who sees it`() {
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of("a", rows(post("a1", "https://other.social/1", 5)), reachedEnd = true),
                AccountRows.of("b", rows(post("b77", "https://other.social/1", 5)), reachedEnd = true),
            ),
        )
        val shown = merged.posts.single()
        assertEquals("a", shown.owner)
        assertEquals("a1", shown.status.id)
        assertEquals(listOf("a", "b"), shown.seenBy)
    }

    @Test
    fun `account order decides who a shared post acts as, whichever account comes first in the list`() {
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of("b", rows(post("b77", "https://other.social/1", 5)), reachedEnd = true),
                AccountRows.of("a", rows(post("a1", "https://other.social/1", 5)), reachedEnd = true),
            ),
        )
        assertEquals("b", merged.posts.single().owner)
        assertEquals(listOf("b", "a"), merged.posts.single().seenBy)
    }

    @Test
    fun `posts from all accounts are newest first, and a boost stands where it was boosted`() {
        val original = post("b5", "https://other.social/5", 1)
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of(
                    "a",
                    rows(post("a3", "https://other.social/3", 3), post("a2", "https://other.social/2", 2)),
                    true,
                ),
                AccountRows.of("b", rows(boost("b9", original, 4), post("b1", "https://other.social/1", 0)), true),
            ),
        )
        assertEquals(listOf("b9", "a3", "a2", "b1"), merged.posts.map { it.status.id })
    }

    @Test
    fun `a boost and the post it boosts are one post, standing as high as the boost`() {
        val original = post("a5", "https://other.social/5", 1)
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of("a", rows(post("a6", "https://other.social/6", 2), original), true),
                AccountRows.of("b", rows(boost("b9", post("b5", "https://other.social/5", 1), 4)), true),
            ),
        )
        assertEquals(listOf("a5", "a6"), merged.posts.map { it.status.id })
        assertEquals(listOf("a", "b"), merged.posts.first().seenBy)
    }

    @Test
    fun `the merged list stops where an account that has more is not loaded yet, and says it is behind`() {
        val merged = MergedTimeline.merge(
            listOf(
                // a has loaded down to minute 2 and has more below
                AccountRows.of(
                    "a",
                    rows(post("a9", "https://other.social/9", 9), post("a2", "https://other.social/2", 2)),
                    false,
                ),
                // b has loaded further down already
                AccountRows.of(
                    "b",
                    rows(post("b5", "https://other.social/5", 5), post("b0", "https://other.social/0", 0)),
                    false,
                ),
            ),
        )
        assertEquals(listOf("a9", "b5", "a2"), merged.posts.map { it.status.id })
        assertEquals(listOf("a"), merged.behind)
    }

    @Test
    fun `rows below a gap wait, and the gap holds the account back`() {
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of(
                    "a",
                    listOf(TimelineRow.Post(post("a9", "https://other.social/9", 9)), TimelineRow.Gap("gap")) +
                        rows(post("a1", "https://other.social/1", 1)),
                    reachedEnd = true,
                ),
                AccountRows.of("b", rows(post("b5", "https://other.social/5", 5)), reachedEnd = true),
            ),
        )
        assertEquals(listOf("a9"), merged.posts.map { it.status.id })
        assertEquals(listOf("a"), merged.behind)
    }

    @Test
    fun `an account with nothing yet, or at its end, holds no one back`() {
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of("a", emptyList(), reachedEnd = false),
                AccountRows.of("b", rows(post("b5", "https://other.social/5", 5)), reachedEnd = true),
            ),
        )
        assertEquals(listOf("b5"), merged.posts.map { it.status.id })
        assertTrue(merged.behind.isEmpty())
    }

    @Test
    fun `what an account's own settings hide is shown through another that keeps it`() {
        val shared = post("a1", "https://other.social/1", 1)
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of("a", rows(shared), reachedEnd = true, shows = { false }),
                AccountRows.of("b", rows(post("b1", "https://other.social/1", 1)), reachedEnd = true),
            ),
        )
        assertEquals("b", merged.posts.single().owner)
        assertEquals(listOf("b"), merged.posts.single().seenBy)
    }

    @Test
    fun `a post a server sent without its address is told apart by account`() {
        val merged = MergedTimeline.merge(
            listOf(
                AccountRows.of("a", rows(post("1", "", 1)), reachedEnd = true),
                AccountRows.of("b", rows(post("1", "", 1)), reachedEnd = true),
            ),
        )
        assertEquals(listOf("a", "b"), merged.posts.map { it.owner })
    }

    @Test
    fun `reading down to a time marks each account read at its newest post no newer`() {
        val read = MergedTimeline.readUpTo(
            mapOf(
                "a" to rows(post("a9", "https://other.social/9", 9), post("a3", "https://other.social/3", 3)),
                "b" to listOf(TimelineRow.Gap("gap")) + rows(post("b5", "https://other.social/5", 5)),
                "c" to rows(post("c8", "https://other.social/8", 8)),
            ),
            at = Instant.parse("2026-10-11T12:05:00Z"),
        )
        assertEquals(mapOf("a" to "a3", "b" to "b5"), read)
    }
}
