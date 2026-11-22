// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.datastore.ReadingPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.model.ReadingStyle
import social.aloha.core.model.WarningReveal
import social.aloha.core.ui.ChoiceRows
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
        TextSizeRow(style.textScale) { scale -> onChange { it.copy(textScale = scale) } }
        SwitchRow(stringResource(R.string.reading_serif), style.serif, { on -> onChange { it.copy(serif = on) } })
        SwitchRow(stringResource(R.string.reading_relaxed), style.relaxed, { on ->
            onChange { it.copy(relaxed = on) }
        })
        SwitchRow(stringResource(R.string.reading_lines), style.postLines, { on ->
            onChange { it.copy(postLines = on) }
        })
        SwitchRow(stringResource(R.string.reading_rounded), style.roundedAvatars, { on ->
            onChange { it.copy(roundedAvatars = on) }
        })
        SwitchRow(stringResource(R.string.reading_counts), style.showCounts, { on ->
            onChange { it.copy(showCounts = on) }
        }, stringResource(R.string.reading_counts_summary))
        SwitchRow(stringResource(R.string.reading_absolute_times), style.absoluteTimes, { on ->
            onChange { it.copy(absoluteTimes = on) }
        }, stringResource(R.string.reading_absolute_times_summary))
        SwitchRow(stringResource(R.string.reading_trends), style.showTrends, { on ->
            onChange { it.copy(showTrends = on) }
        }, stringResource(R.string.reading_trends_summary))
        SwitchRow(stringResource(R.string.reading_boost_carousel), style.boostCarousel, { on ->
            onChange { it.copy(boostCarousel = on) }
        }, stringResource(R.string.reading_boost_carousel_summary))
        SwitchRow(stringResource(R.string.reading_badge), style.unreadBadge, { on ->
            onChange { it.copy(unreadBadge = on) }
        })
        SwitchRow(stringResource(R.string.reading_reduce_motion), style.reduceMotion, { on ->
            onChange { it.copy(reduceMotion = on) }
        }, stringResource(R.string.reading_reduce_motion_summary))
        SwitchRow(stringResource(R.string.reading_collapse), style.collapseLong, { on ->
            onChange { it.copy(collapseLong = on) }
        })
        SwitchRow(stringResource(R.string.reading_missing_alt), style.missingAltBadge, { on ->
            onChange { it.copy(missingAltBadge = on) }
        })
        SwitchRow(stringResource(R.string.reading_previewless), style.previewless, { on ->
            onChange { it.copy(previewless = on) }
        }, stringResource(R.string.reading_previewless_summary))
        ChoiceRows(
            stringResource(R.string.reading_reveal_title),
            listOf(
                WarningReveal.Never to stringResource(R.string.reading_reveal_never),
                WarningReveal.SameAuthor to stringResource(R.string.reading_reveal_author),
                WarningReveal.Everyone to stringResource(R.string.reading_reveal_everyone),
            ),
            style.revealWarnings,
        ) { reveal -> onChange { it.copy(revealWarnings = reveal) } }
    }
}

/** How large a post's text reads, in tenths from four fifths to half again, on top of the system's size. */
@Composable
private fun TextSizeRow(scale: Float, onScale: (Float) -> Unit) {
    // kept here while the thumb moves and saved once it is let go: every step saved would redraw the app
    var shown by remember(scale) { mutableFloatStateOf(scale) }
    val percent = NumberFormat.getPercentInstance().format(shown.toDouble())
    ListItem(
        headlineContent = {
            Row {
                Text(stringResource(R.string.reading_text_size), Modifier.weight(1f))
                Text(percent)
            }
        },
        supportingContent = {
            Slider(
                value = shown,
                onValueChange = { shown = (it * TENTHS).roundToInt() / TENTHS },
                onValueChangeFinished = { onScale(shown) },
                valueRange = ReadingStyle.MIN_TEXT_SCALE..ReadingStyle.MAX_TEXT_SCALE,
                steps = TEXT_SIZE_STEPS,
                modifier = Modifier.semantics { stateDescription = percent },
            )
        },
    )
}

private const val TENTHS = 10f
private const val TEXT_SIZE_STEPS = 6
