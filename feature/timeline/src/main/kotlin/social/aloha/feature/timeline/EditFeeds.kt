// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.lists.Lists
import social.aloha.core.data.tags.Hashtags
import social.aloha.core.data.timeline.PinnedFeeds
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.AccountList
import social.aloha.core.model.PinnedFeed
import social.aloha.core.model.TimelineSource

/** The feeds pinned to Home, and what more could be pinned: the server's live feeds, lists, followed tags. */
@Immutable
internal data class EditFeedsState(
    val feeds: List<PinnedFeed> = emptyList(),
    val lists: List<AccountList> = emptyList(),
    val tags: List<String> = emptyList(),
    val localFeed: Boolean = true,
    val federatedFeed: Boolean = true,
) {
    /** What the Add menu offers, by group, leaving out what is pinned already. */
    val addable: List<Pair<Int, List<PinnedFeed>>>
        get() {
            val pinned = feeds.mapTo(HashSet()) { it.id }
            val live = listOfNotNull(
                PinnedFeed.Kind.Following,
                PinnedFeed.Kind.ThisServer.takeIf { localFeed },
                PinnedFeed.Kind.Everyone.takeIf { federatedFeed },
            )
            val yours = listOf(PinnedFeed.Kind.Notified, PinnedFeed.Kind.Bookmarks, PinnedFeed.Kind.Favourites)
            return listOf(
                R.string.feeds_add_live to live,
                R.string.feeds_add_yours to yours,
                R.string.feeds_add_lists to lists.map { PinnedFeed.Kind.List(it.id, it.title) },
                R.string.feeds_add_hashtags to tags.map { PinnedFeed.Kind.Hashtag(TimelineSource.Hashtag(it)) },
            ).map { (group, kinds) -> group to kinds.map(::PinnedFeed).filter { it.id !in pinned } }
                .filter { it.second.isNotEmpty() }
        }
}

/** What the Edit feeds screen changes; each change is kept at once and Home follows it. */
internal interface EditFeedsActions {
    fun onOrder(feeds: List<PinnedFeed>)
    fun onRemove(feed: PinnedFeed)
    fun onChange(old: PinnedFeed, new: PinnedFeed)
    fun onAdd(feed: PinnedFeed)

    /** Puts [feed] back at [at], undoing its removal. */
    fun onRestore(feed: PinnedFeed, at: Int)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class EditFeedsViewModel @Inject constructor(
    accounts: AccountRepository,
    private val pinned: PinnedFeeds,
    lists: Lists,
    hashtags: Hashtags,
) : ViewModel(),
    EditFeedsActions {
    private val account = accounts.activeAccount.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val choices = MutableStateFlow(emptyList<AccountList>() to emptyList<String>())

    val state: StateFlow<EditFeedsState> = account.filterNotNull().flatMapLatest { reader ->
        combine(pinned.feeds(reader), choices) { feeds, (lists, tags) ->
            EditFeedsState(feeds, lists, tags, reader.capabilities.localFeed, reader.capabilities.federatedFeed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), EditFeedsState())

    init {
        viewModelScope.launch {
            val reader = account.filterNotNull().first()
            val known = (lists.all(reader) as? Answer.Got)?.value.orEmpty()
            val followed = (hashtags.followed(reader) as? Answer.Got)?.value.orEmpty().map { it.name }
            choices.value = known to followed
        }
    }

    override fun onOrder(feeds: List<PinnedFeed>) = save { feeds }

    // Following stays, and so Home always reads something
    override fun onRemove(feed: PinnedFeed) {
        if (feed.kind != PinnedFeed.Kind.Following) save { feeds -> feeds - feed }
    }

    override fun onChange(old: PinnedFeed, new: PinnedFeed) = save { feeds -> feeds.map { if (it == old) new else it } }

    override fun onAdd(feed: PinnedFeed) = save { it + feed }

    override fun onRestore(feed: PinnedFeed, at: Int) = save { feeds ->
        if (feeds.any { it.id == feed.id }) feeds else feeds.toMutableList().apply { add(at.coerceIn(0, size), feed) }
    }

    private fun save(change: (List<PinnedFeed>) -> List<PinnedFeed>) {
        val reader = account.value ?: return
        viewModelScope.launch { pinned.update(reader, change) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

@Composable
public fun EditFeedsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: EditFeedsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditFeedsScreen(state, viewModel, onBack, modifier)
}

/**
 * The pinned feeds in their order: dragged by their handle to reorder, swiped away to remove, with a moment
 * to undo it, tapped to rename, give another icon or, for a hashtag, more tags; the bar's + adds one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditFeedsScreen(
    state: EditFeedsState,
    actions: EditFeedsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    val remove = removeWithUndo(state.feeds, actions, snackbars)
    var editing by remember { mutableStateOf<PinnedFeed?>(null) }
    var newTag by remember { mutableStateOf(false) }
    var newServer by remember { mutableStateOf(false) }
    val title = stringResource(R.string.timeline_feeds_edit)
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AlohaIcons.Back, stringResource(R.string.feeds_back)) }
                },
                actions = { AddMenu(state.addable, actions::onAdd, { newTag = true }) { newServer = true } },
            )
        },
    ) { padding ->
        FeedList(
            state.feeds,
            Modifier.padding(padding),
            onOrder = actions::onOrder,
            onRemove = remove.takeIf { state.feeds.size > 1 },
            onEdit = { editing = it },
        )
    }
    editing?.let { feed ->
        FeedDialog(feed, onDismiss = { editing = null }) {
            actions.onChange(feed, it)
            editing = null
        }
    }
    if (newServer) {
        ServerDialog(onDismiss = { newServer = false }) {
            actions.onAdd(it)
            newServer = false
        }
    }
    if (newTag) {
        FeedDialog(PinnedFeed(PinnedFeed.Kind.Hashtag(TimelineSource.Hashtag(""))), onDismiss = { newTag = false }) {
            actions.onAdd(it)
            newTag = false
        }
    }
}

/** Removes a feed at once, then offers it back, where it was, for as long as the snackbar shows. */
@Composable
private fun removeWithUndo(
    feeds: List<PinnedFeed>,
    actions: EditFeedsActions,
    snackbars: SnackbarHostState,
): (PinnedFeed) -> Unit {
    val scope = rememberCoroutineScope()
    val removed = stringResource(R.string.feeds_removed)
    val undo = stringResource(R.string.feeds_undo)
    return { feed ->
        val at = feeds.indexOf(feed)
        actions.onRemove(feed)
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            val answer = snackbars.showSnackbar(removed, undo, duration = SnackbarDuration.Short)
            if (answer == SnackbarResult.ActionPerformed) actions.onRestore(feed, at)
        }
    }
}
