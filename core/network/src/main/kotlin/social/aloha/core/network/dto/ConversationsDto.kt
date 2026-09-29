// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AccountList
import social.aloha.core.model.Announcement
import social.aloha.core.model.AnnouncementReaction
import social.aloha.core.model.Conversation
import social.aloha.core.model.Marker
import social.aloha.core.model.MarkerSet
import social.aloha.core.model.SearchResults
import social.aloha.core.model.StatusContext
import social.aloha.core.model.Suggestion
import social.aloha.core.model.UnreadCount
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OrNullSerializer

@Serializable
internal data class StatusContextDto(
    @Serializable(with = LossyListSerializer::class) val ancestors: List<StatusDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val descendants: List<StatusDto> = emptyList(),
)

@Serializable
internal data class ConversationDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LossyListSerializer::class) val accounts: List<AccountDto> = emptyList(),
    @Serializable(with = LenientBoolSerializer::class) val unread: Boolean = false,
    @SerialName("last_status") @Serializable(with = StatusOrNull::class) val lastStatus: StatusDto? = null,
)

@Serializable
internal data class MarkerDto(
    @SerialName("last_read_id") @Serializable(with = FlexibleIdSerializer::class) val lastReadId: String,
    @Serializable(with = LenientIntSerializer::class) val version: Int = 0,
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
)

/** The markers, keyed by timeline; `{}` for an account with none yet. */
@Serializable
internal data class MarkerSetDto(
    @Serializable(with = MarkerOrNull::class) val home: MarkerDto? = null,
    @Serializable(with = MarkerOrNull::class) val notifications: MarkerDto? = null,
)

@Serializable
internal data class UnreadCountDto(@Serializable(with = LenientIntSerializer::class) val count: Int = 0)

@Serializable
internal data class AccountListDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val title: String = "",
    @SerialName("replies_policy") val repliesPolicy: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val exclusive: Boolean = false,
)

@Serializable
internal data class SearchResultsDto(
    @Serializable(with = LossyListSerializer::class) val accounts: List<AccountDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val statuses: List<StatusDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val hashtags: List<TagDto> = emptyList(),
)

@Serializable
internal data class SuggestionDto(
    val account: AccountDto,
    val source: String? = null,
    @Serializable(with = LossyListSerializer::class) val sources: List<String>? = null,
)

@Serializable
internal data class AnnouncementDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val content: String = "",
    @Serializable(with = LenientBoolSerializer::class) val published: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val read: Boolean = false,
    @SerialName("published_at") @Serializable(with = LenientInstantSerializer::class) val publishedAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("all_day") @Serializable(with = LenientBoolSerializer::class) val allDay: Boolean = false,
    @SerialName("ends_at") @Serializable(with = LenientInstantSerializer::class) val endsAt: Instant? = null,
    @Serializable(with = LossyListSerializer::class) val reactions: List<AnnouncementReactionDto> = emptyList(),
)

@Serializable
internal data class AnnouncementReactionDto(
    val name: String = "",
    @Serializable(with = LenientIntSerializer::class) val count: Int = 0,
    @Serializable(with = LenientBoolSerializer::class) val me: Boolean = false,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
)

internal object MarkerOrNull : KSerializer<MarkerDto?> by OrNullSerializer(MarkerDto.serializer())

internal fun StatusContextDto.toDomain(): StatusContext =
    StatusContext(ancestors.map { it.toDomain() }, descendants.map { it.toDomain() })

internal fun ConversationDto.toDomain(): Conversation =
    Conversation(id, accounts.map { it.toDomain() }, unread, lastStatus?.toDomain())

internal fun MarkerDto.toDomain(): Marker = Marker(lastReadId, version, updatedAt)

internal fun MarkerSetDto.toDomain(): MarkerSet = MarkerSet(home?.toDomain(), notifications?.toDomain())

internal fun UnreadCountDto.toDomain(): UnreadCount = UnreadCount(count)

internal fun AccountListDto.toDomain(): AccountList = AccountList(id, title, repliesPolicy, exclusive)

internal fun SearchResultsDto.toDomain(): SearchResults = SearchResults(
    accounts.map { it.toDomain() },
    statuses.map { it.toDomain() },
    hashtags.map { it.toDomain() },
)

internal fun SuggestionDto.toDomain(): Suggestion = Suggestion(account.toDomain(), source, sources)

internal fun AnnouncementDto.toDomain(): Announcement = Announcement(
    id = id,
    content = content,
    published = published,
    read = read,
    publishedAt = publishedAt,
    updatedAt = updatedAt,
    allDay = allDay,
    endsAt = endsAt,
    reactions = reactions.filter {
        it.name.isNotEmpty()
    }.map { AnnouncementReaction(it.name, it.count, it.me, it.url) },
)
