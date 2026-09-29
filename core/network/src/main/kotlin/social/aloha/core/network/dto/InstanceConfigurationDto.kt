// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.ServerLimits
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer

/**
 * An instance's `configuration`. Each block is decoded on its own, so one odd block leaves only its own
 * limits at Mastodon's defaults.
 */
@Serializable
internal data class InstanceConfigurationDto(
    @Serializable(with = UrlsOrNull::class) val urls: InstanceUrlsDto? = null,
    @Serializable(with = StatusesConfigOrNull::class) val statuses: StatusesConfigDto? = null,
    @SerialName("media_attachments") @Serializable(with = MediaConfigOrNull::class)
    val mediaAttachments: MediaConfigDto? = null,
    @Serializable(with = PollsConfigOrNull::class) val polls: PollsConfigDto? = null,
    @Serializable(with = AccountsConfigOrNull::class) val accounts: AccountsConfigDto? = null,
    @Serializable(with = TranslationConfigOrNull::class) val translation: TranslationConfigDto? = null,
    @Serializable(with = VapidOrNull::class) val vapid: VapidDto? = null,
)

/**
 * `urls`, an empty object on Nextcloud Social, which is the signal that there is no streaming. v1 spells
 * the key `streaming_api` and Mastodon 4.x's v2 `configuration.urls` spells it `streaming`; both are read.
 */
@Serializable
internal data class InstanceUrlsDto(
    @SerialName("streaming_api") @Serializable(with = LenientUrlSerializer::class) val streamingApi: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val streaming: String? = null,
) {
    val resolved: String? get() = streaming ?: streamingApi
}

@Serializable
internal data class StatusesConfigDto(
    @SerialName("max_characters") @Serializable(with = OptionalIntSerializer::class) val maxCharacters: Int? = null,
    @SerialName("max_media_attachments") @Serializable(with = OptionalIntSerializer::class)
    val maxMediaAttachments: Int? = null,
    @SerialName("characters_reserved_per_url") @Serializable(with = OptionalIntSerializer::class)
    val charactersReservedPerUrl: Int? = null,
)

@Serializable
internal data class MediaConfigDto(
    @SerialName("supported_mime_types") @Serializable(with = LossyListSerializer::class)
    val supportedMimeTypes: List<String>? = null,
    @SerialName("image_size_limit") val imageSizeLimit: Long? = null,
    @SerialName("video_size_limit") val videoSizeLimit: Long? = null,
)

@Serializable
internal data class PollsConfigDto(
    @SerialName("max_options") @Serializable(with = OptionalIntSerializer::class) val maxOptions: Int? = null,
    @SerialName("max_characters_per_option") @Serializable(with = OptionalIntSerializer::class)
    val maxCharactersPerOption: Int? = null,
    @SerialName("min_expiration") val minExpiration: Long? = null,
    @SerialName("max_expiration") val maxExpiration: Long? = null,
)

@Serializable
internal data class AccountsConfigDto(
    @SerialName("max_featured_tags") @Serializable(with = OptionalIntSerializer::class)
    val maxFeaturedTags: Int? = null,
)

@Serializable
internal data class TranslationConfigDto(
    @Serializable(with = LenientBoolSerializer::class) val enabled: Boolean = false,
)

@Serializable
internal data class VapidDto(@SerialName("public_key") val publicKey: String? = null)

internal object UrlsOrNull : KSerializer<InstanceUrlsDto?> by OrNullSerializer(InstanceUrlsDto.serializer())

internal object ConfigurationOrNull : KSerializer<InstanceConfigurationDto?> by
OrNullSerializer(InstanceConfigurationDto.serializer())

internal object StatusesConfigOrNull : KSerializer<StatusesConfigDto?> by OrNullSerializer(
    StatusesConfigDto.serializer(),
)

internal object MediaConfigOrNull : KSerializer<MediaConfigDto?> by OrNullSerializer(MediaConfigDto.serializer())

internal object PollsConfigOrNull : KSerializer<PollsConfigDto?> by OrNullSerializer(PollsConfigDto.serializer())

internal object AccountsConfigOrNull : KSerializer<AccountsConfigDto?> by OrNullSerializer(
    AccountsConfigDto.serializer(),
)

internal object TranslationConfigOrNull : KSerializer<TranslationConfigDto?> by
OrNullSerializer(TranslationConfigDto.serializer())

internal object VapidOrNull : KSerializer<VapidDto?> by OrNullSerializer(VapidDto.serializer())

/** The limits a configuration states, each one falling back to Mastodon's default on its own. */
internal fun InstanceConfigurationDto?.toLimits(): ServerLimits = ServerLimits.MastodonDefaults
    .with(this?.statuses)
    .with(this?.mediaAttachments)
    .with(this?.polls)
    .with(this?.accounts)

private fun ServerLimits.with(statuses: StatusesConfigDto?): ServerLimits = copy(
    maxStatusCharacters = statuses?.maxCharacters ?: maxStatusCharacters,
    maxMediaAttachments = statuses?.maxMediaAttachments ?: maxMediaAttachments,
    charactersReservedPerUrl = statuses?.charactersReservedPerUrl ?: charactersReservedPerUrl,
)

private fun ServerLimits.with(media: MediaConfigDto?): ServerLimits = copy(
    imageSizeLimit = media?.imageSizeLimit ?: imageSizeLimit,
    videoSizeLimit = media?.videoSizeLimit ?: videoSizeLimit,
    supportedMimeTypes = media?.supportedMimeTypes ?: supportedMimeTypes,
)

private fun ServerLimits.with(polls: PollsConfigDto?): ServerLimits = copy(
    maxPollOptions = polls?.maxOptions ?: maxPollOptions,
    maxPollOptionCharacters = polls?.maxCharactersPerOption ?: maxPollOptionCharacters,
    minPollExpiration = polls?.minExpiration ?: minPollExpiration,
    maxPollExpiration = polls?.maxExpiration ?: maxPollExpiration,
)

private fun ServerLimits.with(accounts: AccountsConfigDto?): ServerLimits =
    copy(maxFeaturedTags = accounts?.maxFeaturedTags ?: maxFeaturedTags)
