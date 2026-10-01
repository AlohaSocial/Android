// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.photos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.testing.SignedInFixture

/**
 * Serves Photos' Explore: Pixelfed's trending posts unless [pixelfed] is off, when only Mastodon's
 * trends answer; everything fails while [down] is set.
 */
private class Discover : Dispatcher() {
    @Volatile var pixelfed = true

    @Volatile var down = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (down) return MockResponse.Builder().code(503).body("{}").build()
        return when {
            path.endsWith("/v1.1/discover/posts/trending") && pixelfed -> json("[$PHOTO,$TEXT]")
            path.endsWith("/v1.1/discover/posts/network/trending") && pixelfed -> json("[$FAR]")
            path.endsWith("/v1.1/discover/accounts/popular") && pixelfed -> json("[$ALICE]")
            path.endsWith("/v1/trends/statuses") -> json("[$FAR]")
            path.endsWith("/v1/trends/tags") -> json("""[{"name":"beach","history":[]}]""")
            else -> MockResponse.Builder().code(404).body("{}").build()
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    companion object {
        const val ALICE = """{"id":"6","username":"alice","acct":"alice"}"""
        private const val IMAGE = """[{"id":"m","type":"image","url":"https://media.example/1"}]"""
        const val PHOTO = """{"id":"p1","content":"","account":$ALICE,"media_attachments":$IMAGE}"""
        const val TEXT = """{"id":"t1","content":"<p>Words</p>","account":$ALICE}"""
        const val FAR = """{"id":"p2","content":"","account":$ALICE,"media_attachments":$IMAGE}"""
    }
}

@RunWith(RobolectricTestRunner::class)
class PhotoDiscoveryTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = Discover()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val discovery = PhotoDiscovery(fixture.clients, fixture.statuses)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `Explore keeps the pictures of each section, and stores them to open`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val explore = (discovery.explore(reader) as Answer.Got).value
        assertEquals(listOf("p1"), explore.trending.map { it.id })
        assertEquals(listOf("p2"), explore.network.map { it.id })
        assertEquals(listOf("beach"), explore.tags.map { it.name })
        assertEquals(listOf("alice"), explore.people.map { it.acct })
        assertNotNull(fixture.statuses.get(reader.id, "p1"))
    }

    @Test
    fun `a server without Pixelfed's routes trends through Mastodon's, and leaves the rest out`() = runBlocking {
        answers.pixelfed = false
        val explore = (discovery.explore(fixture.signIn(server.url("/"))) as Answer.Got).value
        assertEquals(listOf("p2"), explore.trending.map { it.id })
        assertTrue(explore.network.isEmpty() && explore.people.isEmpty())
    }

    @Test
    fun `a server that answers nothing is a failure, not an empty page`() = runBlocking {
        answers.down = true
        assertTrue(discovery.explore(fixture.signIn(server.url("/"))) is Answer.Missed)
    }
}
