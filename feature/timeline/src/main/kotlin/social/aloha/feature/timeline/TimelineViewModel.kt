// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.size.Size
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Trouble
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.timeline.PageOutcome
import social.aloha.core.data.timeline.RefreshPlan
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.TimelineMerge
import social.aloha.core.data.timeline.TimelinePosition
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.data.trouble
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.media.ImagePrefetcher
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
 * The home screen's timeline, cache first. What is stored is drawn at once; a fetch runs when the
 * timeline has never fetched in this process or its last fetch is over a minute old. Posts a refresh
 * brings wait behind the pill until the person asks for them, so the list never moves under a reader.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = TimelineViewModel.Factory::class)
internal class TimelineViewModel @AssistedInject constructor(
    @Assisted private val feed: TimelineFeed,
    accounts: AccountRepository,
    private val timelines: TimelineRepository,
    private val rows: TimelineRowBuilder,
    private val interactions: StatusInteractions,
    private val positions: TimelinePositions,
    private val settings: AccountSettingsStore,
    preferences: AppPreferences,
    private val clock: Clock,
    private val prefetcher: ImagePrefetcher,
    private val signals: TimelineSignals,
) : ViewModel(),
    TimelineScreenActions {
    @AssistedFactory
    interface Factory {
        fun create(feed: TimelineFeed): TimelineViewModel
    }

    /** What the timeline is doing, as opposed to what it holds. */
    private data class Control(
        val refreshing: Boolean = false,
        val loadedOnce: Boolean = false,
        val trouble: Trouble? = null,
        val loadingOlder: Boolean = false,
        val reachedEnd: Boolean = false,
        val loadingGaps: Set<String> = emptySet(),
        val held: Set<String> = emptySet(),
        val restoreTo: TimelineUiState.Restore? = null,
        val scrollToTop: Boolean = false,
        val actionFailed: Boolean = false,
    )

    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val control = MutableStateFlow(Control())
    private val scrolled = MutableStateFlow<TimelinePosition?>(null)

    /** The stored rows last seen, unfiltered: gaps are filled and pages anchored from these. */
    @Volatile private var stored: List<TimelineRow> = emptyList()

    @Volatile private var cursor: HttpUrl? = null
    private var restoreJob: Job? = null
    private var prefetchedFrom = -1

    private val account: StateFlow<SignedInAccount?> =
        accounts.activeAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The reading settings; a hashtag's timeline is read as it is, with nothing of home's hidden. */
    private val accountSettings: Flow<AccountSettings> = when (feed) {
        TimelineFeed.Home -> account.filterNotNull().map {
            it.id
        }.distinctUntilChanged().flatMapLatest(settings::settings)

        is TimelineFeed.Tag -> flowOf(AccountSettings())
    }

    private val key: Flow<TimelineKey> = combine(account.filterNotNull(), accountSettings) { account, settings ->
        TimelineKey(FeedMode.Home, sourceOf(account, settings))
    }.distinctUntilChanged()

    /** What a swipe does each way, as the settings chose for the device. */
    private val swipes: Flow<Pair<SwipeAction, SwipeAction>> =
        combine(preferences.swipeTowardsEnd, preferences.swipeTowardsStart) { end, start -> end to start }

    /** What of [Control] the rows are built from; the rest changes the screen without rebuilding them. */
    private data class Shaping(val held: Set<String>, val loadingGaps: Set<String>, val scrollToTop: Boolean)

    /**
     * The rows with what they were shaped by, so a revealed pill, its rows and the scroll to them arrive
     * together; the pill counts only held posts already stored, never ones a reveal could not show yet.
     */
    private data class Shown(val items: List<TimelineItem>, val pending: Int, val scrollToTop: Boolean)

    private val items: Flow<Shown> = combine(account.filterNotNull(), key) { account, key -> account to key }
        .distinctUntilChanged { a, b -> a.first.id == b.first.id && a.second == b.second }
        .flatMapLatest { (account, key) ->
            combine(
                timelines.observe(account, key),
                rows.filters(account.id),
                colors.filterNotNull(),
                accountSettings,
                control.map { Shaping(it.held, it.loadingGaps, it.scrollToTop) }.distinctUntilChanged(),
            ) {
                    stored,
                    filters,
                    colors,
                    settings,
                    shaping,
                ->
                this.stored = stored
                val shape = TimelineRowBuilder.Shape(colors, settings, filters, shaping.held, shaping.loadingGaps)
                val pending = stored.count { it.id in shaping.held }
                Shown(rows.build(account, key.source, stored, shape), pending, shaping.scrollToTop)
            }
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<TimelineUiState> = combine(
        items,
        control,
        combine(accountSettings, swipes, ::Pair),
        account.filterNotNull(),
        minuteTicks(clock),
    ) {
            (items, pending, scrollToTop),
            control,
            (settings, swipes),
            account,
            now,
        ->
        TimelineUiState(
            source = sourceOf(account, settings),
            sources = if (feed == TimelineFeed.Home) homeSources(account.capabilities) else emptyList(),
            items = items,
            loadedOnce = control.loadedOnce,
            refreshing = control.refreshing,
            pending = pending,
            trouble = control.trouble,
            loadingOlder = control.loadingOlder,
            reachedEnd = control.reachedEnd,
            showBoosts = settings.showBoosts,
            showReplies = settings.showReplies,
            now = now,
            restoreTo = control.restoreTo,
            scrollToTop = scrollToTop,
            actionFailed = control.actionFailed,
            swipeTowardsEnd = swipes.first,
            swipeTowardsStart = swipes.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), TimelineUiState())

    init {
        viewModelScope.launch { scrolled.filterNotNull().debounce(SAVE_DEBOUNCE_MILLIS).collect(::savePosition) }
        viewModelScope.launch {
            // a new source, or a switch of account, starts where that timeline was left
            combine(account.filterNotNull(), key) { account, key -> account to key }
                .distinctUntilChanged { a, b -> a.first.id == b.first.id && a.second == b.second }
                .collect { (account, key) ->
                    control.value = Control()
                    // a timeline on screen that switched account is now kept fresh for the new one
                    shownFor?.takeIf { it != account.id }?.let {
                        signals.noteShown(it, isShown = false)
                        shownFor = account.id
                        signals.noteShown(account.id, isShown = true)
                    }
                    cursor = null
                    prefetchedFrom = -1
                    stored = emptyList()
                    rows.clear()
                    scheduleRestore(account, key)
                    refreshIfStale(account, key)
                    rows.refreshFilters(account)
                }
        }
        viewModelScope.launch {
            signals.timelineDue.collect { id ->
                // a timeline off screen is not fetched for; it refreshes itself when it comes back
                if (shownFor == null) return@collect
                current()?.takeIf { (account) -> account.id == id }?.let { (account, key) -> refresh(account, key) }
            }
        }
    }

    private var shownFor: String? = null

    /**
     * The screen came into view or left it. Coming into view refreshes unless this timeline fetched
     * within the last minute; while in view, the poll keeps it fresh.
     */
    fun onShown(isShown: Boolean) {
        if (isShown) {
            shownFor = account.value?.id?.also { signals.noteShown(it, isShown = true) }
            viewModelScope.launch { current()?.let { (account, key) -> refreshIfStale(account, key) } }
        } else {
            shownFor?.let { signals.noteShown(it, isShown = false) }
            shownFor = null
        }
    }

    /** Rows are rendered with the theme's colours, which only the screen knows. */
    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    override fun onRefresh() {
        viewModelScope.launch { current()?.let { (account, key) -> refresh(account, key) } }
    }

    override fun onRevealPending() {
        control.update { it.copy(held = emptySet(), scrollToTop = true) }
    }

    override fun onScrolledToTop() {
        control.update { it.copy(scrollToTop = false) }
    }

    override fun onRestored() {
        control.update { it.copy(restoreTo = null) }
    }

    fun onActionFailureShown() {
        control.update { it.copy(actionFailed = false) }
    }

    override fun onSource(source: TimelineSource) {
        viewModelScope.launch { account.value?.let { settings.update(it.id) { s -> s.copy(homeSource = source) } } }
    }

    override fun onShowBoosts(show: Boolean) = updateSettings { it.copy(showBoosts = show) }

    override fun onShowReplies(show: Boolean) = updateSettings { it.copy(showReplies = show) }

    private fun updateSettings(change: (AccountSettings) -> AccountSettings) {
        viewModelScope.launch { account.value?.let { settings.update(it.id, change) } }
    }

    /** The row now at the top of the screen, and how far it is scrolled past its top edge. */
    override fun onScrolled(rowId: String, offset: Int) {
        scrolled.value = TimelinePosition(rowId, offset)
        // the images of the rows just below the screen, fetched once each time the top row changes
        val shown = uiState.value.items
        val index = shown.indexOfFirst { it.key == rowId }
        if (index < 0 || index == prefetchedFrom) return
        prefetchedFrom = index
        prefetcher.visible(index, shown.map(::imagesOf), PREFETCH_SIZE)
    }

    /** The list came within ten rows of its end: the next page, unless one is on its way or there is none. */
    override fun onNearEnd() {
        val state = control.value
        if (state.loadingOlder || state.reachedEnd) return
        val oldest = stored.lastOrNull { it is TimelineRow.Post }?.id ?: return
        control.update { it.copy(loadingOlder = true) }
        viewModelScope.launch {
            val (account, key) = current() ?: return@launch
            val outcome = timelines.older(account, key, cursor, oldest)
            if (!current().shows(account, key)) return@launch
            control.update { it.copy(loadingOlder = false) }
            when (outcome) {
                is PageOutcome.Loaded -> {
                    cursor = outcome.nextCursor
                    control.update { it.copy(reachedEnd = outcome.reachedEnd, trouble = null) }
                }

                is PageOutcome.Failed -> control.update { it.copy(trouble = outcome.error.trouble) }

                PageOutcome.Busy -> Unit
            }
        }
    }

    override fun onFillGap(gapId: String) {
        val index = stored.indexOfFirst { it.id == gapId }.takeIf { it >= 0 } ?: return
        val above = stored.subList(0, index).lastOrNull { it is TimelineRow.Post }?.id
        control.update { it.copy(loadingGaps = it.loadingGaps + gapId) }
        viewModelScope.launch {
            val (account, key) = current() ?: return@launch
            val outcome = timelines.fillGap(account, key, gapId, above)
            if (!current().shows(account, key)) return@launch
            control.update { state ->
                state.copy(
                    loadingGaps = state.loadingGaps - gapId,
                    trouble = (outcome as? PageOutcome.Failed)?.error?.trouble,
                )
            }
        }
    }

    override fun onSwipe(row: StatusRowUi, action: SwipeAction) {
        when (action) {
            SwipeAction.Favourite -> onToggle(row.statusId, Toggle.Favourite)
            SwipeAction.Boost -> onToggle(row.statusId, Toggle.Boost)
            SwipeAction.Bookmark -> onToggle(row.statusId, Toggle.Bookmark)
            SwipeAction.Reply, SwipeAction.None -> Unit
        }
    }

    fun onToggle(statusId: String, toggle: Toggle) = act(statusId) { account, status ->
        interactions.toggle(account, status, toggle)
    }

    fun onVote(statusId: String, choices: List<Int>) = act(statusId) { account, status ->
        interactions.vote(account, status, choices)
    }

    fun onDelete(statusId: String) = act(statusId) { account, status -> interactions.delete(account, status) }

    private fun act(statusId: String, block: suspend (SignedInAccount, Status) -> ApiError?) {
        val status = rows.statusFor(statusId) ?: return
        viewModelScope.launch {
            val account = account.value ?: return@launch
            if (block(account, status) != null) control.update { it.copy(actionFailed = true) }
        }
    }

    private suspend fun current(): Pair<SignedInAccount, TimelineKey>? {
        val account = account.value ?: return null
        return account to key.first()
    }

    private suspend fun refreshIfStale(account: SignedInAccount, key: TimelineKey) {
        val last = timelines.lastFetched(account, key)
        if (last == null || clock.millis() - last > STALE_MILLIS) refresh(account, key)
    }

    private suspend fun refresh(account: SignedInAccount, key: TimelineKey) {
        if (control.value.refreshing) return
        val hadRows = stored.isNotEmpty()
        control.update { it.copy(refreshing = true) }
        val plan = RefreshPlan.of(
            timelines.lastFetched(account, key) != null,
            stored.firstOrNull {
                it is TimelineRow.Post
            }?.id,
        )
        val outcome = timelines.refresh(account, key, plan)
        // a reader who switched timeline or account meanwhile gets nothing of this one's paging
        if (!current().shows(account, key)) return
        control.update { state ->
            when (outcome) {
                is PageOutcome.Loaded -> {
                    if (plan.direction == TimelineMerge.Direction.Cold) cursor = outcome.nextCursor
                    // a refresh over rows being read holds what it brought behind the pill
                    val held = if (hadRows && plan.direction != TimelineMerge.Direction.Cold) {
                        state.held + outcome.arrived
                    } else {
                        state.held
                    }
                    state.copy(
                        refreshing = false,
                        loadedOnce = true,
                        trouble = null,
                        held = held,
                        reachedEnd =
                            state.reachedEnd || (!hadRows && outcome.reachedEnd),
                    )
                }

                is PageOutcome.Failed -> state.copy(
                    refreshing = false,
                    loadedOnce = true,
                    trouble = outcome.error.trouble,
                )

                PageOutcome.Busy -> state.copy(refreshing = false)
            }
        }
    }

    /** Waits for the first rows, then asks the screen to scroll to where this timeline was left. */
    private fun scheduleRestore(account: SignedInAccount, key: TimelineKey) {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            val saved = positions.saved(account, key)
                ?: if (key.source ==
                    TimelineSource.Home
                ) {
                    positions.homeMarker(account)?.let { TimelinePosition(it, 0) }
                } else {
                    null
                }
            saved ?: return@launch
            val shown = items.first { it.items.isNotEmpty() }.items
            val index = shown.indexOfFirst { it.key == saved.statusId }
            if (index > 0) control.update { it.copy(restoreTo = TimelineUiState.Restore(index, saved.offset)) }
        }
    }

    private suspend fun savePosition(position: TimelinePosition) {
        val (account, key) = current() ?: return
        positions.save(account, key, position)
        if (key.source == TimelineSource.Home) positions.markHomeRead(account, position.statusId)
    }

    /** The chosen source where the server serves it; a choice it no longer serves falls back to Following. */
    private fun sourceOf(account: SignedInAccount, settings: AccountSettings): TimelineSource = when (feed) {
        TimelineFeed.Home -> settings.homeSource.takeIf { it in homeSources(account.capabilities) }
            ?: TimelineSource.Home

        is TimelineFeed.Tag -> TimelineSource.Hashtag(feed.name)
    }

    private companion object {
        val STALE_MILLIS = Duration.ofSeconds(60).toMillis()
        val PREFETCH_SIZE = Size(PREFETCH_PIXELS, PREFETCH_PIXELS)
        const val PREFETCH_PIXELS = 480
        const val STOP_MILLIS = 5_000L
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}

private fun imagesOf(item: TimelineItem): List<String> = when (item) {
    is TimelineItem.Post -> listOfNotNull(item.row.author.avatarUrl) + item.row.media.mapNotNull { it.previewUrl }
    is TimelineItem.Gap -> emptyList()
}

/** Whether the timeline shown now is still [key] for [account], so a late result may land. */
private fun Pair<SignedInAccount, TimelineKey>?.shows(account: SignedInAccount, key: TimelineKey): Boolean =
    this != null && first.id == account.id && second == key
