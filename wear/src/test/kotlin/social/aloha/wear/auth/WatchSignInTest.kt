// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import social.aloha.core.datastore.TokenVault
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.MockServerConfiguration
import social.aloha.core.testing.MockSocialServer

class WatchSignInTest {
    private val mock = MockSocialServer(MockServerConfiguration.Mastodon).start()
    private val http = OkHttpClient()
    private val limiter = RateLimiter(nowMillis = Clock.systemUTC()::millis)
    private val vault = TokenVault(InMemoryDataStore(ByteArray(0)), FakeSecretCipher(), Dispatchers.IO)
    private val accounts = WatchAccounts(vault)

    /** The phone as a person uses it: the consent page opens, and they tap its one link back. */
    private inner class ConsentingPhone(private val approve: Boolean = true) : PhoneBrowser {
        val opened = mutableListOf<HttpUrl>()
        override val redirectUri = "https://wear.googleapis.com/3p_auth/social.aloha.android"

        override suspend fun authorize(url: HttpUrl, clientId: String, verifier: String): PhoneAnswer {
            opened += url
            if (!approve) return PhoneAnswer.Returned("$redirectUri?error=access_denied&state=" + url.state())
            val page = http.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
            return PhoneAnswer.Returned(LINK.find(page)!!.groupValues[1].replace("&amp;", "&"))
        }

        private fun HttpUrl.state() = queryParameter("state").orEmpty()
    }

    private fun signIn(phone: PhoneBrowser) = WatchSignIn(
        ServerProbe(http, limiter, Dispatchers.IO),
        OAuthClient(http, limiter, Dispatchers.IO),
        phone,
        accounts,
        http,
        limiter,
        Dispatchers.IO,
    )

    @After
    fun close() {
        mock.close()
    }

    @Test
    fun `the watch signs in with a client and a token of its own, through the page on the phone`() = runBlocking {
        val phone = ConsentingPhone()
        val result = signIn(phone).signIn(mock.origin.toString())

        val account = (result as WatchSignInResult.SignedIn).account
        assertEquals(MockCredentials.ACCESS_TOKEN, account.token)
        assertEquals(account, accounts.current())
        val asked = phone.opened.single()
        assertEquals(phone.redirectUri, asked.queryParameter("redirect_uri"))
        assertEquals(WatchSignIn.SCOPES, asked.queryParameter("scope"))
        assertEquals("S256", asked.queryParameter("code_challenge_method"))
        val registered = mock.requests.single { it.url.encodedPath.endsWith("/api/v1/apps") }.body?.utf8().orEmpty()
        assertTrue(registered, "Wear+OS" in registered && "wear.googleapis.com" in registered)
    }

    @Test
    fun `a sign-in declined on the phone keeps nothing`() = runBlocking {
        val result = signIn(ConsentingPhone(approve = false)).signIn(mock.origin.toString())

        assertEquals(WatchSignInResult.Failed(WatchSignInProblem.Refused), result)
        assertNull(accounts.current())
    }

    @Test
    fun `signing out gives the token back and forgets the account`() = runBlocking {
        val watch = signIn(ConsentingPhone())
        watch.signIn(mock.origin.toString())
        watch.signOut()

        assertNull(accounts.current())
        assertTrue(mock.requests.any { it.url.encodedPath.endsWith("/oauth/revoke") })
    }

    private companion object {
        val LINK = Regex("""<a href="([^"]+)"""")
    }
}
