// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModeChoicesTest {
    private val mastodon = ServerCapabilities.minimal("https://mastodon.example/")
    private val nextcloud = mastodon.copy(onlyNewsFilter = true)

    @Test
    fun `out of the box a rail and a phone's bar show the same three modes`() {
        val choices = ModeChoices()
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Shorts), choices.wide(nextcloud))
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Shorts), choices.narrow(nextcloud))
    }

    @Test
    fun `a mode turned on joins a rail, and takes the slot it was given on a phone`() {
        val choices = ModeChoices().turned(FeedMode.Audio, on = true, slot = 1)
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Shorts, FeedMode.Audio), choices.wide(mastodon))
        assertEquals(listOf(FeedMode.Photos, FeedMode.Audio, FeedMode.Shorts), choices.narrow(mastodon))
        // turned off, the slot has its mode back
        assertEquals(ModeChoices(), choices.turned(FeedMode.Audio, on = false))
    }

    @Test
    fun `News stays away on a server that cannot pick news out, and its slot keeps what it replaced`() {
        val choices = ModeChoices().turned(FeedMode.News, on = true)
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.News), choices.narrow(nextcloud))
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Shorts), choices.narrow(mastodon))
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Shorts), choices.wide(mastodon))
    }

    @Test
    fun `a mode moved to another slot gives the first one back`() {
        val moved = ModeChoices().turned(
            FeedMode.Audio,
            on = true,
            slot = 2,
        ).turned(FeedMode.Audio, on = true, slot = 0)
        assertEquals(listOf(FeedMode.Audio, FeedMode.Video, FeedMode.Shorts), moved.narrow(mastodon))
        assertEquals(0, moved.slotOf(FeedMode.Audio))
    }

    @Test
    fun `a mode put where the other one was takes its slot, and the other stays on a rail`() {
        val both = ModeChoices().turned(FeedMode.News, on = true, slot = 2).turned(FeedMode.Audio, on = true, slot = 2)
        assertEquals(listOf(FeedMode.Photos, FeedMode.Video, FeedMode.Audio), both.narrow(nextcloud))
        assertEquals(null, both.slotOf(FeedMode.News))
        assertEquals(FeedMode.News in both.wide(nextcloud), true)
    }
}
