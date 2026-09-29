// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AttachmentKind
import social.aloha.core.model.MediaAttachment
import social.aloha.core.model.MediaDimensions
import social.aloha.core.model.MediaFocus
import social.aloha.core.model.MediaMeta
import social.aloha.core.network.decoding.EmptyArrayAsNullSerializer
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.OptionalDoubleSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer

/**
 * An attachment on the wire. Without ffmpeg Nextcloud Social sends `meta.original: []` and a preview
 * equal to the video URL; a fork may send `meta: []`. Either shape leaves the dimensions absent.
 */
@Serializable
internal data class MediaAttachmentDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val type: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("preview_url") @Serializable(with = LenientUrlSerializer::class) val previewUrl: String? = null,
    @SerialName("remote_url") @Serializable(with = LenientUrlSerializer::class) val remoteUrl: String? = null,
    val description: String? = null,
    val blurhash: String? = null,
    @Serializable(with = MediaMetaOrNull::class) val meta: MediaMetaDto? = null,
    @SerialName("hls_url") @Serializable(with = LenientUrlSerializer::class) val hlsUrl: String? = null,
)

@Serializable
internal data class MediaMetaDto(
    @Serializable(with = MediaDimensionsOrNull::class) val original: MediaDimensionsDto? = null,
    @Serializable(with = MediaDimensionsOrNull::class) val small: MediaDimensionsDto? = null,
    @Serializable(with = MediaFocusOrNull::class) val focus: MediaFocusDto? = null,
)

@Serializable
internal data class MediaDimensionsDto(
    @Serializable(with = OptionalIntSerializer::class) val width: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val height: Int? = null,
    val size: String? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val aspect: Double? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val duration: Double? = null,
    @SerialName("frame_rate") val frameRate: String? = null,
    @Serializable(with = OptionalIntSerializer::class) val bitrate: Int? = null,
)

@Serializable
internal data class MediaFocusDto(
    @Serializable(with = OptionalDoubleSerializer::class) val x: Double? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val y: Double? = null,
)

internal object MediaMetaOrNull : KSerializer<MediaMetaDto?> by EmptyArrayAsNullSerializer(MediaMetaDto.serializer())

internal object MediaDimensionsOrNull : KSerializer<MediaDimensionsDto?> by
EmptyArrayAsNullSerializer(MediaDimensionsDto.serializer())

internal object MediaFocusOrNull : KSerializer<MediaFocusDto?> by OrNullSerializer(MediaFocusDto.serializer())

internal fun MediaAttachmentDto.toDomain(): MediaAttachment = MediaAttachment(
    id = id,
    type = AttachmentKind.fromWire(type),
    url = url,
    previewUrl = previewUrl,
    remoteUrl = remoteUrl,
    description = description,
    blurhash = blurhash,
    meta = meta?.toDomain(),
    hlsUrl = hlsUrl,
)

internal fun MediaMetaDto.toDomain(): MediaMeta = MediaMeta(
    original = original?.toDomain(),
    small = small?.toDomain(),
    focus = focus?.let { if (it.x != null && it.y != null) MediaFocus(it.x, it.y) else null },
)

internal fun MediaDimensionsDto.toDomain(): MediaDimensions =
    MediaDimensions(width, height, size, aspect, duration, frameRate, bitrate)
