// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.palettes.TonalPalette

/**
 * Colours that carry meaning and therefore never follow the seed: boost is
 * green, favourite amber, bookmark violet, in every scheme. Tone 30 on light
 * schemes and tone 80 on dark ones keeps them at 4.5:1 against every surface;
 * `SemanticContrastTest` holds them to it.
 */
@Immutable
public data class AlohaSemanticColors(val boost: Color, val favourite: Color, val bookmark: Color) {
    public companion object {
        private const val BOOST_HUE = 145.0
        private const val FAVOURITE_HUE = 70.0
        private const val BOOKMARK_HUE = 290.0
        private const val CHROMA = 48.0
        private const val LIGHT_TONE = 30
        private const val DARK_TONE = 80

        private fun tone(hue: Double, tone: Int) = Color(TonalPalette.fromHueAndChroma(hue, CHROMA).tone(tone))

        public val Light: AlohaSemanticColors = AlohaSemanticColors(
            boost = tone(BOOST_HUE, LIGHT_TONE),
            favourite = tone(FAVOURITE_HUE, LIGHT_TONE),
            bookmark = tone(BOOKMARK_HUE, LIGHT_TONE),
        )

        public val Dark: AlohaSemanticColors = AlohaSemanticColors(
            boost = tone(BOOST_HUE, DARK_TONE),
            favourite = tone(FAVOURITE_HUE, DARK_TONE),
            bookmark = tone(BOOKMARK_HUE, DARK_TONE),
        )
    }
}

public val LocalAlohaSemanticColors: androidx.compose.runtime.ProvidableCompositionLocal<AlohaSemanticColors> =
    staticCompositionLocalOf { AlohaSemanticColors.Light }
