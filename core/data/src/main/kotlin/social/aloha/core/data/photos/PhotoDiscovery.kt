// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.photos

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.Account
import social.aloha.core.model.ContentClassifier
import social.aloha.core.model.ContentKind
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.Tag
import social.aloha.core.network.ApiError
import social.aloha.core.network.endpoints.DiscoveryEndpoints
import social.aloha.core.network.endpoints.SearchEndpoints

/** What Photos' Explore shows; a section the server does not serve is empty. */
public data class PhotoExplore(
    val trending: List<Status> = emptyList(),
    val network: List<Status> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val people: List<Account> = emptyList(),
) {
    val isEmpty: Boolean get() = trending.isEmpty() && network.isEmpty() && tags.isEmpty() && people.isEmpty()
}

/**
 * Photos' Explore, from Pixelfed's discover routes where the server has them: what is trending here and
 * across the network, trending hashtags and popular accounts, asked for together. Only posts with
 * pictures are kept, and stored so a tap opens them at once. A server without Pixelfed's trending posts
 * gets Mastodon's, which the same trends service fills; hashtags always come from Mastodon's route,
 * whose shape is known.
 */
@Singleton
public class PhotoDiscovery @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
) {
    public suspend fun explore(reader: SignedInAccount): Answer<PhotoExplore> = coroutineScope {
        val trending = async { trending(reader) }
        val network = async { clients.answer(reader, DiscoveryEndpoints.networkTrendingPosts()) }
        val tags = async { clients.answer(reader, SearchEndpoints.trendingTags()) }
        val people = async { clients.answer(reader, DiscoveryEndpoints.popularAccounts()) }
        val answers = listOf(trending.await(), network.await(), tags.await(), people.await())
        val explore = PhotoExplore(
            trending = photos(trending.await()),
            network = photos(network.await()),
            tags = tags.await().orEmpty(),
            people = people.await().orEmpty(),
        )
        statuses.saveAll(reader.id, explore.trending + explore.network)
        // nothing at all came back: say why, rather than show an empty page
        val failed = answers.filterIsInstance<Answer.Missed>()
        if (explore.isEmpty && failed.size == answers.size) failed.first() else Answer.Got(explore)
    }

    private suspend fun trending(reader: SignedInAccount): Answer<List<Status>> {
        val pixelfed = clients.answer(reader, DiscoveryEndpoints.trendingPosts())
        val absent = pixelfed is Answer.Missed && pixelfed.error == ApiError.NotFound
        return if (absent) clients.answer(reader, DiscoveryEndpoints.trendingStatuses()) else pixelfed
    }

    private fun photos(answer: Answer<List<Status>>): List<Status> =
        answer.orEmpty().filter { ContentClassifier.classify(it) == ContentKind.Photo }
}

private fun <T> Answer<List<T>>.orEmpty(): List<T> = (this as? Answer.Got)?.value.orEmpty()
