// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.datastore.ModePreferences
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** What Media shows: the account's sensitive-media choice, whether its server takes a change, and a refusal. */
@Immutable
internal data class MediaState(
    val policy: SensitiveMediaPolicy = SensitiveMediaPolicy.Blur,
    val changeable: Boolean = false,
    val refused: Boolean = false,
    val startMuted: Boolean = true,
    val loopShorts: Boolean = true,
    val autoplayOnMobileData: Boolean = true,
    val fullPicturesOnMobileData: Boolean = true,
)

/** The playback switches, each as it is stored. */
internal data class Playback(val startMuted: Boolean, val loopShorts: Boolean, val autoplayOnMobileData: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class MediaSettingsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val sensitive: SensitiveMedia,
    private val playback: ModePreferences,
    private val reading: ReadingPreferences,
) : ViewModel() {
    private val refused = MutableStateFlow(false)

    val state: StateFlow<MediaState> = accounts.activeAccount.flatMapLatest { reader ->
        if (reader == null) return@flatMapLatest flowOf(MediaState())
        combine(sensitive.policy(reader.id), refused) { policy, no ->
            // only Nextcloud Social takes the choice from an app; Mastodon keeps it on its website
            MediaState(policy, reader.capabilities.isNextcloudSocial, no)
        }
    }.combine(
        combine(playback.videosMuted, playback.loopShorts, playback.autoplayOnMobileData, ::Playback),
    ) { state, play ->
        state.copy(
            startMuted = play.startMuted,
            loopShorts = play.loopShorts,
            autoplayOnMobileData = play.autoplayOnMobileData,
        )
    }.combine(reading.style) { state, style -> state.copy(fullPicturesOnMobileData = style.fullPicturesOnMobileData) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), MediaState())

    fun fullPicturesOnMobileData(full: Boolean) {
        viewModelScope.launch { reading.setStyle(reading.style.first().copy(fullPicturesOnMobileData = full)) }
    }

    fun startMuted(muted: Boolean) {
        viewModelScope.launch { playback.setVideosMuted(muted) }
    }

    fun loopShorts(loop: Boolean) {
        viewModelScope.launch { playback.setLoopShorts(loop) }
    }

    fun autoplayOnMobileData(autoplay: Boolean) {
        viewModelScope.launch { playback.setAutoplayOnMobileData(autoplay) }
    }

    fun choose(policy: SensitiveMediaPolicy) {
        val reader = accounts.activeAccount.value ?: return
        viewModelScope.launch { refused.value = !sensitive.choose(reader, policy) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object MediaSection : SettingsSection {
    override val key: String = "media"
    override val order: Int = 150
    override val title: Int = R.string.media_title
    override val icon: ImageVector = AlohaIcons.Photos

    @Composable
    override fun Content() {
        val viewModel: MediaSettingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        MediaContent(
            state,
            MediaActions(
                viewModel::choose,
                viewModel::startMuted,
                viewModel::loopShorts,
                viewModel::autoplayOnMobileData,
                viewModel::fullPicturesOnMobileData,
            ),
        )
    }
}

/** Sensitive media as the account's server keeps it; a server that keeps it on its website says so. */
@Composable
internal fun MediaContent(state: MediaState, actions: MediaActions) {
    val onChoose = actions.onChoose
    Column {
        val options = listOf(
            SensitiveMediaPolicy.Blur to stringResource(R.string.media_sensitive_blur),
            SensitiveMediaPolicy.ShowAll to stringResource(R.string.media_sensitive_show),
            SensitiveMediaPolicy.HideAll to stringResource(R.string.media_sensitive_hide),
        )
        if (state.changeable) {
            ChoiceRows(stringResource(R.string.media_sensitive), options, state.policy, onChoose)
        } else {
            Text(
                stringResource(R.string.media_sensitive),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.xs),
            )
            Text(
                stringResource(
                    R.string.media_sensitive_on_website,
                    options.firstOrNull { it.first == state.policy }?.second ?: options.first().second,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
            )
        }
        if (state.refused) {
            Text(
                stringResource(R.string.media_sensitive_refused),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        HorizontalDivider()
        SwitchRow(
            stringResource(R.string.media_start_muted),
            state.startMuted,
            actions.onStartMuted,
            stringResource(R.string.media_start_muted_summary),
        )
        SwitchRow(stringResource(R.string.media_loop_shorts), state.loopShorts, actions.onLoopShorts)
        SwitchRow(
            stringResource(R.string.media_autoplay_mobile),
            state.autoplayOnMobileData,
            actions.onAutoplayOnMobileData,
            stringResource(R.string.media_autoplay_mobile_summary),
        )
        SwitchRow(
            stringResource(R.string.media_full_pictures),
            state.fullPicturesOnMobileData,
            actions.onFullPicturesOnMobileData,
            stringResource(R.string.media_full_pictures_summary),
        )
    }
}

/** What the Media rows change. */
internal class MediaActions(
    val onChoose: (SensitiveMediaPolicy) -> Unit = {},
    val onStartMuted: (Boolean) -> Unit = {},
    val onLoopShorts: (Boolean) -> Unit = {},
    val onAutoplayOnMobileData: (Boolean) -> Unit = {},
    val onFullPicturesOnMobileData: (Boolean) -> Unit = {},
)
