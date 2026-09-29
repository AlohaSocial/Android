// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminReport
import social.aloha.core.model.InstanceActivityWeek
import social.aloha.core.model.PublicDomainBlock
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LossyListSerializer

@Serializable
internal data class AdminAccountDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientTextSerializer::class) val username: String? = null,
    @Serializable(with = LenientTextSerializer::class) val domain: String? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = LenientBoolSerializer::class) val confirmed: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val approved: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val disabled: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val silenced: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val suspended: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val sensitized: Boolean = false,
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
)

internal fun AdminAccountDto.toDomain(): AdminAccount = AdminAccount(
    id = id,
    username = username.orEmpty(),
    domain = domain?.takeIf { it.isNotEmpty() },
    createdAt = createdAt,
    confirmed = confirmed,
    approved = approved,
    disabled = disabled,
    silenced = silenced,
    suspended = suspended,
    sensitized = sensitized,
    account = account?.toDomain(),
)

@Serializable
internal data class AdminReportDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @SerialName("action_taken") @Serializable(with = LenientBoolSerializer::class) val actionTaken: Boolean = false,
    @SerialName("action_taken_at") @Serializable(with = LenientInstantSerializer::class)
    val actionTakenAt: Instant? = null,
    @Serializable(with = LenientTextSerializer::class) val category: String? = null,
    @Serializable(with = LenientTextSerializer::class) val comment: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val forwarded: Boolean = false,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
    @SerialName("target_account") @Serializable(with = AccountOrNull::class) val targetAccount: AccountDto? = null,
    @SerialName("assigned_account") @Serializable(with = AccountOrNull::class) val assignedAccount: AccountDto? = null,
    @SerialName("action_taken_by_account") @Serializable(with = AccountOrNull::class)
    val actionTakenByAccount: AccountDto? = null,
    @Serializable(with = LossyListSerializer::class) val statuses: List<StatusDto> = emptyList(),
)

internal fun AdminReportDto.toDomain(): AdminReport = AdminReport(
    id = id,
    actionTaken = actionTaken,
    actionTakenAt = actionTakenAt,
    category = category.orEmpty(),
    comment = comment.orEmpty(),
    forwarded = forwarded,
    createdAt = createdAt,
    updatedAt = updatedAt,
    account = account?.toDomain(),
    targetAccount = targetAccount?.toDomain(),
    assignedAccount = assignedAccount?.toDomain(),
    actionTakenByAccount = actionTakenByAccount?.toDomain(),
    statuses = statuses.map { it.toDomain() },
)

/** Every value of `/api/v1/instance/activity` arrives as a string. */
@Serializable
internal data class InstanceActivityWeekDto(
    @Serializable(with = LenientTextSerializer::class) val week: String? = null,
    @Serializable(with = LenientIntSerializer::class) val statuses: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val logins: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val registrations: Int = 0,
)

internal fun InstanceActivityWeekDto.toDomain(): InstanceActivityWeek = InstanceActivityWeek(
    week = week?.toDoubleOrNull()?.toLong() ?: 0,
    statuses = statuses,
    logins = logins,
    registrations = registrations,
)

@Serializable
internal data class PublicDomainBlockDto(
    @Serializable(with = LenientTextSerializer::class) val domain: String? = null,
    @Serializable(with = LenientTextSerializer::class) val digest: String? = null,
    @Serializable(with = LenientTextSerializer::class) val severity: String? = null,
    @Serializable(with = LenientTextSerializer::class) val comment: String? = null,
)

internal fun PublicDomainBlockDto.toDomain(): PublicDomainBlock = PublicDomainBlock(
    domain = domain.orEmpty(),
    digest = digest.orEmpty(),
    severity = severity ?: "suspend",
    comment = comment.orEmpty(),
)
