// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.feature.settings

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import social.aloha.core.data.AccountRepository
import social.aloha.core.datastore.AppPreferences
import social.aloha.core.designsystem.AlohaIcons
import social.aloha.core.designsystem.AlohaSpacing
import social.aloha.core.model.AccentSource
import social.aloha.core.model.Appearance
import social.aloha.core.model.AppearanceContrast
import social.aloha.core.model.AppearanceMode
import social.aloha.core.ui.ChoiceRows
import social.aloha.core.ui.SettingsSection
import social.aloha.core.ui.SwitchRow

/** What Appearance shows: the reader's choices, and whether their server wears a colour to offer. */
@Immutable
internal data class AppearanceState(val appearance: Appearance = Appearance(), val serverColour: Boolean = false)

@HiltViewModel
internal class AppearanceViewModel @Inject constructor(
    accounts: AccountRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    val state: StateFlow<AppearanceState> = combine(preferences.appearance, accounts.activeAccount) { look, reader ->
        AppearanceState(look, reader?.capabilities?.theme?.hasColour == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), AppearanceState())

    fun change(made: (Appearance) -> Appearance) {
        viewModelScope.launch { preferences.setAppearance(made(preferences.appearance.first())) }
    }

    private companion object {
        const val STOP_MILLIS = 5_000L
    }
}

internal object AppearanceSection : SettingsSection {
    override val key: String = "appearance"
    override val order: Int = 50
    override val title: Int = R.string.appearance_title
    override val icon: ImageVector = AlohaIcons.Appearance

    @Composable
    override fun Content() {
        val viewModel: AppearanceViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        AppearanceContent(state, viewModel::change)
    }
}

/** Light or dark, contrast, black, and where the colours come from. */
@Composable
internal fun AppearanceContent(state: AppearanceState, onChange: ((Appearance) -> Appearance) -> Unit) {
    val look = state.appearance
    Column {
        ChoiceRows(
            stringResource(R.string.appearance_mode),
            listOf(
                AppearanceMode.System to stringResource(R.string.appearance_mode_system),
                AppearanceMode.Light to stringResource(R.string.appearance_mode_light),
                AppearanceMode.Dark to stringResource(R.string.appearance_mode_dark),
            ),
            look.mode,
        ) { mode -> onChange { it.copy(mode = mode) } }
        SwitchRow(
            stringResource(R.string.appearance_black),
            look.black,
            { black -> onChange { it.copy(black = black) } },
            stringResource(R.string.appearance_black_summary),
        )
        HorizontalDivider()
        ChoiceRows(
            stringResource(R.string.appearance_contrast),
            listOfNotNull(
                // only Android 14 and later have a contrast setting to follow
                (AppearanceContrast.System to stringResource(R.string.appearance_contrast_system))
                    .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE },
                AppearanceContrast.Standard to stringResource(R.string.appearance_contrast_standard),
                AppearanceContrast.High to stringResource(R.string.appearance_contrast_high),
            ),
            look.contrast.takeUnless {
                it == AppearanceContrast.System && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            } ?: AppearanceContrast.Standard,
        ) { contrast -> onChange { it.copy(contrast = contrast) } }
        HorizontalDivider()
        ChoiceRows(
            stringResource(R.string.appearance_colours),
            listOfNotNull(
                AccentSource.Server to stringResource(
                    if (state.serverColour) {
                        R.string.appearance_colours_server
                    } else {
                        R.string.appearance_colours_no_server
                    },
                ),
                // the wallpaper's colours exist from Android 12
                (AccentSource.Wallpaper to stringResource(R.string.appearance_colours_wallpaper))
                    .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S },
                AccentSource.App to stringResource(R.string.appearance_colours_app),
                AccentSource.Custom to stringResource(R.string.appearance_colours_custom),
            ),
            look.accent,
        ) { accent -> onChange { it.copy(accent = accent) } }
        if (look.accent == AccentSource.Custom) {
            AccentSwatches(look.customAccent) { colour -> onChange { it.copy(customAccent = colour) } }
        }
    }
}

/** A few accents to grow the colours from, each a swatch named for a screen reader. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = AlohaSpacing.m, vertical = AlohaSpacing.s).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
        verticalArrangement = Arrangement.spacedBy(AlohaSpacing.s),
    ) {
        ACCENTS.forEach { (colour, name) ->
            val label = stringResource(name)
            val chosen = colour == selected
            Surface(
                shape = CircleShape,
                color = Color(colour),
                modifier = Modifier
                    .size(SWATCH)
                    .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(colour) }
                    .semantics { contentDescription = label },
            ) {
                // the chosen swatch carries a mark, so the choice never rests on its outline alone
                if (chosen) {
                    Box(contentAlignment = Alignment.Center) {
                        val mark = if (Color(colour).luminance() > HALF) Color.Black else Color.White
                        Icon(AlohaIcons.Check, contentDescription = null, tint = mark)
                    }
                }
            }
        }
    }
}

private val SWATCH = 48.dp
private const val HALF = 0.5f

private val ACCENTS = listOf(
    Appearance.DEFAULT_CUSTOM_ACCENT to R.string.appearance_accent_coral,
    0xFF0082C9.toInt() to R.string.appearance_accent_blue,
    0xFF2E7D32.toInt() to R.string.appearance_accent_green,
    0xFF00897B.toInt() to R.string.appearance_accent_teal,
    0xFF6750A4.toInt() to R.string.appearance_accent_violet,
    0xFFC2185B.toInt() to R.string.appearance_accent_pink,
    0xFFF9A825.toInt() to R.string.appearance_accent_yellow,
    0xFF5D4037.toInt() to R.string.appearance_accent_brown,
)
