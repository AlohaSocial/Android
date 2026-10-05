// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.profile

import javax.inject.Inject
import social.aloha.core.data.Answer
import social.aloha.core.data.ClientFactory
import social.aloha.core.data.answer
import social.aloha.core.model.Account
import social.aloha.core.model.FeaturedTag
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.network.endpoints.ProfileEndpoints

/**
 * What a profile shows about an account beyond its posts: who the reader knows among its followers, and
 * what it features.
 */
public class ProfileFeatured @Inject constructor(private val clients: ClientFactory) {
    /** The people [reader] follows who also follow account [id]; none where the server will not say. */
    public suspend fun familiarFollowers(reader: SignedInAccount, id: String): List<Account> =
        (clients.answer(reader, ProfileEndpoints.familiarFollowers(listOf(id))) as? Answer.Got)
            ?.value?.firstOrNull { it.id == id }?.accounts.orEmpty()

    /** The posts account [id] pinned to its profile. */
    public suspend fun pinned(reader: SignedInAccount, id: String): Answer<List<Status>> =
        clients.answer(reader, ProfileEndpoints.pinnedStatuses(id))

    /** The hashtags account [id] features. */
    public suspend fun tags(reader: SignedInAccount, id: String): Answer<List<FeaturedTag>> =
        clients.answer(reader, ProfileEndpoints.featuredTags(id))
}
