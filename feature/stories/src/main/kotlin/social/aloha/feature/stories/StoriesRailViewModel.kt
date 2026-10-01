// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.stories.Stories
import social.aloha.core.data.stories.StoryReel

/** The active account's stories rail; empty where the server has no stories, or nobody posted one. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class StoriesRailViewModel @Inject constructor(accounts: AccountRepository, stories: Stories) : ViewModel() {
    val reels: StateFlow<List<StoryReel>> = accounts.activeAccount
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.capabilities?.stories == b?.capabilities?.stories }
        .mapLatest { reader ->
            val answer = reader?.takeIf { it.capabilities.stories }?.let { stories.rail(it) }
            (answer as? Answer.Got)?.value.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), emptyList())

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
