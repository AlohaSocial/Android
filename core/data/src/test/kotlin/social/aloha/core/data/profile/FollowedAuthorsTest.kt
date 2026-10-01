// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.testing.SignedInFixture

/** Says the reader follows account 1, says nothing of anyone else, and refuses once [refusing]. */
private class Relationships : Dispatcher() {
    val asked = AtomicInteger()

    @Volatile var refusing = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        asked.incrementAndGet()
        if (refusing) return MockResponse.Builder().code(503).build()
        return MockResponse.Builder().code(200).addHeader("content-type", "application/json")
            .body("""[{"id":"1","following":true}]""").build()
    }
}

@RunWith(RobolectricTestRunner::class)
class FollowedAuthorsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Relationships()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val followed = FollowedAuthors(fixture.clients)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `one the server says nothing of is not followed, and is not asked about again`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        followed.ask(reader, listOf("1", "2"))
        assertEquals(mapOf("1" to true, "2" to false), followed.observe(reader.id).first())
        followed.ask(reader, listOf("1", "2"))
        assertEquals(1, answers.asked.get())
    }

    @Test
    fun `a server that refuses is not asked again at once`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        answers.refusing = true
        followed.ask(reader, listOf("3"))
        followed.ask(reader, listOf("3"))
        assertEquals(1, answers.asked.get())
        assertEquals(emptyMap<String, Boolean>(), followed.observe(reader.id).first())
    }
}
