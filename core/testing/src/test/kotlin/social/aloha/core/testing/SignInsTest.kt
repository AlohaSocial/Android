// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.NewAccount
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities

/** Adding an account ends on its sign-in, whatever account is in use afterwards. */
@RunWith(RobolectricTestRunner::class)
class SignInsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val accounts = AccountRepository(
        accountsDb.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        Clock.systemUTC(),
        scope,
    )

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
    }

    private suspend fun signIn(host: String) = accounts.signedIn(
        NewAccount(host, "6", "alice", "Alice", null, null, ServerCapabilities.minimal("https://$host/"), false),
        AccessToken(MockCredentials.ACCESS_TOKEN, ""),
    )

    @Test
    fun `every sign-in is told, a new account's and one signed in again`() = runBlocking {
        val told = async(start = CoroutineStart.UNDISPATCHED) { accounts.signIns.take(3).toList() }
        val first = signIn("one.test")
        val second = signIn("two.test")
        // the same account again, active already: nothing about the active account changes
        val again = signIn("two.test")
        assertEquals(listOf(first.id, second.id, again.id), withTimeout(5_000) { told.await() })
        assertEquals(second.id, again.id)
    }
}
