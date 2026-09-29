// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.testing

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Authorization
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.NewAccount
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInResult
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthIdentity

@RunWith(RobolectricTestRunner::class)
class AccountRecoveryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AccountsDatabase::class.java,
    ).build()
    private val cipher = FakeSecretCipher()
    private val vaultBytes = InMemoryDataStore(ByteArray(0))
    private val vault = TokenVault(vaultBytes, cipher, Dispatchers.IO)
    private val accounts = AccountRepository(
        database.accountDao(),
        vault,
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        Clock.systemUTC(),
        scope,
    )
    private val http = OkHttpClient()
    private val limiter = RateLimiter(nowMillis = Clock.systemUTC()::millis)
    private val clients = ClientFactory(http, limiter, Dispatchers.IO, accounts)
    private var mock: MockSocialServer? = null

    @After
    fun close() {
        scope.cancel()
        database.close()
        mock?.close()
    }

    private fun newAccount(apiBase: String, id: String = "6") = NewAccount(
        host = "cloud.example",
        serverAccountId = id,
        handle = "alice",
        displayName = "Alice",
        avatarUrl = null,
        headerUrl = null,
        capabilities = ServerCapabilities.minimal(apiBase),
        profilePending = false,
    )

    @Test
    fun `a revoked token marks the account for a new sign-in and keeps it`() = runBlocking {
        val server = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start().also { mock = it }
        val account = accounts.signedIn(
            newAccount(server.apiBase.toString()),
            AccessToken(MockCredentials.REVOKED_TOKEN, ""),
        )
        val client = clients.forAccount(account) ?: error("client")

        val result = client.execute(AccountEndpoints.verifyCredentials())

        assertTrue((result as ApiResult.Failure).error is ApiError.Unauthorised)
        val after = accounts.accounts.first { list -> list.singleOrNull()?.needsReauth == true }.single()
        assertTrue(after.needsReauth)
        assertEquals(account.id, after.id)
        assertEquals(MockCredentials.REVOKED_TOKEN, accounts.token(account.id)?.value)
    }

    /** The app as the next process sees it: same database and vault bytes, nothing held in memory. */
    private fun restarted(): AccountRepository = AccountRepository(
        database.accountDao(),
        TokenVault(vaultBytes, cipher, Dispatchers.IO),
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        Clock.systemUTC(),
        scope,
    )

    @Test
    fun `a vault that cannot be read leaves every account needing a new sign-in, none deleted`() = runBlocking {
        accounts.signedIn(newAccount("https://cloud.example/", "1"), AccessToken("t1", ""))
        accounts.signedIn(newAccount("https://cloud.example/", "2"), AccessToken("t2", ""))
        cipher.lose = true

        val all = restarted().accounts.first { list -> list.all { it.needsReauth } }

        assertEquals(2, all.size)
    }

    @Test
    fun `accounts restored from a backup without their tokens need a new sign-in`() = runBlocking {
        val account = accounts.signedIn(newAccount("https://cloud.example/"), AccessToken("t", ""))
        vault.remove(VaultKey.AccessToken(account.id))

        assertTrue(restarted().accounts.first { list -> list.all { it.needsReauth } }.single().needsReauth)
    }

    @Test
    fun `signing in again to the same person updates the account instead of adding one`() = runBlocking {
        val first = accounts.signedIn(newAccount("https://cloud.example/"), AccessToken("old", ""))
        accounts.markNeedsReauth(first.id)
        val second = accounts.signedIn(newAccount("https://cloud.example/"), AccessToken("new", ""))

        assertEquals(first.id, second.id)
        assertFalse(accounts.all().single().needsReauth)
        assertEquals("new", accounts.token(first.id)?.value)
    }

    @Test
    fun `removing the active account forgets its secrets and activates the next`() = runBlocking {
        val first = accounts.signedIn(newAccount("https://cloud.example/", "1"), AccessToken("t1", ""))
        val second = accounts.signedIn(newAccount("https://cloud.example/", "2"), AccessToken("t2", ""))
        accounts.remove(second.id)

        assertNull(accounts.token(second.id))
        assertEquals(first.id, accounts.all().single().id)
        assertEquals(first.id, accounts.activeAccount.first { it?.id == first.id }?.id)
    }

    @Test
    fun `a new account the server cannot describe yet signs in from userinfo with its profile pending`() = runBlocking {
        val server = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start().also { mock = it }
        server.pin("GET", "/api/v1/accounts/verify_credentials", status = 500, body = "{}")
        val coordinator = SignInCoordinator(
            accounts,
            vault,
            OAuthClient(http, limiter, Dispatchers.IO),
            CapabilityDetector(http, limiter, Dispatchers.IO) { Instant.now() },
            { OAuthIdentity.SCHEME_REDIRECT },
            clients,
        )
        val found = testServerFinder(http, limiter).found(server.origin.toString())
        val state = (
            coordinator.beginAuthorization(
                found,
            ) as Authorization.Started
            ).url.toHttpUrl().queryParameter("state")

        val result = coordinator.complete("alohasocial://oauth-callback/?code=c&state=$state")

        val account = (result as SignInResult.SignedIn).account
        assertTrue(account.profilePending)
        assertEquals("alice", account.handle)
    }
}
