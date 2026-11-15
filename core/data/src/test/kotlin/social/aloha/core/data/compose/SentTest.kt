// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiError

class SentTest {
    private val stopped = ApiError.Unprocessable("held")
    private val picture = DraftMedia("c.jpg", "image/jpeg", "/files/uploads/c.jpg", mediaId = "m3", description = "Sea")
    private val thread = DraftPost(
        segments = listOf(
            DraftSegment("a", sent = "a", key = "k0"),
            DraftSegment("b", sent = "b", key = "k1"),
            DraftSegment("c /flip", listOf(picture), sent = "c heads", key = "k2"),
            DraftSegment("d", key = "k3"),
        ),
        replyToId = "parent",
        spoiler = "Waves",
        visibility = Visibility.Unlisted,
        quotedId = "q",
        poll = DraftPoll(listOf("Yes", "No"), 3_600),
        postedIds = listOf("s1"),
    )

    @Test
    fun `the segments after the one that stopped are a new thread, unkeyed, with their media`() {
        val rest = checkNotNull(Sent(thread, stopped).rest)
        assertEquals(listOf(DraftSegment("c /flip", listOf(picture)), DraftSegment("d")), rest.segments)
        assertNull(rest.replyToId)
        assertNull(rest.poll)
        assertNull(rest.quotedId)
        assertEquals(emptyList<String>(), rest.postedIds)
        assertEquals("Waves", rest.spoiler)
        assertEquals(Visibility.Unlisted, rest.visibility)
    }

    @Test
    fun `nothing after the last segment is no rest`() {
        assertNull(Sent(thread.copy(postedIds = listOf("s1", "s2", "s3")), stopped).rest)
        assertNull(Sent(DraftPost(listOf(DraftSegment("only"))), stopped).rest)
    }
}
