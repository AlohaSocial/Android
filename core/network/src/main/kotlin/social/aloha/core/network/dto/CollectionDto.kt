// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 * Nextcloud Social answers, the post itself without its `account`, the place in its `watch_progress`.
 * The post is kept only when it decodes as a status.
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
