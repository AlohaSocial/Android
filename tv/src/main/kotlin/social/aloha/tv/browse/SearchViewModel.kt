// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.data.search.Searches
import social.aloha.tv.home.PersonCardUi
import social.aloha.tv.home.VideoCardUi
import social.aloha.tv.home.isVideo
import social.aloha.tv.home.toCard
import social.aloha.tv.home.toPerson

@Immutable
internal data class SearchUiState(
    val videos: List<VideoCardUi> = emptyList(),
    val people: List<PersonCardUi> = emptyList(),
    val searching: Boolean = false,
    /** Whether the last search came back, found or not, so the screen can say it found nothing. */
    val searched: Boolean = false,
    val failed: Boolean = false,
)

/** Search on the reader's server, narrowed to what a TV plays: videos and shorts, and people as channels. */
@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val searches: Searches,
    private val sensitive: SensitiveMedia,
) : ViewModel() {
    private val state = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = state.asStateFlow()

    private var search: Job? = null

    /** Searches for [query]; a search still on its way is dropped, so its answer never overwrites this one. */
    fun onSearch(query: String) {
        if (query.isBlank()) return
        search?.cancel()
        state.value = state.value.copy(searching = true, failed = false)
        search = viewModelScope.launch {
            val reader = accounts.activeOnceRead.first()
                ?: return@launch state.update { it.copy(searching = false) }
            val policy = sensitive.policy(reader.id).first()
            state.value = when (val answer = searches.search(reader, query)) {
                is Answer.Got -> SearchUiState(
                    videos = answer.value.statuses.filter(::isVideo)
                        .mapNotNull { it.toCard(reader.capabilities.apiBase, policy, null) }
                        .distinctBy { it.id },
                    people = answer.value.accounts.map { it.toPerson() },
                    searched = true,
                )

                is Answer.Missed -> SearchUiState(searched = true, failed = true)
            }
        }
    }
}
