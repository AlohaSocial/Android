// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.TimelineSource
import social.aloha.core.ui.StatusNavigation
import social.aloha.core.ui.sourceName

/** Audio as a mode: its posts with sound, each played from the list, the rest of the row opening the post. */
@Composable
public fun AudioRoute(
    navigation: StatusNavigation,
    accountButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<AudioViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AudioScreen(
        state,
        AudioScreenActions(viewModel::onPlay, navigation::openThread, viewModel::onSource, viewModel::onNearEnd),
        accountButton,
        modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioScreen(
    state: AudioUiState,
    actions: AudioScreenActions,
    accountButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.audio_title)
    val list = rememberLazyListState()
    val count by rememberUpdatedState(state.posts.size)
    LaunchedEffect(list) {
        // a page that arrives may still leave the reader near the end, so the count is watched too
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to count }
            .distinctUntilChanged()
            .collect { (last, size) -> if (size > 0 && last >= size - NEAR_END) actions.onNearEnd() }
    }
    Scaffold(
        modifier = modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = accountButton) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.sources.size > 1) Sources(state.source, state.sources, actions.onSource)
            when {
                state.posts.isNotEmpty() -> LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(bottom = AlohaSpacing.xl),
                ) {
                    items(state.posts, key = { it.statusId }) { item ->
                        val now = state.nowPlaying?.takeIf { it.item.statusId == item.statusId }
                        AudioRow(item, playing = now?.playing == true, current = now != null, actions)
                        HorizontalDivider()
                    }
                }

                // a source that failed to load says so the same way as one with nothing to play
                state.loadedOnce || state.trouble != null -> Text(
                    stringResource(R.string.audio_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(AlohaSpacing.l),
                )

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun Sources(source: TimelineSource, sources: List<TimelineSource>, onSource: (TimelineSource) -> Unit) {
    // at a large font the chips scroll rather than clip
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AlohaSpacing.m)) {
        sources.forEach { option ->
            FilterChip(
                selected = option == source,
                onClick = { onSource(option) },
                label = { Text(stringResource(sourceName(option))) },
                modifier = Modifier.padding(end = AlohaSpacing.s),
            )
        }
    }
}

/** A post with sound: its picture, its title and who posted it, and play or pause; the rest opens the post. */
@Composable
private fun AudioRow(item: AudioItem, playing: Boolean, current: Boolean, actions: AudioScreenActions) {
    val id = item.statusId
    ListItem(
        headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Artwork(item.artwork, Modifier.size(ARTWORK).clip(RoundedCornerShape(CORNER))) },
        trailingContent = {
            FilledTonalIconButton(onClick = { actions.onPlay(id) }) {
                if (playing) {
                    Icon(AlohaIcons.Pause, stringResource(R.string.audio_pause, item.title))
                } else {
                    val label = if (current) R.string.audio_resume else R.string.audio_play
                    Icon(AlohaIcons.Play, stringResource(label, item.title))
                }
            }
        },
        modifier = Modifier.clickable { actions.onOpen(id) },
    )
}

/** What the Audio list does: play [onPlay] or open [onOpen] a post, switch [onSource], page on [onNearEnd]. */
internal class AudioScreenActions(
    val onPlay: (String) -> Unit,
    val onOpen: (String) -> Unit,
    val onSource: (TimelineSource) -> Unit,
    val onNearEnd: () -> Unit,
)

/** A sound's picture, or headphones on a plain square where it has none or it will not load. */
@Composable
internal fun Artwork(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(AlohaIcons.Audio, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) {
            AsyncImage(
                url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private val ARTWORK = 56.dp
private val CORNER = 8.dp
private const val NEAR_END = 5
