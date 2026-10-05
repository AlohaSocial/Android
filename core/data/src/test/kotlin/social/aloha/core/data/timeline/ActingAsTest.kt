// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.RemoteLookup
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiError
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class ActingAsTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val asked = CopyOnWriteArrayList<String>()
    private var found = true
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                asked += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}"
                val body = when {
                    request.url.encodedPath.endsWith("/search") ->
                        if (found) """{"accounts":[],"hashtags":[],"statuses":[$POST]}""" else """{"statuses":[]}"""

                    else -> POST
                }
                return MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build()
            }
        }
        start()
    }
    private val interactions = StatusInteractions(fixture.statuses, fixture.clients)
    private val acting =
        ActingAs(fixture.accounts, RemoteLookup(fixture.clients, fixture.statuses), fixture.statuses, interactions)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    @Test
    fun `a post found by its address on the other account's server is acted on by its id there`() = runBlocking {
        val other = fixture.signIn(server.url("/"))
        assertNull(acting.toggle(other.id, "https://elsewhere.example/@bob/1", Toggle.Boost, Visibility.Unlisted))
        assertEquals("POST /api/v1/statuses/77/reblog visibility=unlisted", asked.last())
    }

    @Test
    fun `a post the other server cannot find says so, and nothing is acted on`() = runBlocking {
        found = false
        val other = fixture.signIn(server.url("/"))
        assertEquals(ApiError.NotFound, acting.toggle(other.id, "https://elsewhere.example/@bob/1", Toggle.Favourite))
        assertEquals(1, asked.size)
    }

    private companion object {
        const val POST = """{"id":"77","created_at":"2026-10-05T10:00:00.000Z","visibility":"public",""" +
            """"account":{"id":"2","username":"bob","acct":"bob@elsewhere.example"}}"""
    }
}
