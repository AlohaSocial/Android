// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.compose.runtime.Immutable

/** Everything the sign-in screen can ask for; the ViewModel implements it. */
@Immutable
internal interface SignInActions {
    fun onServerChange(text: String)

    fun onContinue()

    fun onSignIn()

    fun onOtherServer()

    fun onOpenBrowserAgain()

    fun onTryAgain()

    fun onCopyInstructions()

    fun onToggleManualApiAddress()

    fun onManualApiAddressChange(text: String)

    fun onUseManualApiAddress()

    fun onTrustCertificate()

    fun onRejectCertificate()

    /** Opens the system's certificate picker; the screen owns the activity it needs. */
    fun onChooseClientCertificate()
}
