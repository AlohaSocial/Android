// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import social.aloha.core.model.DirectoryHashtagResults
import social.aloha.core.model.DirectorySearchResults
import social.aloha.core.model.DirectorySource
import social.aloha.core.model.DirectorySourceReport
import social.aloha.core.model.DiscoverCategory
import social.aloha.core.model.FollowGraph
import social.aloha.core.model.FollowGraphStatus
import social.aloha.core.model.FollowGraphSuggestion
import social.aloha.core.model.StarterPack
import social.aloha.core.model.Tag
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.decodeOrRecord
import social.aloha.core.network.decoding.lossy

/** Nextcloud Social names a pack by `id`; the documented shape by `slug`. */
@Serializable
internal data class StarterPackDto(
    @Serializable(with = LenientTextSerializer::class) val slug: String? = null,
    @Serializable(with = OptionalIdSerializer::class) val id: String? = null,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientTextSerializer::class) val description: String? = null,
    @Serializable(with = LossyListSerializer::class) val handles: List<String> = emptyList(),
    @Serializable(with = OptionalIntSerializer::class) val size: Int? = null,
    @Serializable(with = LossyListSerializer::class) val accounts: List<AccountDto> = emptyList(),
)

/** Null when the pack has no name to address it by. */
internal fun StarterPackDto.toDomain(): StarterPack? {
    val slug = slug ?: id ?: return null
    return StarterPack(
        slug = slug,
        name = name ?: slug,
        description = description.orEmpty(),
        handles = handles,
        size = size?.takeIf { it > 0 } ?: maxOf(handles.size, accounts.size),
        accounts = accounts.map { it.toDomain() },
    )
}

@Serializable
internal data class DirectorySourceDto(
    @Serializable(with = LenientTextSerializer::class) val host: String? = null,
    @Serializable(with = LenientTextSerializer::class) val kind: String? = null,
    @Serializable(with = LenientTextSerializer::class) val label: String? = null,
)

internal fun DirectorySourceDto.toDomain(): DirectorySource =
    DirectorySource(host.orEmpty(), kind.orEmpty(), label ?: host.orEmpty())

/** A source's count arrives as `count`, `accounts` or `hashtags`, depending on what was asked. */
@Serializable
internal data class DirectorySourceReportDto(
    @Serializable(with = LenientTextSerializer::class) val host: String? = null,
    @Serializable(with = LenientTextSerializer::class) val status: String? = null,
    @Serializable(with = LenientTextSerializer::class) val error: String? = null,
    @Serializable(with = OptionalIntSerializer::class) val count: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val accounts: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val hashtags: Int? = null,
)

internal fun DirectorySourceReportDto.toDomain(): DirectorySourceReport =
    DirectorySourceReport(host.orEmpty(), status, error, count ?: accounts ?: hashtags ?: 0)

internal data class DirectoryResultsDto<T>(val results: List<T>, val sources: List<DirectorySourceReportDto>)

/**
 * The documented `{<member>: […], sources: […]}` or a bare array of results. [members] are tried
 * in order; hashtags arrive as `hashtags` or `tags`.
 */
internal class DirectoryResultsSerializer<T>(private val element: KSerializer<T>, private vararg val members: String) :
    KSerializer<DirectoryResultsDto<T>> {
    override val descriptor: SerialDescriptor = DirectorySourceReportDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): DirectoryResultsDto<T> {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        return when (val root = json.decodeJsonElement()) {
            is JsonArray -> DirectoryResultsDto(json.json.lossy(element, root), emptyList())

            is JsonObject -> DirectoryResultsDto(
                results = json.json.lossy(element, members.firstNotNullOfOrNull { root[it] as? JsonArray }),
                sources = json.json.lossy(DirectorySourceReportDto.serializer(), root["sources"]),
            )

            else -> DirectoryResultsDto(emptyList(), emptyList())
        }
    }

    override fun serialize(encoder: Encoder, value: DirectoryResultsDto<T>): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

internal fun DirectoryResultsDto<AccountDto>.toSearchResults(): DirectorySearchResults =
    DirectorySearchResults(results.map { it.toDomain() }, sources.map { it.toDomain() })

internal fun DirectoryResultsDto<TagDto>.toHashtagResults(): DirectoryHashtagResults = DirectoryHashtagResults(
    results.map(TagDto::toDomain).filter {
        it.name.isNotEmpty()
    },
    sources.map { it.toDomain() },
)

/** `{account, count, via}` (with `followers` and `through` as older names), or a bare account. */
internal object FollowGraphSuggestionSerializer : KSerializer<FollowGraphSuggestion> {
    override val descriptor: SerialDescriptor = AccountDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): FollowGraphSuggestion {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val root =
            json.decodeJsonElement() as? JsonObject ?: throw SerializationException("suggestion is not an object")
        val nested = root["account"] as? JsonObject
            ?: return FollowGraphSuggestion(json.json.decodeFromJsonElement(AccountDto.serializer(), root).toDomain())
        val count = json.json.decodeFromJsonElement(SuggestionCountsDto.serializer(), root)
        val via = json.json.lossy(AccountDto.serializer(), root["via"] ?: root["through"])
        return FollowGraphSuggestion(
            account = json.json.decodeFromJsonElement(AccountDto.serializer(), nested).toDomain(),
            count = count.count ?: count.followers ?: 0,
            via = via.map { it.toDomain() },
        )
    }

    override fun serialize(encoder: Encoder, value: FollowGraphSuggestion): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

@Serializable
internal data class SuggestionCountsDto(
    @Serializable(with = OptionalIntSerializer::class) val count: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val followers: Int? = null,
)

/** Reads `suggestions` lossily: one malformed suggestion never empties the graph. */
internal object FollowGraphSerializer : KSerializer<FollowGraph> {
    override val descriptor: SerialDescriptor = FollowGraphCountsDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): FollowGraph {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val root = json.decodeJsonElement() as? JsonObject ?: return FollowGraph()
        val counts = json.json.decodeFromJsonElement(FollowGraphCountsDto.serializer(), root)
        val suggestions = (root["suggestions"] as? JsonArray).orEmpty().mapIndexedNotNull { index, item ->
            json.json.decodeOrRecord(FollowGraphSuggestionSerializer, item, index)
        }
        return FollowGraph(suggestions, counts.asked, counts.needs)
    }

    override fun serialize(encoder: Encoder, value: FollowGraph): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

@Serializable
internal data class FollowGraphCountsDto(
    @Serializable(with = LenientIntSerializer::class) val asked: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val needs: Int = 0,
)

/** The flag arrives as `worthwhile`, `eligible` or `ready`. */
@Serializable
internal data class FollowGraphStatusDto(
    @Serializable(with = OptionalBoolSerializer::class) val worthwhile: Boolean? = null,
    @Serializable(with = OptionalBoolSerializer::class) val eligible: Boolean? = null,
    @Serializable(with = OptionalBoolSerializer::class) val ready: Boolean? = null,
    @Serializable(with = LenientIntSerializer::class) val following: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val needs: Int = 0,
)

/** With no flag at all, enough follows is the answer. */
internal fun FollowGraphStatusDto.toDomain(): FollowGraphStatus = FollowGraphStatus(
    isWorthwhile = worthwhile ?: eligible ?: ready ?: (needs == 0 || following >= needs),
    following = following,
    needs = needs,
)

/** A category's tags arrive as `hashtags` or `tags`; an unnamed one is keyed by its name. */
@Serializable
internal data class DiscoverCategoryDto(
    @Serializable(with = OptionalIdSerializer::class) val id: String? = null,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LossyListSerializer::class) val hashtags: List<String>? = null,
    @Serializable(with = LossyListSerializer::class) val tags: List<String>? = null,
)

internal fun DiscoverCategoryDto.toDomain(): DiscoverCategory {
    val name = name.orEmpty()
    return DiscoverCategory(
        id = id ?: name.lowercase(),
        name = name,
        hashtags = (hashtags ?: tags).orEmpty().mapNotNull(Tag::normalise),
    )
}
