// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.explore

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.Account
import social.aloha.core.model.Card
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.StarterPack
import social.aloha.core.model.Status
import social.aloha.core.model.Suggestion
import social.aloha.core.model.Tag
import social.aloha.core.network.ApiError
import social.aloha.core.network.ApiResult
import social.aloha.core.network.endpoints.DirectoryOrder
import social.aloha.core.network.endpoints.DiscoveryEndpoints
import social.aloha.core.network.endpoints.SearchEndpoints

/**
 * Who to follow: those the server suggests, the starter packs that follow many at once, and those
 * popular here. Each comes from a route of its own, and one a server does not serve is left empty.
 */
public data class People(
    val suggestions: List<Suggestion> = emptyList(),
    val packs: List<StarterPack> = emptyList(),
    val popular: List<Account> = emptyList(),
) {
    val isEmpty: Boolean get() = suggestions.isEmpty() && packs.isEmpty() && popular.isEmpty()
}

/** A page of the directory: [accounts] as read, [sent] as many as the server sent, and whether it was the last. */
public data class DirectoryPage(val accounts: List<Account>, val sent: Int, val done: Boolean)

/**
 * What is going on, by the reader's server: trending posts, hashtags and links, who to follow, and
 * the directory of the accounts here that chose to be in it. Nothing is ranked or reordered here: the
 * order is the server's.
 */
@Singleton
public class Explore @Inject constructor(private val clients: ClientFactory, private val statuses: StatusRepository) {
    /** Trending posts, stored so a tap opens them like any other. */
    public suspend fun posts(reader: SignedInAccount): Answer<List<Status>> {
        val answer = clients.answer(reader, DiscoveryEndpoints.trendingStatuses())
        (answer as? Answer.Got)?.value?.let { statuses.saveAll(reader.id, it) }
        return answer
    }

    /** Trending hashtags over [period], which only Nextcloud Social reads: `1h`, `12h`, `1d`, `3d` or `10d`. */
    public suspend fun hashtags(reader: SignedInAccount, period: String = PERIODS[2]): Answer<List<Tag>> =
        clients.answer(reader, SearchEndpoints.trendingTags(limit = TRENDS, period = period))

    public suspend fun links(reader: SignedInAccount): Answer<List<Card>> =
        clients.answer(reader, SearchEndpoints.trendingLinks(limit = TRENDS))

    /** Who to follow; the answer of the first route that failed when all of them did. */
    public suspend fun people(reader: SignedInAccount): Answer<People> = coroutineScope {
        val suggestions = async { clients.answer(reader, SearchEndpoints.suggestions()) }
        val packs = async { clients.answer(reader, DiscoveryEndpoints.starterPacks()) }
        val popular = async { clients.answer(reader, DiscoveryEndpoints.popularAccounts()) }
        val answers = listOf(suggestions.await(), packs.await(), popular.await())
        val people = People(suggestions.await().orEmpty(), packs.await().orEmpty(), popular.await().orEmpty())
        val failed = answers.filterIsInstance<Answer.Missed>()
        if (people.isEmpty && failed.size == answers.size) failed.first() else Answer.Got(people)
    }

    /**
     * A page of the server's own directory from [offset]: only those who chose to be found, by [order].
     * The offset counts what the server sent, an entry that could not be read included, so the next page
     * starts where this one ended.
     */
    public suspend fun directory(reader: SignedInAccount, order: DirectoryOrder, offset: Int): Answer<DirectoryPage> {
        val client = clients.forAccount(reader) ?: return Answer.Missed(ApiError.NotFound)
        val request = SearchEndpoints.directory(order = order, limit = DIRECTORY_PAGE, offset = offset)
        return when (val result = client.page(request, DIRECTORY_PAGE)) {
            is ApiResult.Success ->
                Answer.Got(DirectoryPage(result.value.items, result.value.rawCount, !result.value.mayHaveMore))

            is ApiResult.Failure -> Answer.Missed(result.error)
        }
    }

    /** Stops suggesting [accountId]. */
    public suspend fun dismiss(reader: SignedInAccount, accountId: String): Answer<Unit> =
        clients.answer(reader, SearchEndpoints.dismissSuggestion(accountId))

    /** Follows everybody in the starter pack [slug]. */
    public suspend fun followAll(reader: SignedInAccount, slug: String): Answer<Unit> =
        clients.answer(reader, DiscoveryEndpoints.followStarterPack(slug))

    public companion object {
        private const val DIRECTORY_PAGE = 40

        /** The periods trending hashtags are measured over, shortest first. */
        public val PERIODS: List<String> = listOf("1h", "12h", "1d", "3d", "10d")
        private const val TRENDS = 20
    }
}

private fun <T> Answer<List<T>>.orEmpty(): List<T> = (this as? Answer.Got)?.value.orEmpty()
