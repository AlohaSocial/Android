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

    private suspend fun connected() = fixture.signIn(server.url("/")).also {
        fixture.connectNextcloud(it.id, "Basic YWxpY2U6YXBwLXBhc3N3b3Jk")
    }.let { fixture.accounts.byId(it.id)!! }

    @Test
    fun `pushed by its server, the account's Nextcloud registration goes`() = runBlocking {
        val account = connected()
        push.rememberEndpoint(PushInstance.nextcloud(account.id), "https://ntfy.lan/up1")
        ok()
        registrar.subscribed(account, viaNextcloud = false)
        val sent = server.takeRequest(1, TimeUnit.SECONDS)!!
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
        val sent = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("DELETE /api/v1/push/subscription", "${sent.method} ${sent.url.encodedPath}")
        assertNull(push.endpoint(account.id))
        assertFalse(push.isActive(account.id))
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
        assertEquals("DELETE", server.takeRequest(1, TimeUnit.SECONDS)!!.method)
        assertTrue(push.refused(account.id))
        registrar.choose(null)
        assertFalse(push.refused(account.id))
    }
}
