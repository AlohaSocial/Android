// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityLatchTest {
    private val none = ServerCapabilities.minimal("https://cloud.example/index.php/apps/social/")

    @Test
    fun `hls_url, reactions and quotes latch on first sighting`() {
        val hls =
            status(
                listOf(
                    MediaAttachment(
                        "m1",
                        AttachmentKind.Video,
                        hlsUrl = "https://cloud.example/media/hls/x/master.m3u8",
                    ),
                ),
            )
        val reacted = status().copy(reactions = listOf(Reaction("🎉", 1)))
        val quoting = status().copy(quoteId = "q1")
        val latched = none.latched(listOf(hls, reacted, quoting))
        assertTrue(latched.hlsLadder)
        assertTrue(latched.emojiReactions)
        assertTrue(latched.quotePosts)
    }

    @Test
    fun `an empty reactions list and plain statuses latch nothing`() {
        val latched = none.latched(listOf(status(), status().copy(reactions = emptyList())))
        assertFalse(latched.hlsLadder || latched.emojiReactions || latched.quotePosts)
    }

    @Test
    fun `a latched capability stays latched`() {
        assertTrue(none.copy(quotePosts = true).latched(listOf(status())).quotePosts)
    }

    @Test
    fun `a boost is judged by the post it carries`() {
        val boost = status().copy(id = "b", reblog = status().copy(quoteId = "q"))
        assertTrue(none.latched(listOf(boost)).quotePosts)
    }
}
