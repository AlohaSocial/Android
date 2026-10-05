// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.toFeed
import social.aloha.core.datastore.toRecord
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.ServerCapabilities
import social.aloha.core.model.SignedInAccount

/**
 * The feeds each account keeps on Home: what the reader pinned, or the defaults until they change them.
 * A feed the server no longer serves is left out rather than shown empty.
 */
@Singleton
public class PinnedFeeds @Inject constructor(private val settings: AccountSettingsStore) {
    public fun feeds(account: SignedInAccount): Flow<List<PinnedFeed>> = settings.settings(account.id).map { stored ->
        val pinned = stored.pinnedFeeds?.mapNotNull { it.toFeed() }?.filter { served(it, account.capabilities) }
        pinned?.takeIf { it.isNotEmpty() } ?: PinnedFeed.defaults(account.capabilities, stored.homeSource)
    }.distinctUntilChanged()

    /** Keeps [feeds] as [account]'s, in their order, each once. */
    public suspend fun save(account: SignedInAccount, feeds: List<PinnedFeed>) {
        settings.update(account.id) {
            it.copy(pinnedFeeds = feeds.distinctBy(PinnedFeed::id).map { feed -> feed.toRecord() })
        }
    }

    private fun served(feed: PinnedFeed, capabilities: ServerCapabilities): Boolean = when (feed.kind) {
        PinnedFeed.Kind.ThisServer -> capabilities.localFeed
        PinnedFeed.Kind.Everyone -> capabilities.federatedFeed
        else -> true
    }
}
