// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.safety

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
import social.aloha.core.data.timeline.FilterRepository
import social.aloha.core.model.Filter
import social.aloha.core.model.SignedInAccount

@Immutable
internal data class FiltersUiState(
    val filters: List<Filter> = emptyList(),
    val loading: Boolean = true,
    /** The server could not be asked; the filters shown are the ones last kept. */
    val failed: Boolean = false,
    val deleteFailed: Boolean = false,
)

/**
 * The reader's filters, as kept on the device and refreshed from the server on opening; a delete
 * goes from the device as the server confirms it, and what the filter hid shows again at once.
 */
@HiltViewModel(assistedFactory = FiltersViewModel.Factory::class)
internal class FiltersViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val filters: FilterRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): FiltersViewModel
    }

    private val state = MutableStateFlow(FiltersUiState())
    val uiState: StateFlow<FiltersUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            reader = account
            launch {
                filters.observe(account.id).collect { kept ->
                    state.update { it.copy(filters = kept.sortedBy { filter -> filter.title.lowercase() }) }
                }
            }
            val error = filters.refresh(account)
            state.update { it.copy(loading = false, failed = error != null) }
        }
    }

    fun onDelete(id: String) {
        val account = reader ?: return
        viewModelScope.launch {
            if (filters.delete(account, id) is Answer.Missed) state.update { it.copy(deleteFailed = true) }
        }
    }

    fun onDeleteFailureShown() {
        state.update { it.copy(deleteFailed = false) }
    }
}
