// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.compose.runtime.Immutable
import social.aloha.core.data.SignInProblem
import social.aloha.core.data.TriedAddress

/** What the sign-in screen shows. Everything in it is already worded for display. */
@Immutable
internal data class SignInUiState(
    val server: String = "",
    val manualApiAddress: String = "",
    val showManualApiAddress: Boolean = false,
    val step: SignInStep = SignInStep.EnterServer,
    /** The administrator instructions were just copied; cleared by the next step. */
    val copied: Boolean = false,
    /** The KeyChain alias chosen as the client certificate for this server, if any. */
    val clientCertificate: String? = null,
)

@Immutable
internal sealed interface SignInStep {
    /** Typing the server; [problem] once the text was refused. */
    data class EnterServerWith(val problem: AddressProblem?) : SignInStep

    data object Probing : SignInStep

    data class Instance(val card: InstanceCard) : SignInStep

    /** The browser tab is open; the app waits for the callback. */
    data object WaitingForBrowser : SignInStep

    /** The code came back and the account is being set up. */
    data object Finishing : SignInStep

    data class Failed(val failure: SignInFailure) : SignInStep

    data class UntrustedCertificate(val certificate: CertificateSummary) : SignInStep

    companion object {
        val EnterServer: SignInStep = EnterServerWith(problem = null)
    }
}

/** Why typed text was refused before anything was sent. */
internal enum class AddressProblem {
    /** It cannot be a server address. */
    NotAnAddress,

    /** It asks for `http://`, which only debug builds accept. */
    NotSecure,
}

@Immutable
internal data class InstanceCard(
    val title: String,
    val domain: String,
    val description: String,
    val userCount: Int?,
    val rules: List<String>,
    val isNextcloudSocial: Boolean,
)

@Immutable
internal sealed interface SignInFailure {
    /** No candidate API base answered; [tried] is what was tried, in order. */
    data class NothingAnswered(val tried: List<TriedAddress>) : SignInFailure

    /** A step answered with a problem other than an untrusted certificate, which has its own step. */
    data class Problem(val problem: SignInProblem) : SignInFailure
}

@Immutable
internal data class CertificateSummary(
    val host: String,
    val subject: String,
    val issuer: String,
    val validUntil: String,
    val sha256: String,
)
