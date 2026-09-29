// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import social.aloha.core.model.CustomEmoji
import social.aloha.core.network.dto.CustomEmojiDto
import social.aloha.core.network.dto.toDomain

class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var base: HttpUrl
    private val rateLimiter = RateLimiter(nowMillis = { 0L })
    private val dropped = mutableListOf<DecodingFailure>()

    @BeforeEach
    fun start() {
        server.start()
        base = server.url("/index.php/apps/social/")
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun client(credentials: Credentials = Credentials("secret-token", "Basic YWxpY2U6cHc=")) = ApiClient(
        apiBase = base,
        credentials = { credentials },
        http = OkHttpClient(),
        rateLimiter = rateLimiter,
        ioDispatcher = Dispatchers.IO,
        failureListener = { _, failures -> dropped += failures },
    )

    private fun emojis(authentication: Authentication = Authentication.Bearer, idempotencyKey: String? = null) =
        listRequest(
            Endpoint("api/v1/custom_emojis", authentication = authentication, idempotencyKey = idempotencyKey),
            CustomEmojiDto.serializer(),
        ) { it.toDomain() }

    private fun respond(body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) {
        server.enqueue(
            MockResponse.Builder().code(code).body(body).apply {
                headers.forEach { (k, v) -> addHeader(k, v) }
            }.build(),
        )
    }

    @Test
    fun `a bearer route sends the token and composes onto a non-root API base`() = runTest {
        respond("""[{"shortcode":"a"}]""")
        val result = client().execute(emojis())
        assertEquals(ApiResult.Success(listOf(CustomEmoji("a"))), result)
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("/index.php/apps/social/api/v1/custom_emojis", recorded?.url?.encodedPath)
        assertEquals("Bearer secret-token", recorded?.headers?.get("Authorization"))
        assertEquals("application/json", recorded?.headers?.get("Accept"))
        assertNull(recorded?.headers?.get("OCS-APIRequest"))
    }

    @Test
    fun `a Nextcloud session route sends the app password with OCS-APIRequest`() = runTest {
        respond("[]")
        client().execute(emojis(Authentication.NextcloudSession))
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("Basic YWxpY2U6cHc=", recorded?.headers?.get("Authorization"))
        assertEquals("true", recorded?.headers?.get("OCS-APIRequest"))
    }

    @Test
    fun `a public route sends no credential`() = runTest {
        respond("[]")
        client().execute(emojis(Authentication.None))
        assertNull(server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Authorization"))
    }

    @Test
    fun `a route whose credential is missing is refused before it leaves the device`() = runTest {
        val result = client(Credentials(bearerToken = null)).execute(emojis())
        assertEquals(ApiResult.Failure(ApiError.Unauthorised(null)), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an idempotency key travels as a header`() = runTest {
        respond("[]")
        client().execute(emojis(idempotencyKey = "draft-7"))
        assertEquals("draft-7", server.takeRequest(1, TimeUnit.SECONDS)?.headers?.get("Idempotency-Key"))
    }

    @Test
    fun `a page counts what the server sent, not what survived decoding`() = runTest {
        respond(
            """[{"shortcode":"a"},{"url":"broken"},{"shortcode":"b"}]""",
            headers = mapOf("Link" to "<${base}api/v1/custom_emojis?max_id=2>; rel=\"next\""),
        )
        val page = client().page(emojis(), limit = 3).valueOrNull()
        assertEquals(listOf("a", "b"), page?.items?.map { it.shortcode })
        assertEquals(3, page?.rawCount)
        assertEquals("2", page?.link?.next?.queryParameter("max_id"))
        assertEquals(listOf(1), dropped.map { it.index })
    }

    @Test
    fun `a cursor on another host is never followed`() = runTest {
        val result = client().page("https://attacker.example/steal".toHttpUrl(), emojis(), limit = 20)
        assertEquals(ApiResult.Failure(ApiError.ForeignCursor("attacker.example")), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a cursor on the same origin is followed verbatim`() = runTest {
        respond("[]")
        client().page(
            base.resolve("api/v1/custom_emojis?only_video=true&max_id=9") ?: error("bad url"),
            emojis(),
            limit = 20,
        )
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("only_video=true&max_id=9", recorded?.url?.query)
        assertEquals("Bearer secret-token", recorded?.headers?.get("Authorization"))
    }

    @Test
    fun `a 429 blocks the host for Retry-After`() = runTest {
        respond("", code = 429, headers = mapOf("Retry-After" to "30"))
        val result = client().execute(emojis())
        assertInstanceOf(ApiResult.Failure::class.java, result)
        assertTrue(rateLimiter.isBlocked(base.host))
    }

    @Test
    fun `a body of the wrong shape is a decoding error, not a crash`() = runTest {
        respond("""{"not":"a list"}""")
        val single = request(Endpoint("api/v1/x"), CustomEmojiDto.serializer()) { it.toDomain() }
        assertInstanceOf(ApiError.Decoding::class.java, client().execute(single).errorOrNull())
    }

    @Test
    fun `a server-supplied id that would climb the path is refused before it is sent`() = runTest {
        listOf("..", "%2e%2E", ".", "x/../../accounts").forEach { id ->
            val request = unitRequest(Endpoint("api/v1/statuses/$id/favourite", HttpMethod.POST))
            assertInstanceOf(ApiError.UnsafePath::class.java, client().execute(request).errorOrNull(), id)
        }
        assertEquals(0, server.requestCount)
    }
}
