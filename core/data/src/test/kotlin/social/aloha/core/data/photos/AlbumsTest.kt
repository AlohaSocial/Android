// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.photos

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.Answer
import social.aloha.core.model.MediaCollection
import social.aloha.core.testing.SignedInFixture

/** Answers the album routes as Nextcloud Social does, and remembers every request with its body. */
private class AlbumServer : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        asked += "${request.method} $path ${request.body?.utf8().orEmpty()}"
        return when {
            path.endsWith("/collections/1/items") && request.method == "GET" -> json("[$POST]")
            path.contains("/collections/1/items") -> json("{}")
            path.endsWith("/collections/1") && request.method == "PUT" -> json(ALBUM.replace("Beach", "Shore"))
            path.endsWith("/collections") && request.method == "POST" -> json(ALBUM)
            else -> json("[$ALBUM]")
        }
    }

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()

    companion object {
        const val ALBUM = """{"id":"1","title":"Beach","description":"Summer","visibility":"private","size":1}"""
        const val POST = """{"id":"p1","content":"<p>Sand</p>",""" +
            """"account":{"id":"6","username":"alice","acct":"alice"},""" +
            """"media_attachments":[{"id":"m1","type":"image","url":"https://media.example/1"}]}"""
    }
}

@RunWith(RobolectricTestRunner::class)
class AlbumsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = AlbumServer()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `an album is made, renamed keeping what it holds, and its posts are stored to open`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val made = (fixture.albums.create(reader, "  Beach ", "Summer") as Answer.Got).value
        assertTrue(answers.asked.last().startsWith("POST /api/v1/collections title=Beach&description=Summer"))
        val album = MediaCollection(made.id, made.title, made.description, visibility = made.visibility)
        val renamed = (fixture.albums.rename(reader, album, "Shore") as Answer.Got).value
        assertEquals("Shore", renamed.title)
        // a rename carries the description and visibility along, or the server would blank them
        assertTrue(answers.asked.last().contains("title=Shore&description=Summer&visibility=private"))
        val posts = (fixture.albums.posts(reader, "1") as Answer.Got).value
        assertEquals(listOf("p1"), posts.map { it.id })
        assertNotNull(fixture.statuses.get(reader.id, "p1"))
    }

    @Test
    fun `a post goes in by form and comes out by path`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        fixture.albums.add(reader, "1", "p1")
        assertEquals("POST /api/v1/collections/1/items status_id=p1", answers.asked.last())
        fixture.albums.remove(reader, "1", "p1")
        assertEquals("DELETE /api/v1/collections/1/items/p1 ", answers.asked.last())
    }
}
