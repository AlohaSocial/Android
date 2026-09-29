// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AnnualArchetype
import social.aloha.core.model.AnnualHashtag
import social.aloha.core.model.AnnualMonth
import social.aloha.core.model.AnnualReport
import social.aloha.core.model.AnnualReportData
import social.aloha.core.model.AnnualReportState
import social.aloha.core.model.AnnualTopStatuses
import social.aloha.core.model.WrappedAnnualReports
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer

@Serializable
internal data class AnnualMonthDto(
    @Serializable(with = LenientIntSerializer::class) val month: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val statuses: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val followers: Int = 0,
)

@Serializable
internal data class AnnualHashtagDto(
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = LenientIntSerializer::class) val count: Int = 0,
)

@Serializable
internal data class AnnualTopStatusesDto(
    @SerialName("by_reblogs") @Serializable(with = OptionalIdSerializer::class) val byReblogs: String? = null,
    @SerialName("by_replies") @Serializable(with = OptionalIdSerializer::class) val byReplies: String? = null,
    @SerialName("by_favourites") @Serializable(with = OptionalIdSerializer::class) val byFavourites: String? = null,
)

@Serializable
internal data class AnnualReportDataDto(
    @Serializable(with = LenientTextSerializer::class) val archetype: String? = null,
    @SerialName("time_series") @Serializable(with = LossyListSerializer::class)
    val timeSeries: List<AnnualMonthDto> = emptyList(),
    @SerialName("top_hashtags") @Serializable(with = LossyListSerializer::class)
    val topHashtags: List<AnnualHashtagDto> = emptyList(),
    @SerialName(
        "top_statuses",
    ) @Serializable(with = TopStatusesOrNull::class) val topStatuses: AnnualTopStatusesDto? = null,
)

@Serializable
internal data class AnnualReportDto(
    @Serializable(with = LenientIntSerializer::class) val year: Int = 0,
    @Serializable(with = AnnualDataOrNull::class) val data: AnnualReportDataDto? = null,
    @SerialName("schema_version") @Serializable(with = OptionalIntSerializer::class) val schemaVersion: Int? = null,
    @SerialName("share_url") @Serializable(with = LenientUrlSerializer::class) val shareUrl: String? = null,
    @SerialName("account_id") @Serializable(with = OptionalIdSerializer::class) val accountId: String? = null,
)

@Serializable
internal data class WrappedAnnualReportsDto(
    @SerialName("annual_reports") @Serializable(with = LossyListSerializer::class)
    val annualReports: List<AnnualReportDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val accounts: List<AccountDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val statuses: List<StatusDto> = emptyList(),
)

@Serializable
internal data class AnnualReportStateDto(@Serializable(with = LenientTextSerializer::class) val state: String? = null)

internal object TopStatusesOrNull : KSerializer<AnnualTopStatusesDto?> by OrNullSerializer(
    AnnualTopStatusesDto.serializer(),
)

internal object AnnualDataOrNull : KSerializer<AnnualReportDataDto?> by OrNullSerializer(
    AnnualReportDataDto.serializer(),
)

internal fun AnnualReportDto.toDomain(): AnnualReport = AnnualReport(
    year = year,
    data = data?.toDomain() ?: AnnualReportData(),
    schemaVersion = schemaVersion ?: 1,
    shareUrl = shareUrl,
    accountId = accountId.orEmpty(),
)

internal fun AnnualReportDataDto.toDomain(): AnnualReportData = AnnualReportData(
    archetype = AnnualArchetype.fromWire(archetype),
    timeSeries = timeSeries.map { AnnualMonth(it.month, it.statuses, it.followers) },
    topHashtags = topHashtags.map { AnnualHashtag(it.name.orEmpty(), it.count) },
    topStatuses = topStatuses?.let { AnnualTopStatuses(it.byReblogs, it.byReplies, it.byFavourites) }
        ?: AnnualTopStatuses(),
)

internal fun WrappedAnnualReportsDto.toDomain(): WrappedAnnualReports = WrappedAnnualReports(
    annualReports = annualReports.map { it.toDomain() },
    accounts = accounts.map { it.toDomain() },
    statuses = statuses.map { it.toDomain() },
)

/** A state the server leaves out is `ineligible`: there is nothing to show. */
internal fun AnnualReportStateDto.toDomain(): AnnualReportState =
    state?.let(AnnualReportState::fromWire) ?: AnnualReportState.Ineligible
