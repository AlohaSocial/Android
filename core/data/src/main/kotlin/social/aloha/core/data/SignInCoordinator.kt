// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.Account
import social.aloha.core.model.LogArea
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Credentials
import social.aloha.core.network.capabilities.CapabilityDetector
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.InstanceEndpoints
import social.aloha.core.network.errorOrNull
import social.aloha.core.network.oauth.OAuthCallback
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthEndpoints
import social.aloha.core.network.oauth.OAuthIdentity
import social.aloha.core.network.oauth.PendingAuthorization
import social.aloha.core.network.oauth.authorizationUrl
import social.aloha.core.network.valueOrNull
import timber.log.Timber

/**
 * Sign-in from a discovered server to a stored, active account: register the app once per server,
 * open the authorisation page, exchange the code that comes back, describe the account, detect what
 * the server can do. The sign-in in flight is kept in the vault, so it survives the process dying
 * while the browser tab is open.
 */
@Singleton
public class SignInCoordinator @Inject constructor(
    private val accounts: AccountRepository,
    private val vault: TokenVault,
    private val oauth: OAuthClient,
    private val detector: CapabilityDetector,
    private val redirects: RedirectUriProvider,
    private val clients: ClientFactory,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Registers where needed and returns the page to open in the browser. */
    public suspend fun beginAuthorization(server: DiscoveredServer): Authorization {
        val outcome = server.outcome
        val base = outcome.apiBase
        val registration = accounts.registration(base.host) ?: when (val registered = oauth.register(base)) {
            is ApiResult.Success -> registered.value.also { accounts.saveRegistration(base.host, it) }
            is ApiResult.Failure -> return Authorization.Failed(registered.error.toProblem())
        }
        val metadata = outcome.authorizationServer ?: oauth.metadata(base)
        val (url, started) =
            authorizationUrl(OAuthEndpoints.from(metadata, base), base, registration.clientId, redirects.redirectUri())
        val pending = started.copy(nodeInfo = outcome.nodeInfo)
        vault.put(VaultKey.PendingAuthorization, json.encodeToString(PendingAuthorization.serializer(), pending))
        return Authorization.Started(url.toString())
    }

    /**
     * A moderator's second authorisation of [account] for the admin scopes, finished by [complete] like a
     * sign-in. A server refuses to authorise more than the app was registered with, so a registration made
     * for the ordinary scopes is replaced by one that also lists the admin scopes.
     */
    public suspend fun beginModeration(account: SignedInAccount): Authorization {
        val base = account.apiBase.toHttpUrlOrNull()
            ?: return Authorization.Failed(SignInProblem.ServerProblem(null))
        val kept = accounts.registration(base.host)?.takeIf { registration ->
            OAuthIdentity.MODERATOR_SCOPES.split(' ').all { it in registration.scopes.split(' ') }
        }
        val registration = kept ?: when (val registered = oauth.register(base, OAuthIdentity.MODERATOR_SCOPES)) {
            is ApiResult.Success -> registered.value.also { accounts.saveRegistration(base.host, it) }
            is ApiResult.Failure -> return Authorization.Failed(registered.error.toProblem())
        }
        val (url, pending) = authorizationUrl(
            OAuthEndpoints.from(oauth.metadata(base), base),
            base,
            registration.clientId,
            redirects.redirectUri(),
            scope = OAuthIdentity.MODERATOR_SCOPES,
        )
        vault.put(VaultKey.PendingAuthorization, json.encodeToString(PendingAuthorization.serializer(), pending))
        return Authorization.Started(url.toString())
    }

    /** Whether a sign-in is waiting for its callback, e.g. after the process was restarted. */
    public suspend fun hasPendingAuthorization(): Boolean = pending() != null

    /** The page of the sign-in waiting for its callback, to open it again; null without one. */
    public suspend fun pendingAuthorizationUrl(): String? = pending()?.authorizationUrl

    public suspend fun cancel() {
        vault.remove(VaultKey.PendingAuthorization)
    }

    /** Finishes the sign-in with the callback URI the browser handed back. Never retried automatically. */
    public suspend fun complete(callbackUri: String): SignInResult {
        val pending = pending() ?: return SignInResult.NotForUs
        return when (val callback = OAuthCallback.parse(callbackUri, pending.state)) {
            OAuthCallback.StateMismatch -> SignInResult.NotForUs
            is OAuthCallback.Denied -> refused(callback.reason).also { cancel() }
            OAuthCallback.MissingCode -> refused(null).also { cancel() }
            is OAuthCallback.Code -> exchange(pending, callback.code).also { cancel() }
        }
    }

    private fun refused(reason: String?) = SignInResult.Failed(SignInProblem.Refused(reason))

    private suspend fun exchange(pending: PendingAuthorization, code: String): SignInResult {
        val base = pending.apiBase.toHttpUrlOrNull() ?: return SignInResult.NotForUs
        val tokenEndpoint = pending.tokenEndpoint.toHttpUrlOrNull() ?: return SignInResult.NotForUs
        val registration = accounts.registration(base.host) ?: return SignInResult.NotForUs
        return when (val token = oauth.exchange(tokenEndpoint, registration, pending, code)) {
            is ApiResult.Success -> describe(pending, base, token.value)

            is ApiResult.Failure -> {
                if (token.error is ApiError.Unauthorised) accounts.forgetRegistration(base.host)
                SignInResult.Failed(token.error.toProblem())
            }
        }
    }

    /**
     * `verify_credentials`, or `/oauth/userinfo` when the server cannot describe a brand-new account
     * yet (Nextcloud Social answers 500 until its avatar cache job has run).
     */
    private suspend fun describe(pending: PendingAuthorization, base: HttpUrl, token: AccessToken): SignInResult {
        val client = clients.create(base) { Credentials(token.value) }
        val instance = client.execute(InstanceEndpoints.v2()).valueOrNull()
            ?: client.execute(InstanceEndpoints.v1()).valueOrNull()
        val me = client.execute(AccountEndpoints.verifyCredentials())
        val newAccount = when (me) {
            is ApiResult.Success -> me.value.toNewAccount(base)
            is ApiResult.Failure -> fromUserInfo(base, pending, token, me.error)
        }
        if (instance == null || newAccount == null) {
            return SignInResult.Failed((me.errorOrNull() ?: ApiError.NotFound).toProblem())
        }
        val capabilities = detector.detect(base, token, instance, pending.nodeInfo)
        return SignInResult.SignedIn(accounts.signedIn(newAccount.copy(capabilities = capabilities), token))
    }

    private fun Account.toNewAccount(base: HttpUrl) = NewAccount(
        host = base.host,
        serverAccountId = id,
        handle = acct,
        displayName = bestDisplayName,
        avatarUrl = avatar,
        headerUrl = header,
        capabilities = placeholder(base),
        profilePending = false,
    )

    private suspend fun fromUserInfo(
        base: HttpUrl,
        pending: PendingAuthorization,
        token: AccessToken,
        error: ApiError,
    ): NewAccount? {
        if (error !is ApiError.Server || error.status < SERVER_ERROR) return null
        val userinfo = pending.userinfoEndpoint.toHttpUrlOrNull() ?: return null
        val info = (oauth.userInfo(userinfo, token) as? ApiResult.Success)?.value ?: return null
        val handle = info.preferredUsername ?: info.subject.substringAfterLast('@')
        return NewAccount(
            base.host,
            info.subject,
            handle,
            info.name ?: handle,
            null,
            null,
            placeholder(base),
            profilePending = true,
        )
    }

    private fun placeholder(base: HttpUrl) = ServerCapabilities.minimal(base.toString())

    private suspend fun pending(): PendingAuthorization? = vault.get(VaultKey.PendingAuthorization)?.let {
        try {
            json.decodeFromString(PendingAuthorization.serializer(), it)
        } catch (e: SerializationException) {
            Timber.tag(LogArea.Auth.name).w("Pending authorization unreadable: %s", e.javaClass.simpleName)
            null
        }
    }

    private companion object {
        const val SERVER_ERROR = 500
    }
}

/** A server's own words are kept when they are words, not an HTML error page. */
internal fun ApiError.toProblem(): SignInProblem = when (this) {
    is ApiError.UntrustedCertificate -> toCertificate()?.let(SignInProblem::UntrustedCertificate)
        ?: SignInProblem.Offline

    is ApiError.Transport, is ApiError.RateLimited -> SignInProblem.Offline

    is ApiError.Unauthorised -> SignInProblem.Refused(message)

    is ApiError.Forbidden -> SignInProblem.Refused(message)

    is ApiError.Unprocessable -> SignInProblem.ServerProblem(message)

    is ApiError.Server -> SignInProblem.ServerProblem(
        body?.takeUnless {
            it.isBlank() || it.trimStart().startsWith("<")
        },
    )

    else -> SignInProblem.ServerProblem(null)
}
