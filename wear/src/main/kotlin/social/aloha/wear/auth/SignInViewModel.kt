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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the watch's sign-in stands. */
@Immutable
sealed interface SignInUiState {
    data object Loading : SignInUiState

    /**
     * Not signed in; [problem] says why the last attempt stopped, if one did, and [phoneInReach] whether the
     * phone app can hand over its account.
     */
    data class SignedOut(val problem: WatchSignInProblem? = null, val phoneInReach: Boolean = false) : SignInUiState

    /** The sign-in page is open on the phone, or the watch is talking to the server or the phone. */
    data class Waiting(val server: String?) : SignInUiState

    data class SignedIn(val handle: String) : SignInUiState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: WatchSignIn,
    private val accounts: WatchAccounts,
    private val phone: PhoneAccounts,
) : ViewModel() {
    private val state = MutableStateFlow<SignInUiState>(SignInUiState.Loading)
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = accounts.current()?.let { SignInUiState.SignedIn(it.handle) } ?: signedOut()
        }
    }

    fun onServer(typed: String) {
        if (typed.isBlank() || state.value is SignInUiState.Waiting) return
        state.value = SignInUiState.Waiting(typed.trim())
        viewModelScope.launch {
            state.value = when (val result = signIn.signIn(typed)) {
                is WatchSignInResult.SignedIn -> SignInUiState.SignedIn(result.account.handle)
                is WatchSignInResult.Failed -> signedOut(result.problem)
            }
        }
    }

    fun onUsePhone() {
        if (state.value is SignInUiState.Waiting) return
        state.value = SignInUiState.Waiting(server = null)
        viewModelScope.launch {
            state.value = signIn.fromPhone()?.let { SignInUiState.SignedIn(it.handle) }
                ?: signedOut(WatchSignInProblem.NothingOnPhone)
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            signIn.signOut()
            state.value = signedOut()
        }
    }

    private fun signedOut(problem: WatchSignInProblem? = null): SignInUiState.SignedOut {
        val signedOut = SignInUiState.SignedOut(problem)
        // the phone is asked after the screen shows, so a slow Data Layer never holds it up
        viewModelScope.launch {
            val inReach = phone.reachable()
            state.update { if (it == signedOut) signedOut.copy(phoneInReach = inReach) else it }
        }
        return signedOut
    }
}
