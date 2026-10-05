// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.Poll
import social.aloha.core.model.Status
import social.aloha.core.model.StatusContext
import social.aloha.core.model.StatusEdit
import social.aloha.core.model.StatusSource
import social.aloha.core.model.Translation
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.CardDto
import social.aloha.core.network.dto.PollDto
import social.aloha.core.network.dto.StatusContextDto
import social.aloha.core.network.dto.StatusDto
import social.aloha.core.network.dto.StatusEditDto
import social.aloha.core.network.dto.StatusSourceDto
import social.aloha.core.network.dto.TranslationDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/** A one-tap action on a status; the wire value is the last path segment. */
public enum class StatusAction(public val wire: String) {
    Favourite("favourite"),
    Unfavourite("unfavourite"),
    Reblog("reblog"),
    Unreblog("unreblog"),
    Bookmark("bookmark"),
    Unbookmark("unbookmark"),
    Pin("pin"),
    Unpin("unpin"),
    Mute("mute"),
    Unmute("unmute"),
}

public object StatusEndpoints {
    public fun status(id: String): ApiRequest<Status> =
        request(Endpoint("api/v1/statuses/$id"), StatusDto.serializer()) { it.toDomain() }

    /**
     * The link preview of one status. The card is inlined in the status entity, which every list reads;
     * this route builds and caches it on the server, so it is a detail-screen request, never a per-row
     * one. A status with no link answers `{}`, which is null here.
     */
    public fun card(id: String): ApiRequest<Card?> =
        request(Endpoint("api/v1/statuses/$id/card"), CardDto.serializer()) { dto ->
            dto.takeUnless { it.url == null && it.title.isNullOrEmpty() }?.toDomain()
        }

    public fun context(id: String): ApiRequest<StatusContext> =
        request(Endpoint("api/v1/statuses/$id/context"), StatusContextDto.serializer()) { it.toDomain() }

    public fun history(id: String): ApiRequest<List<StatusEdit>> =
        listRequest(Endpoint("api/v1/statuses/$id/history"), StatusEditDto.serializer()) { it.toDomain() }

    /** The original plain text, which an edit composer loads instead of the rendered HTML. */
    public fun source(id: String): ApiRequest<StatusSource> =
        request(Endpoint("api/v1/statuses/$id/source"), StatusSourceDto.serializer()) { it.toDomain() }

    public fun favouritedBy(id: String, limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<Account>> =
        accounts("api/v1/statuses/$id/favourited_by", limit)

    public fun rebloggedBy(id: String, limit: Int = Paging.DEFAULT_LIMIT): ApiRequest<List<Account>> =
        accounts("api/v1/statuses/$id/reblogged_by", limit)

    private fun accounts(path: String, limit: Int): ApiRequest<List<Account>> =
        listRequest(Endpoint(path, query = Paging.pageItems(limit, PageAnchor.Cold)), AccountDto.serializer()) {
            it.toDomain()
        }

    /** [action] on post [id]; a boost seen by [visibility] where one is given, else the account's default. */
    public fun action(id: String, action: StatusAction, visibility: String? = null): ApiRequest<Status> = request(
        Endpoint(
            "api/v1/statuses/$id/${action.wire}",
            HttpMethod.POST,
            body = visibility?.let { Body.Form(listOf(QueryItem("visibility", it))) } ?: Body.None,
        ),
        StatusDto.serializer(),
    ) { it.toDomain() }

    /** Deletes a status; the answer carries its source text, for "delete and redraft". */
    public fun delete(id: String): ApiRequest<Status> =
        request(Endpoint("api/v1/statuses/$id", HttpMethod.DELETE), StatusDto.serializer()) { it.toDomain() }

    /** Server translation. A server without a provider answers 503 with a message to show verbatim. */
    public fun translate(id: String, language: String?): ApiRequest<Translation> = request(
        Endpoint("api/v1/statuses/$id/translate", HttpMethod.POST, body = Body.Form(queryOf("lang", language))),
        TranslationDto.serializer(),
    ) { it.toDomain() }

    /**
     * An emoji reaction. Nextcloud Social takes Unicode emoji only, and reads it from `emoji`: a
     * `name` answers 422 "not an emoji this app can draw".
     */
    public fun react(id: String, emoji: String): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/statuses/$id/react", HttpMethod.POST, body = Body.Form(listOf(QueryItem("emoji", emoji)))),
    )

    public fun unreact(id: String, emoji: String): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/statuses/$id/unreact", HttpMethod.POST, body = Body.Form(listOf(QueryItem("emoji", emoji)))),
    )

    public fun vote(pollId: String, choices: List<Int>): ApiRequest<Poll> = request(
        Endpoint(
            "api/v1/polls/$pollId/votes",
            HttpMethod.POST,
            body = Body.Form(repeatedQuery("choices", choices.map(Int::toString))),
        ),
        PollDto.serializer(),
    ) { it.toDomain() }
}

public object PollEndpoints {
    public fun poll(id: String): ApiRequest<Poll> =
        request(Endpoint("api/v1/polls/$id"), PollDto.serializer()) { it.toDomain() }
}
