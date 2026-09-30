// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.sync.PushSubscriptions
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class PushSubscriptionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val server = MockWebServer().apply { start() }
    private val push = PushSubscriptions(fixture.clients, AppPreferences(InMemoryDataStore(emptyPreferences())))

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private fun ok() = server.enqueue(
        MockResponse.Builder().code(200).body("""{"id":"1","endpoint":"e"}""")
            .addHeader("content-type", "application/json").build(),
    )

    @Test
    fun `subscribing sends the endpoint, its keys and every alert, and unsubscribing stops it`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        ok()
        assertNull(push.subscribe(account, "https://ntfy.example/up123", "pub-key", "auth-secret"))
        val sent = server.takeRequest(1, TimeUnit.SECONDS)!!
        val body = sent.body!!.utf8()
        assertEquals("POST /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
        listOf(
            "subscription%5Bendpoint%5D=https%3A%2F%2Fntfy.example%2Fup123",
            "subscription%5Bkeys%5D%5Bp256dh%5D=pub-key",
            "subscription%5Bkeys%5D%5Bauth%5D=auth-secret",
            "data%5Balerts%5D%5Bmention%5D=true",
            "data%5Bpolicy%5D=all",
        ).forEach { assertTrue(it, it in body) }
        assertTrue(push.isActive(account.id))
        ok()
        push.unsubscribe(account)
        assertEquals("DELETE", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
        assertFalse(push.isActive(account.id))
    }

    @Test
    fun `a refused subscription leaves the account polled`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(MockResponse.Builder().code(422).body("""{"error":"Validation failed"}""").build())
        assertTrue(push.subscribe(account, "https://ntfy.example/up123", "pub-key", "auth-secret") != null)
        assertFalse(push.isActive(account.id))
    }
}
