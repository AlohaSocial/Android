// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Account
import social.aloha.core.model.Conversation
import social.aloha.core.model.Status
import social.aloha.core.model.Tag
import social.aloha.core.model.TimelineFilters
import social.aloha.core.model.TimelineSource
import social.aloha.core.model.UnreadCount
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.ConversationDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.UnreadCountDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.flagQuery
import social.aloha.core.network.listRequest
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

public object TimelineEndpoints {
    /**
     * A page of [source]. The [filters] are Nextcloud Social's narrowings; `only_video` wins over
     * `only_media` when both are set, since every video is media.
     */
    public fun timeline(
        source: TimelineSource,
        filters: TimelineFilters = TimelineFilters.None,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Status>> {
        val page = Paging.pageItems(limit, anchor)
        val items = if (source is TimelineSource.Account) {
            page + flagQuery("exclude_replies", !source.includeReplies) + flagQuery("only_media", source.onlyMedia)
        } else {
            page + flagQuery("local", source == TimelineSource.Local) + filterItems(filters)
        }
        return listRequest(Endpoint(path(source), query = items), StatusDto.serializer()) { it.toDomain() }
    }

    /** Sources with a route of their own rather than a `{timeline}` segment. */
    private val ownRoutes: Map<TimelineSource, String> = mapOf(
        TimelineSource.Bookmarks to "api/v1/bookmarks",
        TimelineSource.Favourites to "api/v1/favourites/",
        TimelineSource.Trending to "api/v1/trends/statuses",
    )

    private fun path(source: TimelineSource): String = when (source) {
        is TimelineSource.List -> "api/v1/timelines/list/${source.id}"
        is TimelineSource.Hashtag -> "api/v1/timelines/tag/${Tag.normalise(source.name) ?: source.name}"
        is TimelineSource.Account -> "api/v1/accounts/${source.id}/statuses"
        else -> ownRoutes[source] ?: "api/v1/timelines/${source.pathSegment}/"
    }

    private fun filterItems(filters: TimelineFilters): List<QueryItem> = if (filters.onlyVideo) {
        flagQuery("only_video", true)
    } else {
        flagQuery("only_media", filters.onlyMedia) + flagQuery("only_news", filters.onlyNews)
    }

    public fun conversations(
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Conversation>> = listRequest(
        Endpoint("api/v1/conversations", query = Paging.pageItems(limit, anchor)),
        ConversationDto.serializer(),
    ) {
        it.toDomain()
    }

    public fun markConversationRead(id: String): ApiRequest<Conversation> =
        request(Endpoint("api/v1/conversations/$id/read", HttpMethod.POST), ConversationDto.serializer()) {
            it.toDomain()
        }

    /** Marks every conversation read. */
    public fun markAllConversationsRead(): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/conversations/read_all", HttpMethod.POST))

    /** Takes a conversation off the list; its messages are not deleted, and a later message brings it back. */
    public fun deleteConversation(id: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/conversations/$id", HttpMethod.DELETE))

    /** How many conversations have something unread. */
    public fun conversationsUnreadCount(): ApiRequest<UnreadCount> =
        request(Endpoint("api/v1/conversations/unread_count"), UnreadCountDto.serializer()) { it.toDomain() }

    /**
     * The people a direct message can be started with: mutual follows, which is who Pixelfed's composer
     * offers. The rest of Pixelfed's `direct/thread` family is deliberately not used: it is a second
     * representation of the same direct statuses the Mastodon routes read and write.
     */
    public fun directMessageMutuals(): ApiRequest<List<Account>> =
        listRequest(Endpoint("api/v1.1/direct/compose/mutuals"), AccountDto.serializer()) { it.toDomain() }
}
