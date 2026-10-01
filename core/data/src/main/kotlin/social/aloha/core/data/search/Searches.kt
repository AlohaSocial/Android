// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.search

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.SearchResults
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.SearchEndpoints

/**
 * Search on the reader's server: accounts, hashtags and posts. A query that is an address or a full
 * handle asks the server to fetch what it names from wherever it lives, so a post or a person from
 * any server opens in the app. The posts found are stored like any other, so acting on one works.
 * The last few searches are kept per account, newest first, until cleared.
 */
@Singleton
public class Searches @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
    private val settings: AccountSettingsStore,
) {
    public suspend fun search(reader: SignedInAccount, query: String): Answer<SearchResults> {
        val answer = clients.answer(reader, SearchEndpoints.search(query.trim(), resolve = resolvable(query)))
        (answer as? Answer.Got)?.value?.statuses?.let { statuses.saveAll(reader.id, it) }
        return answer
    }

    public fun recent(reader: SignedInAccount): Flow<List<String>> = settings.settings(reader.id).map {
        it.recentSearches
    }

    /** Keeps [query] first among the recent searches, once. */
    public suspend fun remember(reader: SignedInAccount, query: String) {
        val wanted = query.trim().takeIf { it.isNotEmpty() } ?: return
        settings.update(reader.id) { current ->
            val recent = (listOf(wanted) + current.recentSearches).distinctBy(String::lowercase).take(RECENT)
            current.copy(recentSearches = recent)
        }
    }

    public suspend fun clearRecent(reader: SignedInAccount) {
        settings.update(reader.id) { it.copy(recentSearches = emptyList()) }
    }

    public companion object {
        private const val RECENT = 10
        private val ADDRESS = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)
        private val HANDLE = Regex("""^@?[\w.-]+@[\w-]+(\.[\w-]+)+$""")

        /**
         * Whether the server should fetch what [query] names from where it lives: a web address, or a
         * handle with its server. A word, or a bare name, is searched for as it is.
         */
        public fun resolvable(query: String): Boolean = query.trim().let { ADDRESS.matches(it) || HANDLE.matches(it) }
    }
}
