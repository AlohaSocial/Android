// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.io.IOException
import java.security.cert.X509Certificate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The outcome of one request. Cancellation is not a result: it propagates as a
 * `CancellationException`, so structured concurrency keeps working.
 */
public sealed interface ApiResult<out T> {
    public data class Success<T>(val value: T) : ApiResult<T>

    public data class Failure(val error: ApiError) : ApiResult<Nothing>
}

public inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

public fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

public fun <T> ApiResult<T>.errorOrNull(): ApiError? = (this as? ApiResult.Failure)?.error

/** Why a request failed. The one place an HTTP response becomes a typed error is [ApiError.from]. */
public sealed interface ApiError {
    /**
     * 401: the token was revoked or never presented (Nextcloud Social answers
     * `{"error":"the access_token was revoked"}`). There is no refresh token: the account needs a
     * new sign-in, its cached data stays.
     */
    public data class Unauthorised(val message: String?) : ApiError

    /** 403; [insufficientScope] when the server said so in `WWW-Authenticate`. */
    public data class Forbidden(val message: String?, val insufficientScope: Boolean) : ApiError

    /** 404 or 410. */
    public data object NotFound : ApiError

    /** 422: validation; [message] is written for a person and is shown verbatim when present. */
    public data class Unprocessable(val message: String?) : ApiError

    /** 429; [retryAfter] from `Retry-After` when the server sent one. */
    public data class RateLimited(val retryAfter: Duration?) : ApiError

    /** Any other non-2xx answer, including a non-JSON body from a PHP upload limit. */
    public data class Server(val status: Int, val body: String?) : ApiError

    /** The body did not have the shape of the route's entity. */
    public data class Decoding(val context: String, val reason: String) : ApiError

    /** The server presented a certificate that neither the system nor the person trusts. */
    public data class UntrustedCertificate(val host: String, val chain: List<X509Certificate>) : ApiError

    /** A `Link` cursor pointed at another origin than the API base; it was not followed. */
    public data class ForeignCursor(val host: String) : ApiError

    /** A path segment from server data would have resolved as `.` or `..`; nothing was sent. */
    public data class UnsafePath(val path: String) : ApiError

    /** No answer: DNS, connection, TLS or timeout. */
    public data class Transport(val cause: IOException) : ApiError

    /** Whether retrying the identical request could plausibly succeed. Writes are never retried automatically. */
    public val isTransient: Boolean
        get() = when (this) {
            is RateLimited, is Transport -> true
            is Server -> status >= SERVER_ERROR
            else -> false
        }

    /** The account needs a new sign-in: a state, not a failure. */
    public val requiresReauthentication: Boolean get() = this is Unauthorised

    public companion object {
        private const val UNAUTHORISED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404
        private const val GONE = 410
        private const val UNPROCESSABLE = 422
        private const val TOO_MANY_REQUESTS = 429
        private const val SERVER_ERROR = 500
        private val SUCCESS = 200..299

        internal fun from(status: Int, message: String?, rawBody: String?, headers: (String) -> String?): ApiError? {
            if (status in SUCCESS) return null
            return when (status) {
                UNAUTHORISED -> Unauthorised(message)
                FORBIDDEN -> forbidden(message, headers("WWW-Authenticate"))
                NOT_FOUND, GONE -> NotFound
                UNPROCESSABLE -> Unprocessable(message ?: rawBody?.takeIf { it.isNotBlank() })
                TOO_MANY_REQUESTS -> RateLimited(headers("Retry-After")?.let(::parseRetryAfter))
                else -> Server(status, message ?: rawBody)
            }
        }

        private fun forbidden(message: String?, authenticate: String?) =
            Forbidden(message, insufficientScope = authenticate?.contains("insufficient_scope") == true)

        private fun parseRetryAfter(raw: String): Duration? = raw.trim().toLongOrNull()?.seconds
    }
}
