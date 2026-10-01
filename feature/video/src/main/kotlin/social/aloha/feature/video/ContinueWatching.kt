// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.html.RichTextCache
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper
import social.aloha.core.ui.StatusRowUi
import social.aloha.core.ui.VideoCard
import social.aloha.core.ui.videoTitle

/** One video to carry on with: its card, and how far the reader got. */
@Immutable
internal data class Unfinished(val row: StatusRowUi, val watched: Double)

/** The active account's videos to carry on with, where its server keeps them. */
@HiltViewModel
internal class ContinueWatchingViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val watching: WatchPositions,
    private val cache: RichTextCache,
) : ViewModel() {
    private val state = MutableStateFlow<List<Unfinished>>(emptyList())
    val videos: StateFlow<List<Unfinished>> = state.asStateFlow()

    /** Loads once the theme's colours are known, which the cards are drawn with. */
    fun onColors(colors: RichTextColors) {
        viewModelScope.launch {
            val reader = accounts.activeAccount.filterNotNull().first()
            if (!reader.capabilities.watchPositions) return@launch
            val answer = watching.continueWatching(reader) as? Answer.Got ?: return@launch
            val mapper = StatusRowMapper(cache, colors)
            state.value = answer.value.map {
                Unfinished(mapper.map(it.status, reader.serverAccountId), it.item.fraction)
            }
        }
    }

    fun onRemove(statusId: String) {
        state.update { list -> list.filterNot { it.row.statusId == statusId } }
        viewModelScope.launch {
            accounts.activeAccount.value?.let { watching.remove(it, statusId) }
        }
    }
}

/**
 * Continue watching, atop Video: the videos the reader started and did not finish, where the server
 * keeps track of them, each with a way off the list. Nothing at all when there are none.
 */
@Composable
public fun ContinueWatching(onOpen: (statusId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<ContinueWatchingViewModel>()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val colors = RichTextColors.fromTheme()
    LaunchedEffect(colors) { viewModel.onColors(colors) }
    ContinueWatching(videos, onOpen, viewModel::onRemove, modifier)
}

@Composable
internal fun ContinueWatching(
    videos: List<Unfinished>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    Column(modifier) {
        Text(
            stringResource(R.string.continue_watching),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m).semantics { heading() },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = AlohaSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.xs),
        ) {
            items(videos, key = { it.row.statusId }) { video ->
                Box(Modifier.width(CARD)) {
                    VideoCard(video.row, video.watched, onOpen = { onOpen(video.row.statusId) })
                    val remove = stringResource(R.string.continue_watching_remove, videoTitle(video.row))
                    IconButton(
                        onClick = { onRemove(video.row.statusId) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(AlohaSpacing.s),
                    ) {
                        Icon(
                            AlohaIcons.Close,
                            contentDescription = remove,
                            tint = Color.White,
                            modifier = Modifier.background(Color.Black.copy(alpha = SCRIM), CircleShape)
                                .padding(AlohaSpacing.xs),
                        )
                    }
                }
            }
        }
    }
}

private val CARD = 240.dp
private const val SCRIM = 0.6f
