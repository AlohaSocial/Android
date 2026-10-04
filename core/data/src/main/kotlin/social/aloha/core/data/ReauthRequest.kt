// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader asked to sign the account in use in again, from the banner or from Settings, Accounts;
 * the app shows sign-in for it until a sign-in succeeds.
 */
@Singleton
public class ReauthRequest @Inject constructor() {
    private val asked = MutableStateFlow(false)

    public val requested: StateFlow<Boolean> = asked.asStateFlow()

    public fun ask() {
        asked.value = true
    }

    public fun done() {
        asked.value = false
    }
}
