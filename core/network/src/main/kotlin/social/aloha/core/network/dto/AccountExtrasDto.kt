// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AuthorizedApp
import social.aloha.core.model.VideoChannel
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.StringListSerializer

/** Scopes arrive as an array from the app and as one space-separated string from the OAuth layer. */
@Serializable
internal data class AuthorizedAppDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val website: String? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("signed_in") @Serializable(with = LenientInstantSerializer::class) val signedIn: Instant? = null,
    @SerialName("last_used_at") @Serializable(with = LenientInstantSerializer::class) val lastUsedAt: Instant? = null,
    @Serializable(with = StringListSerializer::class) val scopes: List<String> = emptyList(),
)

internal fun AuthorizedAppDto.toDomain(): AuthorizedApp =
    AuthorizedApp(id, name.orEmpty(), website, createdAt, signedIn, lastUsedAt, scopes)

@Serializable
internal data class VideoChannelDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientTextSerializer::class) val handle: String? = null,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientTextSerializer::class) val description: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("videos_count") @Serializable(with = LenientIntSerializer::class) val videosCount: Int = 0,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
)

internal fun VideoChannelDto.toDomain(): VideoChannel =
    VideoChannel(id, handle.orEmpty(), name.orEmpty(), description.orEmpty(), url, videosCount, createdAt)
