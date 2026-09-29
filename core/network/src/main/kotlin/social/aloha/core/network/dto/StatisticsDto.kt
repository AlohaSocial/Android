// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AccountStatistics
import social.aloha.core.model.ConversationPartner
import social.aloha.core.model.NamedCount
import social.aloha.core.model.NumberMap
import social.aloha.core.model.StatisticsAccount
import social.aloha.core.model.StatisticsActivity
import social.aloha.core.model.StatisticsPartners
import social.aloha.core.model.StatisticsWindow
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.NumberMapSerializer
import social.aloha.core.network.decoding.OrNullSerializer

@Serializable
internal data class StatisticsDto(
    @Serializable(with = StatisticsAccountOrNull::class) val account: StatisticsAccountDto? = null,
    @Serializable(with = StatisticsWindowOrNull::class) val window: StatisticsWindowDto? = null,
    @Serializable(with = NumberMapSerializer::class) val posts: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val engagement: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val rates: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val visibility: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val consistency: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val media: Map<String, Double> = emptyMap(),
    @SerialName("by_month") @Serializable(with = NumberMapSerializer::class) val byMonth: Map<String, Double> =
        emptyMap(),
    @SerialName("engagement_by_month") @Serializable(with = NumberMapSerializer::class)
    val engagementByMonth: Map<String, Double> = emptyMap(),
    @Serializable(with = StatisticsActivityOrNull::class) val activity: StatisticsActivityDto? = null,
    @Serializable(with = StatisticsPartnersOrNull::class) val partners: StatisticsPartnersDto? = null,
    @Serializable(with = LossyListSerializer::class) val languages: List<NamedCountDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val domains: List<NamedCountDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val hashtags: List<NamedCountDto> = emptyList(),
)

@Serializable
internal data class StatisticsAccountDto(
    val acct: String = "",
    @Serializable(with = LenientIntSerializer::class) val followers: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val following: Int = 0,
)

@Serializable
internal data class StatisticsWindowDto(
    @Serializable(with = LenientIntSerializer::class) val days: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val counted: Int = 0,
    @Serializable(with = LenientBoolSerializer::class) val capped: Boolean = false,
)

@Serializable
internal data class StatisticsActivityDto(
    @Serializable(with = NumberMapSerializer::class) val originals: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val replies: Map<String, Double> = emptyMap(),
    @Serializable(with = NumberMapSerializer::class) val boosts: Map<String, Double> = emptyMap(),
)

@Serializable
internal data class StatisticsPartnersDto(
    @Serializable(with = LossyListSerializer::class) val inbound: List<ConversationPartnerDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val outbound: List<ConversationPartnerDto> = emptyList(),
)

@Serializable
internal data class NamedCountDto(
    val name: String,
    @Serializable(with = LenientIntSerializer::class) val count: Int = 0,
)

@Serializable
internal data class ConversationPartnerDto(
    val account: String,
    @Serializable(with = LenientIntSerializer::class) val replies: Int = 0,
)

internal object StatisticsAccountOrNull : KSerializer<StatisticsAccountDto?> by OrNullSerializer(
    StatisticsAccountDto.serializer(),
)

internal object StatisticsWindowOrNull : KSerializer<StatisticsWindowDto?> by OrNullSerializer(
    StatisticsWindowDto.serializer(),
)

internal object StatisticsActivityOrNull : KSerializer<StatisticsActivityDto?> by OrNullSerializer(
    StatisticsActivityDto.serializer(),
)

internal object StatisticsPartnersOrNull : KSerializer<StatisticsPartnersDto?> by OrNullSerializer(
    StatisticsPartnersDto.serializer(),
)

internal fun StatisticsDto.toDomain(): AccountStatistics = AccountStatistics(
    account = account?.let { StatisticsAccount(it.acct, it.followers, it.following) },
    window = window?.let { StatisticsWindow(it.days, it.counted, it.capped) },
    posts = NumberMap(posts),
    engagement = NumberMap(engagement),
    rates = NumberMap(rates),
    visibility = NumberMap(visibility),
    consistency = NumberMap(consistency),
    media = NumberMap(media),
    byMonth = NumberMap(byMonth),
    engagementByMonth = NumberMap(engagementByMonth),
    activity = activity?.let {
        StatisticsActivity(NumberMap(it.originals), NumberMap(it.replies), NumberMap(it.boosts))
    },
    partners = partners?.let { p ->
        StatisticsPartners(
            p.inbound.map { ConversationPartner(it.account, it.replies) },
            p.outbound.map { ConversationPartner(it.account, it.replies) },
        )
    },
    languages = languages.map { NamedCount(it.name, it.count) },
    domains = domains.map { NamedCount(it.name, it.count) },
    hashtags = hashtags.map { NamedCount(it.name, it.count) },
)
