// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.designsystem

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

public enum class ThemeMode { System, Light, Dark }

/** Contrast follows the system on Android 14+ unless the person picked one. */
public enum class ContrastPreference { FollowSystem, Standard, High }

/**
 * Where the colour scheme comes from: the server's theming colour first, the
 * wallpaper only when chosen, the app's seed otherwise.
 */
public sealed interface ColourSource {
    public data class Server(@param:ColorInt val primary: Int) : ColourSource

    public data object Dynamic : ColourSource

    public data object Seed : ColourSource
}

/** Whether the Black theme is on, where filled pills draw outlined instead. */
public val LocalBlackTheme: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

@Immutable
public data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    val contrast: ContrastPreference = ContrastPreference.FollowSystem,
    val black: Boolean = false,
    val colourSource: ColourSource = ColourSource.Seed,
)

/**
 * The root of every window (each Activity's `setContent` starts here, so
 * sheets and dialogs composed inside inherit it).
 */
@Composable
public fun AlohaTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val contrast = resolveContrast(settings.contrast, context)
    val scheme = remember(settings, dark, contrast) {
        val base = colorScheme(context, settings.colourSource, dark, contrast)
        if (dark && settings.black) base.black() else base
    }
    CompositionLocalProvider(
        LocalAlohaSemanticColors provides if (dark) AlohaSemanticColors.Dark else AlohaSemanticColors.Light,
        LocalBlackTheme provides (dark && settings.black),
    ) {
        MaterialTheme(colorScheme = scheme, shapes = AlohaShapes, content = content)
    }
}

private fun resolveContrast(preference: ContrastPreference, context: Context): ContrastLevel = when (preference) {
    ContrastPreference.FollowSystem -> systemContrast(context)
    ContrastPreference.Standard -> ContrastLevel.Standard
    ContrastPreference.High -> ContrastLevel.High
}

private fun colorScheme(context: Context, source: ColourSource, dark: Boolean, contrast: ContrastLevel): ColorScheme =
    when (source) {
        is ColourSource.Server -> seededColorScheme(source.primary, dark, contrast)
        ColourSource.Dynamic -> wallpaperScheme(context, dark) ?: seededColorScheme(ALOHA_SEED, dark, contrast)
        ColourSource.Seed -> seededColorScheme(ALOHA_SEED, dark, contrast)
    }

/** Android's wallpaper colours on API 31+, `null` below. */
private fun wallpaperScheme(context: Context, dark: Boolean): ColorScheme? = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> null
    dark -> dynamicDarkColorScheme(context)
    else -> dynamicLightColorScheme(context)
}

// ponytail: read once per composition of the root; a contrast change in system
// settings shows after the next configuration change. Register a
// UiModeManager.ContrastChangeListener if that proves too slow.
private fun systemContrast(context: Context): ContrastLevel {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.Standard
    val contrast = context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f
    return when {
        contrast >= HIGH_CONTRAST_THRESHOLD -> ContrastLevel.High
        contrast >= MEDIUM_CONTRAST_THRESHOLD -> ContrastLevel.Medium
        else -> ContrastLevel.Standard
    }
}

private const val MEDIUM_CONTRAST_THRESHOLD = 0.25f
private const val HIGH_CONTRAST_THRESHOLD = 0.75f
