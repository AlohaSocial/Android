// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.NotificationPolicy
import social.aloha.core.model.NotificationPolicySummary
import social.aloha.core.model.NotificationRequest
import social.aloha.core.model.PolicyDecision
import social.aloha.core.model.Preferences
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Visibility
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer

@Serializable
internal data class PreferencesDto(
    @SerialName("posting:default:visibility") val defaultVisibility: String? = null,
    @SerialName("posting:default:sensitive") @Serializable(with = LenientBoolSerializer::class)
    val defaultSensitive: Boolean = false,
    @SerialName("posting:default:language") val defaultLanguage: String? = null,
    @SerialName("reading:expand:media") val expandMedia: String? = null,
    @SerialName("reading:expand:spoilers") @Serializable(with = LenientBoolSerializer::class)
    val expandSpoilers: Boolean = false,
)

@Serializable
internal data class NotificationPolicyDto(
    @SerialName("for_not_following") val forNotFollowing: String? = null,
    @SerialName("for_not_followers") val forNotFollowers: String? = null,
    @SerialName("for_new_accounts") val forNewAccounts: String? = null,
    @SerialName("for_private_mentions") val forPrivateMentions: String? = null,
    @SerialName("for_limited_accounts") val forLimitedAccounts: String? = null,
    @Serializable(with = PolicySummaryOrNull::class) val summary: NotificationPolicySummaryDto? = null,
)

@Serializable
internal data class NotificationPolicySummaryDto(
    @SerialName("pending_requests_count") @Serializable(with = LenientIntSerializer::class)
    val pendingRequestsCount: Int = 0,
    @SerialName("pending_notifications_count") @Serializable(with = LenientIntSerializer::class)
    val pendingNotificationsCount: Int = 0,
)

@Serializable
internal data class NotificationRequestDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val account: AccountDto,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
    @SerialName("notifications_count") @Serializable(with = LenientIntSerializer::class)
    val notificationsCount: Int = 0,
    @SerialName("last_status") @Serializable(with = StatusOrNull::class) val lastStatus: StatusDto? = null,
)

internal object PolicySummaryOrNull : KSerializer<NotificationPolicySummaryDto?> by
OrNullSerializer(NotificationPolicySummaryDto.serializer())

internal fun PreferencesDto.toDomain(): Preferences = Preferences(
    defaultVisibility = defaultVisibility?.let(Visibility::fromWire),
    defaultSensitive = defaultSensitive,
    defaultLanguage = defaultLanguage,
    expandMedia = expandMedia?.let(SensitiveMediaPolicy::fromWire),
    expandSpoilers = expandSpoilers,
)

internal fun NotificationPolicyDto.toDomain(): NotificationPolicy = NotificationPolicy(
    forNotFollowing = PolicyDecision.fromWire(forNotFollowing),
    forNotFollowers = PolicyDecision.fromWire(forNotFollowers),
    forNewAccounts = PolicyDecision.fromWire(forNewAccounts),
    forPrivateMentions = PolicyDecision.fromWire(forPrivateMentions),
    forLimitedAccounts = PolicyDecision.fromWire(forLimitedAccounts),
    summary = summary?.let { NotificationPolicySummary(it.pendingRequestsCount, it.pendingNotificationsCount) },
)

internal fun NotificationRequestDto.toDomain(): NotificationRequest =
    NotificationRequest(id, account.toDomain(), createdAt, updatedAt, notificationsCount, lastStatus?.toDomain())
