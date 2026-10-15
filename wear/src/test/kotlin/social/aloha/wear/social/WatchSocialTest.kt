// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.social

import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.datastore.TokenVault
import social.aloha.core.network.RateLimiter
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.NumberedTimeline
import social.aloha.wear.auth.WatchAccount
import social.aloha.wear.auth.WatchAccounts

class WatchSocialTest {
    private val timeline = NumberedTimeline().apply { newest = 30 }
    private val server = MockWebServer().apply {
        dispatcher = timeline
        start()
    }
    private val accounts =
        WatchAccounts(TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO))
    private val social =
        WatchSocial(accounts, OkHttpClient(), RateLimiter(nowMillis = Clock.systemUTC()::millis), Dispatchers.IO)

    private fun signIn(base: String = server.url("/").toString()) = runBlocking {
        accounts.keep(WatchAccount(base, "alice", "watch-token"))
    }

    @After
    fun close() {
        server.close()
    }

    @Test
    fun `home is the newest twenty posts, as plain text, asked for with the watch's own token`() = runBlocking {
        signIn()
        val posts = social.home()!!

        assertEquals(20, posts.size)
        assertEquals("30", posts.first().id)
        assertFalse(posts.first().text.contains('<'))
        val asked = server.takeRequest()
        assertEquals("20", asked.url.queryParameter("limit"))
        assertEquals("Bearer watch-token", asked.headers["Authorization"])
    }

    @Test
    fun `a favourite set from the watch is set on the server, and taken back the same way`() = runBlocking {
        signIn()
        val post = social.home()!!.first()

        val favourited = social.toggle(post, WatchSocial.Mark.Favourite)!!
        assertTrue(favourited.favourited)
        val back = social.toggle(favourited, WatchSocial.Mark.Favourite)!!
        assertFalse(back.favourited)
        val paths = generateSequence { server.takeRequest(0, java.util.concurrent.TimeUnit.SECONDS) }
            .map { it.url.encodedPath }.toList()
        assertTrue(
            paths.toString(),
            paths.containsAll(listOf("/api/v1/statuses/30/favourite", "/api/v1/statuses/30/unfavourite")),
        )
    }

    @Test
    fun `a reply goes to the post's author, and to nobody on the reader's own post`() = runBlocking {
        signIn()
        val theirs = social.home()!!.first().copy(authorHandle = "kai")
        server.takeRequest()

        social.reply(theirs, "Shaka")
        assertEquals("@kai Shaka", server.takeRequest().formStatus())
        social.reply(theirs.copy(authorHandle = "alice"), "Shaka")
        assertEquals("Shaka", server.takeRequest().formStatus())
    }

    private fun mockwebserver3.RecordedRequest.formStatus(): String? =
        body?.utf8()?.split('&')?.map { java.net.URLDecoder.decode(it, "UTF-8") }
            ?.firstOrNull { it.startsWith("status=") }?.removePrefix("status=")

    @Test
    fun `a token the server refuses signs the watch out`() = runBlocking {
        val refusing = MockWebServer().apply {
            enqueue(MockResponse.Builder().code(401).body("""{"error":"The access token was revoked"}""").build())
            start()
        }
        signIn(refusing.url("/").toString())

        assertNull(social.home())
        assertNull(accounts.current())
        refusing.close()
    }

    @Test
    fun `a refused token forgets only the account that still holds it`() = runBlocking {
        accounts.keep(WatchAccount(server.url("/").toString(), "alice", "new-token"))
        accounts.forget("old-token")

        assertEquals("new-token", accounts.current()?.token)
    }

    @Test
    fun `signed out, the watch asks the server nothing`() = runBlocking {
        assertNull(social.home())
        assertEquals(0, server.requestCount)
    }
}
