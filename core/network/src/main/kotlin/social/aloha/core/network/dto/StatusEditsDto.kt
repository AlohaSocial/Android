// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.ScheduledStatus
import social.aloha.core.model.ScheduledStatusParams
import social.aloha.core.model.StatusEdit
import social.aloha.core.model.StatusSource
import social.aloha.core.model.Translation
import social.aloha.core.model.Visibility
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalIdSerializer

@Serializable
internal data class TranslationDto(
    val content: String = "",
    @SerialName("spoiler_text") val spoilerText: String? = null,
    @SerialName("detected_source_language") val detectedSourceLanguage: String? = null,
    val provider: String? = null,
)

@Serializable
internal data class StatusEditDto(
    val account: AccountDto,
    val content: String? = null,
    @SerialName("spoiler_text") val spoilerText: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val sensitive: Boolean = false,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("media_attachments") @Serializable(with = LossyListSerializer::class)
    val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
)

@Serializable
internal data class StatusSourceDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val text: String = "",
    @SerialName("spoiler_text") val spoilerText: String = "",
)

@Serializable
internal data class ScheduledStatusDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    val params: ScheduledStatusParamsDto,
    @SerialName("scheduled_at") @Serializable(with = LenientInstantSerializer::class) val scheduledAt: Instant? = null,
    @SerialName("media_attachments") @Serializable(with = LossyListSerializer::class)
    val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
)

@Serializable
internal data class ScheduledStatusParamsDto(
    val text: String? = null,
    val visibility: String? = null,
    @SerialName("spoiler_text") val spoilerText: String? = null,
    @Serializable(with = LenientBoolSerializer::class) val sensitive: Boolean = false,
    val language: String? = null,
    @SerialName("in_reply_to_id") @Serializable(with = OptionalIdSerializer::class) val inReplyToId: String? = null,
)

internal fun TranslationDto.toDomain(): Translation =
    Translation(content, spoilerText, detectedSourceLanguage, provider)

internal fun StatusEditDto.toDomain(): StatusEdit = StatusEdit(
    account = account.toDomain(),
    content = content.orEmpty(),
    spoilerText = spoilerText.orEmpty(),
    sensitive = sensitive,
    createdAt = createdAt ?: Instant.EPOCH,
    mediaAttachments = mediaAttachments.map { it.toDomain() },
)

internal fun StatusSourceDto.toDomain(): StatusSource = StatusSource(id, text, spoilerText)

internal fun ScheduledStatusDto.toDomain(): ScheduledStatus = ScheduledStatus(
    id = id,
    params = ScheduledStatusParams(
        text = params.text,
        visibility = params.visibility?.let(Visibility::fromWire),
        spoilerText = params.spoilerText,
        sensitive = params.sensitive,
        language = params.language,
        inReplyToId = params.inReplyToId,
    ),
    scheduledAt = scheduledAt ?: Instant.EPOCH,
    mediaAttachments = mediaAttachments.map { it.toDomain() },
)
