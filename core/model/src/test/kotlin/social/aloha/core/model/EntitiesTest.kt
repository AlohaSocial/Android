// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntitiesTest {
    @Test
    fun `an unrecognised notification type is unknown and does not group`() {
        val kind = NotificationKind.fromWire("something.new")
        assertTrue(kind.isUnknown)
        assertFalse(kind.groups)
        assertEquals(NotificationKind.FollowRequest, NotificationKind.fromWire("follow_request"))
    }

    @Test
    fun `visibility fails closed`() {
        val v = Visibility.fromWire("local_only")
        assertTrue(v.isUnknown)
        assertEquals(Visibility.Direct.restrictiveness, v.restrictiveness)
        assertEquals(v, Visibility.mostRestrictive(Visibility.Public, v))
        assertEquals(Visibility.Unknown, Visibility.fromWire(null))
        assertEquals(Visibility.Unknown, Visibility.fromWire("__unknown"))
    }

    @Test
    fun `sensitive media policy keeps PeerTube's three states under Mastodon's names`() {
        assertTrue(SensitiveMediaPolicy.fromWire("show_all").allowsAutomaticReveal)
        assertTrue(SensitiveMediaPolicy.fromWire("default").drawsAtAll)
        assertFalse(SensitiveMediaPolicy.fromWire("hide_all").drawsAtAll)
    }

    @Test
    fun `attachment kinds tell Mastodon's unknown from a value the app does not know`() {
        assertEquals(AttachmentKind.UnsupportedFile, AttachmentKind.fromWire("unknown"))
        assertEquals(AttachmentKind.UnsupportedFile, AttachmentKind.fromWire(null))
        assertEquals(AttachmentKind.Unknown, AttachmentKind.fromWire("hologram"))
    }

    @Test
    fun `a reply is never less restrictive than what it answers`() {
        val private = status().copy(visibility = Visibility.Private)
        assertEquals(Visibility.Private, private.replyVisibility(Visibility.Public))
        assertEquals(Visibility.Direct, private.replyVisibility(Visibility.Direct))
    }

    @Test
    fun `reply mentions put the author first and leave out the replier and duplicates`() {
        val s = status().copy(
            mentions = listOf(
                Mention("2", "bob", "bob@b.test"),
                Mention("3", "me", "me"),
                Mention("1", "alice", "alice@example.test"),
            ),
        )
        assertEquals(listOf("alice@example.test", "bob@b.test"), s.replyMentions(excludingViewerAcct = "me"))
    }

    @Test
    fun `a boost shows the boosted post and names the booster`() {
        val inner = status()
        val booster = alice.copy(id = "9", acct = "bob")
        val boost = Status(id = "b", account = booster, reblog = inner)
        assertEquals(inner, boost.displayed)
        assertEquals(booster, boost.booster)
        assertNull(inner.booster)
    }

    @Test
    fun `a display name falls back through the handle`() {
        assertEquals("alice", Account(id = "1", username = "alice", acct = "alice").bestDisplayName)
        assertEquals("a@x", Account(id = "1", username = "", acct = "a@x").bestDisplayName)
        assertEquals("x.test", Account(id = "1", username = "a", acct = "a@x.test").host)
        assertEquals("cloud.test", Account(id = "1", username = "a", acct = "a", url = "https://cloud.test/@a").host)
    }

    @Test
    fun `hashtags normalise to what the server stores`() {
        assertEquals("nextcloud", Tag.normalise(" #NextCloud "))
        assertEquals("swift", Tag.normalise("##Swift"))
        assertNull(Tag.normalise("#"))
        assertEquals(127, Tag.normalise("a".repeat(200))?.length)
    }

    @Test
    fun `a poll hides results until voted or closed and shares by the right denominator`() {
        val poll =
            Poll(id = "p", multiple = true, votesCount = 10, votersCount = 5, options = listOf(PollOption("a", 4)))
        assertFalse(poll.showsResults)
        assertTrue(poll.copy(voted = true).showsResults)
        assertEquals(0.8, poll.shareOfOptionAt(0))
        assertEquals(0.0, poll.shareOfOptionAt(3))
        assertEquals(0.4, poll.copy(multiple = false).shareOfOptionAt(0))
    }

    @Test
    fun `a filter stops applying the moment it expires`() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        assertFalse(Filter(id = "f").isExpired(now))
        assertTrue(Filter(id = "f", expiresAt = now).isExpired(now))
        assertFalse(Filter(id = "f", expiresAt = now.plusSeconds(1)).isExpired(now))
    }

    @Test
    fun `theme hex values normalise or vanish`() {
        assertEquals("#aabbcc", NextcloudTheme.normalise("#ABC"))
        assertEquals("#0082c9", NextcloudTheme.normalise("0082c9"))
        assertNull(NextcloudTheme.normalise("blue"))
        assertNull(NextcloudTheme.normalise(""))
        val theme = NextcloudTheme(colourHex = "#0082c9", elementDarkHex = "#4ba3de")
        assertEquals("#4ba3de", theme.hex(onDarkBackground = true))
        assertEquals("#0082c9", theme.hex(onDarkBackground = false))
    }

    @Test
    fun `chapters parse from a description the way people write them`() {
        val chapters = VideoChapters.parse(
            "Intro text\n0:00 Intro\n1:02 - The first thing\n0:30 a mention\n01:02:03 – Much later\n",
        )
        assertEquals(listOf(0.0, 62.0, 3723.0), chapters.map { it.start })
        assertEquals("The first thing", chapters[1].title)
        assertTrue(VideoChapters.parse("see 1:02 for the goal").isEmpty())
        assertNull(VideoChapters.seconds("1:60"))
        assertEquals("1:02:03", VideoChapters.clock(3723.0))
        assertEquals("1:02", VideoChapters.clock(62.0))
    }

    @Test
    fun `a status survives persistence as json`() {
        val s = status(
            listOf(attachment(AttachmentKind.Video, duration = 3.0, width = 1, height = 2)),
        ).copy(createdAt = Instant.parse("2026-09-29T10:00:00Z"))
        assertEquals(s, Json.decodeFromString(Status.serializer(), Json.encodeToString(Status.serializer(), s)))
    }
}
