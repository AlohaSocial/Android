// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.video

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.testing.SignedInFixture

/** A clock a test moves by hand. */
private class HandClock(var now: Instant = Instant.parse("2026-09-30T12:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

/**
 * Nextcloud Social's watch routes, every request remembered; the videos to carry on with are two, one
 * of them sent without its post.
 */
private class Watched : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path ${request.body?.utf8().orEmpty()}".trim()
        return when {
            path.endsWith("/videos/continue") -> json(
                """[{"status_id":"v1","position":120,"duration":600},""" +
                    """{"status_id":"v2","position":30,"duration":60,"status":$POST}]""",
            )

            path.endsWith("/statuses/v1") -> json(POST.replace("\"v2\"", "\"v1\""))

            else -> json("{}")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    private companion object {
        const val POST = """{"id":"v2","content":"","account":{"id":"6","username":"alice","acct":"alice"},""" +
            """"media_attachments":[{"id":"m","type":"video","url":"https://media.example/v.mp4"}]}"""
    }
}

@RunWith(RobolectricTestRunner::class)
class WatchPositionsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Watched()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val clock = HandClock()
    private val positions = fixture.watchPositions(clock)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private suspend fun reader(watching: Boolean = true): SignedInAccount = fixture.signIn(
        server.url("/"),
        ServerCapabilities.minimal(server.url("/").toString()).copy(watchPositions = watching),
    )

    private fun reports() = answers.asked.filter { it.startsWith("POST") }

    @Test
    fun `reports come every five seconds at most, and a pause tells the latest position at once`() = runBlocking {
        val reader = reader()
        // nothing under ten seconds is told
        positions.report(reader, "v", positionSeconds = 8.0, durationSeconds = 600.0)
        positions.report(reader, "v", positionSeconds = 20.0, durationSeconds = 600.0)
        clock.now = clock.now.plusSeconds(3)
        positions.report(reader, "v", positionSeconds = 23.0, durationSeconds = 600.0)
        positions.report(reader, "v", positionSeconds = 23.0, durationSeconds = 600.0, forced = true)
        // a second pause at the same place has nothing new to tell
        positions.report(reader, "v", positionSeconds = 23.4, durationSeconds = 600.0, forced = true)
        assertEquals(
            listOf(
                "POST /api/v1/statuses/v/watched position=20&duration=600",
                "POST /api/v1/statuses/v/watched position=23&duration=600",
            ),
            reports(),
        )
        assertEquals(23.0, positions.resumeAt(reader.id, "v"))
        assertEquals(23.0 / 600.0, positions.observe(reader.id).first().getValue("v"), 1e-9)
    }

    @Test
    fun `a video watched to its end is forgotten here, as the server forgets it`() = runBlocking {
        val reader = reader()
        positions.report(reader, "v", positionSeconds = 100.0, durationSeconds = 600.0)
        clock.now = clock.now.plusSeconds(10)
        positions.report(reader, "v", positionSeconds = 590.0, durationSeconds = 600.0)
        assertNull(positions.resumeAt(reader.id, "v"))
        assertEquals(2, reports().size)
    }

    @Test
    fun `a server that keeps no positions is not told, and the bar still shows here`() = runBlocking {
        val reader = reader(watching = false)
        positions.report(reader, "v", positionSeconds = 60.0, durationSeconds = 600.0)
        assertEquals(emptyList<String>(), reports())
        assertEquals(60.0, positions.resumeAt(reader.id, "v"))
    }

    @Test
    fun `the videos to carry on with come with their posts, fetched where the server sent none`() = runBlocking {
        val reader = reader()
        val watched = (positions.continueWatching(reader) as Answer.Got).value
        assertEquals(listOf("v1", "v2"), watched.map { it.status.id })
        assertEquals(120.0, positions.resumeAt(reader.id, "v1"))
        positions.remove(reader, "v1")
        assertEquals("DELETE /api/v1/statuses/v1/watched", answers.asked.last())
        assertNull(positions.resumeAt(reader.id, "v1"))
    }
}
