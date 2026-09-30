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
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.sync.TimelineSignals
import social.aloha.core.data.timeline.StatusInteractions
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelinePosition
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.timeline.Toggle
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.media.ImagePrefetcher
import social.aloha.core.model.FeedMode
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.Status
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineFilters
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.network.ApiError
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.minuteTicks

/**
 * A timeline screen, cache first: home, a media mode's, or a hashtag's. What is stored is drawn at once;
 * a fetch runs when the timeline has never fetched in this process or its last fetch is over a minute
 * old. Posts a refresh brings wait behind the pill until the person asks for them, so the list never
 * moves under a reader.
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
    private val preferences: AppPreferences,
    private val clock: Clock,
    private val prefetcher: ImagePrefetcher,
    private val signals: TimelineSignals,
) : ViewModel(),
    TimelineScreenActions {
    @AssistedFactory
    interface Factory {
        fun create(feed: TimelineFeed): TimelineViewModel
    }

    /** The screen's own requests, beside what the [pager] is doing. */
    private data class Control(
        val restoreTo: TimelineUiState.Restore? = null,
        val scrollToTop: Boolean = false,
        val actionFailed: Boolean = false,
    )

    private val colors = MutableStateFlow<RichTextColors?>(null)
    private val control = MutableStateFlow(Control())
    private val scrolled = MutableStateFlow<TimelinePosition?>(null)
    private val pager = TimelinePager(timelines, clock, viewModelScope) { current() }
    private var restoreJob: Job? = null
    private var prefetchedFrom = -1

    private val account: StateFlow<SignedInAccount?> =
        accounts.activeAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val storedSettings: Flow<AccountSettings> =
        account.filterNotNull().map { it.id }.distinctUntilChanged().flatMapLatest(settings::settings)

    /**
     * The reading settings. Hiding boosts and replies is home's: a mode shows what it is made of, and a
     * hashtag's timeline is read as it is.
     */
    private val accountSettings: Flow<AccountSettings> = when (feed) {
        TimelineFeed.Home -> storedSettings
        is TimelineFeed.Mode -> storedSettings.map { it.copy(showBoosts = true, showReplies = true) }
        is TimelineFeed.Tag -> flowOf(AccountSettings())
    }

    private val key: Flow<TimelineKey> = combine(account.filterNotNull(), accountSettings) { account, settings ->
        TimelineKey(feed.mode, sourceOf(account, settings))
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
    private data class Shown(
        val items: List<TimelineItem>,
        val pending: Int,
        val pendingAvatars: List<String?>,
        val scrollToTop: Boolean,
    )

    private val items: Flow<Shown> = combine(account.filterNotNull(), key) { account, key -> account to key }
        .distinctUntilChanged { a, b -> a.first.id == b.first.id && a.second == b.second }
        .flatMapLatest { (account, key) ->
            combine(
                pager.observe(account, key),
                rows.filters(account.id),
                colors.filterNotNull(),
                accountSettings,
                combine(pager.states, control) { paging, control ->
                    Shaping(paging.held, paging.loadingGaps, control.scrollToTop)
                }.distinctUntilChanged(),
            ) {
                    stored,
                    filters,
                    colors,
                    settings,
                    shaping,
                ->
                val shape = TimelineRowBuilder.Shape(colors, settings, filters, shaping.held, shaping.loadingGaps)
                val held = stored.filter { it.id in shaping.held }
                // the newest few who posted what waits, once each, for the pill to show
                val avatars = held.filterIsInstance<TimelineRow.Post>()
                    .map { it.status.displayed.account }
                    .distinctBy { it.id }
                    .take(PILL_AVATARS)
                    .map { it.avatar }
                Shown(rows.build(account, key.source, stored, shape), held.size, avatars, shaping.scrollToTop)
            }
        }
        .flowOn(Dispatchers.Default)

    /** Grid or feed, for Photos only: every other timeline is a list. */
    private val grid: Flow<Boolean?> = if (feed.mode == FeedMode.Photos) preferences.photosGrid else flowOf(null)

    val uiState: StateFlow<TimelineUiState> = combine(
        items,
        combine(pager.states, control, ::Pair),
        combine(accountSettings, swipes, grid, ::Triple),
        account.filterNotNull(),
        minuteTicks(clock),
    ) {
            shown,
            (paging, control),
            (settings, swipes, grid),
            account,
            now,
        ->
        TimelineUiState(
            source = sourceOf(account, settings),
            sources = if (feed is TimelineFeed.Tag) emptyList() else homeSources(account.capabilities),
            items = shown.items,
            loadedOnce = paging.loadedOnce,
            refreshing = paging.refreshing,
            pending = shown.pending,
            pendingAvatars = shown.pendingAvatars,
            trouble = paging.trouble,
            loadingOlder = paging.loadingOlder,
            reachedEnd = paging.reachedEnd,
            sparse = TimelineFilters.forMode(feed.mode, account.capabilities).isEmpty && feed.mode != FeedMode.Home,
            grid = grid,
            showBoosts = settings.showBoosts,
            showReplies = settings.showReplies,
            now = now,
            restoreTo = control.restoreTo,
            scrollToTop = shown.scrollToTop,
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
                    prefetchedFrom = -1
                    rows.clear()
                    scheduleRestore(account, key)
                    pager.start(account, key)
                    rows.refreshFilters(account)
                }
        }
        viewModelScope.launch {
            signals.timelineDue.collect { id ->
                // a timeline off screen is not fetched for; it refreshes itself when it comes back
                if (shownFor == null) return@collect
                current()?.takeIf { (account) -> account.id == id }?.let { pager.refresh() }
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
            viewModelScope.launch { current()?.let { (account, key) -> pager.refreshIfStale(account, key) } }
        } else {
            shownFor?.let { signals.noteShown(it, isShown = false) }
            shownFor = null
        }
    }

    /** Rows are rendered with the theme's colours, which only the screen knows. */
    fun onColors(value: RichTextColors) {
        colors.value = value
    }

    override fun onRefresh() = pager.refresh()

    override fun onRevealPending() {
        pager.reveal()
        control.update { it.copy(scrollToTop = true) }
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

    override fun onSource(source: TimelineSource) = when (feed) {
        TimelineFeed.Home -> updateSettings { it.copy(homeSource = source) }
        is TimelineFeed.Mode -> updateSettings { it.copy(modeSources = it.modeSources + (feed.mode.key to source)) }
        is TimelineFeed.Tag -> Unit
    }

    override fun onShowBoosts(show: Boolean) = updateSettings { it.copy(showBoosts = show) }

    override fun onShowReplies(show: Boolean) = updateSettings { it.copy(showReplies = show) }

    override fun onGrid(grid: Boolean) {
        viewModelScope.launch { preferences.setPhotosGrid(grid) }
    }

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
    override fun onNearEnd() = pager.older()

    override fun onFillGap(gapId: String) = pager.fillGap(gapId)

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

    /** Waits for the first rows, then asks the screen to scroll to where this timeline was left. */
    private fun scheduleRestore(account: SignedInAccount, key: TimelineKey) {
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            // the read marker is home's own: another mode's timeline of the same source starts where it was left
            val saved = positions.saved(account, key)
                ?: positions.homeMarker(account)?.takeIf { key == TimelineKey.home() }?.let { TimelinePosition(it, 0) }
            saved ?: return@launch
            val shown = items.first { it.items.isNotEmpty() }.items
            val index = shown.indexOfFirst { it.key == saved.statusId }
            if (index > 0) control.update { it.copy(restoreTo = TimelineUiState.Restore(index, saved.offset)) }
        }
    }

    private suspend fun savePosition(position: TimelinePosition) {
        val (account, key) = current() ?: return
        positions.save(account, key, position)
        if (key == TimelineKey.home()) positions.markHomeRead(account, position.statusId)
    }

    /** The chosen source where the server serves it; a choice it no longer serves falls back to Following. */
    private fun sourceOf(account: SignedInAccount, settings: AccountSettings): TimelineSource {
        val chosen = when (feed) {
            TimelineFeed.Home -> settings.homeSource
            is TimelineFeed.Mode -> settings.modeSources[feed.mode.key]
            is TimelineFeed.Tag -> return TimelineSource.Hashtag(feed.name)
        }
        return chosen?.takeIf { it in homeSources(account.capabilities) } ?: TimelineSource.Home
    }

    private companion object {
        const val PILL_AVATARS = 3
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
