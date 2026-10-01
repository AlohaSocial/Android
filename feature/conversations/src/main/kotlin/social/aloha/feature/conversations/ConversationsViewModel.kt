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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.conversations.Conversations
import social.aloha.core.model.Conversation
import social.aloha.core.model.SignedInAccount

@Immutable
internal data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val done: Boolean = false,
    /** A change the server refused, which was undone. */
    val changeFailed: Boolean = false,
)

/**
 * The reader's direct conversations, newest first, loaded afresh each time the screen comes back: a
 * reply sent from a thread moves its conversation up. Opening one marks it read; one taken off the
 * list, or everything marked read, is shown so at once and undone if the server refuses.
 */
@HiltViewModel(assistedFactory = ConversationsViewModel.Factory::class)
internal class ConversationsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val conversations: Conversations,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): ConversationsViewModel
    }

    private val state = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null
    private var next: HttpUrl? = null

    // the page on its way: a refresh replaces it rather than racing it
    private var loading: Job? = null

    /** The first page again, in place of what is shown. */
    fun onRefresh() {
        loading?.cancel()
        loading = viewModelScope.launch {
            val account = reader ?: accounts.byId(readerId)?.also { reader = it } ?: return@launch
            load(account, null)
        }
    }

    fun onMore() {
        val account = reader ?: return
        if (loading?.isActive == true || state.value.done) return
        loading = viewModelScope.launch { load(account, next) }
    }

    fun onOpened(id: String) {
        val account = reader ?: return
        if (state.value.conversations.none { it.id == id && it.unread }) return
        change(
            shown = { list -> list.unread(setOf(id), false) },
            undone = { list -> list.unread(setOf(id), true) },
        ) { conversations.read(account, id) }
    }

    fun onReadAll() {
        val account = reader ?: return
        val unread = state.value.conversations.filter { it.unread }.map { it.id }.toSet()
        change({ list -> list.unread(unread, false) }, { list -> list.unread(unread, true) }) {
            conversations.readAll(account)
        }
    }

    fun onDelete(id: String) {
        val account = reader ?: return
        val shown = state.value.conversations
        val index = shown.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = shown[index]
        change(
            shown = { list -> list.filter { it.id != id } },
            // back where it was, unless a refresh brought it back already
            undone = { list ->
                if (list.any {
                        it.id == id
                    }
                ) {
                    list
                } else {
                    list.toMutableList().apply { add(index.coerceAtMost(size), removed) }
                }
            },
        ) { conversations.delete(account, id) }
    }

    fun onChangeFailureShown() {
        state.update { it.copy(changeFailed = false) }
    }

    /**
     * Shows a change at once and asks the server for it; a refusal undoes only this change, so others
     * made meanwhile, and a refresh that landed, stay.
     */
    private fun change(
        shown: (List<Conversation>) -> List<Conversation>,
        undone: (List<Conversation>) -> List<Conversation>,
        ask: suspend () -> Answer<*>,
    ) {
        state.update { it.copy(conversations = shown(it.conversations)) }
        viewModelScope.launch {
            if (ask() is Answer.Missed) {
                state.update {
                    it.copy(conversations = undone(it.conversations), changeFailed = true)
                }
            }
        }
    }

    private suspend fun load(account: SignedInAccount, after: HttpUrl?) {
        state.update { it.copy(loading = true, failed = false) }
        when (val answer = conversations.page(account, after)) {
            is Answer.Got -> {
                next = answer.value.next
                state.update { current ->
                    val page = answer.value.conversations
                    val shown = if (after == null) page else current.conversations + page
                    current.copy(conversations = shown.distinctBy { it.id }, loading = false, done = next == null)
                }
            }

            is Answer.Missed -> state.update { it.copy(loading = false, failed = true) }
        }
    }
}

private fun List<Conversation>.unread(ids: Set<String>, unread: Boolean) =
    map { if (it.id in ids) it.copy(unread = unread) else it }
