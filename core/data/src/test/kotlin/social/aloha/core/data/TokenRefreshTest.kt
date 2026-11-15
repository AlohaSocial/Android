// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
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
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ClientRegistration
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.endpoints.MarkerEndpoints
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.SignedInFixture

/**
 * A Social server at `/index.php/apps/social/` whose `api/v1/markers` accepts only the tokens in [accepted],
 * answering any other as Aloha Social answers an expired or revoked one, and whose token endpoint
 * answers [refreshAnswer], accepting the refreshed token from then on. [onAccepted] sees each accepted
 * request; refusals wait for [refusalsHeld], and the token endpoint for [refreshHeld]. [revoked] keeps
 * what each revocation sent.
 */
private class Social : Dispatcher() {
    val accepted = CopyOnWriteArraySet<String>()
    val refreshes = CopyOnWriteArrayList<String>()
    val revoked = CopyOnWriteArrayList<String>()

    @Volatile var refreshAnswer: MockResponse = json(
        """{"access_token":"new-access","token_type":"Bearer","scope":"read write follow push",""" +
            """"created_at":1,"expires_in":86400,"refresh_token":"new-refresh"}""",
    )

    @Volatile var refusalsHeld: CountDownLatch? = null

    @Volatile var refreshHeld: CountDownLatch? = null

    @Volatile var onAccepted: () -> Unit = {}

    override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
        "/index.php/apps/social/oauth/token" -> refresh(request)
        "/index.php/apps/social/api/v1/markers" -> markers(request)
        "/index.php/apps/social/oauth/revoke" -> json("{}").also { revoked += request.body?.utf8().orEmpty() }
        else -> json("{}", NOT_FOUND)
    }

    private fun refresh(request: RecordedRequest): MockResponse {
        refreshes += request.body?.utf8().orEmpty()
        refreshHeld?.await(WAIT_SECONDS, TimeUnit.SECONDS)
        if (refreshAnswer.code == OK) accepted += "new-access"
        return refreshAnswer
    }

    private fun markers(request: RecordedRequest): MockResponse {
        if (request.headers["Authorization"]?.removePrefix("Bearer ") in accepted) {
            onAccepted()
            return json("{}")
        }
        refusalsHeld?.run {
            countDown()
            await(WAIT_SECONDS, TimeUnit.SECONDS)
        }
        return json("""{"error":"the access_token was revoked"}""", UNAUTHORISED)
    }

    companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORISED = 401
        const val NOT_FOUND = 404
        const val WAIT_SECONDS = 5L

        fun json(body: String, code: Int = OK) =
            MockResponse.Builder().code(code).body(body).addHeader("content-type", "application/json").build()
    }
}

@RunWith(RobolectricTestRunner::class)
class TokenRefreshTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.systemUTC()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.inMemoryDatabaseBuilder(context, AccountsDatabase::class.java).build()
    private val vault = TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO)
    private val accounts =
        AccountRepository(
            database.accountDao(),
            vault,
            AppPreferences(InMemoryDataStore(emptyPreferences())),
            clock,
            scope,
        )
    private val http = OkHttpClient()
    private val limiter = RateLimiter(nowMillis = clock::millis)
    private val renewals = CopyOnWriteArrayList<String>()

    @Volatile private var listening: () -> Unit = {}

    private val tokens = TokenRefresh(
        accounts,
        vault,
        OAuthClient(http, limiter, Dispatchers.IO),
        setOf(
            TokenRenewalListener { account, old, new ->
                listening()
                renewals += "$old>$new, stored ${vault.get(VaultKey.AccessToken(account.id))}"
            },
        ),
    )
    private val clients = ClientFactory(http, limiter, Dispatchers.IO, accounts, tokens)
    private val social = Social()
    private val server = MockWebServer().apply {
        dispatcher = social
        start()
    }

    @After
    fun close() {
        scope.cancel()
        database.close()
        server.close()
    }

    private suspend fun signIn(access: String = "old-access", refreshToken: String? = "old-refresh"): SignedInAccount {
        val apiBase = server.url("/index.php/apps/social/")
        accounts.saveRegistration(apiBase.host, ClientRegistration("client-1", "secret-1", "read write follow push"))
        return accounts.signedIn(
            NewAccount(
                apiBase.host,
                "6",
                "alice",
                "Alice",
                null,
                null,
                ServerCapabilities.minimal(apiBase.toString()),
                profilePending = false,
            ),
            AccessToken(access, "", refreshToken),
        )
    }

    private suspend fun markers(account: SignedInAccount) =
        checkNotNull(clients.forAccount(account)).execute(MarkerEndpoints.read())

    private suspend fun needsReauth(account: SignedInAccount) = checkNotNull(accounts.byId(account.id)).needsReauth

    private suspend fun stored(account: SignedInAccount) =
        vault.get(VaultKey.AccessToken(account.id)) to vault.get(VaultKey.RefreshToken(account.id))

    private fun formOf(body: String): Map<String, String> = "https://form.invalid/?$body".toHttpUrl().let { url ->
        url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() }
    }

    @Test
    fun `the pair issued at sign-in is kept, and a sign-in without a refresh token leaves none behind`() = runBlocking {
        val account = signIn()
        assertEquals("old-access" to "old-refresh", stored(account))

        signIn(access = "again", refreshToken = null)

        assertEquals("again" to null, stored(account))
    }

    @Test
    fun `an expired token is refreshed once, the new pair stored, and the request sent again`() = runBlocking {
        val account = signIn()
        var storedAtRetry: Pair<String?, String?>? = null
        social.onAccepted = { storedAtRetry = runBlocking { stored(account) } }

        val result = markers(account)

        assertTrue(result is ApiResult.Success)
        val form = formOf(social.refreshes.single())
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("old-refresh", form["refresh_token"])
        assertEquals("client-1", form["client_id"])
        assertEquals("secret-1", form["client_secret"])
        assertEquals("new-access" to "new-refresh", storedAtRetry)
        assertEquals(listOf("old-access>new-access, stored new-access"), renewals)
        assertFalse(needsReauth(account))
    }

    @Test
    fun `two requests refused at once share one refresh`() = runBlocking {
        val account = signIn()
        social.refusalsHeld = CountDownLatch(2)

        val first = async(Dispatchers.IO) { markers(account) }
        val second = async(Dispatchers.IO) { markers(account) }

        assertTrue(first.await() is ApiResult.Success)
        assertTrue(second.await() is ApiResult.Success)
        assertEquals(1, social.refreshes.size)
        assertEquals("new-access" to "new-refresh", stored(account))
    }

    @Test
    fun `a refresh the server refuses, as it does for a revoked token, asks for a new sign-in`() = runBlocking {
        val account = signIn()
        social.refreshAnswer =
            Social.json("""{"error":"invalid_grant","error_description":"unknown refresh_token"}""", Social.BAD_REQUEST)

        val result = markers(account)

        assertTrue((result as ApiResult.Failure).error is ApiError.Unauthorised)
        assertEquals(1, social.refreshes.size)
        assertTrue(renewals.isEmpty())
        assertTrue(needsReauth(account))
    }

    @Test
    fun `a refresh that cannot be asked keeps the pair and the sign-in`() = runBlocking {
        val account = signIn()
        social.refreshAnswer = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

        val result = markers(account)

        assertTrue((result as ApiResult.Failure).error is ApiError.Transport)
        assertEquals("old-access" to "old-refresh", stored(account))
        assertFalse(needsReauth(account))
    }

    @Test
    fun `without a refresh token a refused token asks for a new sign-in, as before`() = runBlocking {
        val account = signIn(refreshToken = null)

        val result = markers(account)

        assertTrue((result as ApiResult.Failure).error is ApiError.Unauthorised)
        assertTrue(social.refreshes.isEmpty())
        assertTrue(needsReauth(account))
    }

    @Test
    fun `a sign-in made while the refresh was in flight keeps its own pair`() = runBlocking {
        val account = signIn()
        val held = CountDownLatch(1).also { social.refreshHeld = it }
        social.accepted += "signed-in-again"

        val request = async(Dispatchers.IO) { markers(account) }
        while (social.refreshes.isEmpty()) Thread.sleep(POLL_MILLIS)
        signIn(access = "signed-in-again", refreshToken = "its-refresh")
        held.countDown()

        assertTrue(request.await() is ApiResult.Success)
        assertEquals("signed-in-again" to "its-refresh", stored(account))
        assertEquals(1, social.refreshes.size)
        assertTrue(renewals.isEmpty())
        assertFalse(needsReauth(account))
    }

    @Test
    fun `a refresh the server answered is stored even when the request that asked for it was cancelled`() =
        runBlocking {
            val account = signIn()
            val held = CountDownLatch(1).also { social.refreshHeld = it }

            val request = launch(Dispatchers.IO) { markers(account) }
            while (social.refreshes.isEmpty()) Thread.sleep(POLL_MILLIS)
            request.cancel()
            held.countDown()
            request.join()

            assertEquals("new-access" to "new-refresh", stored(account))
            assertEquals(listOf("old-access>new-access, stored new-access"), renewals)
        }

    @Test
    fun `a listener still busy with a renewal holds up no other use of the account's lock`() = runBlocking {
        val account = signIn()
        val told = CountDownLatch(1)
        val released = CountDownLatch(1)
        listening = {
            told.countDown()
            released.await(Social.WAIT_SECONDS, TimeUnit.SECONDS)
        }

        val request = async(Dispatchers.IO) { markers(account) }
        assertTrue(told.await(Social.WAIT_SECONDS, TimeUnit.SECONDS))
        val seen = withTimeout(LOCK_MILLIS) { tokens.locked(account.id) { stored(account) } }
        released.countDown()

        assertEquals("new-access" to "new-refresh", seen)
        assertTrue(request.await() is ApiResult.Success)
    }

    @Test
    fun `signing out during a refresh revokes the token the refresh stored`() = runBlocking {
        SignedInFixture(context).use { fixture ->
            val apiBase = server.url("/index.php/apps/social/")
            val account = fixture.signIn(apiBase)
            fixture.accounts.saveRegistration(apiBase.host, ClientRegistration("client-1", "secret-1", "read"))
            fixture.vault.put(VaultKey.RefreshToken(account.id), "old-refresh")
            val held = CountDownLatch(1).also { social.refreshHeld = it }

            val client = checkNotNull(fixture.clients.forAccount(account))
            val request = async(Dispatchers.IO) { client.execute(MarkerEndpoints.read()) }
            while (social.refreshes.isEmpty()) Thread.sleep(POLL_MILLIS)
            val signOut = async(Dispatchers.IO) { fixture.removal.signOut(account) }
            Thread.sleep(SIGN_OUT_START_MILLIS)
            held.countDown()
            signOut.await()
            request.await()

            assertTrue(social.revoked.single(), social.revoked.single().contains("token=new-access"))
            assertNull(fixture.accounts.token(account.id))
        }
    }

    private companion object {
        const val POLL_MILLIS = 10L
        const val LOCK_MILLIS = 1_000L
        const val SIGN_OUT_START_MILLIS = 200L
    }
}
