// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.Tag
import social.aloha.core.model.TagHistory
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer

@Serializable
internal data class TagDto(
    val name: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @Serializable(with = LossyListSerializer::class) val history: List<TagHistoryDto> = emptyList(),
    @Serializable(with = OptionalBoolSerializer::class) val following: Boolean? = null,
)

/** One bucket of tag use; counts arrive as strings on Mastodon and as numbers on some forks. */
@Serializable
internal data class TagHistoryDto(
    @Serializable(with = LenientTextSerializer::class) val day: String? = null,
    @Serializable(with = LenientTextSerializer::class) val uses: String? = null,
    @Serializable(with = LenientTextSerializer::class) val accounts: String? = null,
)

@Serializable
internal data class FeaturedTagDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val name: String = "",
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("statuses_count") @Serializable(with = LenientIntSerializer::class) val statusesCount: Int = 0,
    @SerialName("last_status_at") @Serializable(with = LenientInstantSerializer::class)
    val lastStatusAt: Instant? = null,
)

internal fun TagDto.toDomain(): Tag = Tag(
    name = name.orEmpty(),
    url = url,
    history = history.map { TagHistory(it.day.orEmpty(), it.uses ?: "0", it.accounts ?: "0") },
    following = following,
)

internal fun FeaturedTagDto.toDomain(): FeaturedTag = FeaturedTag(id, name, url, statusesCount, lastStatusAt)
