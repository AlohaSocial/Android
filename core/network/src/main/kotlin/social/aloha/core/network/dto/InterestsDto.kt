// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.dto

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import social.aloha.core.model.HeldPost
import social.aloha.core.model.HeldPostsPage
import social.aloha.core.model.InterestSettings
import social.aloha.core.model.InterestTag
import social.aloha.core.model.InterestsState
import social.aloha.core.model.SubscriptionEntry
import social.aloha.core.model.SubscriptionFeed
import social.aloha.core.model.WeeklyRecap
import social.aloha.core.network.decoding.FlexibleIdSerializer
import social.aloha.core.network.decoding.LenientBoolSerializer
import social.aloha.core.network.decoding.LenientInstantSerializer
import social.aloha.core.network.decoding.LenientIntSerializer
import social.aloha.core.network.decoding.LenientTextSerializer
import social.aloha.core.network.decoding.LenientUrlSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.decoding.OptionalBoolSerializer
import social.aloha.core.network.decoding.OptionalDoubleSerializer
import social.aloha.core.network.decoding.OptionalIntSerializer
import social.aloha.core.network.decoding.OrNullSerializer

/** The interests shape has grown on the server by accretion; every field tolerates absence. */
@Serializable
internal data class InterestsStateDto(
    @Serializable(with = InterestSettingsOrNull::class) val settings: InterestSettingsDto? = null,
    @Serializable(with = LossyListSerializer::class) val interests: List<InterestTagDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val candidates: List<InterestTagDto> = emptyList(),
    @Serializable(with = LenientBoolSerializer::class) val thin: Boolean = false,
)

/** Learning is on unless the server says otherwise. */
@Serializable
internal data class InterestSettingsDto(
    @Serializable(with = OptionalBoolSerializer::class) val learning: Boolean? = null,
    @Serializable(with = LenientBoolSerializer::class) val paused: Boolean = false,
    @Serializable(with = LossyListSerializer::class) val languages: List<String> = emptyList(),
)

internal object InterestSettingsOrNull : KSerializer<InterestSettingsDto?> by OrNullSerializer(
    InterestSettingsDto.serializer(),
)

/** A ranked tag, named `tag` or `name`, whose score may be a string. */
@Serializable
internal data class InterestTagDto(
    @Serializable(with = LenientTextSerializer::class) val tag: String? = null,
    @Serializable(with = LenientTextSerializer::class) val name: String? = null,
    @Serializable(with = OptionalDoubleSerializer::class) val score: Double? = null,
    @Serializable(with = LenientBoolSerializer::class) val pinned: Boolean = false,
)

internal fun InterestsStateDto.toDomain(): InterestsState = InterestsState(
    settings = settings?.let { InterestSettings(it.learning ?: true, it.paused, it.languages) } ?: InterestSettings(),
    interests = interests.map { it.toDomain() },
    candidates = candidates.map { it.toDomain() },
    thin = thin,
)

internal fun InterestTagDto.toDomain(): InterestTag = InterestTag(tag ?: name.orEmpty(), score ?: 0.0, pinned)

/** A feed's home page arrives as `site_url` or `link`, its count as `entry_count` or `entries`. */
@Serializable
internal data class SubscriptionFeedDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @Serializable(with = LenientTextSerializer::class) val title: String? = null,
    @SerialName("site_url") @Serializable(with = LenientUrlSerializer::class) val siteUrl: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val link: String? = null,
    @SerialName("entry_count") @Serializable(with = OptionalIntSerializer::class) val entryCount: Int? = null,
    @Serializable(with = OptionalIntSerializer::class) val entries: Int? = null,
    @Serializable(with = LenientTextSerializer::class) val error: String? = null,
    @SerialName("last_read_at") @Serializable(with = LenientInstantSerializer::class) val lastReadAt: Instant? = null,
)

internal fun SubscriptionFeedDto.toDomain(): SubscriptionFeed = SubscriptionFeed(
    id = id,
    url = url,
    title = title.orEmpty(),
    siteUrl = siteUrl ?: link,
    entryCount = entryCount ?: entries ?: 0,
    error = error,
    lastReadAt = lastReadAt,
)

@Serializable
internal data class SubscriptionEntryDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @Serializable(with = LenientTextSerializer::class) val title: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val url: String? = null,
    @Serializable(with = LenientUrlSerializer::class) val link: String? = null,
    @Serializable(with = LenientTextSerializer::class) val summary: String? = null,
    @Serializable(with = LenientInstantSerializer::class) val published: Instant? = null,
    @SerialName("published_at") @Serializable(with = LenientInstantSerializer::class) val publishedAt: Instant? = null,
    @SerialName("feed_title") @Serializable(with = LenientTextSerializer::class) val feedTitle: String? = null,
    @Serializable(with = LenientTextSerializer::class) val feed: String? = null,
)

internal fun SubscriptionEntryDto.toDomain(): SubscriptionEntry =
    SubscriptionEntry(id, title.orEmpty(), url ?: link, summary, published ?: publishedAt, feedTitle ?: feed)

/** A Google Takeout import answers with how many channels it subscribed to. */
@Serializable
internal data class TakeoutResultDto(@Serializable(with = LenientIntSerializer::class) val subscribed: Int = 0)

@Serializable
internal data class WeeklyRecapDto(
    @Serializable(with = LenientBoolSerializer::class) val enabled: Boolean = false,
    @SerialName("this_week") @Serializable(with = LenientIntSerializer::class) val thisWeek: Int = 0,
    @SerialName("last_week") @Serializable(with = LenientIntSerializer::class) val lastWeek: Int = 0,
)

internal fun WeeklyRecapDto.toDomain(): WeeklyRecap = WeeklyRecap(enabled, thisWeek, lastWeek)

@Serializable
internal data class HeldPostDto(
    @Serializable(with = FlexibleIdSerializer::class) val id: String,
    @SerialName("created_at") @Serializable(with = LenientInstantSerializer::class) val createdAt: Instant? = null,
    @SerialName("spoiler_text") @Serializable(with = LenientTextSerializer::class) val spoilerText: String? = null,
    @Serializable(with = LenientTextSerializer::class) val text: String? = null,
    @Serializable(with = LenientTextSerializer::class) val content: String? = null,
    @SerialName("media_count") @Serializable(with = LenientIntSerializer::class) val mediaCount: Int = 0,
    @Serializable(with = LenientTextSerializer::class) val reason: String? = null,
)

@Serializable
internal data class HeldPostsPageDto(
    @Serializable(with = LossyListSerializer::class) val held: List<HeldPostDto> = emptyList(),
    @Serializable(with = LossyListSerializer::class) val reasons: List<String> = emptyList(),
)

internal fun HeldPostsPageDto.toDomain(): HeldPostsPage = HeldPostsPage(
    held = held.map {
        HeldPost(
            it.id,
            it.createdAt,
            it.spoilerText.orEmpty(),
            it.text ?: it.content.orEmpty(),
            it.mediaCount,
            it.reason,
        )
    },
    reasons = reasons,
)
