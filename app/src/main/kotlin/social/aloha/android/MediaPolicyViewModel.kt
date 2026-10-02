// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.model.SensitiveMediaPolicy

/** How the account in use shows sensitive media, asked of its server each time it comes into use. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaPolicyViewModel @Inject constructor(accounts: AccountRepository, sensitive: SensitiveMedia) : ViewModel() {
    private val active = accounts.activeAccount.filterNotNull().distinctUntilChangedBy { it.id }

    val policy: StateFlow<SensitiveMediaPolicy> = active.flatMapLatest { sensitive.policy(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SensitiveMediaPolicy.Blur)

    init {
        viewModelScope.launch { active.collect { sensitive.refresh(it) } }
    }
}
