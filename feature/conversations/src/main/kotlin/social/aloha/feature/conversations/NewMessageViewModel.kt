// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.conversations

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.conversations.Conversations
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount

@Immutable
internal data class NewMessageUiState(
    val query: String = "",
    /** Those the reader and they follow each other with, shown while nothing is typed. */
    val mutuals: List<Account> = emptyList(),
    val found: List<Account> = emptyList(),
    val loading: Boolean = true,
) {
    val shown: List<Account> get() = if (query.isBlank()) mutuals else found
}

/** Whom a new direct message is for: a mutual follow, or anyone found by name. */
@HiltViewModel(assistedFactory = NewMessageViewModel.Factory::class)
internal class NewMessageViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val conversations: Conversations,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): NewMessageViewModel
    }

    private val state = MutableStateFlow(NewMessageUiState())
    val uiState: StateFlow<NewMessageUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null
    private var search: Job? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            reader = account
            val mutuals = (conversations.mutuals(account) as? Answer.Got)?.value.orEmpty()
            state.update { it.copy(mutuals = mutuals, loading = false) }
            // a name typed before the account was known is searched now
            if (state.value.query.isNotBlank()) onQuery(state.value.query)
        }
    }

    fun onQuery(query: String) {
        state.update { it.copy(query = query) }
        search?.cancel()
        val account = reader ?: return
        if (query.isBlank()) return state.update { it.copy(found = emptyList(), loading = false) }
        search = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            state.update { it.copy(loading = true) }
            val found = (conversations.people(account, query) as? Answer.Got)?.value.orEmpty()
            state.update { it.copy(found = found, loading = false) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
