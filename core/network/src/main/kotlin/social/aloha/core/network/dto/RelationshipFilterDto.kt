// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.model.FilterKeyword
import social.aloha.core.model.FilterStatus
import social.aloha.core.model.Relationship
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer

@Serializable
internal data class RelationshipDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientBoolSerializer::class) val following: Boolean = false,
    @SerialName("followed_by") @Serializable(with = LenientBoolSerializer::class) val followedBy: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val blocking: Boolean = false,
    @SerialName("blocked_by") @Serializable(with = LenientBoolSerializer::class) val blockedBy: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val muting: Boolean = false,
    @SerialName("muting_notifications") @Serializable(with = LenientBoolSerializer::class)
    val mutingNotifications: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val requested: Boolean = false,
    @SerialName("requested_by") @Serializable(with = LenientBoolSerializer::class) val requestedBy: Boolean = false,
    @SerialName("domain_blocking") @Serializable(with = LenientBoolSerializer::class)
    val domainBlocking: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val endorsed: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val notifying: Boolean = false,
    @SerialName("showing_reblogs") @Serializable(with = OptionalBoolSerializer::class)
    val showingReblogs: Boolean? = null,
    val note: String? = null,
    @Serializable(with = LossyListSerializer::class) val languages: List<String>? = null,
    @SerialName("mute_expires_at") @Serializable(with = LenientInstantSerializer::class)
    val muteExpiresAt: Instant? = null,
)

@Serializable
internal data class FilterDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val title: String? = null,
    @Serializable(with = LossyListSerializer::class) val context: List<String> = emptyList(),
    @SerialName("expires_at") @Serializable(with = LenientInstantSerializer::class) val expiresAt: Instant? = null,
    @SerialName("filter_action") val filterAction: String? = null,
    @Serializable(with = LossyListSerializer::class) val keywords: List<FilterKeywordDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val statuses: List<FilterStatusDto> = emptyList(),
)

@Serializable
internal data class FilterKeywordDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val keyword: String,
    @SerialName("whole_word") @Serializable(with = LenientBoolSerializer::class) val wholeWord: Boolean = false,
)

@Serializable
internal data class FilterStatusDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @SerialName("status_id") @Serializable(with = FlexibleIdSerializer::class) val statusId: String,
)

/** A relationship the server does not qualify shows reblogs, as Mastodon's default follow does. */
internal fun RelationshipDto.toDomain(): Relationship = Relationship(
    id = id,
    following = following,
    followedBy = followedBy,
    blocking = blocking,
    blockedBy = blockedBy,
    muting = muting,
    mutingNotifications = mutingNotifications,
    requested = requested,
    requestedBy = requestedBy,
    domainBlocking = domainBlocking,
    endorsed = endorsed,
    notifying = notifying,
    showingReblogs = showingReblogs ?: true,
    note = note,
    languages = languages,
    muteExpiresAt = muteExpiresAt,
)

internal fun FilterDto.toDomain(): Filter = Filter(
    id = id,
    title = title.orEmpty(),
    context = context.map(FilterContext::fromWire),
    expiresAt = expiresAt,
    filterAction = FilterAction.fromWire(filterAction),
    keywords = keywords.map { FilterKeyword(it.id, it.keyword, it.wholeWord) },
    statuses = statuses.map { FilterStatus(it.id, it.statusId) },
)
