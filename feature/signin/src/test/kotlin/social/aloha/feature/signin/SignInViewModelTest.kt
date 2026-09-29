// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.OAuthCallbackInbox
import social.aloha.core.data.ServerFinder
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInProblem
import social.aloha.core.database.AccountsDatabase
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.TokenVault
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthIdentity
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.network.tls.ClientCertificateAliases
import social.aloha.core.network.tls.UserTrustStore
import social.aloha.core.testing.FakeSecretCipher
import social.aloha.core.testing.InMemoryDataStore
import social.aloha.core.testing.MockServerConfiguration
import social.aloha.core.testing.MockSocialServer

// the ViewModel's scope is the main dispatcher, which a JVM test replaces
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignInViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mock = MockSocialServer(MockServerConfiguration.NextcloudWithoutRewrite).start()
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
    private val inbox = OAuthCallbackInbox()
    private lateinit var viewModel: SignInViewModel

    @Before
    fun create() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val http = OkHttpClient()
        val limiter = RateLimiter(nowMillis = Clock.systemUTC()::millis)
        viewModel = SignInViewModel(
            finder = ServerFinder(
                ServerProbe(http, limiter, Dispatchers.IO),
                UserTrustStore(File.createTempFile("trust", ".p12").apply { delete() }),
                ClientCertificateAliases(File.createTempFile("aliases", ".properties").apply { delete() }),
                cleartextAllowed = true,
                ioDispatcher = Dispatchers.IO,
            ),
            coordinator = SignInCoordinator(
                accounts,
                vault,
                OAuthClient(http, limiter, Dispatchers.IO),
                CapabilityDetector(http, limiter, Dispatchers.IO) { Instant.now() },
                { OAuthIdentity.SCHEME_REDIRECT },
                ClientFactory(http, limiter, Dispatchers.IO, accounts),
            ),
            inbox = inbox,
            savedState = SavedStateHandle(),
        )
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        scope.cancel()
        database.close()
        mock.close()
    }

    private suspend fun awaitStep(predicate: (SignInStep) -> Boolean): SignInStep =
        withTimeout(15.seconds) { viewModel.uiState.first { predicate(it.step) }.step }

    @Test
    fun `text that cannot be an address is marked invalid without a request`() {
        viewModel.onServerChange("not a host")
        viewModel.onContinue()
        assertEquals(SignInStep.EnterServerWith(AddressProblem.NotAnAddress), viewModel.uiState.value.step)
        assertEquals(0, mock.requests.size)
    }

    @Test
    fun `a found server shows its card, and the callback ends signed in`() = runBlocking<Unit> {
        viewModel.onServerChange(mock.origin.toString())
        viewModel.onContinue()
        val card = awaitStep { it is SignInStep.Instance } as SignInStep.Instance
        assertEquals("nextcloud.local", card.card.domain)
        assertTrue(card.card.isNextcloudSocial)

        viewModel.onSignIn()
        val url = withTimeout(15.seconds) { viewModel.pendingBrowserUrl.filterNotNull().first() }.toHttpUrl()
        viewModel.onBrowserOpened()
        assertEquals(SignInStep.WaitingForBrowser, awaitStep { it == SignInStep.WaitingForBrowser })

        // a stray callback leaves the wait as it was, and the page can still be opened again
        inbox.deliver("alohasocial://oauth-callback/?code=stolen&state=evil")
        withTimeout(15.seconds) { inbox.callback.first { it == null } }
        assertEquals(SignInStep.WaitingForBrowser, viewModel.uiState.value.step)
        viewModel.onOpenBrowserAgain()
        assertEquals(url.toString(), withTimeout(15.seconds) { viewModel.pendingBrowserUrl.filterNotNull().first() })
        viewModel.onBrowserOpened()

        inbox.deliver("alohasocial://oauth-callback/?code=c&state=${url.queryParameter("state")}")
        val account = withTimeout(15.seconds) { accounts.activeAccount.filterNotNull().first() }
        assertEquals(mock.origin.host, account.host)
        withTimeout(15.seconds) { inbox.callback.first { it == null } }
    }

    @Test
    fun `a server nobody answers for says it could not be reached, without the administrator note`() =
        runBlocking<Unit> {
            viewModel.onServerChange("http://127.0.0.1:1/")
            viewModel.onContinue()
            val failed = awaitStep { it is SignInStep.Failed } as SignInStep.Failed
            assertEquals(SignInFailure.Problem(SignInProblem.Offline), failed.failure)
        }
}
