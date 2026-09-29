// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Account
import social.aloha.core.model.AccountList
import social.aloha.core.model.Card
import social.aloha.core.model.ListRepliesPolicy
import social.aloha.core.model.SearchResults
import social.aloha.core.model.Suggestion
import social.aloha.core.model.Tag
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Authentication
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.AccountListDto
import social.aloha.core.network.dto.CardDto
import social.aloha.core.network.dto.SearchResultsDto
import social.aloha.core.network.dto.SuggestionDto
import social.aloha.core.network.dto.TagDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.flagQuery
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/** The order of the profile directory. */
public enum class DirectoryOrder(public val wire: String) {
    /** Most recently posted. */
    Active("active"),

    /** Most recently joined. */
    New("new"),
}

public object SearchEndpoints {
    private const val DIRECTORY_LIMIT = 40
    private const val TRENDS_LIMIT = 10
    private const val SUGGESTIONS_LIMIT = 20

    public fun search(
        query: String,
        type: String? = null,
        resolve: Boolean = false,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): ApiRequest<SearchResults> = request(
        Endpoint(
            "api/v2/search",
            query =
                listOf(QueryItem("q", query), Paging.limitItem(limit)) + queryOf("type", type) +
                    flagQuery("resolve", resolve),
        ),
        SearchResultsDto.serializer(),
    ) { it.toDomain() }

    /**
     * The instance's profile directory: the accounts here that chose to be discoverable. `local` is
     * accepted by the server and ignored, so it is not sent.
     */
    public fun directory(
        order: DirectoryOrder = DirectoryOrder.Active,
        limit: Int = DIRECTORY_LIMIT,
        offset: Int = 0,
    ): ApiRequest<List<Account>> = listRequest(
        Endpoint(
            "api/v1/directory",
            query = listOf(
                QueryItem("order", order.wire),
                Paging.limitItem(limit),
                QueryItem("offset", offset.coerceAtLeast(0).toString()),
            ),
            authentication = Authentication.None,
        ),
        AccountDto.serializer(),
    ) { it.toDomain() }

    public fun trendingTags(limit: Int = TRENDS_LIMIT, period: String = "1d"): ApiRequest<List<Tag>> = listRequest(
        Endpoint(
            "api/v1/trends/tags",
            query = listOf(Paging.limitItem(limit), QueryItem("period", period)),
            authentication = Authentication.None,
        ),
        TagDto.serializer(),
    ) { it.toDomain() }

    public fun trendingLinks(limit: Int = TRENDS_LIMIT): ApiRequest<List<Card>> = listRequest(
        Endpoint("api/v1/trends/links", query = listOf(Paging.limitItem(limit)), authentication = Authentication.None),
        CardDto.serializer(),
    ) { it.toDomain() }

    public fun suggestions(): ApiRequest<List<Suggestion>> = listRequest(
        Endpoint("api/v2/suggestions", query = listOf(Paging.limitItem(SUGGESTIONS_LIMIT))),
        SuggestionDto.serializer(),
    ) { it.toDomain() }

    public fun dismissSuggestion(accountId: String): ApiRequest<Unit> =
        unitRequest(Endpoint("api/v1/suggestions/$accountId", HttpMethod.DELETE))
}

/** Hashtag routes; every name is normalised the way the server stores it. */
public object TagEndpoints {
    public fun tag(name: String): ApiRequest<Tag> = tagRequest(Endpoint("api/v1/tags/${normalised(name)}"))

    public fun follow(name: String): ApiRequest<Tag> =
        tagRequest(Endpoint("api/v1/tags/${normalised(name)}/follow", HttpMethod.POST))

    public fun unfollow(name: String): ApiRequest<Tag> =
        tagRequest(Endpoint("api/v1/tags/${normalised(name)}/unfollow", HttpMethod.POST))

    /**
     * Followed tags. Pages on the followed-tag row id, not a status id: a tag can be unfollowed and
     * followed again, so its name cannot page.
     */
    public fun followed(
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Tag>> =
        listRequest(Endpoint("api/v1/followed_tags", query = Paging.pageItems(limit, anchor)), TagDto.serializer()) {
            it.toDomain()
        }

    /** Tags that travel with this one on public posts (Nextcloud Social). */
    public fun related(name: String, limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<Tag>> = listRequest(
        Endpoint("api/v1/tags/${normalised(name)}/related", query = listOf(Paging.limitItem(limit, RELATED_LIMIT))),
        TagDto.serializer(),
    ) { it.toDomain() }

    private const val RELATED_LIMIT = 40

    private fun normalised(name: String): String = Tag.normalise(name) ?: name

    private fun tagRequest(endpoint: Endpoint): ApiRequest<Tag> = request(endpoint, TagDto.serializer()) {
        it.toDomain()
    }
}

public object ListEndpoints {
    public fun all(): ApiRequest<List<AccountList>> =
        listRequest(Endpoint("api/v1/lists"), AccountListDto.serializer()) { it.toDomain() }

    public fun create(title: String): ApiRequest<AccountList> = request(
        Endpoint("api/v1/lists", HttpMethod.POST, body = Body.Form(listOf(QueryItem("title", title)))),
        AccountListDto.serializer(),
    ) { it.toDomain() }

    public fun delete(id: String): ApiRequest<Unit> = unitRequest(Endpoint("api/v1/lists/$id", HttpMethod.DELETE))

    /**
     * A list's members, paged by the `Link` header. No `limit` is sent: Mastodon reads `limit=0` as
     * "all", and Nextcloud Social answers it with a 400.
     */
    public fun accounts(id: String): ApiRequest<List<Account>> =
        listRequest(Endpoint("api/v1/lists/$id/accounts"), AccountDto.serializer()) { it.toDomain() }

    /** Renames a list and sets whose replies it shows and whether its members leave the home timeline. */
    public fun update(
        id: String,
        title: String,
        repliesPolicy: ListRepliesPolicy?,
        exclusive: Boolean,
    ): ApiRequest<AccountList> {
        val fields = listOf(QueryItem("title", title), QueryItem("exclusive", exclusive.toString())) +
            queryOf("replies_policy", repliesPolicy?.wire)
        return request(
            Endpoint("api/v1/lists/$id", HttpMethod.PUT, body = Body.Form(fields)),
            AccountListDto.serializer(),
        ) {
            it.toDomain()
        }
    }

    public fun addAccounts(id: String, accountIds: List<String>): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/lists/$id/accounts",
            HttpMethod.POST,
            body = Body.Form(repeatedQuery("account_ids", accountIds)),
        ),
    )

    public fun removeAccounts(id: String, accountIds: List<String>): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/lists/$id/accounts",
            HttpMethod.DELETE,
            body = Body.Form(repeatedQuery("account_ids", accountIds)),
        ),
    )
}
