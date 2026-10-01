// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.saved

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.testing.SignedInFixture

/**
 * Bookmarks page by the bookmark's own id, which only the `Link` header knows; the archive sends no
 * header, and pages from its oldest post.
 */
private class SavedServer : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        asked += "${url.encodedPath}${url.encodedQuery?.let { "?$it" }.orEmpty()}"
        val path = url.encodedPath
        return when {
            path.endsWith("/bookmarks") && url.queryParameter("max_id") == null -> json(
                "[${post("9")},${post("4")}]",
                link = "<${url.newBuilder().addQueryParameter("max_id", "77").build()}>; rel=\"next\"",
            )

            path.endsWith("/bookmarks") -> json("[${post("2")}]")

            // a full page, and no cursor
            path.endsWith("/favourites/") -> json((40 downTo 1).joinToString(",", "[", "]") { post("$it") })

            path.endsWith("/archive/list") && url.queryParameter("max_id") == null ->
                json((40 downTo 1).joinToString(",", "[", "]") { post("$it") })

            else -> json("[]")
        }
    }

    private fun post(id: String) = """{"id":"$id","created_at":"2026-09-29T10:00:00Z","content":"<p>$id</p>",""" +
        """"account":{"id":"6","username":"alice","acct":"alice"}}"""

    private fun json(body: String, link: String? = null) = MockResponse.Builder().code(200).body(body)
        .addHeader("content-type", "application/json")
        .apply { link?.let { addHeader("Link", it) } }
        .build()
}

@RunWith(RobolectricTestRunner::class)
class SavedPostsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = SavedServer()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val saved = SavedPosts(fixture.clients, fixture.statuses)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `bookmarks page on along the server's cursor, not from the oldest post's id`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val first = (saved.bookmarks(reader) as Answer.Got).value
        assertEquals(listOf("9", "4"), first.posts.map { it.id })
        assertFalse(first.done)
        val next = (saved.bookmarks(reader, first) as Answer.Got).value
        assertEquals(listOf("2"), next.posts.map { it.id })
        assertTrue(next.done)
        assertTrue(answers.asked.any { it.contains("/bookmarks") && "max_id=77" in it })
    }

    @Test
    fun `the archive, which sends no cursor, pages on from its oldest post`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val first = (saved.archived(reader) as Answer.Got).value
        assertFalse(first.done)
        val next = (saved.archived(reader, first) as Answer.Got).value
        assertTrue(next.done)
        assertTrue(answers.asked.any { it.contains("/archive/list") && "max_id=1" in it })
    }

    @Test
    fun `favourites without the server's cursor end there, since their order is not the posts' ids`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        assertTrue((saved.favourites(reader) as Answer.Got).value.done)
    }
}
