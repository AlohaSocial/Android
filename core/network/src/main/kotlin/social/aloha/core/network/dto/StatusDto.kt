// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.Status
import social.aloha.core.model.Visibility
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

/**
 * A status on the wire. Nextcloud Social always sends `reblog: null` (a boost is not visible as a boost),
 * `reactions: []` whatever was reacted, and `place: null` whatever was sent.
 */
@Serializable
internal data class StatusDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val account: AccountDto,
    val uri: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("edited_at") @Serializable(with = LenientInstantSerializer::class) val editedAt: Instant? = null,
    val content: String? = null,
    @SerialName("spoiler_text") val spoilerText: String? = null,
    val visibility: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val sensitive: Boolean = false,
    val language: String? = null,
    @SerialName("replies_count") @Serializable(with = LenientIntSerializer::class) val repliesCount: Int = 0,
    @SerialName("reblogs_count") @Serializable(with = LenientIntSerializer::class) val reblogsCount: Int = 0,
    @SerialName("favourites_count") @Serializable(with = LenientIntSerializer::class) val favouritesCount: Int = 0,
    @Serializable(with = LenientBoolSerializer::class) val favourited: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val reblogged: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val bookmarked: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val pinned: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val muted: Boolean = false,
    @SerialName("in_reply_to_id") @Serializable(with = OptionalIdSerializer::class) val inReplyToId: String? = null,
    @SerialName("in_reply_to_account_id") @Serializable(with = OptionalIdSerializer::class)
    val inReplyToAccountId: String? = null,
    @Serializable(with = StatusOrNull::class) val reblog: StatusDto? = null,
    @SerialName("media_attachments") @Serializable(with = LossyListSerializer::class)
    val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val mentions: List<MentionDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val tags: List<StatusTagDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val emojis: List<CustomEmojiDto> = emptyList(),
    @Serializable(with = PollOrNull::class) val poll: PollDto? = null,
    @Serializable(with = CardOrNull::class) val card: CardDto? = null,
    @Serializable(with = ApplicationOrNull::class) val application: ApplicationSummaryDto? = null,
    val text: String? = null,
    @Serializable(with = LossyListSerializer::class) val filtered: List<FilterResultDto>? = null,
    @Serializable(with = LossyListSerializer::class) val reactions: List<ReactionDto>? = null,
    @SerialName("quote_id") @Serializable(with = OptionalIdSerializer::class) val quoteId: String? = null,
    @Serializable(with = QuoteSerializer::class) val quote: QuotedStatusDto? = null,
    @SerialName("quote_approval_policy") @Serializable(with = LenientTextSerializer::class)
    val quoteApprovalPolicy: String? = null,
    @SerialName("dislikes_count") @Serializable(with = LenientIntSerializer::class) val dislikesCount: Int = 0,
    @Serializable(with = LenientBoolSerializer::class) val disliked: Boolean = false,
    @Serializable(with = OptionalBoolSerializer::class) val archived: Boolean? = null,
    @Serializable(with = StatusPlaceOrNull::class) val place: StatusPlaceDto? = null,
    @Serializable(with = VideoDetailsOrNull::class) val video: VideoDetailsDto? = null,
    @Serializable(with = OptionalBoolSerializer::class) val local: Boolean? = null,
)

internal object StatusOrNull : KSerializer<StatusDto?> by OrNullSerializer(StatusDto.serializer())

@Suppress("LongMethod") // a one-to-one field mapping; splitting it hides nothing
internal fun StatusDto.toDomain(): Status = Status(
    id = id,
    account = account.toDomain(),
    uri = uri.orEmpty(),
    url = url,
    createdAt = createdAt ?: Instant.EPOCH,
    editedAt = editedAt,
    content = content.orEmpty(),
    spoilerText = spoilerText.orEmpty(),
    visibility = Visibility.fromWire(visibility),
    sensitive = sensitive,
    language = language,
    repliesCount = repliesCount,
    reblogsCount = reblogsCount,
    favouritesCount = favouritesCount,
    favourited = favourited,
    reblogged = reblogged,
    bookmarked = bookmarked,
    pinned = pinned,
    muted = muted,
    inReplyToId = inReplyToId,
    inReplyToAccountId = inReplyToAccountId,
    reblog = reblog?.toDomain(),
    mediaAttachments = mediaAttachments.map { it.toDomain() },
    mentions = mentions.map { it.toDomain() },
    tags = tags.map { it.toDomain() },
    emojis = emojis.map { it.toDomain() },
    poll = poll?.toDomain(),
    card = card?.toDomain(),
    application = application?.toDomain(),
    text = text,
    filtered = filtered?.map { it.toDomain() },
    reactions = reactions?.map { it.toDomain() },
    quoteId = quoteId,
    quote = quote?.toDomain(),
    quoteApprovalPolicy = quoteApprovalPolicy,
    dislikesCount = dislikesCount,
    disliked = disliked,
    archived = archived,
    place = place?.toDomain(),
    video = video?.toDomain(),
    local = local,
)
