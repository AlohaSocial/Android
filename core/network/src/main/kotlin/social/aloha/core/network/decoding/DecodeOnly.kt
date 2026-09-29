// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.decoding

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * The shared pieces of the lenient serializers: they read the raw JSON element and decide, and they
 * never encode, because wire DTOs only travel from the server to the app.
 */

internal fun Decoder.jsonDecoder(): JsonDecoder = this as? JsonDecoder ?: throw SerializationException("JSON only")

internal fun Decoder.jsonElement(): JsonElement = jsonDecoder().decodeJsonElement()

internal fun JsonElement.primitiveOrNull(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun decodeOnly(): Nothing = throw SerializationException("wire DTOs are decode-only")
