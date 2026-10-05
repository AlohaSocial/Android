// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.ModerationWarning
import social.aloha.core.model.NotificationKind
import social.aloha.core.model.SeveranceEvent
import social.aloha.core.model.Status
import social.aloha.core.model.TimelineSource
import social.aloha.core.model.Visibility
import social.aloha.core.network.AlohaJson
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Decoded
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.endpoints.NotificationEndpoints
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.TimelineEndpoints

private fun <T> ApiRequest<T>.decode(body: String): Decoded<T> = decoder.decode(AlohaJson, body)

private const val ACCOUNT = """{"id": 1790000000000000001, "username": "alice", "acct": "alice", "avatar": ""}"""

private fun status(extra: String = "", id: String = "1"): String =
    """{"id": "$id", "created_at": "2026-09-28T10:00:00.000Z", "account": $ACCOUNT, "visibility": "public" $extra}"""

private fun media(json: String) = status(""", "media_attachments": [$json]""")

class DtoDecodingTest {
    private fun decodeStatus(json: String): Status = StatusEndpoints.status("1").decode(json).value

    @Test
    fun `an account with an empty avatar has none, and a numeric id keeps every digit`() {
        val account = decodeStatus(status()).account
        assertNull(account.avatar)
        assertEquals("1790000000000000001", account.id)
    }

    @Test
    fun `an account without acct falls back to its username`() {
        val s = decodeStatus("""{"id": "1", "account": {"id": "2", "username": "bob", "avatar": null}}""")
        assertEquals("bob", s.account.acct)
        assertNull(s.account.avatar)
    }

    @Test
    fun `nextcloud social's null reblog, empty reactions and null place decode as absent`() {
        val s = decodeStatus(status(""", "reblog": null, "reactions": [], "place": null"""))
        assertNull(s.reblog)
        assertEquals(emptyList<Any>(), s.reactions)
        assertNull(s.place)
    }

    @Test
    fun `media meta from a fork as an empty array leaves the dimensions absent`() {
        val s = decodeStatus(media("""{"id": 5, "type": "video", "url": "https://x/v.mp4", "meta": []}"""))
        val attachment = s.mediaAttachments.single()
        assertEquals(AttachmentKind.Video, attachment.type)
        assertNull(attachment.meta)
        assertNull(attachment.aspectRatio)
    }

    @Test
    fun `an empty original from a server without ffmpeg defers classification and is not a failure`() {
        val json = media("""{"id": "5", "type": "video", "meta": {"original": [], "focus": {"x": 0.1, "y": -0.2}}}""")
        val decoded = StatusEndpoints.status("1").decode(json)
        assertTrue(decoded.failures.isEmpty())
        val meta = decoded.value.mediaAttachments.single().meta
        assertNotNull(meta)
        assertNull(meta?.original)
        assertEquals(0.1, meta?.focus?.x)
    }

    @Test
    fun `unknown enum values decode rather than fail and visibility fails closed`() {
        val json = media("""{"id": "5", "type": "hologram"}""").replace("\"public\"", "\"local_only\"")
        val s = decodeStatus(json)
        assertEquals(AttachmentKind.Unknown, s.mediaAttachments.single().type)
        assertEquals(Visibility.Unknown, s.visibility)
        assertEquals(Visibility.Unknown, decodeStatus("""{"id": "1", "account": $ACCOUNT}""").visibility)
    }

    @Test
    fun `a quote decodes from the wrapper and from the bare status`() {
        val wrapper = """{"state": "accepted", "quoted_status": ${status(id = "9")}}"""
        val wrapped = decodeStatus(status(""", "quote": $wrapper"""))
        assertEquals("accepted", wrapped.quote?.state)
        assertEquals("9", wrapped.quotedStatus?.id)
        val bare = decodeStatus(status(""", "quote": ${status(id = "8")}"""))
        assertNull(bare.quote?.state)
        assertEquals("8", bare.quotedStatus?.id)
        assertNull(decodeStatus(status(""", "quote": "nonsense"""")).quote)
    }

    @Test
    fun `a malformed status drops from its page but still counts toward the raw count`() {
        val page = "[${status()}, {\"id\": \"2\"}, ${status(id = "3")}]"
        val decoded = TimelineEndpoints.timeline(TimelineSource.Home).decode(page)
        assertEquals(listOf("1", "3"), decoded.value.map { it.id })
        assertEquals(3, decoded.rawCount)
        assertEquals(listOf(1), decoded.failures.map { it.index })
    }

    @Test
    fun `a malformed nested attachment is dropped and recorded without failing the status`() {
        val json = status(""", "media_attachments": [{"type": "image"}, {"id": "6", "type": "image"}]""")
        val decoded = StatusEndpoints.status("1").decode(json)
        assertEquals(listOf("6"), decoded.value.mediaAttachments.map { it.id })
        assertEquals(1, decoded.failures.size)
    }

    @Test
    fun `a video status carries PeerTube's labelled facts and chapters in either form`() {
        val video = """, "video": {"views": "12", "category": {"id": 3, "label": "Travels"}, "licence": "CC-BY",
            "download": 0, "chapters": [{"start": 0, "title": "Intro"}, {"start": "1:02", "title": "Middle"},
            {"start": "soon", "title": "x"}]}"""
        val details = decodeStatus(status(video)).video
        assertEquals(12, details?.views)
        assertEquals("Travels", details?.category)
        assertEquals("CC-BY", details?.licence)
        assertEquals(false, details?.download)
        assertEquals(listOf(0.0, 62.0), details?.chapters?.map { it.start })
    }

    @Test
    fun `a card of an empty object is no card`() {
        assertNull(StatusEndpoints.card("1").decode("{}").value)
        val card = StatusEndpoints.card("1").decode("""{"url": "https://a.test", "title": "Title"}""").value
        assertEquals("Title", card?.title)
    }

    @Test
    fun `live feeds a server disables are not offered, and a server that says nothing keeps them`() {
        val social = """{"domain":"mastodon.social","configuration":{"timelines_access":
            {"live_feeds":{"local":"disabled","remote":"disabled"},"hashtag_feeds":{"local":"public"}}}}"""
        val members = """{"domain":"m.test","configuration":
            {"timelines_access":{"live_feeds":{"local":"authenticated"}}}}"""
        val older = """{"domain":"cloud.test","configuration":{}}"""
        InstanceEndpoints.v2().decode(social).value.let {
            assertFalse(it.localFeed)
            assertFalse(it.federatedFeed)
        }
        InstanceEndpoints.v2().decode(members).value.let {
            assertTrue(it.localFeed)
            assertTrue(it.federatedFeed)
        }
        assertTrue(InstanceEndpoints.v2().decode(older).value.localFeed)
    }

    @Test
    fun `both spellings of the streaming url are read`() {
        val modern = """{"domain":"m.test","configuration":{"urls":{"streaming":"wss://m.test/s"}}}"""
        val legacy = """{"domain":"m.test","configuration":{"urls":{"streaming_api":"wss://m.test/s"}}}"""
        assertTrue(InstanceEndpoints.v2().decode(modern).value.hasStreaming)
        assertTrue(InstanceEndpoints.v2().decode(legacy).value.hasStreaming)
    }

    @Test
    fun `an empty urls object and an empty vapid key mean no streaming and no push`() {
        val body = """{"domain":"cloud.test","configuration":{"urls":{},"vapid":{"public_key":""}}}"""
        val instance = InstanceEndpoints.v2().decode(body).value
        assertFalse(instance.hasStreaming)
        assertFalse(instance.hasWebPush)
    }

    @Test
    fun `v1 and v2 produce the same limits, including a video ceiling beyond 2 GiB`() {
        val configuration = """"configuration":{"statuses":{"max_characters":5000,"max_media_attachments":4},
            "media_attachments":{"image_size_limit":10485760,"video_size_limit":2147483648}}"""
        val v1 = InstanceEndpoints.v1().decode("""{"uri":"cloud.test",$configuration}""").value
        val v2 = InstanceEndpoints.v2().decode("""{"domain":"cloud.test",$configuration}""").value
        assertEquals(v1.limits, v2.limits)
        assertEquals(10L * 1024 * 1024, v2.limits.imageSizeLimit)
        assertEquals(2048L * 1024 * 1024, v2.limits.sizeLimit("video/mp4"))
        assertEquals(5000, v2.limits.maxStatusCharacters)
    }

    @Test
    fun `one odd configuration block leaves only its own limits at the defaults`() {
        val body = """{"domain":"x","configuration":{"statuses":[],"polls":{"max_options":8}}}"""
        val instance = InstanceEndpoints.v2().decode(body).value
        assertEquals(500, instance.limits.maxStatusCharacters)
        assertEquals(8, instance.limits.maxPollOptions)
    }

    @Test
    fun `grouped notifications count their groups as the page`() {
        val first = """{"group_key": "favourite-1", "notifications_count": 2, "type": "favourite",
            "most_recent_notification_id": 7, "sample_account_ids": [1790000000000000001]}"""
        val second = """{"group_key": "x", "type": "story:react", "most_recent_notification_id": "8"}"""
        val body = """{"accounts": [$ACCOUNT], "statuses": [], "notification_groups": [$first, $second]}"""
        val decoded = NotificationEndpoints.grouped().decode(body)
        val groups = decoded.value.notificationGroups
        assertEquals(2, decoded.rawCount)
        assertEquals("7", groups[0].mostRecentNotificationId)
        assertEquals(listOf("1790000000000000001"), groups[0].sampleAccountIds)
        assertEquals(NotificationKind.Unknown, groups[1].type)
        assertNotNull(decoded.value.account("1790000000000000001"))
    }

    @Test
    fun `a group says why follows were cut, and what a moderator warned about`() {
        val severed = """{"group_key": "s", "type": "severed_relationships", "most_recent_notification_id": "9",
            "event": {"id": "1", "type": "domain_block", "target_name": "spam.example"}}"""
        val warned = """{"group_key": "w", "type": "moderation_warning", "most_recent_notification_id": "10",
            "moderation_warning": {"id": "4", "action": "silence", "text": "Use content warnings."}}"""
        val body = """{"accounts": [], "statuses": [], "notification_groups": [$severed, $warned]}"""
        val groups = NotificationEndpoints.grouped().decode(body).value.notificationGroups
        assertEquals(SeveranceEvent("domain_block", "spam.example"), groups[0].severance)
        assertEquals(ModerationWarning("4", "silence", "Use content warnings."), groups[1].warning)
    }

    @Test
    fun `a relationship that does not mention reblogs shows them`() {
        val json = """{"id": 3, "following": "true"}"""
        val relationship = AlohaJson.decodeFromString(RelationshipDto.serializer(), json).toDomain()
        assertTrue(relationship.showingReblogs)
        assertTrue(relationship.following)
    }

    @Test
    fun `the nextcloud theme is read from the OCS envelope and normalised`() {
        val theming = """{"name": "Cloud", "color": "#0082C9", "color-element-dark": "abc", "color-text": 12}"""
        val body = """{"ocs": {"meta": {}, "data": {"capabilities": {"core": {}, "theming": $theming}}}}"""
        val theme = AlohaJson.decodeFromString(OcsCapabilitiesDto.serializer(), body).theming?.toDomain()
        assertEquals("Cloud", theme?.name)
        assertEquals("#0082c9", theme?.colourHex)
        assertEquals("#aabbcc", theme?.elementDarkHex)
        assertNull(theme?.textHex)
        val broken = AlohaJson.decodeFromString(OcsCapabilitiesDto.serializer(), """{"ocs": {"data": []}}""")
        assertNull(broken.theming)
    }

    @Test
    fun `markers are an object keyed by timeline and empty for a new account`() {
        val json = """{"home": {"last_read_id": 1790637085797595891, "version": 2}}"""
        val set = AlohaJson.decodeFromString(MarkerSetDto.serializer(), json).toDomain()
        assertEquals("1790637085797595891", set.home?.lastReadId)
        assertNull(set.notifications)
        assertNull(AlohaJson.decodeFromString(MarkerSetDto.serializer(), "{}").toDomain().home)
    }
}
