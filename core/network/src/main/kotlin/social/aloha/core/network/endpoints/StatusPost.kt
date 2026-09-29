// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import java.time.Instant
import java.util.UUID
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
) {
    internal fun formItems(): List<QueryItem> = listOf(
        QueryItem("status", text),
        QueryItem("visibility", visibility.wire),
        QueryItem("sensitive", sensitive.toString()),
    ) + queryOf("spoiler_text", spoilerText) + queryOf("language", language) +
        queryOf("in_reply_to_id", inReplyToId) +
        repeatedQuery("media_ids", mediaIds) + pollItems() + queryOf("scheduled_at", scheduledAt?.toString()) +
        extras()

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
