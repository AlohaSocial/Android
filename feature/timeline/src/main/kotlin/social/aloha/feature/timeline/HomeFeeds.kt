// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.compose.Outbox
import social.aloha.core.data.timeline.PinnedFeeds
import social.aloha.core.data.timeline.TimelinePositions
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.OutboxState
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.SignedInAccount
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.rememberReducedMotion

/**
 * Home: the reader's pinned feeds, one page each. [unread] holds the feeds with posts above where the
 * reader left them, as last asked. A swipe moves between the pages only where no swipe acts on a post,
 * which would otherwise take every sideways drag that starts on one.
 */
@Immutable
internal data class HomeFeedsState(
    val feeds: List<PinnedFeed> = emptyList(),
    val unread: Set<String> = emptySet(),
    val explained: Set<String> = emptySet(),
    val showBoosts: Boolean = true,
    val showReplies: Boolean = true,
    val swipeBetween: Boolean = false,
    val titleNext: Boolean = false,
    /** The reader has a draft put aside. */
    val draft: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class HomeFeedsViewModel @Inject constructor(
    accounts: AccountRepository,
    pinned: PinnedFeeds,
    private val settings: AccountSettingsStore,
    private val timelines: TimelineRepository,
    private val positions: TimelinePositions,
    preferences: AppPreferences,
    reading: ReadingPreferences,
    outbox: Outbox,
) : ViewModel(),
    HomeActions {
    private val account = accounts.activeAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val unread = MutableStateFlow(emptySet<String>())
    private val noSwipes = combine(preferences.swipeTowardsEnd, preferences.swipeTowardsStart) { end, start ->
        end == SwipeAction.None && start == SwipeAction.None
    }

    val state: StateFlow<HomeFeedsState> = account.filterNotNull().flatMapLatest { reader ->
        val drafts = outbox.observe(reader.id).map { list -> list.any { it.state == OutboxState.Draft } }
        combine(
            pinned.feeds(reader),
            settings.settings(reader.id),
            unread,
            combine(noSwipes, reading.titleNextFeed, ::Pair),
            drafts.distinctUntilChanged(),
        ) { feeds, own, dots, (swipe, next), draft ->
            HomeFeedsState(feeds, dots, own.explainedFeeds, own.showBoosts, own.showReplies, swipe, next, draft)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), HomeFeedsState())

    /** The feeds are about to be listed: which hold posts above where the reader left them. */
    override fun onListed() {
        val reader = account.value ?: return
        viewModelScope.launch {
            unread.value = state.value.feeds.filter { hasNew(reader, it) }.mapTo(mutableSetOf()) { it.id }
        }
    }

    override fun onShowBoosts(show: Boolean) = update { it.copy(showBoosts = show) }

    override fun onShowReplies(show: Boolean) = update { it.copy(showReplies = show) }

    override fun onExplained(feed: PinnedFeed) = update { it.copy(explainedFeeds = it.explainedFeeds + feed.kindKey) }

    /** A feed never read has no place to be ahead of, so it shows no news either. */
    private suspend fun hasNew(reader: SignedInAccount, feed: PinnedFeed): Boolean {
        val key = TimelineKey.home(feed.source)
        val top = timelines.observe(reader, key, limit = TOP_ROWS).first().firstOrNull { it is TimelineRow.Post }
        val left = positions.saved(reader, key)?.statusId
        return top != null && left != null && top.id != left
    }

    private fun update(change: (AccountSettings) -> AccountSettings) {
        val reader = account.value ?: return
        viewModelScope.launch { settings.update(reader.id, change) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
        const val TOP_ROWS = 3
    }
}

/** What Home's bar and pages ask for beyond each page's own timeline. */
internal interface HomeActions {
    fun onListed()
    fun onShowBoosts(show: Boolean)
    fun onShowReplies(show: Boolean)
    fun onExplained(feed: PinnedFeed)
}

/** Home: the reader's pinned feeds, each its own timeline. */
@Composable
public fun HomeRoute(
    navigation: StatusNavigation,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    onSearch: (() -> Unit)? = null,
    onEditFeeds: (() -> Unit)? = null,
    onComposeAs: ((accountId: String) -> Unit)? = null,
    onCatchUp: (() -> Unit)? = null,
) {
    val viewModel: HomeFeedsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state,
        viewModel,
        HomeChrome(
            navigationIcon,
            header,
            onSearch,
            onEditFeeds,
            { navigation.openComposer(null) },
            onComposeAs,
            onCatchUp,
        ),
        modifier,
    ) { feed, list, banner ->
        TimelineRoute(
            navigation,
            feed = TimelineFeed.Pinned(feed.source),
            listState = list,
            header = banner,
            onCatchUp = onCatchUp.takeIf { feed.source == TimelineSource.Home },
        )
    }
}

/** What the app puts around Home's pages: its button, what goes above them, search, editing, writing. */
internal class HomeChrome(
    val navigationIcon: @Composable () -> Unit = {},
    val header: @Composable () -> Unit = {},
    val onSearch: (() -> Unit)? = null,
    val onEditFeeds: (() -> Unit)? = null,
    val onCompose: () -> Unit = {},
    val onComposeAs: ((accountId: String) -> Unit)? = null,
    val onCatchUp: (() -> Unit)? = null,
)

/**
 * Home's pinned feeds, a page each under one bar whose title names the feed shown and lists the others;
 * Back on any but the first feed returns to the first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeFeedsState,
    actions: HomeActions,
    chrome: HomeChrome,
    modifier: Modifier = Modifier,
    page: @Composable (feed: PinnedFeed, list: LazyListState, banner: @Composable () -> Unit) -> Unit,
) {
    val feeds = state.feeds
    if (feeds.isEmpty()) return
    // the pager counts and keys its pages from the same list, the newest, read as it asks: a feed added in
    // Edit feeds must not be counted before the list holding it is the one keyed
    val shown by rememberUpdatedState(feeds)
    val pager = rememberPagerState { shown.size }
    val scope = rememberCoroutineScope()
    // each page keeps where it was while the feeds around it are renamed, reordered or added to
    val kept = remember { HashMap<String, LazyListState>() }
    val lists = remember(feeds) {
        kept.keys.retainAll(feeds.map { it.id }.toSet())
        feeds.associate { it.id to kept.getOrPut(it.id) { LazyListState() } }
    }
    val reduced = rememberReducedMotion()
    val turnTo = { page: Int ->
        scope.launch { if (reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    }
    val current = feeds[pager.currentPage.coerceIn(0, feeds.lastIndex)]
    val title = feedName(current)
    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    BackHandler(enabled = pager.currentPage != 0) { turnTo(0) }
    Scaffold(
        modifier = modifier.nestedScroll(bar.nestedScrollConnection).semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = {
                    FeedSwitcher(
                        current,
                        state,
                        onPick = { turnTo(it) },
                        onListed = actions::onListed,
                        onEdit = chrome.onEditFeeds,
                    )
                },
                navigationIcon = chrome.navigationIcon,
                scrollBehavior = bar,
                actions = {
                    chrome.onSearch?.let {
                        IconButton(onClick = it) { Icon(AlohaIcons.Search, stringResource(R.string.timeline_search)) }
                    }
                    ShowOptions(
                        state.showBoosts,
                        state.showReplies,
                        actions::onShowBoosts,
                        actions::onShowReplies,
                        chrome.onCatchUp,
                    )
                },
            )
        },
        floatingActionButton = {
            ComposeButton(chrome, expanded = !lists.getValue(current.id).canScrollBackward, draft = state.draft)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            chrome.header()
            HorizontalPager(
                pager,
                Modifier.weight(1f),
                userScrollEnabled = state.swipeBetween,
                key = { shown.getOrNull(it)?.id ?: it },
            ) {
                val feed = shown.getOrNull(it) ?: return@HorizontalPager
                page(feed, lists.getValue(feed.id)) { FeedBanner(feed, state.explained) { actions.onExplained(feed) } }
            }
        }
    }
}
