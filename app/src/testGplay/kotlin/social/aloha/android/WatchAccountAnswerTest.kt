// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import social.aloha.core.data.NewAccount
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.WatchHandover
import social.aloha.core.network.TokenRenewal
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WatchAccountAnswerTest {
    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext())
    private val accounts = fixture.accounts
    private val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
    private val renewed = mutableListOf<String>()
    private val answer = WatchAccountAnswer(accounts, settings) { account, rejected ->
        renewed += "${account.handle}:$rejected"
        fixture.vault.put(VaultKey.AccessToken(account.id), "$rejected-renewed")
        TokenRenewal.Renewed
    }

    @After
    fun close() = fixture.close()

    private suspend fun signIn(handle: String, token: String): SignedInAccount = accounts.signedIn(
        NewAccount(
            "$handle.example",
            "6",
            handle,
            handle,
            null,
            null,
            ServerCapabilities.minimal("https://$handle.example/"),
            profilePending = false,
        ),
        AccessToken(token, ""),
    )

    @Test
    fun `a watch's refused token is renewed before the phone answers with the new one`() = runBlocking {
        val alice = signIn("alice", "alice-token")

        val handed = answer.answer(WatchHandover.digest("alice-token"))

        assertEquals(listOf("alice:alice-token"), renewed)
        assertEquals("alice-token-renewed", handed?.token)
        assertEquals("alice", handed?.handle)
        assertTrue(settings.settings(alice.id).first().handedToWatch)
    }

    @Test
    fun `the account holding the refused token answers, though another is in use`() = runBlocking {
        signIn("alice", "alice-token")
        val bob = signIn("bob", "bob-token")
        accounts.activeAccount.filterNotNull().first { it.id == bob.id }

        val handed = answer.answer(WatchHandover.digest("alice-token"))

        assertEquals("alice", handed?.handle)
        assertEquals("alice-token-renewed", handed?.token)
    }

    @Test
    fun `without a token of its own the phone answers with the account in use, renewing nothing`() = runBlocking {
        signIn("alice", "alice-token")
        val bob = signIn("bob", "bob-token")
        accounts.activeAccount.filterNotNull().first { it.id == bob.id }

        assertEquals("bob-token", answer.answer(WatchHandover.digest("gone-token"))?.token)
        assertEquals("bob-token", answer.answer("")?.token)
        assertTrue(renewed.isEmpty())
    }
}
