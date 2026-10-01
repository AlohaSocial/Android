// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import social.aloha.core.data.AccountRepository
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.FeedMode
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource

/** The account in use and the timeline a mode reads for it. */
public data class ModeTimeline(val reader: SignedInAccount, val key: TimelineKey, val sources: List<TimelineSource>)

/**
 * Which timeline a mode with a screen of its own reads: the source chosen for that mode, kept per
 * account, where the server still serves it, else the people followed.
 */
public class ModeSources @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: AccountSettingsStore,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun timeline(mode: FeedMode): Flow<ModeTimeline> = accounts.activeAccount.filterNotNull()
        .flatMapLatest { reader -> settings.settings(reader.id).map { reader to it.modeSources[mode.key] } }
        .map { (reader, chosen) ->
            val sources = sourcesOf(reader)
            ModeTimeline(reader, TimelineKey(mode, chosen?.takeIf { it in sources } ?: TimelineSource.Home), sources)
        }
        .distinctUntilChanged { a, b -> a.reader.id == b.reader.id && a.key == b.key && a.sources == b.sources }

    public suspend fun choose(mode: FeedMode, source: TimelineSource) {
        val reader = accounts.activeAccount.filterNotNull().first()
        settings.update(reader.id) { it.copy(modeSources = it.modeSources + (mode.key to source)) }
    }

    /** The people followed, and this server and everyone where the server serves those timelines. */
    private fun sourcesOf(reader: SignedInAccount): List<TimelineSource> = buildList {
        add(TimelineSource.Home)
        if (reader.capabilities.localFeed) add(TimelineSource.Local)
        if (reader.capabilities.federatedFeed) add(TimelineSource.Federated)
    }
}
