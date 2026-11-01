// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.signin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.RedirectUriProvider
import social.aloha.core.data.ServerFinder
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthIdentity
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.network.tls.ClientCertificateAliases
import social.aloha.core.network.tls.UserTrustStore
import social.aloha.core.testing.MainDispatcherRule
import social.aloha.core.testing.MockCredentials
import social.aloha.core.testing.MockServerConfiguration
import social.aloha.core.testing.MockSocialServer
import social.aloha.core.testing.SignedInFixture

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TvSignInViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SignedInFixture(ApplicationProvider.getApplicationContext<Context>())
    private val mock = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start()
    private lateinit var viewModel: TvSignInViewModel

    @Before
    fun create() {
        val http = OkHttpClient()
        val limiter = RateLimiter(nowMillis = fixture.clock::millis)
        val outOfBand = object : RedirectUriProvider {
            override fun redirectUri() = OAuthIdentity.OUT_OF_BAND_REDIRECT

            override fun registeredRedirects() = OAuthIdentity.OUT_OF_BAND_REDIRECT
        }
        viewModel = main.keep(
            TvSignInViewModel(
                ServerFinder(
                    ServerProbe(http, limiter, Dispatchers.IO),
                    UserTrustStore(File.createTempFile("trust", ".p12").apply { delete() }),
                    ClientCertificateAliases(File.createTempFile("aliases", ".properties").apply { delete() }),
                    cleartextAllowed = true,
                    ioDispatcher = Dispatchers.IO,
                ),
                SignInCoordinator(
                    fixture.accounts,
                    fixture.vault,
                    OAuthClient(http, limiter, Dispatchers.IO),
                    CapabilityDetector(http, limiter, Dispatchers.IO) { Instant.now() },
                    outOfBand,
                    fixture.clients,
                ),
            ),
        )
    }

    @After
    fun close() {
        main.clear()
        fixture.close()
        mock.close()
    }

    private suspend fun page(): TvSignInState.Code =
        withTimeout(15.seconds) { viewModel.uiState.first { it is TvSignInState.Code } } as TvSignInState.Code

    @Test
    fun `the server's sign-in page comes as a code to scan, and the code it shows signs the TV in`() = runBlocking {
        viewModel.onServer(mock.origin.toString())
        val page = page()
        assertEquals(OAuthIdentity.OUT_OF_BAND_REDIRECT, page.url.toHttpUrl().queryParameter("redirect_uri"))
        assertEquals(mock.origin.host, page.host)

        viewModel.onCode(MockCredentials.AUTHORIZATION_CODE)
        val account = withTimeout(15.seconds) { fixture.accounts.activeAccount.filterNotNull().first() }
        assertEquals(MockCredentials.ACCESS_TOKEN, fixture.accounts.token(account.id)?.value)
        // ready for the next sign-in, after a sign-out, rather than still at work on this one
        val after = withTimeout(15.seconds) { viewModel.uiState.first { it is TvSignInState.Server } }
        assertEquals(TvSignInState.Server(), after)
    }

    @Test
    fun `a refused code starts over with a new page to scan, and says so`() = runBlocking {
        mock.pin("POST", "/oauth/token", 400, """{"error":"invalid_grant"}""")
        viewModel.onServer(mock.origin.toString())
        val first = page()

        viewModel.onCode("mistyped")
        val again = withTimeout(15.seconds) {
            viewModel.uiState.first { it is TvSignInState.Code && it.problem != null }
        } as TvSignInState.Code
        assertEquals(TvSignInProblem.CodeRefused, again.problem)
        assertNotEquals(first.url, again.url)
        assertTrue(fixture.accounts.all().isEmpty())
    }

    @Test
    fun `text that cannot be an address is refused without a request`() {
        viewModel.onServer("not a host")
        assertEquals(TvSignInState.Server(TvSignInProblem.Address), viewModel.uiState.value)
        assertTrue(mock.requests.isEmpty())
    }
}
