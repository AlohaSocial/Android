// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.wear.auth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the watch's sign-in stands. */
@Immutable
sealed interface SignInUiState {
    data object Loading : SignInUiState

    /** Not signed in; [problem] says why the last attempt stopped, if one did. */
    data class SignedOut(val problem: WatchSignInProblem? = null) : SignInUiState

    /** The sign-in page is open on the phone, or the watch is talking to the server. */
    data class Waiting(val server: String) : SignInUiState

    data class SignedIn(val handle: String) : SignInUiState
}

@HiltViewModel
class SignInViewModel @Inject constructor(private val signIn: WatchSignIn, private val accounts: WatchAccounts) :
    ViewModel() {
    private val state = MutableStateFlow<SignInUiState>(SignInUiState.Loading)
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = accounts.current()?.let { SignInUiState.SignedIn(it.handle) } ?: SignInUiState.SignedOut()
        }
    }

    fun onServer(typed: String) {
        if (typed.isBlank() || state.value is SignInUiState.Waiting) return
        state.value = SignInUiState.Waiting(typed.trim())
        viewModelScope.launch {
            state.value = when (val result = signIn.signIn(typed)) {
                is WatchSignInResult.SignedIn -> SignInUiState.SignedIn(result.account.handle)
                is WatchSignInResult.Failed -> SignInUiState.SignedOut(result.problem)
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            signIn.signOut()
            state.value = SignInUiState.SignedOut()
        }
    }
}
