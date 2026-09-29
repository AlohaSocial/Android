// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixed semantic colours hold 4.5:1 against every surface a status row can
 * sit on, in light and dark, at every contrast level, with the Black option,
 * for the app seed and for real server colours (Nextcloud's default blue and a
 * pale yellow, the hardest case for a light scheme).
 */
class SemanticContrastTest {
    private data class Case(val name: String, val scheme: ColorScheme, val semantic: AlohaSemanticColors)

    private val seeds = mapOf(
        "aloha" to ALOHA_SEED,
        "nextcloud-blue" to 0xFF00679E.toInt(),
        "pale-yellow" to 0xFFF4E04D.toInt(),
    )

    private fun cases(): List<Case> = seeds.flatMap { (seedName, seed) ->
        listOf(false, true).flatMap { dark ->
            ContrastLevel.entries.flatMap { contrast ->
                val base = seededColorScheme(seed, dark, contrast)
                val semantic = if (dark) AlohaSemanticColors.Dark else AlohaSemanticColors.Light
                val variants = if (dark) listOf("" to base, " black" to base.black()) else listOf("" to base)
                variants.map { (variant, scheme) -> Case("$seedName dark=$dark $contrast$variant", scheme, semantic) }
            }
        }
    }

    @Test
    fun semanticColoursReadOnEverySurface() {
        val failures = cases().flatMap { case ->
            case.scheme.rowSurfaces().flatMap { (surfaceName, surface) ->
                case.semantic.all().mapNotNull { (colourName, colour) ->
                    contrastRatio(colour, surface).takeIf { it < MINIMUM }
                        ?.let { "${case.name} $colourName/$surfaceName: $it" }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun contrastRatioMatchesWcagReference() {
        assertTrue(contrastRatio(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrastRatio(Color.White, Color.White) in 0.99..1.01)
    }

    private fun ColorScheme.rowSurfaces() = listOf(
        "background" to background,
        "surface" to surface,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    private fun AlohaSemanticColors.all() = listOf("boost" to boost, "favourite" to favourite, "bookmark" to bookmark)

    private companion object {
        const val MINIMUM = 4.5

        fun contrastRatio(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        fun luminance(c: Color): Double {
            fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }
    }
}
