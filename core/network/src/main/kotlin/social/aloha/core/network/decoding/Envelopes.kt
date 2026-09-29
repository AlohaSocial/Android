// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.decoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Every element of [array] that decodes; the ones that fail are recorded, as a lossy list does. */
internal fun <T> Json.lossy(element: KSerializer<T>, array: JsonElement?): List<T> =
    (array as? JsonArray)?.mapIndexedNotNull { index, item -> decodeOrRecord(element, item, index) }.orEmpty()

/**
 * A list sent either bare or wrapped in an object under [member] (`{"reactions": […]}`,
 * `{"channels": […]}`). Nextcloud Social's own routes wrap; Pixelfed's and some forks' do not.
 * Anything else is empty.
 */
internal class ListOrMemberSerializer<T>(private val element: KSerializer<T>, private val member: String) :
    KSerializer<List<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(element).descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val json = decoder.jsonDecoder()
        return when (val root = json.decodeJsonElement()) {
            is JsonArray -> json.json.lossy(element, root)
            is JsonObject -> json.json.lossy(element, root[member])
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>): Unit = decodeOnly()
}

/** A list of strings sent as an array or as one space-separated string, as OAuth scopes are. */
internal object StringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun deserialize(decoder: Decoder): List<String> =
        when (val element = decoder.jsonDecoder().decodeJsonElement()) {
            is JsonArray -> element.mapNotNull { it.primitiveOrNull()?.content }
            is JsonPrimitive -> element.primitiveOrNull()?.content?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
            else -> emptyList()
        }

    override fun serialize(encoder: Encoder, value: List<String>): Unit = decodeOnly()
}

/**
 * `{"2026-01": 12, "2026-02": "7", "public": true}`: a map of numbers whose values may arrive as
 * strings or booleans. A value that is none of them is left out.
 */
internal object NumberMapSerializer : KSerializer<Map<String, Double>> {
    override val descriptor: SerialDescriptor = MapSerializer(String.serializer(), Double.serializer()).descriptor

    override fun deserialize(decoder: Decoder): Map<String, Double> {
        val root = decoder.jsonDecoder().decodeJsonElement() as? JsonObject ?: return emptyMap()
        return root.mapNotNull { (key, value) -> value.asNumber()?.let { key to it } }.toMap()
    }

    private fun JsonElement.asNumber(): Double? {
        val primitive = (this as? JsonPrimitive)?.takeUnless { it is JsonNull } ?: return null
        if (primitive.isString) return primitive.content.toDoubleOrNull()
        return primitive.doubleOrNull ?: primitive.booleanOrNull?.let { if (it) 1.0 else 0.0 }
    }

    override fun serialize(encoder: Encoder, value: Map<String, Double>): Unit = decodeOnly()
}
