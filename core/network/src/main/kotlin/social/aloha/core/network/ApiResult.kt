// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.io.IOException
import java.security.cert.X509Certificate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/**
 * A refusal's stable [code] beside its English sentence, as Nextcloud Social names it from 0.26.276 on
 * (`shorts-full`, `featured-tags-full`, …), with the [fields] its advice needs, such as `limit`, as text.
 * A post a review rule holds, Social's 422 with `held_for_review: true` on `POST /statuses` from 0.20.x
 * on and on scheduled posts from 0.26.294 on, is the code [HELD_FOR_REVIEW], the rule's own code
 * (`first_post`, `links`, …) under `reason`.
 */
public data class Refusal(val code: String, val fields: Map<String, String> = emptyMap()) {
    public fun number(field: String): Int? = fields[field]?.toIntOrNull()

    public companion object {
        /** The post waits for a moderator: nothing was published, and the server keeps the request. */
        public const val HELD_FOR_REVIEW: String = "held-for-review"

        private val MESSAGE_FIELDS = setOf("error", "error_description", "code")

        /** Social's `{"error": …, "code": …, "limit": …}`: the code and the plain fields beside it; `null` without. */
        internal fun of(body: String): Refusal? = try {
            val payload = AlohaJson.parseToJsonElement(body) as? JsonObject ?: return null
            val code = (payload["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: HELD_FOR_REVIEW.takeIf { (payload["held_for_review"] as? JsonPrimitive)?.content == "true" }
            code?.takeIf { it.isNotBlank() }?.let {
                val fields = payload.filterKeys { key -> key !in MESSAGE_FIELDS }.mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { key to it.content }
                }
                Refusal(it, fields.toMap())
            }
        } catch (_: SerializationException) {
            null
        }
    }
}

/** Why a request failed. The one place an HTTP response becomes a typed error is [ApiError.from]. */
public sealed interface ApiError {
    /**
     * 401: the token was revoked, expired or never presented (Nextcloud Social answers
     * `{"error":"the access_token was revoked"}` for each). It is the answer only once no refresh
     * token renewed the token: the account needs a new sign-in, its cached data stays.
     */
    public data class Unauthorised(val message: String?) : ApiError

    /** 403; [insufficientScope] when the server said so in `WWW-Authenticate`. */
    public data class Forbidden(
        val message: String?,
        val insufficientScope: Boolean,
        override val refusal: Refusal? = null,
    ) : ApiError

    /** 404 or 410. */
    public data object NotFound : ApiError

    /** 422: validation; [message] is written for a person and is shown verbatim when present. */
    public data class Unprocessable(val message: String?, override val refusal: Refusal? = null) : ApiError

    /** 429; [retryAfter] from `Retry-After` when the server sent one. */
    public data class RateLimited(val retryAfter: Duration?) : ApiError

    /** Any other non-2xx answer, including a non-JSON body from a PHP upload limit. */
    public data class Server(val status: Int, val body: String?, override val refusal: Refusal? = null) : ApiError

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

    /** The refusal's code and fields where the server named them; `null` from a server that does not. */
    public val refusal: Refusal? get() = null

    /** A review rule holds the post for a moderator: it counts as sent, and sending it again finds the one held. */
    public val heldForReview: Boolean get() = this is Unprocessable && refusal?.code == Refusal.HELD_FOR_REVIEW

    /** The review rule that holds the post, as the server names it (`first_post`, `links`, …), when one does. */
    public val heldReason: String? get() = refusal?.fields?.get("reason")?.takeIf { heldForReview }

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
            val refusal = rawBody?.let(Refusal::of)
            return when (status) {
                UNAUTHORISED -> Unauthorised(message)
                FORBIDDEN -> forbidden(message, headers("WWW-Authenticate"), refusal)
                NOT_FOUND, GONE -> NotFound
                UNPROCESSABLE -> Unprocessable(message ?: rawBody?.takeIf { it.isNotBlank() }, refusal)
                TOO_MANY_REQUESTS -> RateLimited(headers("Retry-After")?.let(::parseRetryAfter))
                else -> Server(status, message ?: rawBody, refusal)
            }
        }

        private fun forbidden(message: String?, authenticate: String?, refusal: Refusal?) =
            Forbidden(message, insufficientScope = authenticate?.contains("insufficient_scope") == true, refusal)

        private fun parseRetryAfter(raw: String): Duration? = raw.trim().toLongOrNull()?.seconds
    }
}
