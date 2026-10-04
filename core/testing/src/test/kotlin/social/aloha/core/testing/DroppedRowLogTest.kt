// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import social.aloha.core.data.diagnostics.LogBuffer
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.ApiClient
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Credentials
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.endpoints.TimelineEndpoints
import timber.log.Timber

class DroppedRowLogTest {
    private val buffer = LogBuffer(Clock.systemUTC()).also { Timber.plant(it) }
    private val server = MockSocialServer(MockServerConfiguration.MalformedEntities).start()

    @AfterEach
    fun close() {
        Timber.uprootAll()
        server.close()
    }

    @Test
    fun `a row the server sent malformed leaves one warning with path, type and index, never the JSON`() {
        val client = ApiClient(
            server.apiBase,
            { Credentials(MockCredentials.ACCESS_TOKEN) },
            OkHttpClient(),
            RateLimiter(nowMillis = Clock.systemUTC()::millis),
            Dispatchers.IO,
        )

        val page = runBlocking { client.execute(TimelineEndpoints.timeline(TimelineSource.Federated)) }

        assertTrue(page is ApiResult.Success)
        val warnings = buffer.lines().filter { " W/" in it }
        assertEquals(1, warnings.size, warnings.joinToString("\n"))
        val warning = warnings.single()
        assertTrue(" W/Network: Dropped " in warning && " at index 2 of api/v1/timelines/public" in warning, warning)
        assertTrue('{' !in warning, warning)
    }
}
