// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiClient
import social.aloha.core.network.Credentials
import social.aloha.core.network.RateLimiter
import social.aloha.core.network.TokenRenewal
import social.aloha.core.network.di.IoDispatcher

/**
 * Builds the API client for an account, or for a base and credentials not stored yet. Without [tokens]
 * a refused token is never renewed.
 */
@Singleton
public class ClientFactory @Inject constructor(
    private val http: OkHttpClient,
    private val rateLimiter: RateLimiter,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val accounts: AccountRepository,
    private val tokens: TokenRefresh? = null,
) {
    public fun create(apiBase: HttpUrl, credentials: suspend () -> Credentials): ApiClient =
        ApiClient(apiBase, { credentials() }, http, rateLimiter, ioDispatcher)

    /**
     * The client for a signed-in account: a 401 renews its token where it has a refresh token, and marks
     * the account as needing a new sign-in where nothing renews it.
     */
    public fun forAccount(account: SignedInAccount): ApiClient? {
        val base = account.apiBase.toHttpUrlOrNull() ?: return null
        return ApiClient(
            base,
            { accounts.credentials(account.id) },
            http,
            rateLimiter,
            ioDispatcher,
            onUnauthorised = { accounts.markNeedsReauth(account.id) },
            renew = { rejected -> tokens?.renew(account, rejected) ?: TokenRenewal.Refused },
        )
    }
}
