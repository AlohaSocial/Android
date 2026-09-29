// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import social.aloha.core.network.tls.UntrustedServerCertificateException

/** Sends [request], suspending until the answer arrives; cancelling the coroutine cancels the call. */
internal suspend fun OkHttpClient.send(request: Request): HttpOutcome = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(HttpOutcome.Answered(response)) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(HttpOutcome.Failed(e.toApiError()))
            }
        },
    )
}

/**
 * A TLS failure caused by a certificate nobody trusts becomes [ApiError.UntrustedCertificate]. OkHttp
 * reports the last route it tried and keeps the others as suppressed exceptions, so both the cause
 * chain and the suppressed ones are searched.
 */
internal fun IOException.toApiError(): ApiError =
    findUntrusted(this, depth = 0)?.let { ApiError.UntrustedCertificate(it.host, it.chain) } ?: ApiError.Transport(this)

private fun findUntrusted(throwable: Throwable?, depth: Int): UntrustedServerCertificateException? {
    if (throwable == null || depth > MAX_CAUSE_DEPTH) return null
    if (throwable is UntrustedServerCertificateException) return throwable
    return findUntrusted(throwable.cause, depth + 1)
        ?: throwable.suppressed.firstNotNullOfOrNull { findUntrusted(it, depth + 1) }
}

private const val MAX_CAUSE_DEPTH = 8

internal fun Endpoint.resolve(base: HttpUrl): HttpUrl {
    val builder = base.newBuilder()
    if (path.isNotEmpty()) builder.addPathSegments(path)
    query.forEach { builder.addQueryParameter(it.name, it.value) }
    return builder.build()
}

internal fun HttpUrl.sameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private val OCS_ROUTES = setOf(Authentication.NextcloudSession, Authentication.NextcloudPublic)

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

internal fun Request.Builder.applyEndpoint(endpoint: Endpoint, authorization: String): Request.Builder {
    header("Accept", "application/json")
    if (authorization.isNotEmpty()) header("Authorization", authorization)
    if (endpoint.authentication in OCS_ROUTES) header("OCS-APIRequest", "true")
    endpoint.idempotencyKey?.let { header("Idempotency-Key", it) }
    return method(endpoint.method, endpoint.body)
}

private fun Request.Builder.method(method: HttpMethod, body: Body): Request.Builder = when (method) {
    HttpMethod.GET -> get()
    HttpMethod.DELETE -> if (body == Body.None) delete() else delete(body.toRequestBody())
    HttpMethod.POST -> post(body.toRequestBody())
    HttpMethod.PUT -> put(body.toRequestBody())
    HttpMethod.PATCH -> patch(body.toRequestBody())
}

private fun Body.toRequestBody(): RequestBody = when (this) {
    Body.None -> ByteArray(0).toRequestBody()
    is Body.Json -> text.toRequestBody(JSON_MEDIA)
    is Body.Form -> formBody(fields)
    is Body.Multipart -> MultipartBody.Builder().setType(MultipartBody.FORM).apply { parts.forEach(::addPart) }.build()
}

private fun formBody(fields: List<QueryItem>): RequestBody {
    val builder = FormBody.Builder()
    fields.forEach { builder.add(it.name, it.value) }
    return builder.build()
}

private fun MultipartBody.Builder.addPart(part: Part) {
    when (part) {
        is Part.Field -> addFormDataPart(part.name, part.value)

        is Part.FileContent ->
            addFormDataPart(part.name, part.fileName, part.file.asRequestBody(part.mimeType.toMediaType()))
    }
}

@Serializable
private data class ErrorPayload(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

/** Mastodon-shaped errors are `{"error": …, "error_description": …}`; anything else has no message. */
internal fun errorMessage(body: String): String? = try {
    AlohaJson.decodeFromString(ErrorPayload.serializer(), body).let { it.errorDescription ?: it.error }
} catch (_: kotlinx.serialization.SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
