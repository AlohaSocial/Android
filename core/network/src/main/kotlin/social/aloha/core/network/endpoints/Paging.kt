// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.network.QueryItem

/**
 * Where a page request starts from. A fetch is always anchored: `min_id` from the newest cached entry,
 * `max_id` from the oldest, or a cold load. Following a `Link` cursor replays its URL verbatim instead.
 */
public sealed interface PageAnchor {
    public data object Cold : PageAnchor

    public data class OlderThan(val id: String) : PageAnchor

    public data class NewerThan(val id: String) : PageAnchor

    public data class ImmediatelyAfter(val id: String) : PageAnchor
}

internal fun PageAnchor.queryItems(): List<QueryItem> = when (this) {
    PageAnchor.Cold -> emptyList()
    is PageAnchor.OlderThan -> listOf(QueryItem("max_id", id))
    is PageAnchor.NewerThan -> listOf(QueryItem("min_id", id))
    is PageAnchor.ImmediatelyAfter -> listOf(QueryItem("since_id", id))
}

/** Page sizes. The server caps most routes at 50 whatever is asked, and answers `limit=0` with a 400. */
public object Paging {
    public const val DEFAULT_LIMIT: Int = 20
    public const val MAXIMUM_LIMIT: Int = 50

    /** A `limit` between 1 and [maximum], so `limit=0` is never sent. */
    internal fun limitItem(limit: Int, maximum: Int = MAXIMUM_LIMIT): QueryItem =
        QueryItem("limit", limit.coerceIn(1, maximum).toString())

    internal fun pageItems(limit: Int, anchor: PageAnchor): List<QueryItem> =
        listOf(limitItem(limit)) + anchor.queryItems()
}
