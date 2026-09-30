// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.nextcloud

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.toAnswer
import social.aloha.core.database.AccountDao
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AppPasswordGrant
import social.aloha.core.model.LoginFlowStart
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiClient
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.Credentials
import social.aloha.core.network.endpoints.NextcloudEndpoints

/** How starting a connection went. */
public sealed interface ConnectStart {
    /** The person approves at [start]'s login page; [NextcloudConnection.await] waits for it. */
    public data class Opened(val start: LoginFlowStart) : ConnectStart

    /** The server is no Nextcloud, so there is nothing to connect. */
    public data object NotNextcloud : ConnectStart

    /** The Nextcloud is unreachable, not installed, or in maintenance: worth trying again later. */
    public data object Unavailable : ConnectStart
}

/** How waiting for the person's approval ended. */
public enum class ConnectResult { Connected, TimedOut, Failed }

/**
 * Connects a Social account to the Nextcloud it runs on, for what only a Nextcloud session opens: push
 * through the Nextcloud, its Files, and the routes Social refuses a token. With Login Flow v2 the person
 * approves in their browser and the app is handed an app password; it is kept in the vault beside the
 * token, and given back to the Nextcloud on disconnect and on sign-out.
 */
@Singleton
public class NextcloudConnection @Inject constructor(
    private val clients: ClientFactory,
    private val accounts: AccountRepository,
    private val dao: AccountDao,
    private val vault: TokenVault,
) {
    /** Checks that the Nextcloud is up, then opens a Login Flow on it. */
    public suspend fun begin(account: SignedInAccount): ConnectStart {
        val client = root(account) ?: return ConnectStart.NotNextcloud
        return when (val status = client.execute(NextcloudEndpoints.status())) {
            is ApiResult.Failure -> status.error.asStart()
            is ApiResult.Success -> if (status.value.ready) open(client) else ConnectStart.Unavailable
        }
    }

    private suspend fun open(client: ApiClient): ConnectStart =
        when (val start = client.execute(NextcloudEndpoints.loginFlowStart())) {
            is ApiResult.Success -> ConnectStart.Opened(start.value)
            is ApiResult.Failure -> start.error.asStart()
        }

    /**
     * Asks for the grant until the person approved, for at most five minutes, first after a second and
     * then less and less often. The answer is only asked for on the Nextcloud's own origin: a poll address
     * elsewhere, or over plain HTTP where the Nextcloud has HTTPS, fails rather than carry the password.
     */
    public suspend fun await(account: SignedInAccount, start: LoginFlowStart): ConnectResult {
        val client = root(account)
        val path = client?.let { pollPath(it.apiBase, start.pollEndpoint) } ?: return ConnectResult.Failed
        return withTimeoutOrNull(LOGIN_WINDOW) { poll(account, client, path, start.pollToken) }
            ?: ConnectResult.TimedOut
    }

    private suspend fun poll(account: SignedInAccount, client: ApiClient, path: String, token: String): ConnectResult {
        var wait = FIRST_WAIT
        while (true) {
            val answer = client.execute(NextcloudEndpoints.loginFlowPoll(path, token))
            when {
                answer is ApiResult.Success -> return store(account, answer.value)

                // 404 is "not approved yet"; only an answer that cannot be read ends the wait early
                answer is ApiResult.Failure && answer.error is ApiError.Decoding -> return ConnectResult.Failed
            }
            delay(wait)
            wait = minOf(wait * BACKOFF, MAXIMUM_WAIT)
        }
    }

    /** The push kinds the Nextcloud's notifications app offers this connected account. */
    internal suspend fun pushTypes(account: SignedInAccount): Answer<List<String>> =
        root(account)?.execute(NextcloudEndpoints.pushTypes()).toAnswer()

    /**
     * The key a Web Push registration is made for, where the notifications app pushes to this person;
     * null where it does not (no `webpush` in its push kinds, `webpush_enabled` unset).
     */
    public suspend fun webPushVapid(account: SignedInAccount): String? {
        val kinds = (pushTypes(account) as? Answer.Got)?.value.orEmpty()
        if ("webpush" !in kinds) return null
        return (root(account)?.execute(NextcloudEndpoints.webPushVapid()) as? ApiResult.Success)?.value?.ifEmpty {
            null
        }
    }

    /** Has the Nextcloud push the Social app's notifications to [endpoint]; activation follows by push. */
    public suspend fun registerWebPush(
        account: SignedInAccount,
        endpoint: String,
        publicKey: String,
        auth: String,
    ): ApiError? = execute(account, NextcloudEndpoints.registerWebPush(endpoint, publicKey, auth))

    /** Confirms the registration with the [token] its first push carried. */
    public suspend fun activateWebPush(account: SignedInAccount, token: String): ApiError? =
        execute(account, NextcloudEndpoints.activateWebPush(token))

    /** Removes this device's registration; the Nextcloud stops pushing to it. */
    public suspend fun unregisterWebPush(account: SignedInAccount) {
        execute(account, NextcloudEndpoints.unregisterWebPush())
    }

    /**
     * Forgets the app password on the device, then gives it back to the Nextcloud, after removing the
     * push registration it made while the password still works.
     */
    public suspend fun disconnect(account: SignedInAccount) {
        val basic = vault.get(VaultKey.AppPassword(account.id))
        if (basic != null) unregisterWebPush(account)
        vault.remove(VaultKey.AppPassword(account.id))
        dao.setNextcloudConnected(account.id, connected = false)
        if (basic != null) revoke(account, basic)
    }

    private suspend fun execute(account: SignedInAccount, request: ApiRequest<Unit>): ApiError? =
        (root(account)?.execute(request).toAnswer() as? Answer.Missed)?.error

    /** Gives [basic] back to [account]'s Nextcloud; one that cannot be reached keeps it until it expires. */
    internal suspend fun revoke(account: SignedInAccount, basic: String) {
        val base = account.capabilities.nextcloudRoot.toHttpUrlOrNull() ?: return
        clients.create(base) { Credentials(bearerToken = null, nextcloudBasic = basic) }
            .execute(NextcloudEndpoints.revokeAppPassword())
    }

    private suspend fun store(account: SignedInAccount, grant: AppPasswordGrant): ConnectResult {
        // approved after the account signed out: the password goes back rather than into a vault nobody reads
        if (accounts.byId(account.id) == null) {
            revoke(account, grant.basicAuthorization)
            return ConnectResult.Failed
        }
        vault.put(VaultKey.AppPassword(account.id), grant.basicAuthorization)
        dao.setNextcloudConnected(account.id, connected = true)
        return ConnectResult.Connected
    }

    private fun root(account: SignedInAccount): ApiClient? {
        val base = account.capabilities.nextcloudRoot.toHttpUrlOrNull() ?: return null
        return clients.create(base) { accounts.credentials(account.id) }
    }

    internal companion object {
        private val FIRST_WAIT = 1.seconds
        private val MAXIMUM_WAIT = 5.seconds
        private val LOGIN_WINDOW = 5.minutes
        private const val BACKOFF = 1.5

        /** [endpoint] below [root], on the same origin; null when it is anywhere else. */
        fun pollPath(root: HttpUrl, endpoint: String): String? {
            val url = endpoint.toHttpUrlOrNull() ?: return null
            if (url.scheme != root.scheme || url.host != root.host || url.port != root.port) return null
            val base = root.pathSegments.filter { it.isNotEmpty() }
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            if (segments.size <= base.size || segments.take(base.size) != base) return null
            return segments.drop(base.size).joinToString("/")
        }
    }
}

private fun ApiError.asStart(): ConnectStart =
    if (this is ApiError.NotFound) ConnectStart.NotNextcloud else ConnectStart.Unavailable
