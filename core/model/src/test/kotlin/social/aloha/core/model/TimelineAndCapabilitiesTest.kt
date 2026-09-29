// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.model

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineAndCapabilitiesTest {
    private fun capabilities(nextcloud: Boolean) = ServerCapabilities(
        apiBase = "https://example.test/",
        onlyMediaFilter = nextcloud,
        onlyVideoFilter = nextcloud,
        onlyNewsFilter = nextcloud,
    )

    @Test
    fun `video mode asks for only_video where the server has it`() {
        val filters = TimelineFilters.forMode(FeedMode.Video, capabilities(nextcloud = true))
        assertTrue(filters.onlyVideo)
        assertEquals(1, OverFetch.multiplier(FeedMode.Video, filters))
    }

    @Test
    fun `without server narrowing Shorts over-fetches hardest`() {
        val filters = TimelineFilters.forMode(FeedMode.Shorts, capabilities(nextcloud = false))
        assertTrue(filters.isEmpty)
        assertEquals(8, OverFetch.multiplier(FeedMode.Shorts, filters))
    }

    @Test
    fun `news is hidden on a server without only_news`() {
        assertFalse(capabilities(nextcloud = false).supports(FeedMode.News))
        assertTrue(capabilities(nextcloud = true).supports(FeedMode.News))
    }

    @Test
    fun `timeline keys keep modes from sharing rows`() {
        val home = TimelineKey(FeedMode.Home, TimelineSource.Home)
        val photosOfHome = TimelineKey(FeedMode.Photos, TimelineSource.Home)
        assertNotEquals(home.storageKey, photosOfHome.storageKey)
        assertEquals("photos:home", photosOfHome.storageKey)
        assertEquals("home:tag:nextcloud", TimelineKey.home(TimelineSource.Hashtag("NextCloud")).storageKey)
        assertEquals(
            "home:account:7:0:1",
            TimelineKey.home(TimelineSource.Account("7", includeReplies = false, onlyMedia = true)).storageKey,
        )
    }

    @Test
    fun `a timeline key survives persistence`() {
        val key = TimelineKey(FeedMode.Shorts, TimelineSource.List("3"))
        assertEquals(
            key,
            Json.decodeFromString(TimelineKey.serializer(), Json.encodeToString(TimelineKey.serializer(), key)),
        )
    }

    @Test
    fun `an empty vapid key and no streaming url mean polling`() {
        val c = ServerCapabilities(apiBase = "https://cloud.example/", webPushVapidKey = "")
        assertEquals(SyncTier.Polling, c.syncTier)
    }

    @Test
    fun `a streaming url upgrades to streaming`() {
        val c = ServerCapabilities(apiBase = "https://m.example/", streamingUrl = "wss://m.example/api/v1/streaming")
        assertEquals(SyncTier.Streaming, c.syncTier)
    }

    @Test
    fun `a real vapid key wins over streaming`() {
        val c =
            ServerCapabilities(
                apiBase = "https://m.example/",
                streamingUrl = "wss://m.example/x",
                webPushVapidKey = "BNcT",
            )
        assertEquals(SyncTier.WebPush, c.syncTier)
    }

    @Test
    fun `the nextcloud root is the api base without the app prefix`() {
        assertEquals(
            "https://cloud.example/",
            ServerCapabilities.minimal("https://cloud.example/index.php/apps/social/").nextcloudRoot,
        )
        assertEquals("https://cloud.example/", ServerCapabilities.minimal("https://cloud.example/").nextcloudRoot)
    }

    @Test
    fun `capabilities go stale after a day and are stale before detection`() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        assertTrue(ServerCapabilities.minimal("https://x.test/").isStale(now))
        val fresh = ServerCapabilities(
            apiBase = "https://x.test/",
            detectedAt = now.minus(Duration.ofHours(23)),
            format = ServerCapabilities.CURRENT_FORMAT,
        )
        assertFalse(fresh.isStale(now))
        assertTrue(fresh.copy(detectedAt = now.minus(Duration.ofHours(25))).isStale(now))
        // written before the newest field was detected: detected again at once
        assertTrue(fresh.copy(format = 0).isStale(now))
    }

    @Test
    fun `nextcloud social is recognised by its software name`() {
        assertTrue(ServerCapabilities(apiBase = "https://x.test/", softwareName = "nextcloud-social").isNextcloudSocial)
        assertFalse(ServerCapabilities(apiBase = "https://x.test/", softwareName = "mastodon").isNextcloudSocial)
    }

    @Test
    fun `limits pick the video ceiling for video only`() {
        val limits = ServerLimits.MastodonDefaults.copy(imageSizeLimit = 10L shl 20, videoSizeLimit = 2048L shl 20)
        assertEquals(2048L shl 20, limits.sizeLimit("video/mp4"))
        assertEquals(10L shl 20, limits.sizeLimit("image/png"))
        assertTrue(limits.accepts("image/png"))
        assertFalse(limits.accepts("application/x-msdownload"))
        assertTrue(limits.copy(supportedMimeTypes = emptyList()).accepts("anything/at-all"))
    }

    @Test
    fun `ids order by length then lexically, never as numbers`() {
        assertTrue(ServerIds.isNewer("1790637085797595891", "999999999999999999"))
        assertTrue(ServerIds.isNewer("1790637085797595892", "1790637085797595891"))
        assertEquals("100", ServerIds.newest(listOf("99", "100", "7")))
        assertEquals(0, ServerIds.compare("42", "42"))
    }

    @Test
    fun `capabilities stored before live feeds were read keep both feeds`() {
        val stored = """{"apiBase":"https://m.test/"}"""
        val capabilities = Json.decodeFromString(ServerCapabilities.serializer(), stored)
        assertTrue(capabilities.localFeed)
        assertTrue(capabilities.federatedFeed)
    }
}
