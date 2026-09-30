// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** The writing settings, kept on the device: whether posting warns about pictures without a description. */
@HiltViewModel
internal class ComposerSettingsViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    val warnMissingDescription: StateFlow<Boolean> = preferences.warnMissingDescription
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), true)

    fun onWarnMissingDescription(warn: Boolean) {
        viewModelScope.launch { preferences.setWarnMissingDescription(warn) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object ComposerSettingsSection : SettingsSection {
    override val key: String = "composer"
    override val order: Int = 200
    override val title: Int = R.string.composer_settings_title
    override val icon: ImageVector = AlohaIcons.Compose

    @Composable
    override fun Content() {
        val viewModel: ComposerSettingsViewModel = hiltViewModel()
        val warn by viewModel.warnMissingDescription.collectAsStateWithLifecycle()
        SwitchRow(
            stringResource(R.string.composer_settings_warn_description),
            warn,
            viewModel::onWarnMissingDescription,
            stringResource(R.string.composer_settings_warn_description_summary),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object ComposerSettingsModule {
    @Provides
    @IntoSet
    fun section(): SettingsSection = ComposerSettingsSection
}
