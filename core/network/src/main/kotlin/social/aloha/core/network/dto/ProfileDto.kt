// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import social.aloha.core.model.FamiliarFollowers
import social.aloha.core.model.GifEntry
import social.aloha.core.model.GifLibrary
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.TaggedPerson
import social.aloha.core.model.TeamAccount
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer

/**
 * Highlights: `since` in Unix seconds as a number or a string, and `hashtags` as tag objects
 * (`{name, count}`) or bare names. Both read as names.
 */
@Serializable
internal data class ProfileHighlightsDto(
    @Serializable(with = LenientBoolSerializer::class) val available: Boolean = false,
    @Serializable(with = UnixSecondsSerializer::class) val since: Instant? = null,
    @Serializable(with = LossyListSerializer::class) val weeks: List<
        @Serializable(with = LenientIntSerializer::class)
        Int,
        > =
        emptyList(),
    @Serializable(with = NamesSerializer::class) val hashtags: List<String> = emptyList(),
)

internal fun ProfileHighlightsDto.toDomain(): ProfileHighlights = ProfileHighlights(available, since, weeks, hashtags)

/** Unix seconds as a number or a numeric string; zero and anything else are absent. */
internal object UnixSecondsSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor = LenientIntSerializer.descriptor

    override fun deserialize(decoder: Decoder): Instant? {
        val primitive = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        val seconds = primitive.longOrNull ?: primitive.content.toDoubleOrNull()?.toLong() ?: return null
        return if (seconds > 0) Instant.ofEpochSecond(seconds) else null
    }

    override fun serialize(encoder: Encoder, value: Instant?): Unit = throw SerializationException("decode-only")
}

/** A list of names sent as strings or as objects with a `name`. */
internal object NamesSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(LenientTextSerializer).descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val array = (decoder as? JsonDecoder)?.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonObject -> (item["name"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
                is JsonPrimitive -> item.takeUnless { it is JsonNull }?.content
                else -> null
            }?.takeIf { it.isNotEmpty() }
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>): Unit = throw SerializationException("decode-only")
}

@Serializable
internal data class FamiliarFollowersDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LossyListSerializer::class) val accounts: List<AccountDto> = emptyList(),
)

internal fun FamiliarFollowersDto.toDomain(): FamiliarFollowers = FamiliarFollowers(id, accounts.map { it.toDomain() })

/** A tagged person: an account-shaped object, or the bare handle. */
internal object TaggedPersonSerializer : KSerializer<TaggedPerson> {
    override val descriptor: SerialDescriptor = TaggedPersonDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): TaggedPerson {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val element = json.decodeJsonElement()
        if (element is JsonPrimitive && element.isString) return TaggedPerson(element.content, element.content)
        val dto = json.json.decodeFromJsonElement(TaggedPersonDto.serializer(), element)
        val handle = dto.acct ?: dto.username.orEmpty()
        return TaggedPerson(dto.id ?: handle, handle, dto.displayName)
    }

    override fun serialize(encoder: Encoder, value: TaggedPerson): Unit = throw SerializationException("decode-only")
}

@Serializable
internal data class TaggedPersonDto(
    @Serializable(with = OptionalIdSerializer::class) val id: String? = null,
    @Serializable(with = LenientTextSerializer::class) val acct: String? = null,
    @Serializable(with = LenientTextSerializer::class) val username: String? = null,
    @SerialName("display_name") @Serializable(with = LenientTextSerializer::class) val displayName: String? = null,
)

/** A library picture; its title may arrive as `name`, its preview as `preview`. */
@Serializable
internal data class GifEntryDto(
    @Serializable(with = LenientTextSerializer::class) val slug: String? = null,
    @Serializable(with = LenientTextSerializer::class) val title: String? = null,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("preview_url") @Serializable(with = LenientUrlSerializer::class) val previewUrl: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val preview: String? = null,
    @Serializable(with = OptionalIntSerializer::class) val width: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val height: Int? = null,
)

internal fun GifEntryDto.toDomain(): GifEntry {
    val slug = slug.orEmpty()
    return GifEntry(slug, title ?: name ?: slug, url, previewUrl ?: preview, width, height)
}

/** The total defaults to the number of pictures on the page. */
@Serializable
internal data class GifLibraryDto(
    @Serializable(with = LossyListSerializer::class) val gifs: List<GifEntryDto> = emptyList(),
    @Serializable(with = OptionalIntSerializer::class) val total: Int? = null,
    @Serializable(with = LenientTextSerializer::class) val attribution: String? = null,
)

internal fun GifLibraryDto.toDomain(): GifLibrary =
    GifLibrary(gifs.map { it.toDomain() }, total ?: gifs.size, attribution)

/** A team account's handle may arrive as `acct`, its name as `display_name`. */
@Serializable
internal data class TeamAccountDto(
    @Serializable(with = LenientTextSerializer::class) val handle: String? = null,
    @Serializable(with = LenientTextSerializer::class) val acct: String? = null,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @SerialName("display_name") @Serializable(with = LenientTextSerializer::class) val displayName: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val avatar: String? = null,
)

internal fun TeamAccountDto.toDomain(): TeamAccount {
    val handle = handle ?: acct.orEmpty()
    return TeamAccount(handle, name ?: displayName ?: handle, avatar)
}
