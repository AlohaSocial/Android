// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import social.aloha.core.model.AccessToken
import social.aloha.core.model.ClientRegistration
import social.aloha.core.network.ApiClient
import social.aloha.core.network.Credentials
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.di.IoDispatcher
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.oauth.OAuthCallback
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthEndpoints
import social.aloha.core.network.oauth.PendingAuthorization
import social.aloha.core.network.oauth.authorizationUrl
import social.aloha.core.network.probe.ProbeResult
import social.aloha.core.network.probe.ServerAddress
import social.aloha.core.network.probe.ServerProbe
import social.aloha.core.network.valueOrNull
import social.aloha.wear.BuildConfig

/** Why signing in on the watch stopped. */
enum class WatchSignInProblem { Address, Unreachable, Server, Phone, Refused, NothingOnPhone }

sealed interface WatchSignInResult {
    data class SignedIn(val account: WatchAccount) : WatchSignInResult

    data class Failed(val problem: WatchSignInProblem) : WatchSignInResult
}

/**
 * The watch signing in on its own: it registers a client of its own on the server, opens the server's
 * sign-in page on the phone and exchanges the code that comes back for a token of its own, so the
 * watch shows in Authorized apps by itself and can be signed out there without the phone.
 */
@Singleton
class WatchSignIn @Inject constructor(
    private val probe: ServerProbe,
    private val oauth: OAuthClient,
    private val browser: PhoneBrowser,
    private val phone: PhoneAccounts,
    private val accounts: WatchAccounts,
    private val http: OkHttpClient,
    private val rateLimiter: RateLimiter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Finds the server and registers the watch's own client there, then has the person sign in on the phone. */
    suspend fun signIn(typed: String): WatchSignInResult {
        val address = ServerAddress.parse(typed, allowCleartext = BuildConfig.DEBUG)
            ?: return failed(WatchSignInProblem.Address)
        val outcome = (probe.discover(address) as? ProbeResult.Found)?.outcome
            ?: return failed(WatchSignInProblem.Unreachable)
        val registration = oauth.register(outcome.apiBase, SCOPES, REDIRECTS, CLIENT_NAME).valueOrNull()
            ?: return failed(WatchSignInProblem.Server)
        val base = outcome.apiBase
        val endpoints = OAuthEndpoints.from(outcome.authorizationServer ?: oauth.metadata(base), base)
        val (url, pending) =
            authorizationUrl(endpoints, base, registration.clientId, browser.redirectUri, scope = SCOPES)
        return authorize(url, pending, endpoints, registration)
    }

    private suspend fun authorize(
        url: HttpUrl,
        pending: PendingAuthorization,
        endpoints: OAuthEndpoints,
        registration: ClientRegistration,
    ): WatchSignInResult {
        val returned = (browser.authorize(url, registration.clientId, pending.verifier) as? PhoneAnswer.Returned)?.url
            ?: return failed(WatchSignInProblem.Phone)
        val code = (OAuthCallback.parse(returned, pending.state) as? OAuthCallback.Code)?.code
            ?: return failed(WatchSignInProblem.Refused)
        val token = oauth.exchange(endpoints.token, registration, pending, code).valueOrNull()
            ?: return failed(WatchSignInProblem.Server)
        val me = ApiClient(pending.apiBase.toHttpUrl(), { Credentials(token.value) }, http, rateLimiter, io)
            .execute(AccountEndpoints.verifyCredentials()).valueOrNull()
            ?: return unusable(endpoints, registration, token)
        val account = WatchAccount(
            apiBase = pending.apiBase,
            handle = me.acct,
            token = token.value,
            clientId = registration.clientId,
            clientSecret = registration.clientSecret,
            revocationEndpoint = endpoints.revocation.toString(),
        )
        accounts.keep(account)
        return WatchSignInResult.SignedIn(account)
    }

    /**
     * The account in use on the phone, which needs no sign-in here: it comes with the phone's token. Null when
     * the phone had none to give.
     */
    suspend fun fromPhone(): WatchAccount? {
        val handed = phone.account()?.takeIf { it.token.isNotEmpty() } ?: return null
        return WatchAccount(handed.apiBase, handed.handle, handed.token, shared = true).also { accounts.keep(it) }
    }

    /**
     * Forgets the account first, so the watch is signed out whatever the network does or however the request
     * ends, then gives the watch's own token back to the server where it can be reached; the phone's token, when
     * the account came from there, is only forgotten.
     */
    suspend fun signOut() {
        val account = accounts.current() ?: return
        accounts.forget()
        val base = account.apiBase.toHttpUrlOrNull()
        val revocation = account.revocationEndpoint.toHttpUrlOrNull()
        if (!account.shared && base != null && revocation != null) {
            oauth.revoke(
                OAuthEndpoints.conventional(base).copy(revocation = revocation),
                ClientRegistration(account.clientId, account.clientSecret, SCOPES),
                AccessToken(account.token, ""),
            )
        }
    }

    /** A token issued for an account the server would not then describe is given back, not left behind. */
    private suspend fun unusable(
        endpoints: OAuthEndpoints,
        registration: ClientRegistration,
        token: AccessToken,
    ): WatchSignInResult {
        oauth.revoke(endpoints, registration, token)
        return failed(WatchSignInProblem.Server)
    }

    private fun failed(problem: WatchSignInProblem) = WatchSignInResult.Failed(problem)

    companion object {
        /** Reading, and acting on a post; the watch follows no one and takes no push of its own. */
        const val SCOPES = "read write"

        /** How the watch shows in the server's list of the account's apps. */
        const val CLIENT_NAME = "Aloha Social for Wear OS"

        /** Wear's two redirect addresses, one per line; which one a watch uses depends on its region. */
        val REDIRECTS =
            "https://wear.googleapis.com/3p_auth/${BuildConfig.APPLICATION_ID}\n" +
                "https://wear.googleapis-cn.com/3p_auth/${BuildConfig.APPLICATION_ID}"
    }
}
