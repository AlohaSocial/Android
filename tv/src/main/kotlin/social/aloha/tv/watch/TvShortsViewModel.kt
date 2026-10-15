// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.watch

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.data.timeline.TimelinePager
import social.aloha.core.data.timeline.TimelineRepository
import social.aloha.core.data.timeline.TimelineRow
import social.aloha.core.media.ShortSource
import social.aloha.core.model.FeedMode
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.model.TimelineKey
import social.aloha.core.model.TimelineSource
import social.aloha.tv.home.VideoCardUi
import social.aloha.tv.home.toCard

/** One short on the TV: its card and where it plays from. */
@Immutable
internal data class TvShortUi(val card: VideoCardUi, val source: ShortSource)

@Immutable
internal data class TvShortsUiState(
    val shorts: List<TvShortUi> = emptyList(),
    /** The covered shorts the reader asked to see, by id. */
    val revealed: Set<String> = emptySet(),
    val loadedOnce: Boolean = false,
    val loadingOlder: Boolean = false,
)

/**
 * The shorts of the people followed, one per page, newest first and older ones below, as the phone's
 * Shorts read them; a covered one waits behind its warning until the reader asks for it, where the
 * reader's policy hides it, too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class TvShortsViewModel @Inject constructor(
    accounts: AccountRepository,
    timelines: TimelineRepository,
    sensitive: SensitiveMedia,
    clock: Clock,
) : ViewModel() {
    private val key = TimelineKey(FeedMode.Shorts, TimelineSource.Home)
    private val reader = accounts.activeAccount.filterNotNull().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val pager = TimelinePager(timelines, clock, viewModelScope) { reader.value?.let { it to key } }
    private val revealed = MutableStateFlow(emptySet<String>())

    val uiState: StateFlow<TvShortsUiState> = reader.filterNotNull().flatMapLatest { reader ->
        combine(pager.observe(reader, key), sensitive.policy(reader.id), pager.states, revealed) {
                rows,
                policy,
                paging,
                revealed,
            ->
            TvShortsUiState(
                shorts(rows, reader.capabilities.apiBase, policy),
                revealed,
                paging.loadedOnce,
                paging.loadingOlder,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), TvShortsUiState())

    init {
        viewModelScope.launch { reader.filterNotNull().collect { pager.start(it, key) } }
    }

    fun onNearEnd() = pager.older()

    fun onReveal(id: String) = revealed.update { it + id }

    private fun shorts(rows: List<TimelineRow>, apiBase: String, policy: SensitiveMediaPolicy) =
        rows.filterIsInstance<TimelineRow.Post>().mapNotNull { row ->
            val card = row.status.toCard(apiBase, SensitiveMediaPolicy.ShowAll, null) ?: return@mapNotNull null
            val covered = row.status.displayed.sensitive && !policy.allowsAutomaticReveal
            TvShortUi(
                card.copy(
                    covered = covered,
                    thumbnail = card.thumbnail.takeUnless {
                        covered
                    },
                ),
                ShortSource(card.id, card.preview),
            )
        }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
