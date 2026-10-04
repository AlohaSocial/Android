// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.Account
import social.aloha.core.model.AccountField
import social.aloha.core.model.AccountSource
import social.aloha.core.model.Visibility
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OrNullSerializer

@Serializable
internal data class AccountDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val username: String? = null,
    val acct: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val note: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val uri: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val avatar: String? = null,
    @SerialName("avatar_static") @Serializable(with = LenientUrlSerializer::class) val avatarStatic: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val header: String? = null,
    @SerialName("header_static") @Serializable(with = LenientUrlSerializer::class) val headerStatic: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val locked: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val bot: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val discoverable: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val indexable: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val suspended: Boolean = false,
    @Serializable(with = LenientBoolSerializer::class) val limited: Boolean = false,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("followers_count") @Serializable(with = LenientIntSerializer::class) val followersCount: Int = 0,
    @SerialName("following_count") @Serializable(with = LenientIntSerializer::class) val followingCount: Int = 0,
    @SerialName("statuses_count") @Serializable(with = LenientIntSerializer::class) val statusesCount: Int = 0,
    @SerialName("last_status_at") @Serializable(with = LenientInstantSerializer::class)
    val lastStatusAt: Instant? = null,
    @Serializable(with = LossyListSerializer::class) val fields: List<AccountFieldDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val emojis: List<CustomEmojiDto> = emptyList(),
    @Serializable(with = AccountSourceOrNull::class) val source: AccountSourceDto? = null,
    @Serializable(with = AccountOrNull::class) val moved: AccountDto? = null,
    @Serializable(with = LenientBoolSerializer::class) val memorial: Boolean = false,
)

@Serializable
internal data class AccountFieldDto(
    val name: String,
    val value: String = "",
    @SerialName("verified_at") @Serializable(with = LenientInstantSerializer::class) val verifiedAt: Instant? = null,
)

@Serializable
internal data class AccountSourceDto(
    val note: String? = null,
    @Serializable(with = LossyListSerializer::class) val fields: List<AccountFieldDto>? = null,
    val privacy: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val sensitive: Boolean = false,
    val language: String? = null,
    @SerialName("follow_requests_count") @Serializable(with = LenientIntSerializer::class)
    val followRequestsCount: Int = 0,
)

internal object AccountOrNull : KSerializer<AccountDto?> by OrNullSerializer(AccountDto.serializer())

internal object AccountSourceOrNull : KSerializer<AccountSourceDto?> by OrNullSerializer(AccountSourceDto.serializer())

internal fun AccountDto.toDomain(): Account {
    val name = username.orEmpty()
    return Account(
        id = id,
        username = name,
        acct = acct ?: name,
        displayName = displayName.orEmpty(),
        note = note.orEmpty(),
        url = url,
        uri = uri,
        avatar = avatar,
        avatarStatic = avatarStatic,
        header = header,
        headerStatic = headerStatic,
        locked = locked,
        bot = bot,
        discoverable = discoverable,
        indexable = indexable,
        suspended = suspended,
        limited = limited,
        createdAt = createdAt,
        followersCount = followersCount,
        followingCount = followingCount,
        statusesCount = statusesCount,
        lastStatusAt = lastStatusAt,
        fields = fields.map { it.toDomain() },
        emojis = emojis.map { it.toDomain() },
        source = source?.toDomain(),
        moved = moved?.toDomain(),
        memorial = memorial,
    )
}

internal fun AccountFieldDto.toDomain(): AccountField = AccountField(name, value, verifiedAt)

internal fun AccountSourceDto.toDomain(): AccountSource = AccountSource(
    note = note,
    fields = fields?.map { it.toDomain() },
    privacy = privacy?.let(Visibility::fromWire),
    sensitive = sensitive,
    language = language,
    followRequestsCount = followRequestsCount,
)
