// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/** The app's own seed, used when neither the server nor the wallpaper provides one: a warm coral. */
@ColorInt
public const val ALOHA_SEED: Int = 0xFFE0654A.toInt()

/** How far a scheme pushes contrast, in Material Color Utilities' `contrastLevel` units. */
public enum class ContrastLevel(internal val level: Double) {
    Standard(0.0),
    Medium(0.5),
    High(1.0),
}

/**
 * A Material 3 scheme generated from [seed] (the server's theming colour or
 * [ALOHA_SEED]) the way Nextcloud's Android apps generate theirs: tonal spot.
 */
public fun seededColorScheme(@ColorInt seed: Int, dark: Boolean, contrast: ContrastLevel): ColorScheme =
    SchemeTonalSpot(Hct.fromInt(seed), dark, contrast.level).toColorScheme()

/** The Black option: a dark scheme whose surfaces start at pure black and step up for containers. */
public fun ColorScheme.black(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF121214),
    surfaceContainerHigh = Color(0xFF1B1B1E),
    surfaceContainerHighest = Color(0xFF242428),
    surfaceBright = Color(0xFF2A2A2E),
)
