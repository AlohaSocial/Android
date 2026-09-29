// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.AppPreferences

/**
 * The terms a person accepts before adding an account. Versioned: raising [CURRENT_TERMS_VERSION]
 * shows them again to everyone who accepted an older version.
 */
@HiltViewModel
internal class TermsViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    /** Null until read, so the screen never flashes the terms at someone who accepted them. */
    val accepted: StateFlow<Boolean?> = preferences.acceptedTermsVersion
        .map { it >= CURRENT_TERMS_VERSION }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialValue = null)

    fun accept() {
        viewModelScope.launch { preferences.acceptTerms(CURRENT_TERMS_VERSION) }
    }

    private companion object {
        const val CURRENT_TERMS_VERSION = 1
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
