// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import javax.inject.Singleton
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.RemoteLookup
import social.aloha.core.model.Visibility
import social.aloha.core.network.ApiError

/**
 * A post acted on as one of the reader's accounts: found first by its address on that account's
 * server, where it has an id of its own, then boosted, favourited or bookmarked there.
 */
@Singleton
public class ActingAs @Inject constructor(
    private val accounts: AccountRepository,
    private val lookup: RemoteLookup,
    private val statuses: StatusRepository,
    private val interactions: StatusInteractions,
) {
    /**
     * Boosts (seen by [visibility]), favourites or bookmarks the post at [url] as account [accountId], as
     * [toggle] names; nothing where that account did so already, so acting never undoes. Null once done.
     */
    public suspend fun toggle(
        accountId: String,
        url: String,
        toggle: Toggle,
        visibility: Visibility? = null,
    ): ApiError? {
        val account = accounts.byId(accountId) ?: return ApiError.NotFound
        val id = lookup.post(account, url) ?: return ApiError.NotFound
        val status = statuses.get(account.id, id) ?: return ApiError.NotFound
        val done = when (toggle) {
            Toggle.Boost -> status.reblogged
            Toggle.Favourite -> status.favourited
            Toggle.Bookmark -> status.bookmarked
            else -> false
        }
        return if (done) null else interactions.toggle(account, status, toggle, visibility)
    }
}
