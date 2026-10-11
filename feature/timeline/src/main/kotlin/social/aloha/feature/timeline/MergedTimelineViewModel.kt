// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AllAccounts
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.timeline.MergedPager
import social.aloha.core.data.timeline.MergedTimeline
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.TimelinePosition
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.ApiError
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.minuteTicks

/**
 * Home's Following feed of every account together, the way a mail client shows all inboxes ([MergedRows]
 * says how). A refresh fetches every account at once and holds what it brings behind the pill; scrolling
 * on loads more from whichever account is behind. Where the list was left is kept for it alone, and
 * reading moves each account's own read marker as far as its posts were read.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class MergedTimelineViewModel @Inject constructor(
    allAccounts: AllAccounts,
    timelines: TimelineRepository,
    private val rows: MergedRows,
    private val interactions: StatusInteractions,
    private val positions: TimelinePositions,
    private val settings: AccountSettingsStore,
    preferences: AppPreferences,
    private val reading: ReadingPreferences,
    clock: Clock,
    private val signals: TimelineSignals,
) : ViewModel(),
    TimelineModel {
    /** The screen's own requests, beside what the [pager] is doing. */
    private data class Control(
        val restoreTo: TimelineUiState.Restore? = null,
        val scrollToTop: Boolean = false,
        val actionFailed: Boolean = false,
        /** The row read up to before this visit, above which the caught-up line goes; fixed for the visit. */
        val caughtUpAt: String? = null,
        val expandedBoosts: Set<String> = emptySet(),
    )

    private val accounts: StateFlow<List<SignedInAccount>> =
        allAccounts.shown.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val control = MutableStateFlow(Control())
    private val scrolled = MutableStateFlow<TimelinePosition?>(null)
    private val pager = MergedPager(timelines, clock, viewModelScope) { shownKeys() }
    private var restoreJob: Job? = null
    private var shown = false
    private var noted = emptySet<String>()

    @Volatile private var last = MergedRows.Built(emptyList(), emptyMap(), emptyMap(), emptyList(), emptyList())

    @Volatile private var stored: Map<String, List<TimelineRow>> = emptyMap()

    private val carousel: Flow<Boolean> = reading.style.map { it.boostCarousel }.distinctUntilChanged()

    private val built: Flow<MergedRows.Built> = accounts.filter { it.isNotEmpty() }.flatMapLatest { list ->
        combine(
            pager.observe(list.map { it to HOME }),
            looks(list),
            colors.filterNotNull(),
            carousel,
            combine(pager.states, control, ::Pair),
        ) { stored, looks, colors, carousel, (paging, control) ->
            this.stored = stored
            val built = rows.build(MergedRows.Input(list, stored, looks), colors, paging)
            val items = caughtUp(built.items, control.caughtUpAt)
                .let { if (carousel) foldBoosts(it, control.expandedBoosts) else it }
            built.copy(items = items).also { last = it }
        }
    }.flowOn(Dispatchers.Default)

    override val uiState: StateFlow<TimelineUiState> = combine(
        built,
        combine(pager.states, control, ::Pair),
        combine(preferences.swipeTowardsEnd, preferences.swipeTowardsStart, ::Pair),
        accounts.filter { it.isNotEmpty() },
        minuteTicks(clock),
    ) { built, (paging, control), swipes, accounts, now ->
        TimelineUiState(
            sources = emptyList(),
            items = built.items,
            loadedOnce = paging.loadedOnce,
            refreshing = paging.refreshing,
            pending = built.held.size,
            pendingAvatars = built.held.map { it.displayed.account }.distinctBy { it.avatar }.take(PILL_AVATARS)
                .map { it.avatar },
            trouble = accounts.firstNotNullOfOrNull { paging.troubles[it.id] },
            loadingOlder = paging.loadingOlder,
            reachedEnd = paging.loadedOnce && built.behind.isEmpty(),
            albums = accounts.first().capabilities.collections,
            now = now,
            restoreTo = control.restoreTo,
            scrollToTop = control.scrollToTop,
            actionFailed = control.actionFailed,
            swipeTowardsEnd = swipes.first,
            swipeTowardsStart = swipes.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), TimelineUiState())

    init {
        viewModelScope.launch { scrolled.filterNotNull().debounce(SAVE_DEBOUNCE_MILLIS).collect(::savePosition) }
        viewModelScope.launch {
            // without the pill, what a refresh brings joins the list at once
            combine(pager.states.map { it.held.isNotEmpty() }, reading.newPostsPill) { held, pill -> held && !pill }
                .distinctUntilChanged()
                .collect { if (it) pager.reveal() }
        }
        viewModelScope.launch {
            accounts.filter { it.isNotEmpty() }.distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
                .collect { list ->
                    control.value = Control()
                    // the poll keeps fresh the accounts on screen now, not those shown before
                    if (shown) note(list.map { it.id })
                    rows.keepOnly(list)
                    scheduleRestore()
                    pager.start(list.map { it to HOME })
                    list.forEach { rows.refreshFilters(it) }
                }
        }
        viewModelScope.launch {
            signals.timelineDue.collect { id ->
                // off screen nothing is fetched for; coming back refreshes
                if (shown && accounts.value.any { it.id == id }) pager.refresh()
            }
        }
    }

    /** Each account's reading settings and filters, and the colour its rows are marked in. */
    private fun looks(list: List<SignedInAccount>): Flow<Map<String, AccountLook>> = combine(
        list.map { account ->
            combine(settings.settings(account.id), rows.filters(account.id)) { own, filters ->
                account.id to AccountLook(own, filters, own.accent ?: account.capabilities.theme?.colour)
            }
        },
    ) { it.toMap() }

    private fun shownKeys(): List<Pair<SignedInAccount, TimelineKey>> = accounts.value.map { it to HOME }

    override fun onShown(isShown: Boolean) {
        shown = isShown
        note(if (isShown) accounts.value.map { it.id } else emptyList())
        if (isShown) viewModelScope.launch { pager.refreshIfStale(shownKeys()) }
    }

    /** Tells the poll that [ids]' timelines are on screen, and no other's this screen noted before. */
    private fun note(ids: List<String>) {
        (noted - ids.toSet()).forEach { signals.noteShown(it, isShown = false) }
        (ids - noted).forEach { signals.noteShown(it, isShown = true) }
        noted = ids.toSet()
    }

    override fun onColors(value: RichTextColors) {
        colors.value = value
    }

    override fun onRefresh() = pager.refresh()

    override fun onRevealPending() {
        pager.reveal()
        control.update { it.copy(scrollToTop = true) }
    }

    override fun onCaughtUp() = control.update { it.copy(scrollToTop = true) }

    override fun onScrolledToTop() = control.update { it.copy(scrollToTop = false) }

    override fun onRestored() = control.update { it.copy(restoreTo = null) }

    override fun onActionFailureShown() = control.update { it.copy(actionFailed = false) }

    // the merged feed is Following; a source, a layout or a gap is each account's own timeline's
    override fun onSource(source: TimelineSource) = Unit

    override fun onGrid(grid: Boolean) = Unit

    override fun onFillGap(gapId: String, fromBelow: Boolean) = Unit

    override fun onShowBoosts(show: Boolean) = updateEvery { it.copy(showBoosts = show) }

    override fun onShowReplies(show: Boolean) = updateEvery { it.copy(showReplies = show) }

    private fun updateEvery(change: (AccountSettings) -> AccountSettings) {
        viewModelScope.launch { accounts.value.forEach { settings.update(it.id, change) } }
    }

    override fun onScrolled(rowId: String, offset: Int) {
        scrolled.value = TimelinePosition(rowId, offset)
    }

    /** The list came near its end: the next page from the accounts that are behind. */
    override fun onNearEnd() = pager.older(last.behind)

    override fun onExpandBoosts(key: String) = control.update { it.copy(expandedBoosts = it.expandedBoosts + key) }

    override fun onSwipe(row: StatusRowUi, action: SwipeAction) {
        when (action) {
            SwipeAction.Favourite -> onToggle(row.statusId, Toggle.Favourite)
            SwipeAction.Boost -> onToggle(row.statusId, Toggle.Boost)
            SwipeAction.Bookmark -> onToggle(row.statusId, Toggle.Bookmark)
            SwipeAction.Reply, SwipeAction.None -> Unit
        }
    }

    /** The account a shown post acts as, by the id it shows under. */
    fun ownerOf(statusId: String): String? = last.owners[statusId]?.first

    override fun onToggle(statusId: String, toggle: Toggle) = act(statusId) { account, status ->
        interactions.toggle(account, status, toggle)
    }

    override fun onVote(statusId: String, choices: List<Int>) = act(statusId) { account, status ->
        interactions.vote(account, status, choices)
    }

    override fun onDelete(statusId: String) = act(statusId) { account, status -> interactions.delete(account, status) }

    /** [block] as the account the post acts as, on the post as that account holds it. */
    private fun act(statusId: String, block: suspend (SignedInAccount, Status) -> ApiError?) {
        val (owner, own) = last.owners[statusId] ?: return
        val account = accounts.value.firstOrNull { it.id == owner } ?: return
        // as stored, not as shown: a post shown as another account names people under that server's name
        val status = stored[owner].orEmpty().firstNotNullOfOrNull { row ->
            (row as? TimelineRow.Post)?.status?.displayed?.takeIf { it.id == own }
        } ?: return
        viewModelScope.launch {
            if (block(account, status) != null) control.update { it.copy(actionFailed = true) }
        }
    }

    /** Marks where the previous visit ended, then, for a reader who restores, scrolls there once rows come. */
    private fun scheduleRestore() {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            val saved = positions.savedMerged(HOME) ?: return@launch
            control.update { it.copy(caughtUpAt = saved.statusId) }
            if (!reading.restorePosition.first()) return@launch
            val items = built.first { it.items.isNotEmpty() }.items
            val index = items.indexOfFirst { it.shows(saved.statusId) }
            if (index > 0) control.update { it.copy(restoreTo = TimelineUiState.Restore(index, saved.offset)) }
        }
    }

    /** Keeps where the list is, and moves each account's read marker down to what was read of its posts. */
    private suspend fun savePosition(position: TimelinePosition) {
        positions.saveMerged(HOME, position)
        val at = last.times[position.statusId] ?: return
        val byId = accounts.value.associateBy { it.id }
        MergedTimeline.readUpTo(stored, at).forEach { (id, statusId) ->
            byId[id]?.let { positions.markHomeRead(it, statusId) }
        }
    }

    private companion object {
        val HOME = TimelineKey.home()
        const val PILL_AVATARS = 3
        const val STOP_MILLIS = 5_000L
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}
