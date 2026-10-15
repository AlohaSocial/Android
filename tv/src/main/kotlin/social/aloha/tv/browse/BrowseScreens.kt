// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import social.aloha.tv.NearEnd
import social.aloha.tv.R
import social.aloha.tv.TvInsets
import social.aloha.tv.TvTextField
import social.aloha.tv.home.CARD_WIDTH
import social.aloha.tv.home.PersonCard
import social.aloha.tv.home.PersonCardUi
import social.aloha.tv.home.VideoCard
import social.aloha.tv.home.VideoCardUi

@Composable
internal fun ChannelRoute(
    accountId: String,
    name: String,
    onVideo: (VideoCardUi) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel<ChannelViewModel, ChannelViewModel.Factory> { it.create(accountId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VideoGrid(
        title = name,
        videos = state.videos,
        empty = if (state.loadedOnce) stringResource(R.string.tv_channel_empty) else null,
        onVideo = onVideo,
        loading = state.loadingOlder,
        onNearEnd = viewModel::onNearEnd,
    )
}

@Composable
internal fun SearchRoute(
    onVideo: (VideoCardUi) -> Unit,
    onPerson: (PersonCardUi) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(state, viewModel::onSearch, onVideo, onPerson)
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onVideo: (VideoCardUi) -> Unit,
    onPerson: (PersonCardUi) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val status = when {
        state.searching -> stringResource(R.string.tv_working)
        state.failed -> stringResource(R.string.tv_search_failed)
        state.searched && state.videos.isEmpty() && state.people.isEmpty() -> stringResource(R.string.tv_search_none)
        else -> null
    }
    VideoGrid(
        title = stringResource(R.string.tv_search),
        videos = state.videos,
        empty = status,
        onVideo = onVideo,
        header = {
            TvTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.tv_search_label),
                onDone = { onSearch(query) },
                keyboardType = KeyboardType.Text,
                modifier = Modifier.focusRequester(focus),
            )
            if (state.people.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(state.people, key = { it.id }) { PersonCard(it, onClick = { onPerson(it) }) }
                }
            }
        },
    )
    LaunchedEffect(Unit) { if (!state.searched) focus.requestFocus() }
}

/**
 * Videos as a grid of cards under a [title], with [header] above them and [empty] said where there are
 * none; [onNearEnd] asks for more as the last row comes into view.
 */
@Composable
internal fun VideoGrid(
    title: String,
    videos: List<VideoCardUi>,
    empty: String?,
    onVideo: (VideoCardUi) -> Unit,
    loading: Boolean = false,
    onNearEnd: () -> Unit = {},
    header: @Composable () -> Unit = {},
) {
    val grid = rememberLazyGridState()
    NearEnd(videos.size, NEAR_END, loading, { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }, onNearEnd)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD_WIDTH),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TvInsets.horizontal, vertical = TvInsets.vertical),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                header()
                if (videos.isEmpty() && empty != null) {
                    Text(
                        empty,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        items(videos, key = { it.id }) { VideoCard(it, onClick = { onVideo(it) }) }
    }
}

private const val NEAR_END = 6
