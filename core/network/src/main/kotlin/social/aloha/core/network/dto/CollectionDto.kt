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
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import social.aloha.core.model.ContinueWatchingItem
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.WatchProgress
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalDoubleSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer

/** A Pixelfed album. Nextcloud Social counts its posts as `size`, Pixelfed as `post_count`. */
@Serializable
internal data class CollectionDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientTextSerializer::class) val title: String? = null,
    @Serializable(with = LenientTextSerializer::class) val description: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("post_count") @Serializable(with = OptionalIntSerializer::class) val postCount: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val size: Int? = null,
    @Serializable(with = LenientUrlSerializer::class) val thumbnail: String? = null,
    @Serializable(with = LenientTextSerializer::class) val visibility: String? = null,
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
)

internal fun CollectionDto.toDomain(): MediaCollection = MediaCollection(
    id = id,
    title = title.orEmpty(),
    description = description,
    url = url,
    postCount = postCount ?: size ?: 0,
    thumbnail = thumbnail,
    visibility = visibility,
    updatedAt = updatedAt,
)

/**
 * A continue-watching entry: the documented `{status_id, position, duration, status}`, or, as
 * Nextcloud Social answers, the post itself with the place in its `watch_progress`; without its
 * `account` up to 0.26.278, with it on later builds. The post is kept only when it decodes as a status.
 */
@Serializable
internal data class ContinueWatchingItemDto(
    @SerialName("status_id") @Serializable(with = OptionalIdSerializer::class) val statusId: String? = null,
    @Serializable(with = OptionalIdSerializer::class) val id: String? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val position: Double? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val duration: Double? = null,
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("watch_progress") @Serializable(with = WatchProgressOrNull::class)
    val watchProgress: WatchProgressDto? = null,
    @Serializable(with = StatusOrNull::class) val status: StatusDto? = null,
    @SerialName("media_attachments") @Serializable(with = LossyListSerializer::class)
    val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
)

/**
 * Reads an entry, and where it is the post itself with its author, as Nextcloud Social sends it, keeps
 * that post too, so it needs no second request.
 */
internal object ContinueWatchingEntry : KSerializer<ContinueWatchingItemDto> {
    override val descriptor: SerialDescriptor = ContinueWatchingItemDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): ContinueWatchingItemDto {
        val json = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val element = json.decodeJsonElement()
        val item = json.json.decodeFromJsonElement(ContinueWatchingItemDto.serializer(), element)
        if (item.status != null || item.statusId != null || (element as? JsonObject)?.get("account") == null) {
            return item
        }
        return item.copy(status = json.json.decodeFromJsonElement(StatusOrNull, element))
    }

    override fun serialize(encoder: Encoder, value: ContinueWatchingItemDto): Unit =
        throw SerializationException("wire DTOs are decode-only")
}

/** Null when the entry names no post at all. */
internal fun ContinueWatchingItemDto.toDomain(): ContinueWatchingItem? {
    val statusId = statusId ?: status?.id ?: id ?: return null
    val place = place()
    return ContinueWatchingItem(
        statusId = statusId,
        position = place.position,
        duration = place.duration.takeIf { it > 0 }
            ?: mediaAttachments.firstNotNullOfOrNull { it.toDomain().duration } ?: 0.0,
        updatedAt = place.updatedAt,
        status = status?.toDomain(),
    )
}

/** Where the entry was left: the documented top-level fields, else Nextcloud Social's `watch_progress`. */
private fun ContinueWatchingItemDto.place(): WatchProgress = WatchProgress(
    position = position ?: watchProgress?.position ?: 0.0,
    duration = duration ?: watchProgress?.duration ?: 0.0,
    updatedAt = updatedAt ?: watchProgress?.updatedAt,
)
