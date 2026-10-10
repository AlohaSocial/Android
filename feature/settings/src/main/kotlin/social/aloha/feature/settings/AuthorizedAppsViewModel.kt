// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.Answer
import social.aloha.core.data.nextcloud.NextcloudExtras
import social.aloha.core.model.AuthorizedApp

@Immutable
internal data class AuthorizedAppsUiState(
    val status: PageStatus = PageStatus.Loading,
    val apps: List<AuthorizedApp> = emptyList(),
    val revokeFailed: Boolean = false,
)

/** What the authorized apps page asks for. */
internal interface AuthorizedAppsActions {
    fun onRevoke(id: String)

    fun onRetry()

    fun onRevokeFailureShown()
}

/** The apps holding a key to [readerId]'s account, and signing one out. */
@HiltViewModel(assistedFactory = AuthorizedAppsViewModel.Factory::class)
internal class AuthorizedAppsViewModel @AssistedInject constructor(
    @Assisted private val readerId: String,
    private val accounts: AccountRepository,
    private val extras: NextcloudExtras,
) : ViewModel(),
    AuthorizedAppsActions {
    @AssistedFactory
    interface Factory {
        fun create(readerId: String): AuthorizedAppsViewModel
    }

    private val state = MutableStateFlow(AuthorizedAppsUiState())
    val uiState: StateFlow<AuthorizedAppsUiState> = state.asStateFlow()

    init {
        load()
    }

    override fun onRetry() = load()

    override fun onRevoke(id: String) {
        viewModelScope.launch {
            val account = accounts.byId(readerId) ?: return@launch
            when (extras.revokeApp(account, id)) {
                is Answer.Got -> state.update { it.copy(apps = it.apps.filterNot { app -> app.id == id }) }
                is Answer.Missed -> state.update { it.copy(revokeFailed = true) }
            }
        }
    }

    override fun onRevokeFailureShown() = state.update { it.copy(revokeFailed = false) }

    private fun load() {
        state.update { it.copy(status = PageStatus.Loading) }
        viewModelScope.launch {
            val account = accounts.byId(readerId)
            val blocked = pageStatusOf(account)
            if (account == null || blocked != null) {
                state.update { it.copy(status = blocked ?: PageStatus.Failed) }
                return@launch
            }
            when (val answer = extras.authorizedApps(account)) {
                is Answer.Got -> state.update { it.copy(status = PageStatus.Ready, apps = answer.value) }
                is Answer.Missed -> state.update { it.copy(status = PageStatus.Failed) }
            }
        }
    }
}
