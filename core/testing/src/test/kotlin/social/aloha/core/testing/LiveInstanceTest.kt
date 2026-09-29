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
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Authorization
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInResult
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.model.AccessToken
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthEndpoints
import social.aloha.core.network.oauth.OAuthIdentity
import social.aloha.core.network.oauth.authorizationUrl
import social.aloha.core.network.probe.ProbeOutcome
import social.aloha.core.network.probe.ProbeResult
import social.aloha.core.network.probe.ServerAddress
import social.aloha.core.network.probe.ServerProbe

/**
 * Sign-in against a real Nextcloud Social, end to end, with the real client code. Runs only when
 * `ALOHA_LIVE_SERVER` names the server (e.g. `http://nextcloud.local`) and `ALOHA_LIVE_USER` and
 * `ALOHA_LIVE_PASSWORD` a person on it; CI never sets them. The browser's consent step is replaced
 * by the same form posted with the person's app password and `OCS-APIRequest: true`, which a dev
 * instance accepts; redirects are not followed, so the callback URI is read from `Location`.
 */
@RunWith(RobolectricTestRunner::class)
class LiveInstanceTest {
    private val server: String? = System.getenv("ALOHA_LIVE_SERVER")
    private val user = System.getenv("ALOHA_LIVE_USER").orEmpty()
    private val password = System.getenv("ALOHA_LIVE_PASSWORD").orEmpty()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
    private val oauth = OAuthClient(http, limiter, Dispatchers.IO)
    private val clients = ClientFactory(http, limiter, Dispatchers.IO, accounts)
    private val coordinator = SignInCoordinator(
        accounts,
        vault,
        oauth,
        CapabilityDetector(http, limiter, Dispatchers.IO) { Instant.now() },
        { OAuthIdentity.SCHEME_REDIRECT },
        clients,
    )

    @After
    fun close() {
        scope.cancel()
        database.close()
    }

    private fun probe(): ProbeOutcome {
        assumeTrue("set ALOHA_LIVE_SERVER to run against a real server", server != null)
        val address = ServerAddress.parse(server.orEmpty(), allowCleartext = true) ?: error("address")
        return (
            runBlocking {
                ServerProbe(http, limiter, Dispatchers.IO).discover(address)
            } as ProbeResult.Found
            ).outcome
    }

    private suspend fun begin(): HttpUrl = (
        coordinator.beginAuthorization(
            testServerFinder(http, limiter).found(server.orEmpty()),
        ) as Authorization.Started
        )
        .url.toHttpUrl()

    /** What the browser would do: the consent form, answered as the person, with the redirect captured. */
    private fun consent(authorize: HttpUrl): String {
        val form = FormBody.Builder().apply {
            authorize.queryParameterNames.forEach { name -> add(name, authorize.queryParameter(name).orEmpty()) }
        }.build()
        val request = Request.Builder().url(authorize.newBuilder().query(null).build()).post(form)
            .header("Authorization", Credentials.basic(user, password))
            .header("OCS-APIRequest", "true")
            .build()
        http.newBuilder().followRedirects(false).build().newCall(request).execute().use { response ->
            return response.header("Location")
                ?: error("no redirect: ${response.code} ${response.body.string().take(300)}")
        }
    }

    @Test
    fun `sign-in succeeds, the token works, and the account knows what the server can do`() = runBlocking {
        probe()
        val authorize = begin()
        val callback = consent(authorize)
        assertTrue(callback, callback.startsWith(OAuthIdentity.SCHEME_REDIRECT))

        val account = (coordinator.complete(callback) as SignInResult.SignedIn).account
        println("LIVE apiBase=${account.apiBase} handle=${account.handle} caps=${account.capabilities}")
        assertTrue(account.capabilities.isNextcloudSocial)
        assertTrue(account.capabilities.onlyVideoFilter)
        val me = clients.forAccount(account)?.execute(AccountEndpoints.verifyCredentials())
        assertTrue("$me", me is ApiResult.Success)
    }

    @Test
    fun `a code is single-use, plain PKCE is refused, and a revoked token becomes a new sign-in`() = runBlocking {
        val outcome = probe()
        val authorize = begin()
        val callback = consent(authorize)
        val code =
            "https://x.invalid/?${callback.substringAfter('?')}".toHttpUrl().queryParameter("code") ?: error("code")
        val base = outcome.apiBase
        val registration = accounts.registration(base.host) ?: error("registration")
        val endpoints = OAuthEndpoints.from(outcome.authorizationServer ?: oauth.metadata(base), base)
        val pending = authorizationUrl(endpoints, base, registration.clientId, OAuthIdentity.SCHEME_REDIRECT).second
            .copy(verifier = "x".repeat(64))

        val wrongVerifier = oauth.exchange(endpoints.token, registration, pending, code)
        println("LIVE exchange with a wrong verifier: $wrongVerifier")
        assertFalse(wrongVerifier is ApiResult.Success)

        val plain = authorize.newBuilder().setQueryParameter("code_challenge_method", "plain").build()
        val plainAnswer = runCatching { consent(plain) }
        println("LIVE plain PKCE: $plainAnswer")
        assertTrue(plainAnswer.isFailure || !plainAnswer.getOrThrow().contains("code="))

        val revoked = AccessToken("not-a-real-token", "")
        val answer = clients.create(base) { social.aloha.core.network.Credentials(revoked.value) }
            .execute(AccountEndpoints.verifyCredentials())
        assertEquals(ApiError.Unauthorised::class, (answer as ApiResult.Failure).error::class)
    }
}
