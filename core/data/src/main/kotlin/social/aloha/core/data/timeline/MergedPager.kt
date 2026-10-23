// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.Trouble
import social.aloha.core.data.trouble
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey

/**
 * Every account's timeline as one screen reads it: their stored rows together, a refresh of all of them at
 * once, the next page from the accounts the merge says are behind, and what a refresh brought held behind
 * the pill while rows are being read. Merging is [MergedTimeline]'s, after the screen has filtered each
 * account's rows by that account's own settings. One per screen, living in its [scope]; [current] says
 * which accounts it shows now and which timeline of each.
 */
public class MergedPager(
    private val timelines: TimelineRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val current: suspend () -> List<Pair<SignedInAccount, TimelineKey>>,
) {
    /**
     * What the merged timeline is doing. [troubles] are the accounts whose last fetch failed, the others
     * showing what they have; [ended] the accounts whose server has nothing further down; [held] each
     * account's posts that wait behind the pill.
     */
    public data class State(
        val refreshing: Boolean = false,
        val loadedOnce: Boolean = false,
        val troubles: Map<String, Trouble> = emptyMap(),
        val loadingOlder: Boolean = false,
        val ended: Set<String> = emptySet(),
        val held: Map<String, Set<String>> = emptyMap(),
    )

    private val state = MutableStateFlow(State())
    public val states: StateFlow<State> = state.asStateFlow()

    // each account's stored rows as last seen, with the timeline they are of, and its cursor further down
    private val seen = ConcurrentHashMap<String, Pair<TimelineKey, List<TimelineRow>>>()
    private val cursors = ConcurrentHashMap<String, HttpUrl>()

    /** Each account's stored rows, by account id, as they change; what paging and gap filling start from. */
    public fun observe(shown: List<Pair<SignedInAccount, TimelineKey>>): Flow<Map<String, List<TimelineRow>>> {
        if (shown.isEmpty()) return flowOf(emptyMap())
        val each = shown.map { (account, key) ->
            timelines.observe(account, key).onEach { seen[account.id] = key to it }.map { account.id to it }
        }
        return combine(each) { it.toMap() }
    }

    /** The screen shows [shown] now: nothing of the previous accounts' paging carries over. */
    public suspend fun start(shown: List<Pair<SignedInAccount, TimelineKey>>) {
        state.value = State()
        cursors.clear()
        // rows that came in ahead of the switch are kept where they are of the timeline shown now
        seen.keys.retainAll(shown.filter { (account, key) -> seen[account.id]?.first == key }.map { it.first.id })
        refreshIfStale(shown)
    }

    private fun rowsOf(account: SignedInAccount): List<TimelineRow> = seen[account.id]?.second.orEmpty()

    /** Refreshes every account whose timeline has not fetched within the last minute. */
    public suspend fun refreshIfStale(shown: List<Pair<SignedInAccount, TimelineKey>>) {
        val stale = shown.filter { (account, key) ->
            val last = timelines.lastFetched(account, key)
            last == null || clock.millis() - last > STALE_MILLIS
        }
        if (stale.isNotEmpty()) refresh(stale)
    }

    /** The newest posts of every account: over rows being read, they wait behind the pill. */
    public fun refresh() {
        scope.launch { refresh(current()) }
    }

    /** What waited behind the pill joins the rows. */
    public fun reveal() {
        state.update { it.copy(held = emptyMap()) }
    }

    /** The next page from each account in [behind], as the last merge named them, unless one is on its way. */
    public fun older(behind: List<String>) {
        if (state.value.loadingOlder || behind.isEmpty()) return
        state.update { it.copy(loadingOlder = true) }
        scope.launch {
            val shown = current().filter { (account, _) -> account.id in behind }
            val outcomes = coroutineScope {
                shown.map { (account, key) -> async { account.id to further(account, key) } }.awaitAll()
            }
            val still = current().map { it.first.id }.toSet()
            state.update { now ->
                outcomes.filter { (id, _) -> id in still }.fold(now.copy(loadingOlder = false)) { acc, (id, outcome) ->
                    when (outcome) {
                        is PageOutcome.Loaded -> acc.copy(
                            troubles = acc.troubles - id,
                            ended = if (outcome.reachedEnd) acc.ended + id else acc.ended,
                        )

                        is PageOutcome.Failed -> acc.copy(troubles = acc.troubles + (id to outcome.error.trouble))

                        PageOutcome.Busy, null -> acc
                    }
                }
            }
        }
    }

    /**
     * What lies just below what [account] has: the gap its unbroken rows end at, filled from above, or the
     * next page down. Null when it has nothing to go on from.
     */
    private suspend fun further(account: SignedInAccount, key: TimelineKey): PageOutcome? {
        val rows = rowsOf(account)
        val gap = rows.indexOfFirst { it is TimelineRow.Gap }
        val unbroken = if (gap >= 0) rows.subList(0, gap) else rows
        val oldest = unbroken.lastOrNull { it is TimelineRow.Post }?.id ?: return null
        return if (gap >= 0) {
            timelines.fillGap(account, key, rows[gap].id, aboveId = oldest)
        } else {
            timelines.older(account, key, cursors[account.id], oldest).also { outcome ->
                (outcome as? PageOutcome.Loaded)?.nextCursor?.let { cursors[account.id] = it }
            }
        }
    }

    private suspend fun refresh(shown: List<Pair<SignedInAccount, TimelineKey>>) {
        if (state.value.refreshing || shown.isEmpty()) return
        state.update { it.copy(refreshing = true) }
        val outcomes = coroutineScope {
            shown.map { (account, key) ->
                val before = rowsOf(account)
                async { Triple(account.id, refreshed(account, key), before) }
            }.awaitAll()
        }
        val still = current().map { it.first.id }.toSet()
        state.update { now ->
            outcomes.filter { (id, _, _) -> id in still }
                .fold(now.copy(refreshing = false, loadedOnce = true)) { acc, (id, result, before) ->
                    acc.after(id, result, hadRows = before.isNotEmpty())
                }
        }
    }

    private suspend fun refreshed(account: SignedInAccount, key: TimelineKey): Pair<PageOutcome, RefreshPlan> {
        val newest = rowsOf(account).firstOrNull { it is TimelineRow.Post }?.id
        val plan = RefreshPlan.of(timelines.lastFetched(account, key) != null, newest, key.source.keptOrder)
        val outcome = timelines.refresh(account, key, plan)
        if (outcome is PageOutcome.Loaded && plan.direction == TimelineMerge.Direction.Cold) {
            outcome.nextCursor?.let { cursors[account.id] = it } ?: cursors.remove(account.id)
        }
        // a page the server was still filling in is read once more; what that brings waits behind the pill
        scope.launch {
            val refilled = timelines.refill(account, key, plan, outcome) ?: return@launch
            if (current().none { (shown, of) -> shown.id == account.id && of == key }) return@launch
            state.update { it.copy(held = it.held + (account.id to it.held[account.id].orEmpty() + refilled.arrived)) }
        }
        return outcome to plan
    }

    /** [id]'s refresh as it ended: what it brought over rows being read waits behind the pill. */
    private fun State.after(id: String, result: Pair<PageOutcome, RefreshPlan>, hadRows: Boolean): State {
        val (outcome, plan) = result
        return when (outcome) {
            is PageOutcome.Loaded -> {
                val holds = hadRows && plan.direction != TimelineMerge.Direction.Cold
                copy(
                    troubles = troubles - id,
                    held = if (holds) held + (id to held[id].orEmpty() + outcome.arrived) else held,
                    ended = if (!hadRows && outcome.reachedEnd) ended + id else ended,
                )
            }

            is PageOutcome.Failed -> copy(troubles = troubles + (id to outcome.error.trouble))

            PageOutcome.Busy -> this
        }
    }

    private companion object {
        val STALE_MILLIS = Duration.ofSeconds(60).toMillis()
    }
}
