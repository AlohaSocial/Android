// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.Trouble
import social.aloha.core.data.map
import social.aloha.core.data.thread.ThreadRepository
import social.aloha.core.data.trouble
import social.aloha.core.html.RichTextCache
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.StatusListKey
import social.aloha.core.navigation.StatusListKind
import social.aloha.core.ui.RichTextColors
import social.aloha.core.ui.StatusRowMapper

/** One of a post's lists: who favourited or boosted it, the posts quoting it, or its reactions. */
@HiltViewModel(assistedFactory = StatusListViewModel.Factory::class)
internal class StatusListViewModel @AssistedInject constructor(
    @Assisted private val key: StatusListKey,
    private val accounts: AccountRepository,
    private val threads: ThreadRepository,
    private val cache: RichTextCache,
    private val clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: StatusListKey): StatusListViewModel
    }

    private val state = MutableStateFlow<StatusListState>(StatusListState.Loading)
    val uiState: StateFlow<StatusListState> = state.asStateFlow()

    /** Loads the list once the screen knows its colours; again on [onRetry]. */
    fun onColors(colors: RichTextColors) {
        if (state.value == StatusListState.Loading) load(colors)
    }

    fun onRetry(colors: RichTextColors) {
        state.value = StatusListState.Loading
        load(colors)
    }

    private fun load(colors: RichTextColors) {
        viewModelScope.launch {
            val account = accounts.byId(key.readerId)
            state.value = if (account == null) StatusListState.Failed(Trouble.Server) else fetch(account, colors)
        }
    }

    // parsing every post's text is no work for the main thread
    private suspend fun fetch(account: SignedInAccount, colors: RichTextColors): StatusListState =
        withContext(Dispatchers.Default) {
            val mapper = StatusRowMapper(cache, colors)
            val id = key.statusId
            val answer: Answer<StatusListState> = when (key.kind) {
                StatusListKind.FavouritedBy -> threads.favouritedBy(account, id).map { people(it, mapper) }

                StatusListKind.BoostedBy -> threads.boostedBy(account, id).map { people(it, mapper) }

                StatusListKind.Quotes -> threads.quotes(account, id).map { quotes ->
                    StatusListState.Posts(quotes.map { mapper.map(it, account.serverAccountId) }, clock.instant())
                }

                StatusListKind.Reactions -> threads.reactions(account, id).map { StatusListState.Reactions(it) }
            }
            when (answer) {
                is Answer.Got -> answer.value
                is Answer.Missed -> StatusListState.Failed(answer.error.trouble)
            }
        }

    private fun people(accounts: List<Account>, mapper: StatusRowMapper) =
        StatusListState.Accounts(accounts.map { StatusListState.Person(mapper.author(it), it.emojis) })
}
