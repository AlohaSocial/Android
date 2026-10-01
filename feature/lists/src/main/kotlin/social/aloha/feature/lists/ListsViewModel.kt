// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.lists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.lists.Lists
import social.aloha.core.model.AccountList
import social.aloha.core.model.ListRepliesPolicy
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError

/** Why a change to a list did not happen: in the server's words where it gave them. */
@Immutable
internal sealed interface Refusal {
    data class Said(val message: String) : Refusal

    data object Failed : Refusal
}

internal fun ApiError.refusal(): Refusal =
    (this as? ApiError.Unprocessable)?.message?.takeIf { it.isNotBlank() }?.let(Refusal::Said) ?: Refusal.Failed

@Immutable
internal data class ListsUiState(
    val lists: List<AccountList> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val refusal: Refusal? = null,
)

/** The reader's lists, made, set and deleted here; a change the server refuses says why. */
@HiltViewModel(assistedFactory = ListsViewModel.Factory::class)
internal class ListsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val lists: Lists,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): ListsViewModel
    }

    private val state = MutableStateFlow(ListsUiState())
    val uiState: StateFlow<ListsUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            reader = accounts.byId(readerId)
            load()
        }
    }

    fun onRetry() {
        viewModelScope.launch { load() }
    }

    fun onCreate(title: String) = change { lists.create(it, title) }

    fun onUpdate(list: AccountList, title: String, repliesPolicy: ListRepliesPolicy?, exclusive: Boolean) = change {
        lists.update(it, list.copy(title = title, repliesPolicy = repliesPolicy?.wire, exclusive = exclusive))
    }

    fun onDelete(list: AccountList) = change { lists.delete(it, list.id) }

    fun onRefusalShown() {
        state.update { it.copy(refusal = null) }
    }

    /** Makes a change, and reads the lists again once it is made; says why where it was refused. */
    private fun <T> change(make: suspend (SignedInAccount) -> Answer<T>) {
        val account = reader ?: return
        viewModelScope.launch {
            when (val answer = make(account)) {
                is Answer.Got -> load()
                is Answer.Missed -> state.update { it.copy(refusal = answer.error.refusal()) }
            }
        }
    }

    private suspend fun load() {
        val account = reader ?: return
        state.update { it.copy(loading = true, failed = false) }
        val answer = lists.all(account)
        state.update {
            it.copy(
                lists = (answer as? Answer.Got)?.value ?: it.lists,
                loading = false,
                failed = answer is Answer.Missed,
            )
        }
    }
}
