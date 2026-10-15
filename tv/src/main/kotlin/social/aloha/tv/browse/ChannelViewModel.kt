// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.data.video.WatchPositions
import social.aloha.core.model.FeedMode
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.tv.home.VideoCardUi
import social.aloha.tv.home.toCard

@Immutable
internal data class GridUiState(
    val videos: List<VideoCardUi> = emptyList(),
    val loadedOnce: Boolean = false,
    val loadingOlder: Boolean = false,
)

/**
 * A channel: the videos and shorts someone posted, newest first, from their account's media posts, paging
 * further down as the reader nears the end. Following them is following the channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChannelViewModel.Factory::class)
internal class ChannelViewModel @AssistedInject constructor(
    @Assisted private val accountId: String,
    accounts: AccountRepository,
    timelines: TimelineRepository,
    sensitive: SensitiveMedia,
    watching: WatchPositions,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(accountId: String): ChannelViewModel
    }

    private val key =
        TimelineKey(FeedMode.Video, TimelineSource.Account(accountId, includeReplies = false, onlyMedia = true))
    private val reader = accounts.activeAccount.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val pager = TimelinePager(timelines, clock, viewModelScope) { reader.value?.let { it to key } }

    val uiState: StateFlow<GridUiState> = reader.filterNotNull().flatMapLatest { reader ->
        combine(
            pager.observe(reader, key),
            sensitive.policy(reader.id),
            watching.observe(reader.id),
            pager.states,
        ) { rows, policy, progress, paging ->
            val apiBase = reader.capabilities.apiBase
            GridUiState(
                rows.filterIsInstance<TimelineRow.Post>()
                    .mapNotNull { it.status.toCard(apiBase, policy, progress[it.status.displayed.id]) }
                    // a boost of one's own video is that video again
                    .distinctBy { it.id },
                paging.loadedOnce,
                paging.loadingOlder,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), GridUiState())

    init {
        viewModelScope.launch { reader.filterNotNull().collect { pager.start(it, key) } }
    }

    fun onNearEnd() = pager.older()

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
