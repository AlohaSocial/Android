// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import social.aloha.core.data.Trouble
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.ui.LocalReadingStyle
import social.aloha.core.ui.LocalSensitiveMediaPolicy
import social.aloha.core.ui.LocalWarningReveals
import social.aloha.core.ui.PostDivider
import social.aloha.core.ui.ProvideLinkRouting
import social.aloha.core.ui.RefreshBox
import social.aloha.core.ui.StackedAvatars
import social.aloha.core.ui.StatusActions
import social.aloha.core.ui.StatusCard
import social.aloha.core.ui.TroubleStrip
import social.aloha.core.ui.WarningReveals
import social.aloha.core.ui.freshHighlight
import social.aloha.core.ui.fullDate
import social.aloha.core.ui.readingColumn
import social.aloha.core.ui.shake

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadScreen(
    state: ThreadUiState,
    actions: ThreadScreenActions,
    rowActions: StatusActions,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    shake: Int = 0,
) {
    val title = stringResource(R.string.thread_title)
    val bar = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(bar.nestedScrollConnection).semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                scrollBehavior = bar,
                navigationIcon = {
                    IconButton(onClick = actions::onBack) {
                        Icon(AlohaIcons.Back, stringResource(R.string.thread_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.trouble?.takeIf { !state.gone }?.let { TroubleStrip(it) }
            RefreshBox(
                refreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = actions::onRefresh,
                modifier = Modifier.fillMaxSize().shake(shake),
            ) {
                when {
                    state.gone -> Message(stringResource(R.string.thread_gone))
                    state.items.isEmpty() -> Loading()
                    else -> Posts(state, actions, rowActions, listState)
                }
            }
        }
    }
    // a link in an older version routes as one in the post does
    state.history?.let { ProvideLinkRouting(onLink = rowActions::onLink) { History(it, actions::onHistoryDismissed) } }
    FocusOnce(state, listState)
}

@Composable
private fun Posts(
    state: ThreadUiState,
    actions: ThreadScreenActions,
    rowActions: StatusActions,
    listState: LazyListState,
) {
    val fresh = rememberFreshKeys(state.items.map { it.key })
    val revealMode = LocalReadingStyle.current.revealWarnings
    val reveals = remember(revealMode) { WarningReveals(revealMode) }
    CompositionLocalProvider(LocalWarningReveals provides reveals) {
        LazyColumn(
            state = listState,
            modifier = Modifier.readingColumn(),
            contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
        ) {
            items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ThreadItem.Post -> {
                        // the indent shows how deep a reply sits; a screen reader says it, and finds the post
                        // by heading
                        val level = stringResource(R.string.thread_reply_level, item.depth)
                        StatusCard(
                            item.row,
                            state.now,
                            LocalSensitiveMediaPolicy.current,
                            rowActions,
                            modifier = Modifier.freshHighlight(
                                item.key in fresh,
                            ).padding(start = indent(item.depth)).semantics {
                                if (item.focused) heading()
                                if (item.depth > 0) stateDescription = level
                            },
                            canReact = item.focused && state.canReact,
                            focused = item.focused,
                        )
                        if (item.focused) Footer(state, actions)
                    }

                    is ThreadItem.More -> TextButton(
                        onClick = { actions.onMore(item.parentId) },
                        modifier = Modifier.padding(start = indent(item.depth + 1)),
                    ) { Text(pluralStringResource(R.plurals.thread_more_replies, item.count, item.count)) }
                }
                PostDivider()
            }
        }
    }
}

/** Under the focused post: who favourited and boosted it, the quotes and reactions, the edit history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(state: ThreadUiState, actions: ThreadScreenActions) {
    if (state.lists.isEmpty() && !state.edited) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
    ) {
        state.lists.forEach { (kind, count) ->
            // who favourited or boosted shows as the people themselves, still one button with its count
            OutlinedButton(onClick = { actions.onList(kind) }) {
                peopleSlots(state.people[kind], count.takeIf { kind in PEOPLE_KINDS })?.let {
                    StackedAvatars(it, LIST_AVATAR)
                    Spacer(Modifier.width(AlohaSpacing.s))
                }
                Text(listLabel(kind, count))
            }
        }
        if (state.edited) {
            OutlinedButton(onClick = actions::onHistory) {
                Text(stringResource(R.string.thread_edit_history))
            }
        }
    }
}

private val LIST_AVATAR = 24.dp

/** [plural] with [count], or [plain] while the reader hides the numbers. */
@Composable
private fun counted(@PluralsRes plural: Int, @StringRes plain: Int, count: Int?): String =
    if (LocalReadingStyle.current.showCounts) {
        pluralStringResource(plural, count ?: 0, count ?: 0)
    } else {
        stringResource(plain)
    }

/**
 * The avatars a favourites or boosts button draws, at most a few: while who they are is still on its
 * way, a circle for each the count names, so the button keeps its width; once known, those the server
 * named, which on a post opened again is so from the start. None for a count or a list of none.
 */
internal fun peopleSlots(known: List<String?>?, count: Int?): List<String?>? {
    val slots = known?.take(PEOPLE_SHOWN) ?: List(minOf(count ?: 0, PEOPLE_SHOWN)) { null }
    return slots.takeIf { it.isNotEmpty() }
}

private const val PEOPLE_SHOWN = 4

/** The lists whose people show as avatars on their button. */
private val PEOPLE_KINDS = setOf(StatusListKind.FavouritedBy, StatusListKind.BoostedBy)

@Composable
private fun listLabel(kind: StatusListKind, count: Int?): String = when (kind) {
    StatusListKind.FavouritedBy -> counted(R.plurals.thread_favourites, R.string.thread_favourites_plain, count)
    StatusListKind.BoostedBy -> counted(R.plurals.thread_boosts, R.string.thread_boosts_plain, count)
    StatusListKind.Quotes -> stringResource(R.string.thread_quotes)
    StatusListKind.Reactions -> stringResource(R.string.thread_reactions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun History(versions: List<EditVersion>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(AlohaSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AlohaSpacing.m),
        ) {
            item {
                Text(
                    stringResource(R.string.thread_history_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
            // newest first, as the post reads now, back to how it was first posted
            items(versions.asReversed().withIndex().toList(), key = { it.index }) { (index, version) ->
                Column(
                    Modifier.semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(AlohaSpacing.xxs),
                ) {
                    val date = fullDate(version.createdAt)
                    val original = index == versions.lastIndex
                    val stamp = if (original) stringResource(R.string.thread_history_original) + " · " + date else date
                    Text(
                        stamp,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    version.spoiler?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    Text(version.body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun TroubleStrip(trouble: Trouble) {
    TroubleStrip(stringResource(if (trouble == Trouble.Offline) R.string.thread_offline else R.string.thread_error))
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(AlohaSpacing.l), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Loading() {
    val loading = stringResource(R.string.thread_loading)
    Box(Modifier.fillMaxSize().semantics { contentDescription = loading }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Once the posts above arrive, the focused post moves to the top rather than the first ancestor. */
@Composable
private fun FocusOnce(state: ThreadUiState, listState: LazyListState) {
    var done by rememberSaveable { mutableStateOf(false) }
    val index = state.items.indexOfFirst { it is ThreadItem.Post && it.focused }
    LaunchedEffect(index, state.loading) {
        if (!done && !state.loading && index >= 0) {
            if (index > 0) listState.scrollToItem(index)
            done = true
        }
    }
}

private fun indent(depth: Int) = INDENT * depth.coerceAtMost(ThreadShape.MAX_DEPTH)

private val INDENT = 12.dp

/**
 * The keys a refresh brought that were not on screen before, so their rows can say so; nothing on the
 * first load, when everything is new.
 */
@Composable
private fun rememberFreshKeys(keys: List<String>): Set<String> {
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var fresh by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(keys) {
        if (keys.isEmpty()) return@LaunchedEffect
        val before = seen
        fresh = if (before == null) emptySet() else keys.toSet() - before
        seen = (before ?: emptySet()) + keys
    }
    return fresh
}
