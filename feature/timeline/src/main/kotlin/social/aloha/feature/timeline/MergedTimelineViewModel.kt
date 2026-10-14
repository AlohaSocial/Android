// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
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
import kotlinx.coroutines.flow.flowOf
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
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.model.FeedMode
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
 * Home's Following feed, or a mode's timeline, of every account together, the way a mail client shows all
 * inboxes ([MergedRows] says how); a mode is each account's own source for it. A refresh fetches every
 * account at once and holds what it brings behind the pill; scrolling on loads more from whichever
 * account is behind. Where the list was left is kept for it alone, and reading Following moves each
 * account's own read marker as far as its posts were read.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = MergedTimelineViewModel.Factory::class)
internal class MergedTimelineViewModel @AssistedInject constructor(
    @Assisted private val feed: TimelineFeed,
    allAccounts: AllAccounts,
    timelines: TimelineRepository,
    private val rows: MergedRows,
    private val interactions: StatusInteractions,
    private val positions: TimelinePositions,
    private val settings: AccountSettingsStore,
    preferences: AppPreferences,
    private val reading: ReadingPreferences,
    private val modes: ModePreferences,
    private val watching: WatchPositions,
    clock: Clock,
    private val signals: TimelineSignals,
) : ViewModel(),
    TimelineModel {
    @AssistedFactory
    interface Factory {
        fun create(feed: TimelineFeed): MergedTimelineViewModel
    }

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

    /** Where the merged list of [feed] keeps its place: one per mode, whatever source each account reads. */
    private val place = TimelineKey(feed.mode, TimelineSource.Home)

    @Volatile private var last = MergedRows.Built(emptyList(), emptyMap(), emptyMap(), emptyList(), emptyList())

    @Volatile private var stored: Map<String, List<TimelineRow>> = emptyMap()

    /** Each account shown with its timeline for [feed]: Following, or the source it chose for the mode. */
    private val keys: StateFlow<List<Pair<SignedInAccount, TimelineKey>>> = accounts.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(list.map { account -> settings.settings(account.id).map { account to keyFor(account, it) } }) {
                it.toList()
            }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Whether boosts in a row fold into one row of cards: Following's, where the reader chose it. */
    private val carousel: Flow<Boolean> = if (feed.mode == FeedMode.Home) {
        reading.style.map { it.boostCarousel }.distinctUntilChanged()
    } else {
        flowOf(false)
    }

    private val built: Flow<MergedRows.Built> = keys.filter { it.isNotEmpty() }.flatMapLatest { keys ->
        val list = keys.map { it.first }
        combine(
            pager.observe(keys),
            looks(list),
            colors.filterNotNull(),
            carousel,
            combine(pager.states, control, ::Pair),
        ) { stored, looks, colors, carousel, (paging, control) ->
            this.stored = stored
            val sources = keys.associate { (account, key) -> account.id to key.source }
            val built = rows.build(MergedRows.Input(list, stored, looks, sources), colors, paging)
            val items = caughtUp(built.items, control.caughtUpAt)
                .let { if (carousel) foldBoosts(it, control.expandedBoosts) else it }
            built.copy(items = items).also { last = it }
        }
    }.flowOn(Dispatchers.Default)

    /** Grid or feed, for Photos only; how far the account in use got in each video, for Video only. */
    private val layout: Flow<Pair<Boolean?, Map<String, Double>?>> = combine(
        if (feed.mode == FeedMode.Photos) modes.photosGrid else flowOf(null),
        if (feed.mode == FeedMode.Video) {
            accounts.map { it.firstOrNull()?.id }.distinctUntilChanged()
                .flatMapLatest { id -> id?.let(watching::observe) ?: flowOf(emptyMap()) }
        } else {
            flowOf(null)
        },
        ::Pair,
    )

    override val uiState: StateFlow<TimelineUiState> = combine(
        built,
        combine(pager.states, control, ::Pair),
        combine(preferences.swipeTowardsEnd, preferences.swipeTowardsStart, layout, ::Triple),
        accounts.filter { it.isNotEmpty() },
        minuteTicks(clock),
    ) { built, (paging, control), (towardsEnd, towardsStart, layout), accounts, now ->
        val swipes = towardsEnd to towardsStart
        TimelineUiState(
            grid = layout.first,
            watched = layout.second,
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
            // other accounts, or another source of one of them, start afresh
            keys.filter { it.isNotEmpty() }.distinctUntilChanged { a, b ->
                a.map { (account, key) -> account.id to key } == b.map { (account, key) -> account.id to key }
            }.collect { keys ->
                val list = keys.map { it.first }
                control.value = Control()
                // the poll keeps fresh the accounts on screen now, not those shown before
                if (shown) note(list.map { it.id })
                rows.keepOnly(list)
                scheduleRestore()
                pager.start(keys)
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

    /**
     * Each account's reading settings and filters, and the colour its rows are marked in. Hiding boosts
     * and replies is Following's: a mode shows what it is made of.
     */
    private fun looks(list: List<SignedInAccount>): Flow<Map<String, AccountLook>> = combine(
        list.map { account ->
            combine(settings.settings(account.id), rows.filters(account.id)) { own, filters ->
                val reading = if (feed.mode == FeedMode.Home) own else own.copy(showBoosts = true, showReplies = true)
                account.id to AccountLook(reading, filters, own.accent ?: account.capabilities.theme?.colour)
            }
        },
    ) { it.toMap() }

    /** [account]'s timeline for [feed]: Following, or the source it chose for the mode while its server serves it. */
    private fun keyFor(account: SignedInAccount, own: AccountSettings): TimelineKey {
        val chosen = if (feed.mode == FeedMode.Home) null else own.modeSources[feed.mode.key]
        val source = chosen?.takeIf { it in homeSources(account.capabilities) } ?: TimelineSource.Home
        return TimelineKey(feed.mode, source)
    }

    private fun shownKeys(): List<Pair<SignedInAccount, TimelineKey>> = keys.value

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

    // each account reads its own source, chosen on its own; gaps are filled as the list is scrolled
    override fun onSource(source: TimelineSource) = Unit

    override fun onGrid(grid: Boolean) {
        viewModelScope.launch { modes.setPhotosGrid(grid) }
    }

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

    /** The account a shown post belongs to, and the id that account stores it under, a boost's own for a boost. */
    fun heldAs(statusId: String): Pair<String, String>? {
        val (owner, own) = last.owners[statusId] ?: return null
        val status = stored[owner].orEmpty().firstNotNullOfOrNull { row ->
            (row as? TimelineRow.Post)?.status?.takeIf { it.displayed.id == own }
        } ?: return null
        return owner to status.id
    }

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
            val saved = positions.savedMerged(place) ?: return@launch
            control.update { it.copy(caughtUpAt = saved.statusId) }
            if (!reading.restorePosition.first()) return@launch
            val items = built.first { it.items.isNotEmpty() }.items
            val index = items.indexOfFirst { it.shows(saved.statusId) }
            if (index > 0) control.update { it.copy(restoreTo = TimelineUiState.Restore(index, saved.offset)) }
        }
    }

    /** Keeps where the list is, and on Following moves each account's read marker down to what was read of it. */
    private suspend fun savePosition(position: TimelinePosition) {
        positions.saveMerged(place, position)
        if (feed.mode != FeedMode.Home) return
        val at = last.times[position.statusId] ?: return
        val byId = accounts.value.associateBy { it.id }
        MergedTimeline.readUpTo(stored, at).forEach { (id, statusId) ->
            byId[id]?.let { positions.markHomeRead(it, statusId) }
        }
    }

    private companion object {
        const val PILL_AVATARS = 3
        const val STOP_MILLIS = 5_000L
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}
