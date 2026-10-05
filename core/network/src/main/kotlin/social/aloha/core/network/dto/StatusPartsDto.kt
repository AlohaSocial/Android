// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import social.aloha.core.model.ApplicationSummary
import social.aloha.core.model.FilterResult
import social.aloha.core.model.Mention
import social.aloha.core.model.QuotedStatus
import social.aloha.core.model.Reaction
import social.aloha.core.model.StatusPlace
import social.aloha.core.model.StatusTag
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalDoubleSerializer
import social.aloha.core.network.decoding.OrNullSerializer
import social.aloha.core.network.decoding.decodeOrRecord

@Serializable
internal data class MentionDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val username: String = "",
    val acct: String = "",
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
)

@Serializable
internal data class StatusTagDto(
    val name: String,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
)

@Serializable
internal data class ReactionDto(
    val name: String,
    @Serializable(with = LenientIntSerializer::class) val count: Int = 0,
    @Serializable(with = LenientBoolSerializer::class) val me: Boolean = false,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("static_url") @Serializable(with = LenientUrlSerializer::class) val staticUrl: String? = null,
)

@Serializable
internal data class FilterResultDto(
    val filter: FilterDto,
    @SerialName("keyword_matches") @Serializable(with = LossyListSerializer::class)
    val keywordMatches: List<String>? = null,
    @SerialName("status_matches") @Serializable(with = LossyListSerializer::class)
    val statusMatches: List<String>? = null,
)

@Serializable
internal data class ApplicationSummaryDto(
    val name: String,
    @Serializable(with = LenientUrlSerializer::class) val website: String? = null,
)

internal data class QuotedStatusDto(val state: String?, val quotedStatus: StatusDto?)

@Serializable
internal data class StatusPlaceDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val name: String = "",
    val country: String? = null,
    @SerialName("lat") @Serializable(with = OptionalDoubleSerializer::class) val latitude: Double? = null,
    @SerialName("lon") @Serializable(with = OptionalDoubleSerializer::class) val longitude: Double? = null,
    @SerialName("long") @Serializable(with = OptionalDoubleSerializer::class) val long: Double? = null,
)

/** Who may quote a post, and what quoting it comes to for the reader. */
@Serializable
internal data class QuoteApprovalDto(@SerialName("current_user") val currentUser: String? = null)

internal object QuoteApprovalOrNull : KSerializer<QuoteApprovalDto?> by OrNullSerializer(QuoteApprovalDto.serializer())

internal object CardOrNull : KSerializer<CardDto?> by OrNullSerializer(CardDto.serializer())

internal object PollOrNull : KSerializer<PollDto?> by OrNullSerializer(PollDto.serializer())

internal object ApplicationOrNull : KSerializer<ApplicationSummaryDto?> by OrNullSerializer(
    ApplicationSummaryDto.serializer(),
)

internal object StatusPlaceOrNull : KSerializer<StatusPlaceDto?> by OrNullSerializer(StatusPlaceDto.serializer())

internal object VideoDetailsOrNull : KSerializer<VideoDetailsDto?> by OrNullSerializer(VideoDetailsDto.serializer())

/**
 * A quote in either shape servers send: Mastodon 4.4's `{state, quoted_status}` wrapper, or the quoted
 * status itself. A quote that fits neither is absent rather than failing the status.
 */
internal object QuoteSerializer : KSerializer<QuotedStatusDto?> {
    override val descriptor: SerialDescriptor = StatusDto.serializer().descriptor.nullable

    override fun deserialize(decoder: Decoder): QuotedStatusDto? {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val element = json.decodeJsonElement() as? JsonObject ?: return null
        val isWrapper = "quoted_status" in element || "state" in element
        val quoted = (if (isWrapper) element["quoted_status"] else element)
            ?.takeUnless { it is JsonNull }
            ?.let { json.json.decodeOrRecord(StatusDto.serializer(), it, index = 0) }
        val state = if (isWrapper) {
            (element["state"] as? JsonPrimitive)?.takeUnless {
                it is JsonNull
            }?.content
        } else {
            null
        }
        return if (isWrapper || quoted != null) QuotedStatusDto(state, quoted) else null
    }

    override fun serialize(encoder: Encoder, value: QuotedStatusDto?): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

internal fun MentionDto.toDomain(): Mention = Mention(id, username, acct, url)

internal fun StatusTagDto.toDomain(): StatusTag = StatusTag(name, url)

internal fun ReactionDto.toDomain(): Reaction = Reaction(name, count, me, url, staticUrl)

internal fun FilterResultDto.toDomain(): FilterResult = FilterResult(filter.toDomain(), keywordMatches, statusMatches)

internal fun ApplicationSummaryDto.toDomain(): ApplicationSummary = ApplicationSummary(name, website)

internal fun QuotedStatusDto.toDomain(): QuotedStatus = QuotedStatus(state, quotedStatus?.toDomain())

/** Nextcloud Social spells the longitude `long` in place search and `lon` on a status; both are read. */
internal fun StatusPlaceDto.toDomain(): StatusPlace = StatusPlace(id, name, country, latitude, longitude ?: long)
