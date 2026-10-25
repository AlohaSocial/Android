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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.AllAccounts
import social.aloha.core.data.NewAccount
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AccountViewPreferences
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount

@RunWith(RobolectricTestRunner::class)
class AllAccountsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val app = AppPreferences(InMemoryDataStore(emptyPreferences()))
    private val accountsDb = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val accounts = AccountRepository(
        accountsDb.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        app,
        Clock.systemUTC(),
        scope,
    )
    private val all = AllAccounts(accounts, AccountViewPreferences(InMemoryDataStore(emptyPreferences())))

    @After
    fun close() {
        scope.cancel()
        accountsDb.close()
    }

    private suspend fun signedIn(host: String): SignedInAccount = accounts.signedIn(
        NewAccount(host, "6", "alice", "Alice", null, null, ServerCapabilities.minimal("https://$host/"), false),
        AccessToken(MockCredentials.ACCESS_TOKEN, ""),
    )

    private suspend fun inUse(id: String) =
        withTimeout(TIMEOUT) { accounts.activeAccount.filterNotNull().first { it.id == id } }

    @Test
    fun `one account is shown alone, two together, as the first`() = runBlocking {
        val first = signedIn("one.example")
        withTimeout(TIMEOUT) { all.shown.first { shown -> shown.map { it.id } == listOf(first.id) } }
        assertFalse(all.on.first())

        val second = signedIn("two.example")
        withTimeout(TIMEOUT) { all.shown.first { shown -> shown.map { it.id } == listOf(first.id, second.id) } }
        assertTrue(all.on.first())
    }

    @Test
    fun `together, the first account stays in use, and one picked shows alone until all are asked for`() = runBlocking {
        val first = signedIn("one.example")
        val keeping = scope.launch { all.keepFirstInUse() }
        // a new sign-in makes the new account the one in use; together, the first takes over again
        val second = signedIn("two.example")
        // the stored choice, not activeAccount, which may not have seen the sign-in yet: once it is the first
        // again, keepFirstInUse has acted and will not race the pick below
        withTimeout(TIMEOUT) { app.activeAccountId.first { it == first.id } }
        inUse(first.id)

        all.show(second.id)
        inUse(second.id)
        assertFalse(all.on.first())
        assertEquals(listOf(second.id), all.shown.first().map { it.id })

        all.showAll()
        inUse(first.id)
        assertTrue(all.on.first())
        keeping.cancel()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
