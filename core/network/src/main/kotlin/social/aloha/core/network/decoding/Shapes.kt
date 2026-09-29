// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.decoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import social.aloha.core.model.VideoChapters

/** Optional text that also accepts a number; any other shape, and `""`, is absent. */
internal object LenientTextSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientText", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? =
        (decoder.jsonElement() as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotEmpty() }

    override fun serialize(encoder: Encoder, value: String?): Unit = decodeOnly()
}

/**
 * PeerTube's `{id, label}` for a video's category, language and licence, which Nextcloud Social may pass
 * on as that object or as the bare label. Both read as the label.
 */
internal object LabelledTextSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LabelledText", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val element = decoder.jsonElement()
        val text = when (element) {
            is JsonObject -> (element["label"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            is JsonPrimitive -> element.takeUnless { it is JsonNull }?.content
            else -> null
        }
        return text?.takeIf { it.isNotEmpty() }
    }

    override fun serialize(encoder: Encoder, value: String?): Unit = decodeOnly()
}

/** A chapter start in seconds, sent as a number or as a clock (`1:02`); anything else is absent. */
internal object ChapterStartSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ChapterStart", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? {
        val primitive = (decoder.jsonElement() as? JsonPrimitive)?.takeUnless { it is JsonNull } ?: return null
        return if (primitive.isString) VideoChapters.seconds(primitive.content) else primitive.doubleOrNull
    }

    override fun serialize(encoder: Encoder, value: Double?): Unit = decodeOnly()
}

/**
 * An object that a server sends as `[]` when it has nothing to say: Nextcloud Social without ffmpeg
 * sends `meta.original: []` for every video. `null` and an empty array are quietly absent; any other
 * wrong shape is absent and recorded.
 */
internal class EmptyArrayAsNullSerializer<T : Any>(private val inner: KSerializer<T>) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = inner.descriptor.nullable

    override fun deserialize(decoder: Decoder): T? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val element = json.decodeJsonElement()
        if (element is JsonNull || (element is JsonArray && element.isEmpty())) return null
        return json.json.decodeOrRecord(inner, element, index = 0)
    }

    override fun serialize(encoder: Encoder, value: T?): Unit = decodeOnly()
}
