// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Filter
import social.aloha.core.model.FilterAction
import social.aloha.core.model.FilterContext
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.FilterDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * One word of a filter as the editor holds it: an existing keyword carries its [id], a new one does
 * not, and one the editor removed is sent with [destroy] so the server drops it.
 */
public data class KeywordDraft(
    val keyword: String,
    val id: String? = null,
    val wholeWord: Boolean = false,
    val destroy: Boolean = false,
)

/**
 * What a filter editor sends when creating or changing a filter. With [keepExpiry] the expiry is not sent
 * at all, so the server keeps the one the filter has, run out or not; else [expiresInSeconds] sets it,
 * null clearing it.
 */
public data class FilterDraft(
    val title: String,
    val context: List<FilterContext>,
    val action: FilterAction,
    val expiresInSeconds: Long?,
    val keywords: List<KeywordDraft>,
    val keepExpiry: Boolean = false,
)

public object FilterEndpoints {
    public fun all(): ApiRequest<List<Filter>> = listRequest(Endpoint("api/v2/filters"), FilterDto.serializer()) {
        it.toDomain()
    }

    public fun get(id: String): ApiRequest<Filter> = request(Endpoint("api/v2/filters/$id"), FilterDto.serializer()) {
        it.toDomain()
    }

    public fun delete(id: String): ApiRequest<Unit> = unitRequest(Endpoint("api/v2/filters/$id", HttpMethod.DELETE))

    public fun create(draft: FilterDraft): ApiRequest<Filter> = request(
        Endpoint("api/v2/filters", HttpMethod.POST, body = Body.Form(formItems(draft))),
        FilterDto.serializer(),
    ) {
        it.toDomain()
    }

    /** Changes a filter; what is not named is left alone by the server. */
    public fun update(id: String, draft: FilterDraft): ApiRequest<Filter> = request(
        Endpoint("api/v2/filters/$id", HttpMethod.PUT, body = Body.Form(formItems(draft))),
        FilterDto.serializer(),
    ) {
        it.toDomain()
    }

    /**
     * Rails' nested-attributes shape, which Mastodon's v2 API takes: a flat `keywords[]` is silently
     * ignored. An absent expiry is sent as `""` rather than omitted, because the server leaves an
     * unnamed field alone and an update would keep an expiry the editor just cleared; for the same reason
     * an expiry kept as it is is not sent.
     */
    internal fun formItems(draft: FilterDraft): List<QueryItem> = listOf(QueryItem("title", draft.title)) +
        draft.context.filter { it != FilterContext.Unknown }.map { QueryItem("context[]", it.wire) } +
        QueryItem("filter_action", draft.action.wire) +
        listOfNotNull(
            QueryItem("expires_in", draft.expiresInSeconds?.toString().orEmpty()).takeUnless {
                draft.keepExpiry
            },
        ) +
        draft.keywords.flatMapIndexed(::keywordItems)

    private fun keywordItems(index: Int, draft: KeywordDraft): List<QueryItem> {
        val prefix = "keywords_attributes[$index]"
        return buildList {
            add(QueryItem("$prefix[keyword]", draft.keyword))
            add(QueryItem("$prefix[whole_word]", if (draft.wholeWord) "1" else "0"))
            draft.id?.let { add(QueryItem("$prefix[id]", it)) }
            if (draft.destroy) add(QueryItem("$prefix[_destroy]", "1"))
        }
    }
}
