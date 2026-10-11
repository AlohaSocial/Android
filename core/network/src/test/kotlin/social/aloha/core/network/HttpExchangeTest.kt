// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpExchangeTest {
    private val server = MockWebServer().apply { start() }
    private val exchange = HttpExchange(OkHttpClient(), RateLimiter(nowMillis = { 0L }), Dispatchers.IO)

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun propfind() = Request.Builder().url(server.url("/remote.php/dav/files/alice/"))
        .method("PROPFIND", "<d:propfind xmlns:d=\"DAV:\"/>".toRequestBody()).build()

    @Test
    fun `an answer comes back whatever its status, with the request as it was built`() = runTest {
        server.enqueue(MockResponse.Builder().code(412).body("<d:error/>").build())
        val answer = exchange.exchange(propfind(), "PROPFIND files")
        val response = (answer as ApiResult.Success).value
        response.use { assertEquals(412, it.code) }
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test
    fun `an answer nobody can use maps as the API client would map it`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("<d:error/>").build())
        val response = (exchange.exchange(propfind(), "PROPFIND files") as ApiResult.Success).value
        assertInstanceOf(ApiError.Unauthorised::class.java, exchange.error(response))
    }

    @Test
    fun `a 429 holds the host back for its Retry-After, as an API call's does`() = runTest {
        var now = 0L
        val limiter = RateLimiter(nowMillis = { now })
        val limited = HttpExchange(OkHttpClient(), limiter, Dispatchers.IO)
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
        val response = (limited.exchange(propfind(), "PROPFIND files") as ApiResult.Success).value
        assertInstanceOf(ApiError.RateLimited::class.java, limited.error(response))
        assertTrue(limiter.isBlocked(server.hostName))
        now += 31_000
        assertFalse(limiter.isBlocked(server.hostName))
    }

    @Test
    fun `a server that cannot be reached is a transport failure`() = runTest {
        val gone = server.url("/")
        server.close()
        val answer = exchange.exchange(Request.Builder().url(gone).build(), "GET files")
        assertInstanceOf(ApiError.Transport::class.java, (answer as ApiResult.Failure).error)
    }
}
