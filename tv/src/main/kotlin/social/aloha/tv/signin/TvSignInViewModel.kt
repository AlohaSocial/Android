// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.tv.signin

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import social.aloha.core.data.Authorization
import social.aloha.core.data.DiscoveredServer
import social.aloha.core.data.ServerFinder
import social.aloha.core.data.ServerLookup
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInProblem
import social.aloha.core.data.SignInResult

/** Why a step of signing in on the TV stopped, in terms the screen words. */
internal enum class TvSignInProblem { Address, Insecure, Unreachable, NotAServer, Certificate, Server, CodeRefused }

@Immutable
internal sealed interface TvSignInState {
    /** The server to sign in with is asked for. */
    data class Server(val problem: TvSignInProblem? = null) : TvSignInState

    /** Looking for the server, starting the sign-in or exchanging the code. */
    data object Working : TvSignInState

    /**
     * The server's sign-in page, as a QR code for a phone and as an address, and the code it will show there
     * once the person allowed the TV in.
     */
    data class Code(val url: String, val host: String, val problem: TvSignInProblem? = null) : TvSignInState
}

/**
 * Signing a TV in without a browser on it: the person names the server, scans the sign-in page with a
 * phone, signs in and allows the TV there, and types the code the page then shows. The account is kept
 * as the phone keeps its accounts, and the app moves on to it once it is the active one.
 */
@HiltViewModel
internal class TvSignInViewModel @Inject constructor(
    private val finder: ServerFinder,
    private val coordinator: SignInCoordinator,
) : ViewModel() {
    private val state = MutableStateFlow<TvSignInState>(TvSignInState.Server())
    val uiState: StateFlow<TvSignInState> = state.asStateFlow()

    private var server: DiscoveredServer? = null

    init {
        // a sign-in started before the process was stopped, its page perhaps already open on the phone
        viewModelScope.launch {
            val url = coordinator.pendingAuthorizationUrl()?.toHttpUrlOrNull() ?: return@launch
            if (state.value == TvSignInState.Server()) state.value = TvSignInState.Code(url.toString(), url.host)
        }
    }

    fun onServer(typed: String) {
        if (typed.isBlank()) return
        state.value = TvSignInState.Working
        viewModelScope.launch {
            when (val lookup = finder.find(typed)) {
                is ServerLookup.Found -> authorize(lookup.server)
                else -> state.value = TvSignInState.Server(lookup.problem())
            }
        }
    }

    fun onCode(typed: String) {
        if (state.value !is TvSignInState.Code || typed.isBlank()) return
        state.value = TvSignInState.Working
        viewModelScope.launch {
            val problem = when (val result = coordinator.completeWithCode(typed)) {
                // the app leaves this screen once the account is the active one; it outlives the account, which
                // may sign out again, so it is left ready for the next sign-in
                is SignInResult.SignedIn -> {
                    server = null
                    state.value = TvSignInState.Server()
                    return@launch
                }

                // a server answers a mistyped code with a 400 or a 401, whichever: unless it could not be reached,
                // the code is what to look at
                is SignInResult.Failed -> if (result.problem == SignInProblem.Offline) {
                    TvSignInProblem.Unreachable
                } else {
                    TvSignInProblem.CodeRefused
                }

                SignInResult.NotForUs -> TvSignInProblem.CodeRefused
            }
            // a code tried is used up, whatever the server said: a new page to scan, and the reason, or, for
            // a sign-in restored after a restart, which knows no server to start again with, the server asked
            server?.let { authorize(it, problem) } ?: run { state.value = TvSignInState.Server(problem) }
        }
    }

    /** Back to naming the server, dropping the sign-in in flight. */
    fun onStartOver() {
        viewModelScope.launch { coordinator.cancel() }
        server = null
        state.update { TvSignInState.Server() }
    }

    private suspend fun authorize(found: DiscoveredServer, problem: TvSignInProblem? = null) {
        server = found
        state.value = when (val started = coordinator.beginAuthorization(found)) {
            is Authorization.Started -> TvSignInState.Code(started.url, found.host, problem)
            is Authorization.Failed -> TvSignInState.Server(started.problem.problem())
        }
    }

    private fun ServerLookup.problem(): TvSignInProblem = when (this) {
        ServerLookup.InvalidAddress -> TvSignInProblem.Address

        ServerLookup.InsecureAddress -> TvSignInProblem.Insecure

        ServerLookup.Unreachable -> TvSignInProblem.Unreachable

        is ServerLookup.NothingAnswered -> TvSignInProblem.NotAServer

        // a TV has no way to show a certificate and have it trusted; the phone does
        is ServerLookup.UntrustedCertificate -> TvSignInProblem.Certificate

        is ServerLookup.Found -> TvSignInProblem.Server
    }

    private fun SignInProblem.problem(): TvSignInProblem = when (this) {
        SignInProblem.Offline -> TvSignInProblem.Unreachable
        is SignInProblem.Refused -> TvSignInProblem.CodeRefused
        is SignInProblem.ServerProblem -> TvSignInProblem.Server
        is SignInProblem.UntrustedCertificate -> TvSignInProblem.Certificate
    }
}
