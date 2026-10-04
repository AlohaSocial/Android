// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.Visibility
import social.aloha.core.model.Writing
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/**
 * The writing settings, kept on the device: the warning about pictures without a description, asking
 * before posting, the content warning field, thread numbers, and what new posts start out as.
 */
@HiltViewModel
internal class ComposerSettingsViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    val warnMissingDescription: StateFlow<Boolean> = preferences.warnMissingDescription
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), true)

    val writing: StateFlow<Writing> = preferences.writing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), Writing())

    fun onWarnMissingDescription(warn: Boolean) {
        viewModelScope.launch { preferences.setWarnMissingDescription(warn) }
    }

    fun change(made: (Writing) -> Writing) {
        viewModelScope.launch { preferences.setWriting(made(preferences.writing.first())) }
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
        val writing by viewModel.writing.collectAsStateWithLifecycle()
        val languages = remember { deviceLanguages() }
        WritingContent(warn, viewModel::onWarnMissingDescription, writing, languages, viewModel::change)
    }
}

/** The writing rows; [languages] are those the device reads, to start new posts in. */
@Composable
internal fun WritingContent(
    warn: Boolean,
    onWarn: (Boolean) -> Unit,
    writing: Writing,
    languages: List<String>,
    onChange: ((Writing) -> Writing) -> Unit,
) {
    Column {
        SwitchRow(
            stringResource(R.string.composer_settings_warn_description),
            warn,
            onWarn,
            stringResource(R.string.composer_settings_warn_description_summary),
        )
        SwitchRow(stringResource(R.string.composer_settings_confirm), writing.confirmBeforePosting, { on ->
            onChange { it.copy(confirmBeforePosting = on) }
        })
        SwitchRow(stringResource(R.string.composer_settings_warning), writing.alwaysShowWarning, { on ->
            onChange { it.copy(alwaysShowWarning = on) }
        })
        SwitchRow(
            stringResource(R.string.composer_settings_number),
            writing.numberThreads,
            { on -> onChange { it.copy(numberThreads = on) } },
            stringResource(R.string.composer_settings_number_summary),
        )
        HorizontalDivider()
        ChoiceRows(
            stringResource(R.string.composer_settings_visibility),
            listOf<Pair<Visibility?, String>>(
                null to stringResource(R.string.composer_settings_visibility_server),
                Visibility.Public to stringResource(R.string.composer_visibility_public),
                Visibility.Unlisted to stringResource(R.string.composer_visibility_unlisted),
                Visibility.Private to stringResource(R.string.composer_visibility_private),
            ),
            writing.visibility,
        ) { visibility -> onChange { it.copy(visibility = visibility) } }
        HorizontalDivider()
        ChoiceRows(
            stringResource(R.string.composer_settings_language),
            listOf<Pair<String?, String>>(null to stringResource(R.string.composer_settings_language_server)) +
                (languages + listOfNotNull(writing.language)).distinct().map { it to (languageName(it) ?: it) },
            writing.language,
        ) { language -> onChange { it.copy(language = language) } }
    }
}

/** The languages the device reads, most preferred first, as two-letter codes. */
private fun deviceLanguages(): List<String> {
    val list = LocaleListCompat.getAdjustedDefault()
    return (0 until list.size()).mapNotNull { list[it]?.language?.takeIf(String::isNotEmpty) }.distinct()
}

@Module
@InstallIn(SingletonComponent::class)
internal object ComposerSettingsModule {
    @Provides
    @IntoSet
    fun section(): SettingsSection = ComposerSettingsSection
}
