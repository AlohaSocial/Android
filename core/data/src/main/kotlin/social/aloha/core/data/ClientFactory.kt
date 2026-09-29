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
import social.aloha.core.network.di.IoDispatcher

/** Builds the API client for an account, or for a base and credentials not stored yet. */
@Singleton
public class ClientFactory @Inject constructor(
    private val http: OkHttpClient,
    private val rateLimiter: RateLimiter,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val accounts: AccountRepository,
) {
    public fun create(
        apiBase: HttpUrl,
        onUnauthorised: suspend () -> Unit = {},
        credentials: suspend () -> Credentials,
    ): ApiClient =
        ApiClient(apiBase, { credentials() }, http, rateLimiter, ioDispatcher, onUnauthorised = onUnauthorised)

    /** The client for a signed-in account: a 401 marks the account as needing a new sign-in. */
    public fun forAccount(account: SignedInAccount): ApiClient? {
        val base = account.apiBase.toHttpUrlOrNull() ?: return null
        return create(base, onUnauthorised = {
            accounts.markNeedsReauth(account.id)
        }) { accounts.credentials(account.id) }
    }
}
