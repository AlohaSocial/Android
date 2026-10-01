// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.lists

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.Account
import social.aloha.core.model.AccountList
import social.aloha.core.model.ListRepliesPolicy
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.ListEndpoints
import social.aloha.core.network.endpoints.Paging

/**
 * The reader's lists: made, renamed and set (whose replies they show, whether their members leave the
 * home timeline), deleted, and filled with people the reader follows. A list that follows a Nextcloud
 * group is the group's: the server refuses to change its members, and says why, which is passed on.
 */
@Singleton
public class Lists @Inject constructor(private val clients: ClientFactory) {
    public suspend fun all(reader: SignedInAccount): Answer<List<AccountList>> =
        clients.answer(reader, ListEndpoints.all())

    public suspend fun create(reader: SignedInAccount, title: String): Answer<AccountList> =
        clients.answer(reader, ListEndpoints.create(title.trim()))

    /** Saves [list]'s name, whose replies it shows and whether its members leave the home timeline. */
    public suspend fun update(reader: SignedInAccount, list: AccountList): Answer<AccountList> {
        val policy = ListRepliesPolicy.entries.firstOrNull { it.wire == list.repliesPolicy }
        return clients.answer(reader, ListEndpoints.update(list.id, list.title.trim(), policy, list.exclusive))
    }

    public suspend fun delete(reader: SignedInAccount, listId: String): Answer<Unit> =
        clients.answer(reader, ListEndpoints.delete(listId))

    /**
     * Everyone in list [listId], along the server's `Link` pages.
     *
     * ponytail: read whole, at most [MAX_MEMBER_PAGES] pages; paged on screen if lists that long turn up.
     */
    public suspend fun members(reader: SignedInAccount, listId: String): Answer<List<Account>> {
        val client = clients.forAccount(reader) ?: return Answer.Missed(ApiError.NotFound)
        val request = ListEndpoints.accounts(listId)
        val members = mutableListOf<Account>()
        var result = client.page(request, LIMIT)
        var pages = 1
        while (result is ApiResult.Success) {
            members += result.value.items
            val next = result.value.link.next
            if (next == null || pages++ >= MAX_MEMBER_PAGES) break
            result = client.page(next, request, LIMIT)
        }
        return (result as? ApiResult.Failure)?.let { Answer.Missed(it.error) }
            ?: Answer.Got(members.distinctBy { it.id })
    }

    public suspend fun add(reader: SignedInAccount, listId: String, accountId: String): Answer<Unit> =
        clients.answer(reader, ListEndpoints.addAccounts(listId, listOf(accountId)))

    public suspend fun remove(reader: SignedInAccount, listId: String, accountId: String): Answer<Unit> =
        clients.answer(reader, ListEndpoints.removeAccounts(listId, listOf(accountId)))

    /** Those the reader follows whose name or handle matches [query]: who a list can hold. */
    public suspend fun followed(reader: SignedInAccount, query: String): Answer<List<Account>> =
        clients.answer(reader, AccountEndpoints.search(query.trim(), following = true))

    private companion object {
        const val MAX_MEMBER_PAGES = 25
        const val LIMIT = Paging.DEFAULT_LIMIT
    }
}
