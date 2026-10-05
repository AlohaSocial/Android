// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data.timeline

import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.Trouble
import social.aloha.core.data.trouble
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.TimelineKey

/**
 * One timeline as a screen reads it, whatever its mode: the stored rows, a refresh that holds what it
 * brings behind the pill while rows are being read, the next page further down, and gap filling. A
 * result that arrives after the screen moved to another account or timeline lands nowhere. One per
 * screen, living in its [scope]; [current] says what the screen shows now.
 */
public class TimelinePager(
    private val timelines: TimelineRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val current: suspend () -> Pair<SignedInAccount, TimelineKey>?,
) {
    /** What the timeline is doing, as opposed to what it holds. */
    public data class State(
        val refreshing: Boolean = false,
        val loadedOnce: Boolean = false,
        val trouble: Trouble? = null,
        val loadingOlder: Boolean = false,
        val reachedEnd: Boolean = false,
        val loadingGaps: Set<String> = emptySet(),
        val held: Set<String> = emptySet(),
    )

    private val state = MutableStateFlow(State())
    public val states: StateFlow<State> = state.asStateFlow()

    /**
     * The stored rows last seen, unfiltered, with the timeline they belong to: gaps are filled and pages
     * anchored from these. One value, so rows another timeline sent late never pass for this one's.
     */
    @Volatile private var seen: Pair<TimelineKey?, List<TimelineRow>> = null to emptyList()

    @Volatile private var cursor: HttpUrl? = null

    /** The rows seen last, for the timeline shown now. */
    public val stored: List<TimelineRow> get() = seen.second

    /** [key]'s stored rows as they change; what paging and gap filling start from. */
    public fun observe(account: SignedInAccount, key: TimelineKey): Flow<List<TimelineRow>> =
        timelines.observe(account, key).onEach { seen = key to it }

    /** The screen moved to [key]: nothing of the previous timeline's paging carries over. */
    public suspend fun start(account: SignedInAccount, key: TimelineKey) {
        state.value = State()
        cursor = null
        // the new timeline's rows may have come in already, ahead of the switch: those are kept
        if (seen.first != key) seen = null to emptyList()
        refreshIfStale(account, key)
    }

    /** Refreshes unless this timeline fetched within the last minute. */
    public suspend fun refreshIfStale(account: SignedInAccount, key: TimelineKey) {
        val last = timelines.lastFetched(account, key)
        if (last == null || clock.millis() - last > STALE_MILLIS) refresh(account, key)
    }

    /** The newest posts: over rows being read, they wait behind the pill. */
    public fun refresh() {
        scope.launch { current()?.let { (account, key) -> refresh(account, key) } }
    }

    /** What waited behind the pill joins the rows. */
    public fun reveal() {
        state.update { it.copy(held = emptySet()) }
    }

    /** The next page further down, unless one is on its way or there is none. */
    public fun older() {
        val now = state.value
        if (now.loadingOlder || now.reachedEnd) return
        val oldest = stored.lastOrNull { it is TimelineRow.Post }?.id ?: return
        state.update { it.copy(loadingOlder = true) }
        scope.launch {
            val (account, key) = current() ?: return@launch
            val outcome = timelines.older(account, key, cursor, oldest)
            if (!current().shows(account, key)) return@launch
            state.update { it.copy(loadingOlder = false) }
            when (outcome) {
                is PageOutcome.Loaded -> {
                    cursor = outcome.nextCursor
                    state.update { it.copy(reachedEnd = outcome.reachedEnd, trouble = null) }
                }

                is PageOutcome.Failed -> state.update { it.copy(trouble = outcome.error.trouble) }

                PageOutcome.Busy -> Unit
            }
        }
    }

    /** Fills [gapId] from just below the post over it. */
    public fun fillGap(gapId: String, fromBelow: Boolean = false) {
        val index = stored.indexOfFirst { it.id == gapId }.takeIf { it >= 0 } ?: return
        val above = stored.subList(0, index).lastOrNull { it is TimelineRow.Post }?.id
        val below = if (fromBelow) stored.drop(index + 1).firstOrNull { it is TimelineRow.Post }?.id else null
        state.update { it.copy(loadingGaps = it.loadingGaps + gapId) }
        scope.launch {
            val (account, key) = current() ?: return@launch
            val outcome = timelines.fillGap(account, key, gapId, above, below)
            if (!current().shows(account, key)) return@launch
            state.update { now ->
                now.copy(
                    loadingGaps = now.loadingGaps - gapId,
                    trouble = (outcome as? PageOutcome.Failed)?.error?.trouble,
                )
            }
        }
    }

    private suspend fun refresh(account: SignedInAccount, key: TimelineKey) {
        if (state.value.refreshing) return
        val own = seen.rowsOf(key)
        val hadRows = own.isNotEmpty()
        state.update { it.copy(refreshing = true) }
        val newest = own.firstOrNull { it is TimelineRow.Post }?.id
        val plan = RefreshPlan.of(timelines.lastFetched(account, key) != null, newest, key.source.keptOrder)
        val outcome = timelines.refresh(account, key, plan)
        // a reader who switched timeline or account meanwhile gets nothing of this one's paging
        if (!current().shows(account, key)) return
        state.update { now ->
            when (outcome) {
                is PageOutcome.Loaded -> {
                    if (plan.direction == TimelineMerge.Direction.Cold) cursor = outcome.nextCursor
                    // a refresh over rows being read holds what it brought behind the pill
                    val held = if (hadRows && plan.direction != TimelineMerge.Direction.Cold) {
                        now.held + outcome.arrived
                    } else {
                        now.held
                    }
                    now.copy(
                        refreshing = false,
                        loadedOnce = true,
                        trouble = null,
                        held = held,
                        reachedEnd = now.reachedEnd || (!hadRows && outcome.reachedEnd),
                    )
                }

                is PageOutcome.Failed -> now.copy(
                    refreshing = false,
                    loadedOnce = true,
                    trouble = outcome.error.trouble,
                )

                PageOutcome.Busy -> now.copy(refreshing = false)
            }
        }
    }

    private companion object {
        val STALE_MILLIS = Duration.ofSeconds(60).toMillis()
    }
}

/** Whether the timeline shown now is still [key] for [account], so a late result may land. */
private fun Pair<SignedInAccount, TimelineKey>?.shows(account: SignedInAccount, key: TimelineKey): Boolean =
    this != null && first.id == account.id && second == key

/** The rows seen, if they are [key]'s: right after a switch they may still be the previous timeline's. */
private fun Pair<TimelineKey?, List<TimelineRow>>.rowsOf(key: TimelineKey): List<TimelineRow> =
    if (first == key) second else emptyList()
