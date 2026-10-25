// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.AllAccounts
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.model.FeedMode
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource

/**
 * An account and the timeline a mode reads for it, with the sources it could read instead; [colour] marks
 * its posts where every account shows together: its own accent, else its server's.
 */
public data class ModeTimeline(
    val reader: SignedInAccount,
    val key: TimelineKey,
    val sources: List<TimelineSource>,
    val colour: Int? = null,
)

/**
 * Which timeline a mode with a screen of its own reads: the source chosen for that mode, kept per
 * account, where the server still serves it, else the people followed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ModeSources @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: AccountSettingsStore,
    private val allAccounts: AllAccounts,
) {
    /** The account in use's timeline for [mode]. */
    public fun timeline(mode: FeedMode): Flow<ModeTimeline> = accounts.activeAccount.filterNotNull()
        .flatMapLatest { reader -> settings.settings(reader.id).map { timelineOf(reader, mode, it) } }
        .distinctUntilChanged { a, b -> a.reader.id == b.reader.id && a.key == b.key && a.sources == b.sources }

    /** Every account shown, the first being the one in use, each with its own timeline for [mode]. */
    public fun together(mode: FeedMode): Flow<List<ModeTimeline>> = allAccounts.shown.flatMapLatest { shown ->
        if (shown.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(shown.map { reader -> settings.settings(reader.id).map { timelineOf(reader, mode, it) } }) {
                it.toList()
            }
        }
    }.distinctUntilChanged()

    public suspend fun choose(mode: FeedMode, source: TimelineSource) {
        val reader = accounts.activeAccount.filterNotNull().first()
        settings.update(reader.id) { it.copy(modeSources = it.modeSources + (mode.key to source)) }
    }

    private fun timelineOf(reader: SignedInAccount, mode: FeedMode, own: AccountSettings): ModeTimeline {
        val sources = sourcesOf(reader)
        val chosen = own.modeSources[mode.key]?.takeIf { it in sources } ?: TimelineSource.Home
        return ModeTimeline(
            reader,
            TimelineKey(mode, chosen),
            sources,
            own.accent ?: reader.capabilities.theme?.colour,
        )
    }

    /** The people followed, and this server and everyone where the server serves those timelines. */
    private fun sourcesOf(reader: SignedInAccount): List<TimelineSource> = buildList {
        add(TimelineSource.Home)
        if (reader.capabilities.localFeed) add(TimelineSource.Local)
        if (reader.capabilities.federatedFeed) add(TimelineSource.Federated)
    }
}
