// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.Authorization
import social.aloha.core.data.DiscoveredServer
import social.aloha.core.data.OAuthCallbackInbox
import social.aloha.core.data.ServerCertificate
import social.aloha.core.data.ServerFinder
import social.aloha.core.data.ServerLookup
import social.aloha.core.data.SignInCoordinator
import social.aloha.core.data.SignInProblem
import social.aloha.core.data.SignInResult

@HiltViewModel
internal class SignInViewModel @Inject constructor(
    private val finder: ServerFinder,
    private val coordinator: SignInCoordinator,
    private val inbox: OAuthCallbackInbox,
    private val savedState: SavedStateHandle,
) : ViewModel(),
    SignInActions {
    private val state = MutableStateFlow(SignInUiState(server = savedState.get<String>(SERVER).orEmpty()))
    val uiState: StateFlow<SignInUiState> = state.asStateFlow()

    /** The authorisation page to open, set until the screen has opened it. */
    private val browser = MutableStateFlow<String?>(null)
    val pendingBrowserUrl: StateFlow<String?> = browser.asStateFlow()

    private var discovered: DiscoveredServer? = null
    private var untrusted: ServerCertificate? = null
    private var work: Job? = null

    init {
        viewModelScope.launch {
            // A sign-in whose browser tab outlived the process resumes where it waited.
            if (coordinator.hasPendingAuthorization()) show(SignInStep.WaitingForBrowser)
        }
        viewModelScope.launch { inbox.callback.filterNotNull().collect(::finish) }
    }

    fun onBrowserOpened() {
        browser.value = null
    }

    /** The host a client certificate would be chosen for; null until the server text is an address. */
    fun clientCertificateHost(): String? = finder.hostOf(state.value.server)

    fun onClientCertificateChosen(host: String, alias: String?) {
        viewModelScope.launch {
            finder.useClientCertificate(host, alias)
            state.update { it.copy(clientCertificate = alias) }
        }
    }

    override fun onServerChange(text: String) {
        savedState[SERVER] = text
        state.update { it.copy(server = text, step = SignInStep.EnterServer, copied = false, clientCertificate = null) }
    }

    override fun onContinue() = look { finder.find(state.value.server) }

    override fun onManualApiAddressChange(text: String) {
        state.update { it.copy(manualApiAddress = text) }
    }

    override fun onToggleManualApiAddress() {
        state.update { it.copy(showManualApiAddress = !it.showManualApiAddress) }
    }

    override fun onUseManualApiAddress() = look { finder.findAt(state.value.manualApiAddress) }

    override fun onSignIn() {
        val server = discovered ?: return
        start {
            when (val begun = coordinator.beginAuthorization(server)) {
                is Authorization.Started -> {
                    browser.value = begun.url
                    show(SignInStep.WaitingForBrowser)
                }

                is Authorization.Failed -> fail(begun.problem)
            }
        }
    }

    override fun onOpenBrowserAgain() {
        viewModelScope.launch { browser.value = coordinator.pendingAuthorizationUrl() }
    }

    override fun onOtherServer() {
        work?.cancel()
        discovered = null
        viewModelScope.launch { coordinator.cancel() }
        show(SignInStep.EnterServer)
    }

    override fun onTryAgain() {
        if (state.value.showManualApiAddress && state.value.manualApiAddress.isNotBlank()) {
            onUseManualApiAddress()
        } else {
            onContinue()
        }
    }

    /** The screen copies to the clipboard, which it owns, and then says so here. */
    override fun onCopyInstructions() {
        state.update { it.copy(copied = true) }
    }

    override fun onTrustCertificate() {
        val certificate = untrusted ?: return
        untrusted = null
        start {
            finder.trust(certificate)
            onTryAgain()
        }
    }

    override fun onRejectCertificate() {
        untrusted = null
        show(SignInStep.EnterServer)
    }

    /** The screen opens the picker and reports back through [onClientCertificateChosen]. */
    override fun onChooseClientCertificate() = Unit

    private fun start(block: suspend () -> Unit) {
        work?.cancel()
        work = viewModelScope.launch { block() }
    }

    private fun look(lookup: suspend () -> ServerLookup) {
        show(SignInStep.Probing)
        start {
            when (val found = lookup()) {
                is ServerLookup.Found -> {
                    discovered = found.server
                    show(SignInStep.Instance(found.server.toCard()))
                }

                is ServerLookup.NothingAnswered -> show(SignInStep.Failed(SignInFailure.NothingAnswered(found.tried)))

                ServerLookup.Unreachable -> fail(SignInProblem.Offline)

                is ServerLookup.UntrustedCertificate -> untrustedCertificate(found.certificate)

                ServerLookup.InvalidAddress -> show(SignInStep.EnterServerWith(AddressProblem.NotAnAddress))

                ServerLookup.InsecureAddress -> show(SignInStep.EnterServerWith(AddressProblem.NotSecure))
            }
        }
    }

    private suspend fun finish(callback: String) {
        val before = state.value.step
        show(SignInStep.Finishing)
        val result = coordinator.complete(callback)
        inbox.consume(callback)
        when (result) {
            // the app leaves this screen once the account is active
            is SignInResult.SignedIn -> Unit

            // someone else's callback changes nothing about the sign-in on screen
            SignInResult.NotForUs -> show(before)

            is SignInResult.Failed -> fail(result.problem)
        }
    }

    private fun fail(problem: SignInProblem) = when (problem) {
        is SignInProblem.UntrustedCertificate -> untrustedCertificate(problem.certificate)
        else -> show(SignInStep.Failed(SignInFailure.Problem(problem)))
    }

    private fun untrustedCertificate(certificate: ServerCertificate) {
        untrusted = certificate
        show(SignInStep.UntrustedCertificate(certificate.toSummary()))
    }

    private fun show(step: SignInStep) {
        state.update { it.copy(step = step, copied = false) }
    }

    private companion object {
        const val SERVER = "server"
    }
}
