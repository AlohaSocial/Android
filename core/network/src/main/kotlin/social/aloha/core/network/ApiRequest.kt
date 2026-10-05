// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import social.aloha.core.network.decoding.DecodingFailures
import social.aloha.core.network.decoding.decodeOrRecord

/**
 * One element a lossy array dropped, kept so the loss is logged rather than silent. [error] is the
 * exception's class, what the log keeps; [reason] is its message, which may quote the JSON.
 */
public data class DecodingFailure(val index: Int, val typeName: String, val reason: String, val error: String)

/**
 * An [Endpoint] together with how its response becomes a domain value. The wire DTO and its mapping
 * stay inside this module: callers only ever see the domain type [T].
 */
public class ApiRequest<T> internal constructor(
    public val endpoint: Endpoint,
    internal val decoder: ResponseDecoder<T>,
)

/** What decoding one body produced. [rawCount] is the number of rows the server sent, before lossy decoding. */
internal data class Decoded<T>(val value: T, val rawCount: Int?, val failures: List<DecodingFailure>)

internal fun interface ResponseDecoder<T> {
    fun decode(json: Json, body: String): Decoded<T>
}

/** The one JSON configuration for every server payload. */
internal val AlohaJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** A single object mapped to its domain type. */
internal fun <D, T> request(endpoint: Endpoint, serializer: KSerializer<D>, map: (D) -> T): ApiRequest<T> =
    ApiRequest(endpoint) { json, body ->
        val (value, failures) = DecodingFailures.collect { map(json.decodeFromString(serializer, body)) }
        Decoded(value, rawCount = null, failures = failures)
    }

/**
 * A top-level array, decoded lossily: an element that fails is dropped and recorded, and
 * [Decoded.rawCount] still counts it, so "a page shorter than the limit is the last one" reflects
 * what the server sent. A member [map] answers null for is left out too.
 */
internal fun <D, T : Any> listRequest(
    endpoint: Endpoint,
    serializer: KSerializer<D>,
    map: (D) -> T?,
): ApiRequest<List<T>> = ApiRequest(endpoint) { json, body ->
    val array = json.parseToJsonElement(body) as? JsonArray ?: JsonArray(emptyList())
    val (value, failures) = DecodingFailures.collect {
        array.mapIndexedNotNull { index, element -> json.decodeOrRecord(serializer, element, index)?.let(map) }
    }
    Decoded(value, rawCount = array.size, failures = failures)
}

/**
 * An object whose page is one of its array members, e.g. grouped notifications, where
 * `notification_groups` is what the limit counts.
 */
internal fun <D, T> pagedObjectRequest(
    endpoint: Endpoint,
    serializer: KSerializer<D>,
    pageMember: String,
    map: (D) -> T,
): ApiRequest<T> = ApiRequest(endpoint) { json, body ->
    val element = json.parseToJsonElement(body)
    val rawCount = (element as? JsonObject)?.get(pageMember)?.let { runCatching { it.jsonArray.size }.getOrNull() }
    val (value, failures) = DecodingFailures.collect { map(json.decodeFromJsonElement(serializer, element)) }
    Decoded(value, rawCount = rawCount, failures = failures)
}

/** A route whose body carries nothing the app reads (`{}` or empty). */
internal fun unitRequest(endpoint: Endpoint): ApiRequest<Unit> =
    ApiRequest(endpoint) { _, _ -> Decoded(Unit, rawCount = null, failures = emptyList()) }
