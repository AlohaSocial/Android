// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import social.aloha.core.model.Account
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.Story
import social.aloha.core.model.StoryCarousel
import social.aloha.core.model.StoryReaction
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.decoding.OptionalDoubleSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer
import social.aloha.core.network.decoding.lossy

/**
 * A story in every shape seen: Nextcloud Social's own (`account_id` and the file under `media`),
 * the Mastodon-ish flat one (`account`, `url`, `type`), and a Pixelfed v1.2 carousel item (`src`,
 * `type: "photo"`, `created_at`).
 */
@Serializable
internal data class StoryDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
    @SerialName("account_id") @Serializable(with = LenientTextSerializer::class) val accountId: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val src: String? = null,
    @SerialName("preview_url") @Serializable(with = LenientUrlSerializer::class) val previewUrl: String? = null,
    @Serializable(with = LenientTextSerializer::class) val type: String? = null,
    @Serializable(with = StoryMediaOrNull::class) val media: StoryMediaDto? = null,
    @Serializable(with = LenientTextSerializer::class) val caption: String? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val duration: Double? = null,
    @SerialName("published_at") @Serializable(with = LenientInstantSerializer::class) val publishedAt: Instant? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("expires_at") @Serializable(with = LenientInstantSerializer::class) val expiresAt: Instant? = null,
    @Serializable(with = OptionalBoolSerializer::class) val seen: Boolean? = null,
    @SerialName("view_count") @Serializable(with = OptionalIntSerializer::class) val viewCount: Int? = null,
)

/** Nextcloud Social nests the file under `media`, as a media attachment. */
@Serializable
internal data class StoryMediaDto(
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("preview_url") @Serializable(with = LenientUrlSerializer::class) val previewUrl: String? = null,
    @Serializable(with = LenientTextSerializer::class) val type: String? = null,
)

internal object StoryMediaOrNull : KSerializer<StoryMediaDto?> by OrNullSerializer(StoryMediaDto.serializer())

/** Pixelfed calls a picture a `photo`; every other name is an attachment kind. */
private fun storyKind(raw: String?): AttachmentKind =
    if (raw == "photo") AttachmentKind.Image else raw?.let(AttachmentKind::fromWire) ?: AttachmentKind.Image

/** [poster] and [seen] come from a carousel node, where the account sits on the node above a run of stories. */
internal fun StoryDto.toDomain(poster: Account? = null, seen: Boolean? = null): Story {
    val published = publishedAt ?: createdAt
    return Story(
        id = id,
        account = poster ?: account?.toDomain(),
        accountUri = accountId,
        url = fileUrl(),
        previewUrl = previewUrl ?: media?.previewUrl,
        type = storyKind(type ?: media?.type),
        caption = caption,
        duration = duration ?: Story.DEFAULT_DURATION,
        publishedAt = published,
        expiresAt = expiresAt ?: published?.plus(Story.LIFETIME),
        seen = seen ?: this.seen ?: false,
        viewCount = viewCount,
    )
}

/** The file: flat `url`, Nextcloud Social's `media.url`, or a Pixelfed carousel item's `src`. */
private fun StoryDto.fileUrl(): String? = url ?: media?.url ?: src

/** The poster of a Pixelfed carousel node, which is not an account entity. */
@Serializable
internal data class StoryUserDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val username: String = "",
    @SerialName("username_acct") val usernameAcct: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val avatar: String? = null,
)

internal fun StoryUserDto.toDomain(): Account =
    Account(id = id, username = username, acct = usernameAcct ?: username, avatar = avatar, avatarStatic = avatar)

@Serializable
internal data class StoryNodeDto(
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
    @Serializable(with = StoryUserOrNull::class) val user: StoryUserDto? = null,
    @Serializable(with = LossyListSerializer::class) val stories: List<StoryDto>? = null,
    @Serializable(with = LossyListSerializer::class) val nodes: List<StoryDto>? = null,
    @Serializable(with = OptionalBoolSerializer::class) val seen: Boolean? = null,
)

internal object StoryUserOrNull : KSerializer<StoryUserDto?> by OrNullSerializer(StoryUserDto.serializer())

internal data class StoryCarouselDto(val own: List<StoryDto>, val nodes: List<StoryNodeDto>, val flat: List<StoryDto>)

/** Either a flat array of stories or `{self, nodes}`; `self` may be `null`. */
internal object StoryCarouselSerializer : KSerializer<StoryCarouselDto> {
    override val descriptor: SerialDescriptor = StoryNodeDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): StoryCarouselDto {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        return when (val root = json.decodeJsonElement()) {
            is JsonArray -> StoryCarouselDto(emptyList(), emptyList(), json.json.lossy(StoryDto.serializer(), root))

            is JsonObject -> StoryCarouselDto(
                own = json.json.lossy(StoryDto.serializer(), root["self"]),
                nodes = json.json.lossy(StoryNodeDto.serializer(), root["nodes"]),
                flat = emptyList(),
            )

            else -> StoryCarouselDto(emptyList(), emptyList(), emptyList())
        }
    }

    override fun serialize(encoder: Encoder, value: StoryCarouselDto): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

/** A node without a poster has no one to show the stories under, so it is left out. */
internal fun StoryCarouselDto.toDomain(): StoryCarousel = StoryCarousel(
    own = own.map { it.toDomain() },
    others = flat.map { it.toDomain() } + nodes.flatMap { node ->
        val poster = node.account?.toDomain() ?: node.user?.toDomain() ?: return@flatMap emptyList()
        (node.stories ?: node.nodes).orEmpty().map { it.toDomain(poster, node.seen) }
    },
)

@Serializable
internal data class StoryReactionDto(
    @Serializable(with = OptionalIdSerializer::class) val id: String? = null,
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
    @Serializable(with = AccountOrNull::class) val profile: AccountDto? = null,
    @Serializable(with = LenientTextSerializer::class) val reaction: String? = null,
    @Serializable(with = LenientTextSerializer::class) val emoji: String? = null,
    @Serializable(with = LenientTextSerializer::class) val comment: String? = null,
    @Serializable(with = LenientTextSerializer::class) val caption: String? = null,
    @Serializable(with = LenientTextSerializer::class) val text: String? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
)

/** Null when the reaction names nobody, since a reaction is read as who reacted. */
internal fun StoryReactionDto.toDomain(): StoryReaction? {
    val who = (account ?: profile)?.toDomain() ?: return null
    val reaction = reaction ?: emoji
    val comment = comment ?: caption ?: text
    return StoryReaction(id ?: "${who.id}-${reaction ?: comment.orEmpty()}", who, reaction, comment, createdAt)
}
