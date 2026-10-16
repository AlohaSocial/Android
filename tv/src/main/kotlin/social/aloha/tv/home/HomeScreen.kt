// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import social.aloha.core.media.LadderPlayback
import social.aloha.core.media.Sound
import social.aloha.core.media.mediaPlayer
import social.aloha.tv.NearEnd
import social.aloha.tv.R
import social.aloha.tv.TvInsets
import social.aloha.tv.tap

@Composable
internal fun HomeRoute(links: HomeLinks, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    HomeScreen(state, links, viewModel::onNearEnd)
}

/**
 * The shelves, one under the other, each a row the D-pad moves along. Focus comes back to the card it left
 * when the reader returns from a video; a card that keeps focus for a moment plays a muted preview.
 */
@Composable
internal fun HomeScreen(state: HomeUiState, links: HomeLinks, onNearEnd: (ShelfKind) -> Unit) {
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    // the first card only once the shelves loaded, so rows arriving after it never leave focus mid-row
    val restoreKey = focusedKey ?: state.shelves.firstOrNull()?.firstKey()?.takeIf { state.loadedOnce }
    val preview = rememberPreview()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = TvInsets.vertical),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // focus up there stops the preview, which would otherwise play on under the row
        item { TopRow(links, Modifier.onFocusChanged { if (it.hasFocus) preview.focus(null, null) }) }
        if (state.loadedOnce && state.shelves.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.tv_nothing_yet),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = TvInsets.horizontal),
                )
            }
        }
        items(state.shelves, key = { it.kind }) { shelf ->
            // each shelf hears only of its own keys, so a move along one row leaves the others as they are
            ShelfRow(
                shelf = shelf,
                restoreKey = restoreKey?.takeIf { shelf.owns(it) },
                previewing = preview.playing?.takeIf { shelf.owns(it) },
                player = preview.player,
                onFocused = { key, card ->
                    focusedKey = key
                    preview.focus(key, card)
                },
                links = links,
                onNearEnd = onNearEnd,
            )
        }
    }
}

@Composable
private fun TopRow(links: HomeLinks, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = TvInsets.horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.tv_app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = links.onSearch, modifier = Modifier.tap(links.onSearch)) {
            Icon(Icons.Outlined.Search, stringResource(R.string.tv_search))
        }
        IconButton(onClick = links.onAccount, modifier = Modifier.tap(links.onAccount)) {
            Icon(Icons.Outlined.AccountCircle, stringResource(R.string.tv_account))
        }
    }
}

@Composable
private fun ShelfRow(
    shelf: Shelf,
    restoreKey: String?,
    previewing: String?,
    player: Player?,
    onFocused: (String, VideoCardUi?) -> Unit,
    links: HomeLinks,
    onNearEnd: (ShelfKind) -> Unit,
) {
    val row = rememberLazyListState()
    val count = shelf.videos.size + shelf.people.size
    NearEnd(count, NEAR_END, shelf.loading, { row.layoutInfo.visibleItemsInfo.lastOrNull()?.index }) {
        onNearEnd(shelf.kind)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(shelf.kind.title()),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = TvInsets.horizontal).semantics { heading() },
        )
        LazyRow(
            state = row,
            contentPadding = PaddingValues(horizontal = TvInsets.horizontal),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(shelf.videos, key = { it.id }) { card ->
                val key = shelf.keyOf(card.id)
                VideoCard(
                    card = card,
                    onClick = { if (shelf.kind == ShelfKind.Shorts) links.onShort(card) else links.onVideo(card) },
                    previewing = previewing == key,
                    player = player,
                    modifier = Modifier.restoringFocus(key == restoreKey)
                        .onFocusChanged { if (it.isFocused) onFocused(key, card) },
                )
            }
            items(shelf.people, key = { it.id }) { person ->
                val key = shelf.keyOf(person.id)
                PersonCard(
                    person = person,
                    onClick = { links.onPerson(person) },
                    modifier = Modifier.restoringFocus(key == restoreKey)
                        .onFocusChanged { if (it.isFocused) onFocused(key, null) },
                )
            }
        }
    }
}

/** Takes focus once, as the screen is shown, where the reader left it. */
@Composable
private fun Modifier.restoringFocus(restore: Boolean): Modifier {
    if (!restore) return this
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return focusRequester(focus)
}

/**
 * One muted player for every card: the one focused for a moment plays, until focus moves on. A card is
 * known by its key on its shelf, as the same video can sit on two shelves and only the focused one plays.
 */
private class PreviewState(val player: Player?) {
    var playing by mutableStateOf<String?>(null)
    var focused by mutableStateOf<Pair<String, VideoCardUi>?>(null)

    fun focus(key: String?, card: VideoCardUi?) {
        focused = if (key != null && card != null) key to card else null
    }
}

@Composable
private fun rememberPreview(): PreviewState {
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val state = remember { PreviewState(if (inspecting) null else mediaPlayer(context, Sound.Silent)) }
    val player = state.player ?: return state
    DisposableEffect(player) { onDispose { player.release() } }
    // nothing plays behind the launcher once the app goes to the background
    LifecycleStartEffect(state) { onStopOrDispose { state.focus(null, null) } }
    LaunchedEffect(state.focused) {
        state.playing = null
        player.stop()
        val (key, card) = state.focused?.takeIf { it.second.preview.isNotEmpty() } ?: return@LaunchedEffect
        delay(PREVIEW_DELAY_MILLIS)
        val ladder = LadderPlayback(player, card.preview)
        player.volume = 0f
        player.playWhenReady = true
        ladder.start()
        state.playing = key
        try {
            awaitCancellation()
        } finally {
            ladder.release()
        }
    }
    return state
}

private fun Shelf.firstKey(): String? = (videos.firstOrNull()?.id ?: people.firstOrNull()?.id)?.let(::keyOf)

private fun Shelf.keyOf(id: String) = "${kind.name}:$id"

private fun Shelf.owns(key: String) = key.startsWith("${kind.name}:")

@StringRes
private fun ShelfKind.title(): Int = when (this) {
    ShelfKind.ContinueWatching -> R.string.tv_shelf_continue
    ShelfKind.HomeVideo -> R.string.tv_shelf_home
    ShelfKind.Shorts -> R.string.tv_shelf_shorts
    ShelfKind.Following -> R.string.tv_shelf_following
    ShelfKind.FederatedVideo -> R.string.tv_shelf_federated
    ShelfKind.Trending -> R.string.tv_shelf_trending
}

private const val NEAR_END = 4
private const val PREVIEW_DELAY_MILLIS = 1_200L
