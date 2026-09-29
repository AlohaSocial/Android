// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.DeliveryReport
import social.aloha.core.model.GifLibrary
import social.aloha.core.model.QuoteApprovalPolicy
import social.aloha.core.model.Reaction
import social.aloha.core.model.Status
import social.aloha.core.model.StatusPlace
import social.aloha.core.model.TeamAccount
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.ListOrMemberSerializer
import social.aloha.core.network.dto.DeliveryReportDto
import social.aloha.core.network.dto.GifLibraryDto
import social.aloha.core.network.dto.ReactionDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.StatusPlaceDto
import social.aloha.core.network.dto.TeamAccountDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

private fun statusList(endpoint: Endpoint): ApiRequest<List<Status>> =
    listRequest(endpoint, StatusDto.serializer()) { it.toDomain() }

/**
 * Nextcloud Social's per-status extras: archive, delivery, quotes and dislikes. A stock Mastodon
 * answers 404 to all of them, so each sits behind a capability.
 */
public object StatusExtraEndpoints {
    /** Off the profile, not deleted, not federated (Pixelfed's archive). */
    public fun archive(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/pixelfed/v1/archive/add/$id", HttpMethod.POST))

    public fun unarchive(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/pixelfed/v1/archive/remove/$id", HttpMethod.POST))

    public fun archived(
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Status>> =
        statusList(Endpoint("api/pixelfed/v1/archive/list", query = Paging.pageItems(limit, anchor)))

    /**
     * The reactions to a status. Nextcloud Social stores and returns them here only; the status
     * entity always carries an empty `reactions` list, for every viewer.
     */
    public fun reactions(id: String): ApiRequest<List<Reaction>> =
        listRequest(Endpoint("api/v1/statuses/$id/reactions"), ReactionDto.serializer()) { it.toDomain() }

    /** Author only: where the post got to, server by server. */
    public fun delivery(id: String): ApiRequest<DeliveryReport> =
        request(Endpoint("api/v1/statuses/$id/delivery"), DeliveryReportDto.serializer()) { it.toDomain() }

    public fun quotes(
        id: String,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Status>> =
        statusList(Endpoint("api/v1/statuses/$id/quotes", query = Paging.pageItems(limit, anchor)))

    public fun setQuotePolicy(id: String, policy: QuoteApprovalPolicy): ApiRequest<Status> = request(
        Endpoint(
            "api/v1/statuses/$id/interaction_policy",
            HttpMethod.PUT,
            body = Body.Form(listOf(QueryItem("quote_approval_policy", policy.wire))),
        ),
        StatusDto.serializer(),
    ) { it.toDomain() }

    /** Withdraws a quote already made; the quoting post stays, shown as withdrawn on its own server. */
    public fun revokeQuote(id: String, quotingId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/statuses/$id/quotes/$quotingId/revoke", HttpMethod.POST))

    /**
     * PeerTube's thumbs-down. The server publishes `dislikes_count` and `disliked` on a video but
     * wires no route to them yet, so this answers 404 until it does.
     */
    public fun dislike(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/statuses/$id/dislike", HttpMethod.POST))

    public fun undislike(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/statuses/$id/undislike", HttpMethod.POST))
}

/** Places a post can be tagged with: names only, no map service on either side. */
public object PlaceEndpoints {
    private const val MAXIMUM_SEARCH = 40

    public fun search(query: String, limit: Int = 10): ApiRequest<List<StatusPlace>> = listRequest(
        Endpoint(
            "api/v1/places/search",
            query = listOf(QueryItem("q", query), Paging.limitItem(limit, MAXIMUM_SEARCH)),
        ),
        StatusPlaceDto.serializer(),
    ) { it.toDomain() }

    public fun place(id: String): ApiRequest<StatusPlace> =
        request(Endpoint("api/v1/places/$id"), StatusPlaceDto.serializer()) { it.toDomain() }

    public fun statuses(
        id: String,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Status>> =
        statusList(Endpoint("api/v1/places/$id/statuses", query = Paging.pageItems(limit, anchor)))
}

/** The instance's own library of animated pictures; nothing about a search leaves the server. */
public object GifEndpoints {
    private const val MAXIMUM_LIMIT = 200
    private const val DEFAULT_LIMIT = 40

    public fun library(query: String?, limit: Int = DEFAULT_LIMIT, offset: Int = 0): ApiRequest<GifLibrary> = request(
        Endpoint(
            "api/v1/gifs",
            query = queryOf("q", query?.takeIf { it.isNotEmpty() }) +
                listOf(Paging.limitItem(limit, MAXIMUM_LIMIT), QueryItem("offset", offset.coerceAtLeast(0).toString())),
        ),
        GifLibraryDto.serializer(),
    ) { it.toDomain() }
}

/** Group accounts the viewer may post as, sent as `{teams: [...]}`. */
public object TeamEndpoints {
    public fun all(): ApiRequest<List<TeamAccount>> =
        request(Endpoint("api/v1.1/teams"), ListOrMemberSerializer(TeamAccountDto.serializer(), "teams")) { list ->
            list.map { it.toDomain() }.filter { it.handle.isNotEmpty() }
        }
}
