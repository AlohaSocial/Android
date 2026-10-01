// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.FeedMode
import social.aloha.core.model.ModeChoices
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** The modes that can be turned on, those the server can serve, and where each sits on a phone. */
@Immutable
internal data class ModesSettingsState(
    val choices: ModeChoices = ModeChoices(),
    /** News needs a server that picks such posts out; Audio works anywhere. */
    val available: List<FeedMode> = listOf(FeedMode.Audio),
)

@HiltViewModel
internal class ModesSettingsViewModel @Inject constructor(
    accounts: AccountRepository,
    private val preferences: ModePreferences,
) : ViewModel() {
    val state: StateFlow<ModesSettingsState> = combine(preferences.modeChoices, accounts.activeAccount) {
            choices,
            reader,
        ->
        val available = listOf(FeedMode.News, FeedMode.Audio).filter {
            reader?.capabilities?.supports(it)
                ?: (it != FeedMode.News)
        }
        ModesSettingsState(choices, available)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ModesSettingsState())

    fun onTurned(mode: FeedMode, on: Boolean) = change { it.turned(mode, on) }

    fun onSlot(mode: FeedMode, slot: Int) = change { it.turned(mode, on = true, slot = slot) }

    private fun change(made: (ModeChoices) -> ModeChoices) {
        viewModelScope.launch { preferences.setModeChoices(made(preferences.modeChoices.first())) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object ModesSettingsSection : SettingsSection {
    override val key: String = "modes"
    override val order: Int = 110
    override val title: Int = R.string.modes_settings_title
    override val icon: ImageVector = AlohaIcons.Shorts

    @Composable
    override fun Content() {
        val viewModel: ModesSettingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ModesSettingsContent(state, viewModel::onTurned, viewModel::onSlot)
    }
}

/**
 * Each optional mode as a switch; one turned on also says which of Photos, Video and Shorts it takes
 * the place of on a phone's bar, which holds three. A tablet's rail shows them all.
 */
@Composable
internal fun ModesSettingsContent(
    state: ModesSettingsState,
    onTurned: (FeedMode, Boolean) -> Unit,
    onSlot: (FeedMode, Int) -> Unit,
) {
    val slots = ModeChoices.PHONE.mapIndexed { slot, mode -> slot to stringResource(modeTitle(mode)) }
    Column {
        state.available.forEachIndexed { index, mode ->
            if (index > 0) HorizontalDivider()
            val on = mode in state.choices.optional
            SwitchRow(stringResource(modeTitle(mode)), on, { onTurned(mode, it) }, stringResource(summaryOf(mode)))
            // one moved off the bar by the other is still on, and can be given a slot again
            if (on) {
                val slot = state.choices.slotOf(mode) ?: NO_SLOT
                ChoiceRows(stringResource(R.string.modes_settings_phone), slots, slot) { onSlot(mode, it) }
            }
        }
    }
}

private const val NO_SLOT = -1

private fun summaryOf(mode: FeedMode): Int = if (mode ==
    FeedMode.News
) {
    R.string.modes_settings_news
} else {
    R.string.modes_settings_audio
}

@Module
@InstallIn(SingletonComponent::class)
internal object ModesSettingsModule {
    @Provides
    @IntoSet
    fun section(): SettingsSection = ModesSettingsSection
}
