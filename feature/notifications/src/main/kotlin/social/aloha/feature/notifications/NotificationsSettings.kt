// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.notifications

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.sync.SyncSettings
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.PollFrequency
import social.aloha.core.model.QuietHours
import social.aloha.core.sync.Distributor
import social.aloha.core.sync.PushRegistrar
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow
import social.aloha.core.ui.openInBrowser

/** [quiet] is null while there are no quiet hours. */
internal data class SyncSettingsUi(
    val frequency: PollFrequency = PollFrequency.Normal,
    val wifiOnly: Boolean = false,
    val quiet: QuietHours? = null,
)

/** The push distributors installed, and the one chosen; null for none. */
internal data class PushUi(val distributors: List<Distributor> = emptyList(), val chosen: String? = null)

/** How the active account's news arrives: the push app, how often it is asked, Wi-Fi only and quiet hours. */
@HiltViewModel
internal class NotificationsSettingsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SyncSettings,
    private val registrar: PushRegistrar,
) : ViewModel() {
    private val pushState = MutableStateFlow(PushUi(registrar.distributors(), registrar.current()))
    val push: StateFlow<PushUi> = pushState

    fun onDistributor(packageName: String?) {
        pushState.update { it.copy(chosen = packageName) }
        viewModelScope.launch { registrar.choose(packageName) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SyncSettingsUi> = accounts.activeAccount.filterNotNull().flatMapLatest { account ->
        combine(settings.pollFrequency(account.id), settings.wifiOnly, settings.quietHours, ::SyncSettingsUi)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), SyncSettingsUi())

    fun onFrequency(frequency: PollFrequency) {
        val account = accounts.activeAccount.value ?: return
        viewModelScope.launch { settings.setPollFrequency(account.id, frequency) }
    }

    fun onWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(wifiOnly) }
    }

    fun onQuietHours(hours: QuietHours?) {
        viewModelScope.launch { settings.setQuietHours(hours) }
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
        val push by viewModel.push.collectAsStateWithLifecycle()
        val context = LocalContext.current
        Column {
            PushRows(push, viewModel::onDistributor) { openInBrowser(context, DISTRIBUTORS) }
            SyncRows(state, viewModel::onFrequency, viewModel::onWifiOnly, viewModel::onQuietHours) {
                openSettings(context)
            }
        }
    }
}

@Composable
internal fun SyncRows(
    state: SyncSettingsUi,
    onFrequency: (PollFrequency) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onQuietHours: (QuietHours?) -> Unit,
    onKinds: () -> Unit,
) {
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
        QuietRows(state.quiet, onQuietHours)
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onKinds),
            headlineContent = { Text(stringResource(R.string.settings_notification_kinds)) },
            supportingContent = { Text(stringResource(R.string.settings_notification_kinds_summary)) },
        )
    }
}

/** Which app delivers pushes; without one, the app polls as it always does. */
@Composable
internal fun PushRows(state: PushUi, onDistributor: (String?) -> Unit, onFind: () -> Unit) {
    if (state.distributors.isEmpty()) {
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onFind),
            headlineContent = { Text(stringResource(R.string.settings_push_find)) },
            supportingContent = { Text(stringResource(R.string.settings_push_none)) },
        )
        return
    }
    ChoiceRows(
        stringResource(R.string.settings_push),
        listOf<Pair<String?, String>>(null to stringResource(R.string.settings_push_off)) +
            state.distributors.map { it.packageName to it.label },
        state.chosen,
        onDistributor,
    )
}

@Composable
private fun QuietRows(quiet: QuietHours?, onQuietHours: (QuietHours?) -> Unit) {
    val shown = quiet ?: QuietHours.Default
    val context = LocalContext.current
    val from = hourText(context, shown.fromHour)
    val until = hourText(context, shown.untilHour)
    SwitchRow(
        stringResource(R.string.settings_quiet_hours),
        quiet != null,
        { onQuietHours(if (it) QuietHours.Default else null) },
        summary = stringResource(R.string.settings_quiet_hours_summary, from, until).takeIf { quiet != null },
    )
    if (quiet == null) return
    var picking by remember { mutableStateOf<Boolean?>(null) }
    ListItem(
        modifier = Modifier.clickable(role = Role.Button) { picking = true },
        headlineContent = { Text(stringResource(R.string.settings_quiet_from, from)) },
    )
    ListItem(
        modifier = Modifier.clickable(role = Role.Button) { picking = false },
        headlineContent = { Text(stringResource(R.string.settings_quiet_until, until)) },
    )
    picking?.let { start ->
        HourDialog(if (start) quiet.fromHour else quiet.untilHour, onDismiss = { picking = null }) { hour ->
            onQuietHours(if (start) quiet.copy(fromHour = hour) else quiet.copy(untilHour = hour))
            picking = null
        }
    }
}

/** An hour of the day as the device writes times, "22:00" or "10:00 PM", as its time picker does too. */
private fun hourText(context: Context, hour: Int): String = DateFormat.getTimeFormat(
    context,
).format(Date.from(LocalTime.of(hour, 0).atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourDialog(hour: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val time = rememberTimePickerState(initialHour = hour, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_quiet_pick)) },
        text = { TimeInput(time) },
        confirmButton = {
            TextButton(onClick = { onPick(time.hour) }) { Text(stringResource(R.string.settings_quiet_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_quiet_cancel)) } },
    )
}

private const val DISTRIBUTORS = "https://unifiedpush.org/users/distributors/"

@Module
@InstallIn(SingletonComponent::class)
internal object NotificationsSettingsModule {
    @Provides
    @IntoSet
    fun section(): SettingsSection = NotificationsSettings
}
