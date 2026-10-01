// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.conversations

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.search.Searches
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.Account
import social.aloha.core.model.Conversation
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.AccountEndpoints
import social.aloha.core.network.endpoints.Paging
import social.aloha.core.network.endpoints.TimelineEndpoints

/** A page of conversations; [next] is the server's cursor to the one after it. */
public data class ConversationPage(val conversations: List<Conversation>, val next: HttpUrl?)

/**
 * The reader's direct conversations, newest first: read, marked read, and taken off the list (which
 * deletes no message; a new one brings it back). A new one starts with someone the reader and they
 * follow each other with, or anyone found by name.
 */
@Singleton
public class Conversations @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
) {
    public suspend fun page(reader: SignedInAccount, next: HttpUrl? = null): Answer<ConversationPage> {
        val client = clients.forAccount(reader) ?: return Answer.Missed(ApiError.NotFound)
        val request = TimelineEndpoints.conversations()
        val result = next?.let { client.page(it, request, Paging.DEFAULT_LIMIT) }
            ?: client.page(request, Paging.DEFAULT_LIMIT)
        return when (result) {
            is ApiResult.Success -> {
                val items = result.value.items
                // stored, so a conversation opens its thread at once
                statuses.saveAll(reader.id, items.mapNotNull { it.lastStatus })
                Answer.Got(ConversationPage(items, result.value.link.next.takeIf { result.value.mayHaveMore }))
            }

            is ApiResult.Failure -> Answer.Missed(result.error)
        }
    }

    public suspend fun read(reader: SignedInAccount, id: String): Answer<Conversation> =
        clients.answer(reader, TimelineEndpoints.markConversationRead(id))

    public suspend fun readAll(reader: SignedInAccount): Answer<Unit> =
        clients.answer(reader, TimelineEndpoints.markAllConversationsRead())

    public suspend fun delete(reader: SignedInAccount, id: String): Answer<Unit> =
        clients.answer(reader, TimelineEndpoints.deleteConversation(id))

    /** Those the reader and they follow each other with: whom a message is likeliest for. */
    public suspend fun mutuals(reader: SignedInAccount): Answer<List<Account>> =
        clients.answer(reader, TimelineEndpoints.directMessageMutuals())

    /** Anyone whose name or handle matches [query]; a full handle on another server is looked up there. */
    public suspend fun people(reader: SignedInAccount, query: String): Answer<List<Account>> =
        clients.answer(reader, AccountEndpoints.search(query.trim(), resolve = Searches.resolvable(query)))
}
