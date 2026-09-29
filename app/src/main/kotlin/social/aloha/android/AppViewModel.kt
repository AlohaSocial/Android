// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountMaintenance
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.timeline.CacheSweeper
import social.aloha.core.model.SignedInAccount

/** What the root of the app shows. */
sealed interface AppSession {
    data object Loading : AppSession

    data object SigningIn : AppSession

    /** [needsReauth]: the server refused the token; the cache stays and a banner offers a new sign-in. */
    data class SignedIn(val accountId: String, val handle: String, val needsReauth: Boolean) : AppSession
}

@HiltViewModel
class AppViewModel @Inject constructor(
    accounts: AccountRepository,
    maintenance: AccountMaintenance,
    sweeper: CacheSweeper,
) : ViewModel() {
    private val signingInAgain = MutableStateFlow(false)

    init {
        // once per launch, off the main thread: moved API bases are found, stale capabilities detected
        // again, and the cache trimmed within its budget
        viewModelScope.launch {
            maintenance.checkAll()
            sweeper.sweep()
        }
        // a new sign-in that succeeded ends the request for one
        viewModelScope.launch {
            accounts.activeAccount.filterNotNull().filter { !it.needsReauth }.collect { signingInAgain.value = false }
        }
    }

    val session: StateFlow<AppSession> =
        combine(accounts.accounts, accounts.activeAccount, signingInAgain, ::sessionOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppSession.Loading)

    fun signInAgain() {
        signingInAgain.value = true
    }

    private fun sessionOf(all: List<SignedInAccount>, active: SignedInAccount?, again: Boolean): AppSession = when {
        active == null && all.isEmpty() -> AppSession.SigningIn
        active == null -> AppSession.Loading
        again && active.needsReauth -> AppSession.SigningIn
        else -> AppSession.SignedIn(active.id, active.handle, active.needsReauth)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
