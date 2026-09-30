// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.nextcloud.ConnectResult
import social.aloha.core.data.nextcloud.ConnectStart
import social.aloha.core.data.nextcloud.NextcloudConnection
import social.aloha.core.sync.PushRegistrar

/** Where connecting stands; a phase after a failed attempt says why. */
internal enum class NextcloudPhase(@param:StringRes val message: Int?) {
    Idle(null),
    Waiting(null),
    Unavailable(R.string.settings_nextcloud_unavailable),
    TimedOut(R.string.settings_nextcloud_timed_out),
    Failed(R.string.settings_nextcloud_failed),
}

/** [available] is false on a server that is no Nextcloud. */
internal data class NextcloudUiState(
    val available: Boolean = false,
    val connected: Boolean = false,
    val phase: NextcloudPhase = NextcloudPhase.Idle,
)

@HiltViewModel
internal class NextcloudViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val connection: NextcloudConnection,
    private val push: PushRegistrar,
) : ViewModel() {
    private val phase = MutableStateFlow(NextcloudPhase.Idle)

    // accounts whose server answered that it is no Nextcloud after all
    private val refused = MutableStateFlow<Set<String>>(emptySet())
    private val pages = Channel<String>(Channel.BUFFERED)
    private var waiting: Job? = null

    /** Login pages to open in the browser, each once. */
    val loginPages: Flow<String> = pages.receiveAsFlow()

    val uiState: StateFlow<NextcloudUiState> =
        combine(accounts.activeAccount, phase, refused) { account, phase, refused ->
            NextcloudUiState(
                available = account != null && account.capabilities.isNextcloudSocial && account.id !in refused,
                connected = account?.nextcloudConnected == true,
                phase = phase,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), NextcloudUiState())

    fun onConnect() {
        val account = accounts.activeAccount.value ?: return
        waiting?.cancel()
        waiting = viewModelScope.launch {
            phase.value = when (val start = connection.begin(account)) {
                is ConnectStart.Opened -> {
                    pages.send(start.start.loginUrl)
                    phase.value = NextcloudPhase.Waiting
                    when (connection.await(account, start.start)) {
                        ConnectResult.Connected -> {
                            // push through the Nextcloud, where it pushes, starts right away
                            push.registerAll()
                            NextcloudPhase.Idle
                        }

                        ConnectResult.TimedOut -> NextcloudPhase.TimedOut

                        ConnectResult.Failed -> NextcloudPhase.Failed
                    }
                }

                ConnectStart.NotNextcloud -> {
                    refused.update { it + account.id }
                    NextcloudPhase.Idle
                }

                ConnectStart.Unavailable -> NextcloudPhase.Unavailable
            }
        }
    }

    fun onCancel() {
        waiting?.cancel()
        phase.value = NextcloudPhase.Idle
    }

    fun onDisconnect() {
        val account = accounts.activeAccount.value ?: return
        viewModelScope.launch {
            push.forgetNextcloud(account)
            connection.disconnect(account)
        }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}
