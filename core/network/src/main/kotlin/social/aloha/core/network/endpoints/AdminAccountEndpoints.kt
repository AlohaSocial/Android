// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import social.aloha.core.model.AdminAccount
import social.aloha.core.model.AdminAccountAction
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AdminAccountDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/**
 * What a moderator may do to an account, under `/api/v1/admin/accounts`. Like [ModerationEndpoints]
 * these take the bearer token and answer 403 to anybody who is not a Nextcloud administrator.
 */
public object AdminAccountEndpoints {
    /** Where an account lives, for the accounts filter; [Any] sends no filter. */
    public enum class Origin(internal val parameter: String?) { Any(null), Local("local"), Remote("remote") }

    /**
     * The standings this server has, for the accounts filter; [Any] sends no filter. Mastodon's
     * `pending` and `disabled` answer with nothing here and are left out.
     */
    public enum class Standing(internal val parameter: String?) {
        Any(null),
        Active("active"),
        Silenced("silenced"),
        Suspended("suspended"),
    }

    /**
     * A page of accounts, newest first. `email` and `ip` are accepted by the server and match nothing, since
     * it holds neither for a fediverse account, so they are not offered.
     */
    public fun accounts(
        origin: Origin = Origin.Any,
        standing: Standing = Standing.Any,
        username: String = "",
        byDomain: String = "",
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<AdminAccount>> = listRequest(
        Endpoint(
            "api/v1/admin/accounts",
            query = Paging.pageItems(limit, anchor) +
                queryOf("origin", origin.parameter) +
                queryOf("status", standing.parameter) +
                queryOf("username", username.ifEmpty { null }) +
                queryOf("by_domain", byDomain.ifEmpty { null }),
        ),
        AdminAccountDto.serializer(),
    ) { it.toDomain() }

    public fun account(id: String): ApiRequest<AdminAccount> = accountRequest(Endpoint("api/v1/admin/accounts/$id"))

    /**
     * Silences or suspends an account, or records a decision that changes nothing. [reportId] resolves that
     * report in the same step, so the decision and the report never disagree.
     */
    public fun act(
        id: String,
        action: AdminAccountAction,
        note: String = "",
        reportId: String? = null,
    ): ApiRequest<Unit> = unitRequest(
        Endpoint(
            "api/v1/admin/accounts/$id/action",
            HttpMethod.POST,
            body = Body.Form(
                listOf(QueryItem("type", action.wire)) + queryOf("text", note.ifEmpty { null }) +
                    queryOf("report_id", reportId),
            ),
        ),
    )

    /** Lifts a silence. */
    public fun unsilence(id: String): ApiRequest<AdminAccount> = accountAction(id, "unsilence")

    /** Lifts a suspension. */
    public fun unsuspend(id: String): ApiRequest<AdminAccount> = accountAction(id, "unsuspend")

    /** Stops forcing every attachment of the account behind a warning. */
    public fun unsensitive(id: String): ApiRequest<AdminAccount> = accountAction(id, "unsensitive")
}

private fun accountRequest(endpoint: Endpoint) = request(endpoint, AdminAccountDto.serializer()) { it.toDomain() }

private fun accountAction(id: String, action: String) =
    accountRequest(Endpoint("api/v1/admin/accounts/$id/$action", HttpMethod.POST))
