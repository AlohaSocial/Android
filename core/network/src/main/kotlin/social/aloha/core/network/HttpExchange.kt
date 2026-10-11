// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import social.aloha.core.network.di.IoDispatcher

/**
 * Requests in a protocol the API client does not speak, WebDAV above all. The request goes out as the
 * caller built it, through the same client, certificates and rate limiter as every API call, and the
 * answer comes back whatever its status, for the caller to read and close: to WebDAV a 207 or a 412 is
 * an answer, not a failure. Only not reaching the server is an [ApiError] here; [error] maps an answer
 * the caller has no use for, the way the API client would have.
 */
@Singleton
public class HttpExchange @Inject constructor(
    private val http: OkHttpClient,
    private val rateLimiter: RateLimiter,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * [label] names the request in the log in place of its address, which in WebDAV is made of the
     * person's own file names. An [untimed] request has no call timeout, for a body that takes as long as
     * the line needs; the connect and read timeouts still catch a line that stopped moving.
     */
    public suspend fun exchange(request: Request, label: String, untimed: Boolean = false): ApiResult<Response> {
        rateLimiter.acquire(request.url.host)
        val started = System.nanoTime()
        val sent = http.send(request, untimed)
        sent.log(label, (System.nanoTime() - started) / NANOS_PER_MILLI)
        return when (sent) {
            is HttpOutcome.Answered -> ApiResult.Success(sent.response)
            is HttpOutcome.Failed -> ApiResult.Failure(sent.error)
        }
    }

    /** [exchange], with the answer handed to [read] and closed after it. */
    public suspend fun <T> exchange(
        request: Request,
        label: String,
        untimed: Boolean = false,
        read: suspend (Response) -> ApiResult<T>,
    ): ApiResult<T> = when (val answer = exchange(request, label, untimed)) {
        is ApiResult.Failure -> answer
        is ApiResult.Success -> answer.value.use { read(it) }
    }

    /**
     * What [response] says went wrong, read off the caller's thread; it is closed. A 429 holds back further
     * requests to the host for its `Retry-After`, as it does for an API call.
     */
    public suspend fun error(response: Response): ApiError {
        val body = withContext(io) { response.use { it.body.string() } }
        val error = ApiError.from(response.code, errorMessage(body), body, response::header)
            ?: ApiError.Server(response.code, body)
        if (error is ApiError.RateLimited) rateLimiter.noteRateLimited(response.request.url.host, error.retryAfter)
        return error
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000
    }
}
