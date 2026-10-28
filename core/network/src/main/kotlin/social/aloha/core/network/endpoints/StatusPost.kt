// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import social.aloha.core.model.Visibility
import social.aloha.core.network.QueryItem
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery

/**
 * What the composer sends. The Nextcloud Social extras are optional and a stock server ignores them.
 *
 * @property idempotencyKey generated when the composer opened and regenerated only when the content
 *   changes; what makes a retry after a timeout safe.
 * @property pollExpiresInSeconds a day when not set.
 * @property quotedId the post being quoted.
 * @property quotePolicy who may quote the new post: `public`, `followers` or `nobody`.
 * @property placeId a place the server already knows; otherwise [placeName] and [placeCountry] make one.
 * @property postAs a team account handle to post as; null posts as yourself.
 * @property mediaAttributes the descriptions and focal points of media already attached, sent with an
 *   edit: once attached, a medium cannot be changed on its own.
 * @property keepsMedia an edit that leaves the post's media as they were, so [mediaIds] is not sent:
 *   Nextcloud Social 0.26.279 serves media an edit named without their ids, and a later edit naming
 *   them by those empty ids is refused.
 */
public data class StatusPost(
    val text: String,
    val visibility: Visibility = Visibility.Public,
    val spoilerText: String? = null,
    val sensitive: Boolean = false,
    val language: String? = null,
    val inReplyToId: String? = null,
    val mediaIds: List<String> = emptyList(),
    val pollOptions: List<String> = emptyList(),
    val pollExpiresInSeconds: Long? = null,
    val pollMultiple: Boolean = false,
    val pollHideTotals: Boolean = false,
    val scheduledAt: Instant? = null,
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val quotedId: String? = null,
    val quotePolicy: String? = null,
    val placeId: String? = null,
    val placeName: String? = null,
    val placeCountry: String? = null,
    val placeLatitude: Double? = null,
    val placeLongitude: Double? = null,
    val postAs: String? = null,
    val videoTitle: String? = null,
    val videoCategory: String? = null,
    val videoLicence: String? = null,
    val contentType: String? = null,
    val mediaAttributes: List<MediaAttribute> = emptyList(),
    val keepsMedia: Boolean = false,
) {
    internal fun formItems(): List<QueryItem> = listOf(
        QueryItem("status", text),
        QueryItem("visibility", visibility.wire),
        QueryItem("sensitive", sensitive.toString()),
    ) + queryOf("spoiler_text", spoilerText) + queryOf("language", language) +
        queryOf("in_reply_to_id", inReplyToId) +
        repeatedQuery("media_ids", mediaIds) + pollItems() + queryOf("scheduled_at", scheduledAt?.toString()) +
        mediaAttributes.flatMap(MediaAttribute::items) + extras()

    /**
     * The post as an edit sends it. JSON, as a form cannot say an empty list: `media_ids` is the post's
     * whole attachment list in order, and present but empty takes every attachment off (Nextcloud Social
     * 0.26.279, Mastodon); left out, unless the media changed, it keeps them. A warning left out is kept
     * as it was, so an edit without one sends it empty.
     */
    internal fun editJson(): JsonObject = buildJsonObject {
        put("status", text)
        put("visibility", visibility.wire)
        put("spoiler_text", spoilerText.orEmpty())
        put("sensitive", sensitive)
        language?.let { put("language", it) }
        if (!keepsMedia) putJsonArray("media_ids") { mediaIds.forEach(::add) }
        if (mediaAttributes.isNotEmpty()) {
            putJsonArray("media_attributes") { mediaAttributes.forEach { addJsonObject { it.fill(this) } } }
        }
        putPoll()
        quotedId?.let {
            put("quoted_id", it)
            put("quote_id", it)
        }
        quotePolicy?.let { put("quote_policy", it) }
        contentType?.let { put("content_type", it) }
    }

    private fun JsonObjectBuilder.putPoll() {
        if (pollOptions.isEmpty() || mediaIds.isNotEmpty()) return
        putJsonObject("poll") {
            putJsonArray("options") { pollOptions.forEach(::add) }
            put("expires_in", pollExpiresInSeconds ?: DEFAULT_POLL_SECONDS)
            put("multiple", pollMultiple)
            put("hide_totals", pollHideTotals)
        }
    }

    /** A poll and media are mutually exclusive, as Mastodon requires; with media, the poll is dropped. */
    private fun pollItems(): List<QueryItem> {
        if (pollOptions.isEmpty() || mediaIds.isNotEmpty()) return emptyList()
        return repeatedQuery("poll[options]", pollOptions) + listOf(
            QueryItem("poll[expires_in]", (pollExpiresInSeconds ?: DEFAULT_POLL_SECONDS).toString()),
            QueryItem("poll[multiple]", pollMultiple.toString()),
            QueryItem("poll[hide_totals]", pollHideTotals.toString()),
        )
    }

    /**
     * Nextcloud Social's extras. The web composer names the quote `quote_id` and the controller reads
     * `quoted_id`, so both go; a server that knows neither ignores both.
     */
    private fun extras(): List<QueryItem> =
        queryOf("quoted_id", quotedId) + queryOf("quote_id", quotedId) + queryOf("quote_policy", quotePolicy) +
            placeItems() +
            queryOf("post_as", postAs) + queryOf("video_title", videoTitle) + queryOf("video_category", videoCategory) +
            queryOf("video_licence", videoLicence) + queryOf("content_type", contentType)

    private fun placeItems(): List<QueryItem> = when {
        placeId != null -> listOf(QueryItem("place_id", placeId))

        !placeName.isNullOrEmpty() ->
            listOf(QueryItem("place_name", placeName)) + queryOf("place_country", placeCountry) +
                queryOf("place_lat", placeLatitude?.toString()) + queryOf("place_lon", placeLongitude?.toString())

        else -> emptyList()
    }

    private companion object {
        const val DEFAULT_POLL_SECONDS = 86_400L
    }
}

/** A description and focal point for medium [id], `x,y` in −1…1 as Mastodon has it. */
public data class MediaAttribute(val id: String, val description: String, val focus: Pair<Double, Double>? = null) {
    private val focusText: String?
        get() = focus?.let { (x, y) -> String.format(Locale.ROOT, "%.2f,%.2f", x, y) }

    internal fun items(): List<QueryItem> = listOf(
        QueryItem("media_attributes[][id]", id),
        QueryItem("media_attributes[][description]", description),
    ) + queryOf("media_attributes[][focus]", focusText)

    internal fun fill(entry: JsonObjectBuilder) {
        entry.put("id", id)
        entry.put("description", description)
        focusText?.let { entry.put("focus", it) }
    }
}
