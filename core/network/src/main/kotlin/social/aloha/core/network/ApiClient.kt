// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** The credentials of one account, read afresh for every request so a new sign-in takes effect at once. */
public data class Credentials(val bearerToken: String?, val nextcloudBasic: String? = null) {
    override fun toString(): String = "Credentials(bearer=${bearerToken != null}, nextcloud=${nextcloudBasic != null})"
}

public fun interface CredentialSource {
    public suspend fun current(): Credentials
}

/** Told what lossy decoding dropped, so the loss is logged rather than silent. */
public fun interface DecodingFailureListener {
    public fun onDropped(path: String, failures: List<DecodingFailure>)
}

/**
 * One account's view of its server: the resolved API base, its credentials and the shared HTTP
 * client and rate limiter. Returns [ApiResult]s; it never retries a write.
 */
public class ApiClient internal constructor(
    public val apiBase: HttpUrl,
    private val credentials: CredentialSource,
    private val executor: RequestExecutor,
    private val onUnauthorised: suspend () -> Unit,
) {
    /**
     * @param onUnauthorised told when the server answers 401 to a request that carried this account's
     *   credential: the token was revoked and the account needs a new sign-in.
     */
    public constructor(
        apiBase: HttpUrl,
        credentials: CredentialSource,
        http: OkHttpClient,
        rateLimiter: RateLimiter,
        ioDispatcher: CoroutineDispatcher,
        failureListener: DecodingFailureListener = DecodingFailureListener { _, _ -> },
        onUnauthorised: suspend () -> Unit = {},
    ) : this(apiBase, credentials, RequestExecutor(http, rateLimiter, ioDispatcher, failureListener), onUnauthorised)

    public suspend fun <T> execute(request: ApiRequest<T>): ApiResult<T> =
        perform(request, request.endpoint.resolve(apiBase)).map { it.decoded.value }

    /** A page from [request]; [limit] is what the request asked for, used where the server sends no `Link`. */
    public suspend fun <T> page(request: ApiRequest<List<T>>, limit: Int): ApiResult<Paginated<T>> =
        perform(request, request.endpoint.resolve(apiBase)).map { it.toPage(limit) }

    /**
     * The page a `Link` cursor points at, followed verbatim so every filter of the first request
     * survives. A cursor may only point at the API base's own origin: a link elsewhere is refused, so
     * a hostile server cannot steer the token to another host.
     */
    public suspend fun <T> page(following: HttpUrl, like: ApiRequest<List<T>>, limit: Int): ApiResult<Paginated<T>> {
        if (!following.sameOrigin(apiBase)) return ApiResult.Failure(ApiError.ForeignCursor(following.host))
        val get = ApiRequest(like.endpoint.copy(method = HttpMethod.GET, body = Body.None), like.decoder)
        return perform(get, following).map { it.toPage(limit) }
    }

    private fun <T> Answer<List<T>>.toPage(limit: Int) =
        Paginated(decoded.value, link, rawCount = decoded.rawCount ?: decoded.value.size, requestedLimit = limit)

    private suspend fun <T> perform(request: ApiRequest<T>, url: HttpUrl): ApiResult<Answer<T>> {
        val authorization = authorization(request.endpoint.authentication)
            ?: return ApiResult.Failure(ApiError.Unauthorised(message = null))
        val result = executor.execute(request, url, authorization)
        val revoked = (result as? ApiResult.Failure)?.error is ApiError.Unauthorised && authorization.isNotEmpty()
        if (revoked) onUnauthorised()
        return result
    }

    /** The `Authorization` value, `""` for a public route, or null when the route's credential is missing. */
    private suspend fun authorization(authentication: Authentication): String? = when (authentication) {
        Authentication.None, Authentication.NextcloudPublic -> ""
        Authentication.Bearer -> credentials.current().bearerToken?.let { "Bearer $it" }
        Authentication.NextcloudSession -> credentials.current().nextcloudBasic
    }
}
