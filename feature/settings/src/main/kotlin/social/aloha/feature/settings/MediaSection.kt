// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.data.reading.SensitiveMedia
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.SensitiveMediaPolicy
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection

/** What Media shows: the account's sensitive-media choice, whether its server takes a change, and a refusal. */
@Immutable
internal data class MediaState(
    val policy: SensitiveMediaPolicy = SensitiveMediaPolicy.Blur,
    val changeable: Boolean = false,
    val refused: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class MediaSettingsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val sensitive: SensitiveMedia,
) : ViewModel() {
    private val refused = MutableStateFlow(false)

    val state: StateFlow<MediaState> = accounts.activeAccount.flatMapLatest { reader ->
        if (reader == null) return@flatMapLatest flowOf(MediaState())
        combine(sensitive.policy(reader.id), refused) { policy, no ->
            // only Nextcloud Social takes the choice from an app; Mastodon keeps it on its website
            MediaState(policy, reader.capabilities.isNextcloudSocial, no)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), MediaState())

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
        MediaContent(state, viewModel::choose)
    }
}

/** Sensitive media as the account's server keeps it; a server that keeps it on its website says so. */
@Composable
internal fun MediaContent(state: MediaState, onChoose: (SensitiveMediaPolicy) -> Unit) {
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
    }
}
