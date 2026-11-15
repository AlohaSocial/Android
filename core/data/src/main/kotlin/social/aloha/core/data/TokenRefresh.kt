// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.datastore.TokenVault
import social.aloha.core.datastore.VaultKey
import social.aloha.core.model.AccessToken
import social.aloha.core.model.LogArea
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.TokenRenewal
import social.aloha.core.network.oauth.OAuthClient
import social.aloha.core.network.oauth.OAuthEndpoints
import timber.log.Timber

/**
 * Told when an account's access token was replaced by a refresh, once the new one is stored and before
 * the refused request is sent again, outside the account's lock and even when the request was cancelled;
 * it handles its own failures.
 */
public fun interface TokenRenewalListener {
    public suspend fun renewed(account: SignedInAccount, old: String, new: String)
}

/**
 * Renews an account's refused access token with its refresh token. Aloha Social rotates both tokens
 * on every refresh and revokes the whole authorization when a spent refresh token is presented again, so
 * one renewal runs per account at a time, a request refused meanwhile uses the token it stored, and the
 * new pair replaces the old in the vault before anything uses it. Once asked, a refresh and the storing of
 * its answer run to the end even when the request that needed it is cancelled. A refresh whose answer is
 * lost after the server rotated the pair (the connection broke) leaves the spent pair stored, and the
 * server revokes the authorization when it is presented: the account then needs a new sign-in.
 */
@Singleton
public class TokenRefresh @Inject constructor(
    private val accounts: AccountRepository,
    private val vault: TokenVault,
    private val oauth: OAuthClient,
    private val listeners: Set<@JvmSuppressWildcards TokenRenewalListener> = emptySet(),
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Renews [account]'s token after the server refused [rejected]. A refresh the server refuses (400 or
     * 401) leaves nothing to renew with; one that could not be asked keeps the pair stored.
     */
    public suspend fun renew(account: SignedInAccount, rejected: String): TokenRenewal {
        var replacement: String? = null
        val renewal = locked(account.id) {
            val current = vault.get(VaultKey.AccessToken(account.id))
            val refreshToken = vault.get(VaultKey.RefreshToken(account.id))
            when {
                current == null -> TokenRenewal.Refused

                current != rejected -> TokenRenewal.Renewed

                refreshToken == null -> TokenRenewal.Refused

                else -> withContext(NonCancellable) {
                    refresh(account, rejected, refreshToken) { replacement = it }
                }
            }
        }
        replacement?.let { new ->
            withContext(NonCancellable) { listeners.forEach { it.renewed(account, rejected, new) } }
        }
        return renewal
    }

    /** Runs [block] while no renewal of [accountId]'s token is in flight, and none starts until it ends. */
    public suspend fun <T> locked(accountId: String, block: suspend () -> T): T =
        locks.getOrPut(accountId) { Mutex() }.withLock { block() }

    private suspend fun refresh(
        account: SignedInAccount,
        rejected: String,
        refreshToken: String,
        written: (String) -> Unit,
    ): TokenRenewal {
        val base = account.apiBase.toHttpUrlOrNull()
        val registration = accounts.registration(account.host, account.id)
        if (base == null || registration == null) return TokenRenewal.Refused
        val endpoint = OAuthEndpoints.from(oauth.metadata(base), base).token
        return when (val answer = oauth.refresh(endpoint, registration, refreshToken)) {
            is ApiResult.Success -> stored(account, rejected, answer.value, written)

            is ApiResult.Failure -> {
                val error = answer.error
                Timber.tag(LogArea.Auth.name).i("Refresh for %s failed: %s", account.id, error.javaClass.simpleName)
                if (error.refusesRefresh()) TokenRenewal.Refused else TokenRenewal.Failed(error)
            }
        }
    }

    /**
     * Puts [token] and its refresh token in place of the old pair in one write, only while [rejected] is
     * still the account's token: one signed out or signed in again meanwhile keeps what it holds. A token
     * without a refresh token leaves none behind, so a spent one is never kept. [written] hears the new
     * token once it is stored.
     */
    private suspend fun stored(
        account: SignedInAccount,
        rejected: String,
        token: AccessToken,
        written: (String) -> Unit,
    ): TokenRenewal {
        val id = account.id
        val replaced = vault.putAllIf(
            VaultKey.AccessToken(id),
            rejected,
            mapOf(VaultKey.AccessToken(id) to token.value, VaultKey.RefreshToken(id) to token.refreshToken),
        )
        if (replaced) written(token.value)
        return if (vault.get(VaultKey.AccessToken(id)) == null) TokenRenewal.Refused else TokenRenewal.Renewed
    }

    private fun ApiError.refusesRefresh(): Boolean =
        this is ApiError.Unauthorised || (this is ApiError.Server && status == BAD_REQUEST)

    private companion object {
        const val BAD_REQUEST = 400
    }
}
