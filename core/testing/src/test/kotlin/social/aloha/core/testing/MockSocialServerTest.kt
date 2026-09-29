// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MockSocialServerTest {
    private val http = OkHttpClient()
    private var server: MockSocialServer? = null

    @AfterEach
    fun stop() {
        server?.close()
    }

    private fun start(configuration: MockServerConfiguration) = MockSocialServer(configuration).start().also {
        server =
            it
    }

    private fun MockSocialServer.get(path: String, token: String? = MockCredentials.ACCESS_TOKEN): Response =
        http.newCall(
            Request.Builder().url(origin.resolve(path) ?: error(path)).apply {
                token?.let { header("Authorization", "Bearer $it") }
            }.build(),
        ).execute()

    @Test
    fun `every corpus index lists exactly the files on disk`() {
        listOf(FixtureCorpus.nextcloudSocial, FixtureCorpus.mastodonSocial).forEach { corpus ->
            val folder = File("src/main/resources/fixtures/${corpus.folder}")
            val onDisk = folder.walkTopDown().filter { it.isFile && it.extension == "json" }
                .map { it.relativeTo(folder).invariantSeparatorsPath }.sorted().toList()
            assertEquals(onDisk, corpus.fixtures.map { it.name }.sorted(), corpus.folder)
        }
    }

    @Test
    fun `without the rewrite rules the API answers only under the app path`() {
        val mock = start(MockServerConfiguration.NextcloudWithoutRewrite)
        mock.get("/api/v2/instance").use { assertEquals(404, it.code) }
        mock.get("/index.php/apps/social/api/v2/instance").use { assertEquals(200, it.code) }
        assertEquals("/index.php/apps/social/", mock.apiBase.encodedPath)
    }

    @Test
    fun `with the rewrite rules the API answers at the root as well`() {
        val mock = start(MockServerConfiguration.NextcloudWithRewrite)
        mock.get("/api/v2/instance").use { assertEquals(200, it.code) }
        mock.get("/.well-known/oauth-authorization-server").use { assertEquals(200, it.code) }
    }

    @Test
    fun `captured origins are rewritten to the mock's own, in bodies and Link headers`() {
        val mock = start(MockServerConfiguration.NextcloudWithoutRewrite)
        mock.get("/index.php/apps/social/api/v1/timelines/public?limit=20").use { response ->
            val body = response.body.string()
            assertFalse(body.contains("http://nextcloud.local"))
            assertTrue(response.header("Link").orEmpty().startsWith("<${mock.origin.toString().trimEnd('/')}"))
        }
    }

    @Test
    fun `the most specific fixture wins and a pin overrides it`() {
        val mock = start(MockServerConfiguration.NextcloudWithoutRewrite)
        val onlyVideo = mock.get("/index.php/apps/social/api/v1/timelines/public?only_video=true&limit=20").use {
            it.body.string()
        }
        assertEquals(
            FixtureCorpus.nextcloudSocial.named("api/timeline-public-only-video.json").body.length,
            onlyVideo.length,
        )
        mock.pin("GET", "/api/v1/timelines/home", "api/timeline-home-jane-filtered.json")
        val home = mock.get("/index.php/apps/social/api/v1/timelines/home?limit=20").use { it.body.string() }
        assertTrue(home.isNotEmpty())
    }

    @Test
    fun `a viewer route refuses a missing and a revoked token`() {
        val mock = start(MockServerConfiguration.NextcloudWithoutRewrite)
        mock.get("/index.php/apps/social/api/v1/accounts/verify_credentials", token = null).use {
            assertEquals(401, it.code)
        }
        mock.get("/index.php/apps/social/api/v1/accounts/verify_credentials", MockCredentials.REVOKED_TOKEN).use {
            assertEquals(401, it.code)
            assertTrue(it.body.string().contains("revoked"))
        }
    }

    @Test
    fun `the core-only server answers the core and 404s the extensions`() {
        val mock = start(MockServerConfiguration.CoreOnly)
        mock.get("/index.php/apps/social/api/v1/timelines/public?limit=20").use { assertEquals(200, it.code) }
        mock.get("/index.php/apps/social/api/v1/stories/carousel").use { assertEquals(404, it.code) }
        mock.get("/index.php/apps/social/api/v2/config").use { assertEquals(404, it.code) }
    }

    @Test
    fun `the rate-limited server refuses the first read of a path, then answers`() {
        val mock = start(MockServerConfiguration.RateLimited)
        mock.get("/api/v2/instance").use {
            assertEquals(429, it.code)
            assertEquals("1", it.header("Retry-After"))
        }
        mock.get("/api/v2/instance").use { assertEquals(200, it.code) }
    }

    @Test
    fun `the malformed server breaks the third timeline entry only`() {
        val body = start(MockServerConfiguration.MalformedEntities)
            .get("/index.php/apps/social/api/v1/timelines/public?limit=20").use { it.body.string() }
        val array = Json.parseToJsonElement(body) as JsonArray
        assertEquals(JsonNull, array[2].jsonObject["id"])
        assertTrue(array.filterIndexed { index, _ -> index != 2 }.all { it.jsonObject["id"] != JsonNull })
    }

    @Test
    fun `the Mastodon server signs in with a registration, a token and an account`() {
        val mock = start(MockServerConfiguration.Mastodon)
        val register = http.newCall(
            Request.Builder().url(mock.origin.resolve("/api/v1/apps") ?: error("")).post("".toRequestBody()).build(),
        ).execute()
        register.use { assertTrue(it.body.string().contains(MockCredentials.CLIENT_ID)) }
        mock.get("/api/v1/accounts/verify_credentials").use { assertEquals(200, it.code) }
        mock.get("/api/v1/stories/carousel").use { assertEquals(404, it.code) }
    }

    @Test
    fun `the connected server runs Login Flow v2`() {
        val mock = start(MockServerConfiguration.NextcloudConnected)
        val poll = http.newCall(
            Request.Builder().url(
                mock.origin.resolve("/index.php/login/v2/poll") ?: error(""),
            ).post("token=x".toRequestBody()).build(),
        ).execute()
        poll.use { assertTrue(it.body.string().contains(MockCredentials.APP_PASSWORD)) }
    }
}
