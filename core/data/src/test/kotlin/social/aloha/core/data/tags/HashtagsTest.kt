// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.tags

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
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
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.network.ApiError
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

/** #surf and #reef share post 2; every request remembered. */
private class TagServer : Dispatcher() {
    val asked = CopyOnWriteArrayList<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val url = request.url
        asked += "${request.method} ${url.encodedPath}${url.encodedQuery?.let { "?$it" }.orEmpty()}"
        return when {
            url.encodedPath.endsWith("/timelines/tag/surf") && url.queryParameter("max_id") == null ->
                json("[${post("3", "10:03")},${post("2", "10:02")}]")

            url.encodedPath.endsWith("/timelines/tag/reef") && url.queryParameter("max_id") == null ->
                json("[${post("2", "10:02")},${post("1", "10:01")}]")

            url.encodedPath.contains("/timelines/tag/") -> json("[]")

            url.encodedPath.endsWith("/follow") -> json("""{"name":"nextcloud","following":true}""")

            else -> json("{}")
        }
    }

    private fun post(id: String, time: String) =
        """{"id":"$id","created_at":"2026-09-29T$time:00Z","content":"<p>$id</p>",""" +
            """"account":{"id":"6","username":"alice","acct":"alice"}}"""

    private fun json(body: String) =
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
}

@RunWith(RobolectricTestRunner::class)
class HashtagsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val answers = TagServer()
    private val server = MockWebServer().apply {
        dispatcher = answers
        start()
    }
    private val hashtags =
        Hashtags(fixture.clients, fixture.statuses, AccountSettingsStore(InMemoryDataStore(emptyMap())))

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `a hashtag is followed as the server stores it, and one that is nothing is refused unasked`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        assertEquals(true, (hashtags.follow(reader, " #NextCloud ", follow = true) as Answer.Got).value.following)
        assertTrue(answers.asked.any { it == "POST /api/v1/tags/nextcloud/follow" })
        val refused = hashtags.follow(reader, " # ", follow = true)
        assertEquals(Answer.Missed(ApiError.Unprocessable(null)), refused)
        assertEquals(1, answers.asked.count { it.endsWith("/follow") })
    }

    @Test
    fun `a tag group reads as one timeline, each post once, newest first, never above what was shown`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val first = (hashtags.groupPage(reader, listOf("surf", "reef")) as Answer.Got).value
        // #reef has reached further back than #surf, whose next page may still hold posts after 10:01
        assertEquals(listOf("3", "2"), first.posts.map { it.id })
        assertFalse(first.done)
        val next = (hashtags.groupPage(reader, listOf("surf", "reef"), first.cursor) as Answer.Got).value
        assertEquals(listOf("1"), next.posts.map { it.id })
        assertTrue(next.done)
        assertTrue(answers.asked.any { it.contains("/timelines/tag/surf") && "max_id=2" in it })
        // a tag that ran out is not asked again
        val after = (hashtags.groupPage(reader, listOf("surf", "reef"), next.cursor) as Answer.Got).value
        assertTrue(after.done)
        assertEquals(4, answers.asked.count { it.contains("/timelines/tag/") })
    }

    @Test
    fun `an empty tag group is done without asking`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        assertTrue((hashtags.groupPage(reader, emptyList()) as Answer.Got).value.done)
        assertTrue(answers.asked.none { it.contains("/timelines/tag/") })
    }

    @Test
    fun `a group keeps its hashtags normalised, once each, and a group of none is not kept`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        hashtags.saveGroup(reader, "Sea", listOf("#Surf", "surf", "reef"))
        assertEquals(listOf(TagGroup("Sea", listOf("surf", "reef"))), hashtags.groups(reader).first())
        hashtags.saveGroup(reader, "Ocean", listOf("#Surf"), previous = "Sea")
        assertEquals(listOf("Ocean"), hashtags.groups(reader).first().map { it.name })
        hashtags.saveGroup(reader, "Ocean", listOf("#"), previous = "Ocean")
        assertTrue(hashtags.groups(reader).first().isEmpty())
    }

    @Test
    fun `a group never quietly replaces another, nor goes without a name`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        assertTrue(hashtags.saveGroup(reader, "Sea", listOf("surf")))
        assertTrue(hashtags.saveGroup(reader, "Coast", listOf("reef")))
        assertFalse(hashtags.saveGroup(reader, " Sea ", listOf("kite")))
        assertFalse(hashtags.saveGroup(reader, "Sea", listOf("kite"), previous = "Coast"))
        assertFalse(hashtags.saveGroup(reader, " ", listOf("kite")))
        val groups = hashtags.groups(reader).first().associate { it.name to it.tags }
        assertEquals(mapOf("Coast" to listOf("reef"), "Sea" to listOf("surf")), groups)
    }
}
