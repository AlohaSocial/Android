// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.GroupedNotifications
import social.aloha.core.model.Notification
import social.aloha.core.model.NotificationGroup
import social.aloha.core.model.NotificationKind
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer

@Serializable
internal data class NotificationDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val type: String? = null,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    val account: AccountDto,
    @Serializable(with = StatusOrNull::class) val status: StatusDto? = null,
)

@Serializable
internal data class GroupedNotificationsDto(
    @Serializable(with = LossyListSerializer::class) val accounts: List<AccountDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val statuses: List<StatusDto> = emptyList(),
    @SerialName("notification_groups") @Serializable(with = LossyListSerializer::class)
    val notificationGroups: List<NotificationGroupDto> = emptyList(),
)

@Serializable
internal data class NotificationGroupDto(
    @SerialName("group_key") val groupKey: String? = null,
    @SerialName("notifications_count") @Serializable(with = LenientIntSerializer::class)
    val notificationsCount: Int = 0,
    val type: String? = null,
    @SerialName("most_recent_notification_id") @Serializable(with = OptionalIdSerializer::class)
    val mostRecentNotificationId: String? = null,
    @SerialName("page_min_id") @Serializable(with = OptionalIdSerializer::class) val pageMinId: String? = null,
    @SerialName("page_max_id") @Serializable(with = OptionalIdSerializer::class) val pageMaxId: String? = null,
    @SerialName("latest_page_notification_at") @Serializable(with = LenientInstantSerializer::class)
    val latestPageNotificationAt: Instant? = null,
    @SerialName("sample_account_ids") @Serializable(with = FlexibleIdListSerializer::class)
    val sampleAccountIds: List<String> = emptyList(),
    @SerialName("status_id") @Serializable(with = OptionalIdSerializer::class) val statusId: String? = null,
)

/** A list of ids that may arrive as numbers or strings; a malformed one is dropped. */
internal object FlexibleIdListSerializer : KSerializer<List<String>> by LossyListSerializer(FlexibleIdSerializer)

internal fun NotificationDto.toDomain(): Notification = Notification(
    id = id,
    type = NotificationKind.fromWire(type),
    account = account.toDomain(),
    createdAt = createdAt ?: Instant.EPOCH,
    status = status?.toDomain(),
)

internal fun GroupedNotificationsDto.toDomain(): GroupedNotifications = GroupedNotifications(
    accounts = accounts.map { it.toDomain() },
    statuses = statuses.map { it.toDomain() },
    notificationGroups = notificationGroups.map { it.toDomain() },
)

internal fun NotificationGroupDto.toDomain(): NotificationGroup = NotificationGroup(
    groupKey = groupKey.orEmpty(),
    notificationsCount = notificationsCount,
    type = NotificationKind.fromWire(type),
    mostRecentNotificationId = mostRecentNotificationId.orEmpty(),
    pageMinId = pageMinId,
    pageMaxId = pageMaxId,
    latestPageNotificationAt = latestPageNotificationAt,
    sampleAccountIds = sampleAccountIds,
    statusId = statusId,
)
