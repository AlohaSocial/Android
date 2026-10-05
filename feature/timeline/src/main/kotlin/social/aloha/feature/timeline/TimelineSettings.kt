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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.datastore.AccountSettings
import social.aloha.core.datastore.AccountSettingsStore
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.SwipeAction
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** The timeline's settings: what home shows for the account in use, and what a swipe does on the device. */
@Immutable
internal data class TimelineSettingsState(
    val showBoosts: Boolean = true,
    val showReplies: Boolean = true,
    val swipeTowardsEnd: SwipeAction = SwipeAction.Favourite,
    val swipeTowardsStart: SwipeAction = SwipeAction.Boost,
    val restorePosition: Boolean = true,
    val newPostsPill: Boolean = true,
    val titleNextFeed: Boolean = false,
)

/** The device's own timeline switches, as stored. */
private data class Reading(val restore: Boolean, val pill: Boolean, val titleNext: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class TimelineSettingsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: AccountSettingsStore,
    private val preferences: AppPreferences,
    private val reading: ReadingPreferences,
) : ViewModel(),
    TimelineSettingsActions {
    val state: StateFlow<TimelineSettingsState> = combine(
        accounts.activeAccount.filterNotNull().flatMapLatest { settings.settings(it.id) },
        preferences.swipeTowardsEnd,
        preferences.swipeTowardsStart,
        combine(reading.restorePosition, reading.newPostsPill, reading.titleNextFeed, ::Reading),
    ) { account, end, start, reading ->
        TimelineSettingsState(
            account.showBoosts,
            account.showReplies,
            end,
            start,
            reading.restore,
            reading.pill,
            reading.titleNext,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), TimelineSettingsState())

    override fun onShowBoosts(show: Boolean) = update { it.copy(showBoosts = show) }

    override fun onShowReplies(show: Boolean) = update { it.copy(showReplies = show) }

    override fun onTitleNextFeed(next: Boolean) {
        viewModelScope.launch { reading.setTitleNextFeed(next) }
    }

    override fun onRestorePosition(restore: Boolean) {
        viewModelScope.launch { reading.setRestorePosition(restore) }
    }

    override fun onNewPostsPill(pill: Boolean) {
        viewModelScope.launch { reading.setNewPostsPill(pill) }
    }

    override fun onSwipeTowardsEnd(action: SwipeAction) {
        viewModelScope.launch { preferences.setSwipeTowardsEnd(action) }
    }

    override fun onSwipeTowardsStart(action: SwipeAction) {
        viewModelScope.launch { preferences.setSwipeTowardsStart(action) }
    }

    private fun update(change: (AccountSettings) -> AccountSettings) {
        viewModelScope.launch { accounts.activeAccount.value?.let { settings.update(it.id, change) } }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object TimelineSettingsSection : SettingsSection {
    override val key: String = "timeline"
    override val order: Int = 100
    override val title: Int = R.string.timeline_settings_title
    override val icon: ImageVector = AlohaIcons.Timeline

    @Composable
    override fun Content() {
        val viewModel: TimelineSettingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        TimelineSettingsContent(state, viewModel)
    }
}

/** What the timeline section changes; the section passes its ViewModel, a preview passes nothing. */
internal interface TimelineSettingsActions {
    fun onShowBoosts(show: Boolean)

    fun onShowReplies(show: Boolean)

    fun onTitleNextFeed(next: Boolean) {}

    fun onRestorePosition(restore: Boolean) {}

    fun onNewPostsPill(pill: Boolean) {}

    fun onSwipeTowardsEnd(action: SwipeAction)

    fun onSwipeTowardsStart(action: SwipeAction)
}

@Composable
internal fun TimelineSettingsContent(state: TimelineSettingsState, actions: TimelineSettingsActions) {
    val account = stringResource(R.string.timeline_settings_account)
    val options = SwipeAction.entries.map { it to stringResource(swipeLabel(it)) }
    Column {
        SwitchRow(stringResource(R.string.timeline_show_boosts), state.showBoosts, actions::onShowBoosts, account)
        SwitchRow(stringResource(R.string.timeline_show_replies), state.showReplies, actions::onShowReplies, account)
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.timeline_settings_title_next),
            state.titleNextFeed,
            actions::onTitleNextFeed,
            stringResource(R.string.timeline_settings_title_next_summary),
        )
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.timeline_settings_restore),
            state.restorePosition,
            actions::onRestorePosition,
            stringResource(R.string.timeline_settings_restore_summary),
        )
        SwitchRow(
            stringResource(R.string.timeline_settings_pill),
            state.newPostsPill,
            actions::onNewPostsPill,
            stringResource(R.string.timeline_settings_pill_summary),
        )
        HorizontalDivider()
        ChoiceRows(
            stringResource(R.string.timeline_settings_swipe_end),
            options,
            state.swipeTowardsEnd,
            actions::onSwipeTowardsEnd,
        )
        HorizontalDivider()
        ChoiceRows(
            stringResource(R.string.timeline_settings_swipe_start),
            options,
            state.swipeTowardsStart,
            actions::onSwipeTowardsStart,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object TimelineSettingsModule {
    @Provides
    @IntoSet
    fun section(): SettingsSection = TimelineSettingsSection
}
