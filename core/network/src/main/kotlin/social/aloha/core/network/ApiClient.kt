// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.io.OutputStream
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

/** What became of renewing a token the server refused. */
public sealed interface TokenRenewal {
    /** A new token is stored; the refused request is sent once more with it. */
    public data object Renewed : TokenRenewal

    /** Nothing renews it: the account needs a new sign-in. */
    public data object Refused : TokenRenewal

    /** The renewal could not be asked ([error]); the token kept is tried again next time. */
    public data class Failed(val error: ApiError) : TokenRenewal
}

/** Told what lossy decoding dropped, so the loss is logged rather than silent. */
public fun interface DecodingFailureListener {
    public fun onDropped(path: String, failures: List<DecodingFailure>)
}

/**
 * One account's view of its server: the resolved API base, its credentials and the shared HTTP
 * client and rate limiter. Returns [ApiResult]s. It sends a request a second time only after its token
 * was refused and renewed: the server refused it before acting on it, so a write is not repeated.
 */
public class ApiClient internal constructor(
    public val apiBase: HttpUrl,
    private val credentials: CredentialSource,
    private val executor: RequestExecutor,
    private val onUnauthorised: suspend () -> Unit,
    private val renew: suspend (rejectedToken: String) -> TokenRenewal = { TokenRenewal.Refused },
) {
    /**
     * @param onUnauthorised told when the server answers 401 to a request that carried this account's
     *   token and [renew] did not renew it: the account needs a new sign-in. A 401 to the Nextcloud app
     *   password says nothing about the sign-in and is not told.
     * @param renew asked to renew the token a 401 refused, which Aloha Social answers alike for an
     *   expired and a revoked one (`the access_token was revoked`); by default nothing renews it.
     */
    public constructor(
        apiBase: HttpUrl,
        credentials: CredentialSource,
        http: OkHttpClient,
        rateLimiter: RateLimiter,
        ioDispatcher: CoroutineDispatcher,
        failureListener: DecodingFailureListener = LogDropped,
        onUnauthorised: suspend () -> Unit = {},
        renew: suspend (rejectedToken: String) -> TokenRenewal = { TokenRenewal.Refused },
    ) : this(
        apiBase,
        credentials,
        RequestExecutor(http, rateLimiter, ioDispatcher, failureListener),
        onUnauthorised,
        renew,
    )

    public suspend fun <T> execute(request: ApiRequest<T>): ApiResult<T> =
        perform(request, request.endpoint.resolve(apiBase)).map { it.decoded.value }

    /** [request]'s answer and whether the server said it is still reading for it (see [Filled]). */
    public suspend fun <T> filled(request: ApiRequest<T>): ApiResult<Filled<T>> =
        perform(request, request.endpoint.resolve(apiBase)).map {
            Filled(it.decoded.value, it.filling, it.threadFilling)
        }

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

    /** [endpoint]'s answer written [into], for a download such as an export; nothing is decoded. */
    public suspend fun download(endpoint: Endpoint, into: OutputStream): ApiResult<Unit> =
        authorized(endpoint.authentication) { executor.download(endpoint, endpoint.resolve(apiBase), it, into) }

    private fun <T> Answer<List<T>>.toPage(limit: Int) = Paginated(
        decoded.value,
        link,
        rawCount = decoded.rawCount ?: decoded.value.size,
        requestedLimit = limit,
        filling = filling,
        profileFilling = profileFilling,
        profileSyncFailed = profileSyncFailed,
    )

    private suspend fun <T> perform(request: ApiRequest<T>, url: HttpUrl): ApiResult<Answer<T>> =
        authorized(request.endpoint.authentication) { executor.execute(request, url, it) }

    /** What [send] answers with the `Authorization` value for [authentication], sent once more after a renewal. */
    private suspend fun <R> authorized(
        authentication: Authentication,
        send: suspend (authorization: String) -> ApiResult<R>,
    ): ApiResult<R> {
        val authorization = authorization(authentication)
            ?: return ApiResult.Failure(ApiError.Unauthorised(message = null))
        val result = send(authorization)
        val refused = refusesToken(authentication, authorization, result)
        return if (refused) renewed(authentication, authorization, result, send) else result
    }

    /** What [send] answers once more after its token, sent as [refused], was renewed; else its [result]. */
    private suspend fun <R> renewed(
        authentication: Authentication,
        refused: String,
        result: ApiResult<R>,
        send: suspend (authorization: String) -> ApiResult<R>,
    ): ApiResult<R> = when (val renewal = renew(refused.removePrefix(BEARER))) {
        TokenRenewal.Renewed -> authorization(authentication)?.let { authorization ->
            send(authorization).also {
                if (refusesToken(authentication, authorization, it)) onUnauthorised()
            }
        } ?: result.also { onUnauthorised() }

        TokenRenewal.Refused -> result.also { onUnauthorised() }

        is TokenRenewal.Failed -> ApiResult.Failure(renewal.error)
    }

    // only the sign-in's own token answers for the sign-in: a refused app password is the Nextcloud's
    private fun refusesToken(authentication: Authentication, authorization: String, result: ApiResult<*>): Boolean =
        (result as? ApiResult.Failure)?.error is ApiError.Unauthorised &&
            authentication == Authentication.Bearer && authorization.isNotEmpty()

    /** The `Authorization` value, `""` for a public route, or null when the route's credential is missing. */
    private suspend fun authorization(authentication: Authentication): String? = when (authentication) {
        Authentication.None, Authentication.NextcloudPublic -> ""

        Authentication.Bearer -> credentials.current().bearerToken?.let { "$BEARER$it" }

        Authentication.NextcloudSession -> credentials.current().nextcloudBasic

        Authentication.SessionOrBearer ->
            authorization(Authentication.NextcloudSession) ?: authorization(Authentication.Bearer)
    }

    private companion object {
        const val BEARER = "Bearer "
    }
}
