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
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Authorization
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInResult
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.network.ApiResult
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthIdentity

/**
 * Sign-in from a typed address to a stored, active account, against every server shape: probe,
 * registration, the authorisation URL, the callback the browser would deliver, the token exchange,
 * the account description and capability detection.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class SignInAcceptanceTest(private val configuration: MockServerConfiguration) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mock = MockSocialServer(configuration).start()
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AccountsDatabase::class.java,
    ).build()
    private val vault = TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO)
    private val accounts = AccountRepository(
        database.accountDao(),
        vault,
        AppPreferences(InMemoryDataStore(emptyPreferences())),
        Clock.systemUTC(),
        scope,
    )
    private val http = OkHttpClient()
    private val limiter = RateLimiter(nowMillis = Clock.systemUTC()::millis)
    private val coordinator = SignInCoordinator(
        accounts = accounts,
        vault = vault,
        oauth = OAuthClient(http, limiter, Dispatchers.IO),
        detector = CapabilityDetector(http, limiter, Dispatchers.IO) { java.time.Instant.now() },
        redirects = { OAuthIdentity.SCHEME_REDIRECT },
        clients = ClientFactory(http, limiter, Dispatchers.IO, accounts),
    )
    private val finder = testServerFinder(http, limiter)

    @After
    fun close() {
        scope.cancel()
        database.close()
        mock.close()
    }

    @Test
    fun `sign-in ends with a stored, active account and its token in the vault`() = runBlocking {
        val server = finder.found(mock.origin.toString())
        val authorize = (coordinator.beginAuthorization(server) as Authorization.Started).url.toHttpUrl()
        assertEquals("S256", authorize.queryParameter("code_challenge_method"))
        assertTrue(coordinator.hasPendingAuthorization())

        val state = authorize.queryParameter("state") ?: error("state")
        val result = coordinator.complete("alohasocial://oauth-callback/?code=mock-code&state=$state")

        assertTrue("sign-in ended with $result", result is SignInResult.SignedIn)
        val account = (result as SignInResult.SignedIn).account
        assertFalse(account.needsReauth)
        assertEquals(server.apiBase, account.apiBase)
        assertEquals(MockCredentials.ACCESS_TOKEN, accounts.token(account.id)?.value)
        assertEquals(account.id, accounts.activeAccount.filterNotNull().first().id)
        assertFalse(coordinator.hasPendingAuthorization())
    }

    @Test
    fun `a callback carrying another state is dropped without an exchange`() = runBlocking {
        coordinator.beginAuthorization(finder.found(mock.origin.toString()))
        val before = mock.requests.size
        assertEquals(
            SignInResult.NotForUs,
            coordinator.complete("alohasocial://oauth-callback/?code=stolen&state=evil"),
        )
        assertEquals(before, mock.requests.size)
        assertTrue(coordinator.hasPendingAuthorization())
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = MockServerConfiguration.entries.map { arrayOf(it) }
    }
}
