// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.text.NumberFormat
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.MediaCollection
import social.aloha.core.model.ProfileHighlights
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.Story
import social.aloha.core.ui.Avatar
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.contentDirection
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.readingWidth
import social.aloha.core.ui.rememberEmojiContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    actions: ProfileScreenActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val title = state.header?.author?.plainName.orEmpty()
    var asking by rememberSaveable { mutableStateOf<Asking?>(null) }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions::onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.profile_back))
                    }
                },
                actions = { Menu(state, actions, ask = { asking = it }) },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.trouble?.takeIf { !state.gone }?.let { TroubleStrip(it) }
            PullToRefreshBox(isRefreshing = state.loading && state.header != null, onRefresh = actions::onRefresh) {
                when {
                    state.gone -> Message(stringResource(R.string.profile_gone))
                    state.header == null -> Loading()
                    else -> Content(state, state.header, actions, rowActions, listState)
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
) {
    // on a wide window the profile keeps a reading width, centred, rather than stretching banner and text
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.readingWidth().fillMaxSize(),
            contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
        ) {
            item(key = "header", contentType = "header") {
                ProvideLinkRouting(onLink = rowActions::onLink) { Header(header, state, actions) }
            }
            state.highlights?.let { highlights ->
                item(key = "highlights", contentType = "highlights") { Highlights(highlights) }
            }
            stickyHeader(key = "tabs", contentType = "tabs") { Tabs(state, actions) }
            tabContent(state, actions, rowActions)
        }
    }
}

private fun LazyListScope.tabContent(state: ProfileUiState, actions: ProfileScreenActions, rowActions: StatusActions) {
    when (state.tab) {
        ProfileTab.Collections -> listed(state.collections, MediaCollection::id) { CollectionRow(it, actions) }

        ProfileTab.Stories -> listed(state.stories, Story::id) { StoryRow(it, actions) }

        else -> {
            if (state.items.isEmpty()) {
                item(key = "empty") { Empty(loading = state.loading) }
            }
            items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ProfileItem.Post -> StatusCard(item.row, state.now, SensitiveMediaPolicy.Blur, rowActions)

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
                HorizontalDivider()
            }
            if (state.loadingOlder) {
                item(key = "older") { ListProgress() }
            }
        }
    }
}

private fun <T : Any> LazyListScope.listed(entries: List<T>?, key: (T) -> String, row: @Composable (T) -> Unit) {
    when {
        entries == null -> item(key = "empty") { Empty(loading = true) }

        entries.isEmpty() -> item(key = "empty") { Empty(loading = false) }

        else -> items(entries, key = key) { entry ->
            row(entry)
            HorizontalDivider()
        }
    }
}

@Composable
private fun Tabs(state: ProfileUiState, actions: ProfileScreenActions) {
    PrimaryScrollableTabRow(
        selectedTabIndex = state.tabs.indexOf(state.tab).coerceAtLeast(0),
        edgePadding = AlohaSpacing.m,
    ) {
        state.tabs.forEach { tab ->
            Tab(selected = tab == state.tab, onClick = {
                actions.onTab(tab)
            }, text = { Text(stringResource(tab.label)) })
        }
    }
}

private val ProfileTab.label: Int
    get() = when (this) {
        ProfileTab.Posts -> R.string.profile_tab_posts
        ProfileTab.Replies -> R.string.profile_tab_replies
        ProfileTab.Media -> R.string.profile_tab_media
        ProfileTab.Videos -> R.string.profile_tab_videos
        ProfileTab.Collections -> R.string.profile_tab_collections
        ProfileTab.Stories -> R.string.profile_tab_stories
    }

@Composable
private fun Menu(state: ProfileUiState, actions: ProfileScreenActions, ask: (Asking) -> Unit) {
    val header = state.header ?: return
    var open by remember { mutableStateOf(false) }
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
    }
}

@Composable
private fun TroubleStrip(trouble: Trouble) {
    TroubleStrip(stringResource(if (trouble == Trouble.Offline) R.string.profile_offline else R.string.profile_error))
}

@Composable
private fun Empty(loading: Boolean) {
    if (loading) {
        ListProgress()
    } else {
        Box(Modifier.fillMaxWidth().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.profile_empty), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Loading() {
    val loading = stringResource(R.string.profile_loading)
    Box(Modifier.fillMaxSize().semantics { contentDescription = loading }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private val PROGRESS = 24.dp
