// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal sealed interface HttpOutcome {
    class Answered(val response: Response) : HttpOutcome

    class Failed(val error: ApiError) : HttpOutcome
}

/** A decoded answer and the cursor that came with it. */
internal class Answer<T>(val decoded: Decoded<T>, val link: LinkHeader)

/**
 * Sends one request and turns the answer into an [ApiResult]: the rate limiter first, then the
 * status mapped to an [ApiError], then the body decoded off the caller's thread. Shared by the
 * per-account client, OAuth, the probe and capability detection so all of them fail the same way.
 */
internal class RequestExecutor(
    private val http: OkHttpClient,
    private val rateLimiter: RateLimiter,
    private val ioDispatcher: CoroutineDispatcher,
    private val failureListener: DecodingFailureListener = DecodingFailureListener { _, _ -> },
) {
    /** A copy that gives up after [seconds], for probes that must not wait out the default timeouts. */
    fun withCallTimeout(seconds: Long): RequestExecutor = RequestExecutor(
        http.newBuilder().callTimeout(seconds, java.util.concurrent.TimeUnit.SECONDS).build(),
        rateLimiter,
        ioDispatcher,
        failureListener,
    )

    /**
     * [authorization] is the `Authorization` header value, empty for none. A read answered 429 with a
     * short `Retry-After` is retried once, after the rate limiter has waited it out; a write never is.
     */
    suspend fun <T> execute(request: ApiRequest<T>, url: HttpUrl, authorization: String): ApiResult<Answer<T>> {
        if (request.endpoint.hasDotSegment()) return ApiResult.Failure(ApiError.UnsafePath(request.endpoint.path))
        val first = send(request, url, authorization)
        val limited = (first as? ApiResult.Failure)?.error as? ApiError.RateLimited
        val retry = limited != null && request.endpoint.method == HttpMethod.GET &&
            (limited.retryAfter ?: MAX_RETRY_WAIT) <= MAX_RETRY_WAIT
        return if (retry) send(request, url, authorization) else first
    }

    private suspend fun <T> send(request: ApiRequest<T>, url: HttpUrl, authorization: String): ApiResult<Answer<T>> {
        rateLimiter.acquire(url.host)
        val call = Request.Builder().url(url).applyEndpoint(request.endpoint, authorization).build()
        return when (val sent = http.send(call)) {
            is HttpOutcome.Failed -> ApiResult.Failure(sent.error)
            is HttpOutcome.Answered -> sent.response.use { response -> answer(request, response, url) }
        }
    }

    private suspend fun <T> answer(request: ApiRequest<T>, response: Response, url: HttpUrl): ApiResult<Answer<T>> {
        val body = withContext(ioDispatcher) { response.body.string() }
        if (response.isSuccessful) return decode(request, body, LinkHeader.parse(response.header("Link")))
        val error =
            ApiError.from(response.code, errorMessage(body), body, response::header)
                ?: ApiError.Server(response.code, body)
        if (error is ApiError.RateLimited) rateLimiter.noteRateLimited(url.host, error.retryAfter)
        return ApiResult.Failure(error)
    }

    private suspend fun <T> decode(request: ApiRequest<T>, body: String, link: LinkHeader): ApiResult<Answer<T>> = try {
        val decoded = withContext(ioDispatcher) { request.decoder.decode(AlohaJson, body.ifBlank { "{}" }) }
        if (decoded.failures.isNotEmpty()) failureListener.onDropped(request.endpoint.path, decoded.failures)
        ApiResult.Success(Answer(decoded, link))
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.Decoding(request.endpoint.path, e.message.orEmpty()))
    } catch (e: IllegalArgumentException) {
        ApiResult.Failure(ApiError.Decoding(request.endpoint.path, e.message.orEmpty()))
    }

    private companion object {
        val MAX_RETRY_WAIT = 5.seconds
        val DOT_SEGMENTS = setOf(".", "..", "%2e", "%2e.", ".%2e", "%2e%2e")

        /**
         * A path segment URL resolution would treat as `.` or `..`, in plain or percent-encoded form.
         * Ids and names in paths come from servers; one such segment would move a request carrying the
         * account's token to another route, so it is refused before anything is sent.
         */
        fun Endpoint.hasDotSegment(): Boolean = path.split('/').any { it.lowercase() in DOT_SEGMENTS }
    }
}
