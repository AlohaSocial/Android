// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.network.endpoints

import kotlinx.serialization.builtins.serializer
import social.aloha.core.model.Account
import social.aloha.core.model.Relationship
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.Body
import social.aloha.core.network.Endpoint
import social.aloha.core.network.HttpMethod
import social.aloha.core.network.QueryItem
import social.aloha.core.network.dto.AccountDto
import social.aloha.core.network.dto.RelationshipDto
import social.aloha.core.network.dto.toDomain
import social.aloha.core.network.flagQuery
import social.aloha.core.network.listRequest
import social.aloha.core.network.queryOf
import social.aloha.core.network.repeatedQuery
import social.aloha.core.network.request
import social.aloha.core.network.unitRequest

/** An account action that takes no parameters and answers with the new relationship. */
public enum class AccountAction(public val wire: String) {
    Unfollow("unfollow"),
    Block("block"),
    Unblock("unblock"),
    Unmute("unmute"),
    Endorse("pin"),
    Unendorse("unpin"),
    RemoveFromFollowers("remove_from_followers"),
}

private fun accounts(endpoint: Endpoint): ApiRequest<List<Account>> =
    listRequest(endpoint, AccountDto.serializer()) { it.toDomain() }

private fun relationship(endpoint: Endpoint): ApiRequest<Relationship> =
    request(endpoint, RelationshipDto.serializer()) { it.toDomain() }

public object AccountEndpoints {
    private const val SEARCH_LIMIT = 8

    /**
     * The signed-in account. On Nextcloud Social this answers 500 for an account whose avatar has not
     * been cached yet; the caller falls back to `/oauth/userinfo` and retries later.
     */
    public fun verifyCredentials(): ApiRequest<Account> =
        request(Endpoint("api/v1/accounts/verify_credentials"), AccountDto.serializer()) { it.toDomain() }

    public fun account(id: String): ApiRequest<Account> =
        request(Endpoint("api/v1/accounts/$id"), AccountDto.serializer()) { it.toDomain() }

    public fun lookup(acct: String): ApiRequest<Account> = request(
        Endpoint("api/v1/accounts/lookup", query = listOf(QueryItem("acct", acct))),
        AccountDto.serializer(),
    ) { it.toDomain() }

    public fun relationships(ids: List<String>): ApiRequest<List<Relationship>> = listRequest(
        Endpoint("api/v1/accounts/relationships", query = repeatedQuery("id", ids)),
        RelationshipDto.serializer(),
    ) { it.toDomain() }

    /** What a composer calls to complete a `@handle`; Nextcloud Social serves it for exactly that. */
    public fun search(query: String, limit: Int = SEARCH_LIMIT, resolve: Boolean = false): ApiRequest<List<Account>> {
        val items = listOf(QueryItem("q", query), Paging.limitItem(limit)) + flagQuery("resolve", resolve)
        return accounts(Endpoint("api/v1/accounts/search", query = items))
    }

    public fun follow(id: String, reblogs: Boolean = true, notify: Boolean = false): ApiRequest<Relationship> {
        val fields = listOf(QueryItem("reblogs", reblogs.toString()), QueryItem("notify", notify.toString()))
        return relationship(Endpoint("api/v1/accounts/$id/follow", HttpMethod.POST, body = Body.Form(fields)))
    }

    public fun action(id: String, action: AccountAction): ApiRequest<Relationship> =
        relationship(Endpoint("api/v1/accounts/$id/${action.wire}", HttpMethod.POST))

    /** Mutes an account, for [durationSeconds] or indefinitely. */
    public fun mute(id: String, notifications: Boolean, durationSeconds: Long? = null): ApiRequest<Relationship> {
        val fields = listOf(QueryItem("notifications", notifications.toString())) +
            queryOf("duration", durationSeconds?.toString())
        return relationship(Endpoint("api/v1/accounts/$id/mute", HttpMethod.POST, body = Body.Form(fields)))
    }

    public fun followers(
        id: String,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Account>> =
        accounts(Endpoint("api/v1/accounts/$id/followers", query = Paging.pageItems(limit, anchor)))

    public fun following(
        id: String,
        limit: Int = Paging.DEFAULT_LIMIT,
        anchor: PageAnchor = PageAnchor.Cold,
    ): ApiRequest<List<Account>> =
        accounts(Endpoint("api/v1/accounts/$id/following", query = Paging.pageItems(limit, anchor)))
}

public object FollowRequestEndpoints {
    public fun all(): ApiRequest<List<Account>> = accounts(Endpoint("api/v1/follow_requests"))

    public fun authorise(id: String): ApiRequest<Relationship> =
        relationship(Endpoint("api/v1/follow_requests/$id/authorize", HttpMethod.POST))

    public fun reject(id: String): ApiRequest<Relationship> =
        relationship(Endpoint("api/v1/follow_requests/$id/reject", HttpMethod.POST))
}

/** Blocked and muted accounts and domains. None of these routes sends a `Link` header or takes a cursor. */
public object BlockEndpoints {
    private const val DEFAULT_LIMIT = 40
    private const val MAXIMUM_LIMIT = 80

    /** Blocked accounts, paged by `limit` alone. */
    public fun blocks(limit: Int = DEFAULT_LIMIT): ApiRequest<List<Account>> =
        accounts(Endpoint("api/v1/blocks", query = listOf(Paging.limitItem(limit, MAXIMUM_LIMIT))))

    /** Muted accounts, paged by `limit` alone. */
    public fun mutes(limit: Int = DEFAULT_LIMIT): ApiRequest<List<Account>> =
        accounts(Endpoint("api/v1/mutes", query = listOf(Paging.limitItem(limit, MAXIMUM_LIMIT))))

    /** The blocked domains, as bare hostnames. */
    public fun domainBlocks(): ApiRequest<List<String>> =
        listRequest(Endpoint("api/v1/domain_blocks"), String.serializer()) { it }

    public fun blockDomain(domain: String): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/domain_blocks", HttpMethod.POST, body = Body.Form(listOf(QueryItem("domain", domain)))),
    )

    public fun unblockDomain(domain: String): ApiRequest<Unit> = unitRequest(
        Endpoint("api/v1/domain_blocks", HttpMethod.DELETE, body = Body.Form(listOf(QueryItem("domain", domain)))),
    )
}
