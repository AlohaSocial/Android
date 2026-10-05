// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.Story
import social.aloha.core.ui.EmptyState
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.RefreshBox
import social.aloha.core.ui.Skeleton
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.readingWidth
import social.aloha.core.ui.rememberReducedMotion
import social.aloha.core.ui.swipeTabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    actions: ProfileScreenActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    search: PostSearch? = null,
) {
    val title = state.header?.author?.plainName.orEmpty()
    var asking by rememberSaveable { mutableStateOf<Asking?>(null) }
    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    var showingQr by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.nestedScroll(bar.nestedScrollConnection).semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { ScrolledTitle(title, state.header?.posts, listState) },
                scrollBehavior = bar,
                navigationIcon = {
                    IconButton(onClick = actions::onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.profile_back))
                    }
                },
                actions = {
                    val header = state.header
                    val url = header?.url
                    if (header?.isSelf == true && url != null) {
                        IconButton(onClick = { showingQr = true }) {
                            Icon(AlohaIcons.QrCode, stringResource(R.string.profile_qr_title))
                        }
                        if (showingQr) ProfileQrDialog(url, header.author.handle) { showingQr = false }
                    }
                    Menu(state, actions, ask = { asking = it })
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.trouble?.takeIf { !state.gone }?.let { TroubleStrip(it) }
            RefreshBox(refreshing = state.loading && state.header != null, onRefresh = actions::onRefresh) {
                when {
                    state.gone -> EmptyState(stringResource(R.string.profile_gone))
                    state.header == null -> ProfileSkeleton(state.knownHandle)
                    else -> Content(state, state.header, actions, rowActions, listState, search)
                }
            }
        }
    }
    asking?.let { question ->
        val header = state.header ?: return@let
        RelationDialog(question, header.author.handle, header.domain, state, actions) { asking = null }
    }
    NearEndEffect(listState, state.items.size, actions::onNearEnd)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Content(
    state: ProfileUiState,
    header: ProfileHeader,
    actions: ProfileScreenActions,
    rowActions: StatusActions,
    listState: LazyListState,
    search: PostSearch?,
) {
    // on a wide window the profile keeps a reading width, centred, rather than stretching banner and text
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val shown = shownTabs(state.tabs)
        val at = shown.indexOf(shownTab(state.tab))
        val tabsAt = if (state.highlights != null) 2 else 1
        var keepPinned by remember { mutableStateOf(false) }
        var timeline by rememberSaveable { mutableStateOf(ProfileTab.Posts) }
        if (state.tab in TIMELINE) timeline = state.tab
        val choose = { tab: ProfileTab ->
            keepPinned = listState.firstVisibleItemIndex >= tabsAt
            actions.onTab(tab)
        }
        val swipeTo = { tab: ProfileTab? -> tab?.let { choose(if (it == ProfileTab.Posts) timeline else it) } }
        val pinned by remember(tabsAt) { derivedStateOf { listState.firstVisibleItemIndex >= tabsAt } }
        val reduced = rememberReducedMotion()
        LaunchedEffect(state.tab) {
            if (keepPinned) listState.scrollToItem(tabsAt)
            keepPinned = false
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.readingWidth().fillMaxSize().imePadding().swipeTabs(
                next = { swipeTo(shown.getOrNull(at + 1)) },
                previous = { swipeTo(shown.getOrNull(at - 1)) },
            ),
            contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
        ) {
            item(key = "header", contentType = "header") {
                ProvideLinkRouting(onLink = rowActions::onLink) {
                    // the banner drifts at half the speed of the list, under what scrolls over it
                    val drift = {
                        if (reduced ||
                            listState.firstVisibleItemIndex > 0
                        ) {
                            0f
                        } else {
                            listState.firstVisibleItemScrollOffset / 2f
                        }
                    }
                    Header(header, state, actions, rowActions::onProfile, drift)
                }
            }
            state.highlights?.let { highlights ->
                item(key = "highlights", contentType = "highlights") { Highlights(highlights) }
            }
            stickyHeader(key = "tabs", contentType = "tabs") { Tabs(state, pinned, timeline, choose) }
            tabContent(state, actions, rowActions, search.takeIf { state.postSearch })
        }
    }
}

/** A search through the account's posts, as it stands, and what typing in it does. */
internal data class PostSearch(val state: PostSearchUi, val onQuery: (String) -> Unit)

private fun LazyListScope.tabContent(
    state: ProfileUiState,
    actions: ProfileScreenActions,
    rowActions: StatusActions,
    search: PostSearch?,
) {
    when (state.tab) {
        ProfileTab.Collections -> listed(state.collections, MediaCollection::id) { CollectionRow(it, actions) }

        ProfileTab.Stories -> listed(state.stories, Story::id) { StoryRow(it, actions) }

        ProfileTab.Featured -> state.featured?.let { featured(it, state.now, rowActions) }

        else -> {
            val handle = state.header?.author?.handle.orEmpty()
            val searched = state.tab == ProfileTab.Posts && search != null &&
                postSearch(search, handle, state.now, rowActions)
            if (!searched) timeline(state, actions, rowActions)
        }
    }
}

/** A timeline tab's posts, with the gaps in them, and more coming at the end. */
private fun LazyListScope.timeline(state: ProfileUiState, actions: ProfileScreenActions, rowActions: StatusActions) {
    if (state.items.isEmpty()) {
        item(key = "empty") { Empty(loading = state.loading, Modifier.fillParentMaxHeight()) }
    }
    items(state.items, key = { it.key }, contentType = { it::class }) { item ->
        when (item) {
            is ProfileItem.Post -> StatusCard(
                item.row,
                state.now,
                LocalSensitiveMediaPolicy.current,
                rowActions,
            )

            is ProfileItem.Gap -> if (item.loading) {
                ListProgress(size = PROGRESS)
            } else {
                Box(Modifier.fillMaxWidth().padding(AlohaSpacing.s), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = { actions.onFillGap(item.id) }) {
                        Text(stringResource(R.string.profile_gap))
                    }
                }
            }
        }
        PostDivider()
    }
    if (state.loadingOlder) {
        item(key = "older") { ListProgress() }
    }
}

private fun <T : Any> LazyListScope.listed(entries: List<T>?, key: (T) -> String, row: @Composable (T) -> Unit) {
    when {
        entries == null -> item(key = "empty") { Empty(loading = true, Modifier.fillParentMaxHeight()) }

        entries.isEmpty() -> item(key = "empty") { Empty(loading = false, Modifier.fillParentMaxHeight()) }

        else -> items(entries, key = key) { entry ->
            row(entry)
            HorizontalDivider()
        }
    }
}

@Composable
private fun Menu(state: ProfileUiState, actions: ProfileScreenActions, ask: (Asking) -> Unit) {
    val header = state.header ?: return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.profile_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val relation = state.relation
            if (!header.isSelf && relation != null) {
                RelationItems(relation, header.domain, actions, ask) { open = false }
            }
            if (!header.isSelf) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_report)) },
                    onClick = {
                        open = false
                        actions.onReport()
                    },
                )
            }
            header.url?.let { url ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_open_in_browser)) },
                    onClick = {
                        open = false
                        actions.onOpenInBrowser(url)
                    },
                )
            }
            if (actions.windows) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_new_window)) },
                    onClick = {
                        open = false
                        actions.onNewWindow()
                    },
                )
            }
        }
    }
}

@Composable
private fun TroubleStrip(trouble: Trouble) {
    TroubleStrip(stringResource(if (trouble == Trouble.Offline) R.string.profile_offline else R.string.profile_error))
}

/**
 * A tab still loading, or with nothing in it. [modifier] lets it fill the screen below the tabs, so tabs
 * pinned under the bar stay pinned while it loads instead of the list falling back to the header.
 */
@Composable
private fun Empty(loading: Boolean, modifier: Modifier = Modifier) {
    if (loading) Skeleton(modifier) else EmptyState(stringResource(R.string.profile_empty), modifier)
}

/** The name, and once the header has scrolled away, how many posts under it. */
@Composable
private fun ScrolledTitle(title: String, posts: Int?, listState: LazyListState) {
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (scrolled && posts != null && LocalReadingStyle.current.showCounts) {
            Text(
                pluralStringResource(R.plurals.profile_posts, posts, NumberFormat.getIntegerInstance().format(posts)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private val PROGRESS = 24.dp
