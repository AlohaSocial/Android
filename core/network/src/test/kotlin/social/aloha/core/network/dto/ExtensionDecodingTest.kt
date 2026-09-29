// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.PortfolioLayout
import social.aloha.core.network.AlohaJson
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.endpoints.CollectionEndpoints
import social.aloha.core.network.endpoints.DiscoveryEndpoints
import social.aloha.core.network.endpoints.InterestEndpoints
import social.aloha.core.network.endpoints.PlaceEndpoints
import social.aloha.core.network.endpoints.PortfolioEndpoints
import social.aloha.core.network.endpoints.ProfileEndpoints
import social.aloha.core.network.endpoints.StoryEndpoints
import social.aloha.core.network.endpoints.VideoEndpoints

private fun <T> ApiRequest<T>.value(body: String): T = decoder.decode(AlohaJson, body).value

private const val ACCOUNT = """{"id": "6", "username": "alice", "acct": "alice", "avatar": ""}"""

private const val NEXTCLOUD_STORY =
    """{"id": "1", "account_id": "http://nextcloud.local/index.php/apps/social/@alice", "caption": "Morning colours",
    "duration": 8, "created_at": "2026-09-28T23:11:54+00:00", "expires_at": "2026-09-29T23:11:54+00:00",
    "seen": false, "view_count": 0, "media": {"id": "16", "type": "image",
    "url": "http://nextcloud.local/media/a.jpeg", "preview_url": "http://nextcloud.local/media/b.jpeg"}}"""

class ExtensionDecodingTest {
    @Test
    fun `a Nextcloud story names its poster by uri and nests the file under media`() {
        val story = StoryEndpoints.own().value("[$NEXTCLOUD_STORY]").single()
        assertNull(story.account)
        assertEquals("http://nextcloud.local/index.php/apps/social/@alice", story.accountUri)
        assertEquals("http://nextcloud.local/media/a.jpeg", story.url)
        assertEquals("http://nextcloud.local/media/b.jpeg", story.previewUrl)
        assertEquals(8.0, story.duration)
        assertEquals(Instant.parse("2026-09-28T23:11:54Z"), story.publishedAt)
        assertEquals(0, story.viewCount)
    }

    @Test
    fun `a Pixelfed v1_2 carousel puts the poster on the node and calls a picture a photo`() {
        val body = """{"self": null, "nodes": [{"id": "6",
            "user": {"id": "6", "username": "alice", "username_acct": "alice",
            "avatar": ""}, "nodes": [{"id": "1", "pid": "6", "type": "photo", "src": "http://nextcloud.local/m.jpeg",
            "duration": 8, "seen": false, "created_at": "2026-09-28T23:11:54+00:00"}], "seen": true}]}"""
        val carousel = StoryEndpoints.carouselV2().value(body)
        val story = carousel.others.single()
        assertEquals("alice", story.account?.acct)
        assertEquals(AttachmentKind.Image, story.type)
        assertEquals("http://nextcloud.local/m.jpeg", story.url)
        assertTrue(story.seen)
        assertEquals(Instant.parse("2026-09-29T23:11:54Z"), story.expiresAt)
        assertTrue(carousel.own.isEmpty())
    }

    @Test
    fun `a flat carousel is everybody else's stories`() {
        assertEquals(1, StoryEndpoints.carousel().value("[$NEXTCLOUD_STORY]").others.size)
    }

    @Test
    fun `story reactions arrive bare or wrapped, and one without anybody is dropped`() {
        val reaction = """{"id": "3", "account": $ACCOUNT, "emoji": "🎉"}"""
        assertEquals("🎉", StoryEndpoints.reactions("1").value("[$reaction]").single().reaction)
        val wrapped = """{"reactions": [$reaction, {"id": "4", "caption": "nobody"}]}"""
        assertEquals(1, StoryEndpoints.reactions("1").value(wrapped).size)
    }

    @Test
    fun `continue watching reads a bare post without its account by id`() {
        val bare = """[{"id": "179", "content": "<p>Test video</p>", "visibility": "public",
            "media_attachments": [{"id": "m", "type": "video", "meta": {"original": {"duration": 30.5}}}]}]"""
        val item = VideoEndpoints.continueWatching().value(bare).single()
        assertEquals("179", item.statusId)
        assertEquals(30.5, item.duration)
        assertNull(item.status)
    }

    @Test
    fun `continue watching reads the documented shape with the post`() {
        val documented = """[{"status_id": 179, "position": 12, "duration": 60,
            "status": {"id": "179", "account": $ACCOUNT, "visibility": "public"}}, {"position": 3}]"""
        val item = VideoEndpoints.continueWatching().value(documented).single()
        assertEquals(12.0, item.position)
        assertEquals("179", item.status?.id)
    }

    @Test
    fun `a collection's size is its post count`() {
        val body = """[{"id": "1", "title": "Test album", "visibility": "public", "size": 1}]"""
        assertEquals(1, CollectionEndpoints.all().value(body).single().postCount)
    }

    @Test
    fun `a starter pack is named by id on Nextcloud Social`() {
        val body = """[{"id": "photography", "name": "Photography", "size": 2, "accounts": []}, {"name": "nameless"}]"""
        val pack = DiscoveryEndpoints.starterPacks().value(body).single()
        assertEquals("photography", pack.slug)
        assertEquals(2, pack.size)
    }

    @Test
    fun `place search spells the longitude long and sends empty coordinates`() {
        val place = PlaceEndpoints.search(
            "Ber",
        ).value("""[{"id": "1", "name": "Berlin", "country": "DE", "lat": "", "long": ""}]""")
            .single()
        assertEquals("Berlin, DE", place.label)
        assertNull(place.latitude)
        assertEquals(
            13.4,
            PlaceEndpoints.place("1").value("""{"id": 1, "name": "Berlin", "lat": 52.5, "long": "13.4"}""").longitude,
        )
    }

    @Test
    fun `highlights read since as Unix seconds and hashtags as objects`() {
        val body = """{"available": true, "since": 1790636762, "weeks": [0, "2", 13],
            "hashtags": [{"name": "aloha", "count": 1}, "android"]}"""
        val highlights = ProfileEndpoints.highlights("6").value(body)
        assertEquals(Instant.ofEpochSecond(1790636762), highlights.since)
        assertEquals(listOf(0, 2, 13), highlights.weeks)
        assertEquals(listOf("aloha", "android"), highlights.hashtagNames)
        assertFalse(ProfileEndpoints.highlights("7").value("""{"available": false}""").available)
    }

    @Test
    fun `interests tolerate absent settings and a string score`() {
        val state = InterestEndpoints.state().value(
            """{"interests": [{"name": "Photography", "score": "0.5"}], "thin": 1}""",
        )
        assertTrue(state.settings.learning)
        assertEquals("photography", state.interests.single().id)
        assertEquals(0.5, state.interests.single().score)
        assertTrue(state.thin)
    }

    @Test
    fun `the portfolio settings leave out switches as off and the page as on`() {
        val body = """{"active": 1, "title": "Work", "layout": "rows"}"""
        val settings = PortfolioEndpoints.own().value(body)
        assertEquals(PortfolioLayout.Rows, settings.layout)
        assertFalse(settings.showCaptions)
        assertTrue(PortfolioEndpoints.page("alice").value(body).showCaptions)
    }
}
