// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.timeline.StatusRepository
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.endpoints.SearchEndpoints

/**
 * A post from anywhere, by its address, as the reader's server knows it: a resolving search asks that
 * server to fetch it, and its id there is what the app opens. The post is stored like any other.
 */
@Singleton
public class RemoteLookup @Inject constructor(
    private val clients: ClientFactory,
    private val statuses: StatusRepository,
) {
    /** The post's id on [reader]'s server; null when the server could not find it. */
    public suspend fun post(reader: SignedInAccount, url: String): String? {
        val found = clients.answer(reader, SearchEndpoints.search(url, type = "statuses", resolve = true, limit = 1))
        val status = (found as? Answer.Got)?.value?.statuses?.firstOrNull() ?: return null
        statuses.save(reader.id, status)
        return status.id
    }
}
