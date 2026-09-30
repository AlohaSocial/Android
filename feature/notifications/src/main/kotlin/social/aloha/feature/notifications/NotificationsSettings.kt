// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.PollFrequency
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

internal data class SyncSettingsUi(val frequency: PollFrequency = PollFrequency.Normal, val wifiOnly: Boolean = false)

/** How often the active account is asked what is new, and whether timelines wait for Wi-Fi. */
@HiltViewModel
internal class NotificationsSettingsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SyncSettings,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SyncSettingsUi> = accounts.activeAccount.filterNotNull().flatMapLatest { account ->
        combine(settings.pollFrequency(account.id), settings.wifiOnly, ::SyncSettingsUi)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), SyncSettingsUi())

    fun onFrequency(frequency: PollFrequency) {
        val account = accounts.activeAccount.value ?: return
        viewModelScope.launch { settings.setPollFrequency(account.id, frequency) }
    }

    fun onWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(wifiOnly) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object NotificationsSettings : SettingsSection {
    override val key: String = "notifications"
    override val order: Int = 300
    override val title: Int = R.string.settings_notifications
    override val icon: ImageVector = AlohaIcons.Notifications

    @Composable
    override fun Content() {
        val viewModel: NotificationsSettingsViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        SyncRows(state, viewModel::onFrequency, viewModel::onWifiOnly)
    }
}

@Composable
internal fun SyncRows(state: SyncSettingsUi, onFrequency: (PollFrequency) -> Unit, onWifiOnly: (Boolean) -> Unit) {
    Column {
        ChoiceRows(
            stringResource(R.string.settings_poll_frequency),
            listOf(
                PollFrequency.Frequent to stringResource(R.string.settings_poll_frequent),
                PollFrequency.Normal to stringResource(R.string.settings_poll_normal),
                PollFrequency.BatterySaver to stringResource(R.string.settings_poll_battery),
                PollFrequency.Manual to stringResource(R.string.settings_poll_manual),
            ),
            state.frequency,
            onFrequency,
        )
        SwitchRow(
            stringResource(R.string.settings_wifi_only),
            state.wifiOnly,
            onWifiOnly,
            summary = stringResource(R.string.settings_wifi_only_summary),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object NotificationsSettingsModule {
    @Provides
    @IntoSet
    fun section(): SettingsSection = NotificationsSettings
}
