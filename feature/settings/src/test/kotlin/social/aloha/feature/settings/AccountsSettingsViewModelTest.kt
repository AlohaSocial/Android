// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.NewAccount
import social.aloha.core.data.ReauthRequest
import social.aloha.core.data.sync.PushSubscriptions
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.sync.AccountSignOut
import social.aloha.core.sync.AvatarSource
import social.aloha.core.sync.LocalNotifications
import social.aloha.core.sync.PushRegistrar
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AccountsSettingsViewModelTest {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = SignedInFixture(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val push = PushRegistrar(
        context,
        fixture.accounts,
        PushSubscriptions(fixture.clients, AppPreferences(InMemoryDataStore(emptyPreferences()))),
        fixture.nextcloud,
    )
    private val reauth = ReauthRequest()
    private val store = ViewModelStore()
    private val viewModel = AccountsSettingsViewModel(
        fixture.accounts,
        fixture.order,
        AccountSignOut(push, fixture.removal, LocalNotifications(context, avatars = AvatarSource { null }), scope),
        reauth,
    ).also { store.put("accounts", it) }

    @After
    fun close() {
        // before resetMain: clearing a ViewModel cancels its work on the main dispatcher
        store.clear()
        Dispatchers.resetMain()
        scope.cancel()
        fixture.close()
    }

    private suspend fun account(host: String) = fixture.accounts.signedIn(
        // nothing listens there: what these tests do never needs the server
        NewAccount(host, "6", "alice", "Alice", null, null, ServerCapabilities.minimal("http://127.0.0.1:9/"), false),
        AccessToken("token", ""),
    )

    private suspend fun order(): List<String> =
        withTimeout(WAIT_MILLIS) { viewModel.entries.first { it.size == 3 } }.map { it.handle }

    @Test
    fun `an account moves one place up or down, and never past either end`() = runBlocking {
        val first = account("one.example")
        account("two.example")
        val third = account("three.example")
        assertEquals(listOf("@alice@one.example", "@alice@two.example", "@alice@three.example"), order())
        viewModel.move(third.id, -1)
        withTimeout(WAIT_MILLIS) { viewModel.entries.first { it.map { e -> e.id }.indexOf(third.id) == 1 } }
        viewModel.move(first.id, -1)
        assertEquals(listOf("@alice@one.example", "@alice@three.example", "@alice@two.example"), order())
    }

    @Test
    fun `signing in again makes the account the one in use, and asks for its sign-in`() = runBlocking {
        account("one.example")
        val second = account("two.example")
        fixture.accounts.markNeedsReauth(second.id)
        viewModel.signInAgain(second.id)
        // the account is made the one in use first, then its sign-in asked for
        assertTrue(withTimeout(WAIT_MILLIS) { reauth.requested.first { it } })
        // the account in use is worked out from the stored choice, a moment after it is written
        withTimeout(WAIT_MILLIS) { fixture.accounts.activeAccount.first { it?.id == second.id } }
        Unit
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
