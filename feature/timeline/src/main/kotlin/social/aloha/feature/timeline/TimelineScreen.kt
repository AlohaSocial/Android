// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import android.content.res.Configuration
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SwipeAction
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.CaughtUpDivider
import social.aloha.core.ui.ListProgress
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.NearEndEffect
import social.aloha.core.ui.StackedAvatars
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.readingColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    state: TimelineUiState,
    actions: TimelineScreenActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    title: String = stringResource(R.string.timeline_title),
    navigationIcon: @Composable () -> Unit = {},
    showOptions: Boolean = true,
    onCompose: (() -> Unit)? = null,
    onAlbums: (() -> Unit)? = null,
    onExplore: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    toolbar: @Composable () -> Unit = {},
    header: @Composable () -> Unit = {},
    onVideo: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                actions = {
                    onSearch?.let {
                        IconButton(onClick = it) { Icon(AlohaIcons.Search, stringResource(R.string.timeline_search)) }
                    }
                    toolbar()
                    PhotosButtons(state.grid, actions::onGrid, onAlbums, onExplore)
                    if (showOptions) Options(state, actions)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            onCompose?.let {
                ExtendedFloatingActionButton(
                    onClick = it,
                    expanded = !listState.canScrollBackward,
                    icon = { Icon(AlohaIcons.Compose, contentDescription = null) },
                    text = { Text(stringResource(R.string.timeline_compose)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // what another feature puts atop this timeline, such as Photos' stories
            header()
            if (state.sources.size > 1) SourceRow(state.source, state.sources, actions::onSource)
            state.trouble?.let { TroubleStrip(it) }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions::onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                // the app is fully drawn once the reader sees posts, or learns there are none
                ReportDrawnWhen { state.items.isNotEmpty() || state.loadedOnce }
                Content(state, actions, rowActions, listState, gridState, onVideo, onCompose)
                NewPostsPill(
                    state.pending,
                    state.pendingAvatars,
                    actions::onRevealPending,
                    Modifier.align(Alignment.TopCenter).padding(top = AlohaSpacing.s),
                )
            }
        }
    }
    // a grid keeps its own position and paging; the list's effects follow the list alone
    if (state.grid != true && state.watched == null) ListEffects(state, actions, listState)
}

/** The posts as a list or a grid, what an empty timeline says, or their shapes while the first page loads. */
@Composable
private fun Content(
    state: TimelineUiState,
    actions: TimelineScreenActions,
    rowActions: StatusActions,
    listState: LazyListState,
    gridState: LazyGridState,
    onVideo: (String) -> Unit,
    onCompose: (() -> Unit)?,
) = when {
    state.items.isNotEmpty() && state.grid == true -> PhotoGrid(state, actions, rowActions, gridState)
    state.items.isNotEmpty() && state.watched != null -> VideoGrid(state, actions, onVideo, gridState)
    state.items.isNotEmpty() -> Rows(state, actions, rowActions, listState, onCompose)
    state.loadedOnce -> EmptyState(state.source, TimelineSource.Local in state.sources, state.sparse, actions)
    else -> Skeleton()
}

/**
 * The posts, read with the keyboard too: J and K select the next and the previous post, which the
 * other keys act on (see [TimelineCommand]). With a hardware keyboard the list takes focus at once,
 * and a key pressed while a post's own button has focus reaches it all the same.
 */
@Composable
private fun Rows(
    state: TimelineUiState,
    actions: TimelineScreenActions,
    rowActions: StatusActions,
    listState: LazyListState,
    onCompose: (() -> Unit)?,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val onKey = timelineKeyHandler(state, rowActions, listState, focus, onCompose, selected) { selected = it }
    val keyboard = LocalConfiguration.current.keyboard == Configuration.KEYBOARD_QWERTY
    LaunchedEffect(keyboard) { if (keyboard) focus.requestFocus() }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .readingColumn()
            .testTag(TIMELINE_LIST)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent(onKey),
        contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
    ) {
        items(state.items, key = { it.key }, contentType = { it::class }) { item ->
            when (item) {
                is TimelineItem.Post -> SwipeRow(
                    item.row,
                    item.key == selected,
                    state.swipeTowardsEnd,
                    state.swipeTowardsStart,
                    onSwipe = { row, action ->
                        if (action ==
                            SwipeAction.Reply
                        ) {
                            rowActions.onReply(row)
                        } else {
                            actions.onSwipe(row, action)
                        }
                    },
                ) {
                    // on the card itself, the one node a screen reader reads of the row
                    val chosen = item.key == selected
                    StatusCard(
                        item.row,
                        state.now,
                        LocalSensitiveMediaPolicy.current,
                        rowActions,
                        Modifier.semantics { this.selected = chosen },
                    )
                }

                is TimelineItem.Gap -> GapRow(item, actions)

                TimelineItem.CaughtUp -> CaughtUpDivider(onClick = actions::onCaughtUp)
            }
            if (item != TimelineItem.CaughtUp) HorizontalDivider()
        }
        if (state.loadingOlder) {
            item(contentType = "footer") { ListProgress() }
        }
    }
}

@Composable
internal fun GapRow(gap: TimelineItem.Gap, actions: TimelineScreenActions) {
    if (gap.loading) {
        ListProgress(size = GAP_PROGRESS)
    } else {
        Box(Modifier.fillMaxWidth().padding(AlohaSpacing.s), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = { actions.onFillGap(gap.id) }) { Text(stringResource(R.string.timeline_gap)) }
        }
    }
}

@Composable
private fun NewPostsPill(count: Int, avatars: List<String?>, onReveal: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(visible = count > 0, modifier = modifier) {
        val label = pluralStringResource(R.plurals.timeline_new_posts, count, count)
        // who posted says it at a glance, beside how many
        Button(
            onClick = onReveal,
            modifier = Modifier.semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
            contentPadding = if (avatars.isEmpty()) {
                ButtonDefaults.ContentPadding
            } else {
                ButtonDefaults.ButtonWithIconContentPadding
            },
        ) {
            if (avatars.isEmpty()) {
                Text(label)
            } else {
                Icon(AlohaIcons.NewPosts, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                StackedAvatars(avatars, PILL_AVATAR, ring = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(label)
            }
        }
    }
}

private val PILL_AVATAR = 24.dp

@Composable
private fun SourceRow(source: TimelineSource, sources: List<TimelineSource>, onSource: (TimelineSource) -> Unit) {
    val labels = mapOf(
        TimelineSource.Home to R.string.timeline_source_home,
        TimelineSource.Local to R.string.timeline_source_local,
        TimelineSource.Federated to R.string.timeline_source_federated,
    )
    // chips size to their labels and scroll rather than clip, which equal segments cannot at large font sizes
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        sources.forEach { option ->
            val selected = option == source
            FilterChip(
                selected = selected,
                onClick = { onSource(option) },
                label = { Text(stringResource(labels.getValue(option)), maxLines = 1) },
                leadingIcon = if (selected) {
                    { Icon(AlohaIcons.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

@Composable
private fun Options(state: TimelineUiState, actions: TimelineScreenActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(AlohaIcons.More, stringResource(R.string.timeline_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_show_boosts)) },
                leadingIcon = { Checkbox(state.showBoosts, onCheckedChange = null) },
                onClick = { actions.onShowBoosts(!state.showBoosts) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timeline_show_replies)) },
                leadingIcon = { Checkbox(state.showReplies, onCheckedChange = null) },
                onClick = { actions.onShowReplies(!state.showReplies) },
            )
        }
    }
}

@Composable
private fun TroubleStrip(trouble: Trouble) {
    val text = when (trouble) {
        Trouble.Offline -> R.string.timeline_offline
        Trouble.RateLimited -> R.string.timeline_rate_limited
        Trouble.Server -> R.string.timeline_error
    }
    TroubleStrip(stringResource(text))
}

@Composable
private fun EmptyState(source: TimelineSource, canExplore: Boolean, sparse: Boolean, actions: TimelineScreenActions) {
    Column(
        Modifier.fillMaxSize().padding(AlohaSpacing.l),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.timeline_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics {
                heading()
            },
        )
        if (source == TimelineSource.Home) {
            Text(stringResource(R.string.timeline_empty_home), style = MaterialTheme.typography.bodyMedium)
            if (canExplore) {
                Button(onClick = {
                    actions.onSource(TimelineSource.Local)
                }) { Text(stringResource(R.string.timeline_empty_explore)) }
            }
        } else {
            Text(stringResource(R.string.timeline_empty_public), style = MaterialTheme.typography.bodyMedium)
        }
        // said once, where it explains the emptiness: the server leaves the narrowing to the device
        if (sparse) {
            Text(
                stringResource(R.string.timeline_empty_sparse),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Placeholders the shape of posts while the very first page loads, read as one "loading" element. */
@Composable
private fun Skeleton() {
    val loading = stringResource(R.string.timeline_loading)
    Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = loading }) {
        repeat(SKELETON_ROWS) {
            Row(Modifier.padding(AlohaSpacing.m), horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s)) {
                Box(
                    Modifier.size(
                        SKELETON_AVATAR,
                    ).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xs)) {
                    Box(
                        Modifier.width(
                            SKELETON_NAME,
                        ).height(
                            SKELETON_LINE,
                        ).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(
                            SKELETON_LINE,
                        ).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall),
                    )
                    Box(
                        Modifier.width(
                            SKELETON_SHORT,
                        ).height(
                            SKELETON_LINE,
                        ).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraSmall),
                    )
                }
            }
        }
    }
}

/** The list's side of the conversation: where it is, when it nears its end, and the scrolls it was asked for. */
@Composable
private fun ListEffects(state: TimelineUiState, actions: TimelineScreenActions, listState: LazyListState) {
    val items by rememberUpdatedState(state.items)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                (items.getOrNull(index) as? TimelineItem.Post)?.let { actions.onScrolled(it.key, offset) }
            }
    }
    NearEndEffect(listState, items.size, actions::onNearEnd)
    LaunchedEffect(state.restoreTo) {
        state.restoreTo?.let {
            listState.scrollToItem(it.index, it.offset)
            actions.onRestored()
        }
    }
    LaunchedEffect(state.scrollToTop) {
        if (state.scrollToTop) {
            listState.animateScrollToItem(0)
            actions.onScrolledToTop()
        }
    }
}

private const val SKELETON_ROWS = 5
private val SKELETON_AVATAR = 44.dp
private val SKELETON_NAME = 120.dp
private val SKELETON_SHORT = 180.dp
private val SKELETON_LINE = 14.dp
private val GAP_PROGRESS = 24.dp

/** The home list's test tag, which the scroll benchmark finds it by. */
internal const val TIMELINE_LIST = "timeline"
