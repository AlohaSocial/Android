// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.decoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import social.aloha.core.network.DecodingFailure

/**
 * Collects what lossy decoding dropped while one response body is decoded. Decoding is synchronous
 * and runs on one thread, so a thread-local collector sees exactly the failures of that body.
 */
internal object DecodingFailures {
    private val current = ThreadLocal<MutableList<DecodingFailure>?>()

    fun <T> collect(block: () -> T): Pair<T, List<DecodingFailure>> {
        val previous = current.get()
        val failures = mutableListOf<DecodingFailure>()
        current.set(failures)
        try {
            return block() to failures.toList()
        } finally {
            current.set(previous)
        }
    }

    fun record(failure: DecodingFailure) {
        current.get()?.add(failure)
    }
}

/**
 * Decodes one element of a lossy array, or records why it could not and returns null. A malformed
 * entity never fails its page: a timeline page with one bad status shows the other nineteen.
 */
internal fun <T> Json.decodeOrRecord(serializer: KSerializer<T>, element: JsonElement, index: Int): T? = try {
    decodeFromJsonElement(serializer, element)
} catch (e: SerializationException) {
    DecodingFailures.record(e.asFailure(index, serializer))
    null
} catch (e: IllegalArgumentException) {
    DecodingFailures.record(e.asFailure(index, serializer))
    null
}

private fun Exception.asFailure(index: Int, serializer: KSerializer<*>) =
    DecodingFailure(index, serializer.descriptor.serialName, message.orEmpty(), javaClass.simpleName)

/** A list that decodes element by element and drops, and records, the elements that fail. */
internal class LossyListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(element).descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val array = json.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapIndexedNotNull { index, item -> json.json.decodeOrRecord(element, item, index) }
    }

    override fun serialize(encoder: Encoder, value: List<T>): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

/**
 * A nested object that is absent when it has the wrong shape, e.g. a fork that sends `[]` for an
 * empty `meta` object. Declare one per DTO: `object CardOrNull : KSerializer<CardDto?> by
 * OrNullSerializer(CardDto.serializer())`.
 */
internal class OrNullSerializer<T : Any>(private val inner: KSerializer<T>) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = inner.descriptor.nullable

    override fun deserialize(decoder: Decoder): T? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val element = json.decodeJsonElement()
        if (element is JsonNull) return null
        return json.json.decodeOrRecord(inner, element, index = 0)
    }

    override fun serialize(encoder: Encoder, value: T?): Unit =
        throw SerializationException("wire DTOs are decode-only")
}
