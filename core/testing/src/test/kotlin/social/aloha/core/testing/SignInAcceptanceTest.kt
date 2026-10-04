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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import social.aloha.core.data.AccountRemoval
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Authorization
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInResult
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.nextcloud.NextcloudConnection
import social.aloha.core.data.notifications.RaisedNotifications
import social.aloha.core.data.sync.WidgetUpdates
import social.aloha.core.data.timeline.CacheSweeper
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.database.CacheDatabase
import social.aloha.core.database.OutboxDatabase
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.datastore.WidgetFeedStore
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
    private val clients = ClientFactory(http, limiter, Dispatchers.IO, accounts)
    private val coordinator = SignInCoordinator(
        accounts = accounts,
        vault = vault,
        oauth = OAuthClient(http, limiter, Dispatchers.IO),
        detector = CapabilityDetector(http, limiter, Dispatchers.IO) { java.time.Instant.now() },
        redirects = { OAuthIdentity.SCHEME_REDIRECT },
        clients = clients,
    )
    private val finder = testServerFinder(http, limiter)
    private val cache = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CacheDatabase::class.java,
    ).build()
    private val settings = AccountSettingsStore(InMemoryDataStore(emptyMap()))
    private val outboxDb = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        OutboxDatabase::class.java,
    ).build()
    private val removal = AccountRemoval(
        accounts,
        OAuthClient(http, limiter, Dispatchers.IO),
        CacheSweeper(cache.statusDao(), cache.cacheAccountDao(), Clock.systemUTC()),
        settings,
        Outbox(outboxDb.outboxDao(), Clock.systemUTC()),
        NextcloudConnection(clients, accounts, database.accountDao(), vault),
        WidgetUpdates(ApplicationProvider.getApplicationContext(), WidgetFeedStore(InMemoryDataStore(emptyMap()))),
        RaisedNotifications(database.raisedDao()),
        clients,
    )

    @After
    fun close() {
        scope.cancel()
        database.close()
        cache.close()
        outboxDb.close()
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
        assertEquals(
            SignInResult.NotForUs,
            coordinator.complete("alohasocial://oauth-callback/?code=stolen&state=evil"),
        )
        // what matters is that the code was never exchanged; counting all requests raced the server,
        // which records one on its own thread, sometimes after the client already has the answer
        assertTrue(mock.requests.none { it.url.encodedPath.endsWith("/oauth/token") })
        assertTrue(coordinator.hasPendingAuthorization())
    }

    @Test
    fun `signing out revokes the token and the app password, and forgets the account, its cache and its settings`() =
        runBlocking {
            val server = finder.found(mock.origin.toString())
            val authorize = (coordinator.beginAuthorization(server) as Authorization.Started).url.toHttpUrl()
            val state = authorize.queryParameter("state") ?: error("state")
            val account = (
                coordinator.complete(
                    "alohasocial://oauth-callback/?code=mock-code&state=$state",
                ) as SignInResult.SignedIn
                ).account
            val statuses = StatusRepository(cache.statusDao(), Clock.systemUTC())
            statuses.save(account.id, StatusSamples.post())
            settings.update(account.id) { it.copy(showBoosts = false) }
            vault.put(VaultKey.AppPassword(account.id), APP_PASSWORD)

            removal.signOut(account)

            val revoke = mock.requests.last { it.url.encodedPath.endsWith("/oauth/revoke") }
            assertTrue(revoke.body?.utf8().orEmpty().contains("token=${MockCredentials.ACCESS_TOKEN}"))
            val handedBack = mock.requests.last { it.url.encodedPath.endsWith("/ocs/v2.php/core/apppassword") }
            assertEquals("DELETE", handedBack.method)
            assertEquals(APP_PASSWORD, handedBack.headers["Authorization"])
            assertNull(accounts.credentials(account.id).nextcloudBasic)
            assertNull(accounts.token(account.id))
            assertTrue(accounts.all().none { it.id == account.id })
            assertNull(statuses.get(account.id, StatusSamples.post().id))
            assertEquals(AccountSettings(), settings.settings(account.id).first())
        }

    @Test
    fun `a moderator's new registration leaves a token issued before it revocable with the old client`() = runBlocking {
        assumeTrue(configuration == MockServerConfiguration.NextcloudWithRewrite)
        val server = finder.found(mock.origin.toString())
        val authorize = (coordinator.beginAuthorization(server) as Authorization.Started).url.toHttpUrl()
        val state = authorize.queryParameter("state") ?: error("state")
        val account = (
            coordinator.complete(
                "alohasocial://oauth-callback/?code=mock-code&state=$state",
            ) as SignInResult.SignedIn
            ).account
        // the server hands the moderator's registration a client of its own
        mock.pin(
            "POST",
            "/api/v1/apps",
            200,
            """{"client_id":"moderator-client","client_secret":"moderator-secret",""" +
                """"scopes":["read","write","follow","push","admin:read","admin:write"]}""",
        )
        val moderation = (coordinator.beginModeration(account) as Authorization.Started).url.toHttpUrl()
        assertEquals("moderator-client", moderation.queryParameter("client_id"))
        // the moderator did not finish in the browser: the token is still the old client's
        coordinator.cancel()

        removal.signOut(account)

        val revoke = mock.requests.last { it.url.encodedPath.endsWith("/oauth/revoke") }.body?.utf8().orEmpty()
        assertTrue(revoke, revoke.contains("client_id=${MockCredentials.CLIENT_ID}"))
    }

    @Test
    fun `deleting on the server sends the typed handle with the app password, as an OCS request`() = runBlocking {
        val server = finder.found(mock.origin.toString())
        val authorize = (coordinator.beginAuthorization(server) as Authorization.Started).url.toHttpUrl()
        val state = authorize.queryParameter("state") ?: error("state")
        val account = (
            coordinator.complete("alohasocial://oauth-callback/?code=mock-code&state=$state") as SignInResult.SignedIn
            ).account
        vault.put(VaultKey.AppPassword(account.id), APP_PASSWORD)

        removal.deleteOnServer(account, "@alice")

        val delete = mock.requests.last { it.url.encodedPath.endsWith("/api/v1/account/delete") }
        assertEquals("POST", delete.method)
        assertEquals("confirm=%40alice", delete.body?.utf8())
        assertEquals(APP_PASSWORD, delete.headers["Authorization"])
        assertEquals("true", delete.headers["OCS-APIRequest"])
        // deleting does not sign out by itself
        assertEquals(MockCredentials.ACCESS_TOKEN, accounts.token(account.id)?.value)
    }

    companion object {
        private const val APP_PASSWORD = "Basic YWxpY2U6YXBwLXBhc3N3b3Jk"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = MockServerConfiguration.entries.map { arrayOf(it) }
    }
}
