// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.safety

import javax.inject.Inject
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.AccountAction
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.BlockEndpoints

/**
 * Who the reader blocked and muted, and the servers they blocked, each to be taken back. The account
 * lists take no cursor, so one page of the most there can be is what is shown.
 */
public class Blocking @Inject constructor(private val clients: ClientFactory) {
    public suspend fun blocked(account: SignedInAccount): Answer<List<Account>> =
        clients.answer(account, BlockEndpoints.blocks(PAGE))

    public suspend fun muted(account: SignedInAccount): Answer<List<Account>> =
        clients.answer(account, BlockEndpoints.mutes(PAGE))

    public suspend fun servers(account: SignedInAccount): Answer<List<String>> =
        clients.answer(account, BlockEndpoints.domainBlocks())

    /** True once the server took it. */
    public suspend fun unblock(account: SignedInAccount, id: String): Boolean =
        clients.answer(account, AccountEndpoints.action(id, AccountAction.Unblock)) is Answer.Got

    public suspend fun unmute(account: SignedInAccount, id: String): Boolean =
        clients.answer(account, AccountEndpoints.action(id, AccountAction.Unmute)) is Answer.Got

    public suspend fun blockServer(account: SignedInAccount, domain: String): Boolean =
        clients.answer(account, BlockEndpoints.blockDomain(domain)) is Answer.Got

    public suspend fun unblockServer(account: SignedInAccount, domain: String): Boolean =
        clients.answer(account, BlockEndpoints.unblockDomain(domain)) is Answer.Got

    private companion object {
        const val PAGE = 80
    }
}
