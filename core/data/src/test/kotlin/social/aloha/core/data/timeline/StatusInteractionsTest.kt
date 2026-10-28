// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.testing.SignedInFixture
import social.aloha.core.testing.StatusSamples

@RunWith(RobolectricTestRunner::class)
class StatusInteractionsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val asked = CopyOnWriteArrayList<String>()
    private val firstHeld = CountDownLatch(1)

    @Volatile private var refused = false
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                asked += path
                if (asked.size == 1) firstHeld.await(10, TimeUnit.SECONDS)
                if (refused) return MockResponse.Builder().code(422).body("""{"error":"No"}""").build()
                val favourited = path.endsWith("/favourite")
                return MockResponse.Builder().code(200).body(post(favourited))
                    .addHeader("content-type", "application/json").build()
            }
        }
        start()
    }
    private val interactions = StatusInteractions(fixture.statuses, fixture.clients)

    @After
    fun close() {
        firstHeld.countDown()
        fixture.close()
        server.close()
    }

    @Test
    fun `a second tap waits for the first to be answered, and its own answer is the one kept`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val shown = StatusSamples.post()
        tapTwice(reader, shown)
        assertEquals(listOf("/api/v1/statuses/10/favourite", "/api/v1/statuses/10/unfavourite"), asked)
        assertFalse(fixture.statuses.get(reader.id, shown.id)!!.favourited)
    }

    @Test
    fun `two taps both refused leave the post as the server had it`() = runBlocking {
        refused = true
        val reader = fixture.signIn(server.url("/"))
        val shown = StatusSamples.post().copy(favourited = true, favouritesCount = 4)
        tapTwice(reader, shown)
        val kept = fixture.statuses.get(reader.id, shown.id)!!
        assertTrue(kept.favourited)
        assertEquals(4, kept.favouritesCount)
    }

    @Test
    fun `taps started together each start from the one before`() = runBlocking {
        firstHeld.countDown()
        val reader = fixture.signIn(server.url("/"))
        val shown = StatusSamples.post()
        fixture.statuses.save(reader.id, shown)
        val taps = List(4) { async(Dispatchers.IO) { interactions.toggle(reader, shown, Toggle.Favourite) } }
        taps.forEach { it.await() }
        assertEquals(
            listOf("favourite", "unfavourite", "favourite", "unfavourite"),
            asked.map {
                it.substringAfterLast('/')
            },
        )
        assertFalse(fixture.statuses.get(reader.id, shown.id)!!.favourited)
    }

    @Test
    fun `a tap given up before it is sent leaves the answer to the one before`() = runBlocking {
        val reader = fixture.signIn(server.url("/"))
        val shown = StatusSamples.post()
        fixture.statuses.save(reader.id, shown)
        val first = async(Dispatchers.IO) { interactions.toggle(reader, shown, Toggle.Favourite) }
        withTimeout(10.seconds) { while (asked.isEmpty()) delay(10) }
        val second = async(Dispatchers.IO) { interactions.toggle(reader, shown, Toggle.Favourite) }
        withTimeout(10.seconds) { while (fixture.statuses.get(reader.id, shown.id)!!.favourited) delay(10) }
        second.cancelAndJoin()
        firstHeld.countDown()
        first.await()
        assertEquals(1, asked.size)
        assertTrue(fixture.statuses.get(reader.id, shown.id)!!.favourited)
    }

    /** Favourite taps twice on [shown], the second while the first is still unanswered. */
    private suspend fun tapTwice(reader: SignedInAccount, shown: Status) = coroutineScope {
        fixture.statuses.save(reader.id, shown)
        val first = async(Dispatchers.IO) { interactions.toggle(reader, shown, Toggle.Favourite) }
        withTimeout(10.seconds) { while (asked.isEmpty()) delay(10) }
        val second = async(Dispatchers.IO) { interactions.toggle(reader, shown, Toggle.Favourite) }
        // the second guess shows while the first is unanswered, and the second waits to be sent
        withTimeout(10.seconds) {
            while (fixture.statuses.get(reader.id, shown.id)!!.favourited != shown.favourited) delay(10)
        }
        assertEquals(1, asked.size)
        firstHeld.countDown()
        first.await()
        second.await()
    }

    private companion object {
        fun post(favourited: Boolean) =
            """{"id":"10","created_at":"2026-10-05T10:00:00.000Z","visibility":"public",""" +
                """"favourited":$favourited,"account":{"id":"1","username":"alice","acct":"alice"}}"""
    }
}
