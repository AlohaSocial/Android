// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import social.aloha.core.model.InstanceDescription
import social.aloha.core.model.InstanceDocument
import social.aloha.core.model.InstanceRule
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer

/**
 * `/api/v2/instance`. v2 renames v1's `uri` to `domain`, makes `thumbnail` an object, moves contact and
 * registration fields under `contact` and `registrations`, and folds `urls` and `translation` into
 * `configuration`.
 */
@Serializable
internal data class InstanceV2Dto(
    val domain: String? = null,
    val title: String? = null,
    val version: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    val description: String? = null,
    @Serializable(with = ThumbnailOrNull::class) val thumbnail: ThumbnailDto? = null,
    @Serializable(with = LossyListSerializer::class) val languages: List<String>? = null,
    @Serializable(with = LossyListSerializer::class) val rules: List<InstanceRuleDto>? = null,
    @Serializable(with = ContactOrNull::class) val contact: ContactDto? = null,
    @Serializable(with = RegistrationsOrNull::class) val registrations: RegistrationsDto? = null,
    @Serializable(with = UsageOrNull::class) val usage: UsageDto? = null,
    @SerialName("api_versions") @Serializable(with = ApiVersionsOrNull::class)
    val apiVersions: Map<String, Int>? = null,
    @Serializable(with = ConfigurationOrNull::class) val configuration: InstanceConfigurationDto? = null,
)

/** `/api/v1/instance/`. */
@Serializable
internal data class InstanceV1Dto(
    val uri: String? = null,
    val title: String? = null,
    val version: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val description: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val thumbnail: String? = null,
    @Serializable(with = LossyListSerializer::class) val languages: List<String>? = null,
    @Serializable(with = LossyListSerializer::class) val rules: List<InstanceRuleDto>? = null,
    val email: String? = null,
    @SerialName("contact_account") @Serializable(with = AccountOrNull::class) val contactAccount: AccountDto? = null,
    @Serializable(with = LenientBoolSerializer::class) val registrations: Boolean = false,
    @SerialName("approval_required") @Serializable(with = LenientBoolSerializer::class)
    val approvalRequired: Boolean = false,
    @Serializable(with = StatsOrNull::class) val stats: StatsDto? = null,
    @Serializable(with = UrlsOrNull::class) val urls: InstanceUrlsDto? = null,
    @Serializable(with = ConfigurationOrNull::class) val configuration: InstanceConfigurationDto? = null,
)

@Serializable
internal data class InstanceRuleDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val text: String = "",
    val hint: String? = null,
)

@Serializable
internal data class InstanceDocumentDto(
    val content: String = "",
    @SerialName("updated_at") @Serializable(with = LenientInstantSerializer::class) val updatedAt: Instant? = null,
)

@Serializable
internal data class ThumbnailDto(@Serializable(with = LenientUrlSerializer::class) val url: String? = null)

@Serializable
internal data class ContactDto(
    val email: String? = null,
    @Serializable(with = AccountOrNull::class) val account: AccountDto? = null,
)

@Serializable
internal data class RegistrationsDto(
    @Serializable(with = LenientBoolSerializer::class) val enabled: Boolean = false,
    @SerialName("approval_required") @Serializable(with = LenientBoolSerializer::class)
    val approvalRequired: Boolean = false,
)

@Serializable
internal data class UsageDto(@Serializable(with = UsageUsersOrNull::class) val users: UsageUsersDto? = null)

@Serializable
internal data class UsageUsersDto(
    @SerialName("active_month") @Serializable(with = OptionalIntSerializer::class) val activeMonth: Int? = null,
)

@Serializable
internal data class StatsDto(
    @SerialName("user_count") @Serializable(with = OptionalIntSerializer::class) val userCount: Int? = null,
    @SerialName("status_count") @Serializable(with = OptionalIntSerializer::class) val statusCount: Int? = null,
    @SerialName("domain_count") @Serializable(with = OptionalIntSerializer::class) val domainCount: Int? = null,
)

internal object ThumbnailOrNull : KSerializer<ThumbnailDto?> by OrNullSerializer(ThumbnailDto.serializer())

internal object ContactOrNull : KSerializer<ContactDto?> by OrNullSerializer(ContactDto.serializer())

internal object RegistrationsOrNull : KSerializer<RegistrationsDto?> by OrNullSerializer(RegistrationsDto.serializer())

internal object UsageOrNull : KSerializer<UsageDto?> by OrNullSerializer(UsageDto.serializer())

internal object UsageUsersOrNull : KSerializer<UsageUsersDto?> by OrNullSerializer(UsageUsersDto.serializer())

internal object StatsOrNull : KSerializer<StatsDto?> by OrNullSerializer(StatsDto.serializer())

internal object ApiVersionsOrNull : KSerializer<Map<String, Int>?> by
OrNullSerializer(MapSerializer(String.serializer(), Int.serializer()))

internal fun InstanceV2Dto.toDomain(): InstanceDescription = InstanceDescription(
    domain = domain.orEmpty(),
    title = title.orEmpty(),
    version = version.orEmpty(),
    sourceUrl = sourceUrl,
    shortDescription = description.orEmpty(),
    description = description.orEmpty(),
    thumbnail = thumbnail?.url,
    languages = languages.orEmpty(),
    rules = rules.orEmpty().map { it.toDomain() },
    contactAccount = contact?.account?.toDomain(),
    contactEmail = contact?.email,
    registrationsEnabled = registrations?.enabled ?: false,
    approvalRequired = registrations?.approvalRequired ?: false,
    userCount = usage?.users?.activeMonth,
    streamingUrl = configuration?.urls?.resolved,
    vapidKey = configuration?.vapid?.publicKey,
    translationEnabled = configuration?.translation?.enabled ?: false,
    apiVersions = apiVersions.orEmpty(),
    limits = configuration.toLimits(),
)

internal fun InstanceV1Dto.toDomain(): InstanceDescription = InstanceDescription(
    domain = uri.orEmpty(),
    title = title.orEmpty(),
    version = version.orEmpty(),
    shortDescription = shortDescription.orEmpty(),
    description = description.orEmpty(),
    thumbnail = thumbnail,
    languages = languages.orEmpty(),
    rules = rules.orEmpty().map { it.toDomain() },
    contactAccount = contactAccount?.toDomain(),
    contactEmail = email,
    registrationsEnabled = registrations,
    approvalRequired = approvalRequired,
    userCount = stats?.userCount,
    statusCount = stats?.statusCount,
    domainCount = stats?.domainCount,
    streamingUrl = urls?.resolved ?: configuration?.urls?.resolved,
    vapidKey = configuration?.vapid?.publicKey,
    translationEnabled = configuration?.translation?.enabled ?: false,
    limits = configuration.toLimits(),
)

internal fun InstanceRuleDto.toDomain(): InstanceRule = InstanceRule(id, text, hint)

internal fun InstanceDocumentDto.toDomain(): InstanceDocument = InstanceDocument(content, updatedAt)
