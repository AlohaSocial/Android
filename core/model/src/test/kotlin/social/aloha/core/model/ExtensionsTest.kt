// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionsTest {
    private val published = Instant.parse("2026-09-28T10:00:00Z")

    @Test
    fun `a story is live until it expires`() {
        val story = Story("1", publishedAt = published, expiresAt = published.plus(Duration.ofHours(12)))
        assertTrue(story.isLive(published.plus(Duration.ofHours(11))))
        assertFalse(story.isLive(published.plus(Duration.ofHours(13))))
    }

    @Test
    fun `a story never outlives a day from insert whatever the sender claimed`() {
        val story = Story("1", publishedAt = published, expiresAt = published.plus(Duration.ofDays(30)))
        assertFalse(story.isLive(published.plus(Duration.ofHours(25))))
        val insertedLater = published.plus(Duration.ofHours(2))
        assertTrue(story.isLive(published.plus(Duration.ofHours(25)), insertedAt = insertedLater))
    }

    @Test
    fun `a story without any timestamp is not live`() {
        assertFalse(Story("1").isLive(published))
    }

    @Test
    fun `nothing under ten seconds is worth reporting and past 95 percent is finished`() {
        assertFalse(WatchPositionRules.shouldReport(position = 4.0, duration = 600.0))
        assertTrue(WatchPositionRules.shouldReport(position = 12.0, duration = 600.0))
        assertFalse(WatchPositionRules.shouldReport(position = 12.0, duration = 0.0))
        assertTrue(WatchPositionRules.isComplete(position = 990.0, duration = 1000.0))
        assertFalse(WatchPositionRules.isComplete(position = 300.0, duration = 1000.0))
    }

    @Test
    fun `continue watching progress stays between 0 and 1`() {
        assertEquals(0.25, ContinueWatchingItem("1", position = 25.0, duration = 100.0).fraction)
        assertEquals(1.0, ContinueWatchingItem("1", position = 150.0, duration = 100.0).fraction)
        assertEquals(0.0, ContinueWatchingItem("1", position = 10.0, duration = 0.0).fraction)
    }

    @Test
    fun `the highlights rhythm reads the last four weeks against the rest`() {
        assertEquals(ProfileHighlights.Rhythm.Quiet, ProfileHighlights(true, weeks = List(12) { 0 }).rhythm)
        assertEquals(
            ProfileHighlights.Rhythm.Busier,
            ProfileHighlights(
                true,
                weeks = List(8) {
                    1
                } + List(4) { 5 },
            ).rhythm,
        )
        assertEquals(
            ProfileHighlights.Rhythm.Slowing,
            ProfileHighlights(
                true,
                weeks = List(8) {
                    5
                } + List(4) { 1 },
            ).rhythm,
        )
        assertEquals(ProfileHighlights.Rhythm.Steady, ProfileHighlights(true, weeks = List(12) { 3 }).rhythm)
        assertEquals(ProfileHighlights.Rhythm.Steady, ProfileHighlights(true, weeks = listOf(2, 1)).rhythm)
        assertEquals(13, ProfileHighlights(true, weeks = List(11) { 0 } + 13).total)
    }

    @Test
    fun `a GIF without dimensions is laid out square`() {
        assertEquals(1.0, GifEntry("cat", "cat").aspectRatio)
        assertEquals(2.0, GifEntry("cat", "cat", width = 200, height = 100).aspectRatio)
    }

    @Test
    fun `a feed without a title is named by its site's host`() {
        assertEquals("example.org", SubscriptionFeed("1", siteUrl = "https://example.org/blog").displayTitle)
        assertEquals("feeds.example", SubscriptionFeed("1", url = "https://feeds.example/rss").displayTitle)
        assertEquals("Blog", SubscriptionFeed("1", title = "Blog", url = "https://feeds.example/rss").displayTitle)
        assertEquals("1", SubscriptionFeed("1").displayTitle)
    }

    @Test
    fun `a portfolio post shows its first picture`() {
        val video = MediaAttachment("v", AttachmentKind.Video)
        val picture = MediaAttachment("p", AttachmentKind.Image)
        assertEquals("p", PortfolioPost("1", mediaAttachments = listOf(video, picture)).picture?.id)
        assertEquals("v", PortfolioPost("1", mediaAttachments = listOf(video)).picture?.id)
    }

    @Test
    fun `a number map sorts by key, which for months is chronological`() {
        val map = NumberMap(mapOf("2026-03" to 1.0, "2026-01" to 4.0, "2026-02" to 2.0))
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), map.sorted.map { it.first })
        assertEquals(4.0, map["2026-01"])
    }

    @Test
    fun `following imports go in as follows and the rest keep their name`() {
        assertEquals("follows", MigrationListKind.Following.importPath)
        assertEquals("blocks", MigrationListKind.Blocks.importPath)
    }

    @Test
    fun `a text prefix never cuts an emoji in half`() {
        assertEquals("🎉".repeat(20), CharacterCount.prefix("🎉".repeat(40), 20))
        assertEquals("short", CharacterCount.prefix("short", 20))
        assertEquals("👩‍👩‍👧", CharacterCount.prefix("👩‍👩‍👧👩‍👩‍👧", 1))
    }
}
