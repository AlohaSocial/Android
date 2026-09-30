// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.thread

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.Reaction
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.StatusEdit
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiRequest
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.Paging
import social.aloha.core.network.endpoints.StatusEndpoints
import social.aloha.core.network.endpoints.StatusExtraEndpoints

/** A conversation as the server sent it: the posts above the one asked for, and every reply below. */
public data class Conversation(val focused: Status, val ancestors: List<Status>, val descendants: List<Status>)

/**
 * A post and the conversation around it. Every post the server sends is stored with the rest, so an
 * action on one shows in the thread and in each timeline alike; what only this screen needs (the
 * card, the reactions, the edit history, who favourited or boosted) is fetched and not kept.
 */
@Singleton
public class ThreadRepository @Inject constructor(
    private val statuses: StatusRepository,
    private val clients: ClientFactory,
) {
    public fun observe(account: SignedInAccount, statusIds: List<String>): Flow<Map<String, Status>> =
        statuses.observe(account.id, statusIds)

    public suspend fun load(account: SignedInAccount, statusId: String): Answer<Conversation> {
        val focused = when (val answer = clients.answer(account, StatusEndpoints.status(statusId))) {
            is Answer.Got -> answer.value

            is Answer.Missed -> return answer.also {
                if (it.error == ApiError.NotFound) statuses.delete(account.id, statusId)
            }
        }
        return when (val context = clients.answer(account, StatusEndpoints.context(statusId))) {
            is Answer.Got -> {
                val conversation = Conversation(focused, context.value.ancestors, context.value.descendants)
                statuses.saveAll(account.id, listOf(focused) + conversation.ancestors + conversation.descendants)
                Answer.Got(conversation)
            }

            is Answer.Missed -> context
        }
    }

    /** The link card, which timelines never ask for: one request per row would be a request storm. */
    public suspend fun card(account: SignedInAccount, statusId: String): Card? =
        (clients.answer(account, StatusEndpoints.card(statusId)) as? Answer.Got)?.value

    /** Reactions, which Nextcloud Social serves only here: its statuses always carry an empty list. */
    public suspend fun reactions(account: SignedInAccount, statusId: String): Answer<List<Reaction>> =
        clients.answer(account, StatusExtraEndpoints.reactions(statusId))

    public suspend fun history(account: SignedInAccount, statusId: String): Answer<List<StatusEdit>> =
        clients.answer(account, StatusEndpoints.history(statusId))

    public suspend fun favouritedBy(
        account: SignedInAccount,
        statusId: String,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): Answer<List<Account>> = clients.answer(account, StatusEndpoints.favouritedBy(statusId, limit))

    public suspend fun boostedBy(
        account: SignedInAccount,
        statusId: String,
        limit: Int = Paging.DEFAULT_LIMIT,
    ): Answer<List<Account>> = clients.answer(account, StatusEndpoints.rebloggedBy(statusId, limit))

    public suspend fun quotes(account: SignedInAccount, statusId: String): Answer<List<Status>> =
        when (val answer = clients.answer(account, StatusExtraEndpoints.quotes(statusId))) {
            is Answer.Got -> answer.also { statuses.saveAll(account.id, it.value) }
            is Answer.Missed -> answer
        }
}
