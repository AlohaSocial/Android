// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.moderation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.moderation.Moderation

/** Whether [readerId]'s role on their server grants anything the console offers; asked once per Settings. */
@HiltViewModel(assistedFactory = ModeratorCheck.Factory::class)
internal class ModeratorCheck @AssistedInject constructor(
    @Assisted readerId: String,
    accounts: AccountRepository,
    moderation: Moderation,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): ModeratorCheck
    }

    private val current = MutableStateFlow(false)
    val moderator: StateFlow<Boolean> = current.asStateFlow()

    init {
        viewModelScope.launch {
            current.value = accounts.byId(readerId)?.let { moderation.role(it)?.any } == true
        }
    }
}

/**
 * True when [readerId] may moderate their server, so Settings lists the console. A server that reports no
 * role, Nextcloud Social so far, never shows it.
 */
@Composable
public fun rememberModerator(readerId: String): Boolean {
    val check = hiltViewModel<ModeratorCheck, ModeratorCheck.Factory>(key = "moderator-$readerId") {
        it.create(readerId)
    }
    val moderator by check.moderator.collectAsStateWithLifecycle()
    return moderator
}
