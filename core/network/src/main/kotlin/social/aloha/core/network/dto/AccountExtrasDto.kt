// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AuthorizedApp
import social.aloha.core.model.PortfolioLayout
import social.aloha.core.model.PortfolioPage
import social.aloha.core.model.PortfolioPlace
import social.aloha.core.model.PortfolioPost
import social.aloha.core.model.PortfolioSettings
import social.aloha.core.model.PortfolioSource
import social.aloha.core.model.VideoChannel
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer
import social.aloha.core.network.decoding.OrNullSerializer
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

@Serializable
internal data class PortfolioPlaceDto(
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientTextSerializer::class) val country: String? = null,
)

internal object PortfolioPlaceOrNull : KSerializer<PortfolioPlaceDto?> by OrNullSerializer(
    PortfolioPlaceDto.serializer(),
)

@Serializable
internal data class PortfolioPostDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientTextSerializer::class) val content: String? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("media_attachments") @Serializable(with = LossyListSerializer::class)
    val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
    @Serializable(with = PortfolioPlaceOrNull::class) val place: PortfolioPlaceDto? = null,
)

internal fun PortfolioPostDto.toDomain(): PortfolioPost = PortfolioPost(
    id = id,
    content = content,
    createdAt = createdAt,
    url = url,
    mediaAttachments = mediaAttachments.map { it.toDomain() },
    place = place?.let { PortfolioPlace(it.name, it.country) },
)

/** The four display switches, shared by the settings and the published page. */
@Serializable
internal data class PortfolioDto(
    @Serializable(with = LenientBoolSerializer::class) val active: Boolean = false,
    @Serializable(with = LenientTextSerializer::class) val title: String? = null,
    @Serializable(with = LenientTextSerializer::class) val intro: String? = null,
    @Serializable(with = LenientTextSerializer::class) val handle: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val avatar: String? = null,
    @Serializable(with = LenientTextSerializer::class) val layout: String? = null,
    @Serializable(with = LenientTextSerializer::class) val source: String? = null,
    @SerialName("collection_id") @Serializable(with = OptionalIdSerializer::class) val collectionId: String? = null,
    @SerialName("show_captions") @Serializable(with = OptionalBoolSerializer::class) val showCaptions: Boolean? = null,
    @SerialName("show_places") @Serializable(with = OptionalBoolSerializer::class) val showPlaces: Boolean? = null,
    @SerialName("show_dates") @Serializable(with = OptionalBoolSerializer::class) val showDates: Boolean? = null,
    @SerialName("show_avatar") @Serializable(with = OptionalBoolSerializer::class) val showAvatar: Boolean? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @Serializable(with = LossyListSerializer::class) val posts: List<PortfolioPostDto> = emptyList(),
)

/** The owner's settings: a switch the server leaves out is off. */
internal fun PortfolioDto.toSettings(): PortfolioSettings = PortfolioSettings(
    active = active,
    title = title.orEmpty(),
    intro = intro.orEmpty(),
    layout = PortfolioLayout.fromWire(layout),
    source = PortfolioSource.fromWire(source),
    collectionId = collectionId,
    showCaptions = showCaptions ?: false,
    showPlaces = showPlaces ?: false,
    showDates = showDates ?: false,
    showAvatar = showAvatar ?: false,
    url = url,
    posts = posts.map { it.toDomain() },
)

/** The published page: a switch the server leaves out is on, since the page defaults are the generous ones. */
internal fun PortfolioDto.toPage(): PortfolioPage = PortfolioPage(
    title = title.orEmpty(),
    intro = intro,
    handle = handle,
    avatar = avatar,
    layout = PortfolioLayout.fromWire(layout),
    showCaptions = showCaptions ?: true,
    showPlaces = showPlaces ?: true,
    showDates = showDates ?: true,
    showAvatar = showAvatar ?: true,
    posts = posts.map { it.toDomain() },
)
