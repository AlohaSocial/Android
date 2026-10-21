// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.capabilities

import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import social.aloha.core.model.InstanceDescription
import social.aloha.core.model.NodeInfo
import social.aloha.core.network.RateLimiter

class CapabilityDetectorTest {
    private var health: MockResponse = MockResponse.Builder().code(404).build()
    private val asked = mutableListOf<String>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(asked) { asked += path }
                return if (path == "/api/v1/streaming/health") health else MockResponse.Builder().code(404).build()
            }
        }
        start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private suspend fun streamingUrl(software: String): String? {
        val streaming = server.url("/").toString().replaceFirst("http", "ws")
        return CapabilityDetector(OkHttpClient(), RateLimiter(nowMillis = { 0L }), Dispatchers.IO) { Instant.EPOCH }
            .detect(
                server.url("/"),
                null,
                InstanceDescription(domain = "stream.test", streamingUrl = streaming),
                NodeInfo("2.0", software, "0.26.280"),
            ).streamingUrl
    }

    @Test
    fun `Nextcloud Social's stream counts only when its daemon answers the health route`() = runTest {
        health = MockResponse.Builder().code(200).body("OK\n").build()
        assertEquals(server.url("/").toString().replaceFirst("http", "ws"), streamingUrl("nextcloud-social"))

        health = MockResponse.Builder().code(502).body("Bad Gateway").build()
        assertNull(streamingUrl("nextcloud-social"))

        // a page some other part of the host answers with is not the daemon
        health = MockResponse.Builder().code(200).body("<html>Nextcloud</html>").build()
        assertNull(streamingUrl("nextcloud-social"))
    }

    @Test
    fun `Mastodon's stream is taken as announced, without asking`() = runTest {
        assertEquals(server.url("/").toString().replaceFirst("http", "ws"), streamingUrl("mastodon"))
        assertFalse(synchronized(asked) { "/api/v1/streaming/health" in asked })
    }
}
