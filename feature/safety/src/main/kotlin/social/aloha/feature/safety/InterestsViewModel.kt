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
import social.aloha.core.data.interests.Interests
import social.aloha.core.model.InterestsState
import social.aloha.core.model.SignedInAccount
import social.aloha.core.network.ApiError

@Immutable
internal data class InterestsUiState(
    val interests: InterestsState? = null,
    val loading: Boolean = true,
    /** The server keeps no interests: there is nothing to show. */
    val unavailable: Boolean = false,
    val failed: Boolean = false,
    val changeFailed: Boolean = false,
)

/** What the interests screen asks for. */
internal interface InterestsActions {
    fun onAdd(tag: String)

    fun onRemove(tag: String)

    fun onPin(tag: String, pin: Boolean)

    fun onLearning(learning: Boolean)

    fun onPaused(paused: Boolean)

    fun onReset()

    fun onChangeFailureShown()
}

/** The reader's interests on Nextcloud Social; every change answers with the whole state, which is shown. */
@HiltViewModel(assistedFactory = InterestsViewModel.Factory::class)
internal class InterestsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val interests: Interests,
) : ViewModel(),
    InterestsActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): InterestsViewModel
    }

    private val state = MutableStateFlow(InterestsUiState())
    val uiState: StateFlow<InterestsUiState> = state.asStateFlow()

    private var reader: SignedInAccount? = null

    init {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            reader = account
            when (val answer = interests.state(account)) {
                is Answer.Got -> state.update { it.copy(interests = answer.value, loading = false) }

                is Answer.Missed -> state.update {
                    it.copy(loading = false, unavailable = answer.error == ApiError.NotFound, failed = true)
                }
            }
        }
    }

    override fun onAdd(tag: String) = change { interests.add(it, tag) }

    override fun onRemove(tag: String) = change { interests.remove(it, tag) }

    override fun onPin(tag: String, pin: Boolean) = change { interests.pin(it, tag, pin) }

    override fun onLearning(learning: Boolean) = change { interests.learn(it, learning) }

    override fun onPaused(paused: Boolean) = change { interests.pause(it, paused) }

    override fun onReset() = change { interests.reset(it) }

    override fun onChangeFailureShown() = state.update { it.copy(changeFailed = false) }

    private fun change(ask: suspend (SignedInAccount) -> Answer<InterestsState>) {
        val account = reader ?: return
        viewModelScope.launch {
            when (val answer = ask(account)) {
                is Answer.Got -> state.update { it.copy(interests = answer.value) }
                is Answer.Missed -> state.update { it.copy(changeFailed = true) }
            }
        }
    }
}
