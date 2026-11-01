// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.sync

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
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.PushRefusals
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
class PushRegistrarTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val server = MockWebServer().apply { start() }
    private val push = PushSubscriptions(
        fixture.clients,
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        AccountSettingsStore(InMemoryDataStore(emptyMap())),
        PushRefusals(InMemoryDataStore(emptyPreferences())),
    )
    private val registrar = PushRegistrar(context, fixture.accounts, push, fixture.nextcloud)

    @After
    fun close() {
        fixture.close()
        server.close()
    }

    private fun ok() = server.enqueue(
        MockResponse.Builder().code(200).body("{}").addHeader("content-type", "application/json").build(),
    )

    private fun json(body: String) = server.enqueue(
        MockResponse.Builder().code(200).body(body).addHeader("content-type", "application/json").build(),
    )

    private fun nextcloudPushes(vararg kinds: String) {
        val push = kinds.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        json("""{"ocs":{"data":{"capabilities":{"notifications":{"push":$push}}}}}""")
        if ("webpush" in kinds) json("""{"ocs":{"data":{"vapid":"$VAPID"}}}""")
    }

    private suspend fun connected(vapid: String? = null) = fixture.signIn(
        server.url("/"),
        capabilities = ServerCapabilities.minimal(server.url("/").toString()).copy(webPushVapidKey = vapid),
    ).also {
        fixture.connectNextcloud(it.id, "Basic YWxpY2U6YXBwLXBhc3N3b3Jk")
    }.let { checkNotNull(fixture.accounts.byId(it.id)) }

    @Test
    fun `pushed by its server, the account's Nextcloud registration goes`() = runBlocking {
        val account = connected()
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        ok()
        registrar.subscribed(account, viaNextcloud = false)
        val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("DELETE /ocs/v2.php/apps/notifications/api/v2/webpush", "${sent.method} ${sent.url.encodedPath}")
        assertNull(push.endpoint(PushInstance.nextcloud(account.id)))
    }

    @Test
    fun `pushed by its Nextcloud, the account's server subscription goes`() = runBlocking {
        val account = connected()
        ok()
        push.subscribe(account, "https://ntfy.example/up1", "pub-key", "auth-secret")
        server.takeRequest(1, TimeUnit.SECONDS)
        push.rememberEndpoint(account.id, "https://ntfy.example/up1")
        ok()
        registrar.subscribed(account, viaNextcloud = true)
        val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("DELETE /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
        assertNull(push.endpoint(account.id))
        assertFalse(push.isActive(account.id))
    }

    @Test
    fun `a server that could not be told keeps its endpoint until it confirms`() = runBlocking {
        val account = connected()
        push.rememberEndpoint(account.id, "https://ntfy.example/up1")
        server.enqueue(MockResponse.Builder().code(503).build())
        registrar.subscribed(account, viaNextcloud = true)
        assertEquals("https://ntfy.example/up1", push.endpoint(account.id))
        assertFalse(push.isActive(account.id))
        ok()
        registrar.subscribed(account, viaNextcloud = true)
        assertNull(push.endpoint(account.id))
    }

    @Test
    fun `a Nextcloud that could not be told keeps its endpoint until it confirms`() = runBlocking {
        val account = connected()
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        server.enqueue(MockResponse.Builder().code(503).build())
        registrar.subscribed(account, viaNextcloud = false)
        assertEquals("https://ntfy.lan/up1", push.endpoint(PushInstance.nextcloud(account.id)))
        server.enqueue(MockResponse.Builder().code(404).build())
        registrar.subscribed(account, viaNextcloud = false)
        assertNull(push.endpoint(PushInstance.nextcloud(account.id)))
    }

    @Test
    fun `registering again asks once more for a route whose removal failed`() = runBlocking {
        // no VAPID key: its Nextcloud is the account's route, confirmed by the endpoint recorded for it
        val account = connected()
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        push.rememberEndpoint(account.id, "https://ntfy.example/up1")
        // the Nextcloud's push types, which end this registration before the distributor is asked
        server.enqueue(MockResponse.Builder().code(404).build())
        ok()
        registrar.register(account)
        server.takeRequest(1, TimeUnit.SECONDS)
        val sent = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("DELETE /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
        assertNull(push.endpoint(account.id))
    }

    @Test
    fun `a Nextcloud that pushes is the route even where the server offers Web Push`() = runBlocking {
        val account = connected(vapid = VAPID)
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        push.rememberEndpoint(account.id, "https://ntfy.example/up1")
        nextcloudPushes("devices", "webpush")
        ok()
        // the distributor's keys live in the AndroidKeyStore, which Robolectric lacks; the route is chosen before
        runCatching { registrar.register(account) }
        val sent = List(3) { checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) }.last()
        assertEquals("DELETE /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
        assertNull(push.endpoint(account.id))
    }

    @Test
    fun `a Nextcloud not reached keeps its route, the server's is not taken instead`() = runBlocking {
        val account = connected(vapid = VAPID)
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        push.rememberEndpoint(account.id, "https://ntfy.example/up1")
        server.enqueue(MockResponse.Builder().code(503).build())
        registrar.register(account)
        assertEquals(1, server.requestCount)
        assertEquals("https://ntfy.lan/up1", push.endpoint(PushInstance.nextcloud(account.id)))
    }

    @Test
    fun `a Nextcloud without Web Push leaves the route to the server`() = runBlocking {
        val account = connected(vapid = VAPID)
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        push.rememberEndpoint(account.id, "https://ntfy.example/up1")
        nextcloudPushes("devices")
        ok()
        runCatching { registrar.register(account) }
        val sent = List(2) { checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)) }.last()
        assertEquals("DELETE /ocs/v2.php/apps/notifications/api/v2/webpush", "${sent.method} ${sent.url.encodedPath}")
        assertNull(push.endpoint(PushInstance.nextcloud(account.id)))
    }

    @Test
    fun `with no other route registered, nothing is sent`() = runBlocking {
        val account = connected()
        registrar.subscribed(account, viaNextcloud = false)
        registrar.subscribed(account, viaNextcloud = true)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a refusal holds until a distributor is chosen`() = runBlocking {
        val account = fixture.signIn(server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(422)
                .body("""{"error":"The push endpoint must be a reachable public HTTPS URL"}""").build(),
        )
        push.subscribe(account, "https://ntfy.lan/up1", "pub-key", "auth-secret")
        server.takeRequest(1, TimeUnit.SECONDS)
        ok()
        registrar.refusedByServer(account)
        assertEquals("DELETE", checkNotNull(server.takeRequest(1, TimeUnit.SECONDS)).method)
        assertTrue(push.refused(account.id))
        registrar.choose(null)
        assertFalse(push.refused(account.id))
    }

    private companion object {
        const val VAPID = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM"
    }
}
