// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

@RunWith(RobolectricTestRunner::class)
class ActiveOnceReadTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val vault = TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO)
    private val preferences = AppPreferences(InMemoryDataStore(emptyPreferences()))

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
    }

    private fun repository() = AccountRepository(accountsDb.accountDao(), vault, preferences, Clock.systemUTC(), scope)

    @Test
    fun `the first active account told is the stored one, not the null of a list not read yet`() = runBlocking {
        val capabilities = ServerCapabilities.minimal("https://one.test/")
        val stored = repository().signedIn(
            NewAccount("one.test", "6", "alice", "Alice", null, null, capabilities, false),
            AccessToken(MockCredentials.ACCESS_TOKEN, ""),
        )
        val launched = repository()
        assertEquals(stored.id, withTimeout(5_000) { launched.activeOnceRead.first() }?.id)
    }

    @Test
    fun `with nobody signed in it tells null once the list is read`() = runBlocking {
        assertNull(withTimeout(5_000) { repository().activeOnceRead.first() })
    }
}
