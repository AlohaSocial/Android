// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.Card
import social.aloha.core.model.Poll
import social.aloha.core.model.PollOption
import social.aloha.core.model.VideoChapter
import social.aloha.core.model.VideoDetails
import social.aloha.core.network.decoding.ChapterStartSerializer
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LabelledTextSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer

@Serializable
internal data class CardDto(
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val type: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_url") @Serializable(with = LenientUrlSerializer::class) val authorUrl: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("provider_url") @Serializable(with = LenientUrlSerializer::class) val providerUrl: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val image: String? = null,
    val blurhash: String? = null,
    @Serializable(with = OptionalIntSerializer::class) val width: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val height: Int? = null,
    val html: String? = null,
)

@Serializable
internal data class PollDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @SerialName("expires_at") @Serializable(with = LenientInstantSerializer::class) val expiresAt: Instant? = null,
    @Serializable(with = LenientBoolSerializer::class) val expired: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val multiple: Boolean = false,
    @SerialName("votes_count") @Serializable(with = LenientIntSerializer::class) val votesCount: Int = 0,
    @SerialName("voters_count") @Serializable(with = OptionalIntSerializer::class) val votersCount: Int? = null,
    @Serializable(with = LenientBoolSerializer::class) val voted: Boolean = false,
    @SerialName("own_votes") @Serializable(with = LossyListSerializer::class) val ownVotes: List<Int> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val options: List<PollOptionDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val emojis: List<CustomEmojiDto> = emptyList(),
)

@Serializable
internal data class PollOptionDto(
    val title: String,
    @SerialName("votes_count") @Serializable(with = OptionalIntSerializer::class) val votesCount: Int? = null,
)

@Serializable
internal data class VideoDetailsDto(
    @Serializable(with = LenientIntSerializer::class) val views: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val likes: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val dislikes: Int = 0,
    @Serializable(with = LabelledTextSerializer::class) val category: String? = null,
    @Serializable(with = LabelledTextSerializer::class) val language: String? = null,
    @Serializable(with = LabelledTextSerializer::class) val licence: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val live: Boolean = false,
    @Serializable(with = LenientTextSerializer::class) val support: String? = null,
    @Serializable(with = OptionalBoolSerializer::class) val download: Boolean? = null,
    @Serializable(with = LossyListSerializer::class) val chapters: List<VideoChapterDto> = emptyList(),
)

@Serializable
internal data class VideoChapterDto(
    @Serializable(with = ChapterStartSerializer::class) val start: Double? = null,
    @Serializable(with = LenientTextSerializer::class) val title: String? = null,
)

internal fun CardDto.toDomain(): Card = Card(
    url = url,
    title = title.orEmpty(),
    description = description.orEmpty(),
    type = type ?: "link",
    authorName = authorName,
    authorUrl = authorUrl,
    providerName = providerName,
    providerUrl = providerUrl,
    image = image,
    blurhash = blurhash,
    width = width,
    height = height,
    html = html,
)

internal fun PollDto.toDomain(): Poll = Poll(
    id = id,
    expiresAt = expiresAt,
    expired = expired,
    multiple = multiple,
    votesCount = votesCount,
    votersCount = votersCount,
    voted = voted,
    ownVotes = ownVotes,
    options = options.map { PollOption(it.title, it.votesCount) },
    emojis = emojis.map { it.toDomain() },
)

/** A chapter whose start is unreadable is dropped. */
internal fun VideoDetailsDto.toDomain(): VideoDetails = VideoDetails(
    views = views,
    likes = likes,
    dislikes = dislikes,
    category = category,
    language = language,
    licence = licence,
    live = live,
    support = support,
    download = download,
    chapters = chapters.mapNotNull { chapter -> chapter.start?.let { VideoChapter(it, chapter.title.orEmpty()) } },
)
