// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import org.robolectric.annotation.Config
import social.aloha.core.data.sync.PushSubscriptions
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.sync.AccountSignOut
import social.aloha.core.sync.AvatarSource
import social.aloha.core.sync.LocalNotifications
import social.aloha.core.sync.PushRegistrar
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

/** Nextcloud Social's self-service deletion: 200 for the right handle, 422 naming it for anything else. */
private class Deleting : Dispatcher() {
    val deletions = CopyOnWriteArrayList<RecordedRequest>()

    /** Held until released, as a slow server would be. */
    var hold: CountDownLatch? = null

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (!request.url.encodedPath.endsWith("/api/v1/account/delete")) {
            return MockResponse.Builder().code(404).body("{}").build()
        }
        deletions += request
        hold?.await(HOLD_SECONDS, TimeUnit.SECONDS)
        val confirmed = request.body?.utf8() == "confirm=alice%40social.example"
        return if (confirmed) {
            MockResponse.Builder().code(200).body("""{"deleted":true}""").build()
        } else {
            MockResponse.Builder().code(422)
                .body("""{"status":-1,"error":"$REFUSAL"}""")
                .build()
        }
    }

    companion object {
        const val HOLD_SECONDS = 10L
        const val REFUSAL = "type alice@social.example to confirm that this is the account to delete"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DeleteAccountViewModelTest {
    init {
        // the screen's state is shared in the ViewModel's scope, on Main, which nothing runs while a test blocks
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val server = Deleting()
    private val web = MockWebServer().apply {
        dispatcher = server
        start()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val push = PushRegistrar(
        context,
        fixture.accounts,
        PushSubscriptions(fixture.clients, AppPreferences(InMemoryDataStore(emptyPreferences()))),
        fixture.nextcloud,
    )
    private val signOuts =
        AccountSignOut(push, fixture.removal, LocalNotifications(context, avatars = AvatarSource { null }), scope)
    private val store = ViewModelStore()
    private val viewModel = DeleteAccountViewModel(fixture.accounts, fixture.removal, signOuts, scope).also {
        store.put("delete", it)
    }

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        scope.cancel()
        fixture.close()
        web.close()
    }

    private suspend fun connected() = fixture.signIn(web.url("/")).also {
        fixture.connectNextcloud(it.id, APP_PASSWORD)
        // the screen acts on the account in use, as it has loaded
        withTimeout(WAIT_MILLIS) { fixture.accounts.activeAccount.first { active -> active?.id == it.id } }
    }

    private suspend fun gone(accountId: String) = withTimeout(WAIT_MILLIS) {
        while (fixture.accounts.byId(accountId) != null) delay(POLL_MILLIS)
    }

    @Test
    fun `deleted on the server, the account signs out of the device too`() = runBlocking {
        val alice = connected()
        viewModel.onDelete("alice@social.example")
        gone(alice.id)
        val sent = server.deletions.single()
        assertEquals(APP_PASSWORD, sent.headers["Authorization"])
        assertEquals("true", sent.headers["OCS-APIRequest"])
        assertTrue(fixture.accounts.all().isEmpty())
    }

    @Test
    fun `a refusal shows the server's words and changes nothing`() = runBlocking {
        val alice = connected()
        viewModel.onDelete("@ALICE")
        val state = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.refusal != null && !it.deleting } }
        assertEquals(
            DeletionRefusal.Server(Deleting.REFUSAL),
            state.refusal,
        )
        assertNotNull(fixture.accounts.byId(alice.id))
    }

    @Test
    fun `leaving Settings while the server deletes still signs the account out`() = runBlocking {
        val alice = connected()
        server.hold = CountDownLatch(1)
        viewModel.onDelete("alice@social.example")
        withTimeout(WAIT_MILLIS) { while (server.deletions.isEmpty()) delay(POLL_MILLIS) }
        store.clear()
        server.hold?.countDown()
        gone(alice.id)
    }

    @Test
    fun `only Mastodon's own deletion page is offered, never a guessed one`() = runBlocking {
        fun on(software: String) = ServerCapabilities.minimal(web.url("/").toString()).copy(softwareName = software)
        fixture.signIn(web.url("/"), on("mastodon"))
        val mastodon = withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.handle.isNotEmpty() } }
        assertEquals(DeletionMode.OnTheWeb, mastodon.mode)
        assertEquals("https://${web.url("/").host}/settings/delete", mastodon.webPage)
        fixture.signIn(web.url("/"), on("pleroma"))
        val other =
            withTimeout(WAIT_MILLIS) { viewModel.uiState.first { it.webPage == null && it.handle.isNotEmpty() } }
        assertEquals(DeletionMode.OnTheWeb, other.mode)
    }

    @Test
    fun `another person's handle is never sent`() = runBlocking {
        connected()
        viewModel.onDelete("bob")
        delay(POLL_MILLIS)
        assertTrue(server.deletions.isEmpty())
    }

    private companion object {
        const val APP_PASSWORD = "Basic YWxpY2U6YXBwLXBhc3N3b3Jk"
        const val WAIT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
