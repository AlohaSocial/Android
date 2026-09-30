// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import java.io.Closeable
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.RateLimiter

/**
 * An account repository on an in-memory database and the clients it hands out, for a test that needs
 * a signed-in reader and a server to talk to. [close] releases both.
 */
public class SignedInFixture(context: Context) : Closeable {
    public val clock: Clock = Clock.systemUTC()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()

    public val accounts: AccountRepository = AccountRepository(
        database.accountDao(),
        TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        clock,
        scope,
    )

    public val clients: ClientFactory =
        ClientFactory(OkHttpClient(), RateLimiter(nowMillis = clock::millis), Dispatchers.IO, accounts)

    /** Signs `@alice` in on the server at [apiBase], with the token the mock server accepts. */
    public suspend fun signIn(
        apiBase: HttpUrl,
        capabilities: ServerCapabilities = ServerCapabilities.minimal(apiBase.toString()),
    ): SignedInAccount = accounts.signedIn(
        NewAccount(apiBase.host, "6", "alice", "Alice", null, null, capabilities, profilePending = false),
        AccessToken(MockCredentials.ACCESS_TOKEN, ""),
    )

    override fun close() {
        scope.cancel()
        database.close()
    }
}
