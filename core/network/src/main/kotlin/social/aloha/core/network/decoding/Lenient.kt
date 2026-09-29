// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.decoding

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Lenient decoders for the shapes the fediverse actually sends. Every wire DTO field that a real
 * server has been seen to send in more than one shape uses one of these, so one odd field never
 * fails an entity. They decode only; DTOs are never encoded.
 */

/**
 * A server id, always a string: Nextcloud Social sends numeric `nid`s where Mastodon sends snowflake
 * strings, and ids beyond 2^53 must never pass through a number type.
 */
internal object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val primitive = decoder.jsonElement().primitiveOrNull() ?: throw SerializationException("id was null")
        return if (primitive.isString) primitive.content else primitive.longOrNull?.toString() ?: primitive.content
    }

    override fun serialize(encoder: Encoder, value: String): Unit = decodeOnly()
}

/** An optional id: a number becomes its decimal string, `""` and `null` become absent. */
internal object OptionalIdSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OptionalId", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val primitive = decoder.jsonElement().primitiveOrNull() ?: return null
        val text = if (primitive.isString) primitive.content else primitive.longOrNull?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    override fun serialize(encoder: Encoder, value: String?): Unit = decodeOnly()
}

/**
 * A URL that may be `null`, absent, `""` or whitespace, all meaning "none": Nextcloud Social sends
 * `"avatar": ""` for a local account and `null` from `verify_credentials`.
 */
internal object LenientUrlSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientUrl", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val primitive = decoder.jsonElement().primitiveOrNull()?.takeIf { it.isString } ?: return null
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    override fun serialize(encoder: Encoder, value: String?): Unit = decodeOnly()
}

/** A boolean that also accepts `0`/`1` and `"true"`/`"false"`/`"yes"`; anything else is false. */
internal object LenientBoolSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBool", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean =
        decoder.jsonElement().primitiveOrNull()?.toLenientBoolean() ?: false

    override fun serialize(encoder: Encoder, value: Boolean): Unit = decodeOnly()
}

/** An optional boolean with the same leniency; `null` and absent stay absent. */
internal object OptionalBoolSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OptionalBool", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? = decoder.jsonElement().primitiveOrNull()?.toLenientBoolean()

    override fun serialize(encoder: Encoder, value: Boolean?): Unit = decodeOnly()
}

/** An integer that also accepts a numeric string or a float; anything else is 0. */
internal object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int = decoder.jsonElement().primitiveOrNull()?.toIntOrNull() ?: 0

    override fun serialize(encoder: Encoder, value: Int): Unit = decodeOnly()
}

/** An optional integer with the same leniency. */
internal object OptionalIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OptionalInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? = decoder.jsonElement().primitiveOrNull()?.toIntOrNull()

    override fun serialize(encoder: Encoder, value: Int?): Unit = decodeOnly()
}

/** An optional double that also accepts a numeric string. */
internal object OptionalDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("OptionalDouble", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? {
        val primitive = decoder.jsonElement().primitiveOrNull() ?: return null
        return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
    }

    override fun serialize(encoder: Encoder, value: Double?): Unit = decodeOnly()
}

private val truthyText = setOf("true", "1", "yes")

/** A boolean from `true`/`false`, a number (non-zero is true) or text; null for a number-like value that is neither. */
private fun JsonPrimitive.toLenientBoolean(): Boolean? =
    if (isString) content.lowercase() in truthyText else booleanOrNull ?: longOrNull?.let { it != 0L }

private fun JsonPrimitive.toIntOrNull(): Int? =
    longOrNull?.toInt() ?: doubleOrNull?.toInt() ?: content.toLongOrNull()?.toInt()

/**
 * A timestamp in any form a server sends: ISO 8601 with or without fractional seconds or offset, a
 * bare `yyyy-MM-dd` (filter expiry, announcement dates), or Unix seconds as a number or a string
 * (`/api/v1/instance/activity`). Unreadable values are absent rather than failing the entity.
 */
internal object LenientInstantSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "LenientInstant",
        PrimitiveKind.STRING,
    ).nullable

    override fun deserialize(decoder: Decoder): Instant? {
        val primitive = decoder.jsonElement().primitiveOrNull() ?: return null
        return if (primitive.isString) {
            parseInstant(
                primitive.content,
            )
        } else {
            primitive.longOrNull?.let(Instant::ofEpochSecond)
        }
    }

    override fun serialize(encoder: Encoder, value: Instant?): Unit = decodeOnly()
}

internal fun parseInstant(raw: String): Instant? {
    val text = raw.trim()
    val seconds = text.toLongOrNull()
    return when {
        text.isEmpty() -> null

        seconds != null -> Instant.ofEpochSecond(seconds)

        else -> tryParse { OffsetDateTime.parse(text).toInstant() }
            ?: tryParse { Instant.parse(text) }
            ?: tryParse { java.time.LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }
            ?: tryParse { LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant() }
    }
}

private inline fun tryParse(block: () -> Instant): Instant? = try {
    block()
} catch (_: DateTimeParseException) {
    null
}
