// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.IntelligenceChoices
import social.aloha.core.datastore.IntelligencePreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.intelligence.Intelligence
import social.aloha.core.intelligence.ModelAvailability
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/**
 * The on-device features, each with its own switch and off until it is turned on. Only a build that has
 * them lists the section; a device without the language model shows alt text alone.
 */
internal object IntelligenceSection : SettingsSection {
    override val key: String = "intelligence"
    override val order: Int = 250
    override val title: Int = R.string.settings_intelligence_title
    override val icon: ImageVector = AlohaIcons.Intelligence

    @Composable
    override fun Content() {
        val viewModel: IntelligenceViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        IntelligenceContent(state, viewModel::onChange)
    }
}

internal data class IntelligenceUi(
    val choices: IntelligenceChoices = IntelligenceChoices(),
    val pictures: Boolean = false,
    /** Whether this device runs the model; null until a switch that needs it is on, as nothing asks before. */
    val model: ModelAvailability? = null,
    val hasModel: Boolean = false,
    /** The name the device gives its language model, once it was asked. */
    val modelName: String? = null,
)

/** What the device answered about its model, when it was asked. */
private data class ModelAnswer(val availability: ModelAvailability, val name: String?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class IntelligenceViewModel @Inject constructor(
    private val intelligence: Intelligence,
    private val preferences: IntelligencePreferences,
) : ViewModel() {
    /** The build's paragraph for the privacy statement, when it has any of it. */
    @get:StringRes
    val privacy: Int? get() = intelligence.privacy.takeIf { intelligence.offered }

    // the device is asked about its model only once a switch that needs it is on, and again each time the
    // section comes back, so a model fetched since counts; until then nothing of the model is touched
    private val model = preferences.choices.map { it.rewrite || it.summary }.distinctUntilChanged()
        .flatMapLatest { wanted ->
            if (wanted) {
                flow<ModelAnswer?> { emit(ModelAnswer(intelligence.availability(), intelligence.modelName())) }
            } else {
                flowOf(null)
            }
        }

    val state: StateFlow<IntelligenceUi> =
        combine(preferences.choices, model) { choices, model ->
            // what needs the model fetches it while it is wanted and still to come
            if (model?.availability == ModelAvailability.NotReady) intelligence.prepare()
            IntelligenceUi(
                choices,
                intelligence.describesPictures,
                model?.availability,
                intelligence.hasModel,
                model?.name,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), IntelligenceUi())

    fun onChange(change: (IntelligenceChoices) -> IntelligenceChoices) {
        viewModelScope.launch { preferences.update(change) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

@Composable
internal fun IntelligenceContent(
    state: IntelligenceUi,
    onChange: ((IntelligenceChoices) -> IntelligenceChoices) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.settings_intelligence_about),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s),
        )
        if (state.pictures) {
            SwitchRow(
                stringResource(R.string.settings_intelligence_alt_text),
                state.choices.altText,
                { on -> onChange { it.copy(altText = on) } },
                stringResource(R.string.settings_intelligence_alt_text_summary),
            )
        }
        if (state.hasModel) {
            val waiting = when (state.model) {
                ModelAvailability.NotReady -> stringResource(R.string.settings_intelligence_downloading)
                ModelAvailability.NotEligible -> stringResource(R.string.settings_intelligence_not_eligible)
                else -> null
            }
            SwitchRow(
                stringResource(R.string.settings_intelligence_rewrite),
                state.choices.rewrite,
                { on -> onChange { it.copy(rewrite = on) } },
                listOfNotNull(
                    stringResource(R.string.settings_intelligence_rewrite_summary),
                    waiting,
                ).joinToString("\n"),
            )
            SwitchRow(
                stringResource(R.string.settings_intelligence_summary),
                state.choices.summary,
                { on -> onChange { it.copy(summary = on) } },
                listOfNotNull(
                    stringResource(R.string.settings_intelligence_summary_summary),
                    waiting,
                ).joinToString("\n"),
            )
        }
        Models(state)
    }
}

/**
 * Which models the features that are on use: the open picture reader with alt-text drafts, the device's
 * language model with rewriting or summaries. Nothing while every feature is off: the details apply only
 * once one is on.
 */
@Composable
private fun Models(state: IntelligenceUi) {
    val pictures = stringResource(R.string.settings_intelligence_model_pictures)
        .takeIf { state.pictures && state.choices.altText }
    val language = when {
        !state.hasModel || !(state.choices.rewrite || state.choices.summary) -> null
        state.modelName != null -> stringResource(R.string.settings_intelligence_model_named, state.modelName)
        else -> stringResource(R.string.settings_intelligence_model)
    }
    val lines = listOfNotNull(pictures, language)
    if (lines.isEmpty()) return
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_intelligence_models)) },
        supportingContent = { Text(lines.joinToString("\n")) },
    )
}
