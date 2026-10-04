// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.ReadingStyle
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

@HiltViewModel
internal class ReadingViewModel @Inject constructor(private val reading: ReadingPreferences) : ViewModel() {
    val style: StateFlow<ReadingStyle> =
        reading.style.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ReadingStyle())

    fun change(made: (ReadingStyle) -> ReadingStyle) {
        viewModelScope.launch { reading.setStyle(made(reading.style.first())) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object ReadingSection : SettingsSection {
    override val key: String = "reading"
    override val order: Int = 60
    override val title: Int = R.string.reading_title
    override val icon: ImageVector = AlohaIcons.Feed

    @Composable
    override fun Content() {
        val viewModel: ReadingViewModel = hiltViewModel()
        val style by viewModel.style.collectAsStateWithLifecycle()
        ReadingContent(style, viewModel::change)
    }
}

/** How posts read, each a switch, as every timeline then shows them. */
@Composable
internal fun ReadingContent(style: ReadingStyle, onChange: ((ReadingStyle) -> ReadingStyle) -> Unit) {
    Column {
        SwitchRow(stringResource(R.string.reading_compact), style.compact, { on ->
            onChange { it.copy(compact = on) }
        }, stringResource(R.string.reading_compact_summary))
        SwitchRow(stringResource(R.string.reading_serif), style.serif, { on -> onChange { it.copy(serif = on) } })
        SwitchRow(stringResource(R.string.reading_relaxed), style.relaxed, { on ->
            onChange { it.copy(relaxed = on) }
        })
        SwitchRow(stringResource(R.string.reading_rounded), style.roundedAvatars, { on ->
            onChange { it.copy(roundedAvatars = on) }
        })
        SwitchRow(stringResource(R.string.reading_counts), style.showCounts, { on ->
            onChange { it.copy(showCounts = on) }
        }, stringResource(R.string.reading_counts_summary))
        SwitchRow(stringResource(R.string.reading_badge), style.unreadBadge, { on ->
            onChange { it.copy(unreadBadge = on) }
        })
    }
}
