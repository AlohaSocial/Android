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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.lists.Lists
import social.aloha.core.model.Account
import social.aloha.core.model.SignedInAccount
import social.aloha.core.navigation.ListMembersKey

@Immutable
internal data class MembersUiState(
    val members: List<Account> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The list follows a Nextcloud group, whose members are the group's. */
    val group: Boolean = false,
    val query: String = "",
    /** Those the reader follows who match what is typed and are not in the list yet. */
    val found: List<Account> = emptyList(),
    val refusal: Refusal? = null,
)

/**
 * Who is in one list: removed here, and added from those the reader follows, as a list can only hold
 * them. A group list's members are the group's; the server refuses to change them and says why.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MembersViewModel.Factory::class)
internal class MembersViewModel @AssistedInject constructor(
    @Assisted private val key: ListMembersKey,
    private val accounts: AccountRepository,
    private val lists: Lists,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: ListMembersKey): MembersViewModel
    }

    private val state = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = state.asStateFlow()

    private val query = MutableStateFlow("")
    private var reader: SignedInAccount? = null

    // one search at a time: a newer one, typed or after a change, cancels the one on its way
    private var searching: Job? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(key.readerId) ?: return@launch
            reader = account
            val list = (lists.all(account) as? Answer.Got)?.value?.firstOrNull { it.id == key.listId }
            state.update { it.copy(group = list?.followsGroup == true) }
            load(account)
            query.debounce(TYPING_MILLIS).distinctUntilChanged().collect { typed -> search(account, typed) }
        }
    }

    fun onQuery(value: String) {
        query.value = value
        state.update { it.copy(query = value) }
    }

    fun onAdd(person: Account) = change { lists.add(it, key.listId, person.id) }

    fun onRemove(person: Account) = change { lists.remove(it, key.listId, person.id) }

    fun onRetry() {
        val account = reader ?: return
        viewModelScope.launch { load(account) }
    }

    fun onRefusalShown() {
        state.update { it.copy(refusal = null) }
    }

    private fun change(make: suspend (SignedInAccount) -> Answer<Unit>) {
        val account = reader ?: return
        viewModelScope.launch {
            when (val answer = make(account)) {
                is Answer.Got -> {
                    load(account)
                    search(account, query.value)
                }

                is Answer.Missed -> state.update { it.copy(refusal = answer.error.refusal()) }
            }
        }
    }

    private suspend fun load(account: SignedInAccount) {
        state.update { it.copy(loading = true, failed = false) }
        val answer = lists.members(account, key.listId)
        state.update {
            it.copy(
                members = (answer as? Answer.Got)?.value ?: it.members,
                loading = false,
                failed = answer is Answer.Missed,
            )
        }
    }

    private fun search(account: SignedInAccount, typed: String) {
        searching?.cancel()
        searching = viewModelScope.launch { find(account, typed) }
    }

    private suspend fun find(account: SignedInAccount, typed: String) {
        if (typed.isBlank()) return state.update { it.copy(found = emptyList()) }
        val found = (lists.followed(account, typed) as? Answer.Got)?.value.orEmpty()
        state.update { current ->
            val inList = current.members.map(Account::id).toSet()
            current.copy(found = found.filterNot { it.id in inList })
        }
    }

    private companion object {
        const val TYPING_MILLIS = 300L
    }
}
