// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Account
import social.aloha.core.model.DirectoryHashtagResults
import social.aloha.core.model.DirectorySearchResults
import social.aloha.core.model.DirectorySource
import social.aloha.core.model.DiscoverCategory
import social.aloha.core.model.FollowGraph
import social.aloha.core.model.FollowGraphStatus
import social.aloha.core.model.StarterPack
import social.aloha.core.model.Status
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.decoding.ListOrMemberSerializer
import social.aloha.core.network.decoding.LossyListSerializer
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.DirectoryResultsSerializer
import social.aloha.core.network.dto.DirectorySourceDto
import social.aloha.core.network.dto.DiscoverCategoryDto
import social.aloha.core.network.dto.FollowGraphSerializer
import social.aloha.core.network.dto.FollowGraphStatusDto
import social.aloha.core.network.dto.StarterPackDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.TagDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.dto.toHashtagResults
import social.aloha.core.network.dto.toSearchResults
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/** How a discover grid narrows its trending posts. */
public enum class DiscoverMedia(public val wire: String) {
    Images("image"),
    Videos("video"),
}

/**
 * Starter packs, other servers' directories, the follow graph and Pixelfed's discover surface, all
 * Nextcloud Social's own.
 */
public object DiscoveryEndpoints {
    private const val MAXIMUM_DIRECTORY = 40
    private const val MAXIMUM_DISCOVER = 60
    private const val DEFAULT_DISCOVER = 30

    public fun starterPacks(): ApiRequest<List<StarterPack>> =
        request(Endpoint("api/v1/starter_packs"), LossyListSerializer(StarterPackDto.serializer())) { list ->
            list.mapNotNull { it.toDomain() }
        }

    public fun starterPack(slug: String): ApiRequest<StarterPack?> =
        request(Endpoint("api/v1/starter_packs/$slug"), StarterPackDto.serializer()) { it.toDomain() }

    public fun followStarterPack(slug: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/starter_packs/$slug/follow", HttpMethod.POST))

    /** The directories the server asks when searching beyond itself. */
    public fun directories(): ApiRequest<List<DirectorySource>> =
        listRequest(Endpoint("api/v1/directories"), DirectorySourceDto.serializer()) { it.toDomain() }

    public fun directorySearch(
        query: String,
        source: String? = null,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): ApiRequest<DirectorySearchResults> = request(
        Endpoint(
            "api/v1/directories/search",
            query =
                listOf(QueryItem("q", query), Paging.limitItem(limit, MAXIMUM_DIRECTORY)) +
                    queryOf("source", source),
        ),
        DirectoryResultsSerializer(AccountDto.serializer(), "accounts"),
    ) { it.toSearchResults() }

    public fun directoryHashtags(
        query: String? = null,
        source: String? = null,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): ApiRequest<DirectoryHashtagResults> = request(
        Endpoint(
            "api/v1/directories/hashtags",
            query = listOf(Paging.limitItem(limit, MAXIMUM_DIRECTORY)) +
                queryOf("q", query?.takeIf { it.isNotEmpty() }) + queryOf("source", source),
        ),
        DirectoryResultsSerializer(TagDto.serializer(), "hashtags", "tags"),
    ) { it.toHashtagResults() }

    /** Accounts the people the reader follows also follow. */
    public fun followGraph(): ApiRequest<FollowGraph> =
        request(Endpoint("api/v1/follow_graph"), FollowGraphSerializer) {
            it
        }

    public fun followGraphStatus(): ApiRequest<FollowGraphStatus> =
        request(Endpoint("api/v1/follow_graph/status"), FollowGraphStatusDto.serializer()) { it.toDomain() }

    /** Trending posts narrowed to pictures or video (Pixelfed's discover). */
    public fun discoverPosts(media: DiscoverMedia, limit: Int = DEFAULT_DISCOVER): ApiRequest<List<Status>> =
        listRequest(
            Endpoint(
                "api/v2/discover/posts",
                query = listOf(QueryItem("media", media.wire), Paging.limitItem(limit, MAXIMUM_DISCOVER)),
            ),
            StatusDto.serializer(),
        ) { it.toDomain() }

    /** Curated subjects, sent bare or as `{categories: [...]}`. */
    public fun categories(): ApiRequest<List<DiscoverCategory>> = request(
        Endpoint("api/v1.1/discover/categories"),
        ListOrMemberSerializer(DiscoverCategoryDto.serializer(), "categories"),
    ) { list -> list.map { it.toDomain() } }

    public fun popularAccounts(limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<Account>> = listRequest(
        Endpoint("api/v1.1/discover/accounts/popular", query = listOf(Paging.limitItem(limit))),
        AccountDto.serializer(),
    ) { it.toDomain() }

    /** Mastodon's trending posts, the picture and video grids' fallback where Pixelfed's discover is absent. */
    public fun trendingStatuses(limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<Status>> = listRequest(
        Endpoint("api/v1/trends/statuses", query = listOf(Paging.limitItem(limit, MAXIMUM_DIRECTORY))),
        StatusDto.serializer(),
    ) { it.toDomain() }
}
