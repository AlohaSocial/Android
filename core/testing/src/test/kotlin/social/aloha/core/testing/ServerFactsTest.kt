// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What Nextcloud Social 0.26.97 was seen to do, pinned to the captured corpus. The client is built
 * around each of these; when a re-capture after a server update changes one, the matching test
 * fails and the client code that works around it is due for review.
 */
class ServerFactsTest {
    private val corpus = FixtureCorpus.nextcloudSocial

    private fun body(name: String): JsonElement = Json.parseToJsonElement(corpus.named(name).body)

    private fun JsonElement.field(name: String): JsonElement? = (this as? JsonObject)?.get(name)

    @Test
    fun `reblog is always null, so a boost is not visible as a boost`() {
        val statuses = body("api/timeline-home-bob-sees-boost.json").jsonArray
        assertTrue(statuses.all { it.field("reblog") == null || it.field("reblog") == JsonNull })
    }

    @Test
    fun `an account's avatar is empty and its header is the Nextcloud avatar, except in verify_credentials`() {
        val alice = body("api/accounts-alice.json")
        assertEquals("", alice.field("avatar")?.jsonPrimitive?.content)
        assertTrue(alice.field("header")?.jsonPrimitive?.content.orEmpty().contains("/index.php/avatar/alice/"))
        val me = body("api/accounts-verify-credentials.json")
        assertEquals(me.field("avatar"), me.field("header"))
    }

    @Test
    fun `a tagged place is never stored`() {
        assertEquals(JsonNull, body("api/status-place.json").field("place") ?: JsonNull)
    }

    @Test
    fun `the status entity carries no reactions while the reactions route does`() {
        assertEquals(JsonArray(emptyList()), body("api/status-public.json").field("reactions"))
        assertTrue(body("api/status-public-reactions.json").jsonArray.isNotEmpty())
    }

    @Test
    fun `without ffmpeg a video has no dimensions, no ladder and its own URL as the preview`() {
        val attachment =
            body("api/status-video.json").field("media_attachments")?.jsonArray?.first() ?: error("no attachment")
        assertEquals(JsonNull, attachment.field("hls_url") ?: JsonNull)
        assertEquals(attachment.field("url"), attachment.field("preview_url"))
        val original = attachment.field("meta")?.field("original")
        assertTrue(original == null || (original is JsonArray && original.isEmpty()))
    }

    @Test
    fun `polling routes send no ETag and forbid storing`() {
        val headers = corpus.named("api/timeline-home.json").headers
        assertFalse(headers.keys.any { it.equals("etag", ignoreCase = true) })
        assertTrue(headers["cache-control"].orEmpty().contains("no-store"))
    }

    @Test
    fun `Link headers point at the app path even through the root rules`() {
        val link = corpus.named("root/timeline-public-page-root.json").headers["link"].orEmpty()
        assertTrue(link.contains("/index.php/apps/social/api/v1/timelines/public"), link)
    }

    @Test
    fun `limit=0 is refused with a 400`() {
        assertEquals(400, corpus.named("errors/500-limit-zero.json").status)
    }

    @Test
    fun `the same idempotency key returns the same status`() {
        assertEquals(
            body("writes/idempotent-post-1.json").field("id"),
            body("writes/idempotent-post-2.json").field("id"),
        )
    }

    @Test
    fun `status ids are decimal strings beyond 2 to the 53rd`() {
        val id = body("api/status-public.json").field("id")?.jsonPrimitive ?: error("no id")
        assertTrue(id.isString)
        assertTrue(id.content.length > 16 && id.content.all(Char::isDigit))
    }

    @Test
    fun `a revoked token answers 401 with the server's own words`() {
        val error = corpus.named("errors/401-bad-token.json")
        assertEquals(401, error.status)
        assertEquals(
            "the access_token was revoked",
            body("errors/401-bad-token.json").jsonObject["error"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `there is no streaming and no Web Push key`() {
        val instance = body("plain/instance-v1-app.json")
        assertEquals(JsonObject(emptyMap()), instance.field("urls"))
        assertEquals(404, corpus.named("errors/404-streaming.json").status)
        assertEquals(404, corpus.named("errors/404-push-subscription.json").status)
    }

    @Test
    fun `the RFC 8414 document exists at the root only with the rules and names the app path either way`() {
        assertEquals(404, corpus.named("plain/oauth-authorization-server-root.json").status)
        val issuer = body("root/oauth-authorization-server-root.json").field("issuer")?.jsonPrimitive?.content
        assertTrue(issuer.orEmpty().endsWith("/index.php/apps/social/"), issuer)
    }
}
