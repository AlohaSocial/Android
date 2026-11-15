// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.sync

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
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.PushRefusals
import social.aloha.core.model.NotificationsFrom
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class PushSubscriptionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val server = MockWebServer().apply { start() }
    private val push = PushSubscriptions(
        fixture.clients,
        fixture.preferences,
        AccountSettingsStore(InMemoryDataStore(emptyMap())),
        PushRefusals(InMemoryDataStore(emptyPreferences())),
    )

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
        val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = checkNotNull(sent.body).utf8()
        assertEquals("POST /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
        listOf(
            "subscription%5Bendpoint%5D=https%3A%2F%2Fntfy.example%2Fup123",
            "subscription%5Bkeys%5D%5Bp256dh%5D=pub-key",
            "subscription%5Bkeys%5D%5Bauth%5D=auth-secret",
            "data%5Bpolicy%5D=all",
        ).forEach { assertTrue(it, it in body) }
        listOf(
            "mention", "status", "reblog", "follow", "follow_request", "favourite", "poll", "update",
            "admin.sign_up", "admin.report", "quote", "quoted_update",
        ).forEach { assertTrue(it, "data%5Balerts%5D%5B$it%5D=true" in body) }
        assertTrue(push.isActive(account.id))
        ok()
        push.unsubscribe(account)
        assertEquals("DELETE", checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)).method)
        assertFalse(push.isActive(account.id))
    }

    @Test
    fun `the same endpoint again needs no new subscription, and one no longer used does`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        val endpoint = "https://ntfy.example/up123"
        ok()
        push.subscribe(account, endpoint, "pub-key", "auth-secret")
        assertFalse(push.subscribed(account.id, account.id, endpoint))
        push.rememberEndpoint(account.id, endpoint)
        assertTrue(push.subscribed(account.id, account.id, endpoint))
        assertFalse(push.subscribed(account.id, account.id, "https://ntfy.example/other"))
        ok()
        push.unsubscribe(account)
        assertFalse(push.subscribed(account.id, account.id, endpoint))
    }

    @Test
    fun `a token the server refused takes its subscription along, so signing in again subscribes again`() =
        runBlocking {
            val account = fixture.signIn(server.url("/"))
            val endpoint = "https://ntfy.example/up123"
            ok()
            push.subscribe(account, endpoint, "pub-key", "auth-secret")
            push.rememberEndpoint(account.id, endpoint)
            server.takeRequest(1, TimeUnit.SECONDS)
            server.enqueue(MockResponse.Builder().code(401).body("""{"error":"invalid_token"}""").build())
            push.setPolicy(account, NotificationsFrom.Following)
            server.takeRequest(1, TimeUnit.SECONDS)
            assertTrue(checkNotNull(fixture.accounts.byId(account.id)).needsReauth)
            assertFalse(push.isActive(account.id))
            val again = fixture.signIn(server.url("/"))
            assertFalse(push.subscribed(again.id, again.id, endpoint))
            ok()
            assertNull(push.subscribe(again, endpoint, "pub-key", "auth-secret"))
            val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("POST /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
            push.rememberEndpoint(again.id, endpoint)
            assertTrue(push.subscribed(again.id, again.id, endpoint))
        }

    @Test
    fun `push through the Nextcloud outlives a token the server refused`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        val instance = "nextcloud:${account.id}"
        val endpoint = "https://ntfy.example/up123"
        push.rememberEndpoint(instance, endpoint)
        push.markActive(account.id)
        fixture.accounts.markNeedsReauth(account.id)
        assertTrue(push.subscribed(account.id, instance, endpoint))
    }

    @Test
    fun `a server subscription still being removed leaves push through the Nextcloud to a refused token`() =
        runBlocking {
            val account = fixture.signIn(server.url("/"))
            val instance = "nextcloud:${account.id}"
            val endpoint = "https://ntfy.example/up123"
            push.rememberEndpoint(account.id, "https://ntfy.example/old")
            push.rememberEndpoint(instance, endpoint)
            push.markActive(account.id)
            fixture.accounts.markNeedsReauth(account.id)
            assertTrue(push.subscribed(account.id, instance, endpoint))
            assertNull(push.endpoint(account.id))
        }

    @Test
    fun `a refused subscription leaves the account polled, and is not asked for again until forgotten`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(422)
                .body("""{"error":"The push endpoint must be a reachable public HTTPS URL"}""").build(),
        )
        assertTrue(push.subscribe(account, "https://ntfy.lan/up123", "pub-key", "auth-secret") != null)
        assertFalse(push.isActive(account.id))
        assertTrue(push.refused(account.id))
        push.forgetRefusal(account.id)
        assertFalse(push.refused(account.id))
    }

    @Test
    fun `a refusal is known by its code, whatever its message says`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(422)
                .body("""{"error":"Der Push-Endpunkt ist nicht erreichbar","code":"push-endpoint-unreachable"}""")
                .build(),
        )
        assertTrue(push.subscribe(account, "https://ntfy.lan/up123", "pub-key", "auth-secret") != null)
        assertTrue(push.refused(account.id))
    }

    @Test
    fun `an account with all the subscriptions its server allows is not asked for another`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(422).body(
                """{"error":"This account already has the maximum number of push subscriptions",""" +
                    """"code":"push-subscriptions-full","limit":20}""",
            ).build(),
        )
        assertTrue(push.subscribe(account, "https://ntfy.example/up123", "pub-key", "auth-secret") != null)
        assertFalse(push.isActive(account.id))
        assertTrue(push.refused(account.id))
    }

    @Test
    fun `keys the server finds invalid are no refusal of the endpoint`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(422)
                .body("""{"error":"The push subscription keys are invalid","code":"push-keys-invalid"}""").build(),
        )
        assertTrue(push.subscribe(account, "https://ntfy.example/up123", "pub-key", "auth-secret") != null)
        assertFalse(push.refused(account.id))
    }

    @Test
    fun `a server failing is no refusal`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(MockResponse.Builder().code(503).build())
        assertTrue(push.subscribe(account, "https://ntfy.example/up123", "pub-key", "auth-secret") != null)
        assertFalse(push.refused(account.id))
    }
}
